package com.mistaboom.essence_ascendance.skill;

import java.util.List;

/** Centralized declarations for every curated free loadout choice. */
public final class SkillChoiceGroups {

    private static final List<SkillChoiceGroup> DEFINITIONS = List.of(
            group(
                    SkillGroups.OFFENSE_COMBAT_STANCE,
                    SkillIds.FRENZY,
                    SkillIds.DESPERATION
            ),
            group(
                    SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT,
                    SkillIds.KINDLING,
                    SkillIds.FROSTBITE,
                    SkillIds.STATIC_CHARGE
            ),
            group(
                    SkillGroups.OFFENSE_PROJECTILE_PATH,
                    SkillIds.HOMING_PROJECTILE,
                    SkillIds.RICOCHET,
                    SkillIds.PIERCING_PROJECTILE
            ),
            group(
                    SkillGroups.OFFENSE_PROJECTILE_PAYLOAD,
                    SkillIds.EXPLOSIVE_PAYLOAD,
                    SkillIds.ROOTING_PAYLOAD
            ),
            group(
                    SkillGroups.DEFENSE_BLOCK_REWARD,
                    SkillIds.STORED_FORCE,
                    SkillIds.GUARD_AMPLIFIER
            ),
            group(
                    SkillGroups.DEFENSE_POSTURE,
                    SkillIds.EVASIVE_CURRENT,
                    SkillIds.BULWARK_STANCE,
                    SkillIds.ADAPTIVE_GUARD
            ),
            group(
                    SkillGroups.DEFENSE_STATUS,
                    SkillIds.STATUS_MIRROR,
                    SkillIds.PURE_STATE
            ),
            group(
                    SkillGroups.VITALITY_RECOVERY,
                    SkillIds.RISING_RECOVERY,
                    SkillIds.LIFE_STEAL
            ),
            group(
                    SkillGroups.VITALITY_SUSTENANCE,
                    SkillIds.FEAST_REFLEX,
                    SkillIds.INNER_SUSTENANCE
            ),
            group(
                    SkillGroups.VITALITY_DAMAGE_HANDLING,
                    SkillIds.HUNGER_WARD,
                    SkillIds.STAGGERED_PAIN,
                    SkillIds.DAMAGE_CEILING
            ),
            group(
                    SkillGroups.VITALITY_WARD_MUTATION,
                    SkillIds.DEEP_WARD,
                    SkillIds.SHATTERING_WARD
            ),
            group(
                    SkillGroups.VITALITY_DEATH_DEFIANCE,
                    SkillIds.SECOND_WIND,
                    SkillIds.SPIRIT_WALK
            ),
            group(
                    SkillGroups.MOBILITY_JUMP_STYLE,
                    SkillIds.CHARGED_JUMP,
                    SkillIds.DOUBLE_JUMP,
                    SkillIds.VECTOR_JUMP
            ),
            group(
                    SkillGroups.MOBILITY_FLIGHT_REPLACEMENT,
                    SkillIds.ESSENCE_WINGS,
                    SkillIds.UNTETHERED_FLIGHT
            ),
            group(
                    SkillGroups.GATHERING_SURVEY_FOCUS,
                    SkillIds.ORE_SIGHT,
                    SkillIds.TREASURE_SENSE
            ),
            group(
                    SkillGroups.GATHERING_LOOT_CALLING,
                    SkillIds.HUNTERS_STUDY,
                    SkillIds.ESSENCE_BLOOM
            ),
            group(
                    SkillGroups.GATHERING_RURAL_CALLING,
                    SkillIds.VERDANT_STRIDE,
                    SkillIds.HERDKEEPER
            ),
            group(
                    SkillGroups.UTILITY_SENSE_FOCUS,
                    SkillIds.THREAT_SENSE,
                    SkillIds.WAYLIGHT
            ),
            group(
                    SkillGroups.UTILITY_MAINTENANCE,
                    SkillIds.RESTFUL_MENDING,
                    SkillIds.METABOLIC_MENDING,
                    SkillIds.MASTERWORK_TEMPERING
            )
    );

    private SkillChoiceGroups() {
    }

    static List<SkillChoiceGroup> definitions() {
        return DEFINITIONS;
    }

    private static SkillChoiceGroup group(
            net.minecraft.resources.ResourceLocation id,
            net.minecraft.resources.ResourceLocation... members
    ) {
        String path = id.getPath();
        String keyPath = path.substring(path.lastIndexOf('/') + 1);
        return new SkillChoiceGroup(
                id,
                "skill_group.essence_ascendance." + keyPath,
                List.of(members),
                true
        );
    }
}
