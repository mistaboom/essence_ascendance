package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Direct commit dispatch, independent of Attunement award acceptance. */
public final class GatheringOutcomes {
    private static BiConsumer<MinecraftServer, GatheringOutcome> listener;
    private static BiConsumer<MinecraftServer, java.util.UUID> resetListener;
    private GatheringOutcomes() { }
    public static void register(BiConsumer<MinecraftServer, GatheringOutcome> consumer) {
        if (listener != null) throw new IllegalStateException("Gathering consumer already registered");
        listener = Objects.requireNonNull(consumer);
    }
    public static GatheringOutcome.Context context(ServerPlayer player, String kind) {
        return new GatheringOutcome.Context(AttunementGameplay.action(kind), player.getUUID(),
                player.level().dimension().location().toString(), player.server.overworld().getGameTime());
    }
    public static void registerReset(BiConsumer<MinecraftServer, java.util.UUID> consumer) {
        if (resetListener != null) throw new IllegalStateException("Gathering reset consumer already registered");
        resetListener = Objects.requireNonNull(consumer);
    }
    public static void reset(ServerPlayer player) { if (resetListener != null) resetListener.accept(player.server, player.getUUID()); }
    public static void publish(ServerPlayer player, GatheringOutcome fact) {
        if (!AttunementGameplay.eligible(player) || !fact.context().actor().equals(player.getUUID())) return;
        if (listener != null) try { listener.accept(player.server, fact); }
        catch (RuntimeException failure) { EssenceAscendance.LOGGER.error("Gathering outcome delivery failed", failure); }
    }
}
