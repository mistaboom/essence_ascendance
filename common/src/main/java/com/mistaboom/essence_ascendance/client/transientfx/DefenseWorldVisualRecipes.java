package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.world.phys.Vec3;

/** Defense and control compositions hosted by the shared transient runtime. */
final class DefenseWorldVisualRecipes {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final double TAU = Math.PI * 2.0;

    private DefenseWorldVisualRecipes() { }

    static void registerAll() {
        WorldVisualRecipes.register(TransientVisualIds.WORLD_SHIELD_RAM, new ShieldRam());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_GUARD_RESPONSE, new RiposteReady());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_REFLECTION_RETURN, new TravelingShield(false));
        WorldVisualRecipes.register(TransientVisualIds.WORLD_CROWD_REPRISAL, new TravelingShield(true));
        WorldVisualRecipes.register(TransientVisualIds.WORLD_RIPOSTE_RELEASE, new RiposteRelease());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_STORED_FORCE, new StoredForceImpact());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_STATUS_REJECTION, new StatusRejection());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_SHATTERING_WARD, new ShatteringWard());
    }

    /** Broad face-on impact plane; the small facets only reinforce its forward read. */
    private static final class ShieldRam implements WorldVisualRecipe {
        private double radius(WorldVisualRenderContext c) {
            return c.event().scale() * (0.34 + c.easedProgress() * 0.52);
        }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Basis basis = Basis.around(c.event().direction());
            double radius = radius(c);
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.target(), basis.a(), basis.b(),
                    radius * 0.58, radius, 32, 0, c.rgb(), (float) (c.envelope() * 0.58));
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.target(), basis.a(), basis.b(),
                    radius * 0.91, radius, 32, 0, brighten(c.rgb(), 0.68), (float) (c.envelope() * 0.82));
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Basis basis = Basis.around(c.event().direction());
            double radius = radius(c);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.target(), basis.a(), basis.b(),
                    radius * 1.08, 0, TAU, 6, 4, 0.16, brighten(c.rgb(), 0.55),
                    (float) (c.envelope() * 0.88));
            ProceduralGeometry.spokes(c.pose(), c.lines(), c.target(), basis.a(), basis.b(),
                    radius * 0.42, radius * 1.20, 6, Math.PI / 6, c.rgb(),
                    (float) (c.envelope() * 0.52));
        }

        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            Basis basis = Basis.around(c.event().direction());
            double radius = radius(c);
            for (int i = 0; i < 8; i++) {
                double angle = i * TAU / 8 + c.progress() * 0.22;
                Vec3 radial = basis.a().scale(Math.cos(angle)).add(basis.b().scale(Math.sin(angle)));
                Vec3 point = c.target().add(radial.scale(radius * 0.76))
                        .subtract(c.event().direction().scale(0.05 + c.progress() * 0.12));
                double size = c.event().scale() * 0.032;
                ProceduralGeometry.shard(c.pose(), c.planes(), point,
                        radial.cross(c.event().direction()).normalize(), radial,
                        size, size * 1.6, size * 0.36, brighten(c.rgb(), 0.72),
                        (float) (c.envelope() * 0.48));
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Basis basis = Basis.around(c.event().direction());
            double radius = radius(c);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(),
                    c.target().subtract(c.event().direction().scale(0.11)), basis.a(), basis.b(),
                    radius * 0.72, -c.progress() * 0.65, TAU, 12, 2, 0.48,
                    brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.28));
        }
    }

    /** Eight low horizontal chevrons turn from inward to outward, then hover while Riposte is armed. */
    private static final class RiposteReady implements WorldVisualRecipe {
        private double age(WorldVisualRenderContext c) { return c.progress() * c.event().lifetimeTicks(); }
        private double radius(WorldVisualRenderContext c) {
            return c.event().scale() * (0.82 + Math.sin(age(c) * 0.16) * 0.035);
        }
        private float alpha(WorldVisualRenderContext c) {
            double age = age(c), remaining = (1 - c.progress()) * c.event().lifetimeTicks();
            return (float) (Math.min(1, age / 4) * Math.min(1, remaining / 6));
        }
        private Vec3 center(WorldVisualRenderContext c) { return c.source().add(0, 0.035, 0); }
        private Vec3 orientation(Vec3 radial, Vec3 tangent, double age) {
            double turn = com.mistaboom.essence_ascendance.visual.ProceduralMotion.smoothStep((age - 4) / 8);
            return radial.scale(-Math.cos(Math.PI * turn)).add(tangent.scale(Math.sin(Math.PI * turn))).normalize();
        }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            double age = age(c), radius = radius(c), rotation = age * 0.010;
            Vec3 center = center(c);
            for (int i = 0; i < 8; i++) {
                double angle = i * TAU / 8 + rotation;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
                Vec3 point = center.add(radial.scale(radius));
                chevronPlanes(c, point, orientation(radial, tangent, age), c.event().scale() * 0.25,
                        c.rgb(), alpha(c) * 0.72F);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            double age = age(c), radius = radius(c), rotation = age * 0.010;
            Vec3 center = center(c);
            for (int i = 0; i < 8; i++) {
                double angle = i * TAU / 8 + rotation;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
                Vec3 point = center.add(radial.scale(radius));
                chevronLines(c, point, orientation(radial, tangent, age), c.event().scale() * 0.25,
                        brighten(c.rgb(), 0.68), alpha(c));
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            double age = age(c), radius = radius(c), rotation = age * 0.010;
            Vec3 center = center(c);
            for (int i = 0; i < 8; i++) {
                double angle = i * TAU / 8 + rotation;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 point = center.add(radial.scale(radius));
                ProceduralGeometry.line(c.pose(), c.lines(), point.subtract(radial.scale(0.08)),
                        point.subtract(radial.scale(0.19)), brighten(c.rgb(), 0.72), alpha(c) * 0.25F);
            }
        }
    }

    /** Spending Riposte releases the armed ring radially; no struck creature is implied as its owner. */
    private static final class RiposteRelease implements WorldVisualRecipe {
        private double rush(WorldVisualRenderContext c) {
            return com.mistaboom.essence_ascendance.visual.ProceduralMotion.smoothStep(c.progress());
        }
        private float alpha(WorldVisualRenderContext c) {
            return (float) (Math.min(1, c.progress() * 16) * Math.pow(1 - c.progress(), 0.72));
        }
        private Vec3 center(WorldVisualRenderContext c) { return c.source().add(0, 0.035, 0); }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            double radius = c.event().scale() * (0.84 + rush(c) * 1.72);
            Vec3 center = center(c);
            for (int i = 0; i < 8; i++) {
                double angle = i * TAU / 8;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                chevronPlanes(c, center.add(radial.scale(radius)), radial,
                        c.event().scale() * (0.26 - c.progress() * 0.035),
                        c.rgb(), alpha(c) * 0.82F);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            double radius = c.event().scale() * (0.84 + rush(c) * 1.72);
            Vec3 center = center(c);
            for (int i = 0; i < 8; i++) {
                double angle = i * TAU / 8;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 point = center.add(radial.scale(radius));
                chevronLines(c, point, radial, c.event().scale() * (0.26 - c.progress() * 0.035),
                        brighten(c.rgb(), 0.72), alpha(c));
                ProceduralGeometry.line(c.pose(), c.lines(), point.subtract(radial.scale(0.12)),
                        point.subtract(radial.scale(0.40 + rush(c) * 0.18)),
                        brighten(c.rgb(), 0.55), alpha(c) * 0.38F);
            }
        }
    }

    private static void chevronPlanes(WorldVisualRenderContext c, Vec3 center, Vec3 direction,
                                       double length, int rgb, float alpha) {
        Vec3 forward = new Vec3(direction.x, 0, direction.z).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 tip = center.add(forward.scale(length * 0.56));
        Vec3 tail = center.subtract(forward.scale(length * 0.34));
        for (int sign : new int[]{-1, 1}) {
            Vec3 wing = tail.add(side.scale(sign * length * 0.46));
            Vec3 segment = tip.subtract(wing).normalize();
            Vec3 lateral = Y.cross(segment).normalize();
            ProceduralGeometry.taperedPlane(c.pose(), c.planes(), wing, tip, lateral,
                    length * 0.080, length * 0.055, sign < 0 ? rgb : brighten(rgb, 0.44), alpha);
        }
    }

    private static void chevronLines(WorldVisualRenderContext c, Vec3 center, Vec3 direction,
                                      double length, int rgb, float alpha) {
        Vec3 forward = new Vec3(direction.x, 0, direction.z).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 tip = center.add(forward.scale(length * 0.56));
        Vec3 tail = center.subtract(forward.scale(length * 0.34));
        ProceduralGeometry.line(c.pose(), c.lines(), tail.add(side.scale(length * 0.46)), tip, rgb, alpha);
        ProceduralGeometry.line(c.pose(), c.lines(), tail.subtract(side.scale(length * 0.46)), tip, rgb, alpha);
    }

    /** One upright, beveled shield rushes to each confirmed target; only a short wake follows it. */
    private record TravelingShield(boolean reprisal) implements WorldVisualRecipe {
        private static final double[] EDGE_X = {-0.44, 0.44, 0.48, 0.36, 0, -0.36, -0.48};
        private static final double[] EDGE_Y = {0.50, 0.50, 0.20, -0.20, -0.62, -0.20, 0.20};

        private double travel(WorldVisualRenderContext c) {
            return com.mistaboom.essence_ascendance.visual.ProceduralMotion.smoothStep(
                    Math.clamp(c.progress() / 0.70, 0, 1));
        }
        private Vec3 normal(WorldVisualRenderContext c) {
            Vec3 delta = c.target().subtract(c.position());
            return delta.lengthSqr() > 1.0E-6 ? delta.normalize() : c.event().direction();
        }
        private Vec3 center(WorldVisualRenderContext c) {
            return c.position().lerp(c.target(), 0.06 + 0.94 * travel(c));
        }
        private double size(WorldVisualRenderContext c) {
            return c.event().scale() * (reprisal ? 0.84 : 1.10) * (0.88 + 0.12 * travel(c));
        }
        private Vec3 vertex(Vec3 center, Basis basis, double size, int index) {
            int i = Math.floorMod(index, EDGE_X.length);
            return center.add(basis.a().scale(EDGE_X[i] * size))
                    .add(basis.b().scale(EDGE_Y[i] * size));
        }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 normal = normal(c), center = center(c);
            Basis basis = Basis.upright(normal);
            double size = size(c);
            Vec3 ridge = center.add(normal.scale(size * 0.16));
            float alpha = (float) c.envelope();
            for (int i = 0; i < EDGE_X.length; i++) {
                Vec3 outer = vertex(center, basis, size, i);
                Vec3 next = vertex(center, basis, size, i + 1);
                Vec3 inner = vertex(center, basis, size * 0.80, i);
                Vec3 innerNext = vertex(center, basis, size * 0.80, i + 1);
                ProceduralGeometry.quad(c.pose(), c.planes(), outer, next, innerNext, inner,
                        brighten(c.rgb(), 0.38), alpha * 0.84F);
                ProceduralGeometry.quad(c.pose(), c.planes(), inner, innerNext, ridge, ridge,
                        brighten(c.rgb(), i % 2 == 0 ? 0.18 : 0.48), alpha * (i % 2 == 0 ? 0.32F : 0.48F));
            }
            if (c.progress() > 0.70) {
                double impact = (c.progress() - 0.70) / 0.30;
                double radius = size * (0.32 + impact * 0.46);
                ProceduralGeometry.annulus(c.pose(), c.planes(), c.target(), basis.a(), basis.b(),
                        radius * 0.88, radius, 24, 0, c.rgb(), alpha * 0.44F);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 normal = normal(c), center = center(c);
            Basis basis = Basis.upright(normal);
            double size = size(c);
            for (int i = 0; i < EDGE_X.length; i++)
                ProceduralGeometry.line(c.pose(), c.lines(), vertex(center, basis, size, i),
                        vertex(center, basis, size, i + 1), brighten(c.rgb(), 0.78),
                        (float) (c.envelope() * 0.96));
            // A raised central spine retains the shield's volume when seen from the side.
            Vec3 ridge = center.add(normal.scale(size * 0.16));
            ProceduralGeometry.line(c.pose(), c.lines(), ridge.add(basis.b().scale(size * 0.38)),
                    ridge.subtract(basis.b().scale(size * 0.42)), brighten(c.rgb(), 0.62),
                    (float) (c.envelope() * 0.64));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 normal = normal(c), center = center(c);
            Basis basis = Basis.upright(normal);
            double size = size(c);
            Vec3 ridge = center.add(normal.scale(size * 0.16));
            for (int i = 0; i < EDGE_X.length; i++) {
                ProceduralGeometry.line(c.pose(), c.lines(), vertex(center, basis, size * 0.66, i),
                        vertex(center, basis, size * 0.66, i + 1), brighten(c.rgb(), 0.70),
                        (float) (c.envelope() * 0.25));
                if (i % 2 == 0)
                    ProceduralGeometry.line(c.pose(), c.lines(), ridge,
                            vertex(center, basis, size * 0.78, i), brighten(c.rgb(), 0.60),
                            (float) (c.envelope() * 0.23));
            }
            if (c.progress() < 0.70) {
                Vec3 wake = center.subtract(normal.scale(size * 0.24));
                for (int i = 0; i < EDGE_X.length; i++)
                    ProceduralGeometry.line(c.pose(), c.lines(), vertex(wake, basis, size * 0.88, i),
                            vertex(wake, basis, size * 0.88, i + 1), c.rgb(),
                            (float) (c.envelope() * 0.18));
            }
        }
    }

    /** Fast, hollow comic-impact silhouette with angular cracks and a few tiny breakaway facets. */
    private static final class StoredForceImpact implements WorldVisualRecipe {
        private double expansion(WorldVisualRenderContext c) {
            double t = Math.clamp(c.progress() / 0.32, 0, 1);
            return 1 - (1 - t) * (1 - t) * (1 - t);
        }
        private double radius(WorldVisualRenderContext c) {
            return c.event().scale() * (0.20 + 0.76 * expansion(c) + 0.12 * c.progress());
        }
        private float alpha(WorldVisualRenderContext c) {
            return (float) (Math.min(1, c.progress() * 24) * Math.pow(1 - c.progress(), 1.25));
        }
        private Basis facing(WorldVisualRenderContext c) {
            // World-space billboard: readable comic silhouette, still occluded by the world.
            return Basis.upright(c.target().scale(-1));
        }
        private Vec3 point(WorldVisualRenderContext c, Basis basis, int vertex, double ratio) {
            int i = Math.floorMod(vertex, 24);
            double angle = i * TAU / 24 + 0.12;
            double jag = i % 2 == 0 ? 0.88 + 0.22 * c.variation(i + 300) : 0.49 + 0.15 * c.variation(i + 300);
            double r = radius(c) * jag * ratio;
            return c.target().add(basis.a().scale(Math.cos(angle) * r * 1.13))
                    .add(basis.b().scale(Math.sin(angle) * r * 0.86));
        }
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Basis basis = facing(c);
            for (int i = 0; i < 24; i++)
                ProceduralGeometry.quad(c.pose(), c.planes(), point(c, basis, i, 1),
                        point(c, basis, i + 1, 1), point(c, basis, i + 1, 0.80),
                        point(c, basis, i, 0.80), brighten(c.rgb(), 0.38), alpha(c) * 0.88F);
        }
        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Basis basis = facing(c);
            for (int i = 0; i < 24; i++)
                ProceduralGeometry.line(c.pose(), c.lines(), point(c, basis, i, 1),
                        point(c, basis, i + 1, 1), brighten(c.rgb(), 0.82), alpha(c));
            for (int i = 0; i < 6; i++) {
                Vec3 root = point(c, basis, i * 4, 0.30);
                Vec3 elbow = point(c, basis, i * 4 + 1, 0.72);
                Vec3 tip = point(c, basis, i * 4, 0.85);
                ProceduralGeometry.line(c.pose(), c.lines(), root, elbow, c.rgb(), alpha(c) * 0.72F);
                ProceduralGeometry.line(c.pose(), c.lines(), elbow, tip,
                        brighten(c.rgb(), 0.62), alpha(c) * 0.84F);
            }
            if (c.event().parameterA() > 0.5F)
                ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.source(),
                        basis.a(), basis.b(), 0.34, 0, TAU, 5, 3, 0.30,
                        c.rgb(), alpha(c) * 0.52F);
        }
        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            Basis basis = facing(c);
            for (int i = 0; i < 6; i++) {
                Vec3 pos = point(c, basis, i * 4, 1.05 + c.progress() * 0.20);
                double size = c.event().scale() * 0.025;
                ProceduralGeometry.shard(c.pose(), c.planes(), pos, basis.a(), basis.b(),
                        size, size * 1.8, size * 0.50, brighten(c.rgb(), 0.66), alpha(c) * 0.62F);
            }
        }
        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Basis basis = facing(c);
            for (int i = 0; i < 24; i += 2)
                ProceduralGeometry.line(c.pose(), c.lines(), point(c, basis, i, 1.13),
                        point(c, basis, i + 1, 1.13), brighten(c.rgb(), 0.70), alpha(c) * 0.30F);
        }
    }

    /** Pure State rejects inward; Status Mirror adds a clearly legible counter-direction return. */
    private static final class StatusRejection implements WorldVisualRecipe {
        private boolean mirror(WorldVisualRenderContext c) { return c.event().parameterA() > 0.5F; }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 center = c.source();
            Vec3 end = c.target();
            Vec3 direction = end.subtract(center).lengthSqr() > 1.0E-6
                    ? end.subtract(center).normalize() : c.event().direction();
            Basis basis = Basis.around(direction);
            double radius = c.event().scale() * (0.34 + c.easedProgress() * 0.24);
            ProceduralGeometry.diamondRing(c.pose(), c.planes(), center, basis.a(), basis.b(),
                    radius, radius * 1.16, radius * 0.58, radius * 0.69,
                    c.rgb(), (float) (c.envelope() * 0.64));
            if (mirror(c) && end.distanceToSqr(center) > 1.0E-6) {
                ProceduralGeometry.beam(c.pose(), c.planes(), center, end, basis.a(),
                        c.event().scale() * 0.027, c.rgb(), (float) (c.envelope() * 0.46));
                Vec3 node = center.lerp(end, 0.18 + c.easedProgress() * 0.70);
                ProceduralGeometry.shard(c.pose(), c.planes(), node, basis.a(), direction,
                        c.event().scale() * 0.045, c.event().scale() * 0.11,
                        c.event().scale() * 0.026, brighten(c.rgb(), 0.76),
                        (float) (c.envelope() * 0.82));
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 center = c.source();
            Vec3 end = c.target();
            Vec3 direction = end.subtract(center).lengthSqr() > 1.0E-6
                    ? end.subtract(center).normalize() : c.event().direction();
            Basis basis = Basis.around(direction);
            double radius = c.event().scale() * (0.40 + c.easedProgress() * 0.30);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, basis.a(), basis.b(),
                    radius, c.progress() * 0.45, TAU, 6, 4, 0.20,
                    brighten(c.rgb(), 0.58), (float) (c.envelope() * 0.90));
            ProceduralGeometry.spokes(c.pose(), c.lines(), center, basis.a(), basis.b(),
                    radius * 0.36, radius * 1.08, 6, Math.PI / 6, c.rgb(),
                    (float) (c.envelope() * 0.46));
            if (mirror(c) && end.distanceToSqr(center) > 1.0E-6)
                ProceduralGeometry.line(c.pose(), c.lines(), center, end, brighten(c.rgb(), 0.55),
                        (float) (c.envelope() * 0.70));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 center = c.source();
            Vec3 end = c.target();
            Vec3 delta = end.subtract(center);
            Basis basis = Basis.around(delta.lengthSqr() > 1.0E-6 ? delta : c.event().direction());
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, basis.a(), basis.b(),
                    c.event().scale() * (0.25 + c.easedProgress() * 0.22), -c.progress(), TAU,
                    12, 2, 0.48, brighten(c.rgb(), 0.74), (float) (c.envelope() * 0.28));
            if (mirror(c) && delta.lengthSqr() > 1.0E-6) {
                for (int side : new int[]{-1, 1}) {
                    Vec3 offset = basis.a().scale(side * c.event().scale() * 0.048);
                    ProceduralGeometry.line(c.pose(), c.lines(), center.add(offset), end.add(offset),
                            brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.23));
                }
            }
        }
    }

    /** Sparse large boundary for the ward break, with small outward facets and no opaque volume. */
    private static final class ShatteringWard implements WorldVisualRecipe {
        private double radius(WorldVisualRenderContext c) {
            return c.event().scale() * (0.16 + c.easedProgress() * 0.84);
        }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            double radius = radius(c);
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.source(), X, Z,
                    Math.max(0, radius - c.event().scale() * 0.045), radius,
                    40, 0, c.rgb(), (float) (c.envelope() * 0.42));
            for (int i = 0; i < 10; i++) {
                double angle = i * TAU / 10 + c.variation(i) * 0.12;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 point = c.source().add(radial.scale(radius * (0.86 + c.variation(i + 20) * 0.12)))
                        .add(0, c.event().scale() * (0.025 + c.variation(i + 40) * 0.045), 0);
                double size = c.event().scale() * (0.018 + c.variation(i + 60) * 0.010);
                ProceduralGeometry.shard(c.pose(), c.planes(), point, radial.cross(Y), radial.add(Y.scale(0.28)).normalize(),
                        size, size * 1.9, size * 0.42, brighten(c.rgb(), 0.62),
                        (float) (c.envelope() * 0.60));
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            double radius = radius(c);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.source(), X, Z,
                    radius * 1.03, Math.PI / 10, TAU, 10, 4, 0.20,
                    brighten(c.rgb(), 0.50), (float) (c.envelope() * 0.88));
            ProceduralGeometry.spokes(c.pose(), c.lines(), c.source(), X, Z,
                    radius * 0.52, radius * 1.08, 10, 0,
                    c.rgb(), (float) (c.envelope() * 0.46));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            double radius = radius(c);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.source().add(0, 0.05, 0), X, Z,
                    radius * 0.72, -c.progress() * 0.8, TAU, 16, 2, 0.52,
                    brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.24));
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.source(), X, Y,
                    Math.min(radius * 0.44, c.event().scale() * 0.72), c.progress() * 0.55, TAU,
                    8, 2, 0.48, brighten(c.rgb(), 0.64), (float) (c.envelope() * 0.22));
        }
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
        private static Basis upright(Vec3 direction) {
            Vec3 n = direction.lengthSqr() < 1.0E-8 ? Z : direction.normalize();
            Vec3 reference = Math.abs(n.y) < 0.94 ? Y : X;
            Vec3 right = reference.cross(n).normalize();
            return new Basis(right, n.cross(right).normalize());
        }

        private static Basis around(Vec3 direction) {
            Vec3 n = direction.lengthSqr() < 1.0E-8 ? Y : direction.normalize();
            Vec3 reference = Math.abs(n.y) < 0.88 ? Y : X;
            Vec3 a = n.cross(reference).normalize();
            return new Basis(a, n.cross(a).normalize());
        }
    }
}
