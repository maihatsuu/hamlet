package maihatsuu.hamlet.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import maihatsuu.hamlet.data.ProfessionDataLoader;

import net.minecraft.Util;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTrader.class)
abstract class WanderingTraderMixin {
	@Unique
	private static final int OFFER_COUNT = 6;
	@Unique
	private static final int MAX_USES = 8;

	// The trader's data file doubles as its catalogue: each visit it offers a random selection of those items.
	@Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
	private void hamlet$sellCatalogue(CallbackInfo ci) {
		WanderingTrader self = (WanderingTrader) (Object) this;
		List<Map.Entry<Item, Integer>> catalogue = new ArrayList<>(ProfessionDataLoader.getWanderingTrader().prices().entrySet());
		Util.shuffle(catalogue, self.getRandom());

		MerchantOffers offers = self.getOffers();
		catalogue.stream().limit(OFFER_COUNT).forEach(entry -> offers.add(
			new MerchantOffer(new ItemCost(Items.EMERALD, entry.getValue()), new ItemStack(entry.getKey()), MAX_USES, 1, 0.05F)
		));
		ci.cancel();
	}
}
