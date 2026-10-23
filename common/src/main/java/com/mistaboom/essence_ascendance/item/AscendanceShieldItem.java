package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
//? if >=26.1.2
/*import net.minecraft.world.InteractionResult;*/
//? if <26.1.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
//? if >=26.1.2
/*import net.minecraft.world.item.ItemUseAnimation;*/
//? if <26.1.2
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** One evolving shield; vanilla still owns facing and compatible shield use. */
public final class AscendanceShieldItem extends ShieldItem implements EquipmentProfileItem {
    public AscendanceShieldItem(Properties properties) { super(properties); }

    @Override public ResourceLocation equipmentProfileId() { return EquipmentProfiles.SHIELD.id(); }

    //? if >=26.1.2
    /*@Override public InteractionResult use(Level level, Player player, InteractionHand hand) {*/
    //? if <26.1.2
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) EquipmentShieldService.refreshNativeDurability(stack);
        //? if >=26.1.2
        /*if (!EquipmentShieldService.canGuard(player, stack)) return InteractionResult.FAIL;*/
        //? if <26.1.2
        if (!EquipmentShieldService.canGuard(player, stack)) return InteractionResultHolder.fail(stack);
        return super.use(level, player, hand);
    }

    //? if >=26.1.2
    /*@Override public ItemUseAnimation getUseAnimation(ItemStack stack) {*/
    //? if <26.1.2
    @Override public UseAnim getUseAnimation(ItemStack stack) {
        //? if >=26.1.2
        /*return FracturedEquipmentData.isFractured(stack) ? ItemUseAnimation.NONE : ItemUseAnimation.BLOCK;*/
        //? if <26.1.2
        return FracturedEquipmentData.isFractured(stack) ? UseAnim.NONE : UseAnim.BLOCK;
    }

    //? if >=26.1.2
    /*@Override public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity entity, net.minecraft.world.entity.EquipmentSlot slot) { super.inventoryTick(stack, level, entity, slot); EquipmentShieldService.refreshNativeDurability(stack); }*/
    //? if <26.1.2
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        //? if <26.1.2
        super.inventoryTick(stack, level, entity, slot, selected);
        //? if <26.1.2
        if (!level.isClientSide) EquipmentShieldService.refreshNativeDurability(stack);
    //? if <26.1.2
    }

    //? if <26.1.2
    @Override public int getEnchantmentValue() { return 15; }
}
