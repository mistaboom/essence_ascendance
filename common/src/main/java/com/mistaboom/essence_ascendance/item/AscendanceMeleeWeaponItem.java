package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentConduitItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentConduitType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/*
 * Native melee conduit item.
 *
 * IMPORTANT:
 *
 * This item intentionally does not define fixed attack-damage or
 * attack-speed attribute modifiers.
 *
 * Those primary combat properties are chassis values driven by:
 *
 *     melee_damage
 *     melee_attack_speed
 *
 * WeaponChassisService calculates the authoritative targets. The
 * gameplay effect/refresh layer will apply them later without stacking
 * them on top of a vanilla sword baseline.
 */
public final class AscendanceMeleeWeaponItem
        extends SwordItem
        implements EquipmentConduitItem {

    public AscendanceMeleeWeaponItem(
            Tier tier,
            Item.Properties properties
    ) {

        super(
                tier,
                properties
        );
    }


    @Override
    public EquipmentConduitType conduitType() {

        return EquipmentConduitType.MELEE_WEAPON;
    }
}
