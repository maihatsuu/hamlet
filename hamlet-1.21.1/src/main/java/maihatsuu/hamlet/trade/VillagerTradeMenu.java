package maihatsuu.hamlet.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.data.PriceLoader;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Trades like vanilla's merchant screen. Clicking an offer row ({@link #clickMenuButton}, with the row's
 * index) selects it and moves its cost from the player's inventory into the two payment slots; the result
 * slot then shows what the payment buys, and taking it makes the trade. Shift-clicking the result repeats it.
 * <p>
 * The villager's stock is only ever sold through offers. Its slots are kept in the menu, hidden, just so
 * vanilla syncs them to the client to build the rows. Its storage is shown as ordinary slots.
 */
public class VillagerTradeMenu extends AbstractContainerMenu {
	public static final int STOCK_SIZE = HamletVillager.STOCK_SIZE;
	public static final int STORAGE_SIZE = HamletVillager.STORAGE_SIZE;
	private static final double MAX_DISTANCE_SQR = 64.0;
	private static final ResourceLocation EMPTY_SLOT_TEMPLATE = ResourceLocation.withDefaultNamespace("item/empty_slot_smithing_template_armor_trim");
	private static final ResourceLocation EMPTY_SLOT_ENCHANTED_BOOK = Hamlet.id("item/empty_slot_enchanted_book");

	// Slot indices.
	private static final int STORAGE_START = STOCK_SIZE;
	private static final int PAYMENT_START = STORAGE_START + STORAGE_SIZE;
	private static final int RESULT_SLOT = PAYMENT_START + 2;
	private static final int PLAYER_START = RESULT_SLOT + 1;
	private static final int HOTBAR_START = PLAYER_START + 27;
	private static final int PLAYER_END = HOTBAR_START + 9;

	// Positions, matching the texture.
	private static final int INVENTORIES_X = 108;
	private static final int STORAGE_Y = 18;
	private static final int PAYMENT_A_X = 136;
	private static final int PAYMENT_B_X = 162;
	private static final int RESULT_X = 220;
	private static final int TRADE_Y = 47;
	private static final int PLAYER_INVENTORY_Y = 84;
	private static final int HOTBAR_Y = 142;
	/** Stock slots aren't shown; they sit off the screen and are inactive. */
	private static final int HIDDEN = -10000;

	public static final MenuType<VillagerTradeMenu> TYPE = Registry.register(
		BuiltInRegistries.MENU,
		Hamlet.id("villager_trade"),
		new ExtendedScreenHandlerType<>(VillagerTradeMenu::new, TradeOpeningData.STREAM_CODEC)
	);

	/** Null on the client. */
	@Nullable
	private final Villager villager;
	private final Player player;
	private final SimpleContainer stock;
	private final SimpleContainer payments = new SimpleContainer(2);
	private final SimpleContainer result = new SimpleContainer(1);
	private final Map<Item, Integer> prices;
	/** The template, book or experience service, if the profession has one. */
	@Nullable
	private final Service service;
	/** Server only: the offer being paid for. */
	@Nullable
	private Offer selected;

	public VillagerTradeMenu(int containerId, Inventory playerInventory, TradeOpeningData data) {
		this(containerId, playerInventory, new SimpleContainer(STOCK_SIZE), new SimpleContainer(STORAGE_SIZE),
			data.prices(), data.profession(), null);
	}

	private VillagerTradeMenu(
		int containerId,
		Inventory playerInventory,
		SimpleContainer stock,
		SimpleContainer storage,
		Map<Item, Integer> prices,
		VillagerProfession profession,
		@Nullable Villager villager
	) {
		super(TYPE, containerId);
		this.player = playerInventory.player;
		this.stock = stock;
		this.prices = prices;
		this.villager = villager;
		this.service = Service.forProfession(profession);
		this.payments.addListener(this::slotsChanged);

		for (int slot = 0; slot < STOCK_SIZE; slot++) {
			this.addSlot(new StockSlot(stock, slot));
		}
		for (int column = 0; column < STORAGE_SIZE; column++) {
			this.addSlot(new Slot(storage, column, INVENTORIES_X + column * 18, STORAGE_Y));
		}
		this.addSlot(new Slot(this.payments, 0, PAYMENT_A_X, TRADE_Y));
		this.addSlot(new Slot(this.payments, 1, PAYMENT_B_X, TRADE_Y));
		this.addSlot(new ResultSlot(this.result, RESULT_X, TRADE_Y));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(playerInventory, column + row * 9 + 9, INVENTORIES_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(playerInventory, column, INVENTORIES_X + column * 18, HOTBAR_Y));
		}
	}

	/** Forces the menu type's static registration to run during mod initialization. */
	public static void init() {
	}

	/** Claims the villager for this player and opens the screen. */
	public static void open(Villager villager, Player player) {
		VillagerProfession profession = villager.getVillagerData().getProfession();
		Map<Item, Integer> prices = PriceLoader.prices();
		villager.setTradingPlayer(player);
		player.openMenu(new ExtendedScreenHandlerFactory<TradeOpeningData>() {
			@Override
			public TradeOpeningData getScreenOpeningData(ServerPlayer serverPlayer) {
				return new TradeOpeningData(prices, profession);
			}

			@Override
			public Component getDisplayName() {
				Component name = villager.getDisplayName();
				return WorkRequirements.missing(villager)
					.<Component>map(reason -> Component.empty()
						.append(name)
						.append(" ")
						.append(Component.translatable("hamlet.not_working.title", reason).withStyle(ChatFormatting.RED)))
					.orElse(name);
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
				HamletVillager hamlet = (HamletVillager) villager;
				return new VillagerTradeMenu(containerId, inventory, hamlet.hamlet$getStock(), hamlet.hamlet$getStorage(),
					prices, profession, villager);
			}
		});
	}

	public int priceOf(Item item) {
		return PriceLoader.priceOf(this.prices, item);
	}

	/**
	 * Every offer, services first, then one row per kind of item in stock. Items in the payment slots count
	 * as the player's, so selecting an offer (which moves its cost there) doesn't change the list.
	 */
	public List<Offer> offers() {
		List<Offer> offers = new ArrayList<>();
		Inventory inventory = this.player.getInventory();
		int emeralds = inventory.countItem(Items.EMERALD) + this.paymentCount(Items.EMERALD);

		Service other = this.service;
		if (other == Service.BOTTLE_EXPERIENCE) {
			boolean available = emeralds >= other.cost && hasLevelToBottle(this.player);
			offers.add(new Offer(Offer.Kind.EXPERIENCE, emeralds(other.cost), ItemStack.EMPTY, new ItemStack(Items.EXPERIENCE_BOTTLE),
				-1, available, Component.translatable("hamlet.service.costs_level"), null));
		} else if (other != null && other.input != null) {
			// A row for each kind of template or book the player has, so they choose which to copy.
			List<ItemStack> candidates = new ArrayList<>();
			candidates.add(this.payments.getItem(0));
			for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
				candidates.add(inventory.getItem(slot));
			}
			List<ItemStack> seen = new ArrayList<>();
			for (ItemStack stack : candidates) {
				if (!other.input.test(stack) || seen.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
					continue;
				}
				seen.add(stack);
				offers.add(new Offer(Offer.Kind.COPY, stack.copyWithCount(1), emeralds(other.cost), stack.copyWithCount(1),
					-1, emeralds >= other.cost, null, null));
			}
			if (seen.isEmpty()) {
				boolean template = other == Service.DUPLICATE_TEMPLATE;
				Component bring = Component.translatable(template ? "hamlet.service.bring_template" : "hamlet.service.bring_enchanted_book");
				offers.add(new Offer(Offer.Kind.COPY, ItemStack.EMPTY, emeralds(other.cost), ItemStack.EMPTY,
					-1, false, bring, template ? EMPTY_SLOT_TEMPLATE : EMPTY_SLOT_ENCHANTED_BOOK));
			}
		}

		List<ItemStack> listed = new ArrayList<>();
		for (int slot = 0; slot < this.stock.getContainerSize(); slot++) {
			ItemStack stack = this.stock.getItem(slot);
			if (stack.isEmpty() || listed.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
				continue;
			}
			listed.add(stack);
			int price = this.priceOf(stack.getItem());
			int maxStack = Items.EMERALD.getDefaultMaxStackSize();
			offers.add(new Offer(Offer.Kind.STOCK, emeralds(Math.min(price, maxStack)),
				price > maxStack ? emeralds(price - maxStack) : ItemStack.EMPTY, stack.copyWithCount(1),
				this.countInStock(stack), emeralds >= price, null, null));
		}
		return offers;
	}

	private int paymentCount(Item item) {
		int count = 0;
		for (int slot = 0; slot < this.payments.getContainerSize(); slot++) {
			ItemStack stack = this.payments.getItem(slot);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private int countInStock(ItemStack kind) {
		int count = 0;
		for (int slot = 0; slot < this.stock.getContainerSize(); slot++) {
			ItemStack stack = this.stock.getItem(slot);
			if (ItemStack.isSameItemSameComponents(stack, kind)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static ItemStack emeralds(int count) {
		return new ItemStack(Items.EMERALD, count);
	}

	public static boolean hasLevelToBottle(Player player) {
		return player.experienceLevel >= 1 || player.getAbilities().instabuild;
	}

	/** Selects the offer at that row: the old payment goes back to the player, and the new cost comes out. */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.villager == null) {
			return false;
		}
		List<Offer> offers = this.offers();
		if (id < 0 || id >= offers.size() || !offers.get(id).selectable()) {
			return false;
		}
		Offer offer = offers.get(id);

		for (int slot = 0; slot < this.payments.getContainerSize(); slot++) {
			ItemStack stack = this.payments.getItem(slot);
			if (!stack.isEmpty()) {
				this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true);
				this.payments.setItem(slot, stack);
			}
		}
		this.selected = offer;
		if (this.payments.isEmpty()) {
			this.moveFromInventoryToPayment(0, offer.costA());
			this.moveFromInventoryToPayment(1, offer.costB());
		}
		this.updateResult();
		return true;
	}

	/** Like vanilla's {@code MerchantMenu.moveFromInventoryToPaymentSlot}: fills the slot up to a stack. */
	private void moveFromInventoryToPayment(int paymentSlot, ItemStack cost) {
		if (cost.isEmpty()) {
			return;
		}
		for (int index = PLAYER_START; index < PLAYER_END; index++) {
			ItemStack stack = this.slots.get(index).getItem();
			if (!ItemStack.isSameItemSameComponents(stack, cost)) {
				continue;
			}
			ItemStack payment = this.payments.getItem(paymentSlot);
			if (payment.isEmpty() || ItemStack.isSameItemSameComponents(stack, payment)) {
				int max = stack.getMaxStackSize();
				int moved = Math.min(max - payment.getCount(), stack.getCount());
				ItemStack filled = stack.copyWithCount(payment.getCount() + moved);
				stack.shrink(moved);
				this.payments.setItem(paymentSlot, filled);
				if (filled.getCount() >= max) {
					break;
				}
			}
		}
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == this.payments) {
			this.updateResult();
		}
	}

	/** Server only: shows the selected offer's result in the result slot if the payment covers it. */
	private void updateResult() {
		if (this.villager == null) {
			return;
		}
		Offer offer = this.selected;
		ItemStack shown = ItemStack.EMPTY;
		if (offer != null && this.isPaid(offer)) {
			shown = switch (offer.kind()) {
				case STOCK -> this.stockSlotOf(offer.result()) >= 0 ? offer.result().copy() : ItemStack.EMPTY;
				case EXPERIENCE -> hasLevelToBottle(this.player) ? offer.result().copy() : ItemStack.EMPTY;
				// The original stays in the payment slot, so this works for books, which don't stack.
				case COPY -> this.payments.getItem(0).copyWithCount(1);
			};
		}
		this.result.setItem(0, shown);
	}

	/** Whether the payment slots hold the cost. A copy takes any book or template the service accepts. */
	private boolean isPaid(Offer offer) {
		ItemStack first = this.payments.getItem(0);
		ItemStack second = this.payments.getItem(1);
		if (offer.kind() == Offer.Kind.COPY) {
			return this.service != null && this.service.input != null && this.service.input.test(first)
				&& second.is(Items.EMERALD) && second.getCount() >= offer.costB().getCount();
		}
		return covers(first, offer.costA()) && covers(second, offer.costB());
	}

	private static boolean covers(ItemStack payment, ItemStack cost) {
		return cost.isEmpty() || ItemStack.isSameItemSameComponents(payment, cost) && payment.getCount() >= cost.getCount();
	}

	private int stockSlotOf(ItemStack kind) {
		for (int slot = 0; slot < this.stock.getContainerSize(); slot++) {
			if (ItemStack.isSameItemSameComponents(this.stock.getItem(slot), kind)) {
				return slot;
			}
		}
		return -1;
	}

	/** Takes the payment and anything else the trade costs, once the result has been taken. Server only. */
	private void completeTrade(Player player) {
		Offer offer = this.selected;
		if (offer == null || this.villager == null) {
			return;
		}
		if (offer.kind() != Offer.Kind.COPY) {
			this.payments.getItem(0).shrink(offer.costA().getCount());
		}
		this.payments.getItem(1).shrink(offer.costB().getCount());
		switch (offer.kind()) {
			case STOCK -> {
				int slot = this.stockSlotOf(offer.result());
				if (slot >= 0) {
					this.stock.removeItem(slot, 1);
				}
			}
			case EXPERIENCE -> player.giveExperienceLevels(-1);
			case COPY -> {
			}
		}
		player.awardStat(Stats.TRADED_WITH_VILLAGER);
		this.villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
		this.payments.setChanged();
	}

	/**
	 * Shift-click: the result into the player's inventory (vanilla repeats this while there's a result to
	 * take), storage and payment back to the player, and the player's items into storage, else between
	 * their inventory and hotbar.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved;
		if (index == RESULT_SLOT) {
			moved = this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true);
			if (moved) {
				slot.onQuickCraft(stack, original);
			}
		} else if (index < PLAYER_START) {
			moved = this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true);
		} else if (this.moveItemStackTo(stack, STORAGE_START, PAYMENT_START, false)) {
			moved = true;
		} else {
			moved = index < HOTBAR_START
				? this.moveItemStackTo(stack, HOTBAR_START, PLAYER_END, false)
				: this.moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false);
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot slot) {
		return slot.container != this.result && super.canTakeItemForPickAll(carried, slot);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.villager == null
			|| this.villager.isAlive()
				&& this.villager.getTradingPlayer() == player
				&& this.villager.distanceToSqr(player) <= MAX_DISTANCE_SQR;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (this.villager != null) {
			this.clearContainer(player, this.payments);
			if (this.villager.getTradingPlayer() == player) {
				this.villager.setTradingPlayer(null);
			}
		}
	}

	/** The villager's stock: synced so the client can list it, but never shown or clicked. */
	private static class StockSlot extends Slot {
		StockSlot(SimpleContainer container, int slot) {
			super(container, slot, HIDDEN, HIDDEN);
		}

		@Override
		public boolean isActive() {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}

	/** Shows what the payment buys; taking it makes the trade. */
	private class ResultSlot extends Slot {
		ResultSlot(Container container, int x, int y) {
			super(container, 0, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public void onTake(Player player, ItemStack stack) {
			VillagerTradeMenu.this.completeTrade(player);
			super.onTake(player, stack);
		}
	}
}
