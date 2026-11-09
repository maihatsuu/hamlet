package maihatsuu.hamlet.client;

import maihatsuu.hamlet.client.screen.VillagerTradeScreen;
import maihatsuu.hamlet.trade.VillagerTradeMenu;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.gui.screens.MenuScreens;

public class HamletClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(VillagerTradeMenu.TYPE, VillagerTradeScreen::new);
	}
}