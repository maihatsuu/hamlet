package maihatsuu.hamlet.villager;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Per-villager tracking for {@link VillagerReadiness}. All times are game time. */
public final class ReadinessState {
	static final long UNSET = -1L;

	long graceStart = UNSET;
	long lastSocialized = UNSET;
	long lastWorked = UNSET;
	int socialProgress;
	@Nullable
	BlockPos anchor;
	/** Recomputed shortly after loading, so it isn't saved. */
	boolean hasSpace = true;

	public void restartGrace(long now) {
		this.graceStart = now;
	}

	public boolean isWorkDue(long now, int interval) {
		return this.lastWorked == UNSET || now - this.lastWorked >= interval;
	}

	public void markWorked(long now) {
		this.lastWorked = now;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("GraceStart", this.graceStart);
		tag.putLong("LastSocialized", this.lastSocialized);
		tag.putLong("LastWorked", this.lastWorked);
		tag.putInt("SocialProgress", this.socialProgress);
		if (this.anchor != null) {
			tag.putLong("Anchor", this.anchor.asLong());
		}
		return tag;
	}

	public void load(CompoundTag tag) {
		this.graceStart = tag.contains("GraceStart") ? tag.getLong("GraceStart") : UNSET;
		this.lastSocialized = tag.contains("LastSocialized") ? tag.getLong("LastSocialized") : UNSET;
		this.lastWorked = tag.contains("LastWorked") ? tag.getLong("LastWorked") : UNSET;
		this.socialProgress = tag.getInt("SocialProgress");
		this.anchor = tag.contains("Anchor") ? BlockPos.of(tag.getLong("Anchor")) : null;
	}
}
