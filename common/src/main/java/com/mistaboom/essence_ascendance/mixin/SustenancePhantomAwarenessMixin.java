package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.skill.effect.VitalitySustenanceEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A phantom already swooping does not re-run targeting conditions; clear its protected target before AI work. */
@Mixin(Phantom.class)
public abstract class SustenancePhantomAwarenessMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void essenceAscendance$releaseSustainedTarget(CallbackInfo callback) {
        Phantom phantom = (Phantom) (Object) this;
        if (phantom.getTarget() instanceof ServerPlayer player && VitalitySustenanceEffects.fullySustained(player))
            phantom.setTarget(null);
    }
}
