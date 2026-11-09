package maihatsuu.hamlet.trade;

import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.data.ProfessionData;
import maihatsuu.hamlet.data.ProfessionDataLoader;
import maihatsuu.hamlet.villager.HamletVillager;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.item.SmithingTemplateItem;

/**
 * Stock items are shown in real slots so vanilla syncs them, but they can't be taken directly.
 * Buying goes through {@link #clickMenuButton}: button IDs below {@code STOCK_SIZE} buy one item from
 * that slot, {@code STOCK_SIZE + slot} buys as much of the stack as the player can afford, and
 * {@link #SERVICE_BUTTON} performs the villager's service.
 */
public class VillagerTradeMenu extends AbstractContainerMenu {
	private static final int STOCK_SIZE = HamletVillager.STOCK_SIZE;
	public static final int SERVICE_BUTTON = STOCK_SIZE * 2;
	private static final int SERVICE_EMERALD_COST = 1;
	private static final int TEMPLATES_RETURNED = 2;
	private static final double MAX_DISTANCE_SQR = 64.0;
	private static final Predicate<ItemStack> IS_EMERALD = stack -> stack.is(Items.EMERALD);

	public static final MenuType<VillagerTradeMenu> TYPE = Registry.register(
		BuiltInRegistries.MENU,
		Hamlet.id("villager_trade"),
		new ExtendedScreenHandlerType<>(VillagerTradeMenu::new, TradeOpeningData.STREAM_CODEC)
	);

	/** Null on the client. */
	@Nullable
	private final Villager villager;
	private final SimpleContainer stock;
	private final SimpleContainer serviceInput = new SimpleContainer(1);
	private final Map<Item, Integer> prices;
	@Nullable
	private final Service service;

	/** Client-only view state: stock slots are hidden while the Service tab is open. */
	public boolean stockVisible = true;

	public VillagerTradeMenu(int containerId, Inventory playerInventory, TradeOpeningData data) {
		this(containerId, playerInventory, new SimpleContainer(STOCK_SIZE), data.prices(), data.profession(), null);
	}

	private VillagerTradeMenu(
		int containerId,
		Inventory playerInventory,
		SimpleContainer stock,
		Map<Item, Integer> prices,
		VillagerProfession profession,
		@Nullable Villager villager
	) {
		super(TYPE, containerId);
		this.stock = stock;
		this.prices = prices;
		this.service = Service.forProfession(profession);
		this.villager = villager;

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new StockSlot(stock, column + row * 9, 8 + column * 18, 18 + row * 18));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 85 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 143));
		}
		this.addSlot(new ServiceInputSlot(this.serviceInput, 80, 18));
	}

	/** Forces the menu type's static registration to run during mod initialization. */
	public static void init() {
	}

	/** Claims the villager for this player and opens the screen. */
	public static void open(Villager villager, Player player) {
		VillagerProfession profession = villager.getVillagerData().getProfession();
		Map<Item, Integer> prices = ProfessionDataLoader.get(profession).prices();
		villager.setTradingPlayer(player);
		player.openMenu(new ExtendedScreenHandlerFactory<TradeOpeningData>() {
			@Override
			public TradeOpeningData getScreenOpeningData(ServerPlayer serverPlayer) {
				return new TradeOpeningData(prices, profession);
			}

			@Override
			public Component getDisplayName() {
				return villager.getDisplayName();
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
				SimpleContainer stock = ((HamletVillager) villager).hamlet$getStock();
				return new VillagerTradeMenu(containerId, inventory, stock, prices, profession, villager);
			}
		});
	}

	public int priceOf(Item item) {
		return this.prices.getOrDefault(item, ProfessionData.DEFAULT_PRICE);
	}

	@Nullable
	public Service getService() {
		return this.service;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.villager == null) {
			return false;
		}
		if (id == SERVICE_BUTTON) {
			return this.performService(player);
		}
		return id >= 0 && id < SERVICE_BUTTON && this.buy(player, id % STOCK_SIZE, id >= STOCK_SIZE);
	}

	private boolean buy(Player player, int slot, boolean wholeStack) {
		ItemStack stack = this.stock.getItem(slot);
		if (stack.isEmpty()) {
			return false;
		}

		if (!this.prices.containsKey(stack.getItem())) {
			Hamlet.LOGGER.warn("No price set for {}; using the default", BuiltInRegistries.ITEM.getKey(stack.getItem()));
		}

		Inventory inventory = player.getInventory();
		int unitPrice = this.priceOf(stack.getItem());
		int affordable = inventory.countItem(Items.EMERALD) / unitPrice;
		int count = Math.min(wholeStack ? stack.getCount() : 1, affordable);
		if (count == 0) {
			return refuse(player, "hamlet.trade.cannot_afford");
		}

		takeEmeralds(player, count * unitPrice);
		inventory.placeItemBackInInventory(this.stock.removeItem(slot, count));
		this.villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
		return true;
	}

	private boolean performService(Player player) {
		if (this.service == null) {
			return false;
		}
		if (player.getInventory().countItem(Items.EMERALD) < SERVICE_EMERALD_COST) {
			return refuse(player, "hamlet.trade.cannot_afford");
		}

		Inventory inventory = player.getInventory();
		switch (this.service) {
			case DUPLICATE_TEMPLATE -> {
				ItemStack template = this.serviceInput.getItem(0);
				if (!isTemplate(template)) {
					return refuse(player, "hamlet.service.needs_template");
				}
				ItemStack result = template.copyWithCount(TEMPLATES_RETURNED);
				this.serviceInput.removeItem(0, 1);
				inventory.placeItemBackInInventory(result);
			}
			case BOTTLE_EXPERIENCE -> {
				if (player.experienceLevel < 1) {
					return refuse(player, "hamlet.service.needs_level");
				}
				player.giveExperienceLevels(-1);
				inventory.placeItemBackInInventory(new ItemStack(Items.EXPERIENCE_BOTTLE));
			}
		}

		takeEmeralds(player, SERVICE_EMERALD_COST);
		this.villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
		return true;
	}

	private static void takeEmeralds(Player player, int count) {
		player.getInventory().clearOrCountMatchingItems(IS_EMERALD, count, player.inventoryMenu.getCraftSlots());
	}

	private static boolean refuse(Player player, String messageKey) {
		player.displayClientMessage(Component.translatable(messageKey), true);
		return false;
	}

	private static boolean isTemplate(ItemStack stack) {
		return stack.getItem() instanceof SmithingTemplateItem;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot slot) {
		return !(slot instanceof StockSlot) && super.canTakeItemForPickAll(carried, slot);
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
			this.clearContainer(player, this.serviceInput);
			if (this.villager.getTradingPlayer() == player) {
				this.villager.setTradingPlayer(null);
			}
		}
	}

	public class StockSlot extends Slot {
		private StockSlot(SimpleContainer container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean isActive() {
			return VillagerTradeMenu.this.stockVisible;
		}
	}

	private class ServiceInputSlot extends Slot {
		private ServiceInputSlot(SimpleContainer container, int x, int y) {
			super(container, 0, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return VillagerTradeMenu.this.service == Service.DUPLICATE_TEMPLATE && isTemplate(stack);
		}

		@Override
		public boolean isActive() {
			Service service = VillagerTradeMenu.this.service;
			return !VillagerTradeMenu.this.stockVisible && service != null && service.needsInput;
		}
	}
}
