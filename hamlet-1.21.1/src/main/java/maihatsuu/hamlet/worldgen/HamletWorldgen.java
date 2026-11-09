package maihatsuu.hamlet.worldgen;

import java.util.List;

import maihatsuu.hamlet.Hamlet;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class HamletWorldgen {
	private static final GenerationStep.Decoration STEP = GenerationStep.Decoration.UNDERGROUND_ORES;

	/** Copies of vanilla's four diamond features, so emerald matches diamond's rate and depth. */
	private static final List<ResourceKey<PlacedFeature>> EMERALD_ORE = placed(
		"ore_emerald", "ore_emerald_medium", "ore_emerald_large", "ore_emerald_buried"
	);
	/** Diamond-sized veins at vanilla emerald's height range, up in the peaks. */
	private static final List<ResourceKey<PlacedFeature>> MOUNTAIN_EMERALD_ORE = placed(
		"ore_emerald_mountain", "ore_emerald_mountain_medium", "ore_emerald_mountain_large", "ore_emerald_mountain_buried"
	);

	private HamletWorldgen() {
	}

	public static void init() {
		BiomeModifications.create(Hamlet.id("emerald_ore"))
			.add(ModificationPhase.REMOVALS, BiomeSelectors.foundInOverworld(), context ->
				context.getGenerationSettings().removeFeature(STEP, OrePlacements.ORE_EMERALD))
			.add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInOverworld(), context ->
				EMERALD_ORE.forEach(feature -> context.getGenerationSettings().addFeature(STEP, feature)))
			.add(ModificationPhase.ADDITIONS, BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN), context ->
				MOUNTAIN_EMERALD_ORE.forEach(feature -> context.getGenerationSettings().addFeature(STEP, feature)));
	}

	private static List<ResourceKey<PlacedFeature>> placed(String... names) {
		return List.of(names).stream()
			.map(name -> ResourceKey.create(Registries.PLACED_FEATURE, Hamlet.id(name)))
			.toList();
	}
}
