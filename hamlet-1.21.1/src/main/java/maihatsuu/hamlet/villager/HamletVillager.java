package maihatsuu.hamlet.villager;

import net.minecraft.world.SimpleContainer;

/** Implemented on {@code Villager} by {@code VillagerMixin}. */
public interface HamletVillager {
	int STOCK_SIZE = 27;
	int STORAGE_SIZE = 9;

	/** Work output, sold only: players never reach it directly, only through the trade screen's offers. */
	SimpleContainer hamlet$getStock();

	/** Anything players or pickups give the villager, such as food; players can take it back freely. */
	SimpleContainer hamlet$getStorage();
}
