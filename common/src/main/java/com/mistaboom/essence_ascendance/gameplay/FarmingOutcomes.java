package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Synchronous committed observations, independent of Attunement acceptance and achievement text. */
public final class FarmingOutcomes {
    private static BiConsumer<MinecraftServer, FarmingOutcome> listener;
    private FarmingOutcomes() { }
    public static void register(BiConsumer<MinecraftServer, FarmingOutcome> consumer) {
        if (listener != null) throw new IllegalStateException("Farming consumer already registered");
        listener = Objects.requireNonNull(consumer);
    }
    public static FarmingOutcome.Context context(ServerPlayer player, String kind) {
        return new FarmingOutcome.Context(AttunementGameplay.action(kind), player.getUUID(),
                player.level().dimension().location().toString(), player.server.overworld().getGameTime());
    }
    public static void publish(ServerPlayer player, FarmingOutcome fact) {
        if (player == null || fact == null || !player.server.isSameThread() || !AttunementGameplay.eligible(player)
                || !fact.context().actor().equals(player.getUUID())) return;
        if (listener != null) try { listener.accept(player.server, fact); }
        catch (RuntimeException failure) { EssenceAscendance.LOGGER.error("Farming outcome delivery failed", failure); }
    }
}
