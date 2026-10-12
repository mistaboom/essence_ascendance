package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public abstract class FishingHookTrackingMixin {
    @ModifyExpressionValue(method="addEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityType;clientTrackingRange()I"))
    private int essenceAscendance$rodTracking(int nativeChunks, Entity entity) {
        if(entity instanceof RodHookAccess hook && hook.essenceAscendance$rodHand()!=0) {
            double range=hook.essenceAscendance$rodState().castContext.baseline().range();
            return Math.max(nativeChunks,(int)Math.ceil(range/16)+1);
        }
        return nativeChunks;
    }
}
