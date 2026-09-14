package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client-facing visual mapping for one evolving Ascendance equipment stack.
 *
 * Gameplay tier data remains owned by {@link EquipmentTierData}.  This class
 * deliberately contains only lightweight placeholder presentation mapping so
 * final custom art can replace resources without changing progression logic.
 */
public final class EquipmentTierVisuals {

    public static final ResourceLocation MODEL_PROPERTY =
            ResourceLocation.fromNamespaceAndPath(
                    EssenceAscendance.MOD_ID,
                    "tier"
            );

    private EquipmentTierVisuals() {
    }

    /** Shared grey body color for tier-aware armor and future ingot texture layers. */
    public static int primaryRgb(EquipmentTier tier) { return AscendancePalette.tierPrimaryRgb(tier); }

    /** Armor fittings use the tier's copper-to-champagne accent, never an Essence category hue. */
    public static int armorAccentRgb(EquipmentTier tier) { return AscendancePalette.tierMetalRgb(tier); }

    public static int primaryRgb(ItemStack stack) { return primaryRgb(EquipmentTierData.tier(stack)); }
    public static int armorAccentRgb(ItemStack stack) { return armorAccentRgb(EquipmentTierData.tier(stack)); }

    /**
     * Item-model predicates are clamped floats, so the six tiers are evenly
     * distributed across 0.0 -> 1.0.
     */
    public static float modelPropertyValue(ItemStack stack) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) {
            return 0.0F;
        }
        return EquipmentTierData.tier(stack).ordinal() / 5.0F;
    }

    /**
     * Vanilla armor texture used as a temporary worn-model placeholder.
     * These baked textures have no separate body/fitting tint masks. The palette
     * above is authoritative for replacement art; this resource lookup does not
     * pretend that a single multiplication can recolor both materials correctly.
     *
     * The ItemStack remains the same Ascendance item; only the client texture
     * changes.  Enchantment foil/glint is intentionally left to vanilla.
     */
    public static ResourceLocation armorTexture(
            ItemStack stack,
            boolean innerLayer
    ) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) {
            return null;
        }

        String material = switch (EquipmentTierData.tier(stack)) {
            case LATENT -> "leather";
            case DORMANT -> "chainmail";
            case AWAKENED -> "iron";
            case RESONANT -> "gold";
            case ASCENDANT -> "diamond";
            case TRANSCENDENT -> "netherite";
        };

        return ResourceLocation.fromNamespaceAndPath(
                "minecraft",
                "textures/models/armor/"
                        + material
                        + "_layer_"
                        + (innerLayer ? "2" : "1")
                        + ".png"
        );
    }
}
