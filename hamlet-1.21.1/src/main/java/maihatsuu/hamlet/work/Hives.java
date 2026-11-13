package maihatsuu.hamlet.work;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.villager.HamletVillager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Which hives a farmer harvests, and how: beehives and bee nests near its composter that are full of honey
 * and smoked by a lit campfire below, using vanilla's own check for whether harvesting would anger the bees.
 * Unsmoked hives are left alone. Like a player, it either bottles a hive (1 honey bottle), using up a glass
 * bottle from its storage, or shears it (3 honeycomb). It bottles whenever it has a bottle and isn't capped
 * on honey, and shears otherwise.
 */
public final class Hives {
	/** Wider and taller than the farmer's field, so hives in the trees around it count. */
	private static final int APIARY_RADIUS = 12;
	private static final int APIARY_HEIGHT = 4;
	/** From the hive's centre, horizontally; hives are solid, so the farmer stands beside one. */
	private static final double REACH_SQR = 2.5 * 2.5;
	/** How far above its feet the farmer can reach, so a hive up in a tree trunk still counts. */
	private static final int REACH_UP = 3;
	private static final int HONEYCOMB_PER_HIVE = 3;

	private Hives() {
	}

	/** Full of honey, and smoked. */
	static boolean isReady(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof BeehiveBlock
			&& state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS
			&& CampfireBlock.isSmokeyPos(level, pos);
	}

	/** The nearest ready hive around {@code site} that isn't being skipped. */
	@Nullable
	static BlockPos findReadyHive(ServerLevel level, Villager villager, BlockPos site, Map<BlockPos, Long> skip) {
		if (bothCapped(villager)) {
			return null;
		}
		// Vanilla gives beehives and bee nests separate POI types; searching both avoids scanning every block.
		return level.getPoiManager()
			.getInRange(holder -> holder.is(PoiTypes.BEEHIVE) || holder.is(PoiTypes.BEE_NEST), site, APIARY_RADIUS + APIARY_HEIGHT, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.filter(pos -> inApiary(pos, site) && !skip.containsKey(pos) && isReady(level, pos))
			.min((a, b) -> Double.compare(a.distToCenterSqr(villager.position()), b.distToCenterSqr(villager.position())))
			.orElse(null);
	}

	static boolean canReach(Villager villager, BlockPos hive) {
		Vec3 center = Vec3.atCenterOf(hive);
		double dx = center.x - villager.getX();
		double dz = center.z - villager.getZ();
		double dy = hive.getY() - villager.getBlockY();
		return dx * dx + dz * dz <= REACH_SQR && dy >= -1 && dy <= REACH_UP;
	}

	/** What a harvest with a bottle or shears gives. */
	static ItemStack harvestOf(boolean useBottle) {
		return useBottle ? new ItemStack(Items.HONEY_BOTTLE) : new ItemStack(Items.HONEYCOMB, HONEYCOMB_PER_HIVE);
	}

	/**
	 * Whether harvesting with a bottle (or with shears) is possible and would store anything: a bottle needs a
	 * glass bottle in storage.
	 */
	static boolean canHarvestWith(Villager villager, boolean useBottle) {
		HamletVillager hamlet = (HamletVillager) villager;
		return (!useBottle || hamlet.hamlet$hasInStorage(Items.GLASS_BOTTLE))
			&& !ProfessionWork.isCapped(hamlet.hamlet$getStock(), harvestOf(useBottle));
	}

	/** A bottle when it can use one, else shears. */
	static boolean pickBottle(Villager villager) {
		return canHarvestWith(villager, true);
	}

	/** What a farmer picks up when it's thrown to it, besides food: glass bottles for honey. */
	public static boolean wantsToPickUp(ItemStack stack) {
		return stack.is(Items.GLASS_BOTTLE);
	}

	private static boolean bothCapped(Villager villager) {
		return !canHarvestWith(villager, true) && !canHarvestWith(villager, false);
	}

	/** Holds the tool for this harvest while walking to the hive. */
	static void holdTool(Villager villager, boolean useBottle) {
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(useBottle ? Items.GLASS_BOTTLE : Items.SHEARS));
		villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	static void putToolAway(Villager villager) {
		ItemStack held = villager.getMainHandItem();
		if (held.is(Items.SHEARS) || held.is(Items.GLASS_BOTTLE)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	/** Vanilla's yields: a glass bottle gives one honey bottle, shears give three honeycomb. */
	static void harvest(ServerLevel level, Villager villager, BlockPos pos, boolean useBottle) {
		if (useBottle && !((HamletVillager) villager).hamlet$takeFromStorage(Items.GLASS_BOTTLE)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		// The smoke means the bees stay calm, so vanilla's path for a smoked hive: empty it, release nobody.
		((BeehiveBlock) state.getBlock()).resetHoneyLevel(level, state, pos);
		SimpleContainer stock = ((HamletVillager) villager).hamlet$getStock();
		if (useBottle) {
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(villager, GameEvent.FLUID_PICKUP, pos);
			ProfessionWork.store(stock, harvestOf(true));
		} else {
			level.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(villager, GameEvent.SHEAR, pos);
			ProfessionWork.store(stock, harvestOf(false));
		}
	}

	private static boolean inApiary(BlockPos pos, BlockPos site) {
		return Math.abs(pos.getX() - site.getX()) <= APIARY_RADIUS
			&& Math.abs(pos.getZ() - site.getZ()) <= APIARY_RADIUS
			&& Math.abs(pos.getY() - site.getY()) <= APIARY_HEIGHT;
	}
}
