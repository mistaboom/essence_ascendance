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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Tier-graded cylinder panels contained inside the Pylon's one-block XZ footprint. */
public final class PylonVisuals {
    public static final int VIEW_DISTANCE = 72;
    private static final double DETAIL_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double TAU = Math.PI * 2.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    // Both registrations are exactly 0.25 below the former disc centers.
    private static final Vec3 CENTER = new Vec3(0.5, 0.82, 0.5);
    private static final Vec3 UPPER_CENTER = new Vec3(0.5, 1.05, 0.5);
    private static final double SENDER_RADIUS = 0.36;

    private PylonVisuals() { }

    public static void render(EssencePylonBlockEntity pylon, float partialTick,
                              PoseStack pose, MultiBufferSource buffers) {
        Level level = pylon.getLevel();
        if (level == null) return;
        MachineVisualState.Pylon state = pylon.visualState();
        if (!state.linked()) return;
        double age = level.getGameTime() + partialTick;
        boolean close = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(Vec3.atCenterOf(pylon.getBlockPos())) <= DETAIL_DISTANCE_SQUARED;
        if (!state.focus().installed()) {
            renderEmpty(pose, buffers, age, close);
            return;
        }
        renderFunctional(pylon.getBlockPos(), state, pose, buffers, age, close);
    }

    private static void renderEmpty(PoseStack pose, MultiBufferSource buffers, double age, boolean close) {
        int gray = AscendancePalette.LATENT.primaryRgb();
        double phase = age * 0.003;
        // Three short socket panels, widely separated and distinct from copper Latent.
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        shell(pose, planes, false, 0.37, CENTER.y, CENTER.y + 0.065,
                3, 0.23, 3, phase, gray, gray, 0.10F);
        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        shell(pose, lines, true, 0.37, CENTER.y, CENTER.y + 0.065,
                3, 0.23, 3, phase, gray, gray, 0.25F);
        if (close) ProceduralGeometry.spokes(pose, lines, CENTER, X, Z,
                0.34, 0.40, 3, phase, gray, 0.16F);
    }

    private static void renderFunctional(BlockPos pos, MachineVisualState.Pylon state,
                                         PoseStack pose, MultiBufferSource buffers,
                                         double age, boolean close) {
        MachineVisualState.Focus focus = state.focus();
        int tier = tierLevel(focus.tier());
        double refinement = tier / 5.0;
        int rgb = FocusVisuals.color(focus.tier());
        int luminous = towardWhite(rgb, 0.16 + refinement * 0.36);
        double rate = focus.ratePerSecond() <= 0 ? 0
                : Math.min(1, Math.log1p(focus.ratePerSecond()) / 12.0);
        double activity = focus.active() ? 0.55 + rate * 0.45 : 0;
        double phase = age * (0.006 + refinement * 0.004 + activity * 0.009);
        double radius = 0.40 + refinement * 0.045;
        double top = CENTER.y + 0.13 + refinement * 0.10;
        int panels = 3 + tier;
        double coverage = 0.32 + refinement * 0.46;
        float bodyAlpha = (float) (0.09 + refinement * 0.14 + activity * 0.045);
        float edgeAlpha = (float) (0.29 + refinement * 0.30 + activity * 0.10);

        // Complete all planes before acquiring a line consumer.
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        shell(pose, planes, false, radius, CENTER.y, top, panels, coverage,
                4 + tier, phase, rgb, luminous, bodyAlpha);
        if (tier >= 2) shell(pose, planes, false, radius - 0.055, top - 0.045, top + 0.045,
                3 + tier / 2, 0.30 + refinement * 0.35, 5 + tier,
                -phase * 0.72, rgb, luminous, bodyAlpha * 0.72F);
        if (tier >= 1) orbitNodes(pose, planes, phase, tier, rgb, luminous, focus.active());
        renderAnchorPlane(pos, state, pose, planes, luminous, focus.active());

        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        shell(pose, lines, true, radius, CENTER.y, top, panels, coverage,
                4 + tier, phase, rgb, luminous, edgeAlpha);
        if (tier >= 2) shell(pose, lines, true, radius - 0.055, top - 0.045, top + 0.045,
                3 + tier / 2, 0.30 + refinement * 0.35, 5 + tier,
                -phase * 0.72, rgb, luminous, edgeAlpha * 0.70F);
        if (tier >= 1) verticalPulse(pose, lines, age, radius + 0.008, top, tier,
                rgb, luminous, focus.active(), rate);
        ProceduralGeometry.line(pose, lines, UPPER_CENTER, tetherAnchor(pos, state),
                luminous, focus.active() ? 0.52F : 0.31F);
        if (close) fineDetail(pose, lines, phase, radius, top, tier, rgb, luminous, activity);
    }

    /** Open-ended curved panels: beveled corners, three shaded height bands, no filled discs. */
    private static void shell(PoseStack pose, VertexConsumer out, boolean edges,
                               double radius, double bottom, double top, int count,
                               double coverage, int segments, double phase,
                               int rgb, int luminous, float alpha) {
        double slice = TAU / count;
        double sweep = slice * coverage;
        for (int panel = 0; panel < count; panel++) {
            double start = phase + slice * panel;
            for (int row = 0; row < 3; row++) {
                double insetA = row == 0 ? sweep * 0.09 : 0;
                double insetB = row == 2 ? sweep * 0.09 : 0;
                double yA = bottom + (top - bottom) * row / 3.0;
                double yB = bottom + (top - bottom) * (row + 1) / 3.0;
                int color = row == 2 ? luminous : rgb;
                float opacity = alpha * (0.68F + row * 0.16F);
                for (int segment = 0; segment < segments; segment++) {
                    double t0 = segment / (double) segments;
                    double t1 = (segment + 1) / (double) segments;
                    Vec3 a = cylinderPoint(radius, yA, start + insetA + (sweep - 2 * insetA) * t0);
                    Vec3 b = cylinderPoint(radius, yA, start + insetA + (sweep - 2 * insetA) * t1);
                    Vec3 c = cylinderPoint(radius, yB, start + insetB + (sweep - 2 * insetB) * t1);
                    Vec3 d = cylinderPoint(radius, yB, start + insetB + (sweep - 2 * insetB) * t0);
                    if (!edges) {
                        ProceduralGeometry.quad(pose, out, a, b, c, d, color, opacity);
                    } else {
                        if (row == 0) ProceduralGeometry.line(pose, out, a, b, rgb, alpha * 0.72F);
                        if (row == 2) ProceduralGeometry.line(pose, out, d, c, luminous, alpha);
                        if (segment == 0) ProceduralGeometry.line(pose, out, a, d, color, opacity);
                        if (segment == segments - 1) ProceduralGeometry.line(pose, out, b, c, color, opacity);
                    }
                }
            }
        }
    }

    private static Vec3 cylinderPoint(double radius, double y, double angle) {
        return new Vec3(0.5 + Math.cos(angle) * radius, y, 0.5 + Math.sin(angle) * radius);
    }

    private static void orbitNodes(PoseStack pose, VertexConsumer planes, double phase,
                                   int tier, int rgb, int luminous, boolean active) {
        // Tangential upright facets stay inside the footprint at every orbit angle.
        for (int index = 0; index < tier; index++) {
            double angle = ProceduralMotion.orbitAngle(index, tier, phase * 1.35);
            Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            Vec3 center = cylinderPoint(0.33, UPPER_CENTER.y +
                    ProceduralMotion.oscillate(phase * 5 + index * 1.9, 0.025), angle);
            ProceduralGeometry.diamondRing(pose, planes, center, tangent, Y,
                    0.035, 0.053, 0.020, 0.031, rgb,
                    (active ? 0.26F : 0.15F) + tier * 0.026F);
            ProceduralGeometry.diamond(pose, planes, center, tangent, Y,
                    0.020, 0.031, luminous, (active ? 0.48F : 0.30F) + tier * 0.06F);
        }
    }

    private static void verticalPulse(PoseStack pose, VertexConsumer lines, double age,
                                       double radius, double top, int tier, int rgb, int luminous,
                                       boolean active, double throughput) {
        double speed = active ? 0.026 + throughput * 0.034 : 0.010;
        int count = active && tier >= 4 ? 2 : 1;
        for (int index = 0; index < count; index++) {
            double progress = ProceduralMotion.phase(age + index / (double) count / speed, speed);
            double y = CENTER.y + progress * (top - CENTER.y + 0.04);
            float alpha = (float) ((active ? 0.20 + tier * 0.035 : 0.09 + tier * 0.02)
                    * Math.sin(progress * Math.PI));
            ProceduralGeometry.brokenRing(pose, lines, new Vec3(0.5, y, 0.5), X, Z,
                    radius, 0, TAU, 3 + tier, 5, 0.42 - tier * 0.045,
                    index == 0 ? luminous : rgb, alpha);
        }
    }

    private static void fineDetail(PoseStack pose, VertexConsumer lines, double phase,
                                   double radius, double top, int tier, int rgb, int luminous,
                                   double activity) {
        float faint = (float) (0.09 + tier * 0.025 + activity * 0.04);
        ProceduralGeometry.brokenRing(pose, lines, CENTER.add(0, 0.035, 0), X, Z,
                radius - 0.035, -phase, TAU, 3 + tier, 4,
                0.64 - tier * 0.085, rgb, faint);
        if (tier >= 2) ProceduralGeometry.spokes(pose, lines, new Vec3(0.5, top, 0.5), X, Z,
                radius - 0.015, radius + 0.023, 4 + tier * 2, -phase * 0.25, luminous, faint);
        if (tier >= 3) {
            for (int i = 0; i < tier; i++) {
                double angle = phase + TAU * i / tier;
                Vec3 a = cylinderPoint(radius - 0.025, CENTER.y + 0.06, angle);
                Vec3 b = cylinderPoint(radius - 0.025, top - 0.025, angle + 0.14);
                ProceduralGeometry.line(pose, lines, a, b, luminous, faint * 0.72F);
            }
        }
        if (tier >= 4) ProceduralGeometry.brokenRing(pose, lines, CENTER.add(0, -0.02, 0), X, Z,
                radius - 0.015, phase * 0.60, TAU, 8, 4,
                tier == 5 ? 0.08 : 0.28, rgb, faint * 0.75F);
    }

    /** Shared compact sender; moving it also updates every attached tether. */
    public static Vec3 tetherAnchor(BlockPos pylonPos, MachineVisualState.Pylon state) {
        if (state.linkedCrucible() == null) return UPPER_CENTER;
        double dx = state.linkedCrucible().getX() - pylonPos.getX();
        double dz = state.linkedCrucible().getZ() - pylonPos.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return UPPER_CENTER.add(SENDER_RADIUS, 0.08, 0);
        return UPPER_CENTER.add(dx / length * SENDER_RADIUS, 0.08, dz / length * SENDER_RADIUS);
    }

    private static void renderAnchorPlane(BlockPos pos, MachineVisualState.Pylon state,
                                          PoseStack pose, VertexConsumer planes, int rgb, boolean active) {
        Vec3 anchor = tetherAnchor(pos, state);
        Vec3 radial = new Vec3(anchor.x - 0.5, 0, anchor.z - 0.5).normalize();
        Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
        ProceduralGeometry.diamondRing(pose, planes, anchor, tangent, Y,
                0.075, 0.105, 0.039, 0.061, rgb, active ? 0.42F : 0.28F);
        ProceduralGeometry.diamond(pose, planes, anchor, tangent, Y,
                0.039, 0.061, rgb, active ? 0.78F : 0.58F);
    }

    private static int tierLevel(EssenceFocusTier tier) {
        return tier == null ? 0 : tier.ordinal() + 1;
    }

    private static int towardWhite(int rgb, double amount) {
        int r = (int) Math.round((rgb >> 16 & 255) * (1.0 - amount) + 255 * amount);
        int g = (int) Math.round((rgb >> 8 & 255) * (1.0 - amount) + 255 * amount);
        int b = (int) Math.round((rgb & 255) * (1.0 - amount) + 255 * amount);
        return r << 16 | g << 8 | b;
    }
}
