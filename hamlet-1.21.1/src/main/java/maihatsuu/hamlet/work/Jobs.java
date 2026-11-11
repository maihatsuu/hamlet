package maihatsuu.hamlet.work;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** What each working profession does when it uses its workstation. */
final class Jobs {
	// Farmers, fishermen, shepherds and beekeepers aren't here: they have their own behaviours (FarmCrops,
	// GoFishing, TendAnimals and TendHives).
	private static final Map<VillagerProfession, Job> JOBS = Map.of(
		VillagerProfession.LIBRARIAN, Jobs::copyBook,
		VillagerProfession.CARTOGRAPHER, Jobs::makeMap
	);

	private static final List<ExplorerMap> EXPLORER_MAPS = List.of(
		new ExplorerMap(StructureTags.ON_OCEAN_EXPLORER_MAPS, "filled_map.monument", MapDecorationTypes.OCEAN_MONUMENT),
		new ExplorerMap(StructureTags.ON_WOODLAND_EXPLORER_MAPS, "filled_map.mansion", MapDecorationTypes.WOODLAND_MANSION),
		new ExplorerMap(StructureTags.ON_TRIAL_CHAMBERS_MAPS, "filled_map.trial_chambers", MapDecorationTypes.TRIAL_CHAMBERS)
	);

	private Jobs() {
	}

	@Nullable
	static Job forProfession(VillagerProfession profession) {
		return JOBS.get(profession);
	}

	/** Keeps one of each explorer map in stock, so buying one is what prompts the next. */
	private static boolean makeMap(ServerLevel level, BlockPos site, SimpleContainer stock) {
		MapTargetCache cache = MapTargetCache.get(level);
		for (ExplorerMap map : EXPLORER_MAPS) {
			if (hasMap(stock, map.name())) {
				continue;
			}

			Optional<BlockPos> target = cache.find(level, map.structure(), site);
			if (target.isPresent()) {
				BlockPos pos = target.get();
				ItemStack stack = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 2, true, true);
				MapItem.renderBiomePreviewMap(level, stack);
				MapItemSavedData.addTargetDecoration(stack, pos, "+", map.decoration());
				stack.set(DataComponents.ITEM_NAME, Component.translatable(map.name()));
				stock.addItem(stack);
				return true;
			}
		}
		return false;
	}

	private static boolean hasMap(SimpleContainer stock, String name) {
		Component mapName = Component.translatable(name);
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			ItemStack stack = stock.getItem(slot);
			if (stack.is(Items.FILLED_MAP) && mapName.equals(stack.get(DataComponents.ITEM_NAME))) {
				return true;
			}
		}
		return false;
	}

	// Archive tomes will be handled here as well, once Archive's API exists.
	private static boolean copyBook(ServerLevel level, BlockPos site, SimpleContainer stock) {
		if (!(level.getBlockEntity(site) instanceof LecternBlockEntity lectern) || !lectern.getBook().is(Items.WRITTEN_BOOK)) {
			return false;
		}

		ItemStack book = lectern.getBook();
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		WrittenBookContent copiedContent = content == null ? null : content.tryCraftCopy();
		if (copiedContent == null) {
			return false;
		}

		ItemStack copy = book.copyWithCount(1);
		copy.set(DataComponents.WRITTEN_BOOK_CONTENT, copiedContent);
		stock.addItem(copy);
		return true;
	}

	private record ExplorerMap(TagKey<Structure> structure, String name, Holder<MapDecorationType> decoration) {
	}
}
