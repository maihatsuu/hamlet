package maihatsuu.hamlet.work;

import maihatsuu.hamlet.data.ProfessionDataLoader;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.ReadinessState;
import maihatsuu.hamlet.villager.VillagerReadiness;

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
		if (job == null || VillagerReadiness.refusal(villager).isPresent()) {
			return;
		}

		HamletVillager hamletVillager = (HamletVillager) villager;
		SimpleContainer stock = hamletVillager.hamlet$getStock();
		ReadinessState state = hamletVillager.hamlet$getReadiness();
		long now = level.getGameTime();
		if (isFull(stock) || !state.isWorkDue(now, ProfessionDataLoader.get(this.profession).workInterval())) {
			return;
		}

		villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(site -> {
			if (job.run(level, site.pos(), stock)) {
				state.markWorked(now);
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
