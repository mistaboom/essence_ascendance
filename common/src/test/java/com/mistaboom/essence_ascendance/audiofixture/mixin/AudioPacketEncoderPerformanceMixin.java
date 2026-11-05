package com.mistaboom.essence_ascendance.audiofixture.mixin;

import com.mistaboom.essence_ascendance.audiofixture.AudioNativePerformanceAcceptance;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the real encoder's byte delta, without serializing any packet twice. Each encoder is channel-confined. */
@Mixin(PacketEncoder.class)
public abstract class AudioPacketEncoderPerformanceMixin {
    @Unique private int essenceAudioMeasureStart;
    @Inject(method="encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",at=@At("HEAD"))
    private void beforeEncode(ChannelHandlerContext context,Packet<?> packet,ByteBuf bytes,CallbackInfo ci) { essenceAudioMeasureStart=bytes.writerIndex(); }
    @Inject(method="encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",at=@At("RETURN"))
    private void afterEncode(ChannelHandlerContext context,Packet<?> packet,ByteBuf bytes,CallbackInfo ci) { AudioNativePerformanceAcceptance.Network.encoded(packet,bytes.writerIndex()-essenceAudioMeasureStart); }
}
