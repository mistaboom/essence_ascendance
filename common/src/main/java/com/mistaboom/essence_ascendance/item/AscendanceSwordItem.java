package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if <26.1.2
import net.minecraft.world.item.SwordItem;
//? if >=26.1.2
/*import net.minecraft.world.item.ToolMaterial;*/
//? if <26.1.2
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
public final class AscendanceSwordItem
        //? if >=26.1.2
        /*extends Item*/
        //? if <26.1.2
        extends SwordItem
        implements EquipmentProfileItem, EmissiveAccentItem {

    //? if >=26.1.2
    /*public AscendanceSwordItem(ToolMaterial tier, Item.Properties properties) {*/
    //? if <26.1.2
    public AscendanceSwordItem(Tier tier, Item.Properties properties) {
        //? if >=26.1.2
        /*super(properties);*/
        //? if <26.1.2
        super(tier, properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.SWORD.id();
    }



}
