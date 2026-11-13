package maihatsuu.hamlet.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import maihatsuu.hamlet.Hamlet;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pasture, the animal upkeep mod, if it's installed: milk comes in bottles and needs grazing to refill,
 * chickens drop eggs when bred instead of laying, and a brushed armadillo needs a day. The shepherd follows
 * the same rules through Pasture's API, called by reflection so Hamlet doesn't need Pasture to build.
 */
public final class PastureCompat {
	private static final String API = "maihatsuu.pasture.Pasture";

	private static final MethodHandle CAN_MILK;
	private static final MethodHandle MILK;
	private static final MethodHandle MILK_FOR_STEW;
	private static final MethodHandle CAN_BRUSH;
	private static final boolean LOADED;

	static {
		MethodHandle canMilk = null;
		MethodHandle milk = null;
		MethodHandle milkForStew = null;
		MethodHandle canBrush = null;
		boolean loaded = false;
		if (FabricLoader.getInstance().isModLoaded("pasture")) {
			try {
				Class<?> api = Class.forName(API);
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				MethodType test = MethodType.methodType(boolean.class, Entity.class);
				canMilk = lookup.findStatic(api, "canMilk", test);
				milk = lookup.findStatic(api, "milk", MethodType.methodType(ItemStack.class, Entity.class));
				milkForStew = lookup.findStatic(api, "milkForStew", test);
				canBrush = lookup.findStatic(api, "canBrush", test);
				loaded = true;
			} catch (ReflectiveOperationException e) {
				Hamlet.LOGGER.error("Pasture is installed but its API doesn't match; shepherds will ignore its rules", e);
			}
		}
		CAN_MILK = canMilk;
		MILK = milk;
		MILK_FOR_STEW = milkForStew;
		CAN_BRUSH = canBrush;
		LOADED = loaded;
	}

	private PastureCompat() {
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	/** An adult cow, goat or mooshroom with milk. */
	public static boolean canMilk(Entity entity) {
		return LOADED && test(CAN_MILK, entity);
	}

	/** Milks into a milk bottle, with Pasture's sounds; the caller uses up the glass bottle. */
	public static ItemStack milk(Entity entity) {
		if (!LOADED) {
			return ItemStack.EMPTY;
		}
		try {
			return (ItemStack) MILK.invokeExact(entity);
		} catch (Throwable e) {
			throw new IllegalStateException("Pasture's milk failed", e);
		}
	}

	/** Uses up a mooshroom's milk for stew; the caller uses up the bowl and makes the stew. */
	public static boolean milkForStew(Entity entity) {
		return LOADED && test(MILK_FOR_STEW, entity);
	}

	/** An adult armadillo that isn't still brushed. */
	public static boolean canBrush(Entity entity) {
		return LOADED && test(CAN_BRUSH, entity);
	}

	/** Pasture's milk bottle, for checking the stock cap before milking. */
	public static Item milkBottle() {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("pasture", "milk_bottle"));
	}

	/**
	 * Products Pasture drops on the ground instead of handing over: eggs from breeding chickens and scutes
	 * from brushing. A shepherd picks these up into its stock.
	 */
	public static boolean isDroppedProduct(ItemStack stack) {
		return LOADED && (stack.is(Items.EGG) || stack.is(Items.ARMADILLO_SCUTE));
	}

	private static boolean test(MethodHandle handle, Entity entity) {
		try {
			return (boolean) handle.invokeExact(entity);
		} catch (Throwable e) {
			throw new IllegalStateException("Pasture's API failed", e);
		}
	}
}
