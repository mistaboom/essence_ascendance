package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;

public final class AttunementMovementIntentService {
    private static boolean initialized;
    private AttunementMovementIntentService() { }
    public static void init() {
        if (initialized) return;
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, AttunementMovementIntentPayload.TYPE,
                AttunementMovementIntentPayload.CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player)
                        AttunementGameplay.setMovementIntent(player, payload.active());
                }));
        initialized = true;
    }
}
