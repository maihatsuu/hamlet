package maihatsuu.hamlet.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import maihatsuu.hamlet.data.PriceLoader;
import maihatsuu.hamlet.trade.VillagerTradeMenu;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.VillagerRules;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.damagesource.DamageSource;
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
	private static final String STORAGE_KEY = "hamlet:Storage";
	/** Ticks between each half heart healed: a villager at 1 health is whole again after about 3 minutes. */
	@Unique
	private static final int REGENERATION_INTERVAL = 100;

	@Unique
	private final SimpleContainer hamlet$stock = new SimpleContainer(STOCK_SIZE);
	@Unique
	private final SimpleContainer hamlet$storage = new SimpleContainer(STORAGE_SIZE);

	@Override
	public SimpleContainer hamlet$getStock() {
		return this.hamlet$stock;
	}

	@Override
	public SimpleContainer hamlet$getStorage() {
		return this.hamlet$storage;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$saveStock(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		tag.put(STOCK_KEY, this.hamlet$stock.createTag(self.registryAccess()));
		tag.put(STORAGE_KEY, this.hamlet$storage.createTag(self.registryAccess()));
	}

	// Loading writes the villager data directly, so it doesn't pass through setVillagerData.
	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$loadAndNormalize(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		this.hamlet$stock.fromTag(tag.getList(STOCK_KEY, Tag.TAG_COMPOUND), self.registryAccess());
		if (tag.contains(STORAGE_KEY)) {
			this.hamlet$storage.fromTag(tag.getList(STORAGE_KEY, Tag.TAG_COMPOUND), self.registryAccess());
		} else {
			this.hamlet$moveUnpricedToStorage();
		}

		VillagerProfession savedProfession = self.getVillagerData().getProfession();
		self.setVillagerData(self.getVillagerData());
		self.setVillagerXp(0);
		self.overrideOffers(new MerchantOffers());

		if (self.getVillagerData().getProfession() != savedProfession && self.level() instanceof ServerLevel level) {
			self.refreshBrain(level);
		}
	}

	/**
	 * Saves from before storage existed kept players' unpriced items in the stock. Move them to storage so
	 * they stay the player's to take. Whatever doesn't fit stays in stock, for sale at the default price;
	 * spawning item entities while the villager is still loading isn't safe.
	 */
	@Unique
	private void hamlet$moveUnpricedToStorage() {
		for (int slot = 0; slot < this.hamlet$stock.getContainerSize(); slot++) {
			ItemStack stack = this.hamlet$stock.getItem(slot);
			if (!stack.isEmpty() && !PriceLoader.prices().containsKey(stack.getItem()) && this.hamlet$storage.canAddItem(stack)) {
				this.hamlet$stock.setItem(slot, this.hamlet$storage.addItem(stack));
			}
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

	// Storage holds what players and pickups gave the villager, so it drops on death, as equipment does,
	// even with doMobLoot off. Stock is work output and is lost. This includes turning into a zombie.
	@Inject(method = "die", at = @At("TAIL"))
	private void hamlet$dropStorage(DamageSource source, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		for (ItemStack stack : this.hamlet$storage.removeAllItems()) {
			self.spawnAtLocation(stack);
		}
	}

	// Vanilla villagers never heal on their own, so the odd fall or bump from pathfinding adds up.
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void hamlet$regenerate(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.isAlive() && self.getHealth() < self.getMaxHealth() && self.tickCount % REGENERATION_INTERVAL == 0) {
			self.heal(1.0F);
		}
	}

	// Vanilla stops jobless villagers trading every tick, which would close their screen at once.
	@WrapWithCondition(
		method = "customServerAiStep",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;stopTrading()V")
	)
	private boolean hamlet$keepJoblessTrading(Villager self) {
		return false;
	}

	// Villagers pick up anything that fits in their storage. This covers both pickup paths: Mob.aiStep
	// checks it before picking up, and the item sensor behind GoToWantedItem filters by it.
	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void hamlet$wantsAnything(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(this.hamlet$storage.canAddItem(stack));
	}

	@Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
	private void hamlet$pickUpIntoStorage(ItemEntity itemEntity, CallbackInfo ci) {
		ci.cancel();
		Villager self = (Villager) (Object) this;
		ItemStack stack = itemEntity.getItem();
		if (!self.wantsToPickUp(stack)) {
			return;
		}
		self.take(itemEntity, stack.getCount());
		ItemStack rest = this.hamlet$storage.addItem(stack);
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
