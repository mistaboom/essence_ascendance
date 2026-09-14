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
        Vec3 postureBefore = entity.position();
        original.call(type, requested);
        com.mistaboom.essence_ascendance.posture.PostureService.externalMove(entity, type, postureBefore);
        StaggerController.moved(entity);
        GuardMobilityController.completedMove(entity, initial);
    }
    @Inject(method = "baseTick", at = @At("HEAD"), require = 1, expect = 1)
    private void essenceAscendance$staggerExpiry(CallbackInfo ci) { StaggerController.tick((Entity) (Object) this); }
    @Inject(method = "remove", at = @At("HEAD"), require = 1, expect = 1)
    private void essenceAscendance$staggerRemoved(CallbackInfo ci) { StaggerController.remove((Entity) (Object) this); }
    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), require = 1, expect = 1)
    private void essenceAscendance$postureVelocity(Vec3 velocity, CallbackInfo ci) {
        com.mistaboom.essence_ascendance.posture.PostureService.velocity((Entity)(Object)this,velocity);
    }
    @Inject(method = "push(DDD)V", at = @At("HEAD"), require = 1, expect = 1)
    private void essenceAscendance$posturePush(double x, double y, double z, CallbackInfo ci) {
        if ((Object)this instanceof net.minecraft.server.level.ServerPlayer player && (x != 0 || y != 0 || z != 0))
            com.mistaboom.essence_ascendance.posture.PostureService.forced(player,"native_entity_push");
    }
}
