package maihatsuu.hamlet.trade;

import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.villager.HamletProfessions;

import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;

/**
 * What a profession offers besides its stock, with its emerald cost and the item it takes, if any. Each
 * profession has at most one. The cartographer has none: it draws its explorer maps into its stock.
 */
public enum Service {
	DUPLICATE_TEMPLATE(1, stack -> stack.getItem() instanceof SmithingTemplateItem),
	BOTTLE_EXPERIENCE(1, null),
	COPY_ENCHANTED_BOOK(8, stack -> stack.is(Items.ENCHANTED_BOOK));

	private static final Map<VillagerProfession, Service> BY_PROFESSION = Map.of(
		HamletProfessions.ARCHAEOLOGIST, DUPLICATE_TEMPLATE,
		VillagerProfession.CLERIC, BOTTLE_EXPERIENCE,
		VillagerProfession.LIBRARIAN, COPY_ENCHANTED_BOOK
	);

	public final int cost;
	/** The kind of item, from the player's inventory, that the service copies; null if it takes none. */
	@Nullable
	public final Predicate<ItemStack> input;
	Service(int cost, @Nullable Predicate<ItemStack> input) {
		this.cost = cost;
		this.input = input;
	}

	@Nullable
	public static Service forProfession(VillagerProfession profession) {
		return BY_PROFESSION.get(profession);
	}
}
