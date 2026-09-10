package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

/** Stable, non-localized identities for all 90 curated skills. */
public final class SkillIds {

    public static final ResourceLocation FRENZY = id("frenzy");
    public static final ResourceLocation ARMOR_CRACK = id("armor_crack");
    public static final ResourceLocation DESPERATION = id("desperation");
    public static final ResourceLocation DEATH_RUSH = id("death_rush");
    public static final ResourceLocation KINDLING = id("kindling");
    public static final ResourceLocation COMBUSTION = id("combustion");
    public static final ResourceLocation FROSTBITE = id("frostbite");
    public static final ResourceLocation SHATTER = id("shatter");
    public static final ResourceLocation STATIC_CHARGE = id("static_charge");
    public static final ResourceLocation CHAIN_STRIKE = id("chain_strike");
    public static final ResourceLocation HOMING_PROJECTILE = id("homing_projectile");
    public static final ResourceLocation RICOCHET = id("ricochet");
    public static final ResourceLocation PIERCING_PROJECTILE = id("piercing_projectile");
    public static final ResourceLocation EXPLOSIVE_PAYLOAD = id("explosive_payload");
    public static final ResourceLocation ROOTING_PAYLOAD = id("rooting_payload");

    public static final ResourceLocation GUARDED_ADVANCE = id("guarded_advance");
    public static final ResourceLocation SHIELD_RAM = id("shield_ram");
    public static final ResourceLocation STORED_FORCE = id("stored_force");
    public static final ResourceLocation GUARD_AMPLIFIER = id("guard_amplifier");
    public static final ResourceLocation REFLEXIVE_WARD = id("reflexive_ward");
    public static final ResourceLocation CROWD_REPRISAL = id("crowd_reprisal");
    public static final ResourceLocation INTERCEPTOR = id("interceptor");
    public static final ResourceLocation TRAJECTORY_THEFT = id("trajectory_theft");
    public static final ResourceLocation PROJECTILE_DRAG_FIELD = id("projectile_drag_field");
    public static final ResourceLocation EVASIVE_CURRENT = id("evasive_current");
    public static final ResourceLocation BULWARK_STANCE = id("bulwark_stance");
    public static final ResourceLocation ADAPTIVE_GUARD = id("adaptive_guard");
    public static final ResourceLocation STATUS_MIRROR = id("status_mirror");
    public static final ResourceLocation PURE_STATE = id("pure_state");
    public static final ResourceLocation RIPOSTE = id("riposte");

    public static final ResourceLocation RISING_RECOVERY = id("rising_recovery");
    public static final ResourceLocation LIFE_STEAL = id("life_steal");
    public static final ResourceLocation FEAST_REFLEX = id("feast_reflex");
    public static final ResourceLocation INNER_SUSTENANCE = id("inner_sustenance");
    public static final ResourceLocation HUNGER_WARD = id("hunger_ward");
    public static final ResourceLocation STAGGERED_PAIN = id("staggered_pain");
    public static final ResourceLocation DAMAGE_CEILING = id("damage_ceiling");
    public static final ResourceLocation METABOLIC_CONVERSION = id("metabolic_conversion");
    public static final ResourceLocation PAIN_PURGE = id("pain_purge");
    public static final ResourceLocation ADRENALINE = id("adrenaline");
    public static final ResourceLocation DEEP_WARD = id("deep_ward");
    public static final ResourceLocation SOUL_WARD = id("soul_ward");
    public static final ResourceLocation SHATTERING_WARD = id("shattering_ward");
    public static final ResourceLocation SECOND_WIND = id("second_wind");
    public static final ResourceLocation SPIRIT_WALK = id("spirit_walk");

    public static final ResourceLocation RUNNING_MOMENTUM = id("running_momentum");
    public static final ResourceLocation MOMENTUM_VAULT = id("momentum_vault");
    public static final ResourceLocation RUSH = id("rush");
    public static final ResourceLocation TERRAIN_FREEDOM = id("terrain_freedom");
    public static final ResourceLocation AQUATIC_BODY = id("aquatic_body");
    public static final ResourceLocation WATER_WALKING = id("water_walking");
    public static final ResourceLocation LAVABORN = id("lavaborn");
    public static final ResourceLocation IMPACT_CONTROL = id("impact_control");
    public static final ResourceLocation CHARGED_JUMP = id("charged_jump");
    public static final ResourceLocation DOUBLE_JUMP = id("double_jump");
    public static final ResourceLocation VECTOR_JUMP = id("vector_jump");
    public static final ResourceLocation ESSENCE_WINGS = id("essence_wings");
    public static final ResourceLocation FATIGUE_FLIGHT = id("fatigue_flight");
    public static final ResourceLocation VECTOR_BOOST = id("vector_boost");
    public static final ResourceLocation UNTETHERED_FLIGHT = id("untethered_flight");

    public static final ResourceLocation TOOL_INSTINCT = id("tool_instinct");
    public static final ResourceLocation MINING_MOMENTUM = id("mining_momentum");
    public static final ResourceLocation NATURES_BOON = id("natures_boon");
    public static final ResourceLocation ORE_SIGHT = id("ore_sight");
    public static final ResourceLocation TREASURE_SENSE = id("treasure_sense");
    public static final ResourceLocation HUNTERS_STUDY = id("hunters_study");
    public static final ResourceLocation ESSENCE_BLOOM = id("essence_bloom");
    public static final ResourceLocation TORCHBEARER = id("torchbearer");
    public static final ResourceLocation VERDANT_STRIDE = id("verdant_stride");
    public static final ResourceLocation HERDKEEPER = id("herdkeeper");
    public static final ResourceLocation ANIMAL_GIFT = id("animal_gift");
    public static final ResourceLocation FISHING_INSTINCT = id("fishing_instinct");
    public static final ResourceLocation FISHERS_CALL = id("fishers_call");
    public static final ResourceLocation SALVAGERS_CRAFT = id("salvagers_craft");
    public static final ResourceLocation POCKET_NETS = id("pocket_nets");

    public static final ResourceLocation THREAT_SENSE = id("threat_sense");
    public static final ResourceLocation HUNTERS_LEDGER = id("hunters_ledger");
    public static final ResourceLocation WAYLIGHT = id("waylight");
    public static final ResourceLocation RESTFUL_MENDING = id("restful_mending");
    public static final ResourceLocation DURABILITY_REVERSAL = id("durability_reversal");
    public static final ResourceLocation TEMPERED_REPAIR = id("tempered_repair");
    public static final ResourceLocation VILLAGE_PATRON = id("village_patron");
    public static final ResourceLocation BONDED_COMPANION = id("bonded_companion");
    public static final ResourceLocation POTION_DURATION = id("potion_duration");
    public static final ResourceLocation POTION_RELAY = id("potion_relay");
    public static final ResourceLocation SANCTUARY = id("sanctuary");
    public static final ResourceLocation INDUSTRIOUS_PRESENCE = id("industrious_presence");
    public static final ResourceLocation CONTAINMENT_FIELD = id("containment_field");
    public static final ResourceLocation FRIENDLY_FIRE_WARD = id("friendly_fire_ward");
    public static final ResourceLocation ENCHANTING_INSIGHT = id("enchanting_insight");

    private SkillIds() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }
}
