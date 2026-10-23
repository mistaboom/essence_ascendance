package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fabric 1.21.1's native shield wear still tests the vanilla item identity. */
@Mixin(Player.class)
public abstract class NativeShieldDurabilityMixin {
    @WrapOperation(method = "hurtCurrentlyUsedShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean essenceAscendance$nativeShieldDurability(ItemStack stack,
                                                           Item item, Operation<Boolean> original) {
        return original.call(stack, item) || (item == Items.SHIELD
                && EquipmentShieldService.functional(stack));
    }
}
