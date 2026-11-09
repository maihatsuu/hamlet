package maihatsuu.hamlet.data;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

/** Datapack-defined balance values for one profession. Prices are in emeralds per item. */
public record ProfessionData(Map<Item, Integer> prices, int workInterval) {
	/** One in-game hour. */
	public static final int DEFAULT_WORK_INTERVAL = 1000;
	public static final ProfessionData EMPTY = new ProfessionData(Map.of(), DEFAULT_WORK_INTERVAL);

	public static final Codec<ProfessionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), ExtraCodecs.POSITIVE_INT)
			.optionalFieldOf("prices", Map.of())
			.forGetter(ProfessionData::prices),
		ExtraCodecs.POSITIVE_INT
			.optionalFieldOf("work_interval", DEFAULT_WORK_INTERVAL)
			.forGetter(ProfessionData::workInterval)
	).apply(instance, ProfessionData::new));
}
