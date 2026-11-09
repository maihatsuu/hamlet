package maihatsuu.hamlet.trade;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import maihatsuu.hamlet.villager.HamletProfessions;

import net.minecraft.world.entity.npc.VillagerProfession;

/** The services a villager can offer on the Service tab. Each costs one emerald. */
public enum Service {
	DUPLICATE_TEMPLATE("hamlet.service.duplicate_template", true),
	BOTTLE_EXPERIENCE("hamlet.service.bottle_experience", false);

	private static final Map<VillagerProfession, Service> BY_PROFESSION = Map.of(
		HamletProfessions.ARCHAEOLOGIST, DUPLICATE_TEMPLATE,
		VillagerProfession.CLERIC, BOTTLE_EXPERIENCE
	);

	public final String labelKey;
	public final boolean needsInput;

	Service(String labelKey, boolean needsInput) {
		this.labelKey = labelKey;
		this.needsInput = needsInput;
	}

	@Nullable
	public static Service forProfession(VillagerProfession profession) {
		return BY_PROFESSION.get(profession);
	}
}
