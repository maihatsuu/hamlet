package maihatsuu.hamlet.data;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import maihatsuu.hamlet.Hamlet;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Loads Hamlet's data: prices, all in emeralds per item, and what clerics brew.
 * <ul>
 *   <li>{@code data/<namespace>/hamlet/prices/*.json}, each {@code {"prices": {item: price}}}: one global
 *   list for every villager, whatever its profession. All such files are merged, so a datapack or mod can
 *   add prices by shipping its own file. Prices don't depend on profession, so a villager that changes or
 *   loses its job keeps its stock for sale.</li>
 *   <li>{@code data/<namespace>/hamlet/wandering_trader.json}, {@code {"catalogue": {item: price}}}: what the
 *   wandering trader picks its offers from.</li>
 *   <li>{@code data/<namespace>/hamlet/cleric_potions.json}, {@code {"potions": [potion, ...]}}: what a cleric
 *   brews, picked at random. All such files are merged.</li>
 * </ul>
 */
public final class PriceLoader extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
	private static final ResourceLocation ID = Hamlet.id("prices");
	private static final String PRICES_DIRECTORY = "prices/";
	private static final String WANDERING_TRADER = "wandering_trader";
	private static final String CLERIC_POTIONS = "cleric_potions";
	private static final Codec<Map<Item, Integer>> ITEM_PRICES = Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), ExtraCodecs.POSITIVE_INT);
	private static final Codec<Map<Item, Integer>> PRICES_FILE = ITEM_PRICES.fieldOf("prices").codec();
	private static final Codec<Map<Item, Integer>> CATALOGUE_FILE = ITEM_PRICES.fieldOf("catalogue").codec();
	private static final Codec<List<Holder<Potion>>> POTIONS_FILE = BuiltInRegistries.POTION.holderByNameCodec().listOf().fieldOf("potions").codec();

	private static volatile Map<Item, Integer> prices = Map.of();
	private static volatile Map<Item, Integer> wanderingTraderCatalogue = Map.of();
	private static volatile List<Holder<Potion>> clericPotions = List.of();

	public PriceLoader() {
		super(new Gson(), "hamlet");
	}

	/** What an item with no price set sells for, such as modded fishing loot. */
	public static final int DEFAULT_PRICE = 1;

	/** Every villager's prices; items missing from here sell at {@link #DEFAULT_PRICE}. */
	public static Map<Item, Integer> prices() {
		return prices;
	}

	/** The price of one item in emeralds, from {@code prices}, which is synced to the client with the screen. */
	public static int priceOf(Map<Item, Integer> prices, Item item) {
		return prices.getOrDefault(item, DEFAULT_PRICE);
	}

	public static Map<Item, Integer> wanderingTraderCatalogue() {
		return wanderingTraderCatalogue;
	}

	/** The potions a cleric picks from at random each time it brews. */
	public static List<Holder<Potion>> clericPotions() {
		return clericPotions;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<Item, Integer> loadedPrices = new HashMap<>();
		Map<Item, Integer> loadedCatalogue = new HashMap<>();
		Set<Holder<Potion>> loadedPotions = new LinkedHashSet<>();
		// Sorted, so when two files price the same item the result doesn't depend on load order.
		new TreeMap<>(entries).forEach((id, json) -> {
			if (id.getPath().startsWith(PRICES_DIRECTORY)) {
				PRICES_FILE.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(error -> Hamlet.LOGGER.error("Skipping prices {}: {}", id, error))
					.ifPresent(file -> file.forEach((item, price) -> {
						Integer previous = loadedPrices.put(item, price);
						if (previous != null && !previous.equals(price)) {
							Hamlet.LOGGER.warn("{} prices {} at {}, replacing {}", id, BuiltInRegistries.ITEM.getKey(item), price, previous);
						}
					}));
			} else if (id.getPath().equals(WANDERING_TRADER)) {
				CATALOGUE_FILE.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(error -> Hamlet.LOGGER.error("Skipping wandering trader catalogue {}: {}", id, error))
					.ifPresent(loadedCatalogue::putAll);
			} else if (id.getPath().equals(CLERIC_POTIONS)) {
				POTIONS_FILE.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(error -> Hamlet.LOGGER.error("Skipping cleric potions {}: {}", id, error))
					.ifPresent(loadedPotions::addAll);
			}
		});
		prices = Map.copyOf(loadedPrices);
		wanderingTraderCatalogue = Map.copyOf(loadedCatalogue);
		clericPotions = List.copyOf(loadedPotions);
		Hamlet.LOGGER.info("Loaded {} villager prices, {} wandering trader offers and {} cleric potions",
			prices.size(), wanderingTraderCatalogue.size(), clericPotions.size());
	}

	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}
}
