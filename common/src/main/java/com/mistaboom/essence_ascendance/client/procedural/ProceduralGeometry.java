package com.mistaboom.essence_ascendance.client.procedural;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Textureless world-space primitives. Coordinates are in the caller's current pose. */
public final class ProceduralGeometry {
    private static final double TAU = Math.PI * 2.0;

    private ProceduralGeometry() { }

    public static void quad(PoseStack pose, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                            int rgb, float alpha) {
        quad(pose, out, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z, rgb, alpha);
    }

    public static void quad(PoseStack pose, VertexConsumer out,
                            double ax, double ay, double az, double bx, double by, double bz,
                            double cx, double cy, double cz, double dx, double dy, double dz,
                            int rgb, float alpha) {
        int opacity = opacity(alpha);
        if (opacity == 0) return;
        vertex(pose, out, ax, ay, az, rgb, opacity);
        vertex(pose, out, bx, by, bz, rgb, opacity);
        vertex(pose, out, cx, cy, cz, rgb, opacity);
        vertex(pose, out, dx, dy, dz, rgb, opacity);
    }

    private static void vertex(PoseStack pose, VertexConsumer out, double x, double y, double z,
                               int rgb, int opacity) {
        out.addVertex(pose.last().pose(), (float) x, (float) y, (float) z)
                .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, opacity);
    }

    public static void line(PoseStack pose, VertexConsumer out, Vec3 a, Vec3 b, int rgb, float alpha) {
        line(pose, out, a.x, a.y, a.z, b.x, b.y, b.z, rgb, alpha);
    }

    public static void line(PoseStack pose, VertexConsumer out,
                            double ax, double ay, double az, double bx, double by, double bz,
                            int rgb, float alpha) {
        int opacity = opacity(alpha);
        if (opacity == 0) return; // Invisible line fragments must never occlude later effects.
        double nx = bx - ax, ny = by - ay, nz = bz - az;
        double lengthSquared = nx * nx + ny * ny + nz * nz;
        if (lengthSquared <= Math.ulp(1.0)) return;
        double inverseLength = 1.0 / Math.sqrt(lengthSquared);
        lineVertex(pose, out, ax, ay, az, nx * inverseLength, ny * inverseLength, nz * inverseLength, rgb, opacity);
        lineVertex(pose, out, bx, by, bz, nx * inverseLength, ny * inverseLength, nz * inverseLength, rgb, opacity);
    }

    private static void lineVertex(PoseStack pose, VertexConsumer out,
                                   double x, double y, double z, double nx, double ny, double nz,
                                   int rgb, int opacity) {
        out.addVertex(pose.last().pose(), (float) x, (float) y, (float) z)
                .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, opacity)
                .setNormal(pose.last(), (float) nx, (float) ny, (float) nz);
    }

    private static int opacity(float alpha) { return Math.clamp(Math.round(alpha * 255.0F), 0, 255); }

    public static void diamond(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 right, Vec3 up,
                               double halfWidth, double halfHeight, int rgb, float alpha) {
        quad(pose, out, center.add(up.scale(halfHeight)), center.add(right.scale(halfWidth)),
                center.add(up.scale(-halfHeight)), center.add(right.scale(-halfWidth)), rgb, alpha);
    }

    public static void diamondRing(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 right, Vec3 up,
                                   double outerWidth, double outerHeight, double innerWidth, double innerHeight,
                                   int rgb, float alpha) {
        Vec3 ot = center.add(up.scale(outerHeight)), or = center.add(right.scale(outerWidth));
        Vec3 ob = center.add(up.scale(-outerHeight)), ol = center.add(right.scale(-outerWidth));
        Vec3 it = center.add(up.scale(innerHeight)), ir = center.add(right.scale(innerWidth));
        Vec3 ib = center.add(up.scale(-innerHeight)), il = center.add(right.scale(-innerWidth));
        quad(pose, out, ot, or, ir, it, rgb, alpha);
        quad(pose, out, or, ob, ib, ir, rgb, alpha);
        quad(pose, out, ob, ol, il, ib, rgb, alpha);
        quad(pose, out, ol, ot, it, il, rgb, alpha);
    }

    /** Eight triangular facets with a true volume, readable from any world viewing angle. */
    public static void shard(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 right, Vec3 up,
                             double width, double height, double thickness, int rgb, float alpha) {
        Vec3 side = right.cross(up).normalize().scale(thickness);
        Vec3 top = center.add(up.scale(height)), bottom = center.subtract(up.scale(height));
        Vec3[] belt = {center.add(right.scale(width)), center.add(side),
                center.subtract(right.scale(width)), center.subtract(side)};
        for (int i = 0; i < 4; i++) {
            Vec3 a = belt[i], b = belt[(i + 1) % 4];
            float shade = 0.65F + i * 0.10F;
            quad(pose, out, top, a, b, b, rgb, alpha * shade);
            quad(pose, out, bottom, b, a, a, rgb, alpha * (1.0F - i * 0.08F));
        }
    }

    public static void billboardRect(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 right, Vec3 up,
                                     double width, double height, int rgb, float alpha) {
        Vec3 h = right.scale(width * 0.5), v = up.scale(height * 0.5);
        quad(pose, out, center.subtract(h).subtract(v), center.add(h).subtract(v),
                center.add(h).add(v), center.subtract(h).add(v), rgb, alpha);
    }

    /** A tapered plane whose four corners remain explicit for tilted thrust and feather shapes. */
    public static void taperedPlane(PoseStack pose, VertexConsumer out, Vec3 root, Vec3 tip,
                                    Vec3 lateral, double rootWidth, double tipWidth, int rgb, float alpha) {
        quad(pose, out, root.subtract(lateral.scale(rootWidth)), root.add(lateral.scale(rootWidth)),
                tip.add(lateral.scale(tipWidth)), tip.subtract(lateral.scale(tipWidth)), rgb, alpha);
    }

    public static Vec3 orbitPoint(Vec3 center, Vec3 axisA, Vec3 axisB,
                                  double radiusA, double radiusB, double angle) {
        return center.add(axisA.scale(Math.cos(angle) * radiusA)).add(axisB.scale(Math.sin(angle) * radiusB));
    }

    public static void arc(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 axisA, Vec3 axisB,
                           double radiusA, double radiusB, double start, double sweep, int segments,
                           int rgb, float alpha) {
        if (segments < 1) return;
        double previousA = Math.cos(start) * radiusA, previousB = Math.sin(start) * radiusB;
        double px = center.x + axisA.x * previousA + axisB.x * previousB;
        double py = center.y + axisA.y * previousA + axisB.y * previousB;
        double pz = center.z + axisA.z * previousA + axisB.z * previousB;
        for (int i = 1; i <= segments; i++) {
            double angle = start + sweep * i / segments;
            double a = Math.cos(angle) * radiusA, b = Math.sin(angle) * radiusB;
            double x = center.x + axisA.x * a + axisB.x * b;
            double y = center.y + axisA.y * a + axisB.y * b;
            double z = center.z + axisA.z * a + axisB.z * b;
            line(pose, out, px, py, pz, x, y, z, rgb, alpha);
            px = x; py = y; pz = z;
        }
    }

    public static void ring(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 axisA, Vec3 axisB,
                            double radius, int segments, int rgb, float alpha) {
        arc(pose, out, center, axisA, axisB, radius, radius, 0, TAU, segments, rgb, alpha);
    }

    /** Flat translucent band between two concentric circles in the caller's chosen plane. */
    public static void annulus(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 axisA, Vec3 axisB,
                               double innerRadius, double outerRadius, int segments, double phase,
                               int rgb, float alpha) {
        if (segments < 3 || innerRadius < 0 || outerRadius <= innerRadius) return;
        for (int i = 0; i < segments; i++) {
            double angle = phase + TAU * i / segments;
            double next = phase + TAU * (i + 1) / segments;
            Vec3 outerA = orbitPoint(center, axisA, axisB, outerRadius, outerRadius, angle);
            Vec3 outerB = orbitPoint(center, axisA, axisB, outerRadius, outerRadius, next);
            Vec3 innerB = orbitPoint(center, axisA, axisB, innerRadius, innerRadius, next);
            Vec3 innerA = orbitPoint(center, axisA, axisB, innerRadius, innerRadius, angle);
            quad(pose, out, outerA, outerB, innerB, innerA, rgb, alpha);
        }
    }

    public static void brokenRing(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 axisA, Vec3 axisB,
                                  double radius, double start, double sweep, int pieces, int segmentsPerPiece,
                                  double gapFraction, int rgb, float alpha) {
        if (pieces < 1) return;
        double pieceSweep = sweep / pieces;
        for (int i = 0; i < pieces; i++)
            arc(pose, out, center, axisA, axisB, radius, radius,
                    start + pieceSweep * i, pieceSweep * (1 - gapFraction), segmentsPerPiece, rgb, alpha);
    }

    public static void spokes(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 axisA, Vec3 axisB,
                              double innerRadius, double outerRadius, int count, double phase,
                              int rgb, float alpha) {
        if (count < 1) return;
        for (int i = 0; i < count; i++) {
            double angle = phase + TAU * i / count;
            line(pose, out, orbitPoint(center, axisA, axisB, innerRadius, innerRadius, angle),
                    orbitPoint(center, axisA, axisB, outerRadius, outerRadius, angle), rgb, alpha);
        }
    }

    public static void helix(PoseStack pose, VertexConsumer out, Vec3 start, Vec3 direction,
                             Vec3 axisA, Vec3 axisB, double radius, double turns, int segments,
                             int rgb, float alpha) {
        if (segments < 1) return;
        Vec3 previous = start.add(axisA.scale(radius));
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments, angle = TAU * turns * t;
            Vec3 next = orbitPoint(start.add(direction.scale(t)), axisA, axisB, radius, radius, angle);
            line(pose, out, previous, next, rgb, alpha);
            previous = next;
        }
    }

    public static void travelingDiamond(PoseStack pose, VertexConsumer out, Vec3 start, Vec3 end,
                                        Vec3 right, Vec3 up, double phase, double halfWidth,
                                        double halfHeight, int rgb, float alpha) {
        diamond(pose, out, start.lerp(end, phase - Math.floor(phase)), right, up,
                halfWidth, halfHeight, rgb, alpha);
    }

    /** Sparse connected ribbon; callers choose the path and can leave gaps by splitting it. */
    public static void ribbon(PoseStack pose, VertexConsumer out, List<Vec3> points, int rgb, float alpha) {
        for (int i = 1; i < points.size(); i++) line(pose, out, points.get(i - 1), points.get(i), rgb, alpha);
    }

    /** Straight tether or beam represented by a flat plane with caller-selected facing. */
    public static void beam(PoseStack pose, VertexConsumer out, Vec3 start, Vec3 end, Vec3 lateral,
                            double halfWidth, int rgb, float alpha) {
        taperedPlane(pose, out, start, end, lateral, halfWidth, halfWidth, rgb, alpha);
    }

    /** Twelve structural edges of a box; useful for restrained cages and lattices. */
    public static void cage(PoseStack pose, VertexConsumer out, Vec3 min, Vec3 max, int rgb, float alpha) {
        for (int y = 0; y < 2; y++) {
            double yy = y == 0 ? min.y : max.y;
            for (int z = 0; z < 2; z++) {
                double zz = z == 0 ? min.z : max.z;
                line(pose, out, min.x, yy, zz, max.x, yy, zz, rgb, alpha);
            }
            for (int x = 0; x < 2; x++) {
                double xx = x == 0 ? min.x : max.x;
                line(pose, out, xx, yy, min.z, xx, yy, max.z, rgb, alpha);
            }
        }
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            line(pose, out, x == 0 ? min.x : max.x, min.y, z == 0 ? min.z : max.z,
                    x == 0 ? min.x : max.x, max.y, z == 0 ? min.z : max.z, rgb, alpha);
    }
}
