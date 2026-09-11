package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntitySkillRemovalMixin {
    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void essenceAscendance$releaseSkillTarget(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!entity.level().isClientSide) SkillEffectRuntime.onEntityRemoved(entity);
    }
}
