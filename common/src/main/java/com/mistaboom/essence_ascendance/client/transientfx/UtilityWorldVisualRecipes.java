package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralGlyphs;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.world.phys.Vec3;

/** Directional Utility transfers and field outcomes that need more than a micro acknowledgment. */
final class UtilityWorldVisualRecipes {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final double TAU = Math.PI * 2.0;

    private UtilityWorldVisualRecipes() { }

    static void registerAll() {
        WorldVisualRecipes.register(TransientVisualIds.WORLD_UTILITY_TRANSFER, new UtilityTransfer());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_CONTAINMENT_SEAL, new ContainmentSeal());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_SANCTUARY_RELEASE, new SanctuaryRelease());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_COMPANION_RECALL, new CompanionRecall());
    }

    /** One clear source-to-target ribbon, with a small traveling facet and close parallel tracery. */
    private static final class UtilityTransfer implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 start = c.source(), end = c.target();
            Vec3 direction = normalized(end.subtract(start), c.event().direction());
            Basis basis = Basis.around(direction);
            ProceduralGeometry.taperedPlane(c.pose(), c.planes(), start, end, basis.a(),
                    c.event().scale() * 0.030, c.event().scale() * 0.052,
                    c.rgb(), (float) (c.envelope() * 0.42));
            Vec3 node = start.lerp(end, 0.12 + c.easedProgress() * 0.76);
            ProceduralGeometry.shard(c.pose(), c.planes(), node, basis.a(), direction,
                    c.event().scale() * 0.036, c.event().scale() * 0.075,
                    c.event().scale() * 0.020, brighten(c.rgb(), 0.72),
                    (float) (c.envelope() * 0.78));
            double radius = c.event().scale() * (0.18 + c.easedProgress() * 0.28);
            ProceduralGeometry.annulus(c.pose(), c.planes(), end, basis.a(), basis.b(),
                    radius * 0.78, radius, 24, 0, c.rgb(), (float) (c.envelope() * 0.38));
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 start = c.source(), end = c.target();
            Vec3 direction = normalized(end.subtract(start), c.event().direction());
            Basis basis = Basis.around(direction);
            ProceduralGeometry.line(c.pose(), c.lines(), start, end, brighten(c.rgb(), 0.64),
                    (float) (c.envelope() * 0.70));
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), end, basis.a(), basis.b(),
                    c.event().scale() * (0.22 + c.easedProgress() * 0.34),
                    c.progress() * 0.42, TAU, 6, 4, 0.20,
                    brighten(c.rgb(), 0.74), (float) (c.envelope() * 0.82));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 start = c.source(), end = c.target();
            Vec3 direction = normalized(end.subtract(start), c.event().direction());
            Basis basis = Basis.around(direction);
            for (int side : new int[]{-1, 1}) {
                Vec3 offset = basis.a().scale(side * c.event().scale() * 0.055);
                ProceduralGeometry.line(c.pose(), c.lines(), start.add(offset), end.add(offset),
                        brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.23));
            }
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), end, basis.a(), basis.b(),
                    c.event().scale() * (0.15 + c.easedProgress() * 0.22), -c.progress() * 0.75,
                    TAU, 12, 2, 0.52, brighten(c.rgb(), 0.82), (float) (c.envelope() * 0.25));
        }
    }

    /** A three-axis contracting seal survives distant viewing without becoming an opaque sphere. */
    private static final class ContainmentSeal implements WorldVisualRecipe {
        private double radius(WorldVisualRenderContext c) {
            return c.event().scale() * (0.92 - c.easedProgress() * 0.46);
        }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            double radius = radius(c);
            double width = c.event().scale() * 0.075;
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.position(), X, Z,
                    radius - width, radius, 40, 0, c.rgb(), (float) (c.envelope() * 0.40));
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.position(), X, Y,
                    radius - width, radius, 40, 0, c.rgb(), (float) (c.envelope() * 0.34));
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.position(), Z, Y,
                    radius - width, radius, 40, 0, brighten(c.rgb(), 0.30),
                    (float) (c.envelope() * 0.34));
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            double radius = radius(c);
            for (Basis basis : new Basis[]{new Basis(X, Z), new Basis(X, Y), new Basis(Z, Y)})
                ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.position(), basis.a(), basis.b(),
                        radius, c.progress() * 0.28, TAU, 10, 4, 0.18,
                        brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.84));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            double radius = radius(c) * 0.72;
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.position(), X, Z, radius,
                    -c.progress() * 0.75, TAU, 18, 2, 0.55,
                    brighten(c.rgb(), 0.82), (float) (c.envelope() * 0.24));
            ProceduralGeometry.spokes(c.pose(), c.lines(), c.position(), X, Z,
                    radius * 0.54, radius, 12, Math.PI / 12,
                    c.rgb(), (float) (c.envelope() * 0.20));
        }
    }

    /** Faceted question marks bob above a newly pacified mob's head. */
    private static final class SanctuaryRelease implements WorldVisualRecipe.TargetAttached {
        @Override public int repeatIntervalTicks() { return 8; }

        private Vec3 head(WorldVisualRenderContext c) {
            var entity = c.level().getEntity(c.event().targetEntityId());
            double halfHeight = entity == null ? c.event().parameterA() : entity.getBbHeight() * 0.5;
            return c.target().add(0, halfHeight + 0.48 + c.easedProgress() * 0.28, 0);
        }
        private void draw(WorldVisualRenderContext c, boolean outline) {
            Vec3 center = head(c);
            double tilt = Math.sin(c.progress() * 5.0) * 0.12;
            var basis = ProceduralGlyphs.facing(center, tilt);
            ProceduralGlyphs.question(c.pose(), outline ? c.lines() : c.planes(), center, basis,
                    0.72, outline ? brighten(c.rgb(), 0.80) : c.rgb(),
                    (float) (c.envelope() * (outline ? 0.92 : 0.78)), outline);
        }
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) { draw(c, false); }
        @Override public void renderPrimaryLines(WorldVisualRenderContext c) { draw(c, true); }

        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            Vec3 center = head(c);
            var basis = ProceduralGlyphs.facing(center, -0.18);
            Vec3 small = center.add(basis.right().scale(0.38)).add(0, -0.10, 0);
            ProceduralGlyphs.question(c.pose(), c.planes(), small, basis, 0.36,
                    brighten(c.rgb(), 0.52), (float) (c.envelope() * 0.38), false);
            for (int i = 0; i < 3; i++) {
                Vec3 point = center.add(basis.right().scale(-0.32 - i * 0.05))
                        .add(0, -0.12 + i * 0.10 + Math.sin(c.progress() * 5 + i) * 0.025, 0);
                ProceduralGeometry.diamond(c.pose(), c.planes(), point, basis.right(), basis.up(),
                        0.012, 0.019, brighten(c.rgb(), 0.62), (float) (c.envelope() * 0.32));
            }
        }
    }

    /** Catch-up teleport ends in a large arrival aperture with short, local directional traces. */
    private static final class CompanionRecall implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            Vec3 direction = normalized(c.event().direction(), Z);
            Basis basis = Basis.around(direction);
            double radius = c.event().scale() * (0.34 + c.easedProgress() * 0.54);
            ProceduralGeometry.annulus(c.pose(), c.planes(), center, basis.a(), basis.b(),
                    radius * 0.74, radius, 32, 0, c.rgb(), (float) (c.envelope() * 0.46));
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            Vec3 direction = normalized(c.event().direction(), Z);
            Basis basis = Basis.around(direction);
            double radius = c.event().scale() * (0.40 + c.easedProgress() * 0.62);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, basis.a(), basis.b(), radius,
                    c.progress() * 0.48, TAU, 8, 4, 0.18,
                    brighten(c.rgb(), 0.74), (float) (c.envelope() * 0.88));
            for (int i = -2; i <= 2; i++) {
                Vec3 offset = basis.a().scale(i * c.event().scale() * 0.11);
                ProceduralGeometry.line(c.pose(), c.lines(),
                        center.subtract(direction.scale(c.event().scale() * 0.82)).add(offset),
                        center.subtract(direction.scale(c.event().scale() * 0.18)).add(offset.scale(0.55)),
                        c.rgb(), (float) (c.envelope() * (i == 0 ? 0.62 : 0.34)));
            }
        }

        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            Vec3 direction = normalized(c.event().direction(), Z);
            Basis basis = Basis.around(direction);
            double radius = c.event().scale() * (0.28 + c.easedProgress() * 0.48);
            for (int i = 0; i < 8; i++) {
                double angle = i * TAU / 8 + c.progress() * 0.42;
                Vec3 radial = basis.a().scale(Math.cos(angle)).add(basis.b().scale(Math.sin(angle)));
                Vec3 point = center.add(radial.scale(radius));
                double size = c.event().scale() * 0.017;
                ProceduralGeometry.shard(c.pose(), c.planes(), point, basis.a(), radial,
                        size, size * 1.6, size * 0.36, brighten(c.rgb(), 0.74),
                        (float) (c.envelope() * 0.42));
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 direction = normalized(c.event().direction(), Z);
            Basis basis = Basis.around(direction);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.target(), basis.a(), basis.b(),
                    c.event().scale() * 0.48, -c.progress() * 0.72, TAU,
                    16, 2, 0.54, brighten(c.rgb(), 0.84), (float) (c.envelope() * 0.24));
        }
    }

    private static Vec3 normalized(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() > 1.0E-8 ? value.normalize() : fallback;
    }

    private static int brighten(int rgb, double amount) {
        amount = Math.clamp(amount, 0.0, 1.0);
        int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
        red += (int) Math.round((255 - red) * amount);
        green += (int) Math.round((255 - green) * amount);
        blue += (int) Math.round((255 - blue) * amount);
        return red << 16 | green << 8 | blue;
    }

    private record Basis(Vec3 a, Vec3 b) {
        private static Basis around(Vec3 direction) {
            Vec3 n = normalized(direction, Z);
            Vec3 reference = Math.abs(n.y) < 0.90 ? Y : X;
            Vec3 a = n.cross(reference).normalize();
            return new Basis(a, n.cross(a).normalize());
        }
    }
}
