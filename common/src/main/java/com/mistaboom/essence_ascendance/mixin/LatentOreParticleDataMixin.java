package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ore.LatentOreParticles;
import com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
abstract class LatentOreParticleDataMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void essence$rememberRemovedHost(CallbackInfo ci) {
        if ((Object) this instanceof LatentOreBlockEntity ore) LatentOreParticles.remember(ore);
    }
}
