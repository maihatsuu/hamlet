package maihatsuu.hamlet.villager;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class VillagerReadiness {
	private static final long GRACE_TICKS = 24000L;
	/** A little over a day, because villagers don't start sleeping at the exact same moment each night. */
	private static final long SLEEP_WINDOW_TICKS = 26000L;
	private static final long SOCIAL_WINDOW_TICKS = 24000L;
	private static final int SOCIAL_TICKS_REQUIRED = 600;
	private static final double SOCIAL_DISTANCE_SQR = 64.0;
	private static final int MOVED_DISTANCE_SQR = 32 * 32;

	private static final int UPDATE_INTERVAL = 20;
	private static final int SPACE_CHECK_INTERVAL = 100;
	private static final int MIN_WALKABLE_CELLS = 20;
	private static final int SEARCH_RADIUS = 8;
	private static final int SEARCH_HEIGHT = 4;

	private VillagerReadiness() {
	}

	/** Why this villager currently refuses to work or trade, if it does. */
	public static Optional<Component> refusal(Villager villager) {
		if (inGrace(villager)) {
			return Optional.empty();
		}
		if (!hasSlept(villager)) {
			return Optional.of(Component.translatable("hamlet.refusal.not_slept"));
		}
		if (!hasSocialized(villager)) {
			return Optional.of(Component.translatable("hamlet.refusal.not_socialized"));
		}
		if (!hasSpace(villager)) {
			return Optional.of(Component.translatable("hamlet.refusal.cramped"));
		}
		return Optional.empty();
	}

	public static boolean inGrace(Villager villager) {
		ReadinessState state = state(villager);
		return state.graceStart == ReadinessState.UNSET || now(villager) - state.graceStart < GRACE_TICKS;
	}

	public static boolean hasSlept(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.LAST_SLEPT)
			.filter(lastSlept -> now(villager) - lastSlept < SLEEP_WINDOW_TICKS)
			.isPresent();
	}

	public static boolean hasSocialized(Villager villager) {
		ReadinessState state = state(villager);
		return state.lastSocialized != ReadinessState.UNSET && now(villager) - state.lastSocialized < SOCIAL_WINDOW_TICKS;
	}

	public static boolean hasSpace(Villager villager) {
		return state(villager).hasSpace;
	}

	public static void endGrace(Villager villager) {
		state(villager).graceStart = now(villager) - GRACE_TICKS;
	}

	/** Called every server tick. */
	public static void tick(Villager villager) {
		int offsetTick = villager.tickCount + villager.getId();
		if (offsetTick % UPDATE_INTERVAL != 0) {
			return;
		}

		ReadinessState state = state(villager);
		long now = now(villager);
		BlockPos pos = villager.blockPosition();

		if (state.graceStart == ReadinessState.UNSET) {
			state.graceStart = now;
		}
		if (state.anchor == null) {
			state.anchor = pos;
		} else if (state.anchor.distSqr(pos) > MOVED_DISTANCE_SQR) {
			state.anchor = pos;
			state.graceStart = now;
		}

		if (seesAdultVillager(villager)) {
			state.socialProgress += UPDATE_INTERVAL;
			if (state.socialProgress >= SOCIAL_TICKS_REQUIRED) {
				state.socialProgress = 0;
				state.lastSocialized = now;
			}
		}

		if (offsetTick % SPACE_CHECK_INTERVAL == 0) {
			state.hasSpace = findEnoughWalkableCells(villager.level(), pos);
		}
	}

	private static boolean seesAdultVillager(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
			.map((NearestVisibleLivingEntities visible) -> visible.contains(entity ->
				entity instanceof Villager other
					&& other != villager
					&& !other.isBaby()
					&& other.distanceToSqr(villager) <= SOCIAL_DISTANCE_SQR))
			.orElse(false);
	}

	private static boolean findEnoughWalkableCells(Level level, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);

		while (!queue.isEmpty() && seen.size() < MIN_WALKABLE_CELLS) {
			BlockPos pos = queue.remove();
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos beside = pos.relative(direction);
				for (BlockPos next : new BlockPos[] {beside, beside.above(), beside.below()}) {
					boolean inRange = Math.abs(next.getX() - start.getX()) <= SEARCH_RADIUS
						&& Math.abs(next.getZ() - start.getZ()) <= SEARCH_RADIUS
						&& Math.abs(next.getY() - start.getY()) <= SEARCH_HEIGHT;
					if (inRange && !seen.contains(next) && canStandAt(level, next)) {
						seen.add(next);
						queue.add(next);
						break;
					}
				}
			}
		}

		return seen.size() >= MIN_WALKABLE_CELLS;
	}

	private static boolean canStandAt(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockPos below = pos.below();
		return isPassable(level, pos)
			&& isPassable(level, pos.above())
			&& !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
	}

	/** Wooden doors count as open because villagers can use them. */
	private static boolean isPassable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.WOODEN_DOORS) || state.getCollisionShape(level, pos).isEmpty();
	}

	private static ReadinessState state(Villager villager) {
		return ((HamletVillager) villager).hamlet$getReadiness();
	}

	private static long now(Villager villager) {
		return villager.level().getGameTime();
	}
}
