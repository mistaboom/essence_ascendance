package com.mistaboom.essence_ascendance.visual;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import net.minecraft.resources.ResourceLocation;

/** Canonical identity colors. Interaction state changes emphasis, never the category or tier hue. */
public final class AscendancePalette {
    public static final int OFFENSE = CanonicalPaletteValues.OFFENSE;
    public static final int DEFENSE = CanonicalPaletteValues.DEFENSE;
    public static final int MOBILITY = CanonicalPaletteValues.MOBILITY;
    public static final int UTILITY = CanonicalPaletteValues.UTILITY;
    public static final int VITALITY = CanonicalPaletteValues.VITALITY;
    public static final int GATHERING = CanonicalPaletteValues.GATHERING;

    public static final TierColors LATENT = new TierColors(CanonicalPaletteValues.LATENT_PRIMARY, CanonicalPaletteValues.LATENT_METAL);
    public static final TierColors DORMANT = new TierColors(CanonicalPaletteValues.DORMANT_PRIMARY, CanonicalPaletteValues.DORMANT_METAL);
    public static final TierColors AWAKENED = new TierColors(CanonicalPaletteValues.AWAKENED_PRIMARY, CanonicalPaletteValues.AWAKENED_METAL);
    public static final TierColors RESONANT = new TierColors(CanonicalPaletteValues.RESONANT_PRIMARY, CanonicalPaletteValues.RESONANT_METAL);
    public static final TierColors ASCENDANT = new TierColors(CanonicalPaletteValues.ASCENDANT_PRIMARY, CanonicalPaletteValues.ASCENDANT_METAL);
    public static final TierColors TRANSCENDENT = new TierColors(CanonicalPaletteValues.TRANSCENDENT_PRIMARY, CanonicalPaletteValues.TRANSCENDENT_METAL);

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
