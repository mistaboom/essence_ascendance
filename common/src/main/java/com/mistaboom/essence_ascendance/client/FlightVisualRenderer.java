package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.visual.ProceduralColors;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Textureless procedural presentation shared by Fatigue Flight, Essence Wings
 * and Vector Boost.
 *
 * <p>The caller supplies a pose stack attached to the player's torso. All
 * geometry is therefore body-local and follows the real player pose. The
 * visual language intentionally mirrors Waylight: translucent purple planes,
 * sparse bright cores and separated floating pieces rather than a solid model.
 * The translucent plane pass writes depth so ordinary world/entity renderers
 * correctly occlude against the harness and wings.</p>
 */
public final class FlightVisualRenderer {
    private static final ProceduralColors.Colors WISP_COLORS = ProceduralColors.canonicalWisp();
    private static final int[] SIDES = {-1, 1};
    private static final double[] FEATHER_DEGREES = {0.0, 24.0, 48.0, 72.0};
    private static final double[] FEATHER_FILL_LENGTHS = {1.48, 1.30, 1.06, 0.86};
    private static final double[] FEATHER_EDGE_LENGTHS = {1.42, 1.24, 1.01, 0.82};
    private static final double[] FEATHER_WIDTHS = {0.112, 0.108, 0.100, 0.090};
    private static final float WISP_HALO_ALPHA = 0.12F;
    private static final float WISP_BODY_ALPHA = 0.72F;
    private static final float WISP_CORE_ALPHA = 0.98F;
    private static final float WISP_WING_ALPHA = 0.48F;
    private static final float WISP_LINE_ALPHA = 0.86F;

    private static final RenderType PLANES = ProceduralRenderTypes.WORLD_PLANES;
    private static final RenderType EDGES = ProceduralRenderTypes.WORLD_LINES;

    private FlightVisualRenderer() { }

    public static boolean active() {
        return FlightVisualClientState.fatigueEnabled()
                || FlightVisualClientState.wingsEnabled()
                || FlightVisualClientState.fatigueThrusting()
                || FlightVisualClientState.wingsActive()
                || FlightVisualClientState.vectorBoostIntensity(0.0F) > 0.0;
    }

    public static void render(AbstractClientPlayer player, PoseStack poseStack,
                              MultiBufferSource buffers, float partialTick) {
        boolean fatigueEnabled = FlightVisualClientState.fatigueEnabled();
        boolean wingsEnabled = FlightVisualClientState.wingsEnabled();
        boolean fatigueThrusting = FlightVisualClientState.fatigueThrusting();
        double boost = FlightVisualClientState.vectorBoostIntensity(partialTick);

        boolean harnessVisible = fatigueEnabled || wingsEnabled || boost > 0.0;
        boolean wingVisual = wingsEnabled || boost > 0.0;
        if (!harnessVisible) return;

        double age = player.tickCount + Math.clamp(partialTick, 0.0F, 1.0F);
        double thrustIntensity = boost > 0.0 ? 1.0 + boost * 0.85 : 1.0;

        poseStack.pushPose();
        poseStack.translate(0.0F, -0.090F, -0.090F);

        VertexConsumer fills = buffers.getBuffer(PLANES);
        renderHarnessFills(poseStack, fills, age);
        if (wingVisual) renderWingFeathers(poseStack, fills, age);
        if (fatigueThrusting || boost > 0.0) {
            renderThrusterFills(poseStack, fills, age, thrustIntensity);
        }

        VertexConsumer lines = buffers.getBuffer(EDGES);
        renderHarnessEdges(poseStack, lines, age);
        if (wingVisual) renderWingFeatherAccents(poseStack, lines, age);
        if (fatigueThrusting || boost > 0.0) {
            renderThrusterEdges(poseStack, lines, age, thrustIntensity);
        }

        poseStack.popPose();
    }

    private static void renderHarnessFills(PoseStack pose, VertexConsumer fills, double age) {
        double pulse = 1.0 + ProceduralMotion.oscillate(age * 0.22, 0.075);

        renderCentralShardFill(pose, fills,
                0.000F, 0.220F, 0.465F,
                0.180 * pulse, 0.380 * pulse);

        for (int side : SIDES) {
            renderPivotedSideShardFill(pose, fills,
                    side,
                    side * 0.170F, 0.295F, 0.465F,
                    0.145, 0.285);
        }
    }

    private static void renderHarnessEdges(PoseStack pose, VertexConsumer lines, double age) {
        double pulse = 1.0 + ProceduralMotion.oscillate(age * 0.22, 0.075);

        renderCentralShardEdges(pose, lines,
                0.000F, 0.220F, 0.467F,
                0.180 * pulse, 0.380 * pulse);

        for (int side : SIDES) {
            renderPivotedSideShardEdges(pose, lines,
                    side,
                    side * 0.170F, 0.295F, 0.467F,
                    0.145, 0.285);
        }
    }

    private static void renderCentralShardFill(PoseStack pose, VertexConsumer fills,
                                               float x, float y, float z,
                                               double halfWidth, double halfHeight) {
        int utility = WISP_COLORS.shell();
        int core = WISP_COLORS.core();
        double haloZ = z;
        double bodyZ = z + 0.0015;
        double coreZ = z + 0.0030;
        quad(pose, fills,
                point(x, y - halfHeight, haloZ),
                point(x + halfWidth, y, haloZ),
                point(x, y + halfHeight, haloZ),
                point(x - halfWidth, y, haloZ),
                utility, WISP_HALO_ALPHA);
        quad(pose, fills,
                point(x, y - halfHeight * 0.67, bodyZ),
                point(x + halfWidth * 0.63, y, bodyZ),
                point(x, y + halfHeight * 0.67, bodyZ),
                point(x - halfWidth * 0.63, y, bodyZ),
                utility, WISP_BODY_ALPHA);
        quad(pose, fills,
                point(x, y - halfHeight * 0.37, coreZ),
                point(x + halfWidth * 0.30, y, coreZ),
                point(x, y + halfHeight * 0.37, coreZ),
                point(x - halfWidth * 0.30, y, coreZ),
                core, WISP_CORE_ALPHA);
    }

    private static void renderCentralShardEdges(PoseStack pose, VertexConsumer lines,
                                                float x, float y, float z,
                                                double halfWidth, double halfHeight) {
        int core = WISP_COLORS.core();
        double edgeZ = z + 0.0045;
        diamondEdgesLocal(pose, lines, x, y, edgeZ, halfWidth * 0.63, halfHeight * 0.67, core, 0.38F);
        diamondEdgesLocal(pose, lines, x, y, edgeZ + 0.0005, halfWidth * 0.30, halfHeight * 0.37, core, WISP_LINE_ALPHA);
    }

    /**
     * Side diamonds: their inside corners stay on the same plane as the
     * central shard, then the diamonds fan 45 degrees toward the player so the
     * outside corners sit closer to Steve.
     */
    private static void renderPivotedSideShardFill(PoseStack pose, VertexConsumer fills,
                                                   int side, float pivotX, float pivotY, float pivotZ,
                                                   double halfWidth, double halfHeight) {
        int utility = WISP_COLORS.shell();
        int core = WISP_COLORS.core();
        OrientedDiamond outer = orientedDiamondFromInnerCorner(side, pivotX, pivotY, pivotZ, halfWidth, halfHeight);
        OrientedDiamond mid = orientedDiamondFromInnerCorner(side, pivotX, pivotY, pivotZ - 0.0015F,
                halfWidth * 0.63, halfHeight * 0.67);
        OrientedDiamond inner = orientedDiamondFromInnerCorner(side, pivotX, pivotY, pivotZ - 0.0030F,
                halfWidth * 0.30, halfHeight * 0.37);

        quad(pose, fills, outer.top, outer.right, mid.right, mid.top, utility, WISP_HALO_ALPHA);
        quad(pose, fills, outer.right, outer.bottom, mid.bottom, mid.right, utility, WISP_HALO_ALPHA);
        quad(pose, fills, outer.bottom, outer.left, mid.left, mid.bottom, utility, WISP_HALO_ALPHA);
        quad(pose, fills, outer.left, outer.top, mid.top, mid.left, utility, WISP_HALO_ALPHA);

        quad(pose, fills, mid.top, mid.right, inner.right, inner.top, utility, WISP_BODY_ALPHA);
        quad(pose, fills, mid.right, mid.bottom, inner.bottom, inner.right, utility, WISP_BODY_ALPHA);
        quad(pose, fills, mid.bottom, mid.left, inner.left, inner.bottom, utility, WISP_BODY_ALPHA);
        quad(pose, fills, mid.left, mid.top, inner.top, inner.left, utility, WISP_BODY_ALPHA);

        quad(pose, fills, inner.top, inner.right, inner.bottom, inner.left, core, WISP_CORE_ALPHA);
    }

    private static void renderPivotedSideShardEdges(PoseStack pose, VertexConsumer lines,
                                                    int side, float pivotX, float pivotY, float pivotZ,
                                                    double halfWidth, double halfHeight) {
        int core = WISP_COLORS.core();
        OrientedDiamond mid = orientedDiamondFromInnerCorner(side, pivotX, pivotY, pivotZ - 0.0045F,
                halfWidth * 0.63, halfHeight * 0.67);
        OrientedDiamond inner = orientedDiamondFromInnerCorner(side, pivotX, pivotY, pivotZ - 0.0050F,
                halfWidth * 0.30, halfHeight * 0.37);
        diamondEdges(pose, lines, mid, core, 0.38F);
        diamondEdges(pose, lines, inner, core, WISP_LINE_ALPHA);
    }

    /**
     * Four independent feathers form one uniform bird/angel-wing fan. Their
     * inside corners all point to the same center and start the same distance
     * from that point; only length varies. The whole fan is translated as one
     * plane so it rides higher and sits on the same depth plane as the central
     * harness diamond.
     */
    private static void renderWingFeathers(PoseStack pose, VertexConsumer fills, double age) {
        int utility = WISP_COLORS.shell();
        double breathe = Math.sin(age * 0.12) * 0.020;
        double zWing = 0.465;
        double pivotX = 0.315;
        double pivotY = 0.090;
        double rootRadius = 0.190;
        for (int side : SIDES) {
            for (int i = 0; i < FEATHER_DEGREES.length; i++) {
                LocalPoint root = fanPoint(pivotX, pivotY, zWing, rootRadius, FEATHER_DEGREES[i]);
                LocalPoint tip = fanPoint(pivotX, pivotY, zWing, rootRadius + FEATHER_FILL_LENGTHS[i] + breathe * (1.0 - i * 0.18), FEATHER_DEGREES[i]);
                feather(pose, fills, side, root, tip, FEATHER_WIDTHS[i], utility, WISP_WING_ALPHA);
            }
        }
    }

    private static void renderWingFeatherAccents(PoseStack pose, VertexConsumer lines, double age) {
        int core = WISP_COLORS.core();
        double breathe = Math.sin(age * 0.12) * 0.020;
        double zWing = 0.461;
        double pivotX = 0.315;
        double pivotY = 0.090;
        double rootRadius = 0.190;
        for (int side : SIDES) {
            for (int i = 0; i < FEATHER_DEGREES.length; i++) {
                LocalPoint root = fanPoint(pivotX, pivotY, zWing, rootRadius, FEATHER_DEGREES[i]);
                LocalPoint tip = fanPoint(pivotX, pivotY, zWing, rootRadius + FEATHER_EDGE_LENGTHS[i] + breathe * (1.0 - i * 0.18), FEATHER_DEGREES[i]);
                featherSpine(pose, lines, side, root, tip, core, WISP_LINE_ALPHA);
            }
        }
    }

    private static void renderThrusterFills(PoseStack pose, VertexConsumer fills,
                                            double age, double intensity) {
        int utility = WISP_COLORS.shell();
        int core = WISP_COLORS.core();
        double flicker = 1.0 + Math.sin(age * 0.88) * 0.07 + Math.sin(age * 1.73) * 0.03;
        double length = 0.620 * intensity * flicker;

        for (int side : SIDES) {
            double x = side * 0.2725;
            double top = 0.595;
            double bottom = top + length;
            double z = 0.3725;
            double outer = 0.112 * (0.96 + intensity * 0.06);
            double inner = outer * 0.38;

            quad(pose, fills,
                    point(x - outer, top, z), point(x + outer, top, z),
                    point(x + outer * 0.18, bottom, z + 0.022), point(x - outer * 0.18, bottom, z + 0.022),
                    utility, WISP_HALO_ALPHA);
            quad(pose, fills,
                    point(x, top, z - outer), point(x, top, z + outer),
                    point(x, bottom, z + 0.022 + outer * 0.18), point(x, bottom, z + 0.022 - outer * 0.18),
                    utility, WISP_HALO_ALPHA * 0.72F);
            quad(pose, fills,
                    point(x - inner, top + 0.012, z + 0.010), point(x + inner, top + 0.012, z + 0.010),
                    point(x + inner * 0.14, bottom - length * 0.18, z + 0.028),
                    point(x - inner * 0.14, bottom - length * 0.18, z + 0.028),
                    core, WISP_BODY_ALPHA);

            for (int i = 0; i < 2; i++) {
                double phase = (age * (0.125 + i * 0.017) + i * 0.41) % 1.0;
                double y = top + length * (0.34 + phase * 0.52);
                double size = (0.032 - i * 0.006) * intensity;
                diamond(pose, fills,
                        x + side * (i == 0 ? -0.020 : 0.023), y,
                        z + 0.040 + i * 0.012,
                        size, size * 1.40,
                        utility, i == 0 ? 0.40F : 0.20F);
            }
        }
    }

    private static void renderThrusterEdges(PoseStack pose, VertexConsumer lines,
                                            double age, double intensity) {
        int core = WISP_COLORS.core();
        double flicker = 1.0 + Math.sin(age * 0.88) * 0.07 + Math.sin(age * 1.73) * 0.03;
        double length = 0.620 * intensity * flicker;
        for (int side : SIDES) {
            double x = side * 0.2725;
            line(pose, lines,
                    point(x, 0.615, 0.3685),
                    point(x, 0.595 + length, 0.3985),
                    core, 0.34F);
        }
    }

    private static void feather(PoseStack pose, VertexConsumer fills, double side,
                                LocalPoint root, LocalPoint tip, double halfWidth,
                                int rgb, float alpha) {
        LocalPoint a = mirror(root, side);
        LocalPoint c = mirror(tip, side);
        double dx = c.x - a.x;
        double dy = c.y - a.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 1.0E-6) return;

        double px = -dy / length * halfWidth;
        double py = dx / length * halfWidth;
        LocalPoint shoulder = point(
                a.x + dx * 0.42,
                a.y + dy * 0.42,
                a.z);

        quad(pose, fills,
                a,
                point(shoulder.x + px, shoulder.y + py, shoulder.z),
                c,
                point(shoulder.x - px, shoulder.y - py, shoulder.z),
                rgb, alpha);
    }

    private static void featherSpine(PoseStack pose, VertexConsumer lines, double side,
                                     LocalPoint root, LocalPoint tip, int rgb, float alpha) {
        line(pose, lines, mirror(root, side), mirror(tip, side), rgb, alpha);
    }

    private static LocalPoint mirror(LocalPoint point, double side) {
        return point(point.x * side, point.y, point.z);
    }

    private static LocalPoint fanPoint(double pivotX, double pivotY, double pivotZ,
                                       double radius, double degrees) {
        double radians = Math.toRadians(degrees);
        return point(
                pivotX + Math.cos(radians) * radius,
                pivotY + Math.sin(radians) * radius,
                pivotZ);
    }

    private static OrientedDiamond orientedDiamondFromInnerCorner(int side,
                                                                  double pivotX, double pivotY, double pivotZ,
                                                                  double halfWidth, double halfHeight) {
        double angle = Math.toRadians(45.0);
        double ux = side * Math.cos(angle);
        double uz = -Math.sin(angle);
        LocalPoint center = point(
                pivotX + ux * halfWidth,
                pivotY,
                pivotZ + uz * halfWidth);
        LocalPoint inner = point(pivotX, pivotY, pivotZ);
        LocalPoint outer = point(
                pivotX + ux * halfWidth * 2.0,
                pivotY,
                pivotZ + uz * halfWidth * 2.0);
        LocalPoint top = point(center.x, center.y - halfHeight, center.z);
        LocalPoint bottom = point(center.x, center.y + halfHeight, center.z);
        return new OrientedDiamond(top, outer, bottom, inner);
    }

    private static void diamondEdges(PoseStack pose, VertexConsumer lines,
                                     OrientedDiamond diamond, int rgb, float alpha) {
        line(pose, lines, diamond.top, diamond.right, rgb, alpha);
        line(pose, lines, diamond.right, diamond.bottom, rgb, alpha);
        line(pose, lines, diamond.bottom, diamond.left, rgb, alpha);
        line(pose, lines, diamond.left, diamond.top, rgb, alpha);
    }

    private static void diamondEdgesLocal(PoseStack pose, VertexConsumer lines,
                                          double x, double y, double z,
                                          double halfWidth, double halfHeight,
                                          int rgb, float alpha) {
        LocalPoint top = point(x, y - halfHeight, z);
        LocalPoint right = point(x + halfWidth, y, z);
        LocalPoint bottom = point(x, y + halfHeight, z);
        LocalPoint left = point(x - halfWidth, y, z);
        line(pose, lines, top, right, rgb, alpha);
        line(pose, lines, right, bottom, rgb, alpha);
        line(pose, lines, bottom, left, rgb, alpha);
        line(pose, lines, left, top, rgb, alpha);
    }

    private static void diamond(PoseStack pose, VertexConsumer fills,
                                double x, double y, double z,
                                double halfWidth, double halfHeight,
                                int rgb, float alpha) {
        quad(pose, fills,
                point(x, y - halfHeight, z),
                point(x + halfWidth, y, z),
                point(x, y + halfHeight, z),
                point(x - halfWidth, y, z), rgb, alpha);
    }

    private static void quad(PoseStack pose, VertexConsumer fills,
                             LocalPoint a, LocalPoint b, LocalPoint c, LocalPoint d,
                             int rgb, float alpha) {
        ProceduralGeometry.quad(pose, fills, a.x, a.y, a.z, b.x, b.y, b.z,
                c.x, c.y, c.z, d.x, d.y, d.z, rgb, alpha);
    }

    private static void line(PoseStack pose, VertexConsumer lines,
                             LocalPoint start, LocalPoint end, int rgb, float alpha) {
        ProceduralGeometry.line(pose, lines, start.x, start.y, start.z,
                end.x, end.y, end.z, rgb, alpha);
    }

    private static LocalPoint point(double x, double y, double z) {
        return new LocalPoint(x, y, z);
    }

    private record LocalPoint(double x, double y, double z) { }
    private record OrientedDiamond(LocalPoint top, LocalPoint right, LocalPoint bottom, LocalPoint left) { }
}
