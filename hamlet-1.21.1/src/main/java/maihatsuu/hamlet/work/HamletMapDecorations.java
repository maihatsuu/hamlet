package maihatsuu.hamlet.work;

import maihatsuu.hamlet.Hamlet;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

/**
 * Map icons for the explorer maps vanilla doesn't have. Each icon's texture is
 * {@code assets/hamlet/textures/map/decorations/<name>.png}, 8×8 like vanilla's.
 */
public final class HamletMapDecorations {
	public static final Holder<MapDecorationType> ANCIENT_CITY = register("ancient_city", 0x0E4B5A);
	public static final Holder<MapDecorationType> DESERT_PYRAMID = register("desert_pyramid", 0xD8B26A);

	private HamletMapDecorations() {
	}

	/** Registers the icons, before the registries freeze. */
	public static void init() {
	}

	/** Like vanilla's explorer map icons: shown in item frames, tinting the map item, not counted as a player. */
	private static Holder<MapDecorationType> register(String name, int mapColor) {
		ResourceKey<MapDecorationType> key = ResourceKey.create(Registries.MAP_DECORATION_TYPE, Hamlet.id(name));
		return Registry.registerForHolder(BuiltInRegistries.MAP_DECORATION_TYPE, key, new MapDecorationType(Hamlet.id(name), true, mapColor, true, false));
	}
}
