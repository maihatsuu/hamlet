package maihatsuu.hamlet.mixin;

import net.minecraft.world.entity.ai.behavior.HarvestFarmland;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HarvestFarmland.class)
abstract class HarvestFarmlandMixin {
	// Farmers harvest through their job instead, which keeps the player's crop layout.
	@Inject(
		method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void hamlet$noVanillaHarvest(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
