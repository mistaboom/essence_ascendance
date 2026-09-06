package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.network.EssencePylonStatePayload;
import com.mistaboom.essence_ascendance.network.EssencePylonStateRequestPayload;
import dev.architectury.networking.NetworkManager;

public final class EssencePylonClientState {

    private static volatile EssencePylonStatePayload snapshot;
    private static boolean initialized = false;

    private EssencePylonClientState() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                EssencePylonStatePayload.TYPE,
                EssencePylonStatePayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> {
                    if (payload.schemaVersion()
                            == EssencePylonStatePayload.CURRENT_SCHEMA_VERSION) {
                        snapshot = payload;
                    } else {
                        EssenceAscendance.LOGGER.error(
                                "Ignoring incompatible Essence Pylon state payload schema {} (client expects {})",
                                payload.schemaVersion(),
                                EssencePylonStatePayload.CURRENT_SCHEMA_VERSION
                        );
                    }
                })
        );

        initialized = true;
    }

    public static EssencePylonStatePayload snapshotFor(int menuId) {
        EssencePylonStatePayload current = snapshot;
        return current != null && current.menuId() == menuId ? current : null;
    }

    public static void requestState(int menuId) {
        NetworkManager.sendToServer(new EssencePylonStateRequestPayload(menuId));
    }

    public static void clear() {
        snapshot = null;
    }
}
