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
 *   nothing it can copy or the copy is capped in stock.</li>
 *   <li>Cleric: a random potion from {@link PriceLoader#clericPotions}.</li>
 *   <li>Mason: faster, a batch of {@link #MASON_BATCH} of a random block from {@link PriceLoader#masonBlocks}
 *   every minute.</li>
 * </ul>
 * Like all work output, each kind of item stops at {@link #capOf its cap}: 3 stacks.
 */
public final class ProfessionWork extends WorkAtPoi {
	/** Work time, at the workstation, between items: about 5 minutes. */
	private static final long TICKS_PER_ITEM = 6000L;
	/** The mason's work time between batches: about a minute. */
	private static final long TICKS_PER_MASON_BATCH = 1200L;
	private static final int MASON_BATCH = 8;
	/** How many stacks of each kind of item a villager keeps in stock. */
	private static final int STACKS_PER_ITEM = 3;
	/**
	 * Visits come about every 15 seconds while at the workstation, so the time since the last visit counts
	 * as work up to this much; more means the villager was away from it, or off work.
	 */
	private static final long MAX_TICKS_PER_VISIT = 600L;

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
		if (this.workTicks < (this.profession == VillagerProfession.MASON ? TICKS_PER_MASON_BATCH : TICKS_PER_ITEM)) {
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
			|| profession == VillagerProfession.CLERIC
			|| profession == VillagerProfession.MASON;
	}

	/** Makes one item into the stock, if there's one to make now. */
	private boolean produce(ServerLevel level, SimpleContainer stock, BlockPos site) {
		if (this.profession == VillagerProfession.CARTOGRAPHER) {
			return drawMap(level, stock, site);
		}
		if (this.profession == VillagerProfession.LIBRARIAN) {
			// With the lectern's book capped in stock, it makes plain books instead until some sell.
			return storeUnlessCapped(stock, copyBook(level, site)) || storeUnlessCapped(stock, new ItemStack(Items.BOOK));
		}
		if (this.profession == VillagerProfession.MASON) {
			return cutBlocks(level, stock);
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
			if (!isCapped(stock, stack)) {
				choices.add(stack);
			}
		}
		return !choices.isEmpty() && storeUnlessCapped(stock, Util.getRandom(choices, level.random));
	}

	/** A batch of a random block from the mason's list that the stock doesn't already have enough of. */
	private static boolean cutBlocks(ServerLevel level, SimpleContainer stock) {
		List<ItemStack> choices = new ArrayList<>();
		for (Item block : PriceLoader.masonBlocks()) {
			ItemStack batch = new ItemStack(block, MASON_BATCH);
			if (!isCapped(stock, batch)) {
				choices.add(batch);
			}
		}
		if (choices.isEmpty()) {
			return false;
		}
		store(stock, Util.getRandom(choices, level.random));
		return true;
	}

	private static boolean storeUnlessCapped(SimpleContainer stock, ItemStack stack) {
		if (isCapped(stock, stack)) {
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
	static final TagKey<Item> DISCARDS = TagKey.create(Registries.ITEM, Hamlet.id("villager_discards"));

	/**
	 * Puts work output into the stock, unless it's discarded, keeping no more than {@link #capOf the cap} of
	 * each kind; the rest is thrown away. All work output goes through here. Work that can tell in advance,
	 * such as harvesting, skips capped output instead, so only random output like fishing loot is lost.
	 */
	static void store(SimpleContainer stock, ItemStack stack) {
		if (stack.is(DISCARDS)) {
			return;
		}
		int room = capOf(stack) - countInStock(stock, stack);
		if (room > 0) {
			stock.addItem(stack.copyWithCount(Math.min(room, stack.getCount())));
		}
	}

	/**
	 * Like {@link #store}, for output picked up off the ground: stores what fits under the cap and returns the
	 * rest, which stays on the ground.
	 */
	public static ItemStack storePickedUp(SimpleContainer stock, ItemStack stack) {
		int room = Math.max(0, capOf(stack) - countInStock(stock, stack));
		ItemStack taken = stack.copyWithCount(Math.min(room, stack.getCount()));
		ItemStack rest = stack.copyWithCount(stack.getCount() - taken.getCount());
		if (!taken.isEmpty()) {
			rest.grow(stock.addItem(taken).getCount());
		}
		return rest;
	}

	/** Whether {@code stack}'s kind is capped in the stock; public for pickups. */
	public static boolean isCappedInStock(SimpleContainer stock, ItemStack stack) {
		return isCapped(stock, stack);
	}

	/**
	 * How much of one kind of item, by item and components, a villager keeps in stock: 3 stacks, so 192
	 * wheat but 3 milk buckets or potions. Cartographers keep one of each map anyway.
	 */
	static int capOf(ItemStack stack) {
		return STACKS_PER_ITEM * stack.getMaxStackSize();
	}

	/** Whether the stock already holds the cap of {@code stack}'s kind, so more would be thrown away. */
	static boolean isCapped(SimpleContainer stock, ItemStack stack) {
		return countInStock(stock, stack) >= capOf(stack);
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
