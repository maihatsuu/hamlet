package maihatsuu.hamlet.trade;

import java.util.HashMap;
import java.util.Map;

import maihatsuu.hamlet.data.Price;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/** Sent to the client when the trade screen opens. */
public record TradeOpeningData(Map<Item, Price> prices) {
	public static final StreamCodec<RegistryFriendlyByteBuf, TradeOpeningData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.map(size -> new HashMap<>(), ByteBufCodecs.registry(Registries.ITEM), Price.STREAM_CODEC),
		TradeOpeningData::prices,
		TradeOpeningData::new
	);
}
