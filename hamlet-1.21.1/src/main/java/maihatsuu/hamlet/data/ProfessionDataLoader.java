package maihatsuu.hamlet.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import maihatsuu.hamlet.Hamlet;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Loads {@code data/<namespace>/hamlet/professions/<profession>.json}, keyed by profession ID. */
public final class ProfessionDataLoader extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
	private static final ResourceLocation ID = Hamlet.id("professions");
	private static final ResourceLocation WANDERING_TRADER = Hamlet.id("wandering_trader");

	private static volatile Map<ResourceLocation, ProfessionData> data = Map.of();

	public ProfessionDataLoader() {
		super(new Gson(), "hamlet/professions");
	}

	public static ProfessionData get(VillagerProfession profession) {
		return data.getOrDefault(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession), ProfessionData.EMPTY);
	}

	/** The wandering trader isn't a registered profession, but is configured the same way. */
	public static ProfessionData getWanderingTrader() {
		return data.getOrDefault(WANDERING_TRADER, ProfessionData.EMPTY);
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<ResourceLocation, ProfessionData> loaded = new HashMap<>();
		entries.forEach((id, json) -> ProfessionData.CODEC.parse(JsonOps.INSTANCE, json)
			.resultOrPartial(error -> Hamlet.LOGGER.error("Skipping profession data {}: {}", id, error))
			.ifPresent(parsed -> {
				if (!id.equals(WANDERING_TRADER) && !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) {
					Hamlet.LOGGER.warn("Profession data {} doesn't match a registered profession", id);
				}
				loaded.put(id, parsed);
			}));
		data = Map.copyOf(loaded);
		Hamlet.LOGGER.info("Loaded data for {} professions", loaded.size());
	}

	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}
}
