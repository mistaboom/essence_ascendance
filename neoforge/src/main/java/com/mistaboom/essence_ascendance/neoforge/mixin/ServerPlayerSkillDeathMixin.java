package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ServerPlayer replaces LivingEntity.die and never calls its superclass. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSkillDeathMixin {
    @Unique private boolean essenceAscendance$skillDeathNotified;

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "die")
    private void essenceAscendance$preserveConditions(DamageSource source,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        SkillEffectRuntime.duringNativeDeath((ServerPlayer) (Object) this, () -> original.call(source));
    }

    @Inject(method = "die", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/level/ServerPlayer;setLastDeathLocation(Ljava/util/Optional;)V"))
    private void essenceAscendance$completedPlayerDeath(DamageSource source, CallbackInfo ci) {
        if (essenceAscendance$skillDeathNotified) return;
        essenceAscendance$skillDeathNotified = true;
        ServerPlayer player = (ServerPlayer) (Object) this;
        SkillEffectRuntime.onLivingDeath(player, source);
        PlayerRuntimeLifecycleService.onDeath(player);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void essenceAscendance$allowNextLife(CallbackInfo ci) {
        if (((ServerPlayer) (Object) this).isAlive()) essenceAscendance$skillDeathNotified = false;
    }
}
