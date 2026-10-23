package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** NeoForge's silent overload may bypass the common two-argument equipment hook. */
@Mixin(LivingEntity.class)
public abstract class NeoForgeEquipmentPolicyMixin {
    @WrapMethod(method = "setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;Z)V")
    private void essenceAscendance$equipmentMutation(EquipmentSlot slot, ItemStack stack,
                                                     boolean silent, Operation<Void> original) {
        if (!((Object) this instanceof Player player)) {
            original.call(slot, stack, silent);
            return;
        }
        TwoHandedEquipmentService.begin(player);
        try {
            original.call(slot, stack, silent);
        } finally {
            TwoHandedEquipmentService.end(player);
        }
    }
}
