package maihatsuu.hamlet.villager;

import java.util.Map;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Implemented on {@code Villager} by {@code VillagerMixin}. */
public interface HamletVillager {
	int STOCK_SIZE = 27;
	int STORAGE_SIZE = 9;

	/**
	 * What villagers eat to breed, in vanilla's food points: each parent eats
	 * {@code Villager.BREEDING_FOOD_THRESHOLD} (12) points' worth from its storage when the baby is born,
	 * so 3 bread or baked potatoes each.
	 */
	Map<Item, Integer> BREEDING_FOOD = Map.of(Items.BREAD, 4, Items.BAKED_POTATO, 4);

	/** Work output, sold only: players never reach it directly, only through the trade screen's offers. */
	SimpleContainer hamlet$getStock();

	/** Anything players or pickups give the villager, such as food; players can take it back freely. */
	SimpleContainer hamlet$getStorage();

	/** The food points of the breeding food in storage. */
	default int hamlet$foodPoints() {
		SimpleContainer storage = this.hamlet$getStorage();
		return BREEDING_FOOD.entrySet().stream().mapToInt(entry -> storage.countItem(entry.getKey()) * entry.getValue()).sum();
	}
}
