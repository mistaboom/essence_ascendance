package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.audio.AudioRuntime;
import com.mistaboom.essence_ascendance.network.WorldVisualEventPayload;
import com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;

/** One accepted semantic envelope fans out before independent modality budgets and filtering. */
public final class FeedbackPresentation {
    private FeedbackPresentation() { }
    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,WorldVisualEventPayload.TYPE,WorldVisualEventPayload.CODEC,
                (payload,context)->ClientPacketDispatch.queueWorld(context,()->{
                    var mc=Minecraft.getInstance(); var event=payload.event();
                    if(mc.level==null || mc.player==null || !mc.level.dimension().location().equals(event.dimension())) return;
                    if(!com.mistaboom.essence_ascendance.audio.FeedbackTime.fresh(payload.originatingTick(),mc.level.getGameTime())) return;
                    if (!AscensionAnimation.defer(event, () -> present(payload, true))) present(payload, false);
                }));
    }

    private static void present(WorldVisualEventPayload payload, boolean acceptedDeferred) {
        var mc = Minecraft.getInstance(); var event = payload.event();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(event.dimension())) return;
        // Network freshness was checked before the bounded, world-scoped ceremony deferral.
        // Preserve occurrence identity, but start both modalities on this accepted release tick.
        AudioRuntime.world(event,payload.occurrence(),acceptedDeferred ? mc.level.getGameTime() : payload.originatingTick());
        if(payload.visualRadius()>0 && mc.player.distanceToSqr(event.position())<=payload.visualRadius()*payload.visualRadius()) TransientWorldVisuals.emitReady(event);
    }
}
