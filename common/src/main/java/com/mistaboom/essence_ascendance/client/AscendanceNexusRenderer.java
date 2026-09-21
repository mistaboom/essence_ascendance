package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.nexus.AscendanceNexusBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Renders the Blockbench Nexus mesh and its embedded texture. */
public final class AscendanceNexusRenderer
        implements BlockEntityRenderer<AscendanceNexusBlockEntity> {

    public AscendanceNexusRenderer(
            BlockEntityRendererProvider.Context context
    ) {
    }

    @Override
    public void render(
            AscendanceNexusBlockEntity nexus,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        EssenceMachineMeshes.NEXUS.render(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );
    }
}
