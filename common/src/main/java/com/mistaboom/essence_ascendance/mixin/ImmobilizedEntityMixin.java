package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.skill.effect.ImmobilizationController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ImmobilizedEntityMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 essenceAscendance$rootTranslation(Vec3 requested) {
        return ImmobilizationController.movement((Entity) (Object) this, requested);
    }
    @Inject(method = "move", at = @At("RETURN"))
    private void essenceAscendance$rootSettled(CallbackInfo ci) { ImmobilizationController.moved((Entity) (Object) this); }
    @Inject(method = "baseTick", at = @At("HEAD"))
    private void essenceAscendance$rootExpiry(CallbackInfo ci) { ImmobilizationController.tick((Entity) (Object) this); }
    @Inject(method = "remove", at = @At("HEAD"))
    private void essenceAscendance$rootRemoved(CallbackInfo ci) { ImmobilizationController.remove((Entity) (Object) this); }
}
