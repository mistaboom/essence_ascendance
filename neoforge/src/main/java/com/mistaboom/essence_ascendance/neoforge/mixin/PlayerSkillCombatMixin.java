package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native attack scope keeps early rejection, primary damage and sweep distinct. */
@Mixin(Player.class)
public abstract class PlayerSkillCombatMixin {
    @WrapMethod(method = "attack")
    private void essenceAscendance$primaryAttempt(Entity target, Operation<Void> original) {
        if ((Object) this instanceof ServerPlayer player) {
            EquipmentDamageService.runPrimarySkillAttack(player, target, () -> original.call(target));
        } else {
            original.call(target);
        }
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean essenceAscendance$primaryResult(Entity target, DamageSource source, float amount,
                                                     Operation<Boolean> original) {
        // The separate sweep invocation below never enters this primary-result probe.
        if (!((Object) this instanceof ServerPlayer)) return original.call(target, source, amount);
        return EquipmentDamageService.observePrimarySkillHit(target, source,
                () -> original.call(target, source, amount));
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean essenceAscendance$nativeSweep(LivingEntity target, DamageSource source, float amount,
                                                  Operation<Boolean> original) {
        if (!((Object) this instanceof ServerPlayer player)) return original.call(target, source, amount);
        return EquipmentDamageService.observeNativeSkillSweep(player, target, source,
                () -> original.call(target, source, amount));
    }

    // Player overrides actuallyHurt; wrapping LivingEntity alone misses PvP targets.
    @WrapMethod(method = "actuallyHurt")
    private void essenceAscendance$playerDamageMeasurement(DamageSource source, float amount,
                                                            Operation<Void> original) {
        EquipmentDamageService.observeSkillHealthDamage((Player) (Object) this, source,
                () -> original.call(source, amount));
    }
}
