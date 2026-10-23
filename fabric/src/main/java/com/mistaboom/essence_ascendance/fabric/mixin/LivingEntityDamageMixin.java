package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
    //? if >=26.1.2
    /*@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)*/
    //? if <26.1.2
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    //? if >=26.1.2
    /*private float essenceAscendance$modifyIncomingDamage(float amount, net.minecraft.server.level.ServerLevel level, DamageSource source, float originalAmount) {*/
    //? if <26.1.2
    private float essenceAscendance$modifyIncomingDamage(float amount, DamageSource source, float originalAmount) {
        amount = EquipmentDamageService.modifyOutgoingSkillDamage((LivingEntity) (Object) this, source, amount);
        return (Object) this instanceof ServerPlayer player
                ? EquipmentDamageService.modifyIncomingDamage(player, source, amount) : amount;
    }

    //? if >=26.1.2 {
    /*
    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float essenceAscendance$completedBlock(LivingEntity receiver, net.minecraft.server.level.ServerLevel level,
            DamageSource source, float incomingDamage, Operation<Float> original) {
        ServerPlayer player = receiver instanceof ServerPlayer server ? server : null;
        if (player != null) EquipmentDamageService.captureBlockingShield(player, source);
        float blocked = original.call(receiver, level, source, incomingDamage);
        if (player != null && blocked > 0) {
            EquipmentDamageService.recordBlockedDamage(player, source, blocked);
            EquipmentDamageService.commitBlock(player, source);
        }
        return blocked;
    }
    *///?} else {
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurtCurrentlyUsedShield(F)V"))
    private void essenceAscendance$completedBlock(LivingEntity receiver, float blockedDamage,
                                                   Operation<Void> original, DamageSource source,
                                                   float incomingDamage) {
        ServerPlayer player = receiver instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player != null) {
            // Capture the active stack before durability loss can Fracture it.
            EquipmentDamageService.captureBlockingShield(player, source);
        }

        original.call(receiver, blockedDamage);

        if (player != null) {
            /*
             * Fabric's vanilla block path passes the exact amount it then
             * stores as damage blocked. Commit here instead of at TAIL:
             * LivingEntity#hurt can return early during post-hit immunity,
             * even after a real shield block has already occurred.
             */
            EquipmentDamageService.recordBlockedDamage(player, source, blockedDamage);
            EquipmentDamageService.commitBlock(player, source);
        }
    }
    //?}

}
