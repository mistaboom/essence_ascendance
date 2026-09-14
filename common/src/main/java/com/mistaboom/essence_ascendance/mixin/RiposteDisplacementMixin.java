package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.skill.effect.GuardCounterattackService;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explosion/entity impulses during the synchronous strike, never arbitrary velocity or movement writes. */
@Mixin(Entity.class)
public abstract class RiposteDisplacementMixin {
    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void essenceAscendance$counterattackImpulse(double x, double y, double z, CallbackInfo ci) {
        if (GuardCounterattackService.suppressDisplacement((Entity) (Object) this)) ci.cancel();
    }
}
