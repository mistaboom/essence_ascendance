package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/** Native bobber attachment seam. Only the server writes state; hand/attachment flags use entity data. */
public interface RodHookAccess {
    RodAttachmentState essenceAscendance$rodState();
    int essenceAscendance$rodHand();
    com.mistaboom.essence_ascendance.equipment.EquipmentTier essenceAscendance$rodTier();
    int essenceAscendance$attachment();
    net.minecraft.world.phys.Vec3 essenceAscendance$anchorPosition();
    void essenceAscendance$initializeRod(InteractionHand hand, ItemStack stack, EquipmentFishingRodService.Context context);
    void essenceAscendance$attachBlock(BlockHitResult hit);
    void essenceAscendance$attachEntity(Entity entity);
}
