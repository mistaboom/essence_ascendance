package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.machine.HorizontalMachineBlock;
import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Persistent, state-driven containment and compression field for the Essence Infuser. */
public final class InfuserVisuals {
    public static final int VIEW_DISTANCE = 72;

    private static final double DETAIL_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double TAU = Math.PI * 2.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    /** Twelve model units above the base of the sixteen-unit physical block. */
    private static final Vec3 WORK_CENTER = new Vec3(0.5, 12.0 / 16.0, 0.5);
    private static final Vec3 FOCUS_CENTER = new Vec3(0.5, 1.48, 0.5);

    private InfuserVisuals() { }

    public static void render(EssenceInfuserBlockEntity infuser, ItemRenderer itemRenderer,
                              float partialTick, PoseStack pose,
                              MultiBufferSource.BufferSource buffers,
                              int packedOverlay) {
        Level level = infuser.getLevel();
        if (level == null) return;

        MachineVisualState.Infuser state = infuser.visualState();
        double age = level.getGameTime() + partialTick;
        boolean close = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(Vec3.atCenterOf(infuser.getBlockPos())) <= DETAIL_DISTANCE_SQUARED;

        // Absence of a procedural field is the deliberate unlinked read.
        if (!state.linked()) return;

        PylonLocalFrame frame = HorizontalMachineBlock.frame(
                infuser.getBlockState().getValue(HorizontalMachineBlock.FACING));
        pose.pushPose();
        PylonRenderTransform.applyAroundBlockCenter(pose, frame);
        if (!state.focus().installed()) {
            renderEmpty(pose, buffers, age, close);
        } else {
            renderFunctional(infuser.getBlockPos(), state, frame, itemRenderer, level, pose, buffers,
                    packedOverlay, age, close);
        }
        pose.popPose();
    }

    private static void renderEmpty(PoseStack pose, MultiBufferSource buffers,
                                    double age, boolean close) {
        int dormant = AscendancePalette.LATENT.primaryRgb();
        double phase = age * 0.0025;

        // Deliberately incomplete and low in the chassis: this is a socket registration,
        // not a weakened version of the functional containment field.
        Vec3 socket = WORK_CENTER;
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        ProceduralGeometry.annulus(pose, planes, socket, X, Z,
                0.275, 0.35, 20, phase * 0.22, dormant, 0.11F);

        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        ProceduralGeometry.brokenRing(pose, lines, socket, X, Z, 0.34,
                phase, TAU, 4, 4, 0.68, dormant, 0.26F);
        ProceduralGeometry.brokenRing(pose, lines, socket, X, Y, 0.31,
                -phase * 0.64 + Math.PI * 0.17, TAU, 3, 3,
                0.82, dormant, 0.14F);
        if (close) {
            ProceduralGeometry.spokes(pose, lines, socket, X, Z,
                    0.27, 0.37, 4, Math.PI * 0.25, dormant, 0.18F);
        }
    }

    private static void renderFunctional(BlockPos pos, MachineVisualState.Infuser state,
                                         PylonLocalFrame localFrame,
                                         ItemRenderer itemRenderer, Level level,
                                         PoseStack pose, MultiBufferSource.BufferSource buffers,
                                         int packedOverlay, double age, boolean close) {
        MachineVisualState.Focus focus = state.focus();
        int tier = tierLevel(focus.tier());
        double refinement = tier / 5.0;
        double throughput = throughput(state.throughputPerSecond());
        double progress = state.requiredTicks() <= 0 ? 0.0
                : Math.clamp(state.processingTicks() / (double) state.requiredTicks(), 0.0, 1.0);
        boolean hasWorkpiece = state.workpiece() != null;
        boolean active = state.processing() && hasWorkpiece;
        boolean highIntensity = active && (throughput >= 0.62 || progress >= 0.72);
        double intensity = active ? 0.56 + throughput * 0.24 + progress * 0.20
                : (hasWorkpiece ? 0.24 : 0.10);
        double speed = 0.007 + (hasWorkpiece ? 0.005 : 0.0) + intensity * 0.033;
        double phase = age * speed;
        double counterPhase = -age * (0.005 + intensity * 0.021);
        int rgb = FocusVisuals.color(focus.tier());
        int luminous = towardWhite(rgb, 0.60);
        int frame = towardWhite(AscendancePalette.LATENT.metalRgb(), 0.22);

        // Complete all plane work before acquiring the depth-line consumer. Some
        // BufferSource implementations end the current builder on a type switch.
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        renderCompressionBands(pose, planes, age, phase, rgb, luminous, hasWorkpiece,
                active, highIntensity, intensity, tier, refinement);
        renderShellPieces(pose, planes, localFrame, age, phase, rgb, luminous, tier,
                hasWorkpiece, active, intensity);
        if (state.linked()) {
            renderAnchorPlane(pos, state, localFrame, pose, planes, frame, active);
        }

        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        renderPrimaryCage(pose, lines, age, phase, rgb, luminous, hasWorkpiece,
                active, highIntensity, intensity, tier, refinement);
        if (hasWorkpiece) {
            renderPressureLines(pose, lines, age, phase, luminous, active,
                    highIntensity, intensity, tier);
            renderFocusFeed(pose, lines, age, phase, rgb, luminous, active,
                    intensity, tier);
        }
        if (state.linked()) {
            renderAnchorLine(pos, state, localFrame, pose, lines, frame, active);
        }
        if (close) {
            renderFineDetail(pose, lines, age, phase, counterPhase, rgb, luminous,
                    frame, tier, hasWorkpiece, active, highIntensity, refinement);
        }
    }

    /** Physical item prepass, shared by every machine before any depth-writing linework. */
    public static void renderWorkpiece(EssenceInfuserBlockEntity infuser, ItemRenderer itemRenderer,
                                      float partialTick, PoseStack pose,
                                      MultiBufferSource.BufferSource buffers, int overlay) {
        Level level = infuser.getLevel();
        MachineVisualState.Infuser state = infuser.visualState();
        if (level == null || !state.linked() || !state.focus().installed() || state.workpiece() == null) return;
        double progress = state.requiredTicks() <= 0 ? 0.0
                : Math.clamp(state.processingTicks() / (double) state.requiredTicks(), 0.0, 1.0);
        double intensity = state.processing()
                ? 0.56 + throughput(state.throughputPerSecond()) * 0.24 + progress * 0.20 : 0.24;
        pose.pushPose();
        PylonRenderTransform.applyAroundBlockCenter(pose, HorizontalMachineBlock.frame(
                infuser.getBlockState().getValue(HorizontalMachineBlock.FACING)));
        renderWorkpiece(state, itemRenderer, level, infuser.getBlockPos(), pose, buffers,
                overlay, level.getGameTime() + partialTick, state.processing(), intensity);
        pose.popPose();
    }

    private static void renderWorkpiece(MachineVisualState.Infuser state,
                                        ItemRenderer itemRenderer, Level level, BlockPos pos,
                                        PoseStack pose, MultiBufferSource.BufferSource buffers,
                                        int overlay, double age, boolean active,
                                        double intensity) {
        Item item = BuiltInRegistries.ITEM.get(state.workpiece());
        ItemStack stack = item.getDefaultInstance();
        if (stack.isEmpty()) return;

        pose.pushPose();
        double hover = ProceduralMotion.oscillate(age * (active ? 0.13 : 0.075),
                active ? 0.028 : 0.015);
        pose.translate(WORK_CENTER.x, WORK_CENTER.y + hover, WORK_CENTER.z);
        pose.mulPose(Axis.YP.rotationDegrees((float) (age * (active ? 2.45 : 0.75))));
        pose.mulPose(Axis.XP.rotationDegrees((float) (active
                ? ProceduralMotion.oscillate(age * 0.055, 5.0) : -3.0)));
        float scale = (float) (0.43 + (active ? ProceduralMotion.oscillate(age * 0.19, 0.012) : 0));
        pose.scale(scale, scale, scale);
        int light = LevelRenderer.getLightColor(level, pos.above());
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay,
                pose, buffers, level, pos.hashCode());
        pose.popPose();

        // Item render types live in fixed buffers and would otherwise remain queued
        // until after every procedural pass, painting the workpiece over the whole
        // cage. Establish its real depth now; subsequent field fragments behind it
        // fail normally while fragments physically in front still pass.
        buffers.endBatch();
    }

    private static void renderCompressionBands(PoseStack pose, VertexConsumer planes,
                                               double age, double phase, int rgb, int luminous,
                                               boolean hasWorkpiece, boolean active,
                                               boolean highIntensity, double intensity,
                                               int tier, double refinement) {
        double baseRadius = hasWorkpiece ? 0.58 + refinement * 0.15 : 0.47 + refinement * 0.08;
        float idleOpacity = hasWorkpiece ? 0.065F : 0.045F;
        float opacity = (float) (idleOpacity + refinement * 0.15 + intensity * 0.08);
        double gap = Math.max(0.09, 0.48 - refinement * 0.34 - intensity * 0.04);

        brokenBand(pose, planes, WORK_CENTER, X, Z,
                baseRadius - 0.055, baseRadius, phase * 0.55,
                3 + (tier >= 3 ? 1 : 0), 5 + tier, gap, rgb, opacity);
        brokenBand(pose, planes, WORK_CENTER, X, Y,
                baseRadius - 0.068, baseRadius - 0.008, -phase * 0.72 + Math.PI * 0.17,
                3 + (tier >= 3 ? 1 : 0), 5 + tier, gap + 0.055, rgb, opacity * 0.82F);
        if (tier >= 3) {
            brokenBand(pose, planes, WORK_CENTER, Z, Y,
                    baseRadius - 0.072, baseRadius - 0.012, phase * 0.82 + Math.PI * 0.31,
                    4, 7 + tier, gap + 0.04, luminous, opacity * 0.70F);
        }

        if (active) {
            int pulses = highIntensity && tier >= 4 ? 2 : 1;
            double rate = 0.026 + intensity * 0.026;
            for (int index = 0; index < pulses; index++) {
                double pulse = ProceduralMotion.phase(age + index / (double) pulses / rate, rate);
                double eased = ProceduralMotion.smoothStep(pulse);
                double radius = 0.72 - eased * 0.38;
                float pulseOpacity = (float) ((0.12 + refinement * 0.18 + intensity * 0.12)
                        * (1.0 - pulse));
                brokenBand(pose, planes, WORK_CENTER, X, Z,
                        radius - 0.052, radius, phase * 0.20 + index * 0.43,
                        4, 5, 0.18, luminous, pulseOpacity);
            }
        }
    }

    private static void renderShellPieces(PoseStack pose, VertexConsumer planes,
                                          PylonLocalFrame frame,
                                          double age, double phase, int rgb, int luminous,
                                          int tier, boolean hasWorkpiece, boolean active,
                                          double intensity) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f cameraUp = camera.getUpVector();
        Vec3 right = frame.worldVectorToLocal(new Vec3(-left.x(), -left.y(), -left.z()));
        Vec3 up = frame.worldVectorToLocal(new Vec3(cameraUp.x(), cameraUp.y(), cameraUp.z()));
        int count = tier == 0 ? 0 : (hasWorkpiece ? Math.min(6, tier + 1) : Math.min(4, tier));
        double radius = hasWorkpiece ? 0.57 + tier * 0.012 : 0.43;

        for (int index = 0; index < count; index++) {
            double angle = ProceduralMotion.orbitAngle(index, count,
                    phase * (index % 2 == 0 ? 0.82 : -0.61));
            double vertical = (index % 2 == 0 ? 0.17 : -0.12)
                    + ProceduralMotion.oscillate(age * 0.09 + index * 1.7, 0.045);
            Vec3 center = ProceduralGeometry.orbitPoint(WORK_CENTER.add(0, vertical, 0),
                    X, Z, radius, radius, angle);
            double size = active ? 0.062 : 0.048;
            ProceduralGeometry.diamondRing(pose, planes, center, right, up,
                    size * 1.55, size * 2.05, size, size * 1.32,
                    rgb, (float) (0.08 + tier * 0.035 + intensity * 0.10));
            ProceduralGeometry.diamond(pose, planes, center, right, up,
                    size, size * 1.32, luminous,
                    (float) (0.24 + tier * 0.09 + intensity * 0.20));
        }
    }

    private static void renderPrimaryCage(PoseStack pose, VertexConsumer lines,
                                          double age, double phase, int rgb, int luminous,
                                          boolean hasWorkpiece, boolean active,
                                          boolean highIntensity, double intensity,
                                          int tier, double refinement) {
        double radius = hasWorkpiece ? 0.60 + refinement * 0.15 : 0.49 + refinement * 0.08;
        double gap = Math.max(0.055, 0.49 - refinement * 0.36 - intensity * 0.045);
        float strength = (float) (0.25 + refinement * 0.34 + intensity * 0.23);
        double lock = active ? fieldLock(age, highIntensity ? 0.035 : 0.0225) : 0.0;

        ProceduralGeometry.brokenRing(pose, lines, WORK_CENTER, X, Z, radius,
                phase + lock, TAU, 3 + (tier >= 3 ? 1 : 0), 6 + tier, gap, rgb, strength);
        ProceduralGeometry.brokenRing(pose, lines, WORK_CENTER, X, Y, radius - 0.01,
                -phase * 0.72 + Math.PI * 0.17 - lock, TAU, 3 + (tier >= 3 ? 1 : 0), 6 + tier,
                gap + 0.025, rgb, strength * 0.92F);
        if (tier >= 3) {
            ProceduralGeometry.brokenRing(pose, lines, WORK_CENTER, Z, Y, radius - 0.02,
                    phase * 0.84 + Math.PI * 0.31 + lock, TAU, 4, 8 + tier,
                    gap + 0.045, luminous, strength * 0.74F);
        }

        if (active) {
            double pulse = ProceduralMotion.phase(age, 0.030 + intensity * 0.028);
            double pulseRadius = 0.73 - ProceduralMotion.smoothStep(pulse) * 0.43;
            ProceduralGeometry.ring(pose, lines, WORK_CENTER, X, Z,
                    pulseRadius, 28, luminous,
                    (float) ((0.24 + refinement * 0.30 + intensity * 0.18) * (1.0 - pulse)));
        }
    }

    private static void renderPressureLines(PoseStack pose, VertexConsumer lines,
                                            double age, double phase, int rgb,
                                            boolean active, boolean highIntensity,
                                            double intensity, int tier) {
        int count = 4 + tier * 2;
        double outer = 0.64 + tier * 0.012;
        double inner = active ? 0.10 : 0.17;
        float opacity = (float) (0.17 + tier * 0.055 + intensity * 0.26);
        for (int index = 0; index < count; index++) {
            double angle = ProceduralMotion.orbitAngle(index, count,
                    phase * (index % 2 == 0 ? 0.24 : -0.19));
            double y = index % 3 == 0 ? 0.18 : (index % 3 == 1 ? -0.15 : 0.02);
            Vec3 start = ProceduralGeometry.orbitPoint(WORK_CENTER.add(0, y, 0),
                    X, Z, outer, outer, angle);
            Vec3 end = ProceduralGeometry.orbitPoint(WORK_CENTER, X, Z,
                    inner, inner, angle + (active ? Math.PI * 0.08 : 0));
            ProceduralGeometry.line(pose, lines, start, end, rgb, opacity);

            if (active && (highIntensity || tier >= 2 && index % 2 == 0)) {
                double travel = ProceduralMotion.phase(age + index * 2.7,
                        0.035 + intensity * 0.040);
                Vec3 tracerStart = start.lerp(end, Math.max(0.0, travel - 0.12));
                Vec3 tracerEnd = start.lerp(end, travel);
                ProceduralGeometry.line(pose, lines, tracerStart, tracerEnd,
                        rgb, Math.min(1.0F, opacity + 0.20F));
            }
        }
    }

    private static void renderFocusFeed(PoseStack pose, VertexConsumer lines,
                                        double age, double phase, int rgb, int luminous,
                                        boolean active, double intensity, int tier) {
        int count = Math.min(4, 1 + (tier + 1) / 2);
        for (int index = 0; index < count; index++) {
            double angle = ProceduralMotion.orbitAngle(index, count, -phase * 0.45);
            Vec3 source = ProceduralGeometry.orbitPoint(FOCUS_CENTER, X, Z,
                    0.20, 0.20, angle);
            Vec3 target = ProceduralGeometry.orbitPoint(WORK_CENTER.add(0, 0.04, 0),
                    X, Z, active ? 0.08 : 0.13, active ? 0.08 : 0.13,
                    angle + Math.PI * 0.42);
            ProceduralGeometry.line(pose, lines, source, target,
                    index == 0 ? luminous : rgb,
                    (float) (active ? 0.30 + tier * 0.055 + intensity * 0.15
                            : 0.12 + tier * 0.045));
            if (active) {
                double travel = ProceduralMotion.phase(age + index * 4.1,
                        0.047 + intensity * 0.035);
                Vec3 head = source.lerp(target, travel);
                Vec3 tail = source.lerp(target, Math.max(0.0, travel - 0.16));
                ProceduralGeometry.line(pose, lines, tail, head, luminous, 0.92F);
            }
        }
    }

    private static void renderFineDetail(PoseStack pose, VertexConsumer lines,
                                         double age, double phase, double counterPhase,
                                         int rgb, int luminous, int frame, int tier,
                                         boolean hasWorkpiece, boolean active,
                                         boolean highIntensity, double refinement) {
        float faint = (float) (0.07 + refinement * 0.16 + (active ? 0.06 : 0.0));
        double outer = hasWorkpiece ? 0.59 + refinement * 0.06 : 0.47;

        ProceduralGeometry.brokenRing(pose, lines, WORK_CENTER.add(0, 0.11, 0), X, Z,
                outer, counterPhase, TAU, 8, 4,
                Math.max(0.13, 0.34 - refinement * 0.15), rgb, faint);
        if (tier >= 3) {
            ProceduralGeometry.brokenRing(pose, lines, WORK_CENTER, X, Y,
                    outer * 0.78, phase * 0.43, TAU, 6, 4 + tier,
                    0.42 - refinement * 0.20, frame, faint * 0.66F);
        }
        if (tier >= 2) {
            ProceduralGeometry.spokes(pose, lines, WORK_CENTER.add(0, -0.015, 0), X, Z,
                    outer - 0.075, outer + 0.025, 4 + tier * 2,
                    counterPhase * 0.31, rgb, faint * 0.84F);
        }

        double cage = 0.34 + refinement * 0.07;
        if (tier >= 1) {
            ProceduralGeometry.cage(pose, lines,
                    WORK_CENTER.add(-cage, -cage * 0.74, -cage),
                    WORK_CENTER.add(cage, cage * 0.74, cage), frame, faint * 0.44F);
        }
        if (tier >= 4) {
            double inner = cage * 0.67;
            ProceduralGeometry.cage(pose, lines,
                    WORK_CENTER.add(-inner, -inner * 0.74, -inner),
                    WORK_CENTER.add(inner, inner * 0.74, inner), rgb, faint * 0.38F);
        }

        int marks = tier >= 2 ? tier + 1 : 0;
        for (int index = 0; index < marks; index++) {
            double angle = ProceduralMotion.orbitAngle(index, marks, counterPhase * 0.22);
            Vec3 root = ProceduralGeometry.orbitPoint(WORK_CENTER, X, Z,
                    outer + 0.055, outer + 0.055, angle);
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
            Vec3 elbow = root.add(radial.scale(0.035)).add(tangent.scale(index % 2 == 0 ? 0.028 : -0.028));
            Vec3 tip = elbow.add(0, index % 2 == 0 ? 0.075 : -0.075, 0);
            ProceduralGeometry.line(pose, lines, root, elbow, rgb, faint * 0.78F);
            ProceduralGeometry.line(pose, lines, elbow, tip,
                    highIntensity ? luminous : rgb, faint * 0.70F);
        }

        if (active) {
            int corrections = highIntensity ? Math.min(5, 1 + tier) : Math.max(1, tier / 2);
            for (int index = 0; index < corrections; index++) {
                double angle = phase * -0.75 + index * TAU / corrections;
                Vec3 start = ProceduralGeometry.orbitPoint(WORK_CENTER, X, Z,
                        0.31, 0.31, angle);
                Vec3 end = start.add(0, ProceduralMotion.oscillate(age * 0.17 + index, 0.09), 0);
                ProceduralGeometry.line(pose, lines, start, end, luminous, faint * 0.86F);
            }
        }
    }

    /** Machine-local receiver at Focus height, facing the linked Crucible. */
    public static Vec3 tetherAnchor(BlockPos infuserPos, MachineVisualState.Infuser state) {
        if (state.linkedCrucible() == null) return new Vec3(0.5, 1.5, 0.5);
        double dx = state.linkedCrucible().getX() - infuserPos.getX();
        double dz = state.linkedCrucible().getZ() - infuserPos.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return new Vec3(1.08, 1.5, 0.5);
        return new Vec3(0.5 + dx / length * 0.58, 1.5,
                0.5 + dz / length * 0.58);
    }

    private static void renderAnchorPlane(BlockPos pos, MachineVisualState.Infuser state,
                                          PylonLocalFrame frame,
                                          PoseStack pose, VertexConsumer planes,
                                          int rgb, boolean active) {
        Vec3 anchor = frame.blockToLocal(tetherAnchor(pos, state));
        Vec3 radial = new Vec3(anchor.x - 0.5, 0, anchor.z - 0.5).normalize();
        Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
        ProceduralGeometry.diamondRing(pose, planes, anchor, tangent, Y,
                0.072, 0.095, 0.037, 0.052, rgb, active ? 0.40F : 0.24F);
        ProceduralGeometry.diamond(pose, planes, anchor, tangent, Y,
                0.037, 0.052, rgb, active ? 0.72F : 0.48F);
    }

    private static void renderAnchorLine(BlockPos pos, MachineVisualState.Infuser state,
                                         PylonLocalFrame frame,
                                         PoseStack pose, VertexConsumer lines,
                                         int rgb, boolean active) {
        Vec3 anchor = frame.blockToLocal(tetherAnchor(pos, state));
        Vec3 inner = WORK_CENTER.lerp(anchor, 0.68).add(0, -0.08, 0);
        ProceduralGeometry.line(pose, lines, inner, anchor, rgb, active ? 0.50F : 0.27F);
    }

    private static void brokenBand(PoseStack pose, VertexConsumer planes,
                                   Vec3 center, Vec3 axisA, Vec3 axisB,
                                   double innerRadius, double outerRadius, double start,
                                   int pieces, int segmentsPerPiece, double gapFraction,
                                   int rgb, float alpha) {
        double pieceSweep = TAU / pieces;
        double visibleSweep = pieceSweep * (1.0 - gapFraction);
        for (int piece = 0; piece < pieces; piece++) {
            double pieceStart = start + piece * pieceSweep;
            for (int segment = 0; segment < segmentsPerPiece; segment++) {
                double a = pieceStart + visibleSweep * segment / segmentsPerPiece;
                double b = pieceStart + visibleSweep * (segment + 1) / segmentsPerPiece;
                Vec3 outerA = ProceduralGeometry.orbitPoint(center, axisA, axisB,
                        outerRadius, outerRadius, a);
                Vec3 outerB = ProceduralGeometry.orbitPoint(center, axisA, axisB,
                        outerRadius, outerRadius, b);
                Vec3 innerB = ProceduralGeometry.orbitPoint(center, axisA, axisB,
                        innerRadius, innerRadius, b);
                Vec3 innerA = ProceduralGeometry.orbitPoint(center, axisA, axisB,
                        innerRadius, innerRadius, a);
                ProceduralGeometry.quad(pose, planes, outerA, outerB, innerB, innerA, rgb, alpha);
            }
        }
    }

    private static double fieldLock(double age, double rate) {
        double phase = ProceduralMotion.phase(age, rate);
        if (phase < 0.78) return 0.0;
        double snap = ProceduralMotion.smoothStep((phase - 0.78) / 0.22);
        return Math.sin(snap * Math.PI) * 0.075;
    }

    private static int tierLevel(EssenceFocusTier tier) {
        return tier == null ? 0 : tier.ordinal() + 1;
    }

    private static double throughput(long rate) {
        if (rate <= 0) return 0.0;
        return Math.min(1.0, Math.log1p(rate) / 12.0);
    }

    private static int towardWhite(int rgb, double amount) {
        int r = (int) Math.round((rgb >> 16 & 255) * (1.0 - amount) + 255 * amount);
        int g = (int) Math.round((rgb >> 8 & 255) * (1.0 - amount) + 255 * amount);
        int b = (int) Math.round((rgb & 255) * (1.0 - amount) + 255 * amount);
        return r << 16 | g << 8 | b;
    }
}
