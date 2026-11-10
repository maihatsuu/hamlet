package maihatsuu.hamlet.client;

import maihatsuu.hamlet.client.render.FishingBobberRenderer;
import maihatsuu.hamlet.client.screen.VillagerTradeScreen;
import maihatsuu.hamlet.entity.HamletEntities;
import maihatsuu.hamlet.trade.VillagerTradeMenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.gui.screens.MenuScreens;

public class HamletClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(VillagerTradeMenu.TYPE, VillagerTradeScreen::new);
		EntityRendererRegistry.register(HamletEntities.FISHING_BOBBER, FishingBobberRenderer::new);
	}
}
