package maihatsuu.hamlet.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Zombie;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Zombie.class)
abstract class ZombieMixin {
	// Hard is the only difficulty on which vanilla always converts the villager.
	@ModifyExpressionValue(
		method = "killedEntity",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;")
	)
	private Difficulty hamlet$alwaysZombify(Difficulty original) {
		return Difficulty.HARD;
	}
}
