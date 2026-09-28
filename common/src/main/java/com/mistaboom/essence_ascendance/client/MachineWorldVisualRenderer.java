package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusBlockEntity;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import com.mistaboom.essence_ascendance.machine.HorizontalMachineBlock;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralWorldQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Defers machine-owned translucent world visuals until opaque block entities,
 * signs, and fluid surfaces have populated the frame's depth buffer.
 */
public final class MachineWorldVisualRenderer {
    private static final List<Entry> QUEUED = new ArrayList<>();

    private MachineWorldVisualRenderer() { }

    public static void beginFrame() {
        QUEUED.clear();
    }

    public static void enqueue(EssenceCrucibleBlockEntity crucible, float partialTick) {
        QUEUED.add(new Entry(crucible, Kind.CRUCIBLE, null, partialTick, 0));
    }

    public static void enqueue(EssencePylonBlockEntity pylon, float partialTick, int packedOverlay) {
        QUEUED.add(new Entry(pylon, Kind.PYLON, null, partialTick, packedOverlay));
    }

    public static void enqueue(EssenceInfuserBlockEntity infuser, ItemRenderer itemRenderer,
                               float partialTick, int packedOverlay) {
        QUEUED.add(new Entry(infuser, Kind.INFUSER, itemRenderer, partialTick, packedOverlay));
    }

    public static void enqueue(AscendanceNexusBlockEntity nexus, float partialTick) {
        QUEUED.add(new Entry(nexus, Kind.NEXUS, null, partialTick, 0));
    }

    public static void render(Camera camera, Matrix4f positionMatrix) {
        if (QUEUED.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            QUEUED.clear();
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        List<Entry> entries = new ArrayList<>(QUEUED);
        QUEUED.clear();
        entries.removeIf(entry -> entry.entity().isRemoved()
                || entry.entity().getLevel() != minecraft.level);
        entries.sort(Comparator.comparingDouble(
                (Entry entry) -> Vec3.atCenterOf(entry.entity().getBlockPos())
                        .distanceToSqr(cameraPosition)).reversed());

        if (entries.isEmpty()) return;

        PoseStack pose = new PoseStack();
        pose.mulPose(positionMatrix);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (Entry entry : entries) {
            BlockPos pos = entry.entity().getBlockPos();
            pose.pushPose();
            pose.translate(pos.getX() - cameraPosition.x,
                    pos.getY() - cameraPosition.y,
                    pos.getZ() - cameraPosition.z);
            entry.renderPhysical(pose, buffers);
            ProceduralWorldQueue.enqueue(pose, Vec3.atCenterOf(pos).distanceToSqr(cameraPosition),
                    entry::render);
            pose.popPose();
        }

        // This pass runs after the normal world submissions. Flush every type used
        // by machine visuals, including the Infuser workpiece and Focus gem types.
        buffers.endBatch();
    }

    private enum Kind { CRUCIBLE, PYLON, INFUSER, NEXUS }

    private record Entry(BlockEntity entity, Kind kind, ItemRenderer itemRenderer,
                         float partialTick, int packedOverlay) {
        private void renderPhysical(PoseStack pose, MultiBufferSource.BufferSource buffers) {
            if (kind == Kind.PYLON) {
                EssencePylonBlockEntity pylon = (EssencePylonBlockEntity) entity;
                FocusVisuals.renderInstalled(FocusVisuals.Context.installed(
                                pylon.visualState().focus(), pylon.visualState().linked(),
                                PylonLocalFrame.of(pylon.getBlockState())),
                        pylon.getLevel(), pylon.getBlockPos(), partialTick, pose, buffers, packedOverlay);
            } else if (kind == Kind.INFUSER) {
                EssenceInfuserBlockEntity infuser = (EssenceInfuserBlockEntity) entity;
                InfuserVisuals.renderWorkpiece(infuser, itemRenderer, partialTick, pose, buffers, packedOverlay);
                FocusVisuals.renderInstalled(FocusVisuals.Context.installed(
                                infuser.visualState().focus(), infuser.visualState().linked(),
                                HorizontalMachineBlock.frame(infuser.getBlockState().getValue(HorizontalMachineBlock.FACING))),
                        infuser.getLevel(), infuser.getBlockPos(), partialTick, pose, buffers, packedOverlay);
            }
        }

        private void render(PoseStack pose, MultiBufferSource.BufferSource buffers) {
            switch (kind) {
                case CRUCIBLE -> CrucibleVisuals.render(
                        (EssenceCrucibleBlockEntity) entity, partialTick, pose, buffers);
                case PYLON -> {
                    EssencePylonBlockEntity pylon = (EssencePylonBlockEntity) entity;
                    PylonVisuals.render(pylon, partialTick, pose, buffers);
                    FocusVisuals.renderOrnament(FocusVisuals.Context.installed(
                                    pylon.visualState().focus(), pylon.visualState().linked(),
                                    PylonLocalFrame.of(pylon.getBlockState())),
                            pylon.getLevel(), pylon.getBlockPos(), partialTick,
                            pose, buffers);
                    EssenceTetherVisuals.renderPylon(pylon, partialTick, pose, buffers);
                }
                case INFUSER -> {
                    EssenceInfuserBlockEntity infuser = (EssenceInfuserBlockEntity) entity;
                    InfuserVisuals.render(infuser, itemRenderer, partialTick, pose,
                            buffers, packedOverlay);
                    FocusVisuals.renderOrnament(FocusVisuals.Context.installed(
                                    infuser.visualState().focus(), infuser.visualState().linked(),
                                    HorizontalMachineBlock.frame(infuser.getBlockState().getValue(HorizontalMachineBlock.FACING))),
                            infuser.getLevel(), infuser.getBlockPos(), partialTick,
                            pose, buffers);
                    EssenceTetherVisuals.renderInfuser(infuser, partialTick, pose, buffers);
                }
                case NEXUS -> NexusVisuals.render(
                        (AscendanceNexusBlockEntity) entity, partialTick, pose, buffers);
            }
            if (kind == Kind.CRUCIBLE) {
                EssenceTetherVisuals.renderPlayerChannel(
                        (EssenceCrucibleBlockEntity) entity, partialTick, pose, buffers);
            }
        }
    }
}
