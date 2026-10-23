package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.machine.HorizontalMachineBlock;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Native 26.1.2 submit path for the modeled Infuser and its contained item. */
public final class EssenceInfuserRenderer implements BlockEntityRenderer<EssenceInfuserBlockEntity,
        EssenceInfuserRenderer.State> {
    public static final class State extends MachineBlockEntityRenderState<EssenceInfuserBlockEntity> {
        final ItemStackRenderState workpiece = new ItemStackRenderState();
        double age;
        boolean active;
        boolean hasWorkpiece;
        int workpieceLight;
    }

    public EssenceInfuserRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(EssenceInfuserBlockEntity infuser, State state,
            float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderState.extractBase(infuser, state, breaking);
        state.source = infuser;
        state.partialTick = partialTick;
        state.workpiece.clear();
        state.hasWorkpiece = false;
        if (infuser.getLevel() == null) return;
        MachineVisualState.Infuser visual = infuser.visualState();
        state.age = infuser.getLevel().getGameTime() + partialTick;
        state.active = visual.processing();
        if (!visual.linked() || !visual.focus().installed() || visual.workpiece() == null) return;
        var item = BuiltInRegistries.ITEM.getValue(visual.workpiece()).getDefaultInstance();
        if (item.isEmpty()) return;
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.workpiece,
                item, ItemDisplayContext.FIXED, infuser.getLevel(), null,
                infuser.getBlockPos().hashCode());
        state.hasWorkpiece = !state.workpiece.isEmpty();
        state.workpieceLight = ClientLightCompat.packed(infuser.getLevel(), infuser.getBlockPos().above());
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.source == null) return;
        pose.pushPose();
        PylonRenderTransform.applyAroundBlockCenter(pose,
                HorizontalMachineBlock.frame(state.source.getBlockState().getValue(HorizontalMachineBlock.FACING)));
        EssenceMachineMeshes.INFUSER.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY);
        if (state.hasWorkpiece) {
            pose.pushPose();
            InfuserVisuals.applyWorkpiecePose(pose, state.age, state.active);
            state.workpiece.submit(pose, collector, state.workpieceLight, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.popPose();
        MachineWorldVisualRenderer.enqueue(state.source, state.partialTick, OverlayTexture.NO_OVERLAY);
    }

    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return InfuserVisuals.VIEW_DISTANCE; }
}
