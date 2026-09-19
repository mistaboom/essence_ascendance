package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.gathering.GatheringSurveyService;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Change-only survey synchronization; an empty snapshot explicitly clears stale client highlights. */
public final class GatheringSurveySyncService {
    private static final Map<ServerPlayer, GatheringSurveyService.Snapshot> LAST_SENT = new WeakHashMap<>();
    private static boolean initialized;

    private GatheringSurveySyncService() { }

    public static void init() {
        if (initialized) return;
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(GatheringSurveyPayload.TYPE, GatheringSurveyPayload.CODEC);
        }
        initialized = true;
    }

    public static void syncIfNeeded(ServerPlayer player) {
        if (!NetworkManager.canPlayerReceive(player, GatheringSurveyPayload.TYPE)) return;
        GatheringSurveyService.Snapshot snapshot = GatheringSurveyService.snapshot(player);
        if (snapshot.equals(LAST_SENT.get(player))) return;
        NetworkManager.sendToPlayer(player, new GatheringSurveyPayload(snapshot));
        LAST_SENT.put(player, snapshot);
    }

    public static void forget(ServerPlayer player) { LAST_SENT.remove(player); }
}
