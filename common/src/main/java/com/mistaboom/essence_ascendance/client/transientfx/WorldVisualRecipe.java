package com.mistaboom.essence_ascendance.client.transientfx;

/** A local composition over the existing shared procedural primitives. */
public interface WorldVisualRecipe {
    default void renderPrimaryPlanes(WorldVisualRenderContext context) { }
    default void renderPrimaryLines(WorldVisualRenderContext context) { }

    default void renderDetailPlanes(WorldVisualRenderContext context) { }
    default void renderDetailLines(WorldVisualRenderContext context) { }
}
