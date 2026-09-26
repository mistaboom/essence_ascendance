package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client-facing visual mapping for one evolving Ascendance equipment stack.
 *
 * Gameplay tier data remains owned by {@link EquipmentTierData}.  This class
 * maps custom armor art without changing progression logic.
 */
public final class EquipmentTierVisuals {

    /** White leaves a grayscale or deliberately colored layer unchanged. */
    public static final int CLEAR_TINT = 0xFFFFFFFF;

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

    /** Base, accent and optional unchanged layers use tint indices 0, 1 and 2. */
    public static int itemTint(ItemStack stack, int tintIndex) {
        return switch (tintIndex) {
            case 0 -> opaque(primaryRgb(stack));
            case 1 -> opaque(armorAccentRgb(stack));
            default -> CLEAR_TINT;
        };
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

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
     * Each armor slot has one baked atlas combining independently tinted
     * base/accent masks at build time, including Latent's lighter grey base.
     *
     * The ItemStack remains the same Ascendance item; only the client texture
     * changes.  Enchantment foil/glint is intentionally left to vanilla.
     */
    public static ResourceLocation armorTexture(
            ItemStack stack,
            boolean innerLayer
    ) {
        if (stack == null || !(stack.getItem() instanceof AscendanceArmorItem armor)) {
            return null;
        }

        String piece = switch (armor.ascendanceSlot()) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
        if (piece == null) return null;

        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                "textures/armor/ascendance/generated/" + piece + "_"
                        + EquipmentTierData.tier(stack).serializedName() + ".png"
        );
    }
}
