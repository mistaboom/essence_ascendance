package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.AscendanceToolMiningService;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
//? if >=26.1.2
/*import net.minecraft.world.entity.EquipmentSlot;*/
//? if >=26.1.2
/*import net.minecraft.server.level.ServerLevel;*/
//? if >=26.1.2
/*import net.minecraft.world.item.Item;*/
//? if <26.1.2
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
//? if >=26.1.2
/*import net.minecraft.world.item.ToolMaterial;*/
//? if <26.1.2
import net.minecraft.world.item.Tier;
//? if <26.1.2
import net.minecraft.world.level.Level;

public final class AscendanceAxeItem
        //? if >=26.1.2
        /*extends Item*/
        //? if <26.1.2
        extends AxeItem
        implements EquipmentProfileItem, EmissiveAccentItem {

    //? if >=26.1.2
    /*public AscendanceAxeItem(ToolMaterial tier, Properties properties) {*/
    //? if <26.1.2
    public AscendanceAxeItem(Tier tier, Properties properties) {
        //? if >=26.1.2
        /*super(properties);*/
        //? if <26.1.2
        super(tier, properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.AXE.id();
    }

    @Override
    //? if >=26.1.2
    /*public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {*/
    //? if <26.1.2
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        //? if >=26.1.2
        /*super.inventoryTick(stack, level, entity, slot);*/
        //? if <26.1.2
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        AscendanceToolMiningService.sync(
                stack,
                level,
                entity,
                BlockTags.MINEABLE_WITH_AXE,
                equipmentProfileId()
        );
    }



}
