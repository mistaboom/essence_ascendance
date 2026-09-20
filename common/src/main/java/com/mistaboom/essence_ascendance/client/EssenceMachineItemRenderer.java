package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Shared BEWLR/dynamic-item renderer for the two Generic-Model machine blocks.
 */
public final class EssenceMachineItemRenderer
        extends BlockEntityWithoutLevelRenderer {

    private static volatile EssenceMachineItemRenderer instance;

    private EssenceMachineItemRenderer() {
        super(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels()
        );
    }

    public static EssenceMachineItemRenderer instance() {
        EssenceMachineItemRenderer current = instance;
        if (current != null) {
            return current;
        }

        synchronized (EssenceMachineItemRenderer.class) {
            current = instance;
            if (current == null) {
                current = new EssenceMachineItemRenderer();
                instance = current;
            }
            return current;
        }
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockbenchStaticMesh mesh = null;

        if (stack.is(EssenceCrucibleContent.ESSENCE_CRUCIBLE_ITEM.get())) {
            mesh = EssenceMachineMeshes.CRUCIBLE;
        } else if (stack.is(EssencePylonContent.ESSENCE_PYLON_ITEM.get())) {
            mesh = EssenceMachineMeshes.PYLON;
        }

        if (mesh != null) {
            mesh.render(
                    poseStack,
                    bufferSource,
                    packedLight,
                    packedOverlay
            );
        }
    }
}
