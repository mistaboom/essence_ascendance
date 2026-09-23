package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.visual.transientfx.GuiVisualEvent;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Shared, bounded constructors for server-confirmed micro acknowledgements. */
public final class MicroVisualFeedback {
    private static final double WORLD_RADIUS = 32.0;
    private static final int GATHERING_TICKS = 14;
    private static final int UTILITY_TICKS = 16;

    private MicroVisualFeedback() { }

    public static void gathering(ServerLevel level, Vec3 position, long seed) {
        world(level, TransientVisualIds.WORLD_GATHERING_SPRITZ, position,
                SemanticVisualColor.GATHERING, 0.9F, 1.0F, GATHERING_TICKS, seed);
    }

    public static void utility(ServerLevel level, Vec3 position, long seed) {
        world(level, TransientVisualIds.WORLD_UTILITY_ACKNOWLEDGE, position,
                SemanticVisualColor.UTILITY, 0.85F, 1.0F, UTILITY_TICKS, seed);
    }

    public static void guiResult(ServerPlayer player, ResourceLocation recipe, SemanticVisualColor color, long seed) {
        gui(player, recipe, GuiVisualEvent.Anchor.RESULT_SLOT, -1, color, seed);
    }

    public static void guiSlot(ServerPlayer player, ResourceLocation recipe, int slot,
                               SemanticVisualColor color, long seed) {
        gui(player, recipe, GuiVisualEvent.Anchor.SLOT, slot, color, seed);
    }

    public static void world(ServerLevel level, ResourceLocation recipe, Vec3 position,
                              SemanticVisualColor color, float scale, float intensity,
                              int lifetimeTicks, long seed) {
        TransientVisualDispatch.nearby(level, new WorldVisualEvent(
                recipe, level.dimension().location(), position,
                WorldVisualEvent.NO_ENTITY, WorldVisualEvent.NO_ENTITY, null, new Vec3(0, 1, 0),
                scale, intensity, VisualIntensity.MICRO, color, lifetimeTicks, seed, 0, 0), WORLD_RADIUS);
    }

    private static void gui(ServerPlayer player, ResourceLocation recipe, GuiVisualEvent.Anchor anchor,
                            int slot, SemanticVisualColor color, long seed) {
        int lifetime = recipe.equals(TransientVisualIds.GUI_ENCHANTING_GLYPH) ? 20 : 16;
        TransientVisualDispatch.gui(player, new GuiVisualEvent(
                recipe, anchor, slot, 0, 0, 1.0F, 1.0F,
                VisualIntensity.MICRO, color, lifetime, seed, 0, 0));
    }
}
