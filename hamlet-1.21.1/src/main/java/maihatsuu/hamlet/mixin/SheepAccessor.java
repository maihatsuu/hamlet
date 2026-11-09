package maihatsuu.hamlet.mixin;

import java.util.Map;

import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ItemLike;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Sheep.class)
public interface SheepAccessor {
	@Accessor("ITEM_BY_DYE")
	static Map<DyeColor, ItemLike> hamlet$getItemByDye() {
		throw new AssertionError();
	}
}
