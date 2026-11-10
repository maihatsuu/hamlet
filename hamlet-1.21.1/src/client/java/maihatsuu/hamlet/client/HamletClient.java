package maihatsuu.hamlet.client;

import maihatsuu.hamlet.client.render.FishingBobberRenderer;
import maihatsuu.hamlet.client.screen.VillagerTradeScreen;
import maihatsuu.hamlet.entity.FishingBobber;
import maihatsuu.hamlet.entity.HamletEntities;
import maihatsuu.hamlet.trade.VillagerTradeMenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Items;

public class HamletClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(VillagerTradeMenu.TYPE, VillagerTradeScreen::new);
		EntityRendererRegistry.register(HamletEntities.FISHING_BOBBER, FishingBobberRenderer::new);
		registerRodCastTexture();
	}

	/**
	 * Replaces vanilla's "cast" property for the fishing rod, which picks the cast texture only for players
	 * with a hook out, so that villagers with a bobber out get it too. The player half is vanilla's logic.
	 */
	private static void registerRodCastTexture() {
		ItemProperties.register(Items.FISHING_ROD, ResourceLocation.withDefaultNamespace("cast"), (stack, level, entity, seed) -> {
			if (entity == null) {
				return 0.0F;
			}
			boolean mainHand = entity.getMainHandItem() == stack;
			boolean offHand = entity.getOffhandItem() == stack && !(entity.getMainHandItem().getItem() instanceof FishingRodItem);
			if (!mainHand && !offHand) {
				return 0.0F;
			}
			boolean cast = entity instanceof Player player
				? player.fishing != null
				: entity instanceof Villager && FishingBobber.hasBobberOut(entity);
			return cast ? 1.0F : 0.0F;
		});
	}
}
