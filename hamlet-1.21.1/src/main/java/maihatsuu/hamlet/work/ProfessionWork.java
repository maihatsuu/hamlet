package maihatsuu.hamlet.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.data.PriceLoader;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * Vanilla's walk-to-workstation behaviour, for professions that don't have their own work behaviour. It skips
 * vanilla's restocking. While working at its workstation, a villager makes one item about every 5 minutes:
 * <ul>
 *   <li>Cartographer: an explorer map, keeping one of each kind in stock.</li>
 *   <li>Librarian: a copy of the written or enchanted book on its lectern, or a plain book when there's
 *   nothing it can copy.</li>
 *   <li>Cleric: a random potion from {@link PriceLoader#clericPotions}.</li>
 * </ul>
 * Librarians and clerics keep up to {@link #MAX_OF_EACH} of each item, since books and potions don't stack
 * and would otherwise fill the stock with one kind.
 */
public final class ProfessionWork extends WorkAtPoi {
	/** Work time, at the workstation, between items: about 5 minutes. */
	private static final long TICKS_PER_ITEM = 6000L;
	/**
	 * Visits come about every 15 seconds while at the workstation, so the time since the last visit counts
	 * as work up to this much; more means the villager was away from it, or off work.
	 */
	private static final long MAX_TICKS_PER_VISIT = 600L;
	/** How many of each book or potion a librarian or cleric keeps in stock. */
	private static final int MAX_OF_EACH = 3;

	private final VillagerProfession profession;
	/** Work time towards the next item. Not saved, so a reload restarts the current one. */
	private long workTicks;
	private long lastVisit = Long.MIN_VALUE / 2;

	public ProfessionWork(VillagerProfession profession) {
		this.profession = profession;
	}

	@Override
	protected void useWorkstation(ServerLevel level, Villager villager) {
		if (!makesItems(this.profession) || WorkRequirements.missing(villager).isPresent()) {
			return;
		}
		long now = level.getGameTime();
		this.workTicks += Math.max(0L, Math.min(now - this.lastVisit, MAX_TICKS_PER_VISIT));
		this.lastVisit = now;
		if (this.workTicks < TICKS_PER_ITEM) {
			return;
		}
		SimpleContainer stock = ((HamletVillager) villager).hamlet$getStock();
		if (isFull(stock)) {
			return;
		}
		villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == level.dimension())
			.ifPresent(site -> {
				if (this.produce(level, stock, site.pos())) {
					this.workTicks = 0L;
				}
			});
	}

	private static boolean makesItems(VillagerProfession profession) {
		return profession == VillagerProfession.CARTOGRAPHER
			|| profession == VillagerProfession.LIBRARIAN
			|| profession == VillagerProfession.CLERIC;
	}

	/** Makes one item into the stock, if there's one to make now. */
	private boolean produce(ServerLevel level, SimpleContainer stock, BlockPos site) {
		if (this.profession == VillagerProfession.CARTOGRAPHER) {
			return drawMap(level, stock, site);
		}
		if (this.profession == VillagerProfession.LIBRARIAN) {
			return storeUnlessCapped(stock, copyBook(level, site));
		}
		return brewPotion(level, stock);
	}

	/**
	 * What a librarian makes at its lectern: a copy of an enchanted book on it, a copy of a written book that
	 * can still be copied (one generation further, as vanilla's copying does; copies of copies can't be), or
	 * otherwise a plain book.
	 */
	private static ItemStack copyBook(ServerLevel level, BlockPos site) {
		ItemStack book = level.getBlockEntity(site) instanceof LecternBlockEntity lectern ? lectern.getBook() : ItemStack.EMPTY;
		if (book.is(Items.ENCHANTED_BOOK)) {
			return book.copyWithCount(1);
		}
		WrittenBookContent content = book.is(Items.WRITTEN_BOOK) ? book.get(DataComponents.WRITTEN_BOOK_CONTENT) : null;
		WrittenBookContent copied = content == null ? null : content.tryCraftCopy();
		if (copied != null) {
			ItemStack copy = book.copyWithCount(1);
			copy.set(DataComponents.WRITTEN_BOOK_CONTENT, copied);
			return copy;
		}
		return new ItemStack(Items.BOOK);
	}

	/** A random potion from the cleric's list that the stock doesn't already have enough of. */
	private static boolean brewPotion(ServerLevel level, SimpleContainer stock) {
		List<ItemStack> choices = new ArrayList<>();
		for (Holder<Potion> potion : PriceLoader.clericPotions()) {
			ItemStack stack = PotionContents.createItemStack(Items.POTION, potion);
			if (countInStock(stock, stack) < MAX_OF_EACH) {
				choices.add(stack);
			}
		}
		return !choices.isEmpty() && storeUnlessCapped(stock, Util.getRandom(choices, level.random));
	}

	private static boolean storeUnlessCapped(SimpleContainer stock, ItemStack stack) {
		if (countInStock(stock, stack) >= MAX_OF_EACH) {
			return false;
		}
		store(stock, stack);
		return true;
	}

	private static int countInStock(SimpleContainer stock, ItemStack kind) {
		int count = 0;
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			ItemStack stack = stock.getItem(slot);
			if (ItemStack.isSameItemSameComponents(stack, kind)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/**
	 * Draws one map of a random kind missing from the stock, if one can be charted now. Searching charts the
	 * structure, so this only searches when the map can be stored.
	 * <p>
	 * Kinds are tried in random order, so one that can't be found doesn't hold up the rest. This runs at
	 * most one real search: after one, the rate limit makes the rest return at once, as do kinds that
	 * recently found nothing.
	 */
	private static boolean drawMap(ServerLevel level, SimpleContainer stock, BlockPos from) {
		List<ExplorerMap> missing = new ArrayList<>();
		for (ExplorerMap kind : ExplorerMap.values()) {
			if (!inStock(stock, kind)) {
				missing.add(kind);
			}
		}
		Util.shuffle(missing, level.random);
		for (ExplorerMap kind : missing) {
			Optional<ItemStack> map = kind.draw(level, from);
			if (map.isPresent()) {
				store(stock, map.get());
				return true;
			}
		}
		return false;
	}

	private static boolean inStock(SimpleContainer stock, ExplorerMap kind) {
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			if (kind.isMap(stock.getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Work output nobody would buy, such as seeds from a harvest: thrown away rather than filling the stock.
	 * Datapacks can add to it to stop villagers gathering anything else.
	 */
	private static final TagKey<Item> DISCARDS = TagKey.create(Registries.ITEM, Hamlet.id("villager_discards"));

	/** Puts work output into the stock, unless it's discarded. All work output goes through here. */
	static void store(SimpleContainer stock, ItemStack stack) {
		if (!stack.is(DISCARDS)) {
			stock.addItem(stack);
		}
	}

	static boolean isFull(SimpleContainer stock) {
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			if (stock.getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
