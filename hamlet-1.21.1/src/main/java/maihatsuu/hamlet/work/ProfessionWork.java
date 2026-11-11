package maihatsuu.hamlet.work;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's walk-to-workstation behaviour, for professions that offer services rather than stock. They
 * produce nothing, so this skips vanilla's restocking; the cartographer uses the visit to chart nearby
 * structures ahead of time, so buying a map rarely has to wait for a slow search.
 */
public final class ProfessionWork extends WorkAtPoi {
	private final VillagerProfession profession;

	public ProfessionWork(VillagerProfession profession) {
		this.profession = profession;
	}

	@Override
	protected void useWorkstation(ServerLevel level, Villager villager) {
		if (this.profession != VillagerProfession.CARTOGRAPHER || WorkRequirements.missing(villager).isPresent()) {
			return;
		}
		villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == level.dimension())
			.ifPresent(site -> ExplorerMap.searchAhead(level, site.pos()));
	}

	/**
	 * Work output nobody would buy, such as seeds from a harvest: thrown away rather than filling the stock.
	 * Datapacks can add to it to stop villagers gathering anything else.
	 */
	private static final TagKey<Item> DISCARDS = TagKey.create(Registries.ITEM, Hamlet.id("villager_discards"));

	/** Puts work output into the stock, unless it's discarded. All work output goes through here. */
	static void store(SimpleContainer stock, ItemStack stack) {
		if (!stack.is(DISCARDS)) {
			stock.addItem(stack);
		}
	}

	static boolean isFull(SimpleContainer stock) {
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			if (stock.getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
