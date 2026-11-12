package maihatsuu.hamlet.work;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What farmers and clerics can harvest, and how; {@link FarmCrops} decides which of these each tends. Every harvest leaves the plant able to regrow in place, so the
 * player's layout never changes:
 * <ul>
 *   <li>Crops, cocoa and nether wart: fully grown, reset to age 0.</li>
 *   <li>Sweet berry bushes: fully grown, reset to age 1, like picking them by hand.</li>
 *   <li>Sugar cane: at least 2 tall; everything above the bottom block is taken.</li>
 *   <li>Pumpkins and melons: only fruit with a stem attached, so placed pumpkins are left alone.</li>
 * </ul>
 */
final class Crops {
	private static final Set<Block> COLUMN_PLANTS = Set.of(Blocks.SUGAR_CANE);
	private static final Set<Block> GOURDS = Set.of(Blocks.PUMPKIN, Blocks.MELON);

	private Crops() {
	}

	/**
	 * For column plants this is only true of the second block, so each column is one target and the
	 * farmer stands next to its base.
	 */
	static boolean isHarvestable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (block instanceof SweetBerryBushBlock) {
			return state.getValue(SweetBerryBushBlock.AGE) == SweetBerryBushBlock.MAX_AGE;
		}
		if (block instanceof CocoaBlock) {
			return state.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE;
		}
		if (block instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE;
		}
		if (COLUMN_PLANTS.contains(block)) {
			return level.getBlockState(pos.below()).is(block) && !level.getBlockState(pos.below(2)).is(block);
		}
		if (GOURDS.contains(block)) {
			return hasAttachedStem(level, pos);
		}
		return false;
	}

	/** Harvests the plant at {@code pos} into the stock. Returns false if it wasn't ready. */
	static boolean harvest(ServerLevel level, BlockPos pos, SimpleContainer stock) {
		if (!isHarvestable(level, pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));

		if (COLUMN_PLANTS.contains(block)) {
			// Top down, so no segment is left floating and breaks into a dropped item.
			BlockPos top = pos;
			while (level.getBlockState(top.above()).is(block)) {
				top = top.above();
			}
			for (BlockPos segment = top; segment.getY() >= pos.getY(); segment = segment.below()) {
				store(level, segment, level.getBlockState(segment), stock);
				level.setBlock(segment, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			return true;
		}

		store(level, pos, state, stock);
		level.setBlock(pos, regrown(state), Block.UPDATE_ALL);
		return true;
	}

	private static BlockState regrown(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.getStateForAge(0);
		}
		if (block instanceof SweetBerryBushBlock) {
			return state.setValue(SweetBerryBushBlock.AGE, 1);
		}
		if (block instanceof CocoaBlock) {
			return state.setValue(CocoaBlock.AGE, 0);
		}
		if (block instanceof NetherWartBlock) {
			return state.setValue(NetherWartBlock.AGE, 0);
		}
		// Gourds: the stem stays and grows a new one.
		return Blocks.AIR.defaultBlockState();
	}

	private static void store(ServerLevel level, BlockPos pos, BlockState state, SimpleContainer stock) {
		// Seeds and poisonous potatoes are thrown away through #hamlet:villager_discards.
		Block.getDrops(state, level, pos, null).forEach(drop -> ProfessionWork.store(stock, drop));
	}

	private static boolean hasAttachedStem(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighbour = level.getBlockState(pos.relative(direction));
			if (neighbour.getBlock() instanceof AttachedStemBlock
				&& neighbour.getValue(AttachedStemBlock.FACING) == direction.getOpposite()) {
				return true;
			}
		}
		return false;
	}
}
