package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleChannelPayload;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleStateRequestPayload;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleVentPayload;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleStatePayload;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.resources.ResourceLocation;

public final class EssenceCrucibleClientState {

    private static volatile EssenceCrucibleStatePayload snapshot;
    private static boolean initialized = false;

    private EssenceCrucibleClientState() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                EssenceCrucibleStatePayload.TYPE,
                EssenceCrucibleStatePayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> {
                                    if (payload.schemaVersion()
                                            == EssenceCrucibleStatePayload.CURRENT_SCHEMA_VERSION) {
                                        snapshot = payload;
                                    } else {
                                        EssenceAscendance.LOGGER.error(
                                                "Ignoring incompatible Essence Crucible state payload schema {} (client expects {})",
                                                payload.schemaVersion(),
                                                EssenceCrucibleStatePayload.CURRENT_SCHEMA_VERSION
                                        );
                                    }
                                }
                        )
        );

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(
                player -> clear()
        );

        initialized = true;
    }

    public static EssenceCrucibleStatePayload snapshotFor(int menuId) {
        EssenceCrucibleStatePayload current = snapshot;
        return current != null && current.menuId() == menuId
                ? current
                : null;
    }

    public static void requestChannel(
            int menuId,
            boolean active
    ) {
        NetworkManager.sendToServer(
                new EssenceCrucibleChannelPayload(
                        menuId,
                        active
                )
        );
    }

    public static void requestState(
            int menuId
    ) {
        NetworkManager.sendToServer(
                new EssenceCrucibleStateRequestPayload(
                        menuId
                )
        );
    }

    public static void requestVent(
            int menuId,
            ResourceLocation essenceId
    ) {
        NetworkManager.sendToServer(
                new EssenceCrucibleVentPayload(
                        menuId,
                        essenceId.toString()
                )
        );
    }

    public static void clear() {
        snapshot = null;
    }
}
