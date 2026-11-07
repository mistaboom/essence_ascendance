package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.server.MinecraftServer;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Direct committed-operation observation seam, independent of progression's consumers. */
public final class ProgressionOutcomes {
    private static BiConsumer<MinecraftServer, ProgressionOutcome> listener;
    private ProgressionOutcomes() { }

    public static void register(BiConsumer<MinecraftServer, ProgressionOutcome> consumer) {
        if (listener != null) throw new IllegalStateException("Progression consumer already registered");
        listener = Objects.requireNonNull(consumer);
    }

    public static void publish(MinecraftServer server, ProgressionOutcome outcome) {
        Objects.requireNonNull(server); Objects.requireNonNull(outcome);
        if (!server.isSameThread()) throw new IllegalStateException("Progression outcomes require the server thread");
        if (listener == null) return;
        try { listener.accept(server, outcome); }
        catch (RuntimeException failure) {
            EssenceAscendance.LOGGER.error("Committed progression outcome delivery failed: {}", outcome.actionId(), failure);
        }
    }
}
