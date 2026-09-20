package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Player render-layer bridge for the procedural aerial harness.
 *
 * <p>Rendering from a normal player layer is important here: the incoming pose
 * stack already carries vanilla's body yaw, crouch and fall-flying transforms.
 * We then attach to the actual torso {@link PlayerModel#body} transform so the
 * harness follows the player's back rather than behaving like a world overlay.</p>
 */
public final class FlightVisualLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public FlightVisualLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (Minecraft.getInstance().player != player || player.isInvisible() || player.isSpectator()) return;
        if (!FlightVisualRenderer.active()) return;

        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);
        FlightVisualRenderer.render(player, poseStack, buffer, partialTick);
        poseStack.popPose();
    }
}
