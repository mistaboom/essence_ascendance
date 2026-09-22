package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.gathering.GatheringSurveyService;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Presentation-only through-terrain block outlines for the two Gathering survey skills. */
public final class GatheringSurveyRenderer {
    /*
     * Depth testing is disabled for this render layer, so the wireframe can live on the exact
     * block grid. Keeping exact coordinates matters: inflating every cube creates visibly split
     * parallel lines wherever two surveyed blocks touch.
     */

    private static final RenderType SEE_THROUGH_LINES = ProceduralRenderTypes.PERCEPTION_LINES;

    private GatheringSurveyRenderer() { }

    public static void render(Camera camera, Matrix4f positionMatrix) {
        if (GatheringSurveyClientState.mode() == GatheringSurveyService.Mode.NONE
                || GatheringSurveyClientState.targets().isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        PoseStack pose = new PoseStack();
        pose.mulPose(positionMatrix);
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(SEE_THROUGH_LINES);
        GatheringSurveyService.Mode mode = GatheringSurveyClientState.mode();

        for (GatheringSurveyService.Target target : GatheringSurveyClientState.targets()) {
            int rgb = target.emphasized()
                    ? AscendancePalette.TRANSCENDENT.metalRgb()
                    : AscendancePalette.GATHERING;
            float red = ((rgb >> 16) & 0xFF) / 255.0F;
            float green = ((rgb >> 8) & 0xFF) / 255.0F;
            float blue = (rgb & 0xFF) / 255.0F;
            float alpha = target.emphasized() ? 1.0F
                    : mode == GatheringSurveyService.Mode.TREASURE ? 0.85F : 0.72F;
            AABB box = new AABB(target.pos())
                    .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, alpha);
        }
        buffers.endBatch(SEE_THROUGH_LINES);
    }
}
