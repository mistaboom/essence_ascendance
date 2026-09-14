package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;
import com.mistaboom.essence_ascendance.skill.effect.StaggerController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class GuardMovementEntityMixin {
    @WrapMethod(method = "move")
    private void essenceAscendance$guardedContact(MoverType type, Vec3 requested, Operation<Void> original) {
        Entity entity = (Entity) (Object) this;
        StaggerController.tick(entity);
        var initial = GuardMobilityController.beginMove(entity, type);
        original.call(type, requested);
        StaggerController.moved(entity);
        GuardMobilityController.completedMove(entity, initial);
    }
    @Inject(method = "baseTick", at = @At("HEAD"), require = 1, expect = 1)
    private void essenceAscendance$staggerExpiry(CallbackInfo ci) { StaggerController.tick((Entity) (Object) this); }
    @Inject(method = "remove", at = @At("HEAD"), require = 1, expect = 1)
    private void essenceAscendance$staggerRemoved(CallbackInfo ci) { StaggerController.remove((Entity) (Object) this); }
}
