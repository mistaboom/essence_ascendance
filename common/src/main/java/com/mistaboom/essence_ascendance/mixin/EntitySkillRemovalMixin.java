package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySkillRemovalMixin {
    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), require = 1)
    private void essenceAscendance$rodCoordinateTeleport(double x,double y,double z,CallbackInfo ci) {
        com.mistaboom.essence_ascendance.gameplay.RodOutcomes.teleported((Entity)(Object)this);
    }
    //? if >=26.1.2
    /*@Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"), require = 1)*/
    //? if <26.1.2
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), require = 1)
    private void essenceAscendance$rodCommandTeleport(CallbackInfoReturnable<Boolean> cir) {
        com.mistaboom.essence_ascendance.gameplay.RodOutcomes.teleported((Entity)(Object)this);
    }
    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void essenceAscendance$releaseSkillTarget(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        com.mistaboom.essence_ascendance.gameplay.CompanionOutcomes.removed(entity, reason);
        if (!entity.level().isClientSide) SkillEffectRuntime.onEntityRemoved(entity);
    }
}
