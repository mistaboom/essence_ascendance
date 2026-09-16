package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.skill.effect.VitalitySustenanceEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers the phantom's initial scan and continued-target check; other mobs and players remain ordinary. */
@Mixin(TargetingConditions.class)
public abstract class SustenancePhantomTargetMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$ignoreRestedOwner(LivingEntity attacker, LivingEntity target,
                                                     CallbackInfoReturnable<Boolean> result) {
        if (attacker instanceof Phantom && target instanceof ServerPlayer player
                && VitalitySustenanceEffects.fullySustained(player)) result.setReturnValue(false);
    }
}
