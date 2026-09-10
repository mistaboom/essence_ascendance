package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.skill.requirement.PermanentMilestoneRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The complete curated six-Essence skill catalog. Required tiers and cost
 * bands are intentionally centralized here because this first assignment is
 * provisional pending gameplay balance testing.
 */
public final class Skills {

    private static final List<SkillDefinition> DEFINITIONS = buildDefinitions();

    private Skills() {
    }

    public static List<SkillDefinition> definitions() {
        return DEFINITIONS;
    }

    /** Forces catalog construction and validation during common startup. */
    public static void init() {
        SkillRegistry.validate();
    }

    private static List<SkillDefinition> buildDefinitions() {
        List<SkillDefinition> skills = new ArrayList<>(90);
        addOffense(skills);
        addDefense(skills);
        addVitality(skills);
        addMobility(skills);
        addGathering(skills);
        addUtility(skills);
        return List.copyOf(skills);
    }

    private static void addOffense(List<SkillDefinition> skills) {
        ResourceLocation essence = EssenceTypes.OFFENSE.id();

        skills.add(skill(SkillIds.FRENZY, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 0).group(SkillGroups.OFFENSE_COMBAT_STANCE)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.ARMOR_CRACK, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 1).requires(SkillIds.FRENZY)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.DESPERATION, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 2).group(SkillGroups.OFFENSE_COMBAT_STANCE)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.DEATH_RUSH, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 3).requires(SkillIds.DESPERATION)
                .hint(SkillLayoutHint.LOWER).build());

        skills.add(skill(SkillIds.KINDLING, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 4).group(SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.COMBUSTION, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 5).requires(SkillIds.KINDLING)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.FROSTBITE, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 6).group(SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.SHATTER, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 7).requires(SkillIds.FROSTBITE)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.STATIC_CHARGE, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 8).group(SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.CHAIN_STRIKE, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 9).requires(SkillIds.STATIC_CHARGE)
                .hint(SkillLayoutHint.LOWER).build());

        skills.add(skill(SkillIds.HOMING_PROJECTILE, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 10).group(SkillGroups.OFFENSE_PROJECTILE_PATH)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.RICOCHET, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 11).group(SkillGroups.OFFENSE_PROJECTILE_PATH)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.PIERCING_PROJECTILE, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 12).group(SkillGroups.OFFENSE_PROJECTILE_PATH)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.EXPLOSIVE_PAYLOAD, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.ADVANCED, 13).group(SkillGroups.OFFENSE_PROJECTILE_PAYLOAD)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.ROOTING_PAYLOAD, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.ADVANCED, 14).group(SkillGroups.OFFENSE_PROJECTILE_PAYLOAD)
                .hint(SkillLayoutHint.LOWER).build());
    }

    private static void addDefense(List<SkillDefinition> skills) {
        ResourceLocation essence = EssenceTypes.DEFENSE.id();

        skills.add(skill(SkillIds.GUARDED_ADVANCE, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 0).hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.SHIELD_RAM, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 1).requires(SkillIds.GUARDED_ADVANCE)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.STORED_FORCE, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 2).requires(SkillIds.REFLEXIVE_WARD)
                .group(SkillGroups.DEFENSE_BLOCK_REWARD).hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.GUARD_AMPLIFIER, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 3).requires(SkillIds.REFLEXIVE_WARD)
                .group(SkillGroups.DEFENSE_BLOCK_REWARD).hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.REFLEXIVE_WARD, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 4).hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.CROWD_REPRISAL, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.KEYSTONE, 5).requires(SkillIds.REFLEXIVE_WARD)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.INTERCEPTOR, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 6).requires(SkillIds.PROJECTILE_DRAG_FIELD)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.TRAJECTORY_THEFT, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 7).requires(SkillIds.INTERCEPTOR)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.PROJECTILE_DRAG_FIELD, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.FOUNDATION, 8).hint(SkillLayoutHint.LOWER).build());

        skills.add(skill(SkillIds.EVASIVE_CURRENT, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 9).group(SkillGroups.DEFENSE_POSTURE)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.BULWARK_STANCE, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 10).group(SkillGroups.DEFENSE_POSTURE)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.ADAPTIVE_GUARD, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 11).group(SkillGroups.DEFENSE_POSTURE)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.STATUS_MIRROR, essence, AscendanceTiers.TRANSCENDENT.id(),
                SkillCostBand.ADVANCED, 12).group(SkillGroups.DEFENSE_STATUS)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.PURE_STATE, essence, AscendanceTiers.TRANSCENDENT.id(),
                SkillCostBand.ADVANCED, 13).group(SkillGroups.DEFENSE_STATUS)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.RIPOSTE, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 14).hint(SkillLayoutHint.UPPER).build());
    }

    private static void addVitality(List<SkillDefinition> skills) {
        ResourceLocation essence = EssenceTypes.VITALITY.id();

        skills.add(skill(SkillIds.RISING_RECOVERY, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 0).group(SkillGroups.VITALITY_RECOVERY)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.LIFE_STEAL, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 1).group(SkillGroups.VITALITY_RECOVERY)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.FEAST_REFLEX, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.FOUNDATION, 2).group(SkillGroups.VITALITY_SUSTENANCE)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.INNER_SUSTENANCE, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.FOUNDATION, 3).group(SkillGroups.VITALITY_SUSTENANCE)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.HUNGER_WARD, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 4).group(SkillGroups.VITALITY_DAMAGE_HANDLING)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.STAGGERED_PAIN, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 5).group(SkillGroups.VITALITY_DAMAGE_HANDLING)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.DAMAGE_CEILING, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 6).group(SkillGroups.VITALITY_DAMAGE_HANDLING)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.METABOLIC_CONVERSION, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.ADVANCED, 7).requires(SkillIds.HUNGER_WARD)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.PAIN_PURGE, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.ADVANCED, 8).requires(SkillIds.STAGGERED_PAIN)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.ADRENALINE, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.ADVANCED, 9).requires(SkillIds.DAMAGE_CEILING)
                .hint(SkillLayoutHint.LOWER).build());

        skills.add(skill(SkillIds.DEEP_WARD, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 10).requires(SkillIds.SOUL_WARD)
                .group(SkillGroups.VITALITY_WARD_MUTATION).hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.SOUL_WARD, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 11).hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.SHATTERING_WARD, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 12).requires(SkillIds.SOUL_WARD)
                .group(SkillGroups.VITALITY_WARD_MUTATION).hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.SECOND_WIND, essence, AscendanceTiers.TRANSCENDENT.id(),
                SkillCostBand.KEYSTONE, 13).group(SkillGroups.VITALITY_DEATH_DEFIANCE)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.SPIRIT_WALK, essence, AscendanceTiers.TRANSCENDENT.id(),
                SkillCostBand.KEYSTONE, 14).group(SkillGroups.VITALITY_DEATH_DEFIANCE)
                .hint(SkillLayoutHint.LOWER).build());
    }

    private static void addMobility(List<SkillDefinition> skills) {
        ResourceLocation essence = EssenceTypes.MOBILITY.id();

        skills.add(skill(SkillIds.RUNNING_MOMENTUM, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 0).hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.MOMENTUM_VAULT, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 1).requires(SkillIds.RUNNING_MOMENTUM)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.RUSH, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 2).requires(SkillIds.RUNNING_MOMENTUM)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.TERRAIN_FREEDOM, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 3).hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.AQUATIC_BODY, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 4).requires(SkillIds.TERRAIN_FREEDOM)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.WATER_WALKING, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 5).requires(SkillIds.AQUATIC_BODY)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.LAVABORN, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 6).requires(SkillIds.AQUATIC_BODY)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.IMPACT_CONTROL, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 7).hint(SkillLayoutHint.LOWER).build());

        skills.add(skill(SkillIds.CHARGED_JUMP, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 8).requires(SkillIds.IMPACT_CONTROL)
                .group(SkillGroups.MOBILITY_JUMP_STYLE).hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.DOUBLE_JUMP, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 9).requires(SkillIds.IMPACT_CONTROL)
                .group(SkillGroups.MOBILITY_JUMP_STYLE).hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.VECTOR_JUMP, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.KEYSTONE, 10).requires(SkillIds.DOUBLE_JUMP)
                .group(SkillGroups.MOBILITY_JUMP_STYLE).replaces(SkillIds.DOUBLE_JUMP)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.ESSENCE_WINGS, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 11)
                .requires(SkillIds.IMPACT_CONTROL, SkillIds.FATIGUE_FLIGHT)
                .requirements(milestone(SkillMilestones.SKY_LIMIT))
                .group(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT)
                .replaces(SkillIds.FATIGUE_FLIGHT).hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.FATIGUE_FLIGHT, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 12).requires(SkillIds.IMPACT_CONTROL)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.VECTOR_BOOST, essence, AscendanceTiers.TRANSCENDENT.id(),
                SkillCostBand.KEYSTONE, 13)
                .requires(SkillIds.ESSENCE_WINGS, SkillIds.VECTOR_JUMP)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.UNTETHERED_FLIGHT, essence, AscendanceTiers.TRANSCENDENT.id(),
                SkillCostBand.KEYSTONE, 14)
                .requires(SkillIds.FATIGUE_FLIGHT, SkillIds.IMPACT_CONTROL)
                .requirements(milestone(SkillMilestones.SKY_LIMIT))
                .group(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT)
                .replaces(SkillIds.FATIGUE_FLIGHT).hint(SkillLayoutHint.LOWER).build());
    }

    private static void addGathering(List<SkillDefinition> skills) {
        ResourceLocation essence = EssenceTypes.GATHERING.id();

        skills.add(skill(SkillIds.TOOL_INSTINCT, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 0).hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.MINING_MOMENTUM, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 1).requires(SkillIds.TOOL_INSTINCT)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.NATURES_BOON, essence, AscendanceTiers.TRANSCENDENT.id(),
                SkillCostBand.KEYSTONE, 2).requires(SkillIds.TOOL_INSTINCT)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.ORE_SIGHT, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.ADVANCED, 3).group(SkillGroups.GATHERING_SURVEY_FOCUS)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.TREASURE_SENSE, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.ADVANCED, 4).group(SkillGroups.GATHERING_SURVEY_FOCUS)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.HUNTERS_STUDY, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 5).group(SkillGroups.GATHERING_LOOT_CALLING)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.ESSENCE_BLOOM, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 6).group(SkillGroups.GATHERING_LOOT_CALLING)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.TORCHBEARER, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.FOUNDATION, 7).requires(SkillIds.TOOL_INSTINCT)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.VERDANT_STRIDE, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 8).group(SkillGroups.GATHERING_RURAL_CALLING)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.HERDKEEPER, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 9).group(SkillGroups.GATHERING_RURAL_CALLING)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.ANIMAL_GIFT, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 10).requires(SkillIds.HERDKEEPER)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.FISHING_INSTINCT, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 11).hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.FISHERS_CALL, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 12).requires(SkillIds.FISHING_INSTINCT)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.SALVAGERS_CRAFT, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 13).hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.POCKET_NETS, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 14).requires(SkillIds.FISHING_INSTINCT)
                .hint(SkillLayoutHint.LOWER).build());
    }

    private static void addUtility(List<SkillDefinition> skills) {
        ResourceLocation essence = EssenceTypes.UTILITY.id();

        skills.add(skill(SkillIds.THREAT_SENSE, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 0).group(SkillGroups.UTILITY_SENSE_FOCUS)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.HUNTERS_LEDGER, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 1).requires(SkillIds.THREAT_SENSE)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.WAYLIGHT, essence, AscendanceTiers.DORMANT.id(),
                SkillCostBand.FOUNDATION, 2).group(SkillGroups.UTILITY_SENSE_FOCUS)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.RESTFUL_MENDING, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 3).group(SkillGroups.UTILITY_MAINTENANCE)
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.DURABILITY_REVERSAL, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 4).group(SkillGroups.UTILITY_MAINTENANCE)
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.TEMPERED_REPAIR, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 5).group(SkillGroups.UTILITY_MAINTENANCE)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.VILLAGE_PATRON, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.FOUNDATION, 6)
                .requirements(milestone(SkillMilestones.HERO_OF_THE_VILLAGE))
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.BONDED_COMPANION, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 7)
                .requirements(milestone(SkillMilestones.BEST_FRIENDS_FOREVER))
                .hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.POTION_DURATION, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.FOUNDATION, 8)
                .requirements(milestone(SkillMilestones.LOCAL_BREWERY))
                .hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.POTION_RELAY, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 9).requires(SkillIds.POTION_DURATION)
                .hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.SANCTUARY, essence, AscendanceTiers.TRANSCENDENT.id(),
                SkillCostBand.KEYSTONE, 10).requires(SkillIds.WAYLIGHT)
                .requirements(milestone(SkillMilestones.BEACON_ACTIVATION))
                .toggle().hint(SkillLayoutHint.UPPER).build());
        skills.add(skill(SkillIds.INDUSTRIOUS_PRESENCE, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 11).toggle().hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.CONTAINMENT_FIELD, essence, AscendanceTiers.ASCENDANT.id(),
                SkillCostBand.KEYSTONE, 12)
                .toggle().hint(SkillLayoutHint.CENTER).build());
        skills.add(skill(SkillIds.FRIENDLY_FIRE_WARD, essence, AscendanceTiers.AWAKENED.id(),
                SkillCostBand.ADVANCED, 13).hint(SkillLayoutHint.LOWER).build());
        skills.add(skill(SkillIds.ENCHANTING_INSIGHT, essence, AscendanceTiers.RESONANT.id(),
                SkillCostBand.ADVANCED, 14)
                .requirements(milestone(SkillMilestones.ENCHANTER))
                .hint(SkillLayoutHint.LOWER).build());
    }

    private static Builder skill(
            ResourceLocation id,
            ResourceLocation essenceId,
            ResourceLocation requiredTierId,
            SkillCostBand costBand,
            int displayOrder
    ) {
        return new Builder(id, essenceId, requiredTierId, costBand, displayOrder);
    }

    private static PermanentMilestoneRequirement milestone(ResourceLocation id) {
        return new PermanentMilestoneRequirement(
                id,
                "skill_requirement.essence_ascendance.milestone." + id.getPath()
        );
    }

    private static final class Builder {
        private final ResourceLocation id;
        private final ResourceLocation essenceId;
        private final ResourceLocation requiredTierId;
        private final SkillCostBand costBand;
        private final int displayOrder;
        private List<ResourceLocation> prerequisites = List.of();
        private List<SkillRequirement> requirements = List.of();
        private ResourceLocation choiceGroup;
        private ResourceLocation replacementTarget;
        private SkillActivationPolicy activationPolicy = SkillActivationPolicy.AUTOMATIC;
        private SkillLayoutHint layoutHint = SkillLayoutHint.AUTO;

        private Builder(
                ResourceLocation id,
                ResourceLocation essenceId,
                ResourceLocation requiredTierId,
                SkillCostBand costBand,
                int displayOrder
        ) {
            this.id = id;
            this.essenceId = essenceId;
            this.requiredTierId = requiredTierId;
            this.costBand = costBand;
            this.displayOrder = displayOrder;
        }

        private Builder requires(ResourceLocation... prerequisiteIds) {
            prerequisites = List.copyOf(Arrays.asList(prerequisiteIds));
            return this;
        }

        private Builder requirements(SkillRequirement... skillRequirements) {
            requirements = List.copyOf(Arrays.asList(skillRequirements));
            return this;
        }

        private Builder group(ResourceLocation groupId) {
            choiceGroup = groupId;
            activationPolicy = SkillActivationPolicy.SELECTABLE;
            return this;
        }

        private Builder replaces(ResourceLocation targetId) {
            replacementTarget = targetId;
            activationPolicy = SkillActivationPolicy.SELECTABLE;
            return this;
        }

        private Builder toggle() {
            activationPolicy = SkillActivationPolicy.TOGGLE;
            return this;
        }

        private Builder hint(SkillLayoutHint hint) {
            layoutHint = hint;
            return this;
        }

        private SkillDefinition build() {
            String path = id.getPath();
            return new SkillDefinition(
                    id,
                    essenceId,
                    "skill.essence_ascendance." + path,
                    "skill.essence_ascendance." + path + ".description",
                    requiredTierId,
                    costBand,
                    prerequisites,
                    requirements,
                    choiceGroup,
                    replacementTarget,
                    activationPolicy,
                    displayOrder,
                    layoutHint
            );
        }
    }
}
