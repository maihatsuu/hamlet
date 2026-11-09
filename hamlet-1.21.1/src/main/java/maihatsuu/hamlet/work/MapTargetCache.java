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
 * Remembers structure searches by region, because each one can stall the server. Cache misses are also
 * rate-limited per level, so several cartographers can't trigger a burst of searches.
 */
final class MapTargetCache extends SavedData {
	private static final String NAME = "hamlet_map_targets";
	// Vanilla's no-op fixer type; a null type crashes when the file is read.
	private static final SavedData.Factory<MapTargetCache> FACTORY =
		new SavedData.Factory<>(MapTargetCache::new, MapTargetCache::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	private static final int REGION_SHIFT = 8;
	private static final int SEARCH_RADIUS_CHUNKS = 100;
	private static final long MIN_TICKS_BETWEEN_SEARCHES = 1200L;
	private static final long NOT_FOUND = Long.MIN_VALUE;

	private final Map<String, Long> targets = new HashMap<>();
	private long lastSearch = Long.MIN_VALUE / 2;

	private MapTargetCache() {
	}

	static MapTargetCache get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
	}

	/** Empty if there's no such structure, or if a search would have to wait. */
	Optional<BlockPos> find(ServerLevel level, TagKey<Structure> structure, BlockPos from) {
		String key = structure.location() + "/" + (from.getX() >> REGION_SHIFT) + "/" + (from.getZ() >> REGION_SHIFT);
		Long cached = this.targets.get(key);
		if (cached != null) {
			return cached == NOT_FOUND ? Optional.empty() : Optional.of(BlockPos.of(cached));
		}

		long now = level.getGameTime();
		if (now - this.lastSearch < MIN_TICKS_BETWEEN_SEARCHES) {
			return Optional.empty();
		}

		this.lastSearch = now;
		BlockPos found = level.findNearestMapStructure(structure, from, SEARCH_RADIUS_CHUNKS, true);
		this.targets.put(key, found == null ? NOT_FOUND : found.asLong());
		this.setDirty();
		return Optional.ofNullable(found);
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		this.targets.forEach(tag::putLong);
		return tag;
	}

	private static MapTargetCache load(CompoundTag tag, HolderLookup.Provider registries) {
		MapTargetCache cache = new MapTargetCache();
		tag.getAllKeys().forEach(key -> cache.targets.put(key, tag.getLong(key)));
		return cache;
	}
}
