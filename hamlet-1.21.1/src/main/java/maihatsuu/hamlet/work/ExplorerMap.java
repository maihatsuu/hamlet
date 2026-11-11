package maihatsuu.hamlet.work;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** The explorer maps a cartographer sells, made the way vanilla's cartographer trades make them. */
public enum ExplorerMap {
	OCEAN_MONUMENT(StructureTags.ON_OCEAN_EXPLORER_MAPS, "filled_map.monument", MapDecorationTypes.OCEAN_MONUMENT),
	WOODLAND_MANSION(StructureTags.ON_WOODLAND_EXPLORER_MAPS, "filled_map.mansion", MapDecorationTypes.WOODLAND_MANSION),
	TRIAL_CHAMBERS(StructureTags.ON_TRIAL_CHAMBERS_MAPS, "filled_map.trial_chambers", MapDecorationTypes.TRIAL_CHAMBERS);

	private final TagKey<Structure> structure;
	private final String name;
	private final Holder<MapDecorationType> decoration;

	ExplorerMap(TagKey<Structure> structure, String name, Holder<MapDecorationType> decoration) {
		this.structure = structure;
		this.name = name;
		this.decoration = decoration;
	}

	/** The translation key of the map's name, as vanilla's explorer maps use. */
	public String nameKey() {
		return this.name;
	}

	/**
	 * A map to the nearest such structure, or empty if there's none or it hasn't been found yet. Searches
	 * are slow, so they're cached and rate-limited; {@link #searchAhead} fills the cache in advance.
	 */
	public Optional<ItemStack> create(ServerLevel level, BlockPos from) {
		return MapTargetCache.get(level).find(level, this.structure, from).map(pos -> {
			ItemStack stack = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 2, true, true);
			MapItem.renderBiomePreviewMap(level, stack);
			MapItemSavedData.addTargetDecoration(stack, pos, "+", this.decoration);
			stack.set(DataComponents.ITEM_NAME, Component.translatable(this.name));
			return stack;
		});
	}

	/** Looks up any structure not yet cached for {@code from}, at most one search per rate-limit window. */
	static void searchAhead(ServerLevel level, BlockPos from) {
		MapTargetCache cache = MapTargetCache.get(level);
		for (ExplorerMap map : values()) {
			cache.find(level, map.structure, from);
		}
	}
}
