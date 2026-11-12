package maihatsuu.hamlet.villager;

import com.google.common.collect.ImmutableSet;

import maihatsuu.hamlet.Hamlet;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class HamletProfessions {
	public static final VillagerProfession BEEKEEPER;
	/**
	 * Retired: no workstation gives this job, and {@link VillagerRules} makes existing archaeologists jobless.
	 * Still registered so saves with archaeologists load.
	 */
	public static final VillagerProfession ARCHAEOLOGIST;

	static {
		// A block state can only belong to one POI type, so the beekeeper shares the retired butcher's smoker
		// POI. Vanilla assigns a smoker to the butcher first; VillagerRules turns butchers into beekeepers.
		BEEKEEPER = register("beekeeper", PoiTypes.BUTCHER, SoundEvents.BEEHIVE_WORK);
		ARCHAEOLOGIST = Registry.register(
			BuiltInRegistries.VILLAGER_PROFESSION,
			Hamlet.id("archaeologist"),
			new VillagerProfession("archaeologist", holder -> false, holder -> false, ImmutableSet.of(), ImmutableSet.of(), null)
		);
	}

	private HamletProfessions() {
	}

	private static VillagerProfession register(String name, ResourceKey<PoiType> poi, SoundEvent workSound) {
		return Registry.register(
			BuiltInRegistries.VILLAGER_PROFESSION,
			Hamlet.id(name),
			new VillagerProfession(
				name,
				holder -> holder.is(poi),
				holder -> holder.is(poi),
				ImmutableSet.of(),
				ImmutableSet.of(),
				workSound
			)
		);
	}

	/** Forces the static registration above to run during mod initialization. */
	public static void init() {
	}
}
