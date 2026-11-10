package maihatsuu.hamlet.work;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.google.common.collect.ImmutableMap;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.entity.FishingBobber;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A fisherman's whole work period: walk to the shore of water near its barrel, cast a bobber, wait for
 * a bite, and put the catch straight into its stock. Then cast again, now and then from a new spot.
 */
public final class GoFishing extends Behavior<Villager> {
	private static final int SHORE_RADIUS = 9;
	private static final int SHORE_HEIGHT = 2;
	private static final int SPOT_TRIES = 20;
	/** Each open-water check reads 100 blocks, so only a sample of a big lake is checked. */
	private static final int OPEN_WATER_CHECKS = 64;
	private static final int MIN_CAST = 2;
	private static final int MAX_CAST = 5;
	private static final long SEARCH_COOLDOWN = 100L;
	private static final long GIVE_UP_WALKING = 200L;
	/** Casting needs the fisherman properly on its spot, not just near it at the water's edge. */
	private static final double ON_SPOT_SQR = 0.7 * 0.7;
	private static final int MIN_RECOVERY = 20;
	private static final int MAX_RECOVERY = 40;
	/** Nudged this far off the spot with the line out, the fisherman walks back without reeling in. */
	private static final double NUDGED_SQR = 1.5 * 1.5;
	/** This far off the spot, the line is reeled in. */
	private static final double TOO_FAR_SQR = 6.0 * 6.0;
	/** How long after a bite the fisherman reels in; vanilla's bite lasts at least 20 ticks. */
	private static final int MIN_REACTION = 4;
	private static final int MAX_REACTION = 14;
	/** A bobber that hasn't settled on water by now landed on the bank. */
	private static final long LANDING_TICKS = 60L;
	/** Vanilla's longest wait is about 34 seconds; well past that, something's wrong, so recast. */
	private static final long GIVE_UP_WAITING = 1200L;
	private static final int MIN_PAUSE = 20;
	private static final int MAX_PAUSE = 40;
	private static final int MOVE_SPOT_CHANCE = 4;
	/** Between casts, sometimes stretch its legs nearby for a few seconds. */
	private static final int STROLL_CHANCE = 3;
	private static final int STROLL_RADIUS = 4;
	private static final int MIN_STROLL = 60;
	private static final int MAX_STROLL = 120;
	/** Longer than the work period, so a catch is never cut off; the schedule ends the behaviour. */
	private static final int RUN_TICKS = 8000;
	/** Aim at the top of the water block, where the bobber will float. */
	private static final double SURFACE_HEIGHT = 0.9;
	private static final double ROD_REACH = 0.3;

	private enum Phase { WALKING, WAITING, PAUSED, STROLLING, RECOVERING }

	private final float speed;
	private Phase phase = Phase.WALKING;
	@Nullable
	private Spot spot;
	@Nullable
	private FishingBobber bobber;
	private long phaseStart;
	private long phaseEnd;
	/** When the fisherman will react to the current bite, or 0 if nothing has bitten yet. */
	private long reelTime;
	/** After climbing out of water, when the fisherman will pick a new spot, or 0 while still in it. */
	private long dryTime;
	private long nextSearchTime;

	public GoFishing(float speed) {
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
		if (!canWork(level, villager) || level.getGameTime() < this.nextSearchTime) {
			return false;
		}
		this.spot = findSpot(level, barrel(level, villager).orElseThrow());
		if (this.spot == null) {
			this.nextSearchTime = level.getGameTime() + SEARCH_COOLDOWN;
		}
		return this.spot != null;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
		// The brain keeps ticking a running behaviour after its activity ends, so stop with the work period.
		return this.spot != null && villager.getBrain().isActive(Activity.WORK) && canWork(level, villager);
	}

	private static boolean canWork(ServerLevel level, Villager villager) {
		return villager.getVillagerData().getProfession() == VillagerProfession.FISHERMAN
			&& barrel(level, villager).isPresent()
			&& WorkRequirements.missing(villager).isEmpty()
			&& !ProfessionWork.isFull(((HamletVillager) villager).hamlet$getStock());
	}

	private static Optional<BlockPos> barrel(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == level.dimension())
			.map(GlobalPos::pos);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long time) {
		this.walkToSpot(villager, time);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long time) {
		Spot spot = this.spot;
		if (spot == null) {
			return;
		}

		// Fell in: reel in and get out before doing anything else.
		if (villager.isInWater() && this.phase != Phase.RECOVERING) {
			this.reelIn();
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot.shore(), this.speed, 0));
			this.dryTime = 0;
			this.setPhase(Phase.RECOVERING, time, 0);
			return;
		}

		switch (this.phase) {
			case RECOVERING -> {
				if (villager.isInWater()) {
					this.dryTime = 0;
					if (!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
						villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot.shore(), this.speed, 0));
					}
				} else if (this.dryTime == 0) {
					// Out: stand still and shake it off for a moment, then pick a spot afresh.
					this.dryTime = time + MIN_RECOVERY + level.random.nextInt(MAX_RECOVERY - MIN_RECOVERY + 1);
					villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
					villager.getNavigation().stop();
				} else if (time >= this.dryTime) {
					this.moveSpot(level, villager, time);
				}
			}
			case WALKING -> {
				if (isAt(villager, spot.shore(), ON_SPOT_SQR) && villager.onGround()) {
					this.cast(level, villager, time);
				} else if (time - this.phaseStart > GIVE_UP_WALKING) {
					this.moveSpot(level, villager, time);
				}
			}
			case WAITING -> {
				FishingBobber bobber = this.bobber;
				// Carried far off, or the bobber vanished: reel in and go back.
				if (!isAt(villager, spot.shore(), TOO_FAR_SQR) || bobber == null || bobber.isRemoved()) {
					this.reelIn();
					this.walkToSpot(villager, time);
					return;
				}
				// Only nudged: step back onto the spot with the line still out.
				if (!isAt(villager, spot.shore(), NUDGED_SQR) && !villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
					villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot.shore(), this.speed, 0));
				}
				holdRod(villager);
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(bobber.blockPosition()));

				if (bobber.isNibbling()) {
					if (this.reelTime == 0) {
						this.reelTime = time + MIN_REACTION + level.random.nextInt(MAX_REACTION - MIN_REACTION + 1);
					} else if (time >= this.reelTime) {
						SimpleContainer stock = ((HamletVillager) villager).hamlet$getStock();
						// Open water is judged at the aimed-at block, so a bobber that drifted on landing keeps it.
						Fishing.catchInto(level, bobber, spot.target(), stock);
						this.reelIn();
						this.setPhase(Phase.PAUSED, time, MIN_PAUSE + level.random.nextInt(MAX_PAUSE - MIN_PAUSE + 1));
					}
				} else if (bobber.hasLostWater() || !bobber.isBobbing() && time - this.phaseStart > LANDING_TICKS) {
					// Landed on the bank, or the water was removed: find water elsewhere, or stop if there's none.
					this.reelIn();
					this.moveSpot(level, villager, time);
				} else if (time - this.phaseStart > GIVE_UP_WAITING) {
					this.reelIn();
					this.setPhase(Phase.PAUSED, time, MIN_PAUSE);
				}
			}
			case PAUSED -> {
				if (time < this.phaseEnd) {
					return;
				}
				BlockPos strollTo = level.random.nextInt(STROLL_CHANCE) == 0 ? findStroll(level, villager, spot) : null;
				if (strollTo != null) {
					villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(strollTo, this.speed, 0));
					this.setPhase(Phase.STROLLING, time, MIN_STROLL + level.random.nextInt(MAX_STROLL - MIN_STROLL + 1));
				} else {
					this.returnToFishing(level, villager, time, spot);
				}
			}
			case STROLLING -> {
				if (time >= this.phaseEnd) {
					this.returnToFishing(level, villager, time, spot);
				}
			}
		}
	}

	/** Usually back to the same spot, sometimes a new one. */
	private void returnToFishing(ServerLevel level, Villager villager, long time, Spot spot) {
		if (level.random.nextInt(MOVE_SPOT_CHANCE) == 0 || !Fishing.isSurface(level, spot.target())) {
			this.moveSpot(level, villager, time);
		} else if (isAt(villager, spot.shore(), ON_SPOT_SQR) && villager.onGround()) {
			this.cast(level, villager, time);
		} else {
			this.walkToSpot(villager, time);
		}
	}

	@Nullable
	private static BlockPos findStroll(ServerLevel level, Villager villager, Spot spot) {
		BlockPos barrel = barrel(level, villager).orElse(null);
		if (barrel == null) {
			return null;
		}
		for (int attempt = 0; attempt < SPOT_TRIES; attempt++) {
			BlockPos pos = spot.shore().offset(
				level.random.nextInt(STROLL_RADIUS * 2 + 1) - STROLL_RADIUS,
				level.random.nextInt(3) - 1,
				level.random.nextInt(STROLL_RADIUS * 2 + 1) - STROLL_RADIUS
			);
			boolean nearBarrel = Math.abs(pos.getX() - barrel.getX()) <= SHORE_RADIUS
				&& Math.abs(pos.getZ() - barrel.getZ()) <= SHORE_RADIUS;
			if (nearBarrel && !pos.equals(spot.shore()) && level.isLoaded(pos) && FarmCrops.canStandAt(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	private void cast(ServerLevel level, Villager villager, long time) {
		Spot spot = this.spot;
		if (spot == null) {
			return;
		}
		holdRod(villager);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getNavigation().stop();
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(spot.target()));
		Vec3 target = Vec3.atBottomCenterOf(spot.target()).add(0.0, SURFACE_HEIGHT, 0.0);
		Vec3 eyes = villager.getEyePosition();
		Vec3 toward = new Vec3(target.x - eyes.x, 0.0, target.z - eyes.z).normalize();
		this.bobber = FishingBobber.cast(level, villager, eyes.add(toward.scale(ROD_REACH)), target);
		this.reelTime = 0;
		this.setPhase(Phase.WAITING, time, 0);
	}

	/** Picks a new spot, or ends the behaviour if there's none left. */
	private void moveSpot(ServerLevel level, Villager villager, long time) {
		this.spot = barrel(level, villager).map(barrel -> findSpot(level, barrel)).orElse(null);
		if (this.spot != null) {
			this.walkToSpot(villager, time);
		}
	}

	private void walkToSpot(Villager villager, long time) {
		if (this.spot != null) {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(this.spot.shore(), this.speed, 0));
		}
		this.setPhase(Phase.WALKING, time, 0);
	}

	private void setPhase(Phase phase, long time, int duration) {
		this.phase = phase;
		this.phaseStart = time;
		this.phaseEnd = time + duration;
	}

	private void reelIn() {
		if (this.bobber != null && !this.bobber.isRemoved()) {
			this.bobber.reel();
		}
		this.bobber = null;
	}

	/** Vanilla's trade display clears the main hand when it stops, so this is re-applied while fishing. */
	private static void holdRod(Villager villager) {
		if (villager.getMainHandItem().isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
			villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		}
	}

	private static boolean isAt(Villager villager, BlockPos pos, double distanceSqr) {
		Vec3 center = Vec3.atBottomCenterOf(pos);
		double dx = center.x - villager.getX();
		double dz = center.z - villager.getZ();
		return dx * dx + dz * dz <= distanceSqr && Math.abs(pos.getY() - villager.getY()) <= 1.0;
	}

	/**
	 * Picks water to cast to first, then somewhere on the bank to cast from. Open water is preferred,
	 * since only it can give treasure; in a small pond the one open block can be anywhere, so the cast
	 * can go in any direction rather than only straight out from the bank.
	 */
	@Nullable
	private static Spot findSpot(ServerLevel level, BlockPos barrel) {
		int reach = SHORE_RADIUS + MAX_CAST;
		List<BlockPos> water = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
			barrel.offset(-reach, -SHORE_HEIGHT - 2, -reach),
			barrel.offset(reach, SHORE_HEIGHT, reach)
		)) {
			if (Fishing.isSurface(level, pos)) {
				water.add(pos.immutable());
			}
		}
		Collections.shuffle(water, new Random(level.random.nextLong()));

		int checked = 0;
		for (BlockPos target : water) {
			if (checked++ >= OPEN_WATER_CHECKS) {
				break;
			}
			if (Fishing.isOpenWater(level, target)) {
				BlockPos shore = findBank(level, barrel, target);
				if (shore != null) {
					return new Spot(shore, target);
				}
			}
		}
		for (BlockPos target : water.subList(0, Math.min(water.size(), SPOT_TRIES))) {
			BlockPos shore = findBank(level, barrel, target);
			if (shore != null) {
				return new Spot(shore, target);
			}
		}
		return null;
	}

	/**
	 * Somewhere 2 to 5 blocks from {@code target} to stand and cast from: standable, within reach of the
	 * barrel, and right at the water's edge, with water beside it one or two blocks down.
	 */
	@Nullable
	private static BlockPos findBank(ServerLevel level, BlockPos barrel, BlockPos target) {
		List<BlockPos> banks = new ArrayList<>();
		for (int dx = -MAX_CAST; dx <= MAX_CAST; dx++) {
			for (int dz = -MAX_CAST; dz <= MAX_CAST; dz++) {
				int distanceSqr = dx * dx + dz * dz;
				if (distanceSqr < MIN_CAST * MIN_CAST || distanceSqr > MAX_CAST * MAX_CAST) {
					continue;
				}
				for (int rise = 1; rise <= 2; rise++) {
					BlockPos pos = target.offset(dx, rise, dz);
					boolean nearBarrel = Math.abs(pos.getX() - barrel.getX()) <= SHORE_RADIUS
						&& Math.abs(pos.getZ() - barrel.getZ()) <= SHORE_RADIUS
						&& Math.abs(pos.getY() - barrel.getY()) <= SHORE_HEIGHT;
					if (nearBarrel && level.isLoaded(pos) && FarmCrops.canStandAt(level, pos) && isWaterEdge(level, pos)) {
						banks.add(pos);
					}
				}
			}
		}
		return banks.isEmpty() ? null : banks.get(level.random.nextInt(banks.size()));
	}

	private static boolean isWaterEdge(ServerLevel level, BlockPos standing) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos beside = standing.relative(direction);
			if (Fishing.isSurface(level, beside.below()) || Fishing.isSurface(level, beside.below(2))) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long time) {
		this.reelIn();
		if (villager.getMainHandItem().is(Items.FISHING_ROD)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		Brain<Villager> brain = villager.getBrain();
		brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
		this.spot = null;
		this.phase = Phase.WALKING;
	}

	private record Spot(BlockPos shore, BlockPos target) {
	}
}
