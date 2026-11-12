package maihatsuu.hamlet.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.trade.Offer;
import maihatsuu.hamlet.trade.VillagerTradeMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Laid out like vanilla's trading screen: an offer list on the left; on the right the villager's storage,
 * the payment and result slots, and the player's inventory. Clicking a row selects that offer, moving its
 * cost into the payment slots; taking the result makes the trade. Rows are rebuilt each tick from what the
 * client can see, the same way the server builds them.
 */
public class VillagerTradeScreen extends AbstractContainerScreen<VillagerTradeMenu> {
	private static final ResourceLocation BACKGROUND = Hamlet.id("textures/gui/container/villager.png");
	private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller");
	private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");
	private static final ResourceLocation TRADE_ARROW_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow");
	private static final ResourceLocation TRADE_ARROW_UNAVAILABLE_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow_out_of_stock");
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
	private static final int ARROW_X = 55;
	private static final int RESULT_X = 68;
	private static final int SCROLL_BAR_X = 94;
	private static final int SCROLL_BAR_HEIGHT = 139;
	private static final int SCROLLER_WIDTH = 6;
	private static final int SCROLLER_HEIGHT = 27;
	private static final int INVENTORIES_X = 107;
	private static final int INVENTORY_LABEL_Y = 73;

	private final RowButton[] rowButtons = new RowButton[ROWS];
	private List<Offer> rows = List.of();
	@Nullable
	private Offer selected;
	private int scrollOff;
	private boolean isDragging;

	public VillagerTradeScreen(VillagerTradeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = IMAGE_WIDTH;
		this.imageHeight = IMAGE_HEIGHT;
		this.inventoryLabelX = INVENTORIES_X;
		this.inventoryLabelY = INVENTORY_LABEL_Y;
	}

	@Override
	protected void init() {
		super.init();
		for (int index = 0; index < ROWS; index++) {
			this.rowButtons[index] = this.addRenderableWidget(
				new RowButton(this.leftPos + ROW_X, this.topPos + ROW_TOP + index * ROW_HEIGHT, index)
			);
		}
		this.rows = this.menu.offers();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.rows = this.menu.offers();
		this.scrollOff = Mth.clamp(this.scrollOff, 0, Math.max(0, this.rows.size() - ROWS));
	}

	private void onRowPressed(int index) {
		int row = index + this.scrollOff;
		if (row >= this.rows.size()) {
			return;
		}
		this.selected = this.rows.get(row);
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, row);
	}

	private boolean isSelected(Offer offer) {
		return this.selected != null && this.selected.sameTrade(offer);
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
			if (button.visible && button.isHovered()) {
				button.renderTooltip(graphics, mouseX, mouseY);
			}
		}
		RenderSystem.enableDepthTest();
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	private void renderRow(GuiGraphics graphics, Offer row, int y) {
		int x = this.leftPos + ROW_X;
		graphics.pose().pushPose();
		graphics.pose().translate(0.0F, 0.0F, 100.0F);
		this.renderItem(graphics, row.costA(), x + COST_A_X, y);
		this.renderItem(graphics, row.costB(), x + COST_B_X, y);
		RenderSystem.enableBlend();
		graphics.blitSprite(row.available() ? TRADE_ARROW_SPRITE : TRADE_ARROW_UNAVAILABLE_SPRITE, x + ARROW_X, y + 3, 0, 10, 9);
		this.renderItem(graphics, row.result(), x + RESULT_X, y);
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

	private class RowButton extends Button {
		final int index;

		RowButton(int x, int y, int index) {
			super(x, y, ROW_WIDTH, ROW_HEIGHT, CommonComponents.EMPTY, button -> VillagerTradeScreen.this.onRowPressed(index), DEFAULT_NARRATION);
			this.index = index;
			this.visible = false;
		}

		/** Drawn highlighted while its offer is selected, as vanilla's are. */
		@Override
		public boolean isHoveredOrFocused() {
			return this.isHovered() || this.offer() != null && VillagerTradeScreen.this.isSelected(this.offer());
		}

		@Nullable
		private Offer offer() {
			int row = this.index + VillagerTradeScreen.this.scrollOff;
			return row < VillagerTradeScreen.this.rows.size() ? VillagerTradeScreen.this.rows.get(row) : null;
		}

		/** Like vanilla: the tooltip of whichever item is under the mouse, plus the row's notes. */
		void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
			Offer row = this.offer();
			if (row == null) {
				return;
			}
			ItemStack hovered = mouseX < this.getX() + 20 ? row.costA()
				: mouseX > this.getX() + 30 && mouseX < this.getX() + 50 ? row.costB()
				: mouseX > this.getX() + 65 ? row.result()
				: ItemStack.EMPTY;
			List<Component> lines = new ArrayList<>();
			if (!hovered.isEmpty()) {
				lines.addAll(Screen.getTooltipFromItem(VillagerTradeScreen.this.minecraft, hovered));
			}
			if (hovered == row.result()) {
				lines.add(Component.translatable("hamlet.trade.in_stock", row.inStock()).withStyle(ChatFormatting.GRAY));
			}
			if (!lines.isEmpty()) {
				graphics.renderComponentTooltip(VillagerTradeScreen.this.font, lines, mouseX, mouseY);
			}
		}
	}
}
