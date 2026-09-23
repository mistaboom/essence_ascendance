package com.mistaboom.essence_ascendance.client.transientfx;

/** A GUI composition over GuiProceduralGeometry. */
public interface GuiVisualRecipe {
    void renderPrimary(GuiVisualRenderContext context);
    default void renderDetail(GuiVisualRenderContext context) { }
}
