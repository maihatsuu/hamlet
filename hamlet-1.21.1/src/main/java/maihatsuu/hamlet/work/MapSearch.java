package maihatsuu.hamlet.work;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Structure searches for cartographers' maps. Each one can stall the server, so they're rate-limited per
 * level, and a search that finds nothing is remembered for a day, per region, so a village far from any
 * desert doesn't keep searching for pyramids.
 * <p>
 * Found structures aren't cached: vanilla marks each one it returns as charted and skips it from then on,
 * so every search finds the next uncharted structure, as vanilla's explorer maps do. Only search when the
 * map will be kept, or the structure is wasted.
 */
final class MapSearch extends SavedData {
	// Kept from when this cached found structures, so old saves load; their entries are ignored.
	private static final String NAME = "hamlet_map_targets";
	// Vanilla's no-op fixer type; a null type crashes when the file is read.
	private static final SavedData.Factory<MapSearch> FACTORY =
		new SavedData.Factory<>(MapSearch::new, MapSearch::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
	private static final String FAILED_PREFIX = "failed/";

	private static final int REGION_SHIFT = 8;
	private static final int SEARCH_RADIUS_CHUNKS = 100;
	private static final long MIN_TICKS_BETWEEN_SEARCHES = 1200L;
	private static final long RETRY_FAILED_AFTER = 24000L;

	/** When each failed search, by structure tag and region, may be tried again. */
	private final Map<String, Long> failedUntil = new HashMap<>();
	private long lastSearch = Long.MIN_VALUE / 2;

	private MapSearch() {
	}

	static MapSearch get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
	}

	/**
	 * The nearest uncharted such structure, now charted. Empty if there's none within range, or if a search
	 * would have to wait.
	 */
	Optional<BlockPos> find(ServerLevel level, TagKey<Structure> structure, BlockPos from) {
		String key = structure.location() + "/" + (from.getX() >> REGION_SHIFT) + "/" + (from.getZ() >> REGION_SHIFT);
		long now = level.getGameTime();
		Long retry = this.failedUntil.get(key);
		if (retry != null && now < retry || now - this.lastSearch < MIN_TICKS_BETWEEN_SEARCHES) {
			return Optional.empty();
		}

		this.lastSearch = now;
		BlockPos found = level.findNearestMapStructure(structure, from, SEARCH_RADIUS_CHUNKS, true);
		if (found == null) {
			this.failedUntil.put(key, now + RETRY_FAILED_AFTER);
		} else {
			this.failedUntil.remove(key);
		}
		this.setDirty();
		return Optional.ofNullable(found);
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		this.failedUntil.forEach((key, until) -> tag.putLong(FAILED_PREFIX + key, until));
		return tag;
	}

	private static MapSearch load(CompoundTag tag, HolderLookup.Provider registries) {
		MapSearch search = new MapSearch();
		for (String key : tag.getAllKeys()) {
			if (key.startsWith(FAILED_PREFIX)) {
				search.failedUntil.put(key.substring(FAILED_PREFIX.length()), tag.getLong(key));
			}
		}
		return search;
	}
}
