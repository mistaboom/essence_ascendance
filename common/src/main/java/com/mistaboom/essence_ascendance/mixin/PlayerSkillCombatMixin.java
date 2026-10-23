package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shared native attack scope and health observation; loader hooks identify native sweeps. */
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
            //? if >=26.1.2
            /*target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))*/
            //? if <26.1.2
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean essenceAscendance$primaryResult(Entity target, DamageSource source, float amount,
                                                     Operation<Boolean> original) {
        // The separate native sweep invocation never enters this primary-result probe.
        if (!((Object) this instanceof ServerPlayer)) return original.call(target, source, amount);
        return EquipmentDamageService.observePrimarySkillHit(target, source,
                () -> original.call(target, source, amount));
    }

    // Player overrides actuallyHurt; wrapping LivingEntity alone misses PvP targets.
    @WrapMethod(method = "actuallyHurt")
    //? if >=26.1.2
    /*private void essenceAscendance$playerDamageMeasurement(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount,*/
    //? if <26.1.2
    private void essenceAscendance$playerDamageMeasurement(DamageSource source, float amount,
                                                            Operation<Void> original) {
        EquipmentDamageService.observeSkillHealthDamage((Player) (Object) this, source,
                //? if >=26.1.2
                /*() -> original.call(level, source, amount));*/
                //? if <26.1.2
                () -> original.call(source, amount));
    }
}
