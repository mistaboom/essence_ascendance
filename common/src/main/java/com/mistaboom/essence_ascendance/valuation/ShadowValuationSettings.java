package com.mistaboom.essence_ascendance.valuation;

/*
 * Central tuning values for the procedural Item -> Essence valuation shadow
 * engine.
 *
 * IMPORTANT: these values do not affect Crucible gameplay yields. The shadow
 * engine is diagnostic-only until the generated model has been reviewed and
 * deliberately promoted into the live mapping path.
 *
 * Keeping the heuristics here prevents recipe/tag/source analysis from
 * scattering constants throughout the implementation. These values define the
 * procedural economy's own foundational scale; they are intentionally NOT
 * normalized against the legacy hand-authored mapping totals or any preexisting
 * Focus/equipment/machine costs. Later systems should derive from this valuation
 * foundation, not force the valuation engine to fit them. A later tranche can
 * expose these values through the normal global configuration model after the
 * shape of the algorithm settles.
 */
public final class ShadowValuationSettings {

    public static final long MAX_VALUE = 2_000_000_000L;
    public static final int MAX_RECIPE_DEPTH = 10;
    public static final int MAX_ALTERNATIVES_PER_INGREDIENT = 12;
    public static final int MAX_DOWNSTREAM_EXAMPLES = 6;
    public static final int MAX_ROUTING_DEPTH = 3;
    public static final int MAX_ROUTING_DOWNSTREAM_RECIPES = 96;
    public static final int MAX_ROUTING_ALTERNATIVES_PER_INGREDIENT = 8;

    public static final long DEFAULT_BASE = 24L;
    public static final long BLOCK_BASE = 32L;
    public static final long ORE_BASE = 180L;
    public static final long RAW_MATERIAL_BASE = 160L;
    public static final long INGOT_BASE = 150L;
    public static final long GEM_BASE = 220L;
    public static final long NUGGET_BASE = 18L;
    public static final long STORAGE_BLOCK_BASE = 1_350L;
    public static final long FOOD_BASE = 55L;
    public static final long TOOL_BASE = 240L;
    public static final long WEAPON_BASE = 280L;
    public static final long ARMOR_BASE = 300L;
    public static final long REDSTONE_BASE = 90L;
    public static final long TRANSPORT_BASE = 120L;

    public static final double UNCOMMON_MULTIPLIER = 1.12;
    public static final double RARE_MULTIPLIER = 1.35;
    public static final double EPIC_MULTIPLIER = 1.75;

    public static final double NETHER_MULTIPLIER = 1.35;
    public static final double END_MULTIPLIER = 1.70;
    public static final double BOSS_SCALE_MULTIPLIER = 1.20;

    public static final double ORE_RATE_DENSE_MULTIPLIER = 0.88;
    public static final double ORE_RATE_SPARSE_MULTIPLIER = 1.22;
    public static final double DEEPSLATE_MULTIPLIER = 1.05;
    public static final double NEEDS_STONE_TOOL_MULTIPLIER = 1.06;
    public static final double NEEDS_IRON_TOOL_MULTIPLIER = 1.14;
    public static final double NEEDS_DIAMOND_TOOL_MULTIPLIER = 1.28;

    public static final double CRAFTING_PROCESS_MULTIPLIER = 1.035;
    public static final double SMELTING_PROCESS_MULTIPLIER = 1.08;
    public static final double BLASTING_PROCESS_MULTIPLIER = 1.07;
    public static final double SMOKING_PROCESS_MULTIPLIER = 1.06;
    public static final double CAMPFIRE_PROCESS_MULTIPLIER = 1.05;
    public static final double STONECUTTING_PROCESS_MULTIPLIER = 1.02;
    public static final double SMITHING_PROCESS_MULTIPLIER = 1.14;
    public static final double UNKNOWN_PROCESS_MULTIPLIER = 1.05;

    /*
     * Recipe construction is intentionally close to conservation. Ingredient
     * values already carry rarity/progression, so rarity and mod-namespace
     * must NOT be multiplied a second time at the assembled-item recipe. Only
     * a small structural/depth premium remains on top of the process type.
     */
    public static final double UNIQUE_INGREDIENT_STEP = 0.004;
    public static final double UNIQUE_INGREDIENT_CAP = 0.03;
    public static final double RARE_INGREDIENT_STEP = 0.0;
    public static final double RARE_INGREDIENT_CAP = 0.0;
    public static final double MOD_SPECIFIC_INGREDIENT_STEP = 0.0;
    public static final double MOD_SPECIFIC_INGREDIENT_CAP = 0.0;
    public static final double RECIPE_DEPTH_STEP = 0.006;
    public static final double RECIPE_DEPTH_CAP = 0.05;

    public static final long EASY_INGREDIENT_THRESHOLD = 120L;
    public static final long RARE_INGREDIENT_THRESHOLD = 900L;

    /*
     * Downstream recipe use is deliberately capped. It is a demand/utility
     * signal, not recursive value inheritance, which prevents A -> B -> A
     * feedback loops from inflating both items forever.
     */
    public static final double DOWNSTREAM_USE_STEP = 0.018;
    public static final double DOWNSTREAM_SIGNIFICANT_STEP = 0.025;
    public static final double DOWNSTREAM_CROSS_MOD_STEP = 0.010;
    public static final double DOWNSTREAM_MAX_MULTIPLIER = 1.32;

    /*
     * Essence routing is evidence propagation, not value multiplication. A
     * direct semantic identity (weapon, armor, mining tool, food, etc.) remains
     * strongest. Refined materials/components infer their identity from the
     * things they are used to make; acquisition context is only a small vote.
     * This prevents the old generic Gathering/Utility fallback from dominating
     * large modpacks while still allowing unknown modded components to learn
     * useful routing from their recipe graph.
     */
    public static final double ROUTING_DOWNSTREAM_WEIGHT_WITHOUT_DIRECT = 4.0;
    public static final double ROUTING_DOWNSTREAM_WEIGHT_WITH_DIRECT = 1.25;
    public static final double ROUTING_ACQUISITION_WEIGHT_WITHOUT_DIRECT = 0.70;
    public static final double ROUTING_ACQUISITION_WEIGHT_WITH_DIRECT = 0.30;
    public static final double ROUTING_COMPOSITION_WEIGHT = 1.60;

    /*
     * Direct-source event baselines. These are not normalization targets; they
     * represent the minimum effort unit for obtaining something through a mob
     * encounter or exploration container when the item itself has no useful
     * material-category baseline. Cheap crafting/mining alternatives can still
     * win normally.
     */
    public static final long MOB_DROP_BASE = 72L;
    public static final long CONTAINER_LOOT_BASE = 80L;

    /* Mob/drop source weighting. */
    public static final double MOB_DIFFICULTY_MAX_MULTIPLIER = 3.00;
    public static final double DROP_RARITY_EXPONENT = 0.35;
    public static final double DROP_RARITY_MAX_MULTIPLIER = 12.00;
    public static final double DROP_QUANTITY_MIN_MULTIPLIER = 0.55;
    public static final double MOB_SPAWN_RARITY_MIN_MULTIPLIER = 0.85;
    public static final double MOB_SPAWN_RARITY_MAX_MULTIPLIER = 3.25;
    public static final double NON_BIOME_SPAWN_MULTIPLIER = 3.00;
    public static final double PLAYER_SUMMONED_BOSS_MULTIPLIER = 2.75;
    public static final double UNIQUE_FIRST_KILL_MULTIPLIER = 3.25;
    public static final double FIXED_STRUCTURE_TREASURE_MULTIPLIER = 1.85;

    /* Renewable/farmable marginal-acquisition weighting. */
    public static final double CROP_RENEWABLE_REMAINDER = 0.42;
    public static final double SEED_RENEWABLE_REMAINDER = 0.34;
    public static final double SAPLING_RENEWABLE_REMAINDER = 0.48;
    public static final double LOG_RENEWABLE_REMAINDER = 0.68;
    public static final double LEAF_RENEWABLE_REMAINDER = 0.52;
    public static final double COMMON_MOB_DROP_RENEWABLE_REMAINDER = 0.58;
    public static final double UNCOMMON_MOB_DROP_RENEWABLE_REMAINDER = 0.68;
    public static final double RARE_MOB_DROP_RENEWABLE_REMAINDER = 0.80;
    public static final double VERY_RARE_MOB_DROP_RENEWABLE_REMAINDER = 0.88;
    public static final double FISHING_RENEWABLE_REMAINDER = 0.78;

    /* Fishing is a repeatable direct acquisition path. */
    public static final long FISHING_LOOT_BASE = 65L;
    public static final double FISHING_SOURCE_MULTIPLIER = 1.12;

    /* Villager / wandering-trader acquisition. */
    public static final double TRADE_LEVEL_STEP = 0.055;
    public static final double TRADE_LEVEL_MAX_MULTIPLIER = 1.28;
    public static final double TRADE_LISTING_RARITY_EXPONENT = 0.12;
    public static final double TRADE_LISTING_RARITY_MAX_MULTIPLIER = 1.40;
    public static final double TRADE_LOW_STOCK_MAX_MULTIPLIER = 1.18;
    public static final double WANDERING_TRADER_MULTIPLIER = 1.18;

    /*
     * Container/chest context is deliberately weaker than the actual per-table
     * loot probability. The table's rolls/weights/counts provide the strongest
     * frequency signal; these multipliers represent the progression and likely
     * exploration cost of reaching the container when that can be inferred.
     */
    public static final double CONTAINER_COMMON_MULTIPLIER = 0.90;
    public static final double CONTAINER_STANDARD_MULTIPLIER = 1.00;
    public static final double CONTAINER_EXPLORATION_MULTIPLIER = 1.12;
    public static final double CONTAINER_RARE_MULTIPLIER = 1.28;
    public static final double CONTAINER_LATE_GAME_MULTIPLIER = 1.48;
    public static final double CONTAINER_TREASURE_MULTIPLIER = 1.62;
    public static final double STRUCTURE_REFERENCE_SPACING = 24.0;
    public static final double STRUCTURE_FREQUENCY_MIN_MULTIPLIER = 0.85;
    public static final double STRUCTURE_FREQUENCY_MAX_MULTIPLIER = 4.50;
    public static final double CONTAINER_TEMPLATE_DENSITY_MIN_MULTIPLIER = 0.65;
    public static final double UNKNOWN_STRUCTURE_FREQUENCY_MULTIPLIER = 1.50;
    public static final double UNRESOLVED_SOURCE_CONDITION_MULTIPLIER = 1.35;

    /*
     * Generic advancement progression is a soft/capped signal. It is strong
     * enough to distinguish a late mod progression reward from an early raw
     * material, but it cannot overpower a clearly easier acquisition path by
     * itself. Optional quest/stage adapters will later feed the same model.
     */
    public static final double ADVANCEMENT_PROGRESSION_MAX_MULTIPLIER = 1.55;
    public static final int MAX_PROGRESSION_EVIDENCE_EXAMPLES = 4;

    private ShadowValuationSettings() {
    }
}
