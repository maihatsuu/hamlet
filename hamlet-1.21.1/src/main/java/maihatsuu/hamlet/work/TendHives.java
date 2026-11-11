package maihatsuu.hamlet.work;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.villager.HamletProfessions;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * A beekeeper's whole work period: wander around its smoker, and whenever a hive nearby is full of honey
 * and smoked by a lit campfire below it, walk over and harvest it with shears or a bottle. Unsmoked hives are left alone, since
 * harvesting them would anger the bees.
 */
public final class TendHives extends Behavior<Villager> {
	private static final int APIARY_RADIUS = 12;
	private static final int APIARY_HEIGHT = 4;
	private static final int SCAN_INTERVAL = 20;
	/** From the hive's centre, horizontally; hives are solid, so the beekeeper stands beside one. */
	private static final double REACH_SQR = 2.5 * 2.5;
	/** How far above its feet the beekeeper can reach, so a hive up in a tree trunk still counts. */
	private static final int REACH_UP = 3;
	private static final int HONEYCOMB_PER_HIVE = 3;
	private static final int HARVEST_PAUSE = 20;
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
	/** Whether this harvest is with a glass bottle, for a honey bottle, rather than shears, for honeycomb. */
	private boolean useBottle;
	/** Hives the beekeeper couldn't reach, so it doesn't keep walking at them. */
	private final Map<BlockPos, Long> skipUntil = new HashMap<>();

	public TendHives(float speed) {
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
		// The brain keeps ticking a running behaviour after its activity ends, so stop with the work period.
		return villager.getBrain().isActive(Activity.WORK) && canWork(level, villager);
	}

	private static boolean canWork(ServerLevel level, Villager villager) {
		return villager.getVillagerData().getProfession() == HamletProfessions.BEEKEEPER
			&& smoker(level, villager).isPresent()
			&& WorkRequirements.missing(villager).isEmpty()
			&& !ProfessionWork.isFull(((HamletVillager) villager).hamlet$getStock());
	}

	private static Optional<BlockPos> smoker(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == level.dimension())
			.map(GlobalPos::pos);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long time) {
		if (time < this.nextActionTime) {
			return;
		}
		BlockPos smoker = smoker(level, villager).orElseThrow();
		Brain<Villager> brain = villager.getBrain();

		BlockPos hive = this.target;
		if (hive != null) {
			if (!isReady(level, hive)) {
				this.dropTarget(villager);
			} else if (canReach(villager, hive)) {
				harvest(level, villager, hive, this.useBottle);
				this.dropTarget(villager);
				this.nextActionTime = time + HARVEST_PAUSE;
			} else if (time - this.targetSince > GIVE_UP_TICKS) {
				this.skipUntil.put(hive, time + SKIP_UNREACHABLE_TICKS);
				this.dropTarget(villager);
			}
			return;
		}

		if (time % SCAN_INTERVAL == 0) {
			this.skipUntil.values().removeIf(until -> until <= time);
			BlockPos ready = this.findReadyHive(level, villager, smoker);
			if (ready != null) {
				this.target = ready;
				this.targetSince = time;
				// Like a player, either shears it for honeycomb or bottles it for honey, picked at random.
				this.useBottle = level.random.nextBoolean();
				villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(this.useBottle ? Items.GLASS_BOTTLE : Items.SHEARS));
				villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(ready, this.speed, 1));
				brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(ready));
				return;
			}
		}

		if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			BlockPos spot = findWanderSpot(level, smoker);
			if (spot != null) {
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, this.speed, 0));
			}
			this.nextActionTime = time + MIN_WATCH_PAUSE + level.random.nextInt(MAX_WATCH_PAUSE - MIN_WATCH_PAUSE + 1);
		}
	}

	/** Full of honey, and smoked, using vanilla's own check for whether harvesting would anger the bees. */
	private static boolean isReady(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof BeehiveBlock
			&& state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS
			&& CampfireBlock.isSmokeyPos(level, pos);
	}

	@Nullable
	private BlockPos findReadyHive(ServerLevel level, Villager villager, BlockPos smoker) {
		// Beehives and bee nests share one POI type, so this finds both without scanning every block.
		return level.getPoiManager()
			.getInRange(holder -> holder.is(PoiTypes.BEEHIVE), smoker, APIARY_RADIUS + APIARY_HEIGHT, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.filter(pos -> inApiary(pos, smoker) && !this.skipUntil.containsKey(pos) && isReady(level, pos))
			.min((a, b) -> Double.compare(a.distToCenterSqr(villager.position()), b.distToCenterSqr(villager.position())))
			.orElse(null);
	}

	private static boolean canReach(Villager villager, BlockPos hive) {
		Vec3 center = Vec3.atCenterOf(hive);
		double dx = center.x - villager.getX();
		double dz = center.z - villager.getZ();
		double dy = hive.getY() - villager.getBlockY();
		return dx * dx + dz * dz <= REACH_SQR && dy >= -1 && dy <= REACH_UP;
	}

	/** Vanilla's yields: a glass bottle gives one honey bottle, shears give three honeycomb. */
	private static void harvest(ServerLevel level, Villager villager, BlockPos pos, boolean useBottle) {
		BlockState state = level.getBlockState(pos);
		// The smoke means the bees stay calm, so vanilla's path for a smoked hive: empty it, release nobody.
		((BeehiveBlock) state.getBlock()).resetHoneyLevel(level, state, pos);
		SimpleContainer stock = ((HamletVillager) villager).hamlet$getStock();
		if (useBottle) {
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(villager, GameEvent.FLUID_PICKUP, pos);
			ProfessionWork.store(stock, new ItemStack(Items.HONEY_BOTTLE));
		} else {
			level.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(villager, GameEvent.SHEAR, pos);
			ProfessionWork.store(stock, new ItemStack(Items.HONEYCOMB, HONEYCOMB_PER_HIVE));
		}
	}

	private void dropTarget(Villager villager) {
		this.target = null;
		putToolAway(villager);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	private static void putToolAway(Villager villager) {
		ItemStack held = villager.getMainHandItem();
		if (held.is(Items.SHEARS) || held.is(Items.GLASS_BOTTLE)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	@Nullable
	private static BlockPos findWanderSpot(ServerLevel level, BlockPos smoker) {
		for (int attempt = 0; attempt < WANDER_TRIES; attempt++) {
			int x = smoker.getX() + level.random.nextInt(APIARY_RADIUS * 2 + 1) - APIARY_RADIUS;
			int z = smoker.getZ() + level.random.nextInt(APIARY_RADIUS * 2 + 1) - APIARY_RADIUS;
			for (int dy = -APIARY_HEIGHT; dy <= APIARY_HEIGHT; dy++) {
				BlockPos pos = new BlockPos(x, smoker.getY() + dy, z);
				if (level.isLoaded(pos) && FarmCrops.canStandAt(level, pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	private static boolean inApiary(BlockPos pos, BlockPos smoker) {
		return Math.abs(pos.getX() - smoker.getX()) <= APIARY_RADIUS
			&& Math.abs(pos.getZ() - smoker.getZ()) <= APIARY_RADIUS
			&& Math.abs(pos.getY() - smoker.getY()) <= APIARY_HEIGHT;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long time) {
		putToolAway(villager);
		Brain<Villager> brain = villager.getBrain();
		brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
		this.target = null;
		this.nextActionTime = 0;
	}
}
