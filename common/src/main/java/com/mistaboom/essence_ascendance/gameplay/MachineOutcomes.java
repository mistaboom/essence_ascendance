package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.server.MinecraftServer;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Synchronous operation observer; completion retrieval never replays a job. */
public final class MachineOutcomes {
    private static BiConsumer<MinecraftServer, MachineOutcome> listener;
    private MachineOutcomes() { }
    public static void register(BiConsumer<MinecraftServer, MachineOutcome> consumer) {
        if (listener != null) throw new IllegalStateException("Machine consumer already registered");
        listener = Objects.requireNonNull(consumer);
    }
    public static void publish(MinecraftServer server, MachineOutcome outcome) {
        Objects.requireNonNull(server); Objects.requireNonNull(outcome);
        if (!server.isSameThread()) throw new IllegalStateException("Machine outcomes require server thread");
        if (listener == null) return;
        try { listener.accept(server, outcome); }
        catch (RuntimeException failure) {
            EssenceAscendance.LOGGER.error("Committed machine outcome delivery failed: {}", outcome.actionId(), failure);
        }
    }
}
