package maihatsuu.hamlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import maihatsuu.hamlet.entity.FishingBobber;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** Ported from vanilla's {@code FishingHookRenderer}, with the line running to the villager's rod instead. */
public class FishingBobberRenderer extends EntityRenderer<FishingBobber> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
	private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE);
	private static final int LINE_SEGMENTS = 16;
	// Where the rod tip sits relative to the villager's feet, matching CrossedArmsItemLayerMixin's pose:
	// held out from its crossed hands and angled up over the water.
	private static final double ROD_FORWARD = 0.85;
	private static final double ROD_RIGHT = 0.0;
	private static final double ROD_HEIGHT = 1.5;

	public FishingBobberRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(FishingBobber bobber, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		Villager owner = bobber.getOwner();
		if (owner == null) {
			return;
		}

		poseStack.pushPose();
		poseStack.pushPose();
		poseStack.scale(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
		PoseStack.Pose pose = poseStack.last();
		VertexConsumer quad = buffers.getBuffer(RENDER_TYPE);
		vertex(quad, pose, light, 0.0F, 0, 0, 1);
		vertex(quad, pose, light, 1.0F, 0, 1, 1);
		vertex(quad, pose, light, 1.0F, 1, 1, 0);
		vertex(quad, pose, light, 0.0F, 1, 0, 0);
		poseStack.popPose();

		Vec3 rodTip = rodTip(owner, partialTick);
		Vec3 attach = bobber.getPosition(partialTick).add(0.0, 0.25, 0.0);
		float dx = (float) (rodTip.x - attach.x);
		float dy = (float) (rodTip.y - attach.y);
		float dz = (float) (rodTip.z - attach.z);
		VertexConsumer line = buffers.getBuffer(RenderType.lineStrip());
		PoseStack.Pose linePose = poseStack.last();
		for (int segment = 0; segment <= LINE_SEGMENTS; segment++) {
			stringVertex(dx, dy, dz, line, linePose, fraction(segment), fraction(segment + 1));
		}

		poseStack.popPose();
		super.render(bobber, yaw, partialTick, poseStack, buffers, light);
	}

	private static Vec3 rodTip(Villager villager, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, villager.yBodyRotO, villager.yBodyRot) * Mth.DEG_TO_RAD;
		double scale = villager.getScale();
		double sin = Mth.sin(bodyYaw);
		double cos = Mth.cos(bodyYaw);
		return villager.getPosition(partialTick).add(
			(-sin * ROD_FORWARD - cos * ROD_RIGHT) * scale,
			ROD_HEIGHT * scale,
			(cos * ROD_FORWARD - sin * ROD_RIGHT) * scale
		);
	}

	private static float fraction(int segment) {
		return (float) segment / LINE_SEGMENTS;
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
		consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
			.setColor(-1)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/** Vanilla's sagging line: each vertex lies on a parabola from the bobber to the rod tip. */
	private static void stringVertex(float dx, float dy, float dz, VertexConsumer consumer, PoseStack.Pose pose, float from, float to) {
		float x = dx * from;
		float y = dy * (from * from + from) * 0.5F + 0.25F;
		float z = dz * from;
		float nx = dx * to - x;
		float ny = dy * (to * to + to) * 0.5F + 0.25F - y;
		float nz = dz * to - z;
		float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		consumer.addVertex(pose, x, y, z).setColor(-16777216).setNormal(pose, nx / length, ny / length, nz / length);
	}

	@Override
	public ResourceLocation getTextureLocation(FishingBobber bobber) {
		return TEXTURE;
	}
}
