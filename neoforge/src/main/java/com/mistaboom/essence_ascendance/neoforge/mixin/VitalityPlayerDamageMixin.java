package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.mistaboom.essence_ascendance.vitality.DeferredDamageService;
import com.mistaboom.essence_ascendance.vitality.VitalityDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Final health local after armor, enchantments, resistance and absorption. Loader-specific local layout only. */
@Mixin(Player.class)
public abstract class VitalityPlayerDamageMixin {
    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;F)V"),
            require = 1, expect = 1, allow = 1)
    private void essenceAscendance$routeHealth(CombatTracker tracker, DamageSource source, float amount,
                                               Operation<Void> original, @Local(ordinal = 3) LocalFloatRef healthDamage) {
        if (!((Object)this instanceof ServerPlayer player)) { original.call(tracker, source, amount); return; }
        float routed = VitalityDamageService.route(player, source, amount);
        healthDamage.set(routed);
        var containers = ((NeoForgeDamageContainerAccess)(Object)this).essenceAscendance$damageContainers();
        if (!containers.empty() && containers.peek().getSource() == source) containers.peek().setNewDamage(routed);
        if (routed > 0) original.call(tracker, source, routed);
    }

    // There is no vanilla absorption-bypass damage tag in this version. Only the scoped debt payment
    // sees zero absorption; the native measurement and the owner's actual absorption remain untouched.
    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"), require = 1)
    private float essenceAscendance$alreadyAbsorbed(Player player, Operation<Float> original) {
        return player instanceof ServerPlayer server && DeferredDamageService.paying(server) ? 0 : original.call(player);
    }
    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setAbsorptionAmount(F)V"), require = 1)
    private void essenceAscendance$preserveAbsorption(Player player, float amount, Operation<Void> original) {
        if (!(player instanceof ServerPlayer server) || !DeferredDamageService.paying(server)) original.call(player, amount);
    }
}
