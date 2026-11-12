package maihatsuu.hamlet.client;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.client.render.FishingBobberRenderer;
import maihatsuu.hamlet.client.screen.VillagerTradeScreen;
import maihatsuu.hamlet.entity.FishingBobber;
import maihatsuu.hamlet.entity.HamletEntities;
import maihatsuu.hamlet.trade.VillagerTradeMenu;
import maihatsuu.hamlet.work.ExplorerMap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
		registerExplorerMapTextures();
	}

	/** Vanilla's buried treasure map, from shipwreck and underwater ruin chests, which it names this. */
	private static final Component BURIED_TREASURE = Component.translatable("filled_map.buried_treasure");

	/**
	 * Gives each kind of explorer map its own item texture: the property is the kind's index, or 10 for
	 * vanilla's buried treasure map, which the overrides in
	 * {@code assets/minecraft/models/item/filled_map.json} pick a model from.
	 */
	private static void registerExplorerMapTextures() {
		ItemProperties.register(Items.FILLED_MAP, Hamlet.id("explorer_map"), (stack, level, entity, seed) -> {
			ExplorerMap kind = ExplorerMap.of(stack);
			if (kind != null) {
				return (kind.ordinal() + 1) / 16.0F;
			}
			return BURIED_TREASURE.equals(stack.get(DataComponents.ITEM_NAME)) ? 10 / 16.0F : 0.0F;
		});
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
