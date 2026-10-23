package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native sweep selector: NeoForge adds a patched sweep overload in 26.1.2. */
@Mixin(Player.class)
public abstract class PlayerSkillSweepMixin {
    //? if >=26.1.2
    /*@WrapOperation(method = "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;FLnet/minecraft/world/phys/AABB;)V", at = @At(value = "INVOKE",*/
    //? if <26.1.2
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            //? if >=26.1.2
            /*target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))*/
            //? if <26.1.2
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    //? if >=26.1.2
    /*private boolean essenceAscendance$nativeSweep(LivingEntity target, net.minecraft.server.level.ServerLevel level, DamageSource source, float amount,*/
    //? if <26.1.2
    private boolean essenceAscendance$nativeSweep(LivingEntity target, DamageSource source, float amount,
                                                  Operation<Boolean> original) {
        //? if >=26.1.2
        /*if (!((Object) this instanceof ServerPlayer player)) return original.call(target, level, source, amount);*/
        //? if <26.1.2
        if (!((Object) this instanceof ServerPlayer player)) return original.call(target, source, amount);
        return EquipmentDamageService.observeNativeSkillSweep(player, target, source,
                //? if >=26.1.2
                /*() -> original.call(target, level, source, amount));*/
                //? if <26.1.2
                () -> original.call(target, source, amount));
    }

}
