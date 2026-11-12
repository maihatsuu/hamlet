package maihatsuu.hamlet.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * What an item sells for: {@code count} of it for {@code emeralds}. In price files either a plain number,
 * the emeralds for one item, or {@code {"emeralds": M, "count": N}} for a batch, such as 8 blocks for 1.
 */
public record Price(int emeralds, int count) {
	/** What an item with no price set sells for, such as modded fishing loot. */
	public static final Price DEFAULT = new Price(1, 1);

	private static final Codec<Price> BATCH = RecordCodecBuilder.create(instance -> instance.group(
		ExtraCodecs.POSITIVE_INT.fieldOf("emeralds").forGetter(Price::emeralds),
		Codec.intRange(1, 64).fieldOf("count").forGetter(Price::count)
	).apply(instance, Price::new));

	public static final Codec<Price> CODEC = Codec.either(ExtraCodecs.POSITIVE_INT, BATCH).xmap(
		either -> either.map(emeralds -> new Price(emeralds, 1), price -> price),
		price -> price.count == 1 ? Either.left(price.emeralds) : Either.right(price)
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, Price> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Price::emeralds,
		ByteBufCodecs.VAR_INT, Price::count,
		Price::new
	);
}
