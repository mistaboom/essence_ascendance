package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleEssences;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mistaboom.essence_ascendance.visual.ProceduralColors;
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

import java.util.UUID;

/** Persistent, state-driven dissolution fire inside and above the Essence Crucible. */
public final class CrucibleVisuals {
    public static final int VIEW_DISTANCE = 72;

    private static final double DETAIL_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double TAU = Math.PI * 2.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final Vec3 BOWL_SURFACE = new Vec3(0.5, 0.70, 0.5);

    /*
     * Reserved future tether origins sit on the four diagonal shoulders. Keeping
     * them away from the cardinal axes and the vertical center bore preserves
     * item-transfer approaches through every block face without collision work.
     */
    private static final Vec3[] CORNER_ANCHORS = {
            new Vec3(0.27, 1.03, 0.27),
            new Vec3(0.73, 1.03, 0.27),
            new Vec3(0.73, 1.03, 0.73),
            new Vec3(0.27, 1.03, 0.73)
    };

    private CrucibleVisuals() { }

    public static void render(EssenceCrucibleBlockEntity crucible, float partialTick,
                              PoseStack pose, MultiBufferSource buffers) {
        Level level = crucible.getLevel();
        if (level == null) return;

        MachineVisualState.Crucible state = crucible.visualState();
        double age = level.getGameTime() + partialTick;
        boolean close = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(Vec3.atCenterOf(crucible.getBlockPos())) <= DETAIL_DISTANCE_SQUARED;

        double fill = Math.clamp(state.reservoirFillBasisPoints() / 10_000.0, 0.0, 1.0);
        double throughput = throughput(state.transferRatePerSecond());
        int networkLevel = Math.min(5, state.activePylons().size());
        double refinement = networkLevel / 5.0;
        boolean loaded = !state.inputItems().isEmpty();
        double active = state.active() ? 0.58 + throughput * 0.42 : 0.0;
        double speed = 0.010 + (loaded ? 0.003 : 0.0) + fill * 0.004
                + (state.dissolving() ? 0.027 : 0.0) + (state.channeling() ? 0.013 : 0.0)
                + throughput * 0.020;
        double phase = age * speed;
        int accent = accentColor(state);
        int heat = ProceduralColors.accent(AscendancePalette.RESONANT.metalRgb());
        int core = towardWhite(accent, state.channeling() ? 0.68 : 0.52);

        // A BufferSource may close its current builder when the render type changes.
        // Complete all translucent plane work before acquiring the depth-line pass.
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        renderBowlGlow(pose, planes, age, accent, heat, fill, active, refinement);
        renderMajorRibbons(pose, planes, phase, accent, heat, state, fill, throughput,
                networkLevel, refinement);
        renderEmberShards(pose, planes, age, phase, accent, core, state, throughput,
                networkLevel, refinement);
        renderFlarePlanes(pose, planes, age, accent, heat, state, fill, throughput,
                networkLevel, refinement);
        if (close) {
            renderDetailPlanes(pose, planes, phase, accent, core, state,
                    networkLevel, refinement);
        }

        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        renderPrimaryLines(pose, lines, age, phase, accent, core, state, fill, throughput,
                networkLevel, refinement);
        if (close) {
            renderFineDetail(pose, lines, age, phase, accent, core, state, fill, throughput,
                    networkLevel, refinement);
        }
    }

    private static void renderBowlGlow(PoseStack pose, VertexConsumer planes, double age,
                                       int accent, int heat, double fill, double active,
                                       double refinement) {
        float innerOpacity = (float) (0.08 + refinement * 0.16 + fill * 0.10 + active * 0.12
                + ProceduralMotion.oscillate(age * 0.07, 0.018));
        float rimOpacity = (float) (0.12 + refinement * 0.20 + fill * 0.11 + active * 0.13);
        double phase = age * (0.004 + active * 0.007);

        // The open center is the top/bottom transport corridor; broad glow stays annular.
        ProceduralGeometry.annulus(pose, planes, BOWL_SURFACE, X, Z,
                0.105, 0.285, 24, phase, heat, innerOpacity);
        ProceduralGeometry.annulus(pose, planes, BOWL_SURFACE.add(0, 0.008, 0), X, Z,
                0.285, 0.385, 28, -phase * 0.72, accent, rimOpacity);
    }

    private static void renderMajorRibbons(PoseStack pose, VertexConsumer planes, double phase,
                                           int accent, int heat, MachineVisualState.Crucible state,
                                           double fill, double throughput, int networkLevel,
                                           double refinement) {
        int count = 1 + (networkLevel >= 1 ? 1 : 0) + (networkLevel >= 3 ? 1 : 0)
                + (fill > 0.20 ? 1 : 0) + (state.dissolving() ? 1 : 0)
                + (state.channeling() ? 1 : 0);
        double height = 0.55 + fill * 0.16 + (state.dissolving() ? 0.43 : 0.0)
                + (state.channeling() ? 0.20 : 0.0) + throughput * 0.24;
        float opacity = (float) (0.10 + refinement * 0.18 + fill * 0.05
                + (state.active() ? 0.14 : 0.0));

        for (int index = 0; index < count; index++) {
            double rootAngle = Math.PI * 0.25 + TAU * index / count + phase * 0.36;
            double curl = (index % 2 == 0 ? 1.0 : -1.0)
                    * (0.72 + (state.dissolving() ? 0.32 : 0.0));
            double individualHeight = height * (0.86 + 0.08 * (index % 3));
            ribbonPlane(pose, planes, rootAngle, curl, individualHeight,
                    0.072, accent, opacity);
            ribbonPlane(pose, planes, rootAngle + 0.035, curl * 0.92,
                    individualHeight * 0.91, 0.030, heat, opacity * 0.82F);
        }
    }

    private static void ribbonPlane(PoseStack pose, VertexConsumer planes, double rootAngle,
                                    double curl, double height, double rootWidth,
                                    int rgb, float opacity) {
        int segments = 4;
        Vec3 previous = ribbonPoint(rootAngle, curl, height, 0.0);
        double previousWidth = rootWidth;
        for (int segment = 1; segment <= segments; segment++) {
            double t = segment / (double) segments;
            Vec3 next = ribbonPoint(rootAngle, curl, height, t);
            double angle = rootAngle + curl * t * t;
            Vec3 lateral = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            double width = rootWidth * (1.0 - t * 0.78);
            ProceduralGeometry.quad(pose, planes,
                    previous.subtract(lateral.scale(previousWidth)),
                    previous.add(lateral.scale(previousWidth)),
                    next.add(lateral.scale(width)),
                    next.subtract(lateral.scale(width)), rgb, opacity);
            previous = next;
            previousWidth = width;
        }
    }

    private static Vec3 ribbonPoint(double rootAngle, double curl, double height, double t) {
        double eased = ProceduralMotion.smoothStep(t);
        double angle = rootAngle + curl * t * t;
        // Radius never reaches the central top/bottom access line.
        double radius = 0.245 - eased * 0.075 + Math.sin(t * Math.PI) * 0.025;
        return new Vec3(0.5 + Math.cos(angle) * radius,
                BOWL_SURFACE.y + 0.015 + height * t,
                0.5 + Math.sin(angle) * radius);
    }

    private static void renderEmberShards(PoseStack pose, VertexConsumer planes, double age,
                                          double phase, int accent, int core,
                                          MachineVisualState.Crucible state, double throughput,
                                          int networkLevel, double refinement) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f cameraUp = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        Vec3 up = new Vec3(cameraUp.x(), cameraUp.y(), cameraUp.z());
        int count = networkLevel == 0 ? (state.active() ? 2 : 1)
                : Math.min(7, 1 + networkLevel + (state.active() ? 1 : 0)
                + (throughput > 0.62 ? 1 : 0));

        for (int index = 0; index < count; index++) {
            double progress = ProceduralMotion.phase(age + index * 13.0 / count,
                    state.active() ? 0.019 + throughput * 0.017 : 0.008);
            double angle = phase * 1.7 + TAU * index / count + progress * Math.PI * 0.9;
            double radius = 0.24 + 0.055 * Math.sin(progress * Math.PI);
            Vec3 center = new Vec3(0.5 + Math.cos(angle) * radius,
                    0.82 + progress * (state.active() ? 0.92 : 0.48),
                    0.5 + Math.sin(angle) * radius);
            double size = (state.active() ? 0.046 : 0.034) * (1.0 - progress * 0.32);
            ProceduralGeometry.diamondRing(pose, planes, center, right, up,
                    size * 1.55, size * 2.05, size, size * 1.35,
                    accent, (float) (0.10 + refinement * 0.24 + (state.active() ? 0.10 : 0.0)));
            ProceduralGeometry.diamond(pose, planes, center, right, up,
                    size, size * 1.35, core,
                    (float) (0.30 + refinement * 0.38 + (state.active() ? 0.18 : 0.0)));
        }
    }

    private static void renderFlarePlanes(PoseStack pose, VertexConsumer planes, double age,
                                          int accent, int heat, MachineVisualState.Crucible state,
                                          double fill, double throughput, int networkLevel,
                                          double refinement) {
        if (networkLevel == 0 && !state.active()) return;
        double rate = state.active() ? 0.019 + throughput * 0.026 : 0.0045 + fill * 0.002;
        int pulses = state.dissolving() && throughput > 0.48 && networkLevel >= 4 ? 2 : 1;
        for (int index = 0; index < pulses; index++) {
            double progress = ProceduralMotion.phase(age + index / (double) pulses / rate, rate);
            double eased = ProceduralMotion.smoothStep(progress);
            double inner = 0.205 + eased * (state.active() ? 0.17 : 0.08);
            float opacity = (float) ((state.active() ? 0.16 + refinement * 0.16
                    : 0.06 + refinement * 0.10)
                    * (1.0 - progress) * (0.65 + fill * 0.35));
            ProceduralGeometry.annulus(pose, planes,
                    BOWL_SURFACE.add(0, 0.10 + eased * (state.active() ? 0.54 : 0.24), 0),
                    X, Z, inner, inner + 0.045, 20, index * 0.31 + age * 0.003,
                    index == 0 ? heat : accent, opacity);
        }
    }

    private static void renderDetailPlanes(PoseStack pose, VertexConsumer planes, double phase,
                                           int accent, int core, MachineVisualState.Crucible state,
                                           int networkLevel, double refinement) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f cameraUp = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        Vec3 up = new Vec3(cameraUp.x(), cameraUp.y(), cameraUp.z());
        int count = Math.min(5, networkLevel + (state.active() && networkLevel > 0 ? 1 : 0));
        for (int index = 0; index < count; index++) {
            double angle = phase * -0.7 + TAU * index / count;
            Vec3 center = ProceduralGeometry.orbitPoint(
                    BOWL_SURFACE.add(0, 0.12 + index * 0.035, 0), X, Z,
                    0.335, 0.335, angle);
            ProceduralGeometry.diamond(pose, planes, center, right, up,
                    0.018, 0.026, index % 2 == 0 ? accent : core,
                    (float) (0.10 + refinement * 0.20 + (state.active() ? 0.08 : 0.0)));
        }
    }

    private static void renderPrimaryLines(PoseStack pose, VertexConsumer lines,
                                           double age, double phase, int accent, int core,
                                           MachineVisualState.Crucible state, double fill,
                                           double throughput, int networkLevel,
                                           double refinement) {
        float strength = (float) (0.22 + refinement * 0.34 + (state.active() ? 0.18 : 0.0));
        double height = 0.56 + fill * 0.15 + (state.dissolving() ? 0.44 : 0.0)
                + (state.channeling() ? 0.20 : 0.0) + throughput * 0.24;
        int count = 1 + (networkLevel >= 1 ? 1 : 0) + (networkLevel >= 3 ? 1 : 0)
                + (fill > 0.20 ? 1 : 0) + (state.dissolving() ? 1 : 0)
                + (state.channeling() ? 1 : 0);

        ProceduralGeometry.ring(pose, lines, BOWL_SURFACE.add(0, 0.016, 0), X, Z,
                0.385, 32, accent, strength);
        for (int index = 0; index < count; index++) {
            double rootAngle = Math.PI * 0.25 + TAU * index / count + phase * 0.36;
            double curl = (index % 2 == 0 ? 1.0 : -1.0)
                    * (0.72 + (state.dissolving() ? 0.32 : 0.0));
            ribbonContour(pose, lines, rootAngle, curl,
                    height * (0.86 + 0.08 * (index % 3)), accent, strength);
        }

        if (networkLevel >= 1 || state.active()) {
            double helixHeight = state.active() ? 0.84 + throughput * 0.28 : 0.43 + fill * 0.12;
            ProceduralGeometry.helix(pose, lines, BOWL_SURFACE.add(0, 0.045, 0),
                    Y.scale(helixHeight), X, Z, 0.225,
                    state.active() ? 1.28 + throughput * 0.38 : 0.68,
                    state.active() ? 18 + networkLevel * 2 : 10 + networkLevel,
                    core, (float) (0.14 + refinement * 0.20 + (state.active() ? 0.18 : 0.0)));
        }

        if (networkLevel >= 2 || state.active()) {
            double pulse = ProceduralMotion.phase(age, state.active()
                    ? 0.024 + throughput * 0.030 : 0.006);
            ProceduralGeometry.ring(pose, lines,
                    BOWL_SURFACE.add(0, 0.08 + pulse * (state.active() ? 0.56 : 0.22), 0),
                    X, Z, 0.23 + pulse * 0.16, 18 + networkLevel, core,
                    (float) ((0.08 + refinement * 0.18 + (state.active() ? 0.18 : 0.0))
                            * (1.0 - pulse)));
        }
    }

    private static void ribbonContour(PoseStack pose, VertexConsumer lines, double rootAngle,
                                      double curl, double height, int rgb, float opacity) {
        Vec3 previous = ribbonPoint(rootAngle, curl, height, 0.0);
        for (int segment = 1; segment <= 8; segment++) {
            Vec3 next = ribbonPoint(rootAngle, curl, height, segment / 8.0);
            ProceduralGeometry.line(pose, lines, previous, next, rgb, opacity);
            previous = next;
        }
    }

    private static void renderFineDetail(PoseStack pose, VertexConsumer lines,
                                         double age, double phase, int accent, int core,
                                         MachineVisualState.Crucible state, double fill,
                                         double throughput, int networkLevel,
                                         double refinement) {
        float faint = (float) (0.06 + refinement * 0.14 + (state.active() ? 0.05 : 0.0));
        double reverse = -phase * 0.63;
        int ribbonCount = (networkLevel >= 2 ? 1 : 0) + (networkLevel >= 4 ? 1 : 0)
                + (fill > 0.30 ? 1 : 0) + (state.dissolving() ? 1 : 0)
                + (state.channeling() ? 1 : 0);
        double ribbonHeight = 0.51 + fill * 0.13 + (state.dissolving() ? 0.36 : 0.0)
                + (state.channeling() ? 0.16 : 0.0) + throughput * 0.19;

        // Fine counter-curves sit inside the broad flame planes and disappear with close-detail LOD.
        for (int index = 0; index < ribbonCount; index++) {
            double rootAngle = Math.PI * 0.25 + TAU * index / ribbonCount + phase * 0.36 + 0.028;
            double curl = (index % 2 == 0 ? 1.0 : -1.0)
                    * (0.61 + (state.dissolving() ? 0.25 : 0.0));
            ribbonContour(pose, lines, rootAngle, curl,
                    ribbonHeight * (0.88 + 0.06 * (index % 3)), core, faint * 0.54F);
        }

        if (networkLevel >= 1) {
            ProceduralGeometry.brokenRing(pose, lines, BOWL_SURFACE.add(0, 0.028, 0), X, Z,
                    0.315, reverse, TAU, 6, 4,
                    Math.max(0.16, 0.58 - refinement * 0.38), accent, faint);
        }
        if (networkLevel >= 3) {
            ProceduralGeometry.spokes(pose, lines, BOWL_SURFACE.add(0, 0.033, 0), X, Z,
                    0.255, 0.345, 4 + networkLevel * 2, phase * 0.25, core, faint * 0.78F);
        }

        if (networkLevel >= 2) {
            ProceduralGeometry.helix(pose, lines, BOWL_SURFACE.add(0, 0.075, 0),
                    Y.scale(0.55 + (state.active() ? 0.32 : 0.0)), X, Z,
                    0.135, state.active() ? -1.45 : -0.82,
                    12 + networkLevel * 3, accent, faint * 0.82F);
        }
        if (networkLevel >= 4 && (state.dissolving() || fill > 0.15)) {
            ProceduralGeometry.helix(pose, lines, BOWL_SURFACE.add(0, 0.06, 0),
                    Y.scale(0.46 + throughput * 0.24), X, Z,
                    0.285, 0.72, 18, core, faint * 0.55F);
        }

        int anchorCount = Math.min(CORNER_ANCHORS.length, networkLevel);
        for (int index = 0; index < anchorCount; index++) {
            Vec3 anchor = CORNER_ANCHORS[index];
            Vec3 outward = new Vec3(anchor.x - 0.5, 0, anchor.z - 0.5).normalize();
            Vec3 tangent = new Vec3(-outward.z, 0, outward.x);
            double breathe = ProceduralMotion.oscillate(age * 0.035 + index, 0.008);
            Vec3 inner = anchor.add(outward.scale(-0.048 + breathe));
            Vec3 outer = anchor.add(outward.scale(0.038 + breathe));
            ProceduralGeometry.line(pose, lines,
                    inner.add(tangent.scale(-0.030)), outer, accent, faint * 0.72F);
            ProceduralGeometry.line(pose, lines,
                    outer, inner.add(tangent.scale(0.030)), accent, faint * 0.72F);
            ProceduralGeometry.line(pose, lines, anchor.add(0, -0.10, 0),
                    anchor.add(0, state.channeling() ? 0.16 : 0.09, 0), core, faint * 0.58F);
        }
    }

    /** Corner-biased origin for a later Pylon tether; this method draws no tether. */
    public static Vec3 pylonAnchor(BlockPos cruciblePos, BlockPos pylonPos) {
        double dx = pylonPos.getX() - cruciblePos.getX();
        double dz = pylonPos.getZ() - cruciblePos.getZ();
        int east = dx > 0 ? 1 : 0;
        int south = dz > 0 ? 1 : 0;
        if (Math.abs(dx) < 0.001) east = Math.floorMod(pylonPos.hashCode(), 2);
        if (Math.abs(dz) < 0.001) south = Math.floorMod(pylonPos.hashCode() / 2, 2);
        return CORNER_ANCHORS[south == 0 ? east : 3 - east];
    }

    /** Stable diagonal origin for a later player-channel tether; this method draws no tether. */
    public static Vec3 channelAnchor(UUID playerId) {
        return CORNER_ANCHORS[Math.floorMod(playerId.hashCode(), CORNER_ANCHORS.length)];
    }

    private static int accentColor(MachineVisualState.Crucible state) {
        int index = state.predominantEssenceIndex();
        if (index >= 0 && index < EssenceCrucibleEssences.ORDERED.size()) {
            return ProceduralColors.essence(EssenceCrucibleEssences.ORDERED.get(index).id());
        }
        return ProceduralColors.accent(AscendancePalette.RESONANT.metalRgb());
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
