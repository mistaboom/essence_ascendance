package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Persistent, state-driven resonance field surrounding a Pylon and its shared Focus. */
public final class PylonVisuals {
    public static final int VIEW_DISTANCE = 72;
    private static final double DETAIL_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double TAU = Math.PI * 2.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final Vec3 CENTER = new Vec3(0.5, 1.07, 0.5);
    private static final Vec3 UPPER_CENTER = new Vec3(0.5, 1.30, 0.5);

    private PylonVisuals() { }

    public static void render(EssencePylonBlockEntity pylon, float partialTick,
                              PoseStack pose, MultiBufferSource buffers) {
        Level level = pylon.getLevel();
        if (level == null) return;

        MachineVisualState.Pylon state = pylon.visualState();
        double age = level.getGameTime() + partialTick;
        boolean close = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(Vec3.atCenterOf(pylon.getBlockPos())) <= DETAIL_DISTANCE_SQUARED;

        if (!state.focus().installed()) {
            renderEmpty(pose, buffers, age, close);
            return;
        }

        renderFunctional(pylon.getBlockPos(), state, pose, buffers, age, close);
    }

    private static void renderEmpty(PoseStack pose, MultiBufferSource buffers,
                                    double age, boolean close) {
        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        int dormant = AscendancePalette.LATENT.primaryRgb();
        double phase = age * 0.003;

        // A low, incomplete socket registration reads as machinery awaiting its catalyst.
        ProceduralGeometry.brokenRing(pose, lines, CENTER, X, Z, 0.51,
                phase, TAU, 4, 4, 0.58, dormant, 0.13F);
        if (close) {
            ProceduralGeometry.spokes(pose, lines, CENTER, X, Z,
                    0.43, 0.53, 4, Math.PI * 0.25, dormant, 0.10F);
        }
    }

    private static void renderFunctional(BlockPos pos, MachineVisualState.Pylon state,
                                         PoseStack pose, MultiBufferSource buffers,
                                         double age, boolean close) {
        MachineVisualState.Focus focus = state.focus();
        int rgb = FocusVisuals.color(focus.tier());
        int luminous = towardWhite(rgb, 0.42);
        int tier = tierLevel(focus.tier());
        double tierProgress = tier / 5.0;
        double throughput = throughput(focus.ratePerSecond());
        double activity = focus.active() ? 0.55 + throughput * 0.45 : 0.0;
        double speed = 0.010 + (state.linked() ? 0.004 : 0.0) + activity * 0.030;
        double phase = age * speed;
        double counterPhase = -age * (0.006 + activity * 0.018);

        // A BufferSource can close the current builder when another RenderType is requested.
        // Finish every plane emission before acquiring the depth-line consumer.
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        renderPrimaryDiscs(pose, planes, phase, rgb, luminous, tierProgress, activity);
        renderOrbitNodes(pose, planes, phase, rgb, luminous, tier, focus.active());
        if (state.linked()) {
            renderAnchorPlane(pos, state, pose, planes, luminous, focus.active());
        }

        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        renderPrimaryRings(pose, lines, phase, rgb, tierProgress, state.linked(), activity);
        renderPulse(pose, lines, age, rgb, luminous, focus.active(), throughput);
        if (state.linked()) {
            renderAnchorLine(pos, state, pose, lines, luminous, focus.active());
        }
        if (focus.active()) {
            renderEnergyTransfer(pose, lines, phase, luminous, activity);
        }
        if (close) {
            renderFineDetail(pose, lines, phase, counterPhase, rgb, tier,
                    state.linked(), focus.active());
        }
    }

    private static void renderPrimaryDiscs(PoseStack pose, VertexConsumer planes, double phase,
                                           int rgb, int luminous, double tier, double activity) {
        double outer = 0.78 + tier * 0.08;
        double inner = 0.55 + tier * 0.025;
        float bodyOpacity = (float) (0.16 + activity * 0.10);
        float edgeOpacity = (float) (0.11 + activity * 0.08);

        ProceduralGeometry.annulus(pose, planes, CENTER, X, Z,
                inner, outer, 32, phase * 0.16, rgb, bodyOpacity);
        ProceduralGeometry.annulus(pose, planes, UPPER_CENTER, X, Z,
                0.43, 0.50 + tier * 0.035, 24, -phase * 0.22,
                luminous, edgeOpacity);
    }

    private static void renderPrimaryRings(PoseStack pose, VertexConsumer lines, double phase,
                                           int rgb, double tier, boolean linked, double activity) {
        double outerRadius = 0.80 + tier * 0.08;
        double outerGap = Math.max(0.045, 0.22 - tier * 0.10
                - (linked ? 0.035 : 0.0) - activity * 0.055);
        float outerOpacity = (float) (0.54 + activity * 0.28);

        ProceduralGeometry.brokenRing(pose, lines, CENTER, X, Z, outerRadius,
                phase, TAU, 4, 8, outerGap, rgb, outerOpacity);
        ProceduralGeometry.brokenRing(pose, lines, UPPER_CENTER, X, Z,
                0.51 + tier * 0.035, -phase * 0.72, TAU, 3, 8,
                Math.max(0.06, 0.28 - tier * 0.12 - activity * 0.08),
                rgb, (float) (0.43 + activity * 0.30));
    }

    private static void renderOrbitNodes(PoseStack pose, VertexConsumer planes, double phase,
                                         int rgb, int luminous, int tier, boolean active) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f cameraUp = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        Vec3 up = new Vec3(cameraUp.x(), cameraUp.y(), cameraUp.z());
        int count = 3 + (tier >= 3 ? 1 : 0);

        for (int index = 0; index < count; index++) {
            double angle = ProceduralMotion.orbitAngle(index, count, phase * 1.35);
            Vec3 center = ProceduralGeometry.orbitPoint(UPPER_CENTER, X, Z,
                    0.58 + tier * 0.012, 0.58 + tier * 0.012, angle)
                    .add(0, ProceduralMotion.oscillate(ageOffset(phase, index), 0.025), 0);
            ProceduralGeometry.diamondRing(pose, planes, center, right, up,
                    0.052, 0.078, 0.030, 0.047, rgb, active ? 0.38F : 0.26F);
            ProceduralGeometry.diamond(pose, planes, center, right, up,
                    0.030, 0.047, luminous, active ? 0.86F : 0.68F);
        }
    }

    private static double ageOffset(double phase, int index) {
        return phase * 5.0 + index * 1.9;
    }

    private static void renderPulse(PoseStack pose, VertexConsumer lines, double age,
                                    int rgb, int luminous, boolean active, double throughput) {
        double rate = active ? 0.026 + throughput * 0.034 : 0.010;
        int pulses = active ? 2 : 1;
        for (int index = 0; index < pulses; index++) {
            double progress = ProceduralMotion.phase(age + index / (double) pulses / rate, rate);
            double radius = 0.53 + progress * (active ? 0.60 : 0.34);
            float opacity = (float) ((active ? 0.36 : 0.18) * (1.0 - progress));
            ProceduralGeometry.ring(pose, lines, new Vec3(0.5, 1.17, 0.5), X, Z,
                    radius, 32, index == 0 ? luminous : rgb, opacity);
        }
    }

    /** Stable upper-field origin reserved for the later Pylon-to-Crucible tether. */
    public static Vec3 tetherAnchor(BlockPos pylonPos, MachineVisualState.Pylon state) {
        if (state.linkedCrucible() == null) return UPPER_CENTER;
        double dx = state.linkedCrucible().getX() - pylonPos.getX();
        double dz = state.linkedCrucible().getZ() - pylonPos.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return UPPER_CENTER.add(0.57, 0.08, 0);
        return UPPER_CENTER.add(dx / length * 0.57, 0.08, dz / length * 0.57);
    }

    private static void renderAnchorPlane(BlockPos pos, MachineVisualState.Pylon state,
                                          PoseStack pose, VertexConsumer planes,
                                          int rgb, boolean active) {
        Vec3 anchor = tetherAnchor(pos, state);
        Vec3 radial = new Vec3(anchor.x - 0.5, 0, anchor.z - 0.5).normalize();
        Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
        ProceduralGeometry.diamondRing(pose, planes, anchor, tangent, Y,
                0.075, 0.105, 0.039, 0.061, rgb, active ? 0.42F : 0.28F);
        ProceduralGeometry.diamond(pose, planes, anchor, tangent, Y,
                0.039, 0.061, rgb, active ? 0.78F : 0.58F);
    }

    private static void renderAnchorLine(BlockPos pos, MachineVisualState.Pylon state,
                                         PoseStack pose, VertexConsumer lines,
                                         int rgb, boolean active) {
        Vec3 anchor = tetherAnchor(pos, state);
        ProceduralGeometry.line(pose, lines, UPPER_CENTER, anchor, rgb, active ? 0.52F : 0.31F);
    }

    private static void renderEnergyTransfer(PoseStack pose, VertexConsumer lines,
                                             double phase, int rgb, double activity) {
        for (int index = 0; index < 4; index++) {
            double angle = phase * 0.45 + index * Math.PI * 0.5;
            Vec3 root = ProceduralGeometry.orbitPoint(UPPER_CENTER, X, Z,
                    0.38, 0.38, angle);
            Vec3 tip = new Vec3(0.5, 1.44, 0.5).lerp(root, 0.34);
            ProceduralGeometry.line(pose, lines, root, tip, rgb,
                    (float) (0.24 + activity * 0.22));
        }
    }

    private static void renderFineDetail(PoseStack pose, VertexConsumer lines,
                                         double phase, double counterPhase, int rgb, int tier,
                                         boolean linked, boolean active) {
        double tierProgress = tier / 5.0;
        float faint = active ? 0.24F : 0.16F;
        int ticks = 12 + tier * 2;

        ProceduralGeometry.ring(pose, lines, new Vec3(0.5, 1.02, 0.5), X, Z,
                0.63 + tierProgress * 0.04, 40, rgb, faint);
        ProceduralGeometry.brokenRing(pose, lines, new Vec3(0.5, 1.11, 0.5), X, Z,
                0.37 + tierProgress * 0.03, counterPhase, TAU, 6, 5,
                0.34 - tierProgress * 0.18, rgb, faint * 0.82F);
        ProceduralGeometry.spokes(pose, lines, CENTER, X, Z,
                0.67 + tierProgress * 0.04, 0.73 + tierProgress * 0.06,
                ticks, counterPhase * 0.25, rgb, faint * 0.86F);

        for (int index = 0; index < 4; index++) {
            double angle = Math.PI * 0.25 + index * Math.PI * 0.5;
            Vec3 lower = ProceduralGeometry.orbitPoint(CENTER, X, Z, 0.43, 0.43, angle);
            Vec3 upper = new Vec3(lower.x, linked ? 1.28 : 1.20, lower.z);
            ProceduralGeometry.line(pose, lines, lower, upper, rgb, faint * 0.72F);
        }
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
