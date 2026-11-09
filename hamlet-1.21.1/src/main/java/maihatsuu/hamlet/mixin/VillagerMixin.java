package maihatsuu.hamlet.mixin;

import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.VillagerRules;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
abstract class VillagerMixin implements HamletVillager {
	@Unique
	private static final String STOCK_KEY = "hamlet:Stock";

	@Unique
	private final SimpleContainer hamlet$stock = new SimpleContainer(STOCK_SIZE);

	@Override
	public SimpleContainer hamlet$getStock() {
		return this.hamlet$stock;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$saveStock(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		tag.put(STOCK_KEY, this.hamlet$stock.createTag(self.registryAccess()));
	}

	// Loading writes the villager data directly, so it doesn't pass through setVillagerData.
	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void hamlet$loadAndNormalize(CompoundTag tag, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		this.hamlet$stock.fromTag(tag.getList(STOCK_KEY, Tag.TAG_COMPOUND), self.registryAccess());

		VillagerProfession savedProfession = self.getVillagerData().getProfession();
		self.setVillagerData(self.getVillagerData());
		self.setVillagerXp(0);
		self.overrideOffers(new MerchantOffers());

		if (self.getVillagerData().getProfession() != savedProfession && self.level() instanceof ServerLevel level) {
			self.refreshBrain(level);
		}
	}

	@ModifyVariable(method = "setVillagerData", at = @At("HEAD"), argsOnly = true)
	private VillagerData hamlet$normalize(VillagerData data) {
		return VillagerRules.normalize(data);
	}

	@Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
	private void hamlet$noVanillaTrades(CallbackInfo ci) {
		ci.cancel();
	}
}
