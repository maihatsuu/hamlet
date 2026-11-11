package maihatsuu.hamlet.work;

import maihatsuu.hamlet.villager.WorkRequirements;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

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

	static boolean isFull(SimpleContainer stock) {
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			if (stock.getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
