package maihatsuu.hamlet.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import maihatsuu.hamlet.trade.VillagerTradeMenu;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.VillagerRules;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
abstract class VillagerMixin implements HamletVillager {
	@Unique
	private static final String STOCK_KEY = "hamlet:Stock";

	@Unique
	private final SimpleContainer hamlet$stock = new SimpleContainer(STOCK_SIZE);

	@Unique
	private static final String LAST_WORKED_KEY = "hamlet:LastWorked";

	@Unique
	private long hamlet$lastWorked = NEVER_WORKED;

	@Override
	public SimpleContainer hamlet$getStock() {
		return this.hamlet$stock;
	}

	@Override
	public long hamlet$getLastWorked() {
		return this.hamlet$lastWorked;
	}

	@Override
	public void hamlet$setLastWorked(long gameTime) {
		this.hamlet$lastWorked = gameTime;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$saveStock(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		tag.put(STOCK_KEY, this.hamlet$stock.createTag(self.registryAccess()));
		tag.putLong(LAST_WORKED_KEY, this.hamlet$lastWorked);
	}

	// Loading writes the villager data directly, so it doesn't pass through setVillagerData.
	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$loadAndNormalize(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		this.hamlet$stock.fromTag(tag.getList(STOCK_KEY, Tag.TAG_COMPOUND), self.registryAccess());
		this.hamlet$lastWorked = tag.contains(LAST_WORKED_KEY) ? tag.getLong(LAST_WORKED_KEY) : NEVER_WORKED;

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
	// becomes "has nothing to trade" and vanilla's head shake and early return still apply.
	@ModifyExpressionValue(
		method = "mobInteract",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffers;isEmpty()Z")
	)
	private boolean hamlet$refuseTrade(boolean noOffers) {
		VillagerProfession profession = ((Villager) (Object) this).getVillagerData().getProfession();
		return profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT;
	}

	// Covers both pickup paths: Mob.aiStep checks this before picking items up, and the item sensor
	// behind GoToWantedItem filters by it.
	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void hamlet$noItemPickup(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
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
