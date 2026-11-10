package maihatsuu.hamlet.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;

import maihatsuu.hamlet.work.FarmCrops;
import maihatsuu.hamlet.work.GoFishing;
import maihatsuu.hamlet.work.TendAnimals;
import maihatsuu.hamlet.work.ProfessionWork;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VillagerGoalPackages.class)
abstract class VillagerGoalPackagesMixin {
	// The first Pair.of in getWorkPackage pairs the workstation behaviour with its weight.
	@WrapOperation(
		method = "getWorkPackage",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/datafixers/util/Pair;of(Ljava/lang/Object;Ljava/lang/Object;)Lcom/mojang/datafixers/util/Pair;",
			ordinal = 0
		)
	)
	private static Pair<?, ?> hamlet$useProfessionWork(
		Object vanillaBehaviour,
		Object weight,
		Operation<Pair<?, ?>> original,
		VillagerProfession profession,
		float speed
	) {
		return original.call(new ProfessionWork(profession), weight);
	}

	// Farmers patrol their field and fishermen fish for their whole work period, instead of vanilla's
	// random mix of strolling and workstation visits.
	@ModifyReturnValue(method = "getWorkPackage", at = @At("RETURN"))
	private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> hamlet$dedicatedWork(
		ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work,
		VillagerProfession profession,
		float speed
	) {
		if (profession == VillagerProfession.FARMER) {
			return hamlet$workPackage(new FarmCrops(speed));
		}
		if (profession == VillagerProfession.FISHERMAN) {
			return hamlet$workPackage(new GoFishing(speed));
		}
		if (profession == VillagerProfession.SHEPHERD) {
			return hamlet$workPackage(new TendAnimals(speed));
		}
		return work;
	}

	// The look behaviour copies vanilla's private getMinimalLookBehavior. Vanilla's ShowTradesToPlayer is
	// left out: with no offers to show, it empties the villager's hand every tick a player is nearby, which
	// took the fisherman's rod away. SetLookAndInteract goes with it, since only ShowTradesToPlayer clears
	// the interaction target it sets.
	@Unique
	private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> hamlet$workPackage(
		BehaviorControl<? super Villager> job
	) {
		return ImmutableList.of(
			Pair.of(5, new RunOne<>(ImmutableList.of(
				Pair.of(SetEntityLookTarget.create(EntityType.VILLAGER, 8.0F), 2),
				Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
				Pair.of(new DoNothing(30, 60), 8)
			))),
			Pair.of(5, job),
			Pair.of(3, new GiveGiftToHero(100)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	// Vanilla only checks the bed still exists while resting at night. Work depends on having a bed, so
	// check all day: a broken bed is forgotten as soon as the villager is near it, and it can claim a new one.
	@ModifyReturnValue(method = "getCorePackage", at = @At("RETURN"))
	private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> hamlet$validateBedAllDay(
		ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> core
	) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
			.addAll(core)
			.add(Pair.of(0, ValidateNearbyPoi.create(holder -> holder.is(PoiTypes.HOME), MemoryModuleType.HOME)))
			.build();
	}
}
