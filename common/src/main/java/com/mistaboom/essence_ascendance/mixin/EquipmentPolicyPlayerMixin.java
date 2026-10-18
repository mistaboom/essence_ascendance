package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
abstract class EquipmentPolicyPlayerMixin {
    @WrapMethod(method = "setItemSlot")
    private void essenceAscendance$equipment(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        Player player = (Player)(Object)this;
        TwoHandedEquipmentService.begin(player);
        try { original.call(slot, stack); } finally { TwoHandedEquipmentService.end(player); }
    }
}
