package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** One evolving shield; vanilla still owns facing, raising delay and compatible shield use. */
public final class AscendanceShieldItem extends ShieldItem implements EquipmentProfileItem {
    public AscendanceShieldItem(Properties properties) { super(properties); }

    @Override public ResourceLocation equipmentProfileId() { return EquipmentProfiles.SHIELD.id(); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) EquipmentShieldService.refreshNativeDurability(stack);
        if (!EquipmentShieldService.canGuard(player, stack)) return InteractionResultHolder.fail(stack);
        return super.use(level, player, hand);
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) {
        return FracturedEquipmentData.isFractured(stack) ? UseAnim.NONE : UseAnim.BLOCK;
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide) EquipmentShieldService.refreshNativeDurability(stack);
    }

    @Override public int getEnchantmentValue() { return 15; }
}
