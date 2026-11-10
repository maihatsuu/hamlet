package maihatsuu.hamlet.work;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;

/**
 * A farmer's whole work period: wander around the field near its composter, and whenever a fully grown
 * crop is in sight, walk over and harvest just that crop.
 */
public final class FarmCrops extends Behavior<Villager> {
	private static final int FIELD_RADIUS = 9;
	private static final int FIELD_HEIGHT = 2;
	private static final int SPOT_RADIUS = 8;
	private static final int SPOT_HEIGHT = 2;
	private static final int SCAN_INTERVAL = 10;
	/** Horizontal, since melons, cactus and cocoa are solid and cane is harvested from one block up. */
	private static final double HARVEST_REACH_SQR = 1.5 * 1.5;
	private static final double HARVEST_REACH_UP = 2.5;
	private static final int HARVEST_PAUSE = 10;
	private static final int MIN_WATCH_PAUSE = 20;
	private static final int MAX_WATCH_PAUSE = 60;
	private static final int WANDER_TRIES = 10;
	private static final long GIVE_UP_TICKS = 200L;
	private static final long SKIP_UNREACHABLE_TICKS = 600L;
	private static final int RUN_TICKS = 1200;

	private final float speed;
	@Nullable
	private BlockPos target;
	private long targetSince;
	private long nextActionTime;
	/** Crops the farmer couldn't reach, so it doesn't keep walking at them. */
	private final Map<BlockPos, Long> skipUntil = new HashMap<>();

	public FarmCrops(float speed) {
		super(
			ImmutableMap.of(
				MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
				MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
				MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
			),
			RUN_TICKS
		);
		this.speed = speed;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return canWork(level, villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
		return canWork(level, villager);
	}

	private static boolean canWork(ServerLevel level, Villager villager) {
		return villager.getVillagerData().getProfession() == VillagerProfession.FARMER
			&& field(level, villager).isPresent()
			&& WorkRequirements.missing(villager).isEmpty()
			&& !ProfessionWork.isFull(((HamletVillager) villager).hamlet$getStock());
	}

	/** The composter, if it's in this dimension. */
	private static Optional<BlockPos> field(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == level.dimension())
			.map(GlobalPos::pos);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long time) {
		if (time < this.nextActionTime) {
			return;
		}
		BlockPos site = field(level, villager).orElseThrow();
		Brain<Villager> brain = villager.getBrain();

		if (this.target != null) {
			if (!Crops.isHarvestable(level, this.target)) {
				this.target = null;
			} else if (canReach(villager, this.target)) {
				Crops.harvest(level, this.target, ((HamletVillager) villager).hamlet$getStock());
				this.target = null;
				this.nextActionTime = time + HARVEST_PAUSE;
			} else if (time - this.targetSince > GIVE_UP_TICKS) {
				this.skipUntil.put(this.target, time + SKIP_UNREACHABLE_TICKS);
				this.target = null;
				brain.eraseMemory(MemoryModuleType.WALK_TARGET);
			}
			return;
		}

		if (time % SCAN_INTERVAL == 0) {
			this.skipUntil.values().removeIf(until -> until <= time);
			BlockPos crop = findNearbyCrop(level, villager.blockPosition(), site);
			if (crop != null) {
				this.target = crop;
				this.targetSince = time;
				// A column plant's target is its second block, so walk to its base instead.
				boolean column = level.getBlockState(crop.below()).is(level.getBlockState(crop).getBlock());
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(column ? crop.below() : crop, this.speed, 1));
				brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(crop));
				return;
			}
		}

		if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			BlockPos spot = findWanderSpot(level, site);
			if (spot != null) {
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, this.speed, 0));
			}
			this.nextActionTime = time + MIN_WATCH_PAUSE + level.random.nextInt(MAX_WATCH_PAUSE - MIN_WATCH_PAUSE + 1);
		}
	}

	private static boolean canReach(Villager villager, BlockPos target) {
		Vec3 center = Vec3.atBottomCenterOf(target);
		double dx = center.x - villager.getX();
		double dz = center.z - villager.getZ();
		double dy = target.getY() - villager.getY();
		return dx * dx + dz * dz <= HARVEST_REACH_SQR && dy >= -1.0 && dy <= HARVEST_REACH_UP;
	}

	@Nullable
	private BlockPos findNearbyCrop(ServerLevel level, BlockPos center, BlockPos site) {
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(
			center.offset(-SPOT_RADIUS, -SPOT_HEIGHT, -SPOT_RADIUS),
			center.offset(SPOT_RADIUS, SPOT_HEIGHT, SPOT_RADIUS)
		)) {
			if (!inField(pos, site) || this.skipUntil.containsKey(pos) || !level.isLoaded(pos) || !Crops.isHarvestable(level, pos)) {
				continue;
			}
			double distance = pos.distSqr(center);
			if (distance < nearestDistance) {
				nearest = pos.immutable();
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	@Nullable
	private static BlockPos findWanderSpot(ServerLevel level, BlockPos site) {
		for (int attempt = 0; attempt < WANDER_TRIES; attempt++) {
			int x = site.getX() + level.random.nextInt(FIELD_RADIUS * 2 + 1) - FIELD_RADIUS;
			int z = site.getZ() + level.random.nextInt(FIELD_RADIUS * 2 + 1) - FIELD_RADIUS;
			for (int dy = -FIELD_HEIGHT; dy <= FIELD_HEIGHT; dy++) {
				BlockPos pos = new BlockPos(x, site.getY() + dy, z);
				if (level.isLoaded(pos) && canStandAt(level, pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	/** Crop blocks have no collision, so a villager can stand in one, on top of the farmland. */
	private static boolean canStandAt(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
			&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
	}

	private static boolean inField(BlockPos pos, BlockPos site) {
		return Math.abs(pos.getX() - site.getX()) <= FIELD_RADIUS
			&& Math.abs(pos.getZ() - site.getZ()) <= FIELD_RADIUS
			&& Math.abs(pos.getY() - site.getY()) <= FIELD_HEIGHT;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long time) {
		Brain<Villager> brain = villager.getBrain();
		brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
		this.target = null;
		this.nextActionTime = 0;
	}
}
