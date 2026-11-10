package maihatsuu.hamlet.entity;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * A fisherman's bobber. Vanilla's {@code FishingHook} only works for players, so its flight, bobbing,
 * lure and bite logic are ported here without the player parts (hooking entities, Luck, Lure). The
 * fisherman watches {@link #isNibbling()} and reels in during the bite, as a player would. It's never saved.
 */
public class FishingBobber extends Entity {
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BITING = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.BOOLEAN);
	private static final double MAX_OWNER_DISTANCE_SQR = 32.0 * 32.0;
	private static final int NO_OWNER = -1;
	private static final double GRAVITY = 0.03;
	private static final double DRAG = 0.92;
	private static final double LAUNCH_UP = 0.3;
	private static final int MAX_OUT_OF_WATER_TIME = 10;

	/** Client side only: each villager's bobber, so its rod can show the cast texture. */
	private static final Map<Integer, FishingBobber> CLIENT_BOBBERS = new HashMap<>();

	private final RandomSource synchronizedRandom = RandomSource.create();
	private boolean biting;
	private boolean openWater;
	private int outOfWaterTime;
	private int nibble;
	private int timeUntilLured;
	private int timeUntilHooked;
	private float fishAngle;
	private boolean bobbing;

	public FishingBobber(EntityType<? extends FishingBobber> type, Level level) {
		super(type, level);
		this.noCulling = true;
	}

	/** Throws a bobber from {@code from} in an arc that lands as close to {@code target} as it can. */
	public static FishingBobber cast(ServerLevel level, Villager owner, Vec3 from, Vec3 target) {
		FishingBobber bobber = new FishingBobber(HamletEntities.FISHING_BOBBER, level);
		bobber.entityData.set(OWNER_ID, owner.getId());
		bobber.setPos(from);
		bobber.setDeltaMovement(aim(from, target));
		level.addFreshEntity(bobber);
		owner.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.5F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
		return bobber;
	}

	/** Finds the horizontal speed whose arc, under this entity's gravity and drag, travels the right distance. */
	private static Vec3 aim(Vec3 from, Vec3 target) {
		Vec3 flat = new Vec3(target.x - from.x, 0.0, target.z - from.z);
		double distance = flat.length();
		Vec3 direction = distance > 1.0E-4 ? flat.scale(1.0 / distance) : Vec3.ZERO;
		double bestSpeed = 0.0;
		double bestError = Double.MAX_VALUE;
		for (double speed = 0.05; speed <= 1.2; speed += 0.025) {
			double x = 0.0;
			double y = from.y;
			double vx = speed;
			double vy = LAUNCH_UP;
			for (int tick = 0; tick < 100 && (vy > 0.0 || y > target.y); tick++) {
				vy -= GRAVITY;
				x += vx;
				y += vy;
				vx *= DRAG;
				vy *= DRAG;
			}
			double error = Math.abs(x - distance);
			if (error < bestError) {
				bestError = error;
				bestSpeed = speed;
			}
		}
		return direction.scale(bestSpeed).add(0.0, LAUNCH_UP, 0.0);
	}

	/** A fish is on the line: the fisherman should reel in now. */
	/**
	 * Server side: whether this bobber counts as fishing in open water, for loot tables that check it
	 * (see FishingHookPredicateMixin). Set by the fisherman just before it rolls a catch.
	 */
	public boolean isOpenWaterFishing() {
		return this.openWater;
	}

	public void setOpenWaterFishing(boolean openWater) {
		this.openWater = openWater;
	}

	public boolean isNibbling() {
		return this.nibble > 0;
	}

	/** Floating on water, as opposed to still flying or lying on land. */
	public boolean isBobbing() {
		return this.bobbing && this.outOfWaterTime < MAX_OUT_OF_WATER_TIME;
	}

	/** It was on water, but the water's gone, so nothing will ever bite. */
	public boolean hasLostWater() {
		return this.bobbing && this.outOfWaterTime >= MAX_OUT_OF_WATER_TIME;
	}

	public void reel() {
		this.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));
		this.discard();
	}

	/** Client side only: whether this entity has a bobber out. */
	public static boolean hasBobberOut(Entity owner) {
		FishingBobber bobber = CLIENT_BOBBERS.get(owner.getId());
		return bobber != null && !bobber.isRemoved();
	}

	@Override
	public void onClientRemoval() {
		CLIENT_BOBBERS.remove(this.entityData.get(OWNER_ID), this);
	}

	@Nullable
	public Villager getOwner() {
		return this.level().getEntity(this.entityData.get(OWNER_ID)) instanceof Villager villager ? villager : null;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		if (BITING.equals(accessor)) {
			this.biting = this.entityData.get(BITING);
			if (this.biting) {
				Vec3 motion = this.getDeltaMovement();
				this.setDeltaMovement(motion.x, -0.4F * Mth.nextFloat(this.synchronizedRandom, 0.6F, 1.0F), motion.z);
			}
		}
		super.onSyncedDataUpdated(accessor);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distanceSqr) {
		return distanceSqr < 4096.0;
	}

	// Like vanilla, the client simulates the bobber itself instead of following server positions.
	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
	}

	// Ported from FishingHook.tick, without hooking entities.
	@Override
	public void tick() {
		this.synchronizedRandom.setSeed(this.getUUID().getLeastSignificantBits() ^ this.level().getGameTime());
		super.tick();
		if (this.level().isClientSide) {
			CLIENT_BOBBERS.put(this.entityData.get(OWNER_ID), this);
		} else if (!this.ownerIsFishing()) {
			this.discard();
			return;
		}

		BlockPos pos = this.blockPosition();
		FluidState fluid = this.level().getFluidState(pos);
		float waterHeight = fluid.is(FluidTags.WATER) ? fluid.getHeight(this.level(), pos) : 0.0F;
		boolean inWater = waterHeight > 0.0F;

		if (!this.bobbing) {
			if (inWater) {
				this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 0.2, 0.3));
				this.bobbing = true;
				return;
			}
		} else {
			Vec3 motion = this.getDeltaMovement();
			double offset = this.getY() + motion.y - pos.getY() - waterHeight;
			if (Math.abs(offset) < 0.01) {
				offset += Math.signum(offset) * 0.1;
			}
			this.setDeltaMovement(motion.x * 0.9, motion.y - offset * this.random.nextFloat() * 0.2, motion.z * 0.9);

			if (inWater) {
				this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
				if (this.biting) {
					this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.1 * this.synchronizedRandom.nextFloat() * this.synchronizedRandom.nextFloat(), 0.0));
				}
				if (this.level() instanceof ServerLevel level) {
					this.catchingFish(level, pos);
				}
			} else {
				this.outOfWaterTime = Math.min(MAX_OUT_OF_WATER_TIME, this.outOfWaterTime + 1);
			}
		}

		if (!fluid.is(FluidTags.WATER)) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -GRAVITY, 0.0));
		}
		this.move(MoverType.SELF, this.getDeltaMovement());
		if (!this.bobbing && (this.onGround() || this.horizontalCollision)) {
			this.setDeltaMovement(Vec3.ZERO);
		}
		this.setDeltaMovement(this.getDeltaMovement().scale(DRAG));
		this.reapplyPosition();
	}

	private boolean ownerIsFishing() {
		Villager owner = this.getOwner();
		return owner != null
			&& owner.isAlive()
			&& owner.distanceToSqr(this) <= MAX_OWNER_DISTANCE_SQR
			&& owner.getVillagerData().getProfession() == VillagerProfession.FISHERMAN;
	}

	// Ported from FishingHook.catchingFish, with no Lure: lured for 5 to 30 seconds (splashes nearby),
	// then a fish swims in (the particle trail), then a bite that lasts 1 to 2 seconds.
	private void catchingFish(ServerLevel level, BlockPos pos) {
		int speed = 1;
		BlockPos above = pos.above();
		if (this.random.nextFloat() < 0.25F && level.isRainingAt(above)) {
			speed++;
		}
		if (this.random.nextFloat() < 0.5F && !level.canSeeSky(above)) {
			speed--;
		}

		if (this.nibble > 0) {
			this.nibble--;
			if (this.nibble <= 0) {
				this.timeUntilLured = 0;
				this.timeUntilHooked = 0;
				this.entityData.set(BITING, false);
			}
		} else if (this.timeUntilHooked > 0) {
			this.timeUntilHooked -= speed;
			if (this.timeUntilHooked > 0) {
				this.fishAngle += (float) this.random.triangle(0.0, 9.188);
				float angle = this.fishAngle * Mth.DEG_TO_RAD;
				float sin = Mth.sin(angle);
				float cos = Mth.cos(angle);
				double x = this.getX() + sin * this.timeUntilHooked * 0.1F;
				double y = Mth.floor(this.getY()) + 1.0F;
				double z = this.getZ() + cos * this.timeUntilHooked * 0.1F;
				if (level.getBlockState(BlockPos.containing(x, y - 1.0, z)).is(Blocks.WATER)) {
					if (this.random.nextFloat() < 0.15F) {
						level.sendParticles(ParticleTypes.BUBBLE, x, y - 0.1F, z, 1, sin, 0.1, cos, 0.0);
					}
					float dx = sin * 0.04F;
					float dz = cos * 0.04F;
					level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, dz, 0.01, -dx, 1.0);
					level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -dz, 0.01, dx, 1.0);
				}
			} else {
				this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
				double y = this.getY() + 0.5;
				int count = (int) (1.0F + this.getBbWidth() * 20.0F);
				level.sendParticles(ParticleTypes.BUBBLE, this.getX(), y, this.getZ(), count, this.getBbWidth(), 0.0, this.getBbWidth(), 0.2F);
				level.sendParticles(ParticleTypes.FISHING, this.getX(), y, this.getZ(), count, this.getBbWidth(), 0.0, this.getBbWidth(), 0.2F);
				this.nibble = Mth.nextInt(this.random, 20, 40);
				this.entityData.set(BITING, true);
			}
		} else if (this.timeUntilLured > 0) {
			this.timeUntilLured -= speed;
			float chance = 0.15F;
			if (this.timeUntilLured < 20) {
				chance += (20 - this.timeUntilLured) * 0.05F;
			} else if (this.timeUntilLured < 40) {
				chance += (40 - this.timeUntilLured) * 0.02F;
			} else if (this.timeUntilLured < 60) {
				chance += (60 - this.timeUntilLured) * 0.01F;
			}
			if (this.random.nextFloat() < chance) {
				float angle = Mth.nextFloat(this.random, 0.0F, 360.0F) * Mth.DEG_TO_RAD;
				float distance = Mth.nextFloat(this.random, 25.0F, 60.0F);
				double x = this.getX() + Mth.sin(angle) * distance * 0.1;
				double y = Mth.floor(this.getY()) + 1.0F;
				double z = this.getZ() + Mth.cos(angle) * distance * 0.1;
				if (level.getBlockState(BlockPos.containing(x, y - 1.0, z)).is(Blocks.WATER)) {
					level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2 + this.random.nextInt(2), 0.1F, 0.0, 0.1F, 0.0);
				}
			}
			if (this.timeUntilLured <= 0) {
				this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
				this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
			}
		} else {
			this.timeUntilLured = Mth.nextInt(this.random, 100, 600);
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER_ID, NO_OWNER);
		builder.define(BITING, false);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
