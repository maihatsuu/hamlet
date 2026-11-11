package maihatsuu.hamlet.trade;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.data.PriceLoader;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
 * The villager's inventory is shown in real slots. Items with a price (the same for every villager) are stock:
 * they can't be taken or placed directly, only bought. Anything else is the player's to put in and take
 * out (food, for breeding, once that's designed). Every trade is a click on a row of the offer list,
 * sent through {@link #clickMenuButton}:
 * <ul>
 *   <li>below {@code STOCK_SIZE}: buy one item from that stock slot; {@code STOCK_SIZE + slot}: buy as
 *   many from that slot as the player can afford;</li>
 *   <li>{@link #MAP_BUTTON} {@code + index}: buy that explorer map;</li>
 *   <li>{@link #EXPERIENCE_BUTTON}: bottle one level;</li>
 *   <li>{@link #COPY_BUTTON} {@code + slot}: duplicate the template or copy the book in that slot of the
 *   player's inventory.</li>
 * </ul>
 */
public class VillagerTradeMenu extends AbstractContainerMenu {
	public static final int STOCK_SIZE = HamletVillager.STOCK_SIZE;
	public static final int MAP_BUTTON = STOCK_SIZE * 2;
	public static final int EXPERIENCE_BUTTON = MAP_BUTTON + 16;
	public static final int COPY_BUTTON = EXPERIENCE_BUTTON + 16;
	/** Main inventory and hotbar; armour and offhand aren't offered for copying. */
	public static final int PLAYER_INVENTORY_SIZE = 36;
	/** Duplicating a template or copying a book hands back the original plus one copy. */
	private static final int COPIES_RETURNED = 2;
	private static final double MAX_DISTANCE_SQR = 64.0;
	private static final Predicate<ItemStack> IS_EMERALD = stack -> stack.is(Items.EMERALD);

	// Slot positions, matching the texture: the villager's stock above the player's inventory.
	private static final int INVENTORIES_X = 108;
	private static final int STOCK_Y = 18;
	private static final int PLAYER_INVENTORY_Y = 84;
	private static final int HOTBAR_Y = 142;

	public static final MenuType<VillagerTradeMenu> TYPE = Registry.register(
		BuiltInRegistries.MENU,
		Hamlet.id("villager_trade"),
		new ExtendedScreenHandlerType<>(VillagerTradeMenu::new, TradeOpeningData.STREAM_CODEC)
	);

	/** Null on the client. */
	@Nullable
	private final Villager villager;
	private final SimpleContainer stock;
	private final Map<Item, Integer> prices;
	private final List<Service> mapServices;
	/** The template, book or experience service, if the profession has one. */
	@Nullable
	private final Service otherService;

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
		this.villager = villager;
		List<Service> services = Service.forProfession(profession);
		this.mapServices = services.stream().filter(service -> service.map != null).toList();
		this.otherService = services.stream().filter(service -> service.map == null).findFirst().orElse(null);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new StockSlot(stock, column + row * 9, INVENTORIES_X + column * 18, STOCK_Y + row * 18));
			}
		}
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
				SimpleContainer stock = ((HamletVillager) villager).hamlet$getStock();
				return new VillagerTradeMenu(containerId, inventory, stock, prices, profession, villager);
			}
		});
	}

	/** Only meaningful for priced items; see {@link #isPriced}. */
	public int priceOf(Item item) {
		return this.prices.getOrDefault(item, 0);
	}

	/** Whether villagers sell the item, so it's stock to buy rather than the player's to take. */
	public boolean isPriced(ItemStack stack) {
		return !stack.isEmpty() && this.prices.containsKey(stack.getItem());
	}

	public SimpleContainer getStock() {
		return this.stock;
	}

	public List<Service> getMapServices() {
		return this.mapServices;
	}

	@Nullable
	public Service getOtherService() {
		return this.otherService;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.villager == null || id < 0) {
			return false;
		}
		if (id < MAP_BUTTON) {
			return this.buy(player, id % STOCK_SIZE, id >= STOCK_SIZE);
		}
		if (id < EXPERIENCE_BUTTON) {
			int index = id - MAP_BUTTON;
			return index < this.mapServices.size() && this.sellMap(player, this.mapServices.get(index));
		}
		if (id == EXPERIENCE_BUTTON) {
			return this.otherService == Service.BOTTLE_EXPERIENCE && this.bottleExperience(player);
		}
		int slot = id - COPY_BUTTON;
		return slot >= 0 && slot < PLAYER_INVENTORY_SIZE && this.copy(player, slot);
	}

	private boolean buy(Player player, int slot, boolean asManyAsPossible) {
		ItemStack stack = this.stock.getItem(slot);
		if (!this.isPriced(stack)) {
			return false;
		}

		Inventory inventory = player.getInventory();
		int unitPrice = this.priceOf(stack.getItem());
		int affordable = inventory.countItem(Items.EMERALD) / unitPrice;
		int count = Math.min(asManyAsPossible ? stack.getCount() : 1, affordable);
		if (count == 0) {
			return refuse(player, "hamlet.trade.cannot_afford");
		}

		takeEmeralds(player, count * unitPrice);
		inventory.placeItemBackInInventory(this.stock.removeItem(slot, count));
		this.villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
		return true;
	}

	private boolean sellMap(Player player, Service service) {
		if (!canAfford(player, service)) {
			return refuse(player, "hamlet.trade.cannot_afford");
		}
		// Charted from the cartography table, like the searches the cartographer runs ahead of time.
		BlockPos from = this.villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == this.villager.level().dimension())
			.map(GlobalPos::pos)
			.orElse(this.villager.blockPosition());
		Optional<ItemStack> map = service.map.create((ServerLevel) this.villager.level(), from);
		if (map.isEmpty()) {
			return refuse(player, "hamlet.service.map_not_charted");
		}
		this.complete(player, service, map.get());
		return true;
	}

	private boolean bottleExperience(Player player) {
		Service service = Service.BOTTLE_EXPERIENCE;
		if (!canAfford(player, service)) {
			return refuse(player, "hamlet.trade.cannot_afford");
		}
		if (!hasLevelToBottle(player)) {
			return refuse(player, "hamlet.service.needs_level");
		}
		player.giveExperienceLevels(-1);
		this.complete(player, service, new ItemStack(Items.EXPERIENCE_BOTTLE));
		return true;
	}

	/** Duplicates a template or copies a book: the original goes back with one copy. */
	private boolean copy(Player player, int slot) {
		Service service = this.otherService;
		ItemStack original = player.getInventory().getItem(slot);
		if (service == null || service.input == null || !service.input.test(original)) {
			return false;
		}
		if (!canAfford(player, service)) {
			return refuse(player, "hamlet.trade.cannot_afford");
		}
		ItemStack result = original.copyWithCount(COPIES_RETURNED);
		original.shrink(1);
		this.complete(player, service, result);
		return true;
	}

	private void complete(Player player, Service service, ItemStack result) {
		takeEmeralds(player, service.cost);
		player.getInventory().placeItemBackInInventory(result);
		this.villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
	}

	public static boolean canAfford(Player player, Service service) {
		return player.getInventory().countItem(Items.EMERALD) >= service.cost;
	}

	public static boolean hasLevelToBottle(Player player) {
		return player.experienceLevel >= 1 || player.getAbilities().instabuild;
	}

	private static void takeEmeralds(Player player, int count) {
		player.getInventory().clearOrCountMatchingItems(IS_EMERALD, count, player.inventoryMenu.getCraftSlots());
	}

	private static boolean refuse(Player player, String messageKey) {
		player.displayClientMessage(Component.translatable(messageKey), true);
		return false;
	}

	/**
	 * Shift-click moves an unpriced item between the villager's inventory and the player's; otherwise, as
	 * in vanilla's trading screen, between the player's inventory and hotbar.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		int inventoryStart = STOCK_SIZE;
		int hotbarStart = inventoryStart + 27;
		int hotbarEnd = hotbarStart + 9;
		Slot slot = this.slots.get(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved;
		if (index < inventoryStart) {
			moved = this.moveItemStackTo(stack, inventoryStart, hotbarEnd, true);
		} else if (!this.isPriced(stack) && this.moveItemStackTo(stack, 0, inventoryStart, false)) {
			moved = true;
		} else {
			moved = index < hotbarStart
				? this.moveItemStackTo(stack, hotbarStart, hotbarEnd, false)
				: this.moveItemStackTo(stack, inventoryStart, hotbarStart, false);
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
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

	/** A slot of the villager's inventory: priced items here are stock, bought from the offer list. */
	public class StockSlot extends Slot {
		private StockSlot(SimpleContainer container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !VillagerTradeMenu.this.isPriced(stack);
		}

		@Override
		public boolean mayPickup(Player player) {
			return !VillagerTradeMenu.this.isPriced(this.getItem());
		}
	}
}
