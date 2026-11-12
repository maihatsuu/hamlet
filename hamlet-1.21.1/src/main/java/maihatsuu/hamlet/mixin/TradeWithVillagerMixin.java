package maihatsuu.hamlet.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import maihatsuu.hamlet.villager.HamletVillager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.TradeWithVillager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Villagers share breeding food from their storage, so players can stock one villager and let it feed the
 * rest. Vanilla's sharing reads vanilla's inventory, which Hamlet villagers don't use, and only throws from
 * stacks over 24; this replaces it: a villager with more than it needs to breed throws a neighbour that's
 * short what it's missing, without dropping below enough to breed itself. Vanilla's other throws (wheat to
 * farmers, profession items) read the empty vanilla inventory and do nothing.
 */
@Mixin(TradeWithVillager.class)
abstract class TradeWithVillagerMixin {
	/** Sharing once per meeting: the neighbour hasn't picked up the throw yet, so it would look short again. */
	@Unique
	private boolean hamlet$shared;

	@Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At("TAIL"))
	private void hamlet$resetShared(CallbackInfo ci) {
		this.hamlet$shared = false;
	}

	@ModifyExpressionValue(
		method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;hasExcessFood()Z")
	)
	private boolean hamlet$noVanillaFoodSharing(boolean excess) {
		return false;
	}

	@Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At("TAIL"))
	private void hamlet$shareFood(ServerLevel level, Villager giver, long time, CallbackInfo ci) {
		if (this.hamlet$shared
			|| !(giver.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).orElse(null) instanceof Villager receiver)
			|| receiver.isBaby()
			|| giver.distanceToSqr(receiver) > 5.0) {
			return;
		}
		HamletVillager giverData = (HamletVillager) giver;
		HamletVillager receiverData = (HamletVillager) receiver;
		int shortfall = Villager.BREEDING_FOOD_THRESHOLD - receiverData.hamlet$foodPoints();
		int surplus = giverData.hamlet$foodPoints() - Villager.BREEDING_FOOD_THRESHOLD;
		if (shortfall <= 0 || surplus <= 0) {
			return;
		}

		SimpleContainer storage = giverData.hamlet$getStorage();
		for (int slot = 0; slot < storage.getContainerSize(); slot++) {
			ItemStack stack = storage.getItem(slot);
			Integer points = HamletVillager.BREEDING_FOOD.get(stack.getItem());
			if (points == null) {
				continue;
			}
			int needed = (shortfall + points - 1) / points;
			int spare = surplus / points;
			int count = Math.min(stack.getCount(), Math.min(needed, spare));
			if (count <= 0) {
				continue;
			}
			ItemStack gift = stack.copyWithCount(count);
			// A full storage can't pick it up, and it would lie on the ground for anyone to take.
			if (!receiverData.hamlet$getStorage().canAddItem(gift)) {
				return;
			}
			stack.shrink(count);
			storage.setChanged();
			BehaviorUtils.throwItem(giver, gift, receiver.position());
			this.hamlet$shared = true;
			return;
		}
	}
}
