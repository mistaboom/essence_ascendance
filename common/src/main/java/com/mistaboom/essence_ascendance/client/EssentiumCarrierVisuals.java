package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Stable client-side visual slots for data-bearing Essentium carriers.
 *
 * The six built-in Essence types have stable slots, so future custom art can
 * be dropped into the matching resource folder without changing carrier data,
 * recipes, or rendering code.
 *
 * Slots use exact 1/32 increments.  That leaves room for additional future
 * built-in Essence visuals while keeping the current model predicates stable.
 */
public final class EssentiumCarrierVisuals {

    public static final ResourceLocation MODEL_PROPERTY =
            ResourceLocation.fromNamespaceAndPath(
                    EssenceAscendance.MOD_ID,
                    "essentium_essence"
            );

    private static final float SLOT_SCALE = 1.0F / 32.0F;

    private EssentiumCarrierVisuals() {
    }

    /** Future carrier body layers share armor's exact grey tier primary. Existing vanilla models are placeholders. */
    public static int primaryRgb(EssentiumCarrierData.Value value) {
        return AscendancePalette.tierPrimaryRgb(EquipmentTier.fromSerializedName(value.grade().serializedName()));
    }

    /** Infused ingot inlays carry Essence identity; they do not use armor's tier metal accent. */
    public static int accentRgb(EssentiumCarrierData.Value value) {
        return AscendancePalette.categoryRgb(value.essence());
    }

    public static int primaryRgb(ItemStack stack) {
        return EssentiumCarrierData.read(stack).map(EssentiumCarrierVisuals::primaryRgb)
                .orElse(AscendancePalette.LATENT.primaryRgb());
    }

    public static int accentRgb(ItemStack stack) {
        return EssentiumCarrierData.read(stack).map(EssentiumCarrierVisuals::accentRgb)
                .orElse(AscendancePalette.LATENT.metalRgb());
    }

    /**
     * Returns 0 for an empty/invalid carrier so the generic gold fallback model
     * remains visible.  Valid carrier data resolves to one stable built-in
     * Essence visual slot.
     */
    public static float modelPropertyValue(ItemStack stack) {
        return EssentiumCarrierData.read(stack)
                .map(value -> slotFor(value.essence().id()) * SLOT_SCALE)
                .orElse(0.0F);
    }

    private static int slotFor(ResourceLocation essenceId) {
        if (essenceId == null
                || !EssenceAscendance.MOD_ID.equals(essenceId.getNamespace())) {
            return 0;
        }

        return switch (essenceId.getPath()) {
            case "offense" -> 1;
            case "defense" -> 2;
            case "vitality" -> 3;
            case "mobility" -> 4;
            case "gathering" -> 5;
            case "utility" -> 6;
            default -> 0;
        };
    }
}
