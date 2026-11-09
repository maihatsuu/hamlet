package maihatsuu.hamlet.trade;

import java.util.HashMap;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Stock items are shown in real slots so vanilla syncs them, but they can't be taken directly.
 * Buying goes through {@link #clickMenuButton}: button IDs below {@code STOCK_SIZE} buy one item from
 * that slot, and {@code STOCK_SIZE + slot} buys as much of the stack as the player can afford.
 */
public class VillagerTradeMenu extends AbstractContainerMenu {
	private static final int STOCK_SIZE = HamletVillager.STOCK_SIZE;
	private static final double MAX_DISTANCE_SQR = 64.0;
	private static final Predicate<ItemStack> IS_EMERALD = stack -> stack.is(Items.EMERALD);

	private static final StreamCodec<RegistryFriendlyByteBuf, Map<Item, Integer>> PRICES_CODEC = ByteBufCodecs.map(
		size -> new HashMap<>(),
		ByteBufCodecs.registry(Registries.ITEM),
		ByteBufCodecs.VAR_INT
	);

	public static final MenuType<VillagerTradeMenu> TYPE = Registry.register(
		BuiltInRegistries.MENU,
		Hamlet.id("villager_trade"),
		new ExtendedScreenHandlerType<>(VillagerTradeMenu::new, PRICES_CODEC)
	);

	/** Null on the client. */
	@Nullable
	private final Villager villager;
	private final SimpleContainer stock;
	private final Map<Item, Integer> prices;

	/** Client-only view state: stock slots are hidden while the Service tab is open. */
	public boolean stockVisible = true;

	public VillagerTradeMenu(int containerId, Inventory playerInventory, Map<Item, Integer> prices) {
		this(containerId, playerInventory, new SimpleContainer(STOCK_SIZE), prices, null);
	}

	private VillagerTradeMenu(
		int containerId,
		Inventory playerInventory,
		SimpleContainer stock,
		Map<Item, Integer> prices,
		@Nullable Villager villager
	) {
		super(TYPE, containerId);
		this.stock = stock;
		this.prices = prices;
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
	}

	/** Forces the menu type's static registration to run during mod initialization. */
	public static void init() {
	}

	/** Claims the villager for this player and opens the screen. */
	public static void open(Villager villager, Player player) {
		villager.setTradingPlayer(player);
		player.openMenu(new ExtendedScreenHandlerFactory<Map<Item, Integer>>() {
			@Override
			public Map<Item, Integer> getScreenOpeningData(ServerPlayer serverPlayer) {
				return ProfessionDataLoader.get(villager.getVillagerData().getProfession()).prices();
			}

			@Override
			public Component getDisplayName() {
				return villager.getDisplayName();
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
				ProfessionData data = ProfessionDataLoader.get(villager.getVillagerData().getProfession());
				SimpleContainer stock = ((HamletVillager) villager).hamlet$getStock();
				return new VillagerTradeMenu(containerId, inventory, stock, data.prices(), villager);
			}
		});
	}

	public int priceOf(Item item) {
		return this.prices.getOrDefault(item, ProfessionData.DEFAULT_PRICE);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.villager == null || id < 0 || id >= STOCK_SIZE * 2) {
			return false;
		}

		int slot = id % STOCK_SIZE;
		boolean wholeStack = id >= STOCK_SIZE;
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
			player.displayClientMessage(Component.translatable("hamlet.trade.cannot_afford"), true);
			return false;
		}

		inventory.clearOrCountMatchingItems(IS_EMERALD, count * unitPrice, player.inventoryMenu.getCraftSlots());
		inventory.placeItemBackInInventory(this.stock.removeItem(slot, count));
		this.villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
		return true;
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
		if (this.villager != null && this.villager.getTradingPlayer() == player) {
			this.villager.setTradingPlayer(null);
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
}
