package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Combat compositions hosted by the shared transient runtime and procedural primitive toolkit. */
final class CombatWorldVisualRecipes {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final double TAU = Math.PI * 2.0;

    private CombatWorldVisualRecipes() { }

    static void registerAll() {
        WorldVisualRecipes.register(TransientVisualIds.WORLD_KINDLING, new RisingDiamonds(true));
        WorldVisualRecipes.register(TransientVisualIds.WORLD_IGNITION, new Ignition());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_COMBUSTION, new FireTorus());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_FROSTBITE, new RisingDiamonds(false));
        WorldVisualRecipes.register(TransientVisualIds.WORLD_FREEZE, new Freeze());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_SHATTER, new ShardSphere());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_STATIC_ARC, new StaticArc());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_ROOTING, new Rooting());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_EXPLOSIVE_PAYLOAD, new ExplosivePayload());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_PROJECTILE_GUIDANCE, new ProjectileGuidance());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_PROJECTILE_REDIRECT, new ProjectileRedirect());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_PROJECTILE_PIERCE, new ProjectilePierce());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_PROJECTILE_DRAG, new ProjectileDrag());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_PROJECTILE_INTERCEPT, new ProjectileShockwave());
    }

    /** Heat rises; snowflake-like Chill descends. Both follow the creature with staggered births. */
    private record RisingDiamonds(boolean fire) implements WorldVisualRecipe {
        private double phase(WorldVisualRenderContext c, int i) {
            return (c.progress() - i * 0.055) / 0.62;
        }
        private Vec3 point(WorldVisualRenderContext c, int i, double p) {
            double angle = i * 2.39996 + c.variation(i) * 0.45;
            double radius = Math.max(0.24, c.event().parameterB() * 0.58);
            double flutter = Math.sin(p * (fire ? 11 : 5) + i * 1.7) * (fire ? 0.12 : 0.09);
            return c.target().add(Math.cos(angle) * (radius + flutter),
                    c.event().parameterA() * (fire ? p - 0.5 : 0.5 - p) + 0.04,
                    Math.sin(angle) * (radius + flutter));
        }
        @Override public int repeatIntervalTicks() { return fire ? 18 : 36; }
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            for (int i = 0; i < 7; i++) {
                double p = phase(c, i);
                if (p <= 0 || p >= 1) continue;
                double fade = Math.min(1, p * 9) * Math.min(1, (1 - p) * 5);
                double roll = Math.sin(p * (fire ? 9 : 5) + i) * (fire ? 0.55 : 0.35);
                Vec3 right = new Vec3(Math.cos(i * 2.39996), 0, Math.sin(i * 2.39996));
                Vec3 up = Y.scale(Math.cos(roll)).add(right.scale(Math.sin(roll)));
                Vec3 across = right.scale(Math.cos(roll)).subtract(Y.scale(Math.sin(roll)));
                double size = c.event().scale() * (0.052 + c.variation(i + 20) * 0.024);
                Vec3 pos = point(c, i, p);
                ProceduralGeometry.shard(c.pose(), c.planes(), pos, across, up,
                        size, size * (fire ? 1.85 : 1.1), size * (fire ? 0.42 : 0.22),
                        c.rgb(), (float) (fade * 0.80));
                ProceduralGeometry.diamond(c.pose(), c.planes(), pos, across, up,
                        size * 0.28, size * 0.9, brighten(c.rgb(), 0.70), (float) (fade * 0.76));
            }
        }
        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            for (int i = 0; i < 7; i++) {
                double p = phase(c, i);
                if (p <= 0.06 || p >= 1) continue;
                float alpha = (float) (Math.min(1, (1 - p) * 5) * 0.30);
                Vec3 current = point(c, i, p);
                Vec3 previous = point(c, i, p - (fire ? 0.055 : 0.018));
                ProceduralGeometry.line(c.pose(), c.lines(), previous, current, brighten(c.rgb(), 0.60), alpha);
                if (!fire) {
                    // Six branched crystal arms are close detail, never needed to read the falling diamond.
                    Vec3 right = new Vec3(Math.cos(i * 2.39996), 0, Math.sin(i * 2.39996));
                    for (int arm = 0; arm < 6; arm++) {
                        double angle = arm * TAU / 6 + Math.sin(p * 5 + i) * 0.35;
                        Vec3 ray = right.scale(Math.cos(angle)).add(Y.scale(Math.sin(angle)));
                        Vec3 cross = right.scale(-Math.sin(angle)).add(Y.scale(Math.cos(angle)));
                        Vec3 end = current.add(ray.scale(0.095));
                        Vec3 elbow = current.add(ray.scale(0.058));
                        ProceduralGeometry.line(c.pose(), c.lines(), current, end, 0xD8F5FF, alpha);
                        for (int side : new int[]{-1, 1})
                            ProceduralGeometry.line(c.pose(), c.lines(), elbow,
                                    elbow.subtract(ray.scale(0.018)).add(cross.scale(side * 0.020)),
                                    0xD8F5FF, alpha * 0.8F);
                    }
                }
            }
        }
    }

    /** A rolling tube of flame facets expands in three dimensions around an open center. */
    private static final class FireTorus implements WorldVisualRecipe {
        private Vec3 point(WorldVisualRenderContext c, int i, boolean small) {
            double p = c.easedProgress();
            double angle = i * TAU / (small ? 32 : 20) + c.variation(i) * 0.10;
            double roll = c.progress() * 7.0 + i * 1.71;
            double radius = c.event().scale() * (0.18 + p * 1.08);
            double tube = c.event().scale() * (0.10 + 0.10 * (1 - p));
            return c.position().add(Math.cos(angle) * (radius + Math.cos(roll) * tube),
                    Math.sin(roll) * tube * 1.5 + p * 0.25,
                    Math.sin(angle) * (radius + Math.cos(roll) * tube));
        }
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            double size = c.event().scale() * (0.09 - c.progress() * 0.027);
            for (int i = 0; i < 20; i++) {
                double angle = i * TAU / 20;
                double roll = c.progress() * 7 + i * 1.71;
                Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
                Vec3 up = Y.scale(Math.cos(roll)).add(new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(Math.sin(roll)));
                Vec3 pos = point(c, i, false);
                ProceduralGeometry.shard(c.pose(), c.planes(), pos, tangent, up,
                        size * (i % 4 == 0 ? 1.4 : 0.75), size * 1.8, size * 0.7,
                        i % 3 == 0 ? brighten(c.rgb(), 0.48) : c.rgb(), (float) (c.envelope() * 0.82));
                ProceduralGeometry.shard(c.pose(), c.planes(), pos, tangent, up,
                        size * 0.24, size * 0.85, size * 0.20, 0xFFE2A0, (float) (c.envelope() * 0.9));
            }
        }
        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            double r = c.event().scale() * (0.22 + c.easedProgress() * 1.20);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.position(), X, Z, r,
                    c.progress(), TAU, 10, 3, 0.28, c.rgb(), (float) (c.envelope() * 0.65));
        }
        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            if (c.progress() < 0.16) return;
            for (int i = 0; i < 32; i++) {
                Vec3 pos = point(c, i, true).add(0, 0.14 * Math.sin(i + c.progress() * 8), 0);
                double size = c.event().scale() * (0.011 + c.variation(i + 80) * 0.013);
                ProceduralGeometry.shard(c.pose(), c.planes(), pos, X, Y, size, size * 2, size * 0.5,
                        brighten(c.rgb(), 0.60), (float) (c.envelope() * 0.6));
            }
        }
        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            double r = c.event().scale() * (0.18 + c.easedProgress() * 0.98);
            for (int lane = 0; lane < 2; lane++)
                ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.position().add(0, lane * 0.12, 0), X, Z,
                        r * (0.83 + lane * 0.25), -c.progress() * 1.8, TAU, 14, 3, 0.45,
                        brighten(c.rgb(), 0.62), (float) (c.envelope() * 0.24));
            for (int i = 0; i < 20; i++) {
                Vec3 p = point(c, i, false);
                ProceduralGeometry.line(c.pose(), c.lines(), p, p.lerp(c.position(), 0.18),
                        brighten(c.rgb(), 0.50), (float) (c.envelope() * 0.25));
            }
        }
    }

    private static final class ShardSphere implements WorldVisualRecipe {
        private Vec3 ray(int i, int count) {
            double y = 1 - 2 * (i + 0.5) / count;
            double r = Math.sqrt(1 - y * y), angle = i * 2.39996323;
            return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
        }
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            double radius = c.event().scale() * (0.15 + c.easedProgress() * 1.15);
            for (int i = 0; i < 26; i++) {
                Vec3 direction = ray(i, 26);
                Basis basis = Basis.around(direction);
                Vec3 pos = c.position().add(direction.scale(radius * (0.85 + c.variation(i) * 0.2)));
                double width = c.event().scale() * (i % 5 == 0 ? 0.072 : 0.036);
                ProceduralGeometry.shard(c.pose(), c.planes(), pos, basis.a(), direction,
                        width, width * (2.0 + c.variation(i + 40)), width * 0.6,
                        brighten(c.rgb(), (i % 3) * 0.20), (float) (c.envelope() * 0.85));
            }
        }
        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            double r = c.event().scale() * (0.18 + c.easedProgress() * 1.12);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.position(), X, Z, r,
                    0.25, TAU, 10, 3, 0.4, c.rgb(), (float) (c.envelope() * 0.7));
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.position(), X, Y, r * 0.88,
                    0.6, TAU, 8, 2, 0.6, brighten(c.rgb(), 0.45), (float) (c.envelope() * 0.38));
        }
        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            if (c.progress() < 0.12) return;
            for (int i = 0; i < 38; i++) {
                Vec3 ray = ray(i, 38);
                Vec3 pos = c.position().add(ray.scale(c.event().scale() * (0.12 + c.easedProgress() * 0.92)));
                ProceduralGeometry.shard(c.pose(), c.planes(), pos, Basis.around(ray).a(), ray,
                        0.018, 0.056, 0.011, brighten(c.rgb(), 0.65), (float) (c.envelope() * 0.55));
            }
        }
        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            double radius = c.event().scale() * (0.12 + c.easedProgress());
            for (int i = 0; i < 26; i++) {
                Vec3 a = c.position().add(ray(i, 26).scale(radius * 0.6));
                Vec3 b = c.position().add(ray(i, 26).scale(radius));
                Vec3 elbow = a.lerp(b, 0.5).add(Basis.around(ray(i, 26)).a().scale(0.05));
                ProceduralGeometry.line(c.pose(), c.lines(), a, elbow, brighten(c.rgb(), 0.70), (float) (c.envelope() * 0.22));
                ProceduralGeometry.line(c.pose(), c.lines(), elbow, b, brighten(c.rgb(), 0.70), (float) (c.envelope() * 0.28));
            }
        }
    }

    private static final class Ignition implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            double scale = context.event().scale();
            float alpha = (float) (context.envelope() * 0.72);
            for (int i = 0; i < 6; i++) {
                double angle = context.variation(i) * 0.35 + i * TAU / 6.0;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 point = center.add(radial.scale(scale * (0.12 + 0.22 * context.easedProgress())))
                        .add(0, scale * (0.06 + (0.18 + 0.05 * (i & 1)) * context.easedProgress()), 0);
                ProceduralGeometry.diamond(context.pose(), context.planes(), point,
                        radial.cross(Y), Y, scale * 0.052, scale * 0.12,
                        brighten(context.rgb(), i % 2 == 0 ? 0.48 : 0.22), alpha);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            double radius = context.event().scale() * (0.16 + context.easedProgress() * 0.48);
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), context.position(), X, Z,
                    radius, context.variation(8) * TAU, TAU, 6, 3, 0.24,
                    context.rgb(), (float) (context.envelope() * 0.66));
            ProceduralGeometry.spokes(context.pose(), context.lines(), context.position(), X, Z,
                    radius * 0.62, radius * 1.08, 6, context.variation(9) * TAU,
                    brighten(context.rgb(), 0.38), (float) (context.envelope() * 0.38));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            double radius = context.event().scale() * (0.12 + context.easedProgress() * 0.34);
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), context.position().add(0, 0.08, 0),
                    X, Z, radius * 0.72, -context.progress() * 1.8, TAU,
                    8, 2, 0.38, brighten(context.rgb(), 0.58), (float) (context.envelope() * 0.24));
        }
    }

    private static final class Freeze implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 center = context.position().add(0, context.event().scale() * 0.15, 0);
            double scale = context.event().scale();
            float alpha = (float) (context.envelope() * 0.68);
            for (int i = 0; i < 8; i++) {
                double angle = i * TAU / 8.0 + context.variation(i) * 0.22;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 point = center.add(radial.scale(scale * (0.16 + context.easedProgress() * 0.34)))
                        .add(0, scale * (context.variation(i + 12) - 0.35) * 0.34, 0);
                ProceduralGeometry.diamond(context.pose(), context.planes(), point,
                        radial.cross(Y), radial.add(Y.scale(0.45)).normalize(),
                        scale * 0.065, scale * (0.16 + context.variation(i + 24) * 0.10),
                        brighten(context.rgb(), 0.36), alpha);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            double radius = context.event().scale() * (0.18 + context.easedProgress() * 0.56);
            float alpha = (float) (context.envelope() * 0.72);
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), center, X, Z,
                    radius, context.variation(30) * TAU, TAU, 8, 3, 0.34,
                    context.rgb(), alpha);
            ProceduralGeometry.spokes(context.pose(), context.lines(), center, X, Z,
                    radius * 0.46, radius * 1.05, 8, Math.PI / 8.0,
                    brighten(context.rgb(), 0.46), alpha * 0.62F);
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            crackLattice(context, context.position(), context.event().scale() * (0.28 + context.easedProgress() * 0.46), 12,
                    brighten(context.rgb(), 0.66), (float) (context.envelope() * 0.25));
        }
    }

    private static final class StaticArc implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 start = context.source(), end = context.target();
            if (start.distanceToSqr(end) < 1.0E-6) return;
            List<Vec3> path = jaggedPath(context, start, end, 5, context.event().scale() * 0.055, 0);
            Basis basis = Basis.around(end.subtract(start));
            float alpha = (float) (context.envelope() * 0.74);
            for (int i = 1; i < path.size(); i++) {
                ProceduralGeometry.beam(context.pose(), context.planes(), path.get(i - 1), path.get(i), basis.a(),
                        context.event().scale() * 0.018, brighten(context.rgb(), 0.48), alpha);
            }
            double node = context.event().scale() * 0.072;
            ProceduralGeometry.diamondRing(context.pose(), context.planes(), end, basis.a(), basis.b(),
                    node, node, node * 0.42, node * 0.42, brighten(context.rgb(), 0.70), alpha * 0.82F);
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Vec3 start = context.source(), end = context.target();
            if (start.distanceToSqr(end) < 1.0E-6) return;
            ProceduralGeometry.ribbon(context.pose(), context.lines(),
                    jaggedPath(context, start, end, 7, context.event().scale() * 0.085, 20),
                    context.rgb(), (float) (context.envelope() * 0.92));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            Vec3 start = context.source(), end = context.target();
            if (start.distanceToSqr(end) < 1.0E-6) return;
            Basis basis = Basis.around(end.subtract(start));
            float alpha = (float) (context.envelope() * 0.26);
            for (int filament = -1; filament <= 1; filament += 2) {
                List<Vec3> path = jaggedPath(context, start, end, 9,
                        context.event().scale() * 0.035, 40 + filament);
                Vec3 offset = basis.b().scale(filament * context.event().scale() * 0.022);
                ProceduralGeometry.ribbon(context.pose(), context.lines(), path.stream().map(point -> point.add(offset)).toList(),
                        brighten(context.rgb(), 0.72), alpha);
            }
            double nodeRadius = context.event().scale() * (0.08 + 0.03 * context.progress());
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), end, basis.a(), basis.b(),
                    nodeRadius, context.progress() * 2.4, TAU, 6, 2, 0.44,
                    brighten(context.rgb(), 0.62), alpha * 1.25F);
        }
    }

    private static final class Rooting implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            double scale = context.event().scale();
            float alpha = (float) (context.envelope() * 0.62);
            for (int i = 0; i < 6; i++) {
                double angle = i * TAU / 6.0;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 point = center.add(radial.scale(scale * 0.42)).add(0, scale * (0.05 + 0.32 * context.easedProgress()), 0);
                ProceduralGeometry.diamond(context.pose(), context.planes(), point, radial.cross(Y), Y,
                        scale * 0.045, scale * 0.13, brighten(context.rgb(), 0.30), alpha);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            double scale = context.event().scale();
            double radius = scale * (0.22 + 0.28 * context.easedProgress());
            float alpha = (float) (context.envelope() * 0.70);
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), center.add(0, 0.02, 0), X, Z,
                    radius, Math.PI / 6.0, TAU, 6, 3, 0.20, context.rgb(), alpha);
            for (int i = 0; i < 6; i++) {
                double angle = i * TAU / 6.0;
                Vec3 foot = center.add(Math.cos(angle) * radius, 0.02, Math.sin(angle) * radius);
                Vec3 shoulder = center.add(Math.cos(angle) * radius * 0.72,
                        scale * (0.50 + 0.26 * context.easedProgress()), Math.sin(angle) * radius * 0.72);
                ProceduralGeometry.line(context.pose(), context.lines(), foot, shoulder,
                        brighten(context.rgb(), 0.24), alpha);
                if ((i & 1) == 0) ProceduralGeometry.line(context.pose(), context.lines(), shoulder,
                        center.add(0, scale * 0.62, 0), brighten(context.rgb(), 0.42), alpha * 0.52F);
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            Vec3 center = context.position().add(0, 0.025, 0);
            double scale = context.event().scale();
            float alpha = (float) (context.envelope() * 0.23);
            ProceduralGeometry.ring(context.pose(), context.lines(), center, X, Z,
                    scale * 0.31, 18, brighten(context.rgb(), 0.52), alpha);
            ProceduralGeometry.spokes(context.pose(), context.lines(), center, X, Z,
                    scale * 0.08, scale * 0.48, 12, Math.PI / 12.0,
                    brighten(context.rgb(), 0.62), alpha * 0.76F);
        }
    }

    private static final class ExplosivePayload implements WorldVisualRecipe {
        @Override public void renderDetailPlanes(WorldVisualRenderContext context) {
            if (context.progress() < 0.18) return;
            Vec3 forward = context.event().direction();
            Basis basis = Basis.around(forward);
            double scale = context.event().scale();
            double p = (context.progress() - 0.18) / 0.82;
            for (int i = 0; i < 24; i++) {
                double angle = i * TAU / 24;
                Vec3 spread = basis.a().scale(Math.cos(angle)).add(basis.b().scale(Math.sin(angle)));
                Vec3 ray = forward.scale(0.3 + context.variation(i + 90)).add(spread).normalize();
                Vec3 point = context.position().add(ray.scale(scale * (0.25 + p * 1.15)));
                ProceduralGeometry.shard(context.pose(), context.planes(), point, Basis.around(ray).a(), ray,
                        scale * 0.018, scale * 0.055, scale * 0.01, brighten(context.rgb(), 0.70),
                        (float) (context.envelope() * 0.58));
            }
        }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            Vec3 forward = context.event().direction();
            Basis basis = Basis.around(forward);
            double scale = context.event().scale();
            double p = context.easedProgress();
            float alpha = (float) (context.envelope() * 0.80);
            for (int i = 0; i < 9; i++) {
                double angle = i * TAU / 9.0 + context.variation(i) * 0.20;
                Vec3 spread = basis.a().scale(Math.cos(angle)).add(basis.b().scale(Math.sin(angle)));
                Vec3 ray = forward.scale(0.62 + context.variation(i + 12) * 0.42).add(spread.scale(0.74)).normalize();
                Vec3 point = center.add(ray.scale(scale * (0.18 + p * (0.72 + context.variation(i + 24) * 0.24))));
                ProceduralGeometry.shard(context.pose(), context.planes(), point,
                        Basis.around(ray).a(), ray, scale * (i % 3 == 0 ? 0.17 : 0.085),
                        scale * (0.24 + context.variation(i + 36) * 0.16), scale * 0.065,
                        brighten(context.rgb(), 0.28 + (i % 3 == 0 ? 0.30 : 0.0)), alpha);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            Vec3 forward = context.event().direction();
            Basis basis = Basis.around(forward);
            double scale = context.event().scale();
            double p = context.easedProgress();
            float alpha = (float) (context.envelope() * 0.82);
            double radius = scale * (0.22 + p * 0.78);
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), center, basis.a(), basis.b(),
                    radius, Math.PI / 8.0, TAU, 6, 4, 0.16, context.rgb(), alpha);
            ProceduralGeometry.spokes(context.pose(), context.lines(), center, basis.a(), basis.b(),
                    radius * 0.34, radius * 1.24, 8, 0,
                    brighten(context.rgb(), 0.42), alpha * 0.76F);
            if (p > 0.28) {
                double delayed = (p - 0.28) / 0.72;
                ProceduralGeometry.brokenRing(context.pose(), context.lines(), center.add(0, 0.025, 0), X, Z,
                        scale * (0.18 + delayed * 0.92), context.variation(60) * TAU, TAU,
                        8, 3, 0.34, brighten(context.rgb(), 0.22), alpha * 0.68F);
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            Vec3 forward = context.event().direction();
            Basis basis = Basis.around(forward);
            double scale = context.event().scale();
            float alpha = (float) (context.envelope() * 0.24);
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), center, basis.a(), basis.b(),
                    scale * (0.12 + context.easedProgress() * 0.48), -context.progress() * 1.4, TAU,
                    12, 2, 0.50, brighten(context.rgb(), 0.68), alpha);
            ProceduralGeometry.spokes(context.pose(), context.lines(), center, X, Z,
                    scale * 0.16, scale * (0.42 + context.easedProgress() * 0.56), 12,
                    context.variation(70) * TAU, brighten(context.rgb(), 0.54), alpha * 0.72F);
        }
    }

    private static final class ProjectileGuidance implements WorldVisualRecipe {
        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Vec3 start = context.source(), end = context.target();
            if (start.distanceToSqr(end) < 1.0E-6) return;
            Basis basis = Basis.around(end.subtract(start));
            double radius = context.event().scale() * (0.08 + context.progress() * 0.03);
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), start, basis.a(), basis.b(),
                    radius, context.progress() * 1.4, TAU, 4, 2, 0.38,
                    context.rgb(), (float) (context.envelope() * 0.42));
            Vec3 lead = start.add(end.subtract(start).normalize().scale(0.24));
            ProceduralGeometry.line(context.pose(), context.lines(), start, lead,
                    context.rgb(), (float) (context.envelope() * 0.34));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            Vec3 start = context.source(), end = context.target();
            if (start.distanceToSqr(end) < 1.0E-6) return;
            Basis basis = Basis.around(end.subtract(start));
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), start, basis.a(), basis.b(),
                    0.14, -context.progress() * 2, TAU, 6, 2, 0.6,
                    brighten(context.rgb(), 0.55), (float) (context.envelope() * 0.18));
        }
    }

    private static final class ProjectileRedirect implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 start = context.position(), end = context.secondEndpoint();
            if (start.distanceToSqr(end) < 1.0E-6) return;
            Basis basis = Basis.around(end.subtract(start));
            double phase = Math.min(0.92, 0.18 + context.easedProgress() * 0.74);
            Vec3 marker = start.lerp(end, phase);
            double size = context.event().scale() * 0.075;
            ProceduralGeometry.diamond(context.pose(), context.planes(), marker, basis.a(), basis.b(),
                    size, size * 1.45, brighten(context.rgb(), 0.52),
                    (float) (context.envelope() * 0.68));
            if (context.event().parameterB() > 0.5F) {
                // Theft gets a readable two-plane ribbon and moving node; ordinary ricochets stay restrained.
                for (Vec3 side : List.of(basis.a(), basis.b()))
                    ProceduralGeometry.beam(context.pose(), context.planes(), start, end, side, 0.012,
                            context.rgb(), (float) (context.envelope() * 0.38));
                ProceduralGeometry.shard(context.pose(), context.planes(), marker, basis.a(),
                        end.subtract(start).normalize(), size * 0.65, size * 2.1, size * 0.45,
                        brighten(context.rgb(), 0.75), (float) (context.envelope() * 0.85));
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Vec3 start = context.position(), end = context.secondEndpoint();
            if (start.distanceToSqr(end) < 1.0E-6) return;
            ProceduralGeometry.ribbon(context.pose(), context.lines(),
                    jaggedPath(context, start, end, 4, context.event().scale() * 0.04, 110),
                    context.rgb(), (float) (context.envelope() * 0.64));
            Basis basis = Basis.around(end.subtract(start));
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), start, basis.a(), basis.b(),
                    context.event().scale() * (0.10 + context.easedProgress() * 0.10), context.progress() * 2.2,
                    TAU, 5, 2, 0.34, brighten(context.rgb(), 0.38),
                    (float) (context.envelope() * 0.58));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            Vec3 start = context.position(), end = context.secondEndpoint();
            if (start.distanceToSqr(end) < 1.0E-6) return;
            Basis basis = Basis.around(end.subtract(start));
            double distance = start.distanceTo(end);
            if (context.event().parameterB() > 0.5F) {
                for (int side : new int[]{-1, 1}) {
                    Vec3 offset = basis.a().scale(side * 0.055);
                    ProceduralGeometry.ribbon(context.pose(), context.lines(),
                            jaggedPath(context, start.add(offset), end.add(offset), 8, 0.025, 180 + side),
                            brighten(context.rgb(), 0.55), (float) (context.envelope() * 0.32));
                }
                ProceduralGeometry.brokenRing(context.pose(), context.lines(), end, basis.a(), basis.b(),
                        0.15, -context.progress(), TAU, 6, 2, 0.45,
                        brighten(context.rgb(), 0.6), (float) (context.envelope() * 0.35));
            }
            for (int i = 1; i <= 3; i++) {
                Vec3 node = start.lerp(end, Math.min(0.9, i * Math.min(0.24, 0.75 / Math.max(1.0, distance)) + context.progress() * 0.18));
                double size = context.event().scale() * 0.035;
                ProceduralGeometry.line(context.pose(), context.lines(), node.add(basis.a().scale(size)),
                        node.add(basis.a().scale(-size)), brighten(context.rgb(), 0.65),
                        (float) (context.envelope() * 0.22));
            }
        }
    }

    /** Short renewable wake, attached to the slowed projectile rather than left at a sampled position. */
    private static final class ProjectileDrag implements WorldVisualRecipe {
        @Override public int repeatIntervalTicks() { return 6; }
        private Vec3 axis(WorldVisualRenderContext c) {
            var entity = c.level().getEntity(c.event().targetEntityId());
            Vec3 motion = entity == null ? Vec3.ZERO : entity.getDeltaMovement();
            return motion.lengthSqr() > 1.0E-8 ? motion.normalize() : c.event().direction();
        }
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 axis = axis(c);
            Basis basis = Basis.around(axis);
            double strength = Math.clamp(c.event().parameterA(), 0, 1);
            for (int i = 0; i < 3; i++) {
                double radius = 0.13 + i * 0.045 + strength * 0.075 + c.progress() * 0.045;
                Vec3 center = c.target().subtract(axis.scale(0.05 + i * 0.14 + c.progress() * 0.07));
                ProceduralGeometry.annulus(c.pose(), c.planes(), center, basis.a(), basis.b(),
                        radius - 0.023, radius, 20, i * 0.3, c.rgb(),
                        (float) (c.envelope() * (0.52 - i * 0.10)));
            }
        }
        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 axis = axis(c);
            Basis basis = Basis.around(axis);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.target(), basis.a(), basis.b(),
                    0.18 + c.event().parameterA() * 0.08, c.progress() * 0.5, TAU, 6, 3, 0.30,
                    brighten(c.rgb(), 0.65), (float) (c.envelope() * 0.76));
        }
        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 axis = axis(c);
            Basis basis = Basis.around(axis);
            for (int i = 0; i < 6; i++) {
                double angle = i * TAU / 6 + c.progress() * 0.4;
                Vec3 radial = basis.a().scale(Math.cos(angle)).add(basis.b().scale(Math.sin(angle)));
                Vec3 tip = c.target().add(radial.scale(0.17));
                Vec3 tail = c.target().subtract(axis.scale(0.5)).add(radial.scale(0.27));
                ProceduralGeometry.line(c.pose(), c.lines(), tip, tail, brighten(c.rgb(), 0.55),
                        (float) (c.envelope() * 0.30));
            }
        }
    }

    /** A bright, hollow shock disc perpendicular to the outgoing shot, with a delayed fine echo. */
    private static final class ProjectileShockwave implements WorldVisualRecipe {
        private double radius(WorldVisualRenderContext c) { return 0.14 + c.easedProgress() * 0.62; }
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Basis basis = Basis.around(c.event().direction());
            double r = radius(c);
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.position(), basis.a(), basis.b(),
                    r * 0.80, r, 32, 0, c.rgb(), (float) (c.envelope() * 0.65));
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.position(), basis.a(), basis.b(),
                    r * 0.94, r, 32, 0, brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.85));
        }
        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Basis basis = Basis.around(c.event().direction());
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.position(), basis.a(), basis.b(),
                    radius(c) * 1.06, 0, TAU, 8, 3, 0.18, brighten(c.rgb(), 0.55),
                    (float) (c.envelope() * 0.90));
            ProceduralGeometry.spokes(c.pose(), c.lines(), c.position(), basis.a(), basis.b(),
                    radius(c) * 0.9, radius(c) * 1.23, 8, 0, c.rgb(), (float) (c.envelope() * 0.50));
        }
        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            if (c.progress() < 0.15) return;
            Basis basis = Basis.around(c.event().direction());
            Vec3 echo = c.position().subtract(c.event().direction().scale(0.08));
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), echo, basis.a(), basis.b(),
                    radius(c) * 0.72, -c.progress(), TAU, 12, 3, 0.40,
                    brighten(c.rgb(), 0.65), (float) (c.envelope() * 0.30));
        }
    }

    private static final class ProjectilePierce implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 direction = context.event().direction();
            Basis basis = Basis.around(direction);
            Vec3 center = context.position();
            double scale = context.event().scale();
            Vec3 start = center.add(direction.scale(-scale * 0.38));
            Vec3 end = center.add(direction.scale(scale * (0.32 + context.easedProgress() * 0.42)));
            ProceduralGeometry.beam(context.pose(), context.planes(), start, end, basis.a(), scale * 0.022,
                    brighten(context.rgb(), 0.56), (float) (context.envelope() * 0.66));
            ProceduralGeometry.diamondRing(context.pose(), context.planes(), center, basis.a(), basis.b(),
                    scale * 0.11, scale * 0.11, scale * 0.052, scale * 0.052,
                    context.rgb(), (float) (context.envelope() * 0.52));
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Vec3 direction = context.event().direction();
            Vec3 center = context.position();
            double scale = context.event().scale();
            ProceduralGeometry.line(context.pose(), context.lines(), center.add(direction.scale(-scale * 0.48)),
                    center.add(direction.scale(scale * (0.50 + context.easedProgress() * 0.55))),
                    brighten(context.rgb(), 0.40), (float) (context.envelope() * 0.78));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            Vec3 direction = context.event().direction();
            Basis basis = Basis.around(direction);
            Vec3 center = context.position();
            double scale = context.event().scale();
            float alpha = (float) (context.envelope() * 0.22);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 offset = basis.a().scale(side * scale * 0.045);
                ProceduralGeometry.line(context.pose(), context.lines(), center.add(offset).add(direction.scale(-scale * 0.28)),
                        center.add(offset).add(direction.scale(scale * 0.62)),
                        brighten(context.rgb(), 0.70), alpha);
            }
        }
    }

    private static void crackLattice(WorldVisualRenderContext context, Vec3 center, double radius,
                                     int count, int rgb, float alpha) {
        for (int i = 0; i < count; i++) {
            double angle = i * TAU / count + context.variation(140 + i) * 0.20;
            Vec3 elbow = center.add(Math.cos(angle) * radius * (0.28 + context.variation(160 + i) * 0.20),
                    (context.variation(180 + i) - 0.5) * radius * 0.16,
                    Math.sin(angle) * radius * (0.28 + context.variation(160 + i) * 0.20));
            Vec3 tip = center.add(Math.cos(angle + (context.variation(200 + i) - 0.5) * 0.24) * radius,
                    (context.variation(220 + i) - 0.5) * radius * 0.28,
                    Math.sin(angle + (context.variation(200 + i) - 0.5) * 0.24) * radius);
            ProceduralGeometry.line(context.pose(), context.lines(), center, elbow, rgb, alpha);
            ProceduralGeometry.line(context.pose(), context.lines(), elbow, tip, rgb, alpha * 0.78F);
        }
    }

    private static List<Vec3> jaggedPath(WorldVisualRenderContext context, Vec3 start, Vec3 end,
                                         int segments, double amplitude, int lane) {
        List<Vec3> points = new ArrayList<>(segments + 1);
        Vec3 delta = end.subtract(start);
        Basis basis = Basis.around(delta);
        points.add(start);
        for (int i = 1; i < segments; i++) {
            double t = i / (double) segments;
            double envelope = Math.sin(Math.PI * t);
            double a = (context.variation(lane + i * 2) - 0.5) * 2.0 * amplitude * envelope;
            double b = (context.variation(lane + i * 2 + 1) - 0.5) * 2.0 * amplitude * envelope;
            points.add(start.add(delta.scale(t)).add(basis.a().scale(a)).add(basis.b().scale(b)));
        }
        points.add(end);
        return points;
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
            Vec3 n = direction.lengthSqr() < 1.0E-8 ? Y : direction.normalize();
            Vec3 reference = Math.abs(n.y) < 0.88 ? Y : X;
            Vec3 a = n.cross(reference).normalize();
            return new Basis(a, n.cross(a).normalize());
        }
    }
}
