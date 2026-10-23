package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({LivingEntity.class, net.minecraft.world.entity.player.Player.class, net.minecraft.server.level.ServerPlayer.class})
abstract class EquipmentPolicyDamageMixin {
    //? if >=26.1.2
    /*@WrapMethod(method = "hurtServer")*/
    //? if <26.1.2
    @WrapMethod(method = "hurt")
    //? if >=26.1.2
    /*private boolean essenceAscendance$deferClassification(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {*/
    //? if <26.1.2
    private boolean essenceAscendance$deferClassification(DamageSource source, float amount, Operation<Boolean> original) {
        TwoHandedEquipmentService.enterCombat();
        //? if >=26.1.2
        /*try { return original.call(level, source, amount); } finally { TwoHandedEquipmentService.exitCombat(); }*/
        //? if <26.1.2
        try { return original.call(source, amount); } finally { TwoHandedEquipmentService.exitCombat(); }
    }
}
