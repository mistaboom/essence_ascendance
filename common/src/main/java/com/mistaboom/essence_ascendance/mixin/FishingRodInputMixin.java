package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import com.mistaboom.essence_ascendance.network.RodHeldInputPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class FishingRodInputMixin {
    @Unique private int essenceAscendance$lastRodHook = -1;
    @Unique private boolean essenceAscendance$lastRodHeld;
    @Unique private long essenceAscendance$lastRodInput;
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void essenceAscendance$rodHeartbeat(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        if (!(player.fishing instanceof RodHookAccess access) || access.essenceAscendance$rodHand() == 0
                || !NetworkManager.canServerReceive(RodHeldInputPayload.TYPE)) return;
        Minecraft client = Minecraft.getInstance();
        boolean held = client.screen == null && client.isWindowActive() && client.options.keyUse.isDown();
        long now = player.level().getGameTime();
        if (player.fishing.getId() != essenceAscendance$lastRodHook || held != essenceAscendance$lastRodHeld
                || now < essenceAscendance$lastRodInput || now - essenceAscendance$lastRodInput >= 5) {
            double viewScale = Math.clamp(client.options.getEffectiveRenderDistance() / 8.0, 1, 2.5)
                    * client.options.entityDistanceScaling().get();
            NetworkManager.sendToServer(new RodHeldInputPayload(player.fishing.getId(), held, Math.clamp(viewScale, .25, 12.5)));
            essenceAscendance$lastRodHook = player.fishing.getId(); essenceAscendance$lastRodHeld = held; essenceAscendance$lastRodInput = now;
        }
    }
}
