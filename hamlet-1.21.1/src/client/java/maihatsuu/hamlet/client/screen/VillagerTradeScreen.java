package maihatsuu.hamlet.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.trade.Service;
import maihatsuu.hamlet.trade.VillagerTradeMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Laid out like vanilla's trading screen: an offer list on the left, and on the right the villager's stock
 * above the player's inventory. Every service and every kind of stock item is a row; clicking a row makes
 * the trade at once. Rows are rebuilt from what the client can see each frame, so they always match.
 */
public class VillagerTradeScreen extends AbstractContainerScreen<VillagerTradeMenu> {
	private static final ResourceLocation BACKGROUND = Hamlet.id("textures/gui/container/villager.png");
	private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller");
	private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");
	private static final ResourceLocation TRADE_ARROW_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow");
	private static final ResourceLocation TRADE_ARROW_UNAVAILABLE_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow_out_of_stock");
	private static final ResourceLocation EMPTY_SLOT_TEMPLATE = ResourceLocation.withDefaultNamespace("item/empty_slot_smithing_template_armor_trim");
	private static final ResourceLocation EMPTY_SLOT_ENCHANTED_BOOK = Hamlet.id("item/empty_slot_enchanted_book");
	private static final Component TRADES_LABEL = Component.translatable("merchant.trades");

	// The same layout numbers as vanilla's MerchantScreen.
	private static final int TEXTURE_WIDTH = 512;
	private static final int TEXTURE_HEIGHT = 256;
	private static final int IMAGE_WIDTH = 276;
	private static final int IMAGE_HEIGHT = 166;
	private static final int ROWS = 7;
	private static final int ROW_X = 5;
	private static final int ROW_TOP = 18;
	private static final int ROW_WIDTH = 88;
	private static final int ROW_HEIGHT = 20;
	private static final int COST_A_X = 5;
	private static final int COST_B_X = 35;
	private static final int ARROW_X = 60;
	private static final int RESULT_X = 68;
	private static final int SCROLL_BAR_X = 94;
	private static final int SCROLL_BAR_HEIGHT = 139;
	private static final int SCROLLER_WIDTH = 6;
	private static final int SCROLLER_HEIGHT = 27;
	private static final int INVENTORIES_X = 107;

	private final RowButton[] rowButtons = new RowButton[ROWS];
	private List<Row> rows = List.of();
	private int scrollOff;
	private boolean isDragging;

	public VillagerTradeScreen(VillagerTradeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = IMAGE_WIDTH;
		this.imageHeight = IMAGE_HEIGHT;
		this.inventoryLabelX = INVENTORIES_X;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		for (int index = 0; index < ROWS; index++) {
			this.rowButtons[index] = this.addRenderableWidget(
				new RowButton(this.leftPos + ROW_X, this.topPos + ROW_TOP + index * ROW_HEIGHT, index)
			);
		}
		this.rows = this.buildRows();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.rows = this.buildRows();
		this.scrollOff = Mth.clamp(this.scrollOff, 0, Math.max(0, this.rows.size() - ROWS));
	}

	/** Services first, since they're always on offer, then one row per kind of item in stock. */
	private List<Row> buildRows() {
		List<Row> rows = new ArrayList<>();
		Player player = this.minecraft.player;
		Inventory inventory = player.getInventory();
		int emeralds = inventory.countItem(Items.EMERALD);

		Service other = this.menu.getOtherService();
		if (other == Service.BOTTLE_EXPERIENCE) {
			boolean available = emeralds >= other.cost && VillagerTradeMenu.hasLevelToBottle(player);
			rows.add(new Row(emeraldsStack(other.cost), ItemStack.EMPTY, new ItemStack(Items.EXPERIENCE_BOTTLE),
				VillagerTradeMenu.EXPERIENCE_BUTTON, -1, available, Component.translatable("hamlet.service.costs_level"), null));
		} else if (other != null && other.input != null) {
			// A row for each kind of template or book the player is carrying, so they choose which to copy.
			List<ItemStack> seen = new ArrayList<>();
			for (int slot = 0; slot < VillagerTradeMenu.PLAYER_INVENTORY_SIZE; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!other.input.test(stack) || seen.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
					continue;
				}
				seen.add(stack);
				rows.add(new Row(stack.copyWithCount(1), emeraldsStack(other.cost), stack.copyWithCount(2),
					VillagerTradeMenu.COPY_BUTTON + slot, -1, emeralds >= other.cost, null, null));
			}
			if (seen.isEmpty()) {
				ResourceLocation hint = other == Service.DUPLICATE_TEMPLATE ? EMPTY_SLOT_TEMPLATE : EMPTY_SLOT_ENCHANTED_BOOK;
				String bring = other == Service.DUPLICATE_TEMPLATE ? "hamlet.service.bring_template" : "hamlet.service.bring_enchanted_book";
				rows.add(new Row(ItemStack.EMPTY, emeraldsStack(other.cost), ItemStack.EMPTY,
					-1, -1, false, Component.translatable(bring), hint));
			}
		}

		List<Service> maps = this.menu.getMapServices();
		for (int index = 0; index < maps.size(); index++) {
			Service service = maps.get(index);
			ItemStack map = new ItemStack(Items.FILLED_MAP);
			map.set(DataComponents.ITEM_NAME, Component.translatable(service.map.nameKey()));
			rows.add(new Row(emeraldsStack(service.cost), ItemStack.EMPTY, map,
				VillagerTradeMenu.MAP_BUTTON + index, -1, emeralds >= service.cost, null, null));
		}

		SimpleContainer stock = this.menu.getStock();
		List<ItemStack> listed = new ArrayList<>();
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			ItemStack stack = stock.getItem(slot);
			if (stack.isEmpty() || listed.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
				continue;
			}
			listed.add(stack);
			int price = this.menu.priceOf(stack.getItem());
			int inStock = countInStock(stock, stack);
			rows.add(new Row(emeraldsStack(Math.min(price, 64)), price > 64 ? emeraldsStack(price - 64) : ItemStack.EMPTY,
				stack.copyWithCount(1), slot, inStock, emeralds >= price, null, null));
		}
		return rows;
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

	private static ItemStack emeraldsStack(int count) {
		return new ItemStack(Items.EMERALD, count);
	}

	private void onRowPressed(int index) {
		int row = index + this.scrollOff;
		if (row >= this.rows.size() || this.rows.get(row).buttonId < 0) {
			return;
		}
		int buttonId = this.rows.get(row).buttonId;
		// Shift-click on stock buys as many from that slot as the player can afford.
		if (buttonId < VillagerTradeMenu.STOCK_SIZE && Screen.hasShiftDown()) {
			buttonId += VillagerTradeMenu.STOCK_SIZE;
		}
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, INVENTORIES_X, 6, 0x404040, false);
		graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
		int width = this.font.width(TRADES_LABEL);
		graphics.drawString(this.font, TRADES_LABEL, ROW_X + ROW_WIDTH / 2 - width / 2, 6, 0x404040, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		for (RowButton button : this.rowButtons) {
			button.visible = button.index + this.scrollOff < this.rows.size();
		}
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderScroller(graphics);
		for (int index = 0; index < ROWS; index++) {
			int row = index + this.scrollOff;
			if (row < this.rows.size()) {
				this.renderRow(graphics, this.rows.get(row), this.topPos + ROW_TOP + index * ROW_HEIGHT + 2);
			}
		}
		for (RowButton button : this.rowButtons) {
			if (button.visible && button.isHoveredOrFocused()) {
				button.renderTooltip(graphics, mouseX, mouseY);
			}
		}
		RenderSystem.enableDepthTest();
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	private void renderRow(GuiGraphics graphics, Row row, int y) {
		int x = this.leftPos + ROW_X;
		graphics.pose().pushPose();
		graphics.pose().translate(0.0F, 0.0F, 100.0F);
		if (row.hintSprite != null) {
			graphics.blit(x + COST_A_X, y, 0, 16, 16, this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(row.hintSprite));
		}
		this.renderItem(graphics, row.costA, x + COST_A_X, y);
		this.renderItem(graphics, row.costB, x + COST_B_X, y);
		RenderSystem.enableBlend();
		graphics.blitSprite(row.available ? TRADE_ARROW_SPRITE : TRADE_ARROW_UNAVAILABLE_SPRITE, x + ARROW_X, y + 3, 0, 10, 9);
		this.renderItem(graphics, row.result, x + RESULT_X, y);
		graphics.pose().popPose();
	}

	private void renderItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
		if (!stack.isEmpty()) {
			graphics.renderFakeItem(stack, x, y);
			graphics.renderItemDecorations(this.font, stack, x, y);
		}
	}

	private void renderScroller(GuiGraphics graphics) {
		int x = this.leftPos + SCROLL_BAR_X;
		int top = this.topPos + ROW_TOP;
		int maxOff = this.rows.size() - ROWS;
		if (maxOff > 0) {
			int travel = SCROLL_BAR_HEIGHT - SCROLLER_HEIGHT;
			int offset = Math.round((float) travel * this.scrollOff / maxOff);
			graphics.blitSprite(SCROLLER_SPRITE, x, top + offset, 0, SCROLLER_WIDTH, SCROLLER_HEIGHT);
		} else {
			graphics.blitSprite(SCROLLER_DISABLED_SPRITE, x, top, 0, SCROLLER_WIDTH, SCROLLER_HEIGHT);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int maxOff = this.rows.size() - ROWS;
		if (maxOff > 0) {
			this.scrollOff = Mth.clamp((int) (this.scrollOff - scrollY), 0, maxOff);
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		this.isDragging = false;
		int x = this.leftPos + SCROLL_BAR_X;
		int top = this.topPos + ROW_TOP;
		if (this.rows.size() > ROWS && mouseX > x && mouseX < x + SCROLLER_WIDTH && mouseY > top && mouseY <= top + SCROLL_BAR_HEIGHT + 1) {
			this.isDragging = true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!this.isDragging) {
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		}
		int top = this.topPos + ROW_TOP;
		int maxOff = this.rows.size() - ROWS;
		float position = ((float) mouseY - top - SCROLLER_HEIGHT / 2.0F) / (SCROLL_BAR_HEIGHT - SCROLLER_HEIGHT);
		this.scrollOff = Mth.clamp((int) (position * maxOff + 0.5F), 0, maxOff);
		return true;
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
		if (this.hoveredSlot instanceof VillagerTradeMenu.StockSlot) {
			tooltip.add(this.priceLine(this.menu.priceOf(stack.getItem())));
		}
		return tooltip;
	}

	private Component priceLine(int price) {
		boolean affordable = this.minecraft.player.getInventory().countItem(Items.EMERALD) >= price;
		return Component.translatable("hamlet.trade.price", price).withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int button, ClickType clickType) {
		// The stock is only for looking at; buying is done from the offer list.
		if (slot instanceof VillagerTradeMenu.StockSlot) {
			return;
		}
		super.slotClicked(slot, slotId, button, clickType);
	}

	/**
	 * One offer: up to two costs and a result, drawn like a vanilla trade.
	 *
	 * @param buttonId what to send when clicked, or -1 if the row can't be clicked
	 * @param inStock how many the villager has, for stock rows; -1 for services
	 * @param note an extra tooltip line, such as a cost that isn't an item
	 * @param hintSprite drawn in the first cost's place, for a service missing its item
	 */
	private record Row(
		ItemStack costA,
		ItemStack costB,
		ItemStack result,
		int buttonId,
		int inStock,
		boolean available,
		@Nullable Component note,
		@Nullable ResourceLocation hintSprite
	) {
	}

	private class RowButton extends Button {
		final int index;

		RowButton(int x, int y, int index) {
			super(x, y, ROW_WIDTH, ROW_HEIGHT, CommonComponents.EMPTY, button -> VillagerTradeScreen.this.onRowPressed(index), DEFAULT_NARRATION);
			this.index = index;
			this.visible = false;
		}

		/** Like vanilla: the tooltip of whichever item is under the mouse, plus the row's notes. */
		void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
			int rowIndex = this.index + VillagerTradeScreen.this.scrollOff;
			if (rowIndex >= VillagerTradeScreen.this.rows.size()) {
				return;
			}
			Row row = VillagerTradeScreen.this.rows.get(rowIndex);
			ItemStack hovered = mouseX < this.getX() + 20 ? row.costA
				: mouseX > this.getX() + 30 && mouseX < this.getX() + 50 ? row.costB
				: mouseX > this.getX() + 65 ? row.result
				: ItemStack.EMPTY;
			List<Component> lines = new ArrayList<>();
			if (!hovered.isEmpty()) {
				lines.addAll(Screen.getTooltipFromItem(VillagerTradeScreen.this.minecraft, hovered));
			}
			if (hovered == row.result && row.inStock >= 0) {
				lines.add(Component.translatable("hamlet.trade.in_stock", row.inStock).withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("hamlet.trade.buy_hint").withStyle(ChatFormatting.GRAY));
			}
			if (row.note != null && (hovered.isEmpty() || hovered == row.result)) {
				lines.add(row.note.copy().withStyle(ChatFormatting.GRAY));
			}
			if (!lines.isEmpty()) {
				graphics.renderComponentTooltip(VillagerTradeScreen.this.font, lines, mouseX, mouseY);
			}
		}
	}
}
