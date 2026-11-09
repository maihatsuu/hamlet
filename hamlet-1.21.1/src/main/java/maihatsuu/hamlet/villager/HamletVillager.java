package maihatsuu.hamlet.villager;

import net.minecraft.world.SimpleContainer;

/** Implemented on {@code Villager} by {@code VillagerMixin}. */
public interface HamletVillager {
	int STOCK_SIZE = 27;

	/** What the villager has produced and can sell. Separate from vanilla's inventory, which holds its food. */
	SimpleContainer hamlet$getStock();

	ReadinessState hamlet$getReadiness();
}
