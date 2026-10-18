package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentDefinition;
import com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Physical weapon range, independent of block/entity interaction Reach. */
public final class EquipmentAttackRange {
    public static final String PERSONALIZED_PERK = "maximum_attack_range";
    public static final double MAXIMUM_SUPPORTED = 30;
    private EquipmentAttackRange() { }

    public static double maximum(Player holder, ItemStack stack) {
        return maximum(stack, PersonalizedEquipmentService.active(holder, stack));
    }

    /** The supplied profile is the same eligible snapshot used by name and material presentation. */
    public static double maximum(ItemStack stack, Optional<PersonalizedEquipmentDefinition> eligible) {
        if (!(stack.getItem() instanceof EquipmentProfileItem item)) return 0;
        var profile = EquipmentProfileRegistry.get(item.equipmentProfileId()).orElse(null);
        if (profile == null) return 0;
        double ordinary = profile.maximumAttackRange(FracturedEquipmentData.isFractured(stack)
                ? EquipmentTier.LATENT : EquipmentTierData.tier(stack));
        if (ordinary <= 0) return 0;
        return Math.clamp(eligible.map(p -> p.perk(PERSONALIZED_PERK, ordinary)).orElse(ordinary),
                WeaponActionGeometry.REST_TIP, MAXIMUM_SUPPORTED);
    }
}
