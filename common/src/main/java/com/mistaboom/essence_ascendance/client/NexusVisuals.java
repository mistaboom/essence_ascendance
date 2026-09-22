package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusBlockEntity;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
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

/** Persistent world-space identity above the restrained Ascendance Nexus lectern. */
public final class NexusVisuals {
    public static final int VIEW_DISTANCE = 72;

    private static final double DETAIL_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double TAU = Math.PI * 2.0;
    private static final int LEAF_COUNT = 7;
    private static final int ORBIT_NODE_COUNT = 6;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final Vec3 CODEX_CENTER = new Vec3(0.5, 1.48, 0.5);
    private static final int[] CATEGORY_COLORS = {
            AscendancePalette.OFFENSE,
            AscendancePalette.DEFENSE,
            AscendancePalette.MOBILITY,
            AscendancePalette.UTILITY,
            AscendancePalette.VITALITY,
            AscendancePalette.GATHERING
    };

    private NexusVisuals() { }

    public static void render(AscendanceNexusBlockEntity nexus, float partialTick,
                              PoseStack pose, MultiBufferSource buffers) {
        Level level = nexus.getLevel();
        if (level == null) return;

        boolean active = nexus.visualState().inUse();
        double age = level.getGameTime() + partialTick;
        double localPhase = positionPhase(nexus.getBlockPos());
        double hover = ProceduralMotion.oscillate(age * (active ? 0.075 : 0.045) + localPhase,
                active ? 0.044 : 0.032);
        Vec3 center = CODEX_CENTER.add(0, hover, 0);
        double breathing = 0.5 + 0.5 * Math.sin(age * (active ? 0.052 : 0.028) + localPhase);
        double fanHalfAngle = (active ? 0.67 : 0.46) + breathing * (active ? 0.055 : 0.035);
        double orbitAngle = age * (active ? 0.019 : 0.0075) + localPhase;
        Flip flip = flip(age, localPhase, active);
        boolean close = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(Vec3.atCenterOf(nexus.getBlockPos())) <= DETAIL_DISTANCE_SQUARED;

        // A BufferSource can close the current builder when another RenderType is requested.
        // Emit every translucent plane before acquiring the depth-line consumer.
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        renderBaseConnectionPlanes(pose, planes, center, age, active);
        renderLeafPlanes(pose, planes, center, fanHalfAngle, flip, active);
        renderCorePlanes(pose, planes, center, age, active);
        renderOrbitNodePlanes(pose, planes, center, orbitAngle, age, active);
        if (close) {
            renderTravelingDetailPulses(pose, planes, center, orbitAngle, age, active);
        }

        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        renderBaseConnectionLines(pose, lines, center, age, active);
        renderLeafContours(pose, lines, center, fanHalfAngle, flip, active);
        renderCoreAndOrbitLines(pose, lines, center, orbitAngle, age, active);
        if (close) {
            renderFineDetail(pose, lines, center, fanHalfAngle, flip, orbitAngle, age, active);
        }
    }

    /** Low, physical-looking registration that visually seats the floating codex on the lectern. */
    private static void renderBaseConnectionPlanes(PoseStack pose, VertexConsumer planes,
                                                   Vec3 codexCenter, double age,
                                                   boolean active) {
        Vec3 surface = new Vec3(0.5, 1.018, 0.5);
        Vec3 upperHub = codexCenter.add(0, -0.245, 0);
        int shell = AscendancePalette.UTILITY;
        int core = AscendancePalette.TRANSCENDENT.metalRgb();
        double phase = age * (active ? 0.014 : 0.0045);

        ProceduralGeometry.annulus(pose, planes, surface, X, Z,
                0.175, active ? 0.325 : 0.295, 24, phase,
                shell, active ? 0.145F : 0.095F);
        ProceduralGeometry.diamond(pose, planes, surface.add(0, 0.035, 0), X, Z,
                active ? 0.115 : 0.095, active ? 0.115 : 0.095,
                core, active ? 0.30F : 0.20F);

        // Crossed central planes read as a narrow projected support, while the
        // four diagonal facets make contact with the authored top surface.
        ProceduralGeometry.beam(pose, planes, surface.add(0, 0.025, 0), upperHub,
                X, active ? 0.024 : 0.017, core, active ? 0.24F : 0.15F);
        ProceduralGeometry.beam(pose, planes, surface.add(0, 0.025, 0), upperHub,
                Z, active ? 0.024 : 0.017, shell, active ? 0.20F : 0.12F);

        for (int index = 0; index < 4; index++) {
            double angle = Math.PI * 0.25 + index * Math.PI * 0.5;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
            Vec3 anchor = surface.add(radial.scale(active ? 0.285 : 0.265)).add(0, 0.045, 0);
            ProceduralGeometry.diamondRing(pose, planes, anchor, tangent, Y,
                    0.042, 0.064, 0.023, 0.036,
                    index % 2 == 0 ? shell : core, active ? 0.34F : 0.23F);
            ProceduralGeometry.beam(pose, planes, anchor,
                    upperHub.add(radial.scale(0.055)), tangent,
                    active ? 0.017 : 0.012,
                    index % 2 == 0 ? shell : core, active ? 0.20F : 0.12F);
        }

        double pulse = ProceduralMotion.phase(age, active ? 0.032 : 0.012);
        Vec3 traveling = surface.add(0, 0.08 + pulse * Math.max(0.08, upperHub.y - surface.y - 0.10), 0);
        ProceduralGeometry.diamond(pose, planes, traveling, X, Z,
                active ? 0.040 : 0.030, active ? 0.040 : 0.030,
                towardWhite(core, 0.38), (float) ((active ? 0.52 : 0.30) * (1.0 - pulse * 0.42)));
    }

    private static void renderBaseConnectionLines(PoseStack pose, VertexConsumer lines,
                                                  Vec3 codexCenter, double age,
                                                  boolean active) {
        Vec3 surface = new Vec3(0.5, 1.024, 0.5);
        Vec3 lowerHub = surface.add(0, 0.055, 0);
        Vec3 upperHub = codexCenter.add(0, -0.245, 0);
        int shell = AscendancePalette.UTILITY;
        int core = AscendancePalette.TRANSCENDENT.metalRgb();
        double phase = age * (active ? 0.014 : 0.0045);

        ProceduralGeometry.brokenRing(pose, lines, surface, X, Z,
                active ? 0.325 : 0.295, phase, TAU, 4,
                active ? 6 : 4, active ? 0.24 : 0.43,
                shell, active ? 0.43F : 0.30F);
        ProceduralGeometry.line(pose, lines, lowerHub, upperHub,
                core, active ? 0.62F : 0.43F);

        for (int index = 0; index < 4; index++) {
            double angle = Math.PI * 0.25 + index * Math.PI * 0.5;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 anchor = surface.add(radial.scale(active ? 0.285 : 0.265)).add(0, 0.045, 0);
            Vec3 shoulder = upperHub.add(radial.scale(0.055));
            ProceduralGeometry.line(pose, lines, anchor, lowerHub,
                    index % 2 == 0 ? shell : core, active ? 0.38F : 0.25F);
            ProceduralGeometry.line(pose, lines, anchor, shoulder,
                    index % 2 == 0 ? shell : core, active ? 0.47F : 0.31F);
        }

        ProceduralGeometry.brokenRing(pose, lines, upperHub, X, Z,
                active ? 0.115 : 0.090, -phase * 1.4, TAU, 4, 3,
                active ? 0.28 : 0.48, core, active ? 0.45F : 0.29F);
    }

    private static void renderLeafPlanes(PoseStack pose, VertexConsumer planes, Vec3 center,
                                         double fanHalfAngle, Flip flip, boolean active) {
        int shell = AscendancePalette.UTILITY;
        int edge = AscendancePalette.TRANSCENDENT.metalRgb();
        for (int index = 0; index < LEAF_COUNT; index++) {
            Leaf leaf = leaf(index, center, fanHalfAngle, flip);
            float bodyAlpha = active ? 0.205F : 0.145F;
            float rimAlpha = active ? 0.34F : 0.245F;
            if (index == LEAF_COUNT / 2) {
                bodyAlpha += 0.045F;
                rimAlpha += 0.055F;
            }
            ProceduralGeometry.diamond(pose, planes, leaf.center(), leaf.right(), Y,
                    leaf.width(), leaf.height(), shell, bodyAlpha);
            ProceduralGeometry.diamondRing(pose, planes,
                    leaf.center().add(leaf.normal().scale(0.0035)), leaf.right(), Y,
                    leaf.width(), leaf.height(), leaf.width() - 0.018, leaf.height() - 0.020,
                    edge, rimAlpha);
        }
    }

    private static void renderCorePlanes(PoseStack pose, VertexConsumer planes, Vec3 center,
                                         double age, boolean active) {
        int core = towardWhite(AscendancePalette.TRANSCENDENT.metalRgb(), 0.54);
        int halo = AscendancePalette.TRANSCENDENT.metalRgb();
        double pulse = 1.0 + ProceduralMotion.oscillate(age * (active ? 0.13 : 0.075),
                active ? 0.085 : 0.052);
        double width = 0.090 * pulse;
        double height = 0.205 * pulse;

        // Three crossed faceted planes retain a luminous core read from every approach.
        ProceduralGeometry.diamond(pose, planes, center.add(0, 0.005, 0), X, Y,
                width, height, halo, active ? 0.52F : 0.40F);
        ProceduralGeometry.diamond(pose, planes, center.add(0, 0.005, 0), Z, Y,
                width, height, halo, active ? 0.52F : 0.40F);
        Vec3 diagonal = new Vec3(1, 0, 1).normalize();
        ProceduralGeometry.diamond(pose, planes, center.add(0, 0.005, 0), diagonal, Y,
                width * 0.70, height * 0.82, core, active ? 0.72F : 0.60F);

        ProceduralGeometry.diamond(pose, planes, center.add(0, height + 0.075, 0), X, Z,
                active ? 0.074 : 0.060, active ? 0.074 : 0.060,
                core, active ? 0.62F : 0.46F);
        ProceduralGeometry.diamond(pose, planes, center.add(0, -height - 0.070, 0), X, Z,
                active ? 0.062 : 0.050, active ? 0.062 : 0.050,
                halo, active ? 0.52F : 0.36F);
    }

    private static void renderOrbitNodePlanes(PoseStack pose, VertexConsumer planes, Vec3 center,
                                              double rotation, double age, boolean active) {
        double radius = active ? 0.485 : 0.565;
        for (int index = 0; index < ORBIT_NODE_COUNT; index++) {
            double angle = rotation + TAU * index / ORBIT_NODE_COUNT;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
            double wandering = active ? 0.0
                    : ProceduralMotion.oscillate(age * 0.032 + index * 1.37, 0.038);
            Vec3 node = center.add(radial.scale(radius)).add(0, wandering, 0);
            int color = CATEGORY_COLORS[index];
            ProceduralGeometry.diamondRing(pose, planes, node, tangent, Y,
                    active ? 0.049 : 0.043, active ? 0.074 : 0.065,
                    active ? 0.029 : 0.025, active ? 0.044 : 0.039,
                    color, active ? 0.42F : 0.25F);
            ProceduralGeometry.diamond(pose, planes, node, tangent, Y,
                    active ? 0.026 : 0.022, active ? 0.040 : 0.034,
                    towardWhite(color, 0.38), active ? 0.72F : 0.48F);
        }
    }

    private static void renderLeafContours(PoseStack pose, VertexConsumer lines, Vec3 center,
                                           double fanHalfAngle, Flip flip, boolean active) {
        int contour = AscendancePalette.TRANSCENDENT.metalRgb();
        for (int index = 0; index < LEAF_COUNT; index++) {
            Leaf leaf = leaf(index, center, fanHalfAngle, flip);
            diamondOutline(pose, lines, leaf.center(), leaf.right(), Y,
                    leaf.width(), leaf.height(), contour, active ? 0.69F : 0.52F);

            // The paired offset contour supplies restrained plate thickness without a hard model.
            Vec3 rear = leaf.center().subtract(leaf.normal().scale(0.012));
            diamondOutline(pose, lines, rear, leaf.right(), Y,
                    leaf.width() * 0.985, leaf.height() * 0.985,
                    AscendancePalette.UTILITY, active ? 0.31F : 0.22F);
            ProceduralGeometry.line(pose, lines,
                    leafPoint(leaf, 0, 1), leafPoint(leaf, 0, 1).subtract(leaf.normal().scale(0.012)),
                    contour, active ? 0.36F : 0.24F);
            ProceduralGeometry.line(pose, lines,
                    leafPoint(leaf, 0, -1), leafPoint(leaf, 0, -1).subtract(leaf.normal().scale(0.012)),
                    contour, active ? 0.36F : 0.24F);
        }
    }

    private static void renderCoreAndOrbitLines(PoseStack pose, VertexConsumer lines, Vec3 center,
                                                double rotation, double age, boolean active) {
        int edge = AscendancePalette.TRANSCENDENT.metalRgb();
        int shell = AscendancePalette.UTILITY;
        double radius = active ? 0.485 : 0.565;
        double gap = active ? 0.17 : 0.35;

        ProceduralGeometry.brokenRing(pose, lines, center, X, Z, radius,
                rotation, TAU, 6, active ? 8 : 5, gap,
                shell, active ? 0.46F : 0.31F);
        Vec3 tiltedZ = new Vec3(0, 0.36, 0.94).normalize();
        ProceduralGeometry.brokenRing(pose, lines, center, X, tiltedZ,
                active ? 0.405 : 0.455, -rotation * 0.62, TAU,
                active ? 5 : 4, active ? 6 : 4, active ? 0.21 : 0.43,
                edge, active ? 0.38F : 0.22F);

        double spine = active ? 0.355 : 0.315;
        ProceduralGeometry.line(pose, lines, center.add(0, -spine, 0),
                center.add(0, spine, 0), edge, active ? 0.66F : 0.48F);
        ProceduralGeometry.line(pose, lines, center.add(-0.12, 0, 0),
                center.add(0.12, 0, 0), edge, active ? 0.51F : 0.34F);

        // Observed use completes the constellation and pulls its drifting nodes into alignment.
        int connections = active ? ORBIT_NODE_COUNT : 3;
        for (int index = 0; index < connections; index++) {
            Vec3 node = orbitNode(center, rotation, age, active, index);
            Vec3 destination = active
                    ? center.add(0, (index % 2 == 0 ? 0.12 : -0.12), 0)
                    : orbitNode(center, rotation, age, false, (index + 1) % ORBIT_NODE_COUNT);
            ProceduralGeometry.line(pose, lines, node, destination,
                    CATEGORY_COLORS[index], active ? 0.28F : 0.16F);
        }

        if (active) {
            double pulse = ProceduralMotion.phase(age, 0.028);
            ProceduralGeometry.ring(pose, lines, center, X, Z,
                    0.18 + pulse * 0.37, 28,
                    towardWhite(edge, 0.34), (float) (0.38 * (1.0 - pulse)));
        }
    }

    private static void renderTravelingDetailPulses(PoseStack pose, VertexConsumer planes,
                                                     Vec3 center, double rotation, double age,
                                                     boolean active) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f cameraUp = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        Vec3 up = new Vec3(cameraUp.x(), cameraUp.y(), cameraUp.z());
        int count = active ? 3 : 1;
        for (int index = 0; index < count; index++) {
            int nodeIndex = active ? index * 2 : 3;
            Vec3 node = orbitNode(center, rotation, age, active, nodeIndex);
            double progress = ProceduralMotion.phase(age + index * 11.0, active ? 0.022 : 0.009);
            ProceduralGeometry.travelingDiamond(pose, planes, node, center,
                    right, up, progress, active ? 0.019 : 0.015, active ? 0.028 : 0.022,
                    CATEGORY_COLORS[nodeIndex], active ? 0.50F : 0.28F);
        }
    }

    private static void renderFineDetail(PoseStack pose, VertexConsumer lines, Vec3 center,
                                         double fanHalfAngle, Flip flip, double rotation,
                                         double age, boolean active) {
        int edge = AscendancePalette.TRANSCENDENT.metalRgb();
        float faint = active ? 0.22F : 0.145F;

        // Fine page ruling and restrained polygonal registration are inspection-only.
        for (int index = 0; index < LEAF_COUNT; index++) {
            Leaf leaf = leaf(index, center, fanHalfAngle, flip);
            for (int row = -1; row <= 1; row++) {
                double vertical = row * 0.092;
                double available = leaf.width() * (1.0 - Math.abs(vertical) / leaf.height());
                double inset = available * (row == 0 ? 0.25 : 0.38);
                ProceduralGeometry.line(pose, lines,
                        leaf.center().add(Y.scale(vertical)).subtract(leaf.right().scale(inset)),
                        leaf.center().add(Y.scale(vertical)).add(leaf.right().scale(inset)),
                        index % 2 == 0 ? edge : AscendancePalette.UTILITY,
                        faint * (row == 0 ? 0.76F : 0.58F));
            }
            ProceduralGeometry.line(pose, lines,
                    leaf.center().add(Y.scale(-0.17)), leaf.center().add(Y.scale(0.17)),
                    edge, faint * 0.42F);

            Vec3 top = leafPoint(leaf, 0, 1);
            Vec3 markA = top.add(Y.scale(-0.052)).add(leaf.right().scale(-0.027));
            Vec3 markB = top.add(Y.scale(-0.074));
            Vec3 markC = top.add(Y.scale(-0.052)).add(leaf.right().scale(0.027));
            ProceduralGeometry.line(pose, lines, markA, markB, edge, faint * 0.72F);
            ProceduralGeometry.line(pose, lines, markB, markC, edge, faint * 0.72F);
        }

        // Six semantic category traces stay small and quiet around the dominant codex.
        for (int index = 0; index < ORBIT_NODE_COUNT; index++) {
            Vec3 node = orbitNode(center, rotation, age, active, index);
            Vec3 next = orbitNode(center, rotation, age, active,
                    (index + 1) % ORBIT_NODE_COUNT);
            Vec3 inward = node.lerp(center, active ? 0.33 : 0.20);
            ProceduralGeometry.line(pose, lines, node, inward,
                    CATEGORY_COLORS[index], faint * 0.74F);
            if (active || index % 2 == 0) {
                ProceduralGeometry.line(pose, lines, inward, next.lerp(center, 0.29),
                        CATEGORY_COLORS[index], faint * 0.48F);
            }
        }

        double detailRadius = active ? 0.365 : 0.425;
        ProceduralGeometry.brokenRing(pose, lines, center.add(0, 0.012, 0), X, Z,
                detailRadius, -rotation * 0.83, TAU, 12, 2,
                active ? 0.34 : 0.58, edge, faint * 0.58F);
        ProceduralGeometry.spokes(pose, lines, center, X, Z,
                detailRadius - 0.026, detailRadius + 0.030,
                active ? 18 : 12, rotation * 0.27,
                edge, faint * 0.52F);
    }

    private static Leaf leaf(int index, Vec3 center, double fanHalfAngle, Flip flip) {
        double normalized = (index - (LEAF_COUNT - 1) * 0.5) / ((LEAF_COUNT - 1) * 0.5);
        double angle = normalized * fanHalfAngle;
        if (index == flip.leafIndex()) angle += flip.angleOffset();
        Vec3 right = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        Vec3 normal = new Vec3(-right.z, 0, right.x);
        double layer = Math.abs(normalized);
        Vec3 leafCenter = center.add(normal.scale(layer * 0.016)).add(0, -layer * 0.012, 0);
        return new Leaf(leafCenter, right, normal,
                0.300 - layer * 0.018, 0.335 - layer * 0.010);
    }

    private static Vec3 orbitNode(Vec3 center, double rotation, double age,
                                  boolean active, int index) {
        double angle = rotation + TAU * index / ORBIT_NODE_COUNT;
        double radius = active ? 0.485 : 0.565;
        double wandering = active ? 0.0
                : ProceduralMotion.oscillate(age * 0.032 + index * 1.37, 0.038);
        return center.add(Math.cos(angle) * radius, wandering, Math.sin(angle) * radius);
    }

    private static Vec3 leafPoint(Leaf leaf, double horizontal, double vertical) {
        return leaf.center().add(leaf.right().scale(horizontal * leaf.width()))
                .add(Y.scale(vertical * leaf.height()));
    }

    private static void diamondOutline(PoseStack pose, VertexConsumer lines,
                                       Vec3 center, Vec3 right, Vec3 up,
                                       double halfWidth, double halfHeight,
                                       int rgb, float alpha) {
        Vec3 top = center.add(up.scale(halfHeight));
        Vec3 east = center.add(right.scale(halfWidth));
        Vec3 bottom = center.subtract(up.scale(halfHeight));
        Vec3 west = center.subtract(right.scale(halfWidth));
        ProceduralGeometry.line(pose, lines, top, east, rgb, alpha);
        ProceduralGeometry.line(pose, lines, east, bottom, rgb, alpha);
        ProceduralGeometry.line(pose, lines, bottom, west, rgb, alpha);
        ProceduralGeometry.line(pose, lines, west, top, rgb, alpha);
    }

    private static Flip flip(double age, double localPhase, boolean active) {
        double period = active ? 112.0 : 228.0;
        double shifted = age + localPhase * 31.0;
        long cycle = (long) Math.floor(shifted / period);
        double cycleProgress = shifted / period - Math.floor(shifted / period);
        double window = active ? 0.34 : 0.21;
        if (cycleProgress >= window) return Flip.NONE;

        double progress = ProceduralMotion.smoothStep(cycleProgress / window);
        double direction = (cycle & 1L) == 0L ? 1.0 : -1.0;
        int leafIndex = (cycle & 1L) == 0L ? 2 : 4;
        return new Flip(leafIndex, direction * Math.sin(progress * Math.PI)
                * (active ? 1.18 : 0.96));
    }

    private static double positionPhase(BlockPos pos) {
        long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
        return ((hash >>> 24) & 0xFFFFL) / 65535.0 * TAU;
    }

    private static int towardWhite(int rgb, double amount) {
        int r = (int) Math.round((rgb >> 16 & 255) * (1.0 - amount) + 255 * amount);
        int g = (int) Math.round((rgb >> 8 & 255) * (1.0 - amount) + 255 * amount);
        int b = (int) Math.round((rgb & 255) * (1.0 - amount) + 255 * amount);
        return r << 16 | g << 8 | b;
    }

    private record Leaf(Vec3 center, Vec3 right, Vec3 normal,
                        double width, double height) { }

    private record Flip(int leafIndex, double angleOffset) {
        private static final Flip NONE = new Flip(-1, 0.0);
    }
}
