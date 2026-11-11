package maihatsuu.hamlet.data;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

/** Datapack-defined balance values for one profession. Prices are in emeralds per item. */
public record ProfessionData(Map<Item, Integer> prices) {
	public static final int DEFAULT_PRICE = 1;
	public static final ProfessionData EMPTY = new ProfessionData(Map.of());

	public static final Codec<ProfessionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), ExtraCodecs.POSITIVE_INT)
			.optionalFieldOf("prices", Map.of())
			.forGetter(ProfessionData::prices)
	).apply(instance, ProfessionData::new));
}
