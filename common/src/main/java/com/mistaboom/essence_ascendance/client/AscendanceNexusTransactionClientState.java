package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.network.AscendanceNexusTransactionResultPayload;
import dev.architectury.networking.NetworkManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Connection-scoped mailbox for explicit Nexus transaction acknowledgements. */
public final class AscendanceNexusTransactionClientState {
    private static final int MAX_BUFFERED_RESULTS = 32;
    private static final Map<RequestKey, Result> RESULTS = new LinkedHashMap<>();
    private static boolean initialized;

    private AscendanceNexusTransactionClientState() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                AscendanceNexusTransactionResultPayload.TYPE,
                AscendanceNexusTransactionResultPayload.CODEC,
                (payload, context) ->
                        ClientPacketDispatch.queue(
                                context,
                                () -> accept(payload)
                        )
        );
        initialized = true;
        EssenceAscendance.LOGGER.info(
                "Registered Ascendance Nexus transaction-result S2C receiver"
        );
    }

    private static synchronized void accept(
            AscendanceNexusTransactionResultPayload payload
    ) {
        RequestKey key = new RequestKey(payload.menuId(), payload.requestId());
        RESULTS.put(key, new Result(
                payload.menuId(),
                payload.requestId(),
                payload.nexusRevision(),
                payload.status(),
                payload.ascended()
        ));
        while (RESULTS.size() > MAX_BUFFERED_RESULTS) {
            RequestKey oldest = RESULTS.keySet().iterator().next();
            RESULTS.remove(oldest);
        }
    }

    public static synchronized Optional<Result> consume(
            int menuId,
            long requestId
    ) {
        return Optional.ofNullable(RESULTS.remove(new RequestKey(menuId, requestId)));
    }

    public static synchronized void clear() {
        RESULTS.clear();
    }

    private record RequestKey(int menuId, long requestId) {
    }

    public record Result(
            int menuId,
            long requestId,
            long nexusRevision,
            AscendanceNexusTransactionResultPayload.Status status,
            boolean ascended
    ) {
        public boolean accepted() {
            return status == AscendanceNexusTransactionResultPayload.Status.SUCCESS;
        }
    }
}
