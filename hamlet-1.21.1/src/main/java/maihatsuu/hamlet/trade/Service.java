package maihatsuu.hamlet.trade;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.villager.HamletProfessions;
import maihatsuu.hamlet.work.ExplorerMap;

import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;

/** What a profession offers besides its stock, each with its emerald cost and the item it takes, if any. */
public enum Service {
	DUPLICATE_TEMPLATE(1, stack -> stack.getItem() instanceof SmithingTemplateItem, null),
	BOTTLE_EXPERIENCE(1, null, null),
	COPY_ENCHANTED_BOOK(8, stack -> stack.is(Items.ENCHANTED_BOOK), null),
	OCEAN_MONUMENT_MAP(8, null, ExplorerMap.OCEAN_MONUMENT),
	WOODLAND_MANSION_MAP(8, null, ExplorerMap.WOODLAND_MANSION),
	TRIAL_CHAMBERS_MAP(8, null, ExplorerMap.TRIAL_CHAMBERS);

	private static final Map<VillagerProfession, List<Service>> BY_PROFESSION = Map.of(
		HamletProfessions.ARCHAEOLOGIST, List.of(DUPLICATE_TEMPLATE),
		VillagerProfession.CLERIC, List.of(BOTTLE_EXPERIENCE),
		VillagerProfession.LIBRARIAN, List.of(COPY_ENCHANTED_BOOK),
		VillagerProfession.CARTOGRAPHER, List.of(OCEAN_MONUMENT_MAP, WOODLAND_MANSION_MAP, TRIAL_CHAMBERS_MAP)
	);

	public final int cost;
	/** The kind of item, from the player's inventory, that the service copies; null if it takes none. */
	@Nullable
	public final Predicate<ItemStack> input;
	/** The map this service sells, or null if it isn't a map. */
	@Nullable
	public final ExplorerMap map;

	Service(int cost, @Nullable Predicate<ItemStack> input, @Nullable ExplorerMap map) {
		this.cost = cost;
		this.input = input;
		this.map = map;
	}

	public static List<Service> forProfession(VillagerProfession profession) {
		return BY_PROFESSION.getOrDefault(profession, List.of());
	}
}
