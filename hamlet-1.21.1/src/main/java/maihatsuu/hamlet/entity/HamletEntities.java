package maihatsuu.hamlet.entity;

import maihatsuu.hamlet.Hamlet;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class HamletEntities {
	public static final EntityType<FishingBobber> FISHING_BOBBER = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		Hamlet.id("fishing_bobber"),
		EntityType.Builder.<FishingBobber>of(FishingBobber::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.25F, 0.25F)
			.clientTrackingRange(4)
			.updateInterval(5)
			.build("fishing_bobber")
	);

	private HamletEntities() {
	}

	/** Forces the static registrations to run during mod initialization. */
	public static void init() {
	}
}
