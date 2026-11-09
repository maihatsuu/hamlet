package maihatsuu.hamlet.client.mixin;

import java.util.Map;

import maihatsuu.hamlet.Hamlet;

import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Placeholder art: draws Hamlet's professions with vanilla textures until they have their own. */
@Mixin(VillagerProfessionLayer.class)
abstract class VillagerProfessionLayerMixin {
	@Unique
	private static final Map<ResourceLocation, ResourceLocation> PLACEHOLDERS = Map.of(
		Hamlet.id("beekeeper"), ResourceLocation.withDefaultNamespace("farmer"),
		Hamlet.id("archaeologist"), ResourceLocation.withDefaultNamespace("cartographer")
	);

	@ModifyVariable(method = "getResourceLocation", at = @At("HEAD"), argsOnly = true)
	private ResourceLocation hamlet$usePlaceholder(ResourceLocation id) {
		return PLACEHOLDERS.getOrDefault(id, id);
	}
}
