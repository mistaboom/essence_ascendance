package com.mistaboom.essence_ascendance.audiofixture.mixin;

import com.mistaboom.essence_ascendance.audiofixture.AudioNativePerformanceAcceptance;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Common-side socket observer; references only the client-free nested network collector. */
@Mixin(Connection.class)
public abstract class AudioNetworkPerformanceMixin {
    @Inject(method="channelActive",at=@At("RETURN"))
    private void observeConnection(ChannelHandlerContext context,CallbackInfo ci) { AudioNativePerformanceAcceptance.Network.install(context); }
}
