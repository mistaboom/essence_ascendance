package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.AscendanceToolMiningService;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

public final class AscendanceHoeItem
        extends HoeItem
        implements EquipmentProfileItem {

    public AscendanceHoeItem(
            Tier tier,
            Properties properties
    ) {
        super(tier, properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.HOE.id();
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slotId,
            boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        AscendanceToolMiningService.sync(
                stack,
                level,
                entity,
                BlockTags.MINEABLE_WITH_HOE,
                equipmentProfileId()
        );
    }
}
