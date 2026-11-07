package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/** Current-state observation; native completion and persistent reconciliation policy belong to consumers. */
public final class EquipmentObservations {
    private static final Map<ServerPlayer, Observed> OBSERVED = new WeakHashMap<>();
    private static BiConsumer<ServerPlayer, EquipmentSnapshot> listener;
    private record Observed(EquipmentSnapshot equipment, boolean eligible) { }
    private EquipmentObservations() { }

    public static void register(BiConsumer<ServerPlayer, EquipmentSnapshot> consumer) {
        if (listener != null) throw new IllegalStateException("Equipment consumer already registered");
        listener = Objects.requireNonNull(consumer);
    }

    /** Fixed six-slot sample in the existing equipment reconciliation pass; dispatch only relevant changes. */
    public static void observe(ServerPlayer player) {
        if (listener == null || !player.server.isSameThread() || player.server.overworld() == null) return;
        var current = new Observed(EquipmentSnapshots.capture(player), AttunementGameplay.eligible(player));
        if (current.equals(OBSERVED.put(player, current))) return;
        deliver(player, current.equipment());
    }

    /** Explicit lifecycle reconciliation also publishes unchanged state for a consumer's persisted comparison. */
    public static void refresh(ServerPlayer player) {
        forget(player);
        observe(player);
    }

    private static void deliver(ServerPlayer player, EquipmentSnapshot equipment) {
        try { listener.accept(player, equipment); }
        catch (RuntimeException failure) {
            EssenceAscendance.LOGGER.error("Equipment observation delivery failed for {}", player.getUUID(), failure);
        }
    }

    public static void forget(ServerPlayer player) { OBSERVED.remove(player); }
    public static void clear() { OBSERVED.clear(); }
}
