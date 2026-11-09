package maihatsuu.hamlet.mixin;

import java.util.Optional;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import maihatsuu.hamlet.trade.VillagerTradeMenu;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.ReadinessState;
import maihatsuu.hamlet.villager.VillagerReadiness;
import maihatsuu.hamlet.villager.VillagerRules;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
abstract class VillagerMixin implements HamletVillager {
	@Unique
	private static final String STOCK_KEY = "hamlet:Stock";

	@Unique
	private final SimpleContainer hamlet$stock = new SimpleContainer(STOCK_SIZE);

	@Unique
	private static final String READINESS_KEY = "hamlet:Readiness";

	@Unique
	private final ReadinessState hamlet$readiness = new ReadinessState();

	@Override
	public SimpleContainer hamlet$getStock() {
		return this.hamlet$stock;
	}

	@Override
	public ReadinessState hamlet$getReadiness() {
		return this.hamlet$readiness;
	}

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void hamlet$tickReadiness(CallbackInfo ci) {
		VillagerReadiness.tick((Villager) (Object) this);
	}

	@Inject(method = "setVillagerData", at = @At("HEAD"))
	private void hamlet$restartGraceOnNewJob(VillagerData data, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		VillagerProfession profession = data.getProfession();
		if (profession != VillagerProfession.NONE && profession != self.getVillagerData().getProfession()) {
			this.hamlet$readiness.restartGrace(self.level().getGameTime());
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$saveStock(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		tag.put(STOCK_KEY, this.hamlet$stock.createTag(self.registryAccess()));
		tag.put(READINESS_KEY, this.hamlet$readiness.save());
	}

	// Loading writes the villager data directly, so it doesn't pass through setVillagerData.
	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$loadAndNormalize(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		this.hamlet$stock.fromTag(tag.getList(STOCK_KEY, Tag.TAG_COMPOUND), self.registryAccess());
		this.hamlet$readiness.load(tag.getCompound(READINESS_KEY));

		VillagerProfession savedProfession = self.getVillagerData().getProfession();
		self.setVillagerData(self.getVillagerData());
		self.setVillagerXp(0);
		self.overrideOffers(new MerchantOffers());

		if (self.getVillagerData().getProfession() != savedProfession && self.level() instanceof ServerLevel level) {
			self.refreshBrain(level);
		}
	}

	@ModifyVariable(method = "setVillagerData", at = @At("HEAD"), argsOnly = true)
	private VillagerData hamlet$normalize(VillagerData data) {
		return VillagerRules.normalize(data);
	}

	// Vanilla refuses to trade when the villager has no offers. Hamlet never gives it any, so this check
	// becomes "can't trade right now" and vanilla's head shake and early return still apply.
	@ModifyExpressionValue(
		method = "mobInteract",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffers;isEmpty()Z")
	)
	private boolean hamlet$refuseTrade(boolean noOffers, Player player, InteractionHand hand) {
		Villager self = (Villager) (Object) this;
		VillagerProfession profession = self.getVillagerData().getProfession();
		if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
			return true;
		}

		Optional<Component> refusal = VillagerReadiness.refusal(self);
		if (refusal.isPresent() && hand == InteractionHand.MAIN_HAND) {
			player.displayClientMessage(refusal.get(), true);
		}
		return refusal.isPresent();
	}

	@Inject(method = "startTrading", at = @At("HEAD"), cancellable = true)
	private void hamlet$openTradeMenu(Player player, CallbackInfo ci) {
		VillagerTradeMenu.open((Villager) (Object) this, player);
		ci.cancel();
	}

	@Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
	private void hamlet$noVanillaTrades(CallbackInfo ci) {
		ci.cancel();
	}
}
