package maihatsuu.hamlet.mixin;

import java.util.Optional;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import maihatsuu.hamlet.entity.FishingBobber;

import net.minecraft.advancements.critereon.FishingHookPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a fisherman's bobber count as a fishing hook in loot conditions, so vanilla's treasure entry and any
 * mod's open-water checks in the fishing loot tables work for villagers as they do for players.
 */
@Mixin(FishingHookPredicate.class)
abstract class FishingHookPredicateMixin {
	@Shadow
	@Final
	private Optional<Boolean> inOpenWater;

	@ModifyReturnValue(method = "matches", at = @At("RETURN"))
	private boolean hamlet$matchBobber(boolean matches, Entity entity, ServerLevel level, Vec3 position) {
		if (entity instanceof FishingBobber bobber && this.inOpenWater.isPresent()) {
			return this.inOpenWater.get() == bobber.isOpenWaterFishing();
		}
		return matches;
	}
}
