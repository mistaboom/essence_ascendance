package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.gathering.GatheringSurveyService;
import com.mistaboom.essence_ascendance.network.GatheringSurveyPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/** Connection-scoped cache for the server-authoritative Gathering survey overlay. */
public final class GatheringSurveyClientState {
    private static GatheringSurveyService.Snapshot snapshot;
    private static LocalPlayer receiptPlayer;
    private static ClientLevel receiptLevel;
    private static boolean initialized;

    private GatheringSurveyClientState() { }

    public static void init() {
        if (initialized) return;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, GatheringSurveyPayload.TYPE, GatheringSurveyPayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> accept(payload.snapshot())));
        initialized = true;
    }

    public static void clear() {
        snapshot = null;
        receiptPlayer = null;
        receiptLevel = null;
    }

    public static GatheringSurveyService.Mode mode() {
        return valid() ? snapshot.mode() : GatheringSurveyService.Mode.NONE;
    }

    public static List<GatheringSurveyService.Target> targets() {
        return valid() ? snapshot.targets() : List.of();
    }

    private static void accept(GatheringSurveyService.Snapshot next) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !minecraft.level.dimension().location().equals(next.dimension())) {
            clear();
            return;
        }
        snapshot = next;
        receiptPlayer = minecraft.player;
        receiptLevel = minecraft.level;
    }

    private static boolean valid() {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.player != receiptPlayer || minecraft.level != receiptLevel
                || minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()
                || !minecraft.level.dimension().location().equals(snapshot.dimension())) {
            clear();
            return false;
        }
        return true;
    }
}
