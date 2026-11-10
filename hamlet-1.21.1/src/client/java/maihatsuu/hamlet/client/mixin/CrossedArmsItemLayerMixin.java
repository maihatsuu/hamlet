package maihatsuu.hamlet.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Villagers draw held items at their crossed arms, lying flat. A fishing rod is drawn the way a player
 * holds one instead: from its crossed hands with the arm angled forward, using the item's third-person
 * hand transform, so it points out over the water. These steps mirror ItemInHandLayer.renderArmWithItem.
 */
@Mixin(CrossedArmsItemLayer.class)
abstract class CrossedArmsItemLayerMixin {
	// Where the invisible arm pivots, in model pixels: a player's shoulder height, but sideways so that,
	// after the hand offset below and the rod model's own third-person offset, the rod lines up with the
	// villager's nose, where its crossed hands meet.
	@Unique
	private static final float SHOULDER_X = 2.0F / 16.0F;
	@Unique
	private static final float SHOULDER_Y = 2.0F / 16.0F;
	/** How far forward the invisible arm is raised, so the hand ends up in front of the crossed arms. */
	@Unique
	private static final float ARM_PITCH_DEGREES = -35.0F;

	@Shadow
	@Final
	private ItemInHandRenderer itemInHandRenderer;

	@Inject(
		method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void hamlet$holdRodOut(
		PoseStack poseStack,
		MultiBufferSource buffers,
		int light,
		LivingEntity entity,
		float limbSwing,
		float limbSwingAmount,
		float partialTick,
		float ageInTicks,
		float netHeadYaw,
		float headPitch,
		CallbackInfo ci
	) {
		ItemStack stack = entity.getMainHandItem();
		if (!(entity instanceof Villager) || !stack.is(Items.FISHING_ROD)) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(SHOULDER_X, SHOULDER_Y, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(ARM_PITCH_DEGREES));
		poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.translate(1.0F / 16.0F, 0.125F, -0.625F);
		this.itemInHandRenderer.renderItem(entity, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, buffers, light);
		poseStack.popPose();
		ci.cancel();
	}
}
