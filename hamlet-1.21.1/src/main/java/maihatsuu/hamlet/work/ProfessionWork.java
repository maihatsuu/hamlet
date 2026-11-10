package maihatsuu.hamlet.work;

import maihatsuu.hamlet.data.ProfessionDataLoader;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Vanilla's walk-to-workstation behaviour, doing Hamlet's work instead of vanilla's. */
public final class ProfessionWork extends WorkAtPoi {
	private final VillagerProfession profession;

	public ProfessionWork(VillagerProfession profession) {
		this.profession = profession;
	}

	@Override
	protected void useWorkstation(ServerLevel level, Villager villager) {
		Job job = Jobs.forProfession(this.profession);
		if (job == null || WorkRequirements.missing(villager).isPresent()) {
			return;
		}

		HamletVillager hamletVillager = (HamletVillager) villager;
		SimpleContainer stock = hamletVillager.hamlet$getStock();
		long now = level.getGameTime();
		long lastWorked = hamletVillager.hamlet$getLastWorked();
		int interval = ProfessionDataLoader.get(this.profession).workInterval();
		boolean due = lastWorked == HamletVillager.NEVER_WORKED || now - lastWorked >= interval;
		if (isFull(stock) || !due) {
			return;
		}

		villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(site -> {
			if (job.run(level, site.pos(), stock)) {
				hamletVillager.hamlet$setLastWorked(now);
			}
		});
	}

	private static boolean isFull(SimpleContainer stock) {
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			if (stock.getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
