package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Exact requested identity colors and shared equipment/carrier material contracts. */
public final class AscendancePaletteTest {
    private static int assertions;

    public static void main(String[] args) {
        var categories = Map.of("offense", 0xD65368, "defense", 0x5B8FD9, "mobility", 0x55C7D6,
                "utility", 0xAC7ADD, "vitality", 0xE889B5, "gathering", 0x58B88B);
        for (var entry : categories.entrySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("essence_ascendance", entry.getKey());
            check(AscendancePalette.categoryRgb(id) == entry.getValue(), "Category RGB differs from user palette: " + entry.getKey());
            check(AscendancePalette.categoryArgb(id) == (0xFF000000 | entry.getValue()), "Category alpha changes RGB identity");
            check(ItemEssenceTooltipClientState.colorFor(id).getValue() == entry.getValue(), "Tooltip substituted a nearest ChatFormatting color");
            check(AscendancePalette.categoryRgb(StatCategory.valueOf(entry.getKey().toUpperCase(java.util.Locale.ROOT)))
                    == entry.getValue(), "Stat and Essence category mappings disagree");
        }
        int[] primary = {0xB8BDC4, 0x9FA6AF, 0x858F9A, 0x6B7785, 0x515E6D, 0x394655};
        int[] accent = {0xA86E4B, 0xB78650, 0xC99F54, 0xDFB95F, 0xF2CF78, 0xFFE7AA};
        for (EquipmentTier tier : EquipmentTier.values()) {
            int index = tier.ordinal();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("essence_ascendance", tier.serializedName());
            check(AscendancePalette.tierPrimaryRgb(id) == primary[index], "Tier grey primary differs from user palette");
            check(AscendancePalette.tierMetalRgb(id) == accent[index], "Tier metal accent differs from user palette");
            check(EquipmentTierVisuals.primaryRgb(tier) == primary[index], "Armor primary disagrees with tier palette");
            check(EquipmentTierVisuals.armorAccentRgb(tier) == accent[index], "Armor accent disagrees with tier palette");
            check(AscendancePalette.tierPrimaryArgb(id) == (0xFF000000 | primary[index]), "Tier primary ARGB changes hue");
            check(AscendancePalette.tierMetalArgb(id) == (0xFF000000 | accent[index]), "Tier metal ARGB changes hue");
            if (index > 0) {
                check((primary[index] & 0xFF) < (primary[index - 1] & 0xFF), "Tier grey fails to darken");
                check((accent[index] >>> 16) > (accent[index - 1] >>> 16), "Metal fails copper-to-champagne progression");
            }
        }
        for (EssenceFocusTier grade : EssenceFocusTier.values()) for (var essence : EssenceTypes.ORDERED) {
            var value = new EssentiumCarrierData.Value(essence, grade, 1);
            EquipmentTier tier = EquipmentTier.fromSerializedName(grade.serializedName());
            check(EssentiumCarrierVisuals.primaryRgb(value) == EquipmentTierVisuals.primaryRgb(tier),
                    "Ingot and armor body colors disagree for same tier");
            check(EssentiumCarrierVisuals.accentRgb(value) == categories.get(essence.id().getPath()),
                    "Infused ingot accent lost Essence identity");
        }
        check(AscendancePalette.categoryRgb(ResourceLocation.parse("test:unrecognized")) == 0xB8BDC4,
                "Unrecognized category has unstable fallback");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("AscendancePaletteTest: " + assertions + " checks PASS");
    }

    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
