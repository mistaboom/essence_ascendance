package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One bounded composition, scaled and simplified for natural Awakening. No independent render pass. */
final class AscendanceCeremonyRecipe implements WorldVisualRecipe {
    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
    private static final double TAU = Math.PI * 2;
    private static final StatCategory[] ESSENCES = StatCategory.values();

    @Override public Vec3 anchor(WorldVisualEvent event, ClientLevel level) {
        var entity = level.getEntity(event.sourceEntityId());
        return entity == null ? event.position() : entity.position();
    }

    @Override public boolean alive(WorldVisualEvent event, ClientLevel level) {
        return level.getEntity(event.sourceEntityId()) instanceof LivingEntity entity
                && entity.isAlive() && !entity.isRemoved();
    }

    @Override public void renderPrimaryPlanes(WorldVisualRenderContext c) {
        Shape s = shape(c);
        // Three wide, continuous rising bands form the storm. Their facets are only the detail layer.
        for (int lane = 0; lane < s.lanes; lane++) {
            int segments = s.awakening ? 36 : 64;
            for (int i = 0; i < segments; i++) {
                double t = i / (double) segments, next = (i + 1.0) / segments;
                Vec3 a = spiral(s, lane, t), b = spiral(s, lane, next);
                double widthA = s.scale * (0.10 + 0.16 * Math.sin(Math.PI * t));
                double widthB = s.scale * (0.10 + 0.16 * Math.sin(Math.PI * next));
                ProceduralGeometry.quad(c.pose(), c.planes(), a.add(0, -widthA, 0),
                        b.add(0, -widthB, 0), b.add(0, widthB, 0), a.add(0, widthA, 0),
                        c.rgb(), s.primary * 0.26F);
            }
        }
        ringBand(c, s.base, s.radius * 1.10, s.scale * 0.08, c.rgb(), s.primary * 0.42F);
        ringBand(c, s.base.add(0, s.height, 0), s.radius * 1.28, s.scale * 0.065,
                c.rgb(), s.primary * (float) s.build * 0.46F);
        // A hollow luminous sheath keeps the player visible and never draws a solid camera-facing slab.
        for (int lane = 0; lane < 6; lane++) {
            double angle = TAU * lane / 6 + s.rotation;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 lateral = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            Vec3 root = s.base.add(radial.scale(0.78 + 0.35 * s.scale));
            ProceduralGeometry.taperedPlane(c.pose(), c.planes(), root,
                    root.add(radial.scale(0.22)).add(0, 2.1 + s.build * s.scale, 0), lateral,
                    0.12 * s.scale, 0.015 * s.scale, c.rgb(), s.primary * (float) s.build * 0.20F);
        }
        release(c, s, true);
    }

    @Override public void renderPrimaryLines(WorldVisualRenderContext c) {
        Shape s = shape(c);
        int segments = s.awakening ? 36 : 64;
        for (int lane = 0; lane < s.lanes; lane++) {
            Vec3 previous = spiral(s, lane, 0);
            for (int i = 1; i <= segments; i++) {
                Vec3 next = spiral(s, lane, i / (double) segments);
                ProceduralGeometry.line(c.pose(), c.lines(), previous, next, c.rgb(), s.primary * 0.85F);
                previous = next;
            }
        }
        ring(c, s.base, s.radius * 1.10, c.rgb(), s.primary, 64);
        ProceduralGeometry.brokenRing(c.pose(), c.lines(), s.base.add(0, 0.02, 0), X, Z,
                s.radius * 0.86, -s.rotation, TAU, 6, 6, 0.23, c.rgb(), s.primary * 0.7F);
        ProceduralGeometry.spokes(c.pose(), c.lines(), s.base, X, Z,
                s.radius * 0.90, s.radius * 1.08, 12, s.rotation, c.rgb(), s.primary * 0.7F);
        ring(c, s.base.add(0, s.height, 0), s.radius * 1.28, c.rgb(),
                s.primary * (float) s.build, 64);
        // Convergence rays draw inward during Gather, then resolve into a low, clear six-point sigil.
        for (int i = 0; i < 6; i++) {
            double a = TAU * i / 6 + s.rotation;
            Vec3 outer = ProceduralGeometry.orbitPoint(s.base, X, Z, s.radius, s.radius, a);
            Vec3 inner = ProceduralGeometry.orbitPoint(s.base, X, Z,
                    s.radius * (0.25 + 0.35 * s.gather), s.radius * (0.25 + 0.35 * s.gather), a + 0.35);
            ProceduralGeometry.line(c.pose(), c.lines(), outer, inner, c.rgb(), s.primary * 0.5F);
        }
        release(c, s, false);
    }

    @Override public void renderDetailPlanes(WorldVisualRenderContext c) {
        Shape s = shape(c);
        int count = s.awakening ? 12 : 30;
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            Vec3 point = spiral(s, i % s.lanes, t);
            double angle = s.rotation + t * TAU * 1.35;
            Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            // Individual facets stay small even when the composition is almost eight blocks tall.
            double width = s.awakening ? 0.025 : 0.040;
            double height = s.awakening ? 0.065 : 0.105;
            ProceduralGeometry.shard(c.pose(), c.planes(), point, tangent, Y,
                    width, height, width * 0.60, essence(i), s.detail * 0.75F);
        }
        // Tiny pulses climb continuously along the inner filaments; no noisy random streaks.
        for (int i = 0; i < s.lanes; i++) {
            double t = ProceduralMotion.phase(c.progress() * c.event().lifetimeTicks(), 0.006) + i / (double) s.lanes;
            t -= Math.floor(t);
            Vec3 point = spiral(s, i, t).lerp(s.base.add(0, s.height * t, 0), 0.12);
            ProceduralGeometry.diamond(c.pose(), c.planes(), point, X, Y,
                    0.025, 0.055, AscendancePalette.TRANSCENDENT.metalRgb(), s.detail);
        }
    }

    @Override public void renderDetailLines(WorldVisualRenderContext c) {
        Shape s = shape(c);
        int rings = s.awakening ? 2 : 4;
        for (int i = 0; i < rings; i++) {
            double t = (i + 1.0) / (rings + 1);
            Vec3 center = s.base.add(0, s.height * t, 0);
            double radius = radius(s, t) * 0.90;
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), center, X, Z, radius,
                    -s.rotation + i * 0.3, TAU, 6, 5, 0.30, c.rgb(), s.detail * 0.48F);
            ProceduralGeometry.spokes(c.pose(), c.lines(), center, X, Z,
                    radius * 0.96, radius * 1.02, 12, s.rotation, c.rgb(), s.detail * 0.40F);
        }
        // Sparse constellation ties enrich the ribbons without filling the negative space with a mesh.
        for (int lane = 0; lane < s.lanes; lane++) {
            Vec3 previous = null;
            for (int i = 0; i <= 48; i++) {
                double t = i / 48.0;
                Vec3 point = spiral(s, lane, t).lerp(s.base.add(0, s.height * t, 0), 0.12);
                if (previous != null) ProceduralGeometry.line(c.pose(), c.lines(), previous, point,
                        essence(lane), s.detail * 0.50F);
                previous = point;
                if (i > 0 && i < 48 && i % 12 == 0) {
                    ProceduralGeometry.line(c.pose(), c.lines(), point, spiral(s, (lane + 1) % s.lanes, t + 0.06),
                            c.rgb(), s.detail * 0.20F);
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            double angle = TAU * i / 6 - s.rotation * 0.5;
            Vec3 node = ProceduralGeometry.orbitPoint(s.base.add(0, 0.05, 0), X, Z,
                    s.radius * 1.01, s.radius * 1.01, angle);
            ProceduralGeometry.brokenRing(c.pose(), c.lines(), node, X, Z, s.scale * 0.14,
                    angle, TAU, 3, 3, 0.18, essence(i), s.detail * 0.65F);
        }
    }

    private static void release(WorldVisualRenderContext c, Shape s, boolean planes) {
        if (c.progress() <= 0.58) return;
        double t = ease(c.progress(), 0.58, 0.92);
        float alpha = s.primary * (float) ProceduralMotion.fadeEnvelope(
                Math.clamp((c.progress() - 0.58) / 0.38, 0, 1), 5, 3);
        double radius = s.scale * (1.3 + 4.0 * t);
        Vec3 center = s.base.add(0, s.scale * (1.0 + 5.8 * t), 0);
        if (planes) {
            ringBand(c, center, radius, s.scale * (0.06 + 0.07 * (1 - t)),
                    AscendancePalette.TRANSCENDENT.metalRgb(), alpha * 0.42F);
        } else {
            ring(c, center, radius, AscendancePalette.TRANSCENDENT.metalRgb(), alpha, 80);
            // Six broad crown arcs mark the upward release. They are lines, never giant shards.
            for (int i = 0; i < 6; i++) {
                double a = TAU * i / 6 + s.rotation;
                Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
                ProceduralGeometry.arc(c.pose(), c.lines(), center, radial, Y,
                        radius, s.scale * 1.1, 0, Math.PI * 0.70, 16, c.rgb(), alpha * 0.65F);
            }
        }
    }

    private static void ringBand(WorldVisualRenderContext c, Vec3 center, double radius, double width,
                                 int rgb, float alpha) {
        ProceduralGeometry.annulus(c.pose(), c.planes(), center, X, Z,
                Math.max(0, radius - width), radius + width, 64, 0, rgb, alpha);
    }

    private static void ring(WorldVisualRenderContext c, Vec3 center, double radius, int rgb, float alpha, int segments) {
        ProceduralGeometry.ring(c.pose(), c.lines(), center, X, Z, radius, segments, rgb, alpha);
    }

    private static int essence(int i) { return AscendancePalette.categoryRgb(ESSENCES[i % ESSENCES.length]); }

    private static double radius(Shape s, double t) {
        // Narrow foot, clear waist, generous upper flare: a coherent rising storm silhouette.
        return s.radius * (0.40 + 0.88 * Math.pow(t, 1.45));
    }

    private static Vec3 spiral(Shape s, int lane, double t) {
        double a = TAU * lane / s.lanes + s.rotation + TAU * 1.35 * t;
        double r = radius(s, t);
        return s.base.add(Math.cos(a) * r, s.height * t, Math.sin(a) * r);
    }

    private static double ease(double p, double start, double end) {
        return ProceduralMotion.smoothStep((p - start) / (end - start));
    }

    private static Shape shape(WorldVisualRenderContext c) {
        double p = c.progress(), scale = c.event().scale();
        double gather = ease(p, 0, 0.22), build = ease(p, 0.18, 0.58);
        double release = ease(p, 0.58, 0.76), settle = ease(p, 0.76, 1);
        float primary = (float) (ease(p, 0, 0.10) * (1 - ease(p, 0.76, 0.95)) * c.event().intensity());
        float detail = (float) (ease(p, 0.10, 0.48) * (1 - ease(p, 0.84, 1)) * c.event().intensity());
        var entity = c.level().getEntity(c.event().sourceEntityId());
        Vec3 base = entity == null ? c.position() : c.source().add(0, -entity.getBbHeight() * 0.5, 0);
        base = base.add(0, 0.08 + settle * scale * 0.6, 0);
        double radius = scale * (2.35 + 1.6 * (1 - gather) + 0.50 * release + 0.3 * settle);
        double height = scale * (0.6 + 6.6 * build + 0.6 * release);
        boolean awakening = c.event().parameterA() > 0.5F;
        return new Shape(base, scale, radius, height, gather, build,
                c.variation(0) * TAU + p * c.event().lifetimeTicks() * 0.006,
                primary, detail, awakening, awakening ? 2 : 3);
    }

    private record Shape(Vec3 base, double scale, double radius, double height, double gather,
                         double build, double rotation, float primary, float detail, boolean awakening, int lanes) { }
}
