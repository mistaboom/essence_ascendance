package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
//? if >=26.1.2
/*import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;*/
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
//? if >=26.1.2
/*import com.mojang.blaze3d.platform.CompareOp;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.RenderPipelines;*/
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.nio.ByteOrder;

/** Exercises production line emission against the native vertex builder, without an OpenGL window. */
public final class ProceduralLineRenderTest {
    private static int checks;

    public static void main(String[] args) {
        //? if >=26.1.2
        /*VertexFormat format = RenderPipelines.LINES.getVertexFormat();*/
        //? if <26.1.2
        VertexFormat format = DefaultVertexFormat.POSITION_COLOR_NORMAL;

        //? if >=26.1.2
        /*modernPipelineContract(format);*/

        PoseStack pose = new PoseStack();
        pose.mulPose(new Quaternionf().rotationXYZ(0.2F, -0.6F, 0.1F));
        pose.translate(0.25, 0.75, -2.0);
        try (ByteBufferBuilder storage = new ByteBufferBuilder(4096)) {
            BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.LINES, format);
            // Every sense outline, wing edge and machine outline uses this same emitter.
            ProceduralGeometry.line(pose, builder, Vec3.ZERO, new Vec3(1, 0, 0), 0x57CCBA, 0.75F);
            ProceduralGeometry.arc(pose, builder, new Vec3(0, 0, -4),
                    new Vec3(1, 0, 0), new Vec3(0, 1, 0), 1, 1, 0, Math.PI * 2, 12, 0xB4D9FB, 1);
            try (var mesh = builder.buildOrThrow()) {
                check(mesh.drawState().vertexCount() == 52, "Thirteen line segments produce complete native line quads");
                check(mesh.drawState().indexCount() == 78, "Native line topology remains drawable");
                var bytes = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                var floats = bytes.asFloatBuffer();
                for (int i = 0; i < mesh.drawState().vertexCount(); i++) {
                    int base = i * format.getVertexSize();
                    int position = base + format.getOffset(VertexFormatElement.POSITION);
                    check(Float.isFinite(floats.get(position / Float.BYTES))
                                    && Float.isFinite(floats.get(position / Float.BYTES + 1))
                                    && Float.isFinite(floats.get(position / Float.BYTES + 2)),
                            "Rotated camera-space line positions remain finite");
                    int normal = base + format.getOffset(VertexFormatElement.NORMAL);
                    check(bytes.get(normal) != 0 || bytes.get(normal + 1) != 0 || bytes.get(normal + 2) != 0,
                            "Line shader receives a nonzero transformed direction");
                    //? if >=26.1.2 {
                    /*float width = floats.get((base + format.getOffset(VertexFormatElement.LINE_WIDTH)) / Float.BYTES);
                    check(Float.isFinite(width) && width > 0, "Every expanded endpoint has a visible pixel width");
                    *///? }
                }
            }
        }
        try (ByteBufferBuilder storage = new ByteBufferBuilder(128)) {
            BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.LINES, format);
            ProceduralGeometry.line(pose, builder, Vec3.ZERO, Vec3.ZERO, 0xFFFFFF, 1);
            ProceduralGeometry.line(pose, builder, Vec3.ZERO, new Vec3(1, 0, 0), 0xFFFFFF, 0);
            check(builder.build() == null, "Degenerate and invisible lines emit no GPU geometry");
        }
        System.out.println("ProceduralLineRenderTest: " + checks + " checks PASS (native vertices; no visual approval)");
    }

    //? if >=26.1.2 {
    /*private static void modernPipelineContract(VertexFormat format) {
        check(format.contains(VertexFormatElement.LINE_WIDTH), "Native line shader requires per-vertex width");
        for (var type : new net.minecraft.client.renderer.rendertype.RenderType[] {
                ProceduralRenderTypes.PERCEPTION_LINES, ProceduralRenderTypes.WORLD_LINES,
                ProceduralRenderTypes.WORLD_DEPTH_LINES}) {
            var pipeline = type.pipeline();
            check(pipeline.getVertexFormat().equals(format), "Every procedural line pass matches the native shader's attributes");
            check(pipeline.getVertexFormatMode() == VertexFormat.Mode.LINES,
                    "Every procedural line pass uses the native expanded-line topology");
            check(pipeline.getVertexShader().equals(RenderPipelines.LINES.getVertexShader()),
                    "Procedural line passes use the verified native line shader");
        }
        var perception = ProceduralRenderTypes.PERCEPTION_LINES.pipeline().getDepthStencilState();
        check(perception.depthTest() == CompareOp.ALWAYS_PASS && !perception.writeDepth(),
                "Ore Sight and Threat Sense remain visible through terrain without changing world depth");
        var world = ProceduralRenderTypes.WORLD_DEPTH_LINES.pipeline().getDepthStencilState();
        check(world.depthTest() == CompareOp.LESS_THAN_OR_EQUAL && world.writeDepth(),
                "Physical procedural edges retain normal depth occlusion");
        check(ProceduralRenderTypes.PERCEPTION_PLANES.pipeline().getVertexFormat().equals(DefaultVertexFormat.POSITION_COLOR)
                        && ProceduralRenderTypes.PERCEPTION_PLANES.pipeline().getVertexFormatMode() == VertexFormat.Mode.QUADS,
                "Filled geometry keeps its separate native vertex contract");

        // Regression control: the former emitter is incomplete for the actual 26.1 line shader.
        boolean missingWidthRejected = false;
        try (ByteBufferBuilder storage = new ByteBufferBuilder(128)) {
            BufferBuilder incomplete = new BufferBuilder(storage, VertexFormat.Mode.LINES, format);
            incomplete.addVertex(0, 0, 0).setColor(255, 255, 255, 255).setNormal(1, 0, 0);
            try {
                incomplete.buildOrThrow();
            } catch (IllegalStateException expected) {
                missingWidthRejected = expected.getMessage().contains("LineWidth");
            }
        }
        check(missingWidthRejected, "The native builder rejects the old line emission missing LineWidth");
    }
    *///? }

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
