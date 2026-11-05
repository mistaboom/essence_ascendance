package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.audio.AudioDevelopment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One completion repair after each received tree, without polling or changing server execution. */
@Mixin(ClientPacketListener.class)
abstract class AudioCommandCompletionMixin {
    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void essenceAscendance$completeAudioCommands(ClientboundCommandsPacket packet, CallbackInfo callback) {
        AudioDevelopment.installCompletions(((ClientPacketListener) (Object) this).getCommands());
    }
}
