package maihatsuu.hamlet.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import maihatsuu.hamlet.trade.VillagerTradeMenu;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.VillagerRules;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.item.ItemEntity;
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

	@Override
	public SimpleContainer hamlet$getStock() {
		return this.hamlet$stock;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$saveStock(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		tag.put(STOCK_KEY, this.hamlet$stock.createTag(self.registryAccess()));
	}

	// Loading writes the villager data directly, so it doesn't pass through setVillagerData.
	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$loadAndNormalize(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		this.hamlet$stock.fromTag(tag.getList(STOCK_KEY, Tag.TAG_COMPOUND), self.registryAccess());

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

	// Vanilla refuses to trade when the villager has no offers, and Hamlet never gives it any. Every adult
	// opens the screen instead, jobless or not, to show its inventory; babies still refuse, earlier on.
	@ModifyExpressionValue(
		method = "mobInteract",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffers;isEmpty()Z")
	)
	private boolean hamlet$alwaysOpen(boolean noOffers) {
		return false;
	}

	// Vanilla stops jobless villagers trading every tick, which would close their screen at once.
	@WrapWithCondition(
		method = "customServerAiStep",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;stopTrading()V")
	)
	private boolean hamlet$keepJoblessTrading(Villager self) {
		return false;
	}

	// Villagers pick up anything that fits in Hamlet's inventory. This covers both pickup paths: Mob.aiStep
	// checks it before picking up, and the item sensor behind GoToWantedItem filters by it.
	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void hamlet$wantsAnything(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(this.hamlet$stock.canAddItem(stack));
	}

	@Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
	private void hamlet$pickUpIntoStock(ItemEntity itemEntity, CallbackInfo ci) {
		ci.cancel();
		Villager self = (Villager) (Object) this;
		ItemStack stack = itemEntity.getItem();
		if (!self.wantsToPickUp(stack)) {
			return;
		}
		self.take(itemEntity, stack.getCount());
		ItemStack rest = this.hamlet$stock.addItem(stack);
		if (rest.isEmpty()) {
			itemEntity.discard();
		} else {
			stack.setCount(rest.getCount());
		}
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
