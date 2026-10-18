package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentDefinition;
import com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Shared compact/held/world material resolution, also usable without a tracked holder on observers. */
public record EquipmentAppearance(int baseRgb, int accentRgb, int emissionAlpha) {
    public static EquipmentAppearance resolve(Player holder, ItemStack stack) {
        return resolve(stack, PersonalizedEquipmentService.active(holder,stack));
    }
    public static EquipmentAppearance resolve(ItemStack stack, Optional<PersonalizedEquipmentDefinition> profile) {
        var tier=EquipmentTierData.tier(stack);
        return new EquipmentAppearance(profile.map(PersonalizedEquipmentDefinition::baseRgb)
                .orElseGet(() -> EquipmentTierVisuals.primaryRgb(tier)),
                profile.map(PersonalizedEquipmentDefinition::accentRgb)
                        .orElseGet(() -> EquipmentTierVisuals.armorAccentRgb(tier)), ArmorEmission.alpha(tier));
    }
    public int tint(int material) { return material==0 ? baseRgb : material==1 ? accentRgb : 0xFFFFFF; }
    public int luminousRgb() { return ArmorEmission.luminousColor(accentRgb); }
}
