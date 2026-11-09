package maihatsuu.hamlet.mixin;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PoiTypes.class)
abstract class PoiTypesMixin {
	// Vanilla gives hives no tickets, so villagers can't claim them. Bees ignore tickets when finding a hive.
	@ModifyVariable(method = "register", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static int hamlet$claimableBeehives(int maxTickets, Registry<PoiType> registry, ResourceKey<PoiType> key) {
		return key == PoiTypes.BEEHIVE ? 1 : maxTickets;
	}
}
