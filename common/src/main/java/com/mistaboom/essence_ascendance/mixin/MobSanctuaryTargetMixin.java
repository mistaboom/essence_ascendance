package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.utility.UtilitySanctuaryService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla target goals from instantly undoing a completed Sanctuary disengagement. */
@Mixin(Mob.class)
public abstract class MobSanctuaryTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$keepSanctuaryDisengaged(LivingEntity target, CallbackInfo ci) {
        if (target != null && UtilitySanctuaryService.blocksTargeting((Mob) (Object) this, target)) ci.cancel();
    }
}
