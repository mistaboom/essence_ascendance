package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Small runtime renderer for the static triangle meshes exported from the
 * Blockbench Generic Model files used by the Crucible and Pylon.
 *
 * <p>The mesh resource stores three textured vertices per triangle. Minecraft's
 * entity render types are quad based, so each triangle is emitted as a quad
 * whose final vertex repeats the third vertex. The second half of that quad is
 * therefore degenerate while the first half is the original triangle.</p>
 */
public final class BlockbenchStaticMesh {

    private static final int MAGIC = 0x45414D31; // "EAM1"
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int VERTICES_PER_TRIANGLE = 3;
    private static final int FLOATS_PER_TRIANGLE = FLOATS_PER_VERTEX * VERTICES_PER_TRIANGLE;
    private static final float[] EMPTY = new float[0];

    private final ResourceLocation meshResource;
    private final ResourceLocation texture;

    private volatile float[] vertexData;

    /** Client resource reload boundary; the next render resolves the current mesh asset. */
    public void invalidate() {
        synchronized (this) {
            vertexData = null;
        }
    }

    public BlockbenchStaticMesh(
            ResourceLocation meshResource,
            ResourceLocation texture
    ) {
        this.meshResource = meshResource;
        this.texture = texture;
    }

    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        render(poseStack, bufferSource, packedLight, packedOverlay, 0xFFFFFF, 255, false);
    }

    /** Tint a shared white mesh; optional translucent emission keeps the lit facets visible. */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       int packedOverlay, int rgb, int alpha, boolean emissive) {
        VertexConsumer consumer = bufferSource.getBuffer(
                emissive ? RenderType.entityTranslucentEmissive(texture)
                        : RenderType.entityCutoutNoCull(texture)
        );
        render(poseStack, consumer, packedLight, packedOverlay, rgb, alpha);
    }

    /** Reuse identical geometry and UVs with a caller-owned tier, foil or masked-emission buffer. */
    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight,
                       int packedOverlay, int rgb, int alpha) {
        float[] data = vertexData();
        if (data.length == 0) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();

        for (int triangle = 0; triangle < data.length; triangle += FLOATS_PER_TRIANGLE) {
            emitVertex(consumer, pose, data, triangle, packedLight, packedOverlay, rgb, alpha);
            emitVertex(consumer, pose, data, triangle + FLOATS_PER_VERTEX, packedLight, packedOverlay, rgb, alpha);
            emitVertex(consumer, pose, data, triangle + FLOATS_PER_VERTEX * 2, packedLight, packedOverlay, rgb, alpha);

            // RenderType.entityCutoutNoCull is quad based. Repeating the third
            // vertex produces one real triangle and one degenerate triangle.
            emitVertex(consumer, pose, data, triangle + FLOATS_PER_VERTEX * 2, packedLight, packedOverlay, rgb, alpha);
        }
    }

    private static void emitVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float[] data,
            int offset,
            int packedLight,
            int packedOverlay,
            int rgb,
            int alpha
    ) {
        consumer.addVertex(
                        pose.pose(),
                        data[offset],
                        data[offset + 1],
                        data[offset + 2]
                )
                .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha)
                .setUv(data[offset + 3], data[offset + 4])
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(
                        pose,
                        data[offset + 5],
                        data[offset + 6],
                        data[offset + 7]
                );
    }

    /** Clip only a terminal partial instance; the cached static mesh and UV density stay unchanged. */
    public void renderClipped(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                              int rgb, double planeX, double planeZ, double maximum) {
        renderClipped(stack,consumer,light,overlay,rgb,255,planeX,planeZ,maximum,Double.NEGATIVE_INFINITY);
    }

    /** Intersection of a terminal repeat plane and an optional camera-side axial plane. */
    public void renderClipped(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                              int rgb, int alpha, double planeX, double planeZ, double maximum,double minimumZ) {
        float[] data=vertexData();
        float[][] polygon=new float[6][FLOATS_PER_VERTEX], clipped=new float[6][FLOATS_PER_VERTEX];
        for (int triangle=0;triangle<data.length;triangle+=FLOATS_PER_TRIANGLE) {
            int count=0;
            for (int i=0;i<3;i++) {
                int a=triangle+i*8,b=triangle+((i+1)%3)*8;
                double da=data[a]*planeX+data[a+2]*planeZ-maximum;
                double db=data[b]*planeX+data[b+2]*planeZ-maximum;
                if (da<=0) System.arraycopy(data,a,polygon[count++],0,8);
                if ((da<0 && db>0) || (da>0 && db<0)) {
                    double t=da/(da-db);
                    for (int j=0;j<8;j++) polygon[count][j]=(float)(data[a+j]+(data[b+j]-data[a+j])*t);
                    count++;
                }
            }
            if (Double.isFinite(minimumZ)) {
                int next=0;
                for (int i=0;i<count;i++) {
                    var a=polygon[i]; var b=polygon[(i+1)%count];
                    double da=minimumZ-a[2], db=minimumZ-b[2];
                    if (da<=0) System.arraycopy(a,0,clipped[next++],0,8);
                    if ((da<0 && db>0) || (da>0 && db<0)) {
                        double t=da/(da-db);
                        for (int j=0;j<8;j++) clipped[next][j]=(float)(a[j]+(b[j]-a[j])*t);
                        next++;
                    }
                }
                var temporary=polygon; polygon=clipped; clipped=temporary; count=next;
            }
            for (int i=1;i<count-1;i++) {
                emitVertex(consumer,stack.last(),polygon[0],0,light,overlay,rgb,alpha);
                emitVertex(consumer,stack.last(),polygon[i],0,light,overlay,rgb,alpha);
                emitVertex(consumer,stack.last(),polygon[i+1],0,light,overlay,rgb,alpha);
                emitVertex(consumer,stack.last(),polygon[i+1],0,light,overlay,rgb,alpha);
            }
        }
    }

    private float[] vertexData() {
        float[] loaded = vertexData;
        if (loaded != null) {
            return loaded;
        }

        synchronized (this) {
            loaded = vertexData;
            if (loaded == null) {
                loaded = load();
                vertexData = loaded;
            }
            return loaded;
        }
    }

    private float[] load() {
        Optional<Resource> resource = Minecraft.getInstance()
                .getResourceManager()
                .getResource(meshResource);

        if (resource.isEmpty()) {
            EssenceAscendance.LOGGER.error(
                    "Missing Blockbench mesh resource {}",
                    meshResource
            );
            return EMPTY;
        }

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(resource.get().open())
        )) {
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw new IOException(
                        "Unexpected mesh header 0x" + Integer.toHexString(magic)
                );
            }

            int triangleCount = input.readInt();
            if (triangleCount < 0 || triangleCount > 100_000) {
                throw new IOException(
                        "Invalid triangle count " + triangleCount
                );
            }

            float[] data = new float[
                    triangleCount * FLOATS_PER_TRIANGLE
            ];
            for (int i = 0; i < data.length; i++) {
                data[i] = input.readFloat();
            }
            return data;
        } catch (IOException exception) {
            EssenceAscendance.LOGGER.error(
                    "Unable to load Blockbench mesh {}",
                    meshResource,
                    exception
            );
            return EMPTY;
        }
    }
}
