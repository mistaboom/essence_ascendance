package com.mistaboom.essence_ascendance.visual;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import net.minecraft.resources.ResourceLocation;

/** Canonical identity colors. Interaction state changes emphasis, never the category or tier hue. */
public final class AscendancePalette {
    public static final int OFFENSE = 0xD65368;
    public static final int DEFENSE = 0x5B8FD9;
    public static final int MOBILITY = 0x55C7D6;
    public static final int UTILITY = 0xAC7ADD;
    public static final int VITALITY = 0xE889B5;
    public static final int GATHERING = 0x58B88B;

    public static final TierColors LATENT = new TierColors(0xB8BDC4, 0xA86E4B);
    public static final TierColors DORMANT = new TierColors(0x9FA6AF, 0xB78650);
    public static final TierColors AWAKENED = new TierColors(0x858F9A, 0xC99F54);
    public static final TierColors RESONANT = new TierColors(0x6B7785, 0xDFB95F);
    public static final TierColors ASCENDANT = new TierColors(0x515E6D, 0xF2CF78);
    public static final TierColors TRANSCENDENT = new TierColors(0x394655, 0xFFE7AA);

    private AscendancePalette() { }

    public static int categoryRgb(ResourceLocation essenceId) {
        if (essenceId == null) return LATENT.primaryRgb();
        return switch (essenceId.getPath()) {
            case "offense" -> OFFENSE;
            case "defense" -> DEFENSE;
            case "mobility" -> MOBILITY;
            case "utility" -> UTILITY;
            case "vitality" -> VITALITY;
            case "gathering" -> GATHERING;
            default -> LATENT.primaryRgb();
        };
    }

    public static int categoryRgb(StatCategory category) {
        return switch (category) {
            case OFFENSE -> OFFENSE;
            case DEFENSE -> DEFENSE;
            case MOBILITY -> MOBILITY;
            case UTILITY -> UTILITY;
            case VITALITY -> VITALITY;
            case GATHERING -> GATHERING;
        };
    }

    public static int categoryRgb(EssenceDefinition essence) { return categoryRgb(essence.id()); }
    public static int categoryArgb(ResourceLocation essenceId) { return opaque(categoryRgb(essenceId)); }
    public static int categoryArgb(StatCategory category) { return opaque(categoryRgb(category)); }

    public static TierColors tier(ResourceLocation tierId) {
        return tier(tierId == null ? "latent" : tierId.getPath());
    }

    public static TierColors tier(EquipmentTier tier) { return tier(tier.serializedName()); }

    private static TierColors tier(String id) {
        return switch (id) {
            case "dormant" -> DORMANT;
            case "awakened" -> AWAKENED;
            case "resonant" -> RESONANT;
            case "ascendant" -> ASCENDANT;
            case "transcendent" -> TRANSCENDENT;
            default -> LATENT;
        };
    }

    public static int tierPrimaryRgb(ResourceLocation tierId) { return tier(tierId).primaryRgb(); }
    public static int tierPrimaryArgb(ResourceLocation tierId) { return opaque(tierPrimaryRgb(tierId)); }
    public static int tierMetalRgb(ResourceLocation tierId) { return tier(tierId).metalRgb(); }
    public static int tierMetalArgb(ResourceLocation tierId) { return opaque(tierMetalRgb(tierId)); }
    public static int tierPrimaryRgb(EquipmentTier tier) { return tier(tier).primaryRgb(); }
    public static int tierMetalRgb(EquipmentTier tier) { return tier(tier).metalRgb(); }
    public static int opaque(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }

    /** Grey primary is shared by armor and ingots; armor accents progress from copper to champagne. */
    public record TierColors(int primaryRgb, int metalRgb) {
        public TierColors {
            if ((primaryRgb & 0xFF000000) != 0 || (metalRgb & 0xFF000000) != 0)
                throw new IllegalArgumentException("Palette colors must use 24-bit RGB");
        }
    }
}
