package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;

/*
 * BowItem supplies vanilla bow interaction behavior.
 * Tier baseline + profile defines base ranged characteristics; player
 * ranged_damage / ranged_attack_speed / projectile_speed remain separate
 * invested bonuses.
 */
public final class AscendanceRangedWeaponItem
        extends BowItem
        implements EquipmentProfileItem {

    public AscendanceRangedWeaponItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.RANGED_WEAPON.id();
    }
}
