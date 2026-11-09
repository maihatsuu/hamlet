package maihatsuu.hamlet.client.screen;

import java.util.ArrayList;
import java.util.List;

import maihatsuu.hamlet.trade.VillagerTradeMenu;
import maihatsuu.hamlet.villager.HamletVillager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class VillagerTradeScreen extends AbstractContainerScreen<VillagerTradeMenu> {
	private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int STOCK_ROWS = 3;
	private static final int TAB_WIDTH = 60;
	private static final int TAB_HEIGHT = 20;
	private static final Component NO_SERVICES = Component.translatable("hamlet.trade.no_services");

	private Button stockTab;
	private Button serviceTab;

	public VillagerTradeScreen(VillagerTradeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageHeight = 114 + STOCK_ROWS * 18;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		int y = this.topPos - TAB_HEIGHT;
		this.stockTab = this.addRenderableWidget(
			Button.builder(Component.translatable("hamlet.trade.tab.stock"), button -> this.showStock(true))
				.bounds(this.leftPos, y, TAB_WIDTH, TAB_HEIGHT)
				.build()
		);
		this.serviceTab = this.addRenderableWidget(
			Button.builder(Component.translatable("hamlet.trade.tab.service"), button -> this.showStock(false))
				.bounds(this.leftPos + TAB_WIDTH, y, TAB_WIDTH, TAB_HEIGHT)
				.build()
		);
		this.showStock(this.menu.stockVisible);
	}

	private void showStock(boolean visible) {
		this.menu.stockVisible = visible;
		this.stockTab.active = !visible;
		this.serviceTab.active = visible;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int stockHeight = STOCK_ROWS * 18 + 17;
		graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, stockHeight);
		graphics.blit(BACKGROUND, this.leftPos, this.topPos + stockHeight, 0, 126, this.imageWidth, 96);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderLabels(graphics, mouseX, mouseY);
		if (!this.menu.stockVisible) {
			int x = (this.imageWidth - this.font.width(NO_SERVICES)) / 2;
			graphics.drawString(this.font, NO_SERVICES, x, 40, 0x404040, false);
		}
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
		if (this.hoveredSlot instanceof VillagerTradeMenu.StockSlot) {
			int price = this.menu.priceOf(stack.getItem());
			boolean affordable = this.minecraft.player.getInventory().countItem(Items.EMERALD) >= price;
			tooltip.add(Component.translatable("hamlet.trade.price", price)
				.withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED));
			tooltip.add(Component.translatable("hamlet.trade.buy_hint").withStyle(ChatFormatting.GRAY));
		}
		return tooltip;
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int button, ClickType clickType) {
		if (slot instanceof VillagerTradeMenu.StockSlot) {
			if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
				int buttonId = slot.getContainerSlot() + (clickType == ClickType.QUICK_MOVE ? HamletVillager.STOCK_SIZE : 0);
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
			}
			return;
		}
		super.slotClicked(slot, slotId, button, clickType);
	}
}
