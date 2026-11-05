package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.audio.AudioRuntime;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Initial chunk state and instance removal are independent of block-entity rendering. Client list only. */
@Mixin(BlockEntity.class)
abstract class AudioBlockSourceMixin {
    @Inject(method={"setLevel","setRemoved"},at=@At("TAIL"))
    private void essenceAscendance$sourceChanged(CallbackInfo ci) { AudioRuntime.machineChanged((BlockEntity)(Object)this); }
}
