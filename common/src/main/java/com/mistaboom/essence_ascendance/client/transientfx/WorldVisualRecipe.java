package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** A local composition over the existing shared procedural primitives. */
public interface WorldVisualRecipe {
    /** Optional entity attachment affects both distance LOD and lifetime, not just vertex placement. */
    default Vec3 anchor(WorldVisualEvent event, ClientLevel level) { return event.position(); }
    default boolean alive(WorldVisualEvent event, ClientLevel level) { return true; }
    /** Optional coalescing for repeated entity-attached cues, including multiple nearby emitters. */
    default int repeatIntervalTicks() { return 0; }
    default void renderPrimaryPlanes(WorldVisualRenderContext context) { }
    default void renderPrimaryLines(WorldVisualRenderContext context) { }

    default void renderDetailPlanes(WorldVisualRenderContext context) { }
    default void renderDetailLines(WorldVisualRenderContext context) { }
}
