package com.mistaboom.essence_ascendance.client.procedural;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Small faceted world glyphs, with separate fill and outline passes. */
public final class ProceduralGlyphs {
    private static final double[][] HEART = {
            {0, 0.24}, {0.22, 0.46}, {0.42, 0.43}, {0.54, 0.22},
            {0.44, 0.00}, {0, -0.50}, {-0.44, 0.00}, {-0.54, 0.22},
            {-0.42, 0.43}, {-0.22, 0.46}};
    private static final double[][] QUESTION = {
            {-0.22, 0.25}, {-0.18, 0.42}, {0.02, 0.50}, {0.23, 0.40},
            {0.27, 0.22}, {0.16, 0.09}, {0, -0.02}, {0, -0.19}};

    private ProceduralGlyphs() { }

    public record Facing(Vec3 right, Vec3 up) { }

    /** Input is already camera relative. Keep the glyph legible from every observer's angle. */
    public static Facing facing(Vec3 center, double tilt) {
        Vec3 n = center.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : center.normalize().scale(-1);
        Vec3 ref = Math.abs(n.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = ref.cross(n).normalize(), up = n.cross(right).normalize();
        return new Facing(right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt))),
                up.scale(Math.cos(tilt)).subtract(right.scale(Math.sin(tilt))));
    }

    public static void heart(PoseStack pose, VertexConsumer out, Vec3 center, Facing basis,
                             double size, int rgb, float alpha, boolean outline) {
        Vec3 inset = point(center, basis, size, 0, -0.04);
        for (int i = 0; i < HEART.length; i++) {
            double[] a = HEART[i], b = HEART[(i + 1) % HEART.length];
            Vec3 from = point(center, basis, size, a[0], a[1]);
            Vec3 to = point(center, basis, size, b[0], b[1]);
            if (outline) ProceduralGeometry.line(pose, out, from, to, rgb, alpha);
            else ProceduralGeometry.quad(pose, out, inset, from, to, to, rgb,
                    alpha * (i % 3 == 0 ? 0.68F : i % 3 == 1 ? 0.90F : 1.0F));
        }
    }

    public static void question(PoseStack pose, VertexConsumer out, Vec3 center, Facing basis,
                                double size, int rgb, float alpha, boolean outline) {
        for (int i = 1; i < QUESTION.length; i++) {
            double[] a = QUESTION[i - 1], b = QUESTION[i];
            Vec3 from = point(center, basis, size, a[0], a[1]);
            Vec3 to = point(center, basis, size, b[0], b[1]);
            if (outline) ProceduralGeometry.line(pose, out, from, to, rgb, alpha);
            else {
                Vec3 lateral = basis.right().scale(-(b[1] - a[1]))
                        .add(basis.up().scale(b[0] - a[0])).normalize();
                ProceduralGeometry.beam(pose, out, from, to, lateral, size * 0.038, rgb, alpha);
            }
        }
        if (!outline) ProceduralGeometry.diamond(pose, out,
                point(center, basis, size, 0, -0.36), basis.right(), basis.up(),
                size * 0.055, size * 0.065, rgb, alpha);
    }

    private static Vec3 point(Vec3 center, Facing basis, double size, double x, double y) {
        return center.add(basis.right().scale(size * x)).add(basis.up().scale(size * y));
    }
}
