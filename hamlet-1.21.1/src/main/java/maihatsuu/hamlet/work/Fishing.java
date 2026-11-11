package maihatsuu.hamlet.work;

import maihatsuu.hamlet.entity.FishingBobber;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import maihatsuu.hamlet.Hamlet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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

/**
 * A fisherman's catches, rolled from the same {@code gameplay/fishing} loot table a player's hook uses, so
 * datapacks and mods that change fishing loot apply to villagers too.
 */
final class Fishing {
	private Fishing() {
	}

	/**
	 * Rolls one catch for {@code bobber} and stores it. Open water is judged at {@code water}, the block it
	 * was cast to; FishingHookPredicateMixin lets the table's open-water conditions see that.
	 */
	/** Junk nobody would buy, so fishermen throw it away rather than filling their stock. */
	private static final TagKey<Item> DISCARDS = TagKey.create(Registries.ITEM, Hamlet.id("fisherman_discards"));

	static void catchInto(ServerLevel level, FishingBobber bobber, BlockPos water, SimpleContainer stock) {
		bobber.setOpenWaterFishing(isOpenWater(level, water));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
		// The same parameters as FishingHook.retrieve, minus the player's luck.
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, bobber.position())
			.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
			.withParameter(LootContextParams.THIS_ENTITY, bobber)
			.create(LootContextParamSets.FISHING);
		table.getRandomItems(params).stream()
			.filter(stack -> !isDiscarded(stack))
			.forEach(stock::addItem);
	}

	// Enchanted items are treasure even when the junk table has a plain one, like the fishing rod.
	private static boolean isDiscarded(ItemStack stack) {
		return stack.is(DISCARDS) && !stack.isEnchanted();
	}

	/** A water source with air above, where a bobber can float. */
	static boolean isSurface(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && isWaterSource(level.getFluidState(pos)) && level.getBlockState(pos.above()).isAir();
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
