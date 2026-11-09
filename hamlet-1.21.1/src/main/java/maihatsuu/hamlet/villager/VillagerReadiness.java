package maihatsuu.hamlet.villager;

import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerReadiness {
	private VillagerReadiness() {
	}

	/** Why this villager currently refuses to work or trade, if it does. */
	public static Optional<Component> refusal(Villager villager) {
		return Optional.empty();
	}
}
