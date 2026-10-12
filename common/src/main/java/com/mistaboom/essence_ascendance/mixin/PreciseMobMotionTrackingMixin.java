package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.gathering.PreciseMobMotion;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.Packet;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public abstract class PreciseMobMotionTrackingMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private Consumer<Packet<?>> broadcast;
    @Inject(method = "sendChanges", at = @At("RETURN"))
    private void essenceAscendance$preciseMobMotion(CallbackInfo callback) {
        PreciseMobMotion.synchronize(entity, broadcast);
    }
}
