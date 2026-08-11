package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentConduitItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentConduitType;
import net.minecraft.world.item.Item;

/*
 * Native magic conduit item.
 *
 * The focus intentionally has no spell behavior yet. Its primary magic
 * properties are chassis-driven:
 *
 *     magic_damage
 *     magic_cast_speed
 *
 * WeaponChassisService calculates those authoritative targets. Actual
 * casting behavior belongs to the later gameplay/effect implementation.
 */
public final class AscendanceMagicWeaponItem
        extends Item
        implements EquipmentConduitItem {

    public AscendanceMagicWeaponItem(
            Item.Properties properties
    ) {

        super(
                properties
        );
    }


    @Override
    public EquipmentConduitType conduitType() {

        return EquipmentConduitType.MAGIC_WEAPON;
    }
}
