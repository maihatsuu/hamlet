package maihatsuu.hamlet.mixin;

import net.minecraft.world.entity.ai.behavior.TradeWithVillager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TradeWithVillager.class)
abstract class TradeWithVillagerMixin {
	@Inject(
		method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void hamlet$noFoodSharing(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
