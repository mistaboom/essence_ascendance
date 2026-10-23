package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

//? if >=26.1.2
/*@Mixin(net.minecraft.world.entity.LivingEntity.class)*/
//? if <26.1.2
@Mixin(Player.class)
abstract class EquipmentPolicyPlayerMixin {
    //? if >=26.1.2
    /*@WrapMethod(method = "setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V")*/
    //? if <26.1.2
    @WrapMethod(method = "setItemSlot")
    //? if >=26.1.2
    /*private void essenceAscendance$equipment(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {*/
    //? if <26.1.2
    private void essenceAscendance$equipment(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        //? if >=26.1.2
        /*if (!((Object)this instanceof Player player)) { original.call(slot, stack); return; }*/
        //? if <26.1.2
        Player player = (Player)(Object)this;
        TwoHandedEquipmentService.begin(player);
        //? if >=26.1.2
        /*try { original.call(slot, stack); } finally { TwoHandedEquipmentService.end(player); }*/
        //? if <26.1.2
        try { original.call(slot, stack); } finally { TwoHandedEquipmentService.end(player); }
    }
}
