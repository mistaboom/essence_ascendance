package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.vitality.VitalityDamageService;
import com.mistaboom.essence_ascendance.vitality.VitalityDeathDefianceService;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Both loader-specific routers feed this common native health-write commit point. */
@Mixin(Player.class)
public abstract class VitalityHealthCommitMixin {
    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"), require = 1, expect = 1, allow = 1)
    private void essenceAscendance$commitCapacity(Player player, float health, Operation<Void> original,
                                                   @Local(argsOnly = true) DamageSource source) {
        float healthBefore = player.getHealth();
        float maximumBefore = player.getMaxHealth();
        float resolved = player instanceof ServerPlayer server
                ? VitalityDeathDefianceService.interceptHealthWrite(server, source, healthBefore, health) : health;
        original.call(player, resolved);
        if (player instanceof ServerPlayer server)
            VitalityDamageService.commitHealthDamage(server, source, healthBefore, maximumBefore);
    }
}
