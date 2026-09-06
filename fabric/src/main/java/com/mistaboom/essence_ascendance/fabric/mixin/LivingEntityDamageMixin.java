package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float essenceAscendance$modifyIncomingDamage(float amount, DamageSource source, float originalAmount) {
        return (Object) this instanceof ServerPlayer player
                ? EquipmentDamageService.modifyIncomingDamage(player, source, amount) : amount;
    }

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurtCurrentlyUsedShield(F)V"))
    private void essenceAscendance$captureBlock(LivingEntity receiver, float durabilityDamage, Operation<Void> original,
                                                DamageSource source, float incomingDamage) {
        if (receiver instanceof ServerPlayer player) EquipmentDamageService.captureBlockingShield(player, source);
        // Keep the context before the final durability hit can fracture the shield.
        original.call(receiver, durabilityDamage);
    }

    @Inject(method = "hurt", at = @At("TAIL"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void essenceAscendance$finalBlockedDamage(DamageSource source, float remainingDamage,
            CallbackInfoReturnable<Boolean> ci, float originalDamage, boolean blocked, float blockedDamage) {
        if ((Object) this instanceof ServerPlayer player) {
            EquipmentDamageService.recordBlockedDamage(player, source, blockedDamage);
            EquipmentDamageService.commitBlock(player, source);
        }
    }
}
