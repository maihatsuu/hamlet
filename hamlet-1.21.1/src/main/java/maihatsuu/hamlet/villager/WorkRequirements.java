package maihatsuu.hamlet.villager;

import java.util.Optional;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

/** A villager only works while it has claimed both a bed and a workstation. Trading is never restricted. */
public final class WorkRequirements {
	private WorkRequirements() {
	}

	/** Why this villager currently can't work, if it can't. */
	public static Optional<Component> missing(Villager villager) {
		Brain<Villager> brain = villager.getBrain();
		if (!brain.hasMemoryValue(MemoryModuleType.JOB_SITE)) {
			return Optional.of(Component.translatable("hamlet.not_working.no_workstation"));
		}
		if (!hasBed(villager)) {
			return Optional.of(Component.translatable("hamlet.not_working.no_bed"));
		}
		return Optional.empty();
	}

	/**
	 * Vanilla only forgets a broken bed at night, once the villager goes back to it, so the bed itself is
	 * checked here too. Its job site is already rechecked every tick by vanilla.
	 */
	private static boolean hasBed(Villager villager) {
		Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
		if (home.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
			return home.isPresent();
		}
		GlobalPos pos = home.get();
		return pos.dimension() == level.dimension()
			&& level.getPoiManager().exists(pos.pos(), type -> type.is(PoiTypes.HOME));
	}
}
