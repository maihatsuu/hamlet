package maihatsuu.hamlet.work;

import java.util.Optional;

import maihatsuu.hamlet.Hamlet;
import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.WorkRequirements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's walk-to-workstation behaviour, for professions that don't have their own work behaviour. It skips
 * vanilla's restocking. Most of these professions offer services and produce nothing here; the cartographer
 * draws explorer maps at its table, keeping one of each kind in its stock.
 */
public final class ProfessionWork extends WorkAtPoi {
	/** Work time, at the table, between maps: about 5 minutes. */
	private static final long TICKS_PER_MAP = 6000L;
	/**
	 * Visits come about every 15 seconds while at the table, so the time since the last visit counts as
	 * work up to this much; more means the villager was away from it, or off work.
	 */
	private static final long MAX_TICKS_PER_VISIT = 600L;

	private final VillagerProfession profession;
	/** Cartographer: work time towards the next map. Not saved, so a reload restarts the current map. */
	private long workTicks;
	private long lastVisit = Long.MIN_VALUE / 2;
	/** Cartographer: which kind to try first, rotated so one that can't be found doesn't block the rest. */
	private int nextKind;

	public ProfessionWork(VillagerProfession profession) {
		this.profession = profession;
	}

	@Override
	protected void useWorkstation(ServerLevel level, Villager villager) {
		if (this.profession != VillagerProfession.CARTOGRAPHER || WorkRequirements.missing(villager).isPresent()) {
			return;
		}
		long now = level.getGameTime();
		this.workTicks += Math.max(0L, Math.min(now - this.lastVisit, MAX_TICKS_PER_VISIT));
		this.lastVisit = now;
		if (this.workTicks < TICKS_PER_MAP) {
			return;
		}
		villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == level.dimension())
			.ifPresent(site -> {
				if (this.drawMap(level, ((HamletVillager) villager).hamlet$getStock(), site.pos())) {
					this.workTicks = 0L;
				}
			});
	}

	/**
	 * Draws one map of a kind missing from the stock, if one can be charted now. Searching charts the
	 * structure, so this only searches when the map can be stored.
	 */
	private boolean drawMap(ServerLevel level, SimpleContainer stock, BlockPos from) {
		if (isFull(stock)) {
			return false;
		}
		ExplorerMap[] kinds = ExplorerMap.values();
		for (int i = 0; i < kinds.length; i++) {
			ExplorerMap kind = kinds[(this.nextKind + i) % kinds.length];
			if (inStock(stock, kind)) {
				continue;
			}
			this.nextKind = (kind.ordinal() + 1) % kinds.length;
			// One search per visit at most: they're slow, and rate-limited anyway.
			Optional<ItemStack> map = kind.draw(level, from);
			map.ifPresent(stack -> store(stock, stack));
			return map.isPresent();
		}
		return false;
	}

	private static boolean inStock(SimpleContainer stock, ExplorerMap kind) {
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			if (kind.isMap(stock.getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Work output nobody would buy, such as seeds from a harvest: thrown away rather than filling the stock.
	 * Datapacks can add to it to stop villagers gathering anything else.
	 */
	private static final TagKey<Item> DISCARDS = TagKey.create(Registries.ITEM, Hamlet.id("villager_discards"));

	/** Puts work output into the stock, unless it's discarded. All work output goes through here. */
	static void store(SimpleContainer stock, ItemStack stack) {
		if (!stack.is(DISCARDS)) {
			stock.addItem(stack);
		}
	}

	static boolean isFull(SimpleContainer stock) {
		for (int slot = 0; slot < stock.getContainerSize(); slot++) {
			if (stock.getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
