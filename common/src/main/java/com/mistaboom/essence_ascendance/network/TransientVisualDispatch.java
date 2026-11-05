package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import com.mistaboom.essence_ascendance.visual.transientfx.GuiVisualEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-side one-shot dispatch for client-constructed procedural visuals. */
public final class TransientVisualDispatch {
    private static boolean initialized;

    private TransientVisualDispatch() { }

    public static void init() {
        if (initialized) return;
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(WorldVisualEventPayload.TYPE, WorldVisualEventPayload.CODEC);
            NetworkManager.registerS2CPayloadType(GuiVisualEventPayload.TYPE, GuiVisualEventPayload.CODEC);
        }
        initialized = true;
    }

    /** Sends one event to capable players in the same dimension and presentation radius. */
    public static void nearby(ServerLevel level, WorldVisualEvent event, double radius) {
        if (!level.dimension().location().equals(event.dimension()))
            throw new IllegalArgumentException("Visual event dimension does not match its dispatch level");
        var cue = com.mistaboom.essence_ascendance.audio.AudioCatalog.get().recipe(event.recipeId().toString(),event.parameterA(),event.parameterB());
        double audioRadius = cue != null && cue.audience().scope() == com.mistaboom.essence_ascendance.audio.AudioCatalog.Scope.WORLD ? cue.audience().radius() : 0;
        var payload = new WorldVisualEventPayload(event, FeedbackDispatch.nextOccurrence(), level.getGameTime(), radius);
        FeedbackDispatch.nearby(level, event.position(), Math.max(radius, audioRadius), player -> FeedbackDispatch.send(player, payload));
    }

    /** Sends a confirmed screen-space acknowledgement only to the acting player. */
    public static void gui(ServerPlayer player, GuiVisualEvent event) {
        FeedbackDispatch.send(player, new GuiVisualEventPayload(event, player.containerMenu.containerId));
    }
}
