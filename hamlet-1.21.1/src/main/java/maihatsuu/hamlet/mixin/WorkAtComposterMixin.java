package maihatsuu.hamlet.mixin;

import net.minecraft.world.entity.ai.behavior.WorkAtComposter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorkAtComposter.class)
abstract class WorkAtComposterMixin {
	@Inject(method = "makeBread", at = @At("HEAD"), cancellable = true)
	private void hamlet$noBread(CallbackInfo ci) {
		ci.cancel();
	}
}
