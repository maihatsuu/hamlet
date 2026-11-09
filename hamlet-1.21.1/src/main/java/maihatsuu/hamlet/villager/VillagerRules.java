package maihatsuu.hamlet.villager;

import java.util.Set;

import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerRules {
	private static final Set<VillagerProfession> RETIRED = Set.of(
		VillagerProfession.ARMORER,
		VillagerProfession.BUTCHER,
		VillagerProfession.FLETCHER,
		VillagerProfession.LEATHERWORKER,
		VillagerProfession.MASON,
		VillagerProfession.TOOLSMITH,
		VillagerProfession.WEAPONSMITH
	);

	private VillagerRules() {
	}

	/** Villagers have no levels, and retired professions become unemployed. */
	public static VillagerData normalize(VillagerData data) {
		VillagerData result = data.setLevel(VillagerData.MIN_VILLAGER_LEVEL);
		return RETIRED.contains(result.getProfession()) ? result.setProfession(VillagerProfession.NONE) : result;
	}
}
