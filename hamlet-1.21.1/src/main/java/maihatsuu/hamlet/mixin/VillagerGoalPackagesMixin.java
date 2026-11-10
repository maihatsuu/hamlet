package maihatsuu.hamlet.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;

import maihatsuu.hamlet.work.ProfessionWork;

import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import org.spongepowered.asm.mixin.Mixin;
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
