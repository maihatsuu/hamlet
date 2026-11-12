package maihatsuu.hamlet.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enchanted books go on lecterns, through {@code #minecraft:lectern_books}, so librarians can copy them. There's
 * nothing to read in one, so right-clicking the lectern hands the book straight back instead of opening the
 * book screen, which vanilla's lectern menu would close at once anyway.
 * <p>
 * Server only: the client doesn't know which book is on a lectern, and vanilla already reports the click as
 * handled there.
 */
@Mixin(LecternBlock.class)
abstract class LecternBlockMixin {
	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
	private void hamlet$takeEnchantedBook(
		BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir
	) {
		if (level.isClientSide()
			|| !state.getValue(LecternBlock.HAS_BOOK)
			|| !(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)
			|| !lectern.getBook().is(Items.ENCHANTED_BOOK)) {
			return;
		}
		ItemStack book = lectern.getBook();
		// What vanilla's "Take Book" button does: empty the lectern, then give the book or drop it.
		lectern.setBook(ItemStack.EMPTY);
		LecternBlock.resetBookState(player, level, pos, state, false);
		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}
		cir.setReturnValue(InteractionResult.SUCCESS);
	}
}
