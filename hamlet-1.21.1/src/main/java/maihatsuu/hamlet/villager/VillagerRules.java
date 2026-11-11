package maihatsuu.hamlet.villager;

import java.util.Set;

import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerRules {
	private static final Set<VillagerProfession> RETIRED = Set.of(
		VillagerProfession.ARMORER,
		VillagerProfession.FLETCHER,
		VillagerProfession.LEATHERWORKER,
		VillagerProfession.MASON,
		VillagerProfession.TOOLSMITH,
		VillagerProfession.WEAPONSMITH
	);

	private VillagerRules() {
	}

	/**
	 * Villagers have no levels, and retired professions become unemployed, except the butcher: its smoker is
	 * the beekeeper's workstation, so vanilla's assignment (and old saves) hand butchers over to beekeeping.
	 */
	public static VillagerData normalize(VillagerData data) {
		VillagerData result = data.setLevel(VillagerData.MIN_VILLAGER_LEVEL);
		VillagerProfession profession = result.getProfession();
		if (profession == VillagerProfession.BUTCHER) {
			return result.setProfession(HamletProfessions.BEEKEEPER);
		}
		return RETIRED.contains(profession) ? result.setProfession(VillagerProfession.NONE) : result;
	}
}
