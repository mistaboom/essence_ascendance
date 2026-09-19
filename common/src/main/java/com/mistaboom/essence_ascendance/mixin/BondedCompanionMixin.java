package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.utility.BondedCompanionService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loaded owned creatures reconcile their transient Bonded Companion state on their native tick. */
@Mixin(LivingEntity.class)
public abstract class BondedCompanionMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void essenceAscendance$bondedCompanion(CallbackInfo ci) {
        BondedCompanionService.tick((LivingEntity)(Object)this);
    }
}
