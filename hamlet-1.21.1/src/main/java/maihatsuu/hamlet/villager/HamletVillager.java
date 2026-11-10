package maihatsuu.hamlet.villager;

import net.minecraft.world.SimpleContainer;

/** Implemented on {@code Villager} by {@code VillagerMixin}. */
public interface HamletVillager {
	int STOCK_SIZE = 27;
	long NEVER_WORKED = -1L;

	/** What the villager has produced and can sell. Separate from vanilla's inventory, which holds its food. */
	SimpleContainer hamlet$getStock();

	/** Game time of the last successful work action, or {@link #NEVER_WORKED}. */
	long hamlet$getLastWorked();

	void hamlet$setLastWorked(long gameTime);
}
