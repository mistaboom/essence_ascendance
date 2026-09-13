package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.network.AttunementMovementIntentPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends vanilla keyboard movement intent before its corresponding positional packet; no new keybind. */
@Mixin(LocalPlayer.class)
public abstract class AttunementMovementInputMixin {
    @Unique private boolean essenceAscendance$lastIntent;
    @Unique private int essenceAscendance$lastIntentTick = Integer.MIN_VALUE;
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void essenceAscendance$movementIntent(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!NetworkManager.canServerReceive(AttunementMovementIntentPayload.TYPE)) return;
        boolean active = player.input.forwardImpulse != 0 || player.input.leftImpulse != 0 || player.input.jumping;
        if (active != essenceAscendance$lastIntent || active && (long) player.tickCount - essenceAscendance$lastIntentTick >= 5) {
            NetworkManager.sendToServer(new AttunementMovementIntentPayload(active));
            essenceAscendance$lastIntent = active;
            essenceAscendance$lastIntentTick = player.tickCount;
        }
    }
}
