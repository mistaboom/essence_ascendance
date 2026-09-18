package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.movement.MovementAbilityService;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;

public final class MovementAbilityInputService {
    private static boolean initialized;
    private MovementAbilityInputService() { }
    public static void init() {
        if (initialized) return;
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, MovementAbilityInputPayload.TYPE,
                MovementAbilityInputPayload.CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player)
                        MovementAbilityService.input(player, payload.input());
                }));
        initialized = true;
    }
}
