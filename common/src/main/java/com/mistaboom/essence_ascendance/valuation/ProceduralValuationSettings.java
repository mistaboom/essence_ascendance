package com.mistaboom.essence_ascendance.valuation;

/** Small, shared estimate policy. None of these values asserts acquisition time or availability. */
public final class ProceduralValuationSettings {
    public static final long MAX_VALUE = Long.MAX_VALUE / com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService.SCALE;
    public static final long STORED_ENCHANTMENT_CARRIER_BUDGET = 4;
    public static final int MAX_ESTIMATE_PASSES = 12;
    public static final long DEFAULT_BASE = 24, BLOCK_BASE = 32, ORE_BASE = 180,
            RAW_MATERIAL_BASE = 160, INGOT_BASE = 150, GEM_BASE = 220, NUGGET_BASE = 18,
            STORAGE_BLOCK_BASE = 1350, TOOL_BASE = 240, WEAPON_BASE = 280, ARMOR_BASE = 300,
            TRANSPORT_BASE = 120, FUEL_BASE = 64;
    // Declared rarity is a coarse policy estimate, not a measured drop rate. Recipes can lower it.
    public static final long UNCOMMON_MINIMUM = 32 * DEFAULT_BASE,
            RARE_MINIMUM = 4 * UNCOMMON_MINIMUM, EPIC_MINIMUM = 4 * RARE_MINIMUM;
    public static final double UNCOMMON_MULTIPLIER = 2, RARE_MULTIPLIER = 4, EPIC_MULTIPLIER = 8;
    public static final double INTRINSIC_GLINT_MULTIPLIER = 2;
    private ProceduralValuationSettings() { }
}
