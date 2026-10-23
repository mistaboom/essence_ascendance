package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import java.util.Map;
import java.util.WeakHashMap;

/** Captures player flight visuals during state extraction, including GUI preview players. */
public final class FlightVisualLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Map<AvatarRenderState, FlightVisualRenderer.Frame> FRAMES = new WeakHashMap<>();

    public FlightVisualLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

    public static void capture(AbstractClientPlayer player, AvatarRenderState state, float partialTick) {
        FlightVisualRenderer.Frame frame = player.isAlive() && !player.isInvisible()
                && !player.isSpectator() && FlightVisualRenderer.active(player)
                ? FlightVisualRenderer.capture(player, partialTick) : null;
        if (frame == null) FRAMES.remove(state);
        else FRAMES.put(state, frame);
    }

    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int packedLight,
            AvatarRenderState state, float limbSwing, float limbSwingAmount) {
        FlightVisualRenderer.Frame frame = FRAMES.get(state);
        if (frame == null || state.isInvisible || state.isSpectator) return;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        FlightVisualRenderer.submit(frame, pose, collector);
        pose.popPose();
    }
}
