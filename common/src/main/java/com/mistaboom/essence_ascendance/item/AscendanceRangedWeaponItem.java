package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentConduitItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentConduitType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;

/*
 * Native ranged conduit item.
 *
 * BowItem supplies the vanilla bow interaction behavior, but the
 * Ascendance weapon's primary ranged properties are chassis-driven:
 *
 *     ranged_damage
 *     ranged_attack_speed
 *
 * projectile_speed remains a separate BONUS stat.
 *
 * WeaponChassisService calculates the authoritative damage and draw-rate
 * targets. The gameplay effect layer will consume those targets rather
 * than adding them to vanilla bow damage/draw speed.
 */
public final class AscendanceRangedWeaponItem
        extends BowItem
        implements EquipmentConduitItem {

    public AscendanceRangedWeaponItem(
            Item.Properties properties
    ) {

        super(
                properties
        );
    }


    @Override
    public EquipmentConduitType conduitType() {

        return EquipmentConduitType.RANGED_WEAPON;
    }
}
