package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({LivingEntity.class, net.minecraft.world.entity.player.Player.class, net.minecraft.server.level.ServerPlayer.class})
abstract class EquipmentPolicyDamageMixin {
    @WrapMethod(method = "hurt")
    private boolean essenceAscendance$deferClassification(DamageSource source, float amount, Operation<Boolean> original) {
        TwoHandedEquipmentService.enterCombat();
        try { return original.call(source, amount); } finally { TwoHandedEquipmentService.exitCombat(); }
    }
}
