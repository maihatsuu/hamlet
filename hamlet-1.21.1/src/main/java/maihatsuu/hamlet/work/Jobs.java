package maihatsuu.hamlet.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.mixin.SheepAccessor;
import maihatsuu.hamlet.villager.HamletProfessions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What each working profession does when it uses its workstation. */
final class Jobs {
	private static final int FARM_RADIUS = 9;
	private static final int FARM_HEIGHT = 2;
	private static final int MAX_CROPS_PER_ACTION = 8;
	private static final int ANIMAL_RADIUS = 8;
	private static final long ANIMAL_COOLDOWN_TICKS = 6000L;
	private static final int HIVE_RADIUS = 16;
	private static final int HONEYCOMB_PER_HIVE = 3;
	private static final int WATER_RADIUS = 8;
	private static final int WATER_DEPTH = 3;
	private static final int WATER_RISE = 1;

	// The same weights as vanilla's gameplay/fishing table.
	private static final int JUNK_WEIGHT = 10;
	private static final int TREASURE_WEIGHT = 5;
	private static final int FISH_WEIGHT = 85;

	private static final Map<VillagerProfession, Job> JOBS = Map.of(
		VillagerProfession.FARMER, Jobs::farm,
		VillagerProfession.SHEPHERD, Jobs::tendAnimals,
		VillagerProfession.FISHERMAN, Jobs::fish,
		VillagerProfession.LIBRARIAN, Jobs::copyBook,
		HamletProfessions.BEEKEEPER, Jobs::gatherHoney
	);

	/** When each cow or chicken last produced. Not saved, so a restart just makes them ready sooner. */
	private static final Map<Animal, Long> LAST_PRODUCED = new WeakHashMap<>();

	private Jobs() {
	}

	@Nullable
	static Job forProfession(VillagerProfession profession) {
		return JOBS.get(profession);
	}

	private static boolean farm(ServerLevel level, BlockPos site, SimpleContainer stock) {
		int harvested = 0;
		for (BlockPos pos : BlockPos.betweenClosed(
			site.offset(-FARM_RADIUS, -FARM_HEIGHT, -FARM_RADIUS),
			site.offset(FARM_RADIUS, FARM_HEIGHT, FARM_RADIUS)
		)) {
			if (harvested >= MAX_CROPS_PER_ACTION) {
				break;
			}
			if (!level.isLoaded(pos)) {
				continue;
			}

			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
				BlockPos cropPos = pos.immutable();
				Block.getDrops(state, level, cropPos, null).forEach(stock::addItem);
				level.setBlock(cropPos, crop.getStateForAge(0), Block.UPDATE_ALL);
				harvested++;
			}
		}
		return harvested > 0;
	}

	private static boolean tendAnimals(ServerLevel level, BlockPos site, SimpleContainer stock) {
		long now = level.getGameTime();
		List<Animal> ready = level.getEntitiesOfClass(
			Animal.class,
			new AABB(site).inflate(ANIMAL_RADIUS),
			animal -> isReady(animal, now)
		);
		if (ready.isEmpty()) {
			return false;
		}

		Animal animal = ready.get(level.random.nextInt(ready.size()));
		if (animal instanceof Sheep sheep) {
			sheep.setSheared(true);
			sheep.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
			int count = 1 + level.random.nextInt(3);
			stock.addItem(new ItemStack(SheepAccessor.hamlet$getItemByDye().get(sheep.getColor()), count));
		} else {
			stock.addItem(new ItemStack(animal instanceof Cow ? Items.MILK_BUCKET : Items.EGG));
			LAST_PRODUCED.put(animal, now);
		}
		return true;
	}

	private static boolean isReady(Animal animal, long now) {
		if (animal instanceof Sheep sheep) {
			return sheep.readyForShearing();
		}
		if (!(animal instanceof Cow || animal instanceof Chicken) || animal.isBaby()) {
			return false;
		}
		Long lastProduced = LAST_PRODUCED.get(animal);
		return lastProduced == null || now - lastProduced >= ANIMAL_COOLDOWN_TICKS;
	}

	private static boolean gatherHoney(ServerLevel level, BlockPos site, SimpleContainer stock) {
		return level.getPoiManager()
			.getInRange(holder -> holder.is(PoiTypes.BEEHIVE), site, HIVE_RADIUS, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.filter(pos -> level.isLoaded(pos) && isFullHive(level.getBlockState(pos)))
			.findFirst()
			.map(pos -> {
				BlockState state = level.getBlockState(pos);
				((BeehiveBlock) state.getBlock()).resetHoneyLevel(level, state, pos);
				level.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
				stock.addItem(new ItemStack(Items.HONEY_BOTTLE));
				stock.addItem(new ItemStack(Items.HONEYCOMB, HONEYCOMB_PER_HIVE));
				return true;
			})
			.orElse(false);
	}

	private static boolean isFullHive(BlockState state) {
		return state.getBlock() instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS;
	}

	private static boolean fish(ServerLevel level, BlockPos site, SimpleContainer stock) {
		List<BlockPos> surface = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
			site.offset(-WATER_RADIUS, -WATER_DEPTH, -WATER_RADIUS),
			site.offset(WATER_RADIUS, WATER_RISE, WATER_RADIUS)
		)) {
			if (level.isLoaded(pos) && isWaterSource(level.getFluidState(pos)) && level.getBlockState(pos.above()).isAir()) {
				surface.add(pos.immutable());
			}
		}
		if (surface.isEmpty()) {
			return false;
		}

		BlockPos spot = surface.get(level.random.nextInt(surface.size()));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(rollCatch(level, isOpenWater(level, spot)));
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(spot))
			.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
			.create(LootContextParamSets.FISHING);
		table.getRandomItems(params).forEach(stock::addItem);
		return true;
	}

	private static ResourceKey<LootTable> rollCatch(ServerLevel level, boolean openWater) {
		int treasureWeight = openWater ? TREASURE_WEIGHT : 0;
		int roll = level.random.nextInt(JUNK_WEIGHT + treasureWeight + FISH_WEIGHT);
		if (roll < JUNK_WEIGHT) {
			return BuiltInLootTables.FISHING_JUNK;
		}
		return roll < JUNK_WEIGHT + treasureWeight ? BuiltInLootTables.FISHING_TREASURE : BuiltInLootTables.FISHING_FISH;
	}

	// Ported from FishingHook.calculateOpenWater, which needs a hook entity.
	private static boolean isOpenWater(ServerLevel level, BlockPos pos) {
		WaterLayer previous = WaterLayer.INVALID;
		for (int y = -1; y <= 2; y++) {
			WaterLayer layer = classifyLayer(level, pos.offset(-2, y, -2), pos.offset(2, y, 2));
			if (layer == WaterLayer.INVALID
				|| layer == WaterLayer.ABOVE_WATER && previous == WaterLayer.INVALID
				|| layer == WaterLayer.INSIDE_WATER && previous == WaterLayer.ABOVE_WATER) {
				return false;
			}
			previous = layer;
		}
		return true;
	}

	private static WaterLayer classifyLayer(ServerLevel level, BlockPos from, BlockPos to) {
		WaterLayer result = null;
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			WaterLayer layer = classifyBlock(level, pos);
			if (result != null && layer != result) {
				return WaterLayer.INVALID;
			}
			result = layer;
		}
		return result == null ? WaterLayer.INVALID : result;
	}

	private static WaterLayer classifyBlock(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return WaterLayer.INVALID;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.is(Blocks.LILY_PAD)) {
			return WaterLayer.ABOVE_WATER;
		}
		return isWaterSource(state.getFluidState()) && state.getCollisionShape(level, pos).isEmpty()
			? WaterLayer.INSIDE_WATER
			: WaterLayer.INVALID;
	}

	private static boolean isWaterSource(FluidState fluid) {
		return fluid.is(FluidTags.WATER) && fluid.isSource();
	}

	// Archive tomes will be handled here as well, once Archive's API exists.
	private static boolean copyBook(ServerLevel level, BlockPos site, SimpleContainer stock) {
		if (!(level.getBlockEntity(site) instanceof LecternBlockEntity lectern) || !lectern.getBook().is(Items.WRITTEN_BOOK)) {
			return false;
		}

		ItemStack book = lectern.getBook();
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		WrittenBookContent copiedContent = content == null ? null : content.tryCraftCopy();
		if (copiedContent == null) {
			return false;
		}

		ItemStack copy = book.copyWithCount(1);
		copy.set(DataComponents.WRITTEN_BOOK_CONTENT, copiedContent);
		stock.addItem(copy);
		return true;
	}

	private enum WaterLayer {
		ABOVE_WATER,
		INSIDE_WATER,
		INVALID
	}
}
