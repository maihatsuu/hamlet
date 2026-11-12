package maihatsuu.hamlet.trade;

import net.minecraft.world.item.ItemStack;

/**
 * One row of the trade screen's offer list: emeralds for one item from the villager's stock, with up to two
 * costs like a vanilla trade. Built the same way on both sides from state they share, so the client's row
 * index names the same offer on the server.
 *
 * @param inStock how many the villager has
 * @param available whether the player has what it costs; only used to draw the arrow
 */
public record Offer(ItemStack costA, ItemStack costB, ItemStack result, int inStock, boolean available) {
	/** Whether the two rows are the same trade, even though the player's money or the stock has changed. */
	public boolean sameTrade(Offer other) {
		return ItemStack.isSameItemSameComponents(this.costA, other.costA)
			&& ItemStack.isSameItemSameComponents(this.result, other.result);
	}
}
