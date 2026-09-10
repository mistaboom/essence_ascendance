package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

/** Stable IDs for free mutually exclusive/replacement loadout choices. */
public final class SkillGroups {

    public static final ResourceLocation OFFENSE_COMBAT_STANCE = id("offense_combat_stance");
    public static final ResourceLocation OFFENSE_ELEMENTAL_IMBUEMENT = id("offense_elemental_imbuement");
    public static final ResourceLocation OFFENSE_PROJECTILE_PATH = id("offense_projectile_path");
    public static final ResourceLocation OFFENSE_PROJECTILE_PAYLOAD = id("offense_projectile_payload");

    public static final ResourceLocation DEFENSE_BLOCK_REWARD = id("defense_block_reward");
    public static final ResourceLocation DEFENSE_POSTURE = id("defense_posture");
    public static final ResourceLocation DEFENSE_STATUS = id("defense_status");

    public static final ResourceLocation VITALITY_RECOVERY = id("vitality_recovery");
    public static final ResourceLocation VITALITY_SUSTENANCE = id("vitality_sustenance");
    public static final ResourceLocation VITALITY_DAMAGE_HANDLING = id("vitality_damage_handling");
    public static final ResourceLocation VITALITY_WARD_MUTATION = id("vitality_ward_mutation");
    public static final ResourceLocation VITALITY_DEATH_DEFIANCE = id("vitality_death_defiance");

    public static final ResourceLocation MOBILITY_JUMP_STYLE = id("mobility_jump_style");
    public static final ResourceLocation MOBILITY_FLIGHT_REPLACEMENT = id("mobility_flight_replacement");

    public static final ResourceLocation GATHERING_SURVEY_FOCUS = id("gathering_survey_focus");
    public static final ResourceLocation GATHERING_LOOT_CALLING = id("gathering_loot_calling");
    public static final ResourceLocation GATHERING_RURAL_CALLING = id("gathering_rural_calling");

    public static final ResourceLocation UTILITY_SENSE_FOCUS = id("utility_sense_focus");
    public static final ResourceLocation UTILITY_MAINTENANCE = id("utility_maintenance");

    private SkillGroups() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                "skill_group/" + path
        );
    }
}
