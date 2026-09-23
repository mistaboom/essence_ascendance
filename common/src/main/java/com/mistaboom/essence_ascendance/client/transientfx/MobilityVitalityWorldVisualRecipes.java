package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralGlyphs;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.world.phys.Vec3;

/** Large readable movement and recovery silhouettes with a separately culled fine-detail layer. */
final class MobilityVitalityWorldVisualRecipes {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final double TAU = Math.PI * 2.0;

    private MobilityVitalityWorldVisualRecipes() { }

    static void registerAll() {
        WorldVisualRecipes.register(TransientVisualIds.WORLD_MOBILITY_LAUNCH, new MobilityLaunch());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_MOMENTUM_SURGE, new MomentumSurge());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_VITALITY_PURGE, new VitalityPurge());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_VITALITY_SURGE, new VitalitySurge());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_DEATH_DEFIANCE, new DeathDefiance());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_RECOVERY_TRANSFER, new RecoveryTransfer());
        WorldVisualRecipes.register(TransientVisualIds.WORLD_WARD_CONVERGENCE, new WardConvergence());
    }

    /** One broad impulse disc and long vector stroke; the small facets are deliberately subordinate. */
    private static final class MobilityLaunch implements WorldVisualRecipe {
        @Override public Vec3 anchor(com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent event,
                                     net.minecraft.client.multiplayer.ClientLevel level) {
            return event.parameterA() >= 2 ? VECTOR.anchor(event, level) : event.position();
        }
        @Override public boolean alive(com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent event,
                                       net.minecraft.client.multiplayer.ClientLevel level) {
            return event.parameterA() < 2 || VECTOR.alive(event, level);
        }
        private static final VectorStreaks VECTOR = new VectorStreaks();
        private double expansion(WorldVisualRenderContext c) {
            return 1 - Math.pow(1 - c.easedProgress(), 2);
        }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            if (c.event().parameterA() >= 2) { VECTOR.renderPrimaryPlanes(c); return; }
            Basis basis = Basis.around(c.event().direction());
            double radius = c.event().scale() * (0.28 + expansion(c) * 0.78);
            double width = c.event().scale() * 0.075;
            ProceduralGeometry.annulus(c.pose(), c.planes(), c.position(), basis.a(), basis.b(),
                    Math.max(0, radius - width), radius, 32, c.variation(0) * TAU,
                    c.rgb(), (float) (c.envelope() * 0.56));
            Vec3 direction = normalized(c.event().direction(), Y);
            Vec3 lateral = basis.a();
            Vec3 root = c.position().subtract(direction.scale(c.event().scale() * 0.28));
            Vec3 tip = c.position().add(direction.scale(c.event().scale() * (0.78 + expansion(c) * 0.48)));
            ProceduralGeometry.taperedPlane(c.pose(), c.planes(), root, tip, lateral,
                    c.event().scale() * 0.075, c.event().scale() * 0.022,
                    brighten(c.rgb(), 0.48), (float) (c.envelope() * 0.52));
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            if (c.event().parameterA() >= 2) { VECTOR.renderPrimaryLines(c); return; }
            Basis basis = Basis.around(c.event().direction());
            double radius = c.event().scale() * (0.30 + expansion(c) * 0.84);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), c.position(), basis.a(), basis.b(),
                    radius, c.variation(1) * TAU + c.progress() * 0.34, TAU,
                    8, 4, 0.18, brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.94));
            ProceduralGeometry.spokes(c.pose(), c.lines(), c.position(), basis.a(), basis.b(),
                    radius * 0.62, radius * 1.12, 8, c.variation(1) * TAU,
                    c.rgb(), (float) (c.envelope() * 0.48));
            Vec3 direction = normalized(c.event().direction(), Y);
            ProceduralGeometry.line(c.pose(), c.lines(),
                    c.position().subtract(direction.scale(c.event().scale() * 0.42)),
                    c.position().add(direction.scale(c.event().scale() * (1.0 + expansion(c) * 0.62))),
                    brighten(c.rgb(), 0.78), (float) (c.envelope() * 0.92));
        }

        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            if (c.event().parameterA() >= 2) { VECTOR.renderDetailPlanes(c); return; }
            Basis basis = Basis.around(c.event().direction());
            double radius = c.event().scale() * (0.35 + expansion(c) * 0.82);
            for (int i = 0; i < 10; i++) {
                double angle = i * TAU / 10 + c.variation(i + 5) * 0.16;
                Vec3 radial = basis.a().scale(Math.cos(angle)).add(basis.b().scale(Math.sin(angle)));
                Vec3 point = c.position().add(radial.scale(radius));
                double size = c.event().scale() * (0.016 + c.variation(i + 30) * 0.009);
                ProceduralGeometry.shard(c.pose(), c.planes(), point, radial.cross(c.event().direction()).normalize(),
                        radial, size, size * 1.8, size * 0.42,
                        brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.54));
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            if (c.event().parameterA() >= 2) { VECTOR.renderDetailLines(c); return; }
            Basis basis = Basis.around(c.event().direction());
            double radius = c.event().scale() * (0.22 + expansion(c) * 0.58);
            ProceduralGeometry.ring(c.pose(), c.lines(), c.position(), basis.a(), basis.b(),
                    radius, 28, brighten(c.rgb(), 0.80), (float) (c.envelope() * 0.28));
            Vec3 direction = normalized(c.event().direction(), Y);
            for (int side : new int[]{-1, 1}) {
                Vec3 offset = basis.a().scale(side * c.event().scale() * 0.09);
                ProceduralGeometry.line(c.pose(), c.lines(), c.position().add(offset),
                        c.position().add(offset).add(direction.scale(c.event().scale() * 0.86)),
                        brighten(c.rgb(), 0.68), (float) (c.envelope() * 0.26));
            }
        }
    }

    /** A subtle temporary boost strip grows out from the player's feet. */
    private static final class MomentumSurge implements WorldVisualRecipe.SourceAttached {
        private Vec3 feet(WorldVisualRenderContext c) {
            var player = c.level().getEntity(c.event().sourceEntityId());
            return player == null ? c.position() : c.source().add(0, -player.getBbHeight() * 0.5 + 0.045, 0);
        }
        private double length(WorldVisualRenderContext c) { return 1.375 + c.easedProgress() * 3.625; }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 root = feet(c), forward = horizontal(c.event().direction());
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            double length = length(c);
            // Open center and low opacity let the terrain remain the dominant surface.
            for (int sign : new int[]{-1, 1}) {
                Vec3 offset = side.scale(sign * 0.36);
                ProceduralGeometry.taperedPlane(c.pose(), c.planes(), root.add(offset),
                        root.add(forward.scale(length)).add(offset), side,
                        0.035, 0.009, c.rgb(), (float) (c.envelope() * 0.27));
            }
            for (int i = 0; i < 4; i++) {
                double distance = 0.35 + i * length / 4 + c.easedProgress() * 0.18;
                Vec3 tip = root.add(forward.scale(distance));
                for (int sign : new int[]{-1, 1}) {
                    Vec3 tail = tip.subtract(forward.scale(0.23)).add(side.scale(sign * 0.26));
                    ProceduralGeometry.beam(c.pose(), c.planes(), tail, tip, side,
                            0.018, c.rgb(), (float) (c.envelope() * (0.30 - i * 0.035)));
                }
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 root = feet(c), forward = horizontal(c.event().direction());
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            for (int sign : new int[]{-1, 1}) {
                Vec3 offset = side.scale(sign * 0.36);
                ProceduralGeometry.line(c.pose(), c.lines(), root.add(offset),
                        root.add(forward.scale(length(c))).add(offset),
                        brighten(c.rgb(), 0.45), (float) (c.envelope() * 0.38));
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 root = feet(c), forward = horizontal(c.event().direction());
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            for (int i = 0; i < 8; i++) {
                Vec3 mark = root.add(forward.scale((i + 1) * length(c) / 9));
                for (int sign : new int[]{-1, 1})
                    ProceduralGeometry.line(c.pose(), c.lines(), mark.add(side.scale(sign * 0.40)),
                            mark.add(side.scale(sign * 0.45)), brighten(c.rgb(), 0.62),
                            (float) (c.envelope() * 0.22));
            }
        }
    }

    /** Debt is visibly drawn inward, then resolved by one calm outward life pulse. */
    private static final class VitalityPurge implements WorldVisualRecipe {
        private double inward(WorldVisualRenderContext c) { return Math.max(0, 1 - c.easedProgress() * 1.55); }
        private double pulse(WorldVisualRenderContext c) { return Math.max(0, (c.easedProgress() - 0.34) / 0.66); }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double radius = c.event().scale() * (0.34 + pulse(c) * 0.82);
            ProceduralGeometry.annulus(c.pose(), c.planes(), center, X, Z,
                    Math.max(0, radius - c.event().scale() * 0.065), radius, 36, 0,
                    c.rgb(), (float) (c.envelope() * 0.46));
            for (int i = 0; i < 6; i++) {
                double angle = i * TAU / 6 + c.variation(i) * 0.18;
                Vec3 radial = new Vec3(Math.cos(angle), 0.28 + c.variation(i + 10) * 0.20, Math.sin(angle)).normalize();
                Vec3 point = center.add(radial.scale(c.event().scale() * (0.30 + inward(c) * 1.10)));
                double size = c.event().scale() * 0.026;
                ProceduralGeometry.shard(c.pose(), c.planes(), point, radial.cross(Y).normalize(), radial,
                        size, size * 1.7, size * 0.42, brighten(c.rgb(), 0.64),
                        (float) (c.envelope() * 0.62));
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double radius = c.event().scale() * (0.38 + pulse(c) * 0.88);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, X, Z, radius,
                    -c.progress() * 0.42, TAU, 8, 4, 0.18,
                    brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.92));
            for (int i = 0; i < 6; i++) {
                double angle = i * TAU / 6;
                Vec3 radial = new Vec3(Math.cos(angle), 0.20, Math.sin(angle)).normalize();
                ProceduralGeometry.line(c.pose(), c.lines(), center.add(radial.scale(c.event().scale() * 1.35)),
                        center.add(radial.scale(c.event().scale() * (0.24 + inward(c) * 0.55))),
                        c.rgb(), (float) (c.envelope() * 0.52));
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, X, Y,
                    c.event().scale() * (0.27 + pulse(c) * 0.46), c.progress() * 0.8,
                    TAU, 14, 2, 0.50, brighten(c.rgb(), 0.78), (float) (c.envelope() * 0.25));
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, Z, Y,
                    c.event().scale() * (0.24 + pulse(c) * 0.42), -c.progress() * 0.65,
                    TAU, 12, 2, 0.52, brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.22));
        }
    }

    /** Adrenaline is one rising hourglass-like surge with restrained inner orbitals. */
    private static final class VitalitySurge implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double height = c.event().scale() * (0.58 + c.easedProgress() * 0.72);
            for (int side : new int[]{-1, 1}) {
                Vec3 root = center.add(side * c.event().scale() * 0.34, -height * 0.42, 0);
                Vec3 tip = center.add(side * c.event().scale() * 0.08, height * 0.58, 0);
                ProceduralGeometry.taperedPlane(c.pose(), c.planes(), root, tip, Z,
                        c.event().scale() * 0.11, c.event().scale() * 0.035,
                        side < 0 ? c.rgb() : brighten(c.rgb(), 0.46),
                        (float) (c.envelope() * 0.48));
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double radius = c.event().scale() * (0.34 + c.easedProgress() * 0.72);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, X, Z, radius,
                    c.progress() * 0.50, TAU, 8, 4, 0.20,
                    brighten(c.rgb(), 0.72), (float) (c.envelope() * 0.90));
            ProceduralGeometry.spokes(c.pose(), c.lines(), center, X, Z, radius * 0.48,
                    radius * 1.08, 8, 0, c.rgb(), (float) (c.envelope() * 0.42));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            ProceduralGeometry.helix(c.pose(), c.lines(), center.add(0, -0.58, 0),
                    new Vec3(0, 1.16, 0), X, Z, c.event().scale() * 0.20,
                    1.15, 28, brighten(c.rgb(), 0.76), (float) (c.envelope() * 0.24));
        }
    }

    /** Lethal saves use a large three-axis aperture; reform closes it cleanly instead of adding screen effects. */
    private static final class DeathDefiance implements WorldVisualRecipe {
        private boolean spirit(WorldVisualRenderContext c) { return c.event().parameterA() > 0.5F; }
        private boolean reform(WorldVisualRenderContext c) { return c.event().parameterB() > 0.5F; }
        private double radius(WorldVisualRenderContext c) {
            double p = reform(c) ? 1 - c.easedProgress() : c.easedProgress();
            return c.event().scale() * (0.34 + p * 0.94);
        }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double radius = radius(c);
            double width = c.event().scale() * (spirit(c) ? 0.060 : 0.085);
            ProceduralGeometry.annulus(c.pose(), c.planes(), center, X, Z,
                    Math.max(0, radius - width), radius, 40, 0, c.rgb(),
                    (float) (c.envelope() * 0.46));
            ProceduralGeometry.diamondRing(c.pose(), c.planes(), center, X, Y,
                    radius * 0.76, radius, radius * 0.62, radius * 0.82,
                    brighten(c.rgb(), 0.34), (float) (c.envelope() * 0.42));
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double radius = radius(c);
            for (Basis basis : new Basis[]{new Basis(X, Z), new Basis(X, Y), new Basis(Z, Y)})
                ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, basis.a(), basis.b(), radius,
                        c.progress() * (spirit(c) ? -0.32 : 0.22), TAU, 10, 4, 0.18,
                        brighten(c.rgb(), 0.76), (float) (c.envelope() * 0.82));
            ProceduralGeometry.spokes(c.pose(), c.lines(), center, X, Z, radius * 0.48,
                    radius * 1.10, 10, Math.PI / 10, c.rgb(), (float) (c.envelope() * 0.44));
        }

        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double radius = radius(c);
            for (int i = 0; i < 12; i++) {
                double angle = i * TAU / 12 + c.progress() * (spirit(c) ? -0.45 : 0.28);
                Vec3 radial = new Vec3(Math.cos(angle), (i % 3 - 1) * 0.30, Math.sin(angle)).normalize();
                Vec3 point = center.add(radial.scale(radius * (0.86 + c.variation(i) * 0.10)));
                double size = c.event().scale() * 0.020;
                ProceduralGeometry.shard(c.pose(), c.planes(), point, radial.cross(Y).normalize(), radial,
                        size, size * 1.65, size * 0.40, brighten(c.rgb(), 0.72),
                        (float) (c.envelope() * 0.46));
            }
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double radius = radius(c) * 0.70;
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, X, Y, radius,
                    -c.progress() * 0.9, TAU, 18, 2, 0.58,
                    brighten(c.rgb(), 0.84), (float) (c.envelope() * 0.24));
        }
    }

    /** Metabolic conversion is an inward transfer with a single stable center mark. */
    private static final class RecoveryTransfer implements WorldVisualRecipe {
        @Override public int repeatIntervalTicks() { return 6; }

        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double outer = c.event().scale() * (0.80 - c.easedProgress() * 0.48);
            ProceduralGeometry.diamondRing(c.pose(), c.planes(), center, X, Y,
                    c.event().scale() * 0.28, c.event().scale() * 0.36,
                    c.event().scale() * 0.16, c.event().scale() * 0.21,
                    c.rgb(), (float) (c.envelope() * 0.54));
            for (int i = 0; i < 6; i++) {
                double angle = i * TAU / 6 + c.variation(i) * 0.14;
                Vec3 radial = new Vec3(Math.cos(angle), (i % 2 == 0 ? 0.25 : -0.18), Math.sin(angle)).normalize();
                Vec3 point = center.add(radial.scale(outer));
                double size = c.event().scale() * 0.020;
                ProceduralGeometry.shard(c.pose(), c.planes(), point, radial.cross(Y).normalize(), radial,
                        size, size * 1.7, size * 0.40, brighten(c.rgb(), 0.70),
                        (float) (c.envelope() * 0.50));
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            double radius = c.event().scale() * (0.74 - c.easedProgress() * 0.42);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, X, Z, radius,
                    c.progress() * 0.45, TAU, 6, 4, 0.22,
                    brighten(c.rgb(), 0.68), (float) (c.envelope() * 0.82));
            ProceduralGeometry.spokes(c.pose(), c.lines(), center, X, Z, radius * 0.42,
                    radius, 6, 0, c.rgb(), (float) (c.envelope() * 0.42));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext c) {
            Vec3 center = c.target();
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, X, Y,
                    c.event().scale() * (0.32 + c.easedProgress() * 0.10), -c.progress() * 0.8,
                    TAU, 12, 2, 0.50, brighten(c.rgb(), 0.78), (float) (c.envelope() * 0.24));
        }
    }

    /** Hearts rise and gently wiggle from the defeated creature's fixed death position. */
    private static final class WardConvergence implements WorldVisualRecipe {
        private double phase(WorldVisualRenderContext c, int i) {
            return Math.clamp((c.progress() - i * 0.09) / 0.78, 0, 1);
        }
        private Vec3 point(WorldVisualRenderContext c, int i) {
            double p = phase(c, i), angle = c.variation(i) * TAU;
            double spread = 0.16 + p * (0.32 + i * 0.10);
            return c.position().add(Math.cos(angle) * spread + Math.sin(p * 5.0 + i) * 0.10,
                    0.12 + p * (1.45 + i * 0.18), Math.sin(angle) * spread);
        }
        private float alpha(WorldVisualRenderContext c, int i) {
            double p = phase(c, i);
            return (float) (Math.min(1, p * 8) * Math.pow(1 - p, 0.65));
        }
        private void draw(WorldVisualRenderContext c, boolean outline) {
            for (int i = 0; i < 3; i++) {
                Vec3 center = point(c, i);
                double tilt = Math.sin(phase(c, i) * 5.5 + i * 1.9) * 0.16;
                var basis = ProceduralGlyphs.facing(center, tilt);
                ProceduralGlyphs.heart(c.pose(), outline ? c.lines() : c.planes(), center, basis,
                        c.event().scale() * (0.44 - i * 0.055),
                        outline ? brighten(c.rgb(), 0.72) : c.rgb(),
                        alpha(c, i) * (outline ? 0.86F : 0.74F), outline);
            }
        }
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) { draw(c, false); }
        @Override public void renderPrimaryLines(WorldVisualRenderContext c) { draw(c, true); }
        @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
            for (int i = 0; i < 6; i++) {
                Vec3 center = point(c, i % 3).add((c.variation(i + 12) - 0.5) * 0.42,
                        -0.16 - c.variation(i + 20) * 0.14, (c.variation(i + 30) - 0.5) * 0.26);
                var basis = ProceduralGlyphs.facing(center, 0);
                ProceduralGeometry.diamond(c.pose(), c.planes(), center, basis.right(), basis.up(),
                        0.014, 0.022, brighten(c.rgb(), 0.65), alpha(c, i % 3) * 0.40F);
            }
        }
    }

    /** Fine world-space stars sweep from ahead of the launch toward the player's rear. */
    private static final class VectorStreaks implements WorldVisualRecipe.SourceAttached {
        private void streaks(WorldVisualRenderContext c, int count, int salt, double opacity) {
            Vec3 direction = normalized(c.event().direction(), Z);
            Basis basis = Basis.around(direction);
            Vec3 center = c.source();
            for (int i = 0; i < count; i++) {
                double phase = (c.progress() + c.variation(i + salt) * 0.58) % 1.0;
                double angle = c.variation(i + salt + 40) * TAU;
                Vec3 radial = basis.a().scale(Math.cos(angle)).add(basis.b().scale(Math.sin(angle)));
                double radius = 0.95 + c.variation(i + salt + 80) * 0.95;
                // A widening tunnel produces the perspective stretch, keeping the center unobstructed.
                Vec3 front = center.add(direction.scale(2.7 - phase * 5.3))
                        .add(radial.scale(radius * (0.68 + phase * 0.34)));
                Vec3 back = front.subtract(direction.scale(0.55 + c.easedProgress() * 1.70))
                        .add(radial.scale(0.20 + c.easedProgress() * 0.32));
                double fade = Math.sin(Math.PI * phase);
                ProceduralGeometry.line(c.pose(), c.lines(), front, back,
                        brighten(c.rgb(), 0.78), (float) (c.envelope() * fade * opacity));
            }
        }
        @Override public void renderPrimaryLines(WorldVisualRenderContext c) { streaks(c, 12, 90, 0.58); }
        @Override public void renderDetailLines(WorldVisualRenderContext c) { streaks(c, 12, 220, 0.25); }
    }

    private static Vec3 normalized(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() > 1.0E-8 ? value.normalize() : fallback;
    }

    private static Vec3 horizontal(Vec3 value) {
        Vec3 flat = new Vec3(value.x, 0, value.z);
        return normalized(flat, Z);
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
            Vec3 n = normalized(direction, Y);
            Vec3 reference = Math.abs(n.y) < 0.88 ? Y : X;
            Vec3 a = n.cross(reference).normalize();
            return new Basis(a, n.cross(a).normalize());
        }
    }
}
