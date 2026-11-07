package com.mistaboom.essence_ascendance.gameplay;

import net.minecraft.server.MinecraftServer;
import java.util.Objects;

/** Scalar native clock mutations; consumers decide whether a change invalidates their state. */
public final class WorldClockObservations {
    @FunctionalInterface public interface Observer { void changed(MinecraftServer server, long previous, long current); }
    private static Observer observer;
    private WorldClockObservations() { }
    public static void register(Observer consumer) {
        if (observer != null) throw new IllegalStateException("Clock observer already registered");
        observer = Objects.requireNonNull(consumer);
    }
    public static void changed(MinecraftServer server, long previous, long current) {
        if (!server.isSameThread() || observer == null || previous == current) return;
        try { observer.changed(server, previous, current); }
        catch (RuntimeException failure) {
            com.mistaboom.essence_ascendance.EssenceAscendance.LOGGER.error("Committed world clock observation failed", failure);
        }
    }
}
