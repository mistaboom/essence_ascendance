package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.*;
import java.util.function.Predicate;

/** Inventory-owned completed mutations, including direct command/native calls and multi-write operations. */
@Mixin(Inventory.class)
abstract class EquipmentPolicyInventoryMixin {
    @Shadow @Final public Player player;
    @WrapMethod(method = "setItem")
    private void essenceAscendance$mutation0(int slot, ItemStack stack, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(slot, stack); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;")
    private ItemStack essenceAscendance$mutation1(int slot, int amount, Operation<ItemStack> original) {
        TwoHandedEquipmentService.begin(player);
        try { return original.call(slot, amount); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "removeItem(Lnet/minecraft/world/item/ItemStack;)V")
    private void essenceAscendance$mutation2(ItemStack stack, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(stack); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "removeItemNoUpdate")
    private ItemStack essenceAscendance$mutation3(int slot, Operation<ItemStack> original) {
        TwoHandedEquipmentService.begin(player);
        try { return original.call(slot); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "add(ILnet/minecraft/world/item/ItemStack;)Z")
    private boolean essenceAscendance$mutation4(int slot, ItemStack stack, Operation<Boolean> original) {
        TwoHandedEquipmentService.begin(player);
        try { return original.call(slot, stack); } finally { TwoHandedEquipmentService.end(player); }
    }
    //? if >=26.1.2 {
    /*
    @WrapMethod(method = "setSelectedItem")
    private ItemStack essenceAscendance$mutation5(ItemStack stack, Operation<ItemStack> original) {
        TwoHandedEquipmentService.begin(player);
        try { return original.call(stack); } finally { TwoHandedEquipmentService.end(player); }
    }
    *///?} else {
    @WrapMethod(method = "setPickedItem")
    private void essenceAscendance$mutation5(ItemStack stack, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(stack); } finally { TwoHandedEquipmentService.end(player); }
    }
    //?}

    @WrapMethod(method = "pickSlot")
    private void essenceAscendance$mutation6(int slot, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(slot); } finally { TwoHandedEquipmentService.end(player); }
    }
    //? if >=26.1.2 {
    /*
    @WrapMethod(method = "setSelectedSlot")
    private void essenceAscendance$mutation7(int direction, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(direction); } finally { TwoHandedEquipmentService.end(player); }
    }
    *///?} else {
    @WrapMethod(method = "swapPaint")
    private void essenceAscendance$mutation7(double direction, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(direction); } finally { TwoHandedEquipmentService.end(player); }
    }
    //?}

    @WrapMethod(method = "clearOrCountMatchingItems")
    private int essenceAscendance$mutation8(Predicate<ItemStack> predicate, int amount, Container container, Operation<Integer> original) {
        TwoHandedEquipmentService.begin(player);
        try { return original.call(predicate, amount, container); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "load")
    //? if >=26.1.2
    /*private void essenceAscendance$mutation9(net.minecraft.world.level.storage.ValueInput.TypedInputList tag, Operation<Void> original) {*/
    //? if <26.1.2
    private void essenceAscendance$mutation9(ListTag tag, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(tag); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "replaceWith")
    private void essenceAscendance$mutation10(Inventory inventory, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(inventory); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "clearContent")
    private void essenceAscendance$mutation11(Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "dropAll")
    private void essenceAscendance$mutation12(Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(); } finally { TwoHandedEquipmentService.end(player); }
    }
}
