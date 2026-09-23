package com.mistaboom.essence_ascendance.client.procedural;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/** Frame-local submission of existing compositions after physical surfaces have established depth. */
public final class ProceduralWorldQueue {
    private static final List<Entry> QUEUED = new ArrayList<>();
    private static boolean inWorldFrame;
    private static final Matrix4f WORLD_POSE = new Matrix4f();
    private ProceduralWorldQueue() { }

    public static void clear() { QUEUED.clear(); inWorldFrame = false; }
    public static void beginFrame(Matrix4f positionMatrix) {
        clear();
        WORLD_POSE.set(positionMatrix);
        inWorldFrame = true;
    }
    public static boolean inWorldFrame() { return inWorldFrame; }

    /** Vanilla entity poses omit the camera rotation held in RenderSystem until renderLevel returns. */
    public static void enqueueEntity(PoseStack pose, double distanceSquared,
                                     BiConsumer<PoseStack, MultiBufferSource.BufferSource> render) {
        PoseStack captured = new PoseStack();
        captured.mulPose(WORLD_POSE);
        captured.last().pose().mul(pose.last().pose());
        captured.last().normal().mul(pose.last().normal());
        QUEUED.add(new Entry(captured, distanceSquared, render));
    }

    public static void enqueue(PoseStack pose, double distanceSquared,
                               BiConsumer<PoseStack, MultiBufferSource.BufferSource> render) {
        PoseStack captured = new PoseStack();
        captured.last().pose().set(pose.last().pose());
        captured.last().normal().set(pose.last().normal());
        QUEUED.add(new Entry(captured, distanceSquared, render));
    }

    public static void render() {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        QUEUED.sort(Comparator.comparingDouble(Entry::distanceSquared).reversed());
        try {
            for (Entry entry : QUEUED) entry.render().accept(entry.pose(), buffers);
            buffers.endBatch();
        } finally {
            clear();
        }
    }

    private record Entry(PoseStack pose, double distanceSquared,
                         BiConsumer<PoseStack, MultiBufferSource.BufferSource> render) { }
}
