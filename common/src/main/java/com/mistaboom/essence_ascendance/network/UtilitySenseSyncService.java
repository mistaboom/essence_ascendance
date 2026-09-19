package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.utility.UtilitySenseService;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Change-only synchronization for the Utility sensing snapshot. */
public final class UtilitySenseSyncService {
    private static final Map<ServerPlayer, UtilitySenseService.Snapshot> LAST_SENT = new WeakHashMap<>();
    private static boolean initialized;

    private UtilitySenseSyncService() { }

    public static void init() {
        if (initialized) return;
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(UtilitySensePayload.TYPE, UtilitySensePayload.CODEC);
        }
        initialized = true;
    }

    public static void syncIfNeeded(ServerPlayer player) {
        if (!NetworkManager.canPlayerReceive(player, UtilitySensePayload.TYPE)) return;
        UtilitySenseService.Snapshot snapshot = UtilitySenseService.snapshot(player);
        if (snapshot.equals(LAST_SENT.get(player))) return;
        NetworkManager.sendToPlayer(player, new UtilitySensePayload(snapshot));
        LAST_SENT.put(player, snapshot);
    }

    public static void forget(ServerPlayer player) { LAST_SENT.remove(player); }
}
