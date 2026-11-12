package maihatsuu.hamlet.villager;

import com.google.common.collect.ImmutableSet;

import maihatsuu.hamlet.Hamlet;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class HamletProfessions {
	/**
	 * Retired, merged into the farmer, which now harvests hives: no workstation gives this job, and
	 * {@link VillagerRules} makes existing beekeepers jobless. Still registered so saves with beekeepers load.
	 */
	public static final VillagerProfession BEEKEEPER;
	/**
	 * Retired: no workstation gives this job, and {@link VillagerRules} makes existing archaeologists jobless.
	 * Still registered so saves with archaeologists load.
	 */
	public static final VillagerProfession ARCHAEOLOGIST;

	static {
		BEEKEEPER = registerRetired("beekeeper");
		ARCHAEOLOGIST = registerRetired("archaeologist");
	}

	private HamletProfessions() {
	}

	/** A profession no workstation gives, kept so saved villagers with it still load. */
	private static VillagerProfession registerRetired(String name) {
		return Registry.register(
			BuiltInRegistries.VILLAGER_PROFESSION,
			Hamlet.id(name),
			new VillagerProfession(name, holder -> false, holder -> false, ImmutableSet.of(), ImmutableSet.of(), null)
		);
	}

	/** Forces the static registration above to run during mod initialization. */
	public static void init() {
	}
}
