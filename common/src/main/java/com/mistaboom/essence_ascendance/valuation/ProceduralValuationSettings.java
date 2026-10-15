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
    public static final double UNCOMMON_MULTIPLIER = 1.12, RARE_MULTIPLIER = 1.35, EPIC_MULTIPLIER = 1.75;
    private ProceduralValuationSettings() { }
}
