package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/*
 * Native magic-focus item declaration.
 * Spell behavior remains intentionally unimplemented in this architecture
 * tranche.
 */
public final class AscendanceMagicWeaponItem
        extends Item
        implements EquipmentProfileItem {

    public AscendanceMagicWeaponItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.MAGIC_FOCUS.id();
    }
}
