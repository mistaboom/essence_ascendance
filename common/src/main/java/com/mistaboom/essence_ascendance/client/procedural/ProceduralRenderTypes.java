package com.mistaboom.essence_ascendance.client.procedural;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderType;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderSetup;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.RenderPipelines;*/
//? if >=26.1.2
/*import net.minecraft.resources.Identifier;*/
//? if >=26.1.2
/*import com.mojang.blaze3d.pipeline.BlendFunction;*/
//? if >=26.1.2
/*import com.mojang.blaze3d.pipeline.ColorTargetState;*/
//? if >=26.1.2
/*import com.mojang.blaze3d.pipeline.DepthStencilState;*/
//? if >=26.1.2
/*import com.mojang.blaze3d.platform.CompareOp;*/
//? if >=26.1.2
/*import com.mojang.blaze3d.pipeline.RenderPipeline;*/
//? if <26.1.2
import net.minecraft.client.renderer.RenderType;

/** Shared world render passes. See-through passes are reserved for perception mechanics. */
public final class ProceduralRenderTypes {
    public enum Depth { WORLD, SEE_THROUGH }

    public static final RenderType WORLD_PLANES = planes("essence_ascendance_flight_planes", Depth.WORLD);
    public static final RenderType WORLD_LINES = lines("essence_ascendance_flight_edges", Depth.WORLD);
    /** Fine world structure tests and writes depth, after physical surfaces have been submitted. */
    public static final RenderType WORLD_DEPTH_LINES = lines("essence_ascendance_world_depth_lines", Depth.WORLD, true);
    public static final RenderType PERCEPTION_PLANES = planes("essence_ascendance_utility_sense_fills", Depth.SEE_THROUGH);
    public static final RenderType PERCEPTION_LINES = lines("essence_ascendance_utility_sense_lines", Depth.SEE_THROUGH);

    private ProceduralRenderTypes() { }

    //? if <26.1.2
    public static RenderType planes(String name, Depth depth) {
    //? if <26.1.2
        return new RenderType(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
    //? if <26.1.2
                RenderType.TRANSIENT_BUFFER_SIZE, false, true,
    //? if <26.1.2
                () -> {
    //? if <26.1.2
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    //? if <26.1.2
                    // These planes are translucent. Writing their depth before a
    //? if <26.1.2
                    // later block-entity batch makes the opaque machine disappear
    //? if <26.1.2
                    // behind an otherwise transparent field (an X-ray silhouette).
    //? if <26.1.2
                    begin(depth, false);
    //? if <26.1.2
                },
    //? if <26.1.2
                () -> end(depth)) { };
    //? if <26.1.2
    }
    //? if <26.1.2

    //? if <26.1.2
    public static RenderType lines(String name, Depth depth) {
    //? if <26.1.2
        return lines(name, depth, depth == Depth.WORLD);
    //? if <26.1.2
    }
    //? if <26.1.2

    //? if <26.1.2
    private static RenderType lines(String name, Depth depth, boolean writeDepth) {
    //? if <26.1.2
        return new RenderType(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
    //? if <26.1.2
                RenderType.TRANSIENT_BUFFER_SIZE, false, true,
    //? if <26.1.2
                () -> {
    //? if <26.1.2
                    RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
    //? if <26.1.2
                    begin(depth, writeDepth);
    //? if <26.1.2
                },
    //? if <26.1.2
                () -> end(depth)) { };
    //? if <26.1.2
    }
    //? if <26.1.2

    //? if <26.1.2
    private static void begin(Depth depth, boolean writeDepth) {
    //? if <26.1.2
        RenderSystem.enableBlend();
    //? if <26.1.2
        RenderSystem.defaultBlendFunc();
    //? if <26.1.2
        if (depth == Depth.WORLD) RenderSystem.enableDepthTest();
    //? if <26.1.2
        else RenderSystem.disableDepthTest();
    //? if <26.1.2
        RenderSystem.disableCull();
    //? if <26.1.2
        RenderSystem.depthMask(writeDepth);
    //? if <26.1.2
    }
    //? if <26.1.2

    //? if <26.1.2
    private static void end(Depth depth) {
    //? if <26.1.2
        RenderSystem.depthMask(true);
    //? if <26.1.2
        RenderSystem.enableCull();
    //? if <26.1.2
        if (depth == Depth.SEE_THROUGH) RenderSystem.enableDepthTest();
    //? if <26.1.2
        RenderSystem.disableBlend();
    //? if <26.1.2
    }

    //? if >=26.1.2
    /*public static RenderType planes(String name, Depth depth) { return modern(name, false, depth, false); }*/
    //? if >=26.1.2
    /*public static RenderType lines(String name, Depth depth) { return modern(name, true, depth, depth == Depth.WORLD); }*/
    //? if >=26.1.2
    /*private static RenderType lines(String name, Depth depth, boolean writeDepth) { return modern(name, true, depth, writeDepth); }*/
    //? if >=26.1.2
    /*private static RenderType modern(String name, boolean linePass, Depth depth, boolean writeDepth) { var snippet = linePass ? RenderPipelines.LINES_SNIPPET : RenderPipelines.DEBUG_FILLED_SNIPPET; var pipeline = RenderPipeline.builder(snippet).withLocation(Identifier.fromNamespaceAndPath("essence_ascendance", name)).withVertexFormat(linePass ? DefaultVertexFormat.POSITION_COLOR_NORMAL : DefaultVertexFormat.POSITION_COLOR, linePass ? VertexFormat.Mode.LINES : VertexFormat.Mode.QUADS).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withDepthStencilState(new DepthStencilState(depth == Depth.WORLD ? CompareOp.LESS_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS, writeDepth)).withCull(false).build(); return RenderType.create(name, RenderSetup.builder(pipeline).bufferSize(RenderType.TRANSIENT_BUFFER_SIZE).sortOnUpload().createRenderSetup()); }*/

}
