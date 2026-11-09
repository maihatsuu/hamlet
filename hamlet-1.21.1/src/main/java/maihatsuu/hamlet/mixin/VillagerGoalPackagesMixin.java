package maihatsuu.hamlet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;

import maihatsuu.hamlet.work.ProfessionWork;

import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
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
}
