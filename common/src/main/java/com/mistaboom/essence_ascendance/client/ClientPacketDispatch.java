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
    private static volatile long presentationEpoch;
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
        presentationEpoch++;
        com.mistaboom.essence_ascendance.client.nexus.NexusNavigationState.clearSession();
        com.mistaboom.essence_ascendance.client.archive.ArchiveNavigationState.clearSession();
        RuntimeBalanceClientState.clear();
        ClientEssenceState.clear();
        AscendanceNexusTransactionClientState.clear();
        AscensionAnimation.clear();
        EquipmentTooltipClientState.clear();
        ItemEssenceTooltipClientState.clear();
        EssenceCrucibleClientState.clear();
        EssencePylonClientState.clear();
        SkillEffectHudClientState.clear();
        GatheringSurveyClientState.clear();
        UtilitySenseClientState.clear();
        FlightVisualClientState.clear();
        MachineWorldVisualRenderer.beginFrame();
        com.mistaboom.essence_ascendance.client.procedural.ProceduralWorldQueue.clear();
        TraversalFluidRenderState.clear();
        com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals.clear();
        com.mistaboom.essence_ascendance.client.transientfx.TransientGuiVisuals.clear();
    }

    /** Stable cache boundary for connection-scoped presentation models. */
    public static long presentationEpoch() { return presentationEpoch; }

    /** Rebuild derived localized/resource content without clearing navigation or connection state. */
    public static void resourcesReloaded() { presentationEpoch++; }

    private ClientPacketDispatch() {
    }

    /**
     * Screens can open before the first mod snapshot arrives. Establish the same
     * connection boundary here so they never restore a previous server's state.
     * Called on the client thread, as are queued packet updates and screen init.
     */
    public static void preparePresentationConnection() {
        preparePresentationConnection(Minecraft.getInstance().getConnection());
    }

    private static void preparePresentationConnection(ClientPacketListener connection) {
        if (activeConnection != connection) {
            // Also covers a failed/early disconnect without a player-quit event.
            clearPresentation();
            activeConnection = connection;
        }
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
                preparePresentationConnection(connection);
                update.run();
            }
        });
    }
}
