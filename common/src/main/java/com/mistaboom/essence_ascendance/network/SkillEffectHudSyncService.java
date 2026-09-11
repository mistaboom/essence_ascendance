package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudSnapshot;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Sends immediately on a combat-state transition and periodically for health/timer reconciliation. */
public final class SkillEffectHudSyncService {
    private static final int HEARTBEAT_TICKS = 5;
    private static final Map<ServerPlayer, LastSent> LAST_SENT = new WeakHashMap<>();
    private static boolean initialized;

    private SkillEffectHudSyncService() { }

    public static void init() {
        if (initialized) return;
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(SkillEffectHudPayload.TYPE, SkillEffectHudPayload.CODEC);
        }
        initialized = true;
    }

    public static void syncIfNeeded(ServerPlayer player) {
        if (!NetworkManager.canPlayerReceive(player, SkillEffectHudPayload.TYPE)) return;
        SkillEffectHudSnapshot snapshot = SkillEffectRuntime.hudSnapshot(player);
        LastSent previous = LAST_SENT.get(player);
        if (previous != null && previous.snapshot().sameState(snapshot)
                && snapshot.serverGameTime() - previous.sentAt() < HEARTBEAT_TICKS) return;
        NetworkManager.sendToPlayer(player, new SkillEffectHudPayload(snapshot));
        LAST_SENT.put(player, new LastSent(snapshot, snapshot.serverGameTime()));
    }

    public static void forget(ServerPlayer player) { LAST_SENT.remove(player); }

    private record LastSent(SkillEffectHudSnapshot snapshot, long sentAt) { }
}
