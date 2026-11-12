package maihatsuu.hamlet.work;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

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
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Harvesting near a workstation: whenever a fully grown crop it tends is in sight, the villager walks over
 * and harvests just that crop.
 * <ul>
 *   <li>Farmer: its whole work period, wandering the field around its composter between harvests. It tends
 *   every crop in {@link Crops} except nether wart, and the smoked hives around the field ({@link Hives}).</li>
 *   <li>Cleric: nether wart around its brewing stand. It doesn't wander: this runs alongside vanilla's
 *   strolling and workstation visits, which keep it near its stand and brewing.</li>
 * </ul>
 */
public final class FarmCrops extends Behavior<Villager> {
	private static final int FIELD_RADIUS = 9;
	private static final int FIELD_HEIGHT = 2;
	private static final int SPOT_RADIUS = 8;
	private static final int SPOT_HEIGHT = 2;
	private static final int SCAN_INTERVAL = 10;
	/** Horizontal, since melons and cocoa are solid and cane is harvested from one block up. */
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
	private final VillagerProfession profession;
	private final Predicate<Block> tends;
	private final boolean patrol;
	private final boolean tendsHives;
	@Nullable
	private BlockPos target;
	/** Whether the target is a hive rather than a crop. */
	private boolean targetIsHive;
	/** For a hive: whether this harvest is with a glass bottle, for honey, rather than shears, for honeycomb. */
	private boolean useBottle;
	private long targetSince;
	private long nextActionTime;
	/** Crops the farmer couldn't reach, so it doesn't keep walking at them. */
	private final Map<BlockPos, Long> skipUntil = new HashMap<>();

	public static FarmCrops farmer(float speed) {
		return new FarmCrops(speed, VillagerProfession.FARMER, block -> !(block instanceof NetherWartBlock), true, true);
	}

	public static FarmCrops cleric(float speed) {
		return new FarmCrops(speed, VillagerProfession.CLERIC, block -> block instanceof NetherWartBlock, false, false);
	}

	private FarmCrops(float speed, VillagerProfession profession, Predicate<Block> tends, boolean patrol, boolean tendsHives) {
		super(
			ImmutableMap.of(
				MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
				MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
				MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
			),
			RUN_TICKS
		);
		this.speed = speed;
		this.profession = profession;
		this.tends = tends;
		this.patrol = patrol;
		this.tendsHives = tendsHives;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return this.canWork(level, villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
		// The brain keeps ticking a running behaviour after its activity ends, so stop with the work period.
		return villager.getBrain().isActive(Activity.WORK) && this.canWork(level, villager);
	}

	private boolean canWork(ServerLevel level, Villager villager) {
		return villager.getVillagerData().getProfession() == this.profession
			&& field(level, villager).isPresent()
			&& WorkRequirements.missing(villager).isEmpty()
			&& !ProfessionWork.isFull(((HamletVillager) villager).hamlet$getStock());
	}

	/** The workstation, if it's in this dimension. */
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

		BlockPos target = this.target;
		if (target != null) {
			boolean hive = this.targetIsHive;
			if (hive ? !Hives.isReady(level, target) || !Hives.wouldStore(villager, this.useBottle) : !this.isRipe(level, target, villager)) {
				this.dropTarget(villager);
			} else if (hive ? Hives.canReach(villager, target) : canReach(villager, target)) {
				if (hive) {
					Hives.harvest(level, villager, target, this.useBottle);
				} else {
					Crops.harvest(level, target, ((HamletVillager) villager).hamlet$getStock());
				}
				this.dropTarget(villager);
				this.nextActionTime = time + HARVEST_PAUSE;
			} else if (time - this.targetSince > GIVE_UP_TICKS) {
				this.skipUntil.put(target, time + SKIP_UNREACHABLE_TICKS);
				this.dropTarget(villager);
				brain.eraseMemory(MemoryModuleType.WALK_TARGET);
			}
			return;
		}

		if (time % SCAN_INTERVAL == 0) {
			this.skipUntil.values().removeIf(until -> until <= time);
			BlockPos crop = findNearbyCrop(level, villager, site);
			if (crop != null) {
				this.target = crop;
				this.targetSince = time;
				// A column plant's target is its second block, so walk to its base instead.
				boolean column = level.getBlockState(crop.below()).is(level.getBlockState(crop).getBlock());
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(column ? crop.below() : crop, this.speed, 1));
				brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(crop));
				return;
			}
			BlockPos hive = this.tendsHives ? Hives.findReadyHive(level, villager, site, this.skipUntil) : null;
			if (hive != null) {
				this.target = hive;
				this.targetIsHive = true;
				this.targetSince = time;
				this.useBottle = Hives.pickBottle(villager, level.random);
				Hives.holdTool(villager, this.useBottle);
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(hive, this.speed, 1));
				brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(hive));
				return;
			}
		}

		if (this.patrol && !brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
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
	private BlockPos findNearbyCrop(ServerLevel level, Villager villager, BlockPos site) {
		BlockPos center = villager.blockPosition();
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(
			center.offset(-SPOT_RADIUS, -SPOT_HEIGHT, -SPOT_RADIUS),
			center.offset(SPOT_RADIUS, SPOT_HEIGHT, SPOT_RADIUS)
		)) {
			if (!inField(pos, site) || this.skipUntil.containsKey(pos) || !level.isLoaded(pos) || !this.isRipe(level, pos, villager)) {
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

	/** Forgets the target, and puts away the hive tool if it was a hive. */
	private void dropTarget(Villager villager) {
		if (this.targetIsHive) {
			Hives.putToolAway(villager);
		}
		this.target = null;
		this.targetIsHive = false;
	}

	/** Ready to harvest, and its harvest wouldn't all be thrown away for being over the stock cap. */
	private boolean isRipe(ServerLevel level, BlockPos pos, Villager villager) {
		return this.tends.test(level.getBlockState(pos).getBlock()) && Crops.isHarvestable(level, pos)
			&& Crops.wouldStore(level, pos, ((HamletVillager) villager).hamlet$getStock());
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
	static boolean canStandAt(ServerLevel level, BlockPos pos) {
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
		// The cleric shares its walks with vanilla's strolling, so only drop a walk to a crop.
		if (this.patrol || this.target != null) {
			brain.eraseMemory(MemoryModuleType.WALK_TARGET);
			brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
		}
		this.dropTarget(villager);
		this.nextActionTime = 0;
	}
}
