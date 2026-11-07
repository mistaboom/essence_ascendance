package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.function.BiConsumer;

/** One direct, server-thread consumer seam; no mutable items, history, or global event bus. */
public final class DissolutionOutcomes {
    private static BiConsumer<MinecraftServer, DissolutionOutcome> listener;

    private DissolutionOutcomes() { }

    public static void register(BiConsumer<MinecraftServer, DissolutionOutcome> consumer) {
        if (listener != null) throw new IllegalStateException("Dissolution consumer already registered");
        listener = Objects.requireNonNull(consumer);
    }

    /** Called only after the reservoir and input mutation have committed successfully. */
    public static void publish(MinecraftServer server, DissolutionOutcome outcome) {
        Objects.requireNonNull(server);
        Objects.requireNonNull(outcome);
        if (!server.isSameThread()) throw new IllegalStateException("Dissolution outcomes require the server thread");
        if (listener == null) return;
        try {
            listener.accept(server, outcome);
        } catch (RuntimeException failure) {
            // An observer failure cannot undo already committed inventory or currency.
            EssenceAscendance.LOGGER.error("Committed dissolution outcome delivery failed: {}", outcome.actionId(), failure);
        }
    }
}
