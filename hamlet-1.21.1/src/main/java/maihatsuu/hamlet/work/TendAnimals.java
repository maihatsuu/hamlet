package maihatsuu.hamlet.work;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.mixin.SheepAccessor;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/**
 * A shepherd's whole work period: wander the pasture around its loom, and whenever an animal is ready, walk
 * up to it and take what it gives into stock: wool from sheep, milk from cows and goats, mushroom stew from
 * mooshrooms, scutes from armadillos and eggs from chickens.
 */
public final class TendAnimals extends Behavior<Villager> {
	private static final int PASTURE_RADIUS = 12;
	private static final int PASTURE_HEIGHT = 3;
	private static final int SCAN_INTERVAL = 10;
	private static final double REACH_SQR = 2.0 * 2.0;
	private static final int TEND_PAUSE = 20;
	private static final int MIN_WATCH_PAUSE = 20;
	private static final int MAX_WATCH_PAUSE = 60;
	private static final int WANDER_TRIES = 10;
	private static final long GIVE_UP_TICKS = 200L;
	private static final long SKIP_UNREACHABLE_TICKS = 600L;
	/** How long a cow, goat, chicken or armadillo takes to be ready again. Sheep regrow wool by grazing. */
	private static final long PRODUCE_COOLDOWN_TICKS = 6000L;
	private static final int RUN_TICKS = 1200;

	/** The game time an animal on a cooldown timer last gave something, saved with the animal. */
	private static final AttachmentType<Long> LAST_PRODUCED = AttachmentRegistry.createPersistent(Hamlet.id("last_produced"), Codec.LONG);

	private final float speed;
	@Nullable
	private Animal target;
	private long targetSince;
	private long nextActionTime;
	/** Animals the shepherd couldn't reach, so it doesn't keep chasing them. */
	private final Map<Animal, Long> skipUntil = new WeakHashMap<>();

	/** Registers the saved cooldown; called from mod init, before any world loads. */
	public static void init() {
	}

	public TendAnimals(float speed) {
		super(
			ImmutableMap.of(
				MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
				MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
				MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
			),
			RUN_TICKS
		);
		this.speed = speed;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return canWork(level, villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
		// The brain keeps ticking a running behaviour after its activity ends, so stop with the work period.
		return villager.getBrain().isActive(Activity.WORK) && canWork(level, villager);
	}

	private static boolean canWork(ServerLevel level, Villager villager) {
		return villager.getVillagerData().getProfession() == VillagerProfession.SHEPHERD
			&& loom(level, villager).isPresent()
			&& WorkRequirements.missing(villager).isEmpty()
			&& !ProfessionWork.isFull(((HamletVillager) villager).hamlet$getStock());
	}

	private static Optional<BlockPos> loom(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == level.dimension())
			.map(GlobalPos::pos);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long time) {
		if (time < this.nextActionTime) {
			return;
		}
		BlockPos loom = loom(level, villager).orElseThrow();
		Brain<Villager> brain = villager.getBrain();

		Animal animal = this.target;
		if (animal != null) {
			if (!animal.isAlive() || !isReady(animal, time) || !inPasture(animal.blockPosition(), loom)) {
				this.dropTarget(villager);
			} else if (villager.distanceToSqr(animal) <= REACH_SQR) {
				tend(level, villager, animal, time);
				this.dropTarget(villager);
				this.nextActionTime = time + TEND_PAUSE;
			} else if (time - this.targetSince > GIVE_UP_TICKS) {
				this.skipUntil.put(animal, time + SKIP_UNREACHABLE_TICKS);
				this.dropTarget(villager);
			} else if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
				// Arrived where the animal was, but it wandered off: follow it again.
				this.walkTo(villager, animal);
			}
			return;
		}

		if (time % SCAN_INTERVAL == 0) {
			this.skipUntil.values().removeIf(until -> until <= time);
			Animal ready = this.findReadyAnimal(level, villager, loom, time);
			if (ready != null) {
				this.target = ready;
				this.targetSince = time;
				holdToolFor(villager, ready);
				this.walkTo(villager, ready);
				return;
			}
		}

		if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			BlockPos spot = findWanderSpot(level, loom);
			if (spot != null) {
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, this.speed, 0));
			}
			this.nextActionTime = time + MIN_WATCH_PAUSE + level.random.nextInt(MAX_WATCH_PAUSE - MIN_WATCH_PAUSE + 1);
		}
	}

	private void walkTo(Villager villager, Animal animal) {
		Brain<Villager> brain = villager.getBrain();
		brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(animal, false), this.speed, 1));
		brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(animal, true));
	}

	private void dropTarget(Villager villager) {
		this.target = null;
		putToolAway(villager);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Nullable
	private Animal findReadyAnimal(ServerLevel level, Villager villager, BlockPos loom, long time) {
		AABB pasture = new AABB(loom).inflate(PASTURE_RADIUS, PASTURE_HEIGHT, PASTURE_RADIUS);
		Animal nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Animal animal : level.getEntitiesOfClass(Animal.class, pasture, animal -> isReady(animal, time))) {
			if (this.skipUntil.containsKey(animal)) {
				continue;
			}
			double distance = villager.distanceToSqr(animal);
			if (distance < nearestDistance) {
				nearest = animal;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private static boolean isReady(Animal animal, long now) {
		if (animal instanceof Sheep sheep) {
			return sheep.readyForShearing();
		}
		if (!onCooldownTimer(animal) || animal.isBaby()) {
			return false;
		}
		// A rolled-up armadillo can't be brushed.
		if (animal instanceof Armadillo armadillo && armadillo.isScared()) {
			return false;
		}
		Long lastProduced = animal.getAttached(LAST_PRODUCED);
		return lastProduced == null || now - lastProduced >= PRODUCE_COOLDOWN_TICKS;
	}

	/** Animals that give something on a timer rather than, like sheep, regrowing it themselves. */
	private static boolean onCooldownTimer(Animal animal) {
		return animal instanceof Cow || animal instanceof Goat || animal instanceof Chicken || animal instanceof Armadillo;
	}

	private static void tend(ServerLevel level, Villager villager, Animal animal, long now) {
		SimpleContainer stock = ((HamletVillager) villager).hamlet$getStock();
		if (animal instanceof Sheep sheep) {
			// Vanilla's Sheep.shear drops the wool on the ground; this stores it instead.
			sheep.setSheared(true);
			sheep.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
			sheep.gameEvent(GameEvent.SHEAR, villager);
			int count = 1 + level.random.nextInt(3);
			stock.addItem(new ItemStack(SheepAccessor.hamlet$getItemByDye().get(sheep.getColor()), count));
			return;
		}
		// MushroomCow extends Cow, so it has to be checked first.
		if (animal instanceof MushroomCow) {
			animal.playSound(SoundEvents.MOOSHROOM_MILK, 1.0F, 1.0F);
			stock.addItem(new ItemStack(Items.MUSHROOM_STEW));
		} else if (animal instanceof Cow) {
			animal.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
			stock.addItem(new ItemStack(Items.MILK_BUCKET));
		} else if (animal instanceof Goat goat) {
			goat.playSound(goat.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK, 1.0F, 1.0F);
			stock.addItem(new ItemStack(Items.MILK_BUCKET));
		} else if (animal instanceof Armadillo) {
			// Vanilla's Armadillo.brushOffScute drops the scute on the ground; this stores it instead.
			animal.playSound(SoundEvents.ARMADILLO_BRUSH, 1.0F, 1.0F);
			animal.gameEvent(GameEvent.ENTITY_INTERACT, villager);
			stock.addItem(new ItemStack(Items.ARMADILLO_SCUTE));
		} else {
			animal.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F);
			stock.addItem(new ItemStack(Items.EGG));
		}
		animal.setAttached(LAST_PRODUCED, now);
	}

	/** The tool the shepherd holds while walking over. Chickens need nothing. */
	private static void holdToolFor(Villager villager, Animal animal) {
		ItemStack tool = animal instanceof Sheep ? new ItemStack(Items.SHEARS)
			: animal instanceof MushroomCow ? new ItemStack(Items.BOWL)
			: animal instanceof Cow || animal instanceof Goat ? new ItemStack(Items.BUCKET)
			: animal instanceof Armadillo ? new ItemStack(Items.BRUSH)
			: ItemStack.EMPTY;
		villager.setItemSlot(EquipmentSlot.MAINHAND, tool);
		villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	private static void putToolAway(Villager villager) {
		ItemStack held = villager.getMainHandItem();
		if (held.is(Items.SHEARS) || held.is(Items.BUCKET) || held.is(Items.BOWL) || held.is(Items.BRUSH)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	@Nullable
	private static BlockPos findWanderSpot(ServerLevel level, BlockPos loom) {
		for (int attempt = 0; attempt < WANDER_TRIES; attempt++) {
			int x = loom.getX() + level.random.nextInt(PASTURE_RADIUS * 2 + 1) - PASTURE_RADIUS;
			int z = loom.getZ() + level.random.nextInt(PASTURE_RADIUS * 2 + 1) - PASTURE_RADIUS;
			for (int dy = -PASTURE_HEIGHT; dy <= PASTURE_HEIGHT; dy++) {
				BlockPos pos = new BlockPos(x, loom.getY() + dy, z);
				if (level.isLoaded(pos) && FarmCrops.canStandAt(level, pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	private static boolean inPasture(BlockPos pos, BlockPos loom) {
		return Math.abs(pos.getX() - loom.getX()) <= PASTURE_RADIUS
			&& Math.abs(pos.getZ() - loom.getZ()) <= PASTURE_RADIUS
			&& Math.abs(pos.getY() - loom.getY()) <= PASTURE_HEIGHT;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long time) {
		putToolAway(villager);
		Brain<Villager> brain = villager.getBrain();
		brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
		this.target = null;
		this.nextActionTime = 0;
	}
}
