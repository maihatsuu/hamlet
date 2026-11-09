package maihatsuu.hamlet;

import maihatsuu.hamlet.data.ProfessionDataLoader;
import maihatsuu.hamlet.villager.HamletProfessions;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hamlet implements ModInitializer {
	public static final String MOD_ID = "hamlet";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		HamletProfessions.init();
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new ProfessionDataLoader());
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
