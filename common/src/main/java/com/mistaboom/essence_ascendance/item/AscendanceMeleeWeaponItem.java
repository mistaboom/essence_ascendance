package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/*
 * Vanilla SwordItem supplies interaction semantics only.
 *
 * The Tier constructor values are bootstrap requirements. Actual intended
 * melee balance comes from:
 *
 * Ascendance tier -> EquipmentBaselineService -> melee profile -> invested
 * melee_damage / melee_attack_speed bonuses.
 */
public final class AscendanceMeleeWeaponItem
        extends SwordItem
        implements EquipmentProfileItem {

    public AscendanceMeleeWeaponItem(
            Tier tier,
            Item.Properties properties
    ) {
        super(tier, properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.MELEE_WEAPON.id();
    }
}
