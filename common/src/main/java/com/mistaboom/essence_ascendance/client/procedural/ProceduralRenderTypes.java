package com.mistaboom.essence_ascendance.client.procedural;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;

/** Shared world render passes. See-through passes are reserved for perception mechanics. */
public final class ProceduralRenderTypes {
    public enum Depth { WORLD, SEE_THROUGH }

    public static final RenderType WORLD_PLANES = planes("essence_ascendance_flight_planes", Depth.WORLD);
    public static final RenderType WORLD_LINES = lines("essence_ascendance_flight_edges", Depth.WORLD);
    public static final RenderType PERCEPTION_PLANES = planes("essence_ascendance_utility_sense_fills", Depth.SEE_THROUGH);
    public static final RenderType PERCEPTION_LINES = lines("essence_ascendance_utility_sense_lines", Depth.SEE_THROUGH);

    private ProceduralRenderTypes() { }

    public static RenderType planes(String name, Depth depth) {
        return new RenderType(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                RenderType.TRANSIENT_BUFFER_SIZE, false, true,
                () -> {
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    begin(depth, depth == Depth.WORLD);
                },
                () -> end(depth)) { };
    }

    public static RenderType lines(String name, Depth depth) {
        return new RenderType(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                RenderType.TRANSIENT_BUFFER_SIZE, false, true,
                () -> {
                    RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                    begin(depth, false);
                },
                () -> end(depth)) { };
    }

    private static void begin(Depth depth, boolean writeDepth) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (depth == Depth.WORLD) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(writeDepth);
    }

    private static void end(Depth depth) {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        if (depth == Depth.SEE_THROUGH) RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
