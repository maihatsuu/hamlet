package maihatsuu.hamlet.work;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** A fisherman's catches. Vanilla's treasure check needs a player's hook, so it's ported here. */
final class Fishing {
	// The same weights as vanilla's gameplay/fishing table.
	private static final int JUNK_WEIGHT = 10;
	private static final int TREASURE_WEIGHT = 5;
	private static final int FISH_WEIGHT = 85;

	private Fishing() {
	}

	/** Rolls one catch for a bobber floating at {@code water} and stores it. */
	static void catchInto(ServerLevel level, BlockPos water, SimpleContainer stock) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(rollCatch(level, isOpenWater(level, water)));
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
			.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
			.create(LootContextParamSets.FISHING);
		table.getRandomItems(params).forEach(stock::addItem);
	}

	/** A water source with air above, where a bobber can float. */
	static boolean isSurface(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && isWaterSource(level.getFluidState(pos)) && level.getBlockState(pos.above()).isAir();
	}

	private static ResourceKey<LootTable> rollCatch(ServerLevel level, boolean openWater) {
		int treasureWeight = openWater ? TREASURE_WEIGHT : 0;
		int roll = level.random.nextInt(JUNK_WEIGHT + treasureWeight + FISH_WEIGHT);
		if (roll < JUNK_WEIGHT) {
			return BuiltInLootTables.FISHING_JUNK;
		}
		return roll < JUNK_WEIGHT + treasureWeight ? BuiltInLootTables.FISHING_TREASURE : BuiltInLootTables.FISHING_FISH;
	}

	// Ported from FishingHook.calculateOpenWater, which needs a hook entity.
	static boolean isOpenWater(ServerLevel level, BlockPos pos) {
		WaterLayer previous = WaterLayer.INVALID;
		for (int y = -1; y <= 2; y++) {
			WaterLayer layer = classifyLayer(level, pos.offset(-2, y, -2), pos.offset(2, y, 2));
			if (layer == WaterLayer.INVALID
				|| layer == WaterLayer.ABOVE_WATER && previous == WaterLayer.INVALID
				|| layer == WaterLayer.INSIDE_WATER && previous == WaterLayer.ABOVE_WATER) {
				return false;
			}
			previous = layer;
		}
		return true;
	}

	private static WaterLayer classifyLayer(ServerLevel level, BlockPos from, BlockPos to) {
		WaterLayer result = null;
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			WaterLayer layer = classifyBlock(level, pos);
			if (result != null && layer != result) {
				return WaterLayer.INVALID;
			}
			result = layer;
		}
		return result == null ? WaterLayer.INVALID : result;
	}

	private static WaterLayer classifyBlock(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return WaterLayer.INVALID;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.is(Blocks.LILY_PAD)) {
			return WaterLayer.ABOVE_WATER;
		}
		return isWaterSource(state.getFluidState()) && state.getCollisionShape(level, pos).isEmpty()
			? WaterLayer.INSIDE_WATER
			: WaterLayer.INVALID;
	}

	private static boolean isWaterSource(FluidState fluid) {
		return fluid.is(FluidTags.WATER) && fluid.isSource();
	}

	private enum WaterLayer {
		ABOVE_WATER,
		INSIDE_WATER,
		INVALID
	}
}
