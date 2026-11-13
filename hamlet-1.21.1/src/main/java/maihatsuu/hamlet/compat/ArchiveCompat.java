package maihatsuu.hamlet.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Archive, the enchanting mod, if it's installed: tomes replace enchanted books, and go on lecterns. A
 * librarian copies a tome on its lectern as it would an enchanted book. Tomes are only looked up by id, so
 * Hamlet doesn't need Archive to build. Their price is in {@code prices/archive.json}, which only loads with
 * Archive.
 */
public final class ArchiveCompat {
	private static final ResourceLocation TOME = ResourceLocation.fromNamespaceAndPath("archive", "tome");

	private ArchiveCompat() {
	}

	public static boolean isTome(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(TOME);
	}
}
