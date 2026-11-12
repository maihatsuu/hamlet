package maihatsuu.hamlet.work;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.Hamlet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The explorer maps a cartographer draws into its stock, made the way vanilla's cartographer trades make them.
 * <p>
 * Each kind has its own item texture, picked by the {@code hamlet:explorer_map} item property in
 * {@code assets/minecraft/models/item/filled_map.json} from this order: add new kinds at the end, and add
 * their overrides there too.
 */
public enum ExplorerMap {
	ANCIENT_CITY(hamletTag("on_ancient_city_maps"), "filled_map.hamlet.ancient_city", HamletMapDecorations.ANCIENT_CITY),
	WOODLAND_MANSION(StructureTags.ON_WOODLAND_EXPLORER_MAPS, "filled_map.mansion", HamletMapDecorations.WOODLAND_MANSION),
	DESERT_PYRAMID(hamletTag("on_desert_pyramid_maps"), "filled_map.hamlet.desert_pyramid", HamletMapDecorations.DESERT_PYRAMID),
	OCEAN_MONUMENT(StructureTags.ON_OCEAN_EXPLORER_MAPS, "filled_map.monument", MapDecorationTypes.OCEAN_MONUMENT),
	TRIAL_CHAMBERS(StructureTags.ON_TRIAL_CHAMBERS_MAPS, "filled_map.trial_chambers", MapDecorationTypes.TRIAL_CHAMBERS),
	JUNGLE_TEMPLE(hamletTag("on_jungle_temple_maps"), "filled_map.explorer_jungle", MapDecorationTypes.JUNGLE_TEMPLE),
	SWAMP_HUT(hamletTag("on_swamp_hut_maps"), "filled_map.explorer_swamp", MapDecorationTypes.SWAMP_HUT),
	MINESHAFT(hamletTag("on_mineshaft_maps"), "filled_map.hamlet.mineshaft", HamletMapDecorations.MINESHAFT),
	WARM_OCEAN_RUINS(hamletTag("on_warm_ocean_ruins_maps"), "filled_map.hamlet.warm_ocean_ruins", HamletMapDecorations.WARM_OCEAN_RUINS);

	private final TagKey<Structure> structure;
	private final String name;
	private final Holder<MapDecorationType> decoration;

	ExplorerMap(TagKey<Structure> structure, String name, Holder<MapDecorationType> decoration) {
		this.structure = structure;
		this.name = name;
		this.decoration = decoration;
	}

	/**
	 * Structure tags for the maps vanilla doesn't have, so datapacks can point them at other structures.
	 * Jungle temples and swamp huts get one too: vanilla's tags for them are only filled by the experimental
	 * trade rebalance datapack, so they're empty in normal worlds.
	 */
	private static TagKey<Structure> hamletTag(String name) {
		return TagKey.create(Registries.STRUCTURE, Hamlet.id(name));
	}

	/** Which kind of explorer map {@code stack} is, by its name; null if it isn't one. */
	@Nullable
	public static ExplorerMap of(ItemStack stack) {
		for (ExplorerMap kind : values()) {
			if (kind.isMap(stack)) {
				return kind;
			}
		}
		return null;
	}

	/** Whether {@code stack} is one of these maps, by its name. */
	public boolean isMap(ItemStack stack) {
		return stack.is(Items.FILLED_MAP) && Component.translatable(this.name).equals(stack.get(DataComponents.ITEM_NAME));
	}

	/**
	 * A map to the nearest such structure that no map points to yet, which it then points to; empty if
	 * there's none within range or a search would have to wait.
	 */
	public Optional<ItemStack> draw(ServerLevel level, BlockPos from) {
		return MapSearch.get(level).find(level, this.structure, from).map(pos -> {
			ItemStack stack = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 2, true, true);
			MapItem.renderBiomePreviewMap(level, stack);
			MapItemSavedData.addTargetDecoration(stack, pos, "+", this.decoration);
			stack.set(DataComponents.ITEM_NAME, Component.translatable(this.name));
			return stack;
		});
	}
}
