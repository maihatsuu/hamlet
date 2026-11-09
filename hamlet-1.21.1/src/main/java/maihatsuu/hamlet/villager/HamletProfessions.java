package maihatsuu.hamlet.villager;

import com.google.common.collect.ImmutableSet;

import maihatsuu.hamlet.Hamlet;

import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

public final class HamletProfessions {
	public static final ResourceKey<PoiType> ARCHAEOLOGIST_POI =
		ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Hamlet.id("archaeologist"));

	public static final VillagerProfession BEEKEEPER;
	public static final VillagerProfession ARCHAEOLOGIST;

	static {
		PointOfInterestHelper.register(ARCHAEOLOGIST_POI.location(), 1, 1, Blocks.DECORATED_POT);

		// A block state can only belong to one POI type, so the beekeeper has to share vanilla's beehive POI.
		BEEKEEPER = register("beekeeper", PoiTypes.BEEHIVE, SoundEvents.BEEHIVE_WORK);
		ARCHAEOLOGIST = register("archaeologist", ARCHAEOLOGIST_POI, SoundEvents.BRUSH_GENERIC);
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
