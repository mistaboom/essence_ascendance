package com.mistaboom.essence_ascendance.client;

import dev.architectury.networking.NetworkManager;
import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

/** Keeps a queued presentation update tied to the connection that received it. */
public final class ClientPacketDispatch {
    private static ClientPacketListener activeConnection;
    private static volatile long sessionEpoch;
    private static boolean initialized;

    public static void init() {
        if (!initialized) {
            ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
                sessionEpoch++;
                activeConnection = null;
                clearPresentation();
            });
            initialized = true;
        }
    }

    private static void clearPresentation() {
        RuntimeBalanceClientState.clear();
        ClientEssenceState.clear();
        AscendanceNexusTransactionClientState.clear();
        AscensionAnimation.clear();
        EquipmentTooltipClientState.clear();
        ItemEssenceTooltipClientState.clear();
        EssenceCrucibleClientState.clear();
        EssencePylonClientState.clear();
        SkillEffectHudClientState.clear();
    }

    private ClientPacketDispatch() {
    }

    public static void queue(NetworkManager.PacketContext context, Runnable update) {
        // The context retains the receiving player; do not accidentally capture
        // a new server's global connection for an old callback. Respawned players
        // keep the same listener, so a respawn is not a new mapping session.
        ClientPacketListener connection = context.getPlayer() instanceof LocalPlayer player
                ? player.connection : null;
        long epoch = sessionEpoch;
        context.queue(() -> {
            if (epoch == sessionEpoch
                    && connection != null
                    && Minecraft.getInstance().getConnection() == connection
                    && connection.getConnection().isConnected()) {
                if (activeConnection != connection) {
                    // Also covers a failed/early disconnect without a player-quit event.
                    clearPresentation();
                    activeConnection = connection;
                }
                update.run();
            }
        });
    }
}
