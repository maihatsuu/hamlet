package maihatsuu.hamlet.trade;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * One row of the trade screen's offer list: up to two costs and a result, like a vanilla trade. Built the
 * same way on both sides from state they share, so the client's row index names the same offer on the server.
 *
 * @param inStock how many the villager has, for {@link Kind#STOCK}; -1 otherwise
 * @param available whether the player has what it costs; only used to draw the arrow
 * @param note an extra tooltip line, such as a cost that isn't an item
 * @param hintSprite drawn in the first cost's place, for a copy service the player has nothing for
 */
public record Offer(
	Kind kind,
	ItemStack costA,
	ItemStack costB,
	ItemStack result,
	int inStock,
	boolean available,
	@Nullable Component note,
	@Nullable ResourceLocation hintSprite
) {
	public enum Kind {
		/** One item from the villager's stock. */
		STOCK,
		/** A Bottle o' Enchanting, for a level as well as the emeralds. */
		EXPERIENCE,
		/** A copy of the book or template in the first payment slot, which stays there to copy again. */
		COPY
	}

	/** A copy service with nothing to copy: shown, but can't be selected. */
	public boolean selectable() {
		return this.hintSprite == null;
	}

	/** Whether the two rows are the same trade, even though the player's money or the stock has changed. */
	public boolean sameTrade(Offer other) {
		return this.kind == other.kind
			&& ItemStack.isSameItemSameComponents(this.costA, other.costA)
			&& ItemStack.isSameItemSameComponents(this.result, other.result);
	}
}
