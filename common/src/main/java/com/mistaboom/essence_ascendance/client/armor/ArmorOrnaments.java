package com.mistaboom.essence_ascendance.client.armor;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.visual.ArmorVisualStyle;
import com.mistaboom.essence_ascendance.visual.ArmorVisualStyle.Motif;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Small fixed-cost broken bands and glyphs. No particles, world state, or per-frame shape lists. */
public final class ArmorOrnaments {
    private static final double TAU = Math.PI * 2;
    private ArmorOrnaments() { }

    public static float detail(double distance) {
        return (float) (1 - ProceduralMotion.smoothStep((distance - 12) / 12));
    }
    public static float visibility(double distance) {
        return (float) (1 - ProceduralMotion.smoothStep((distance - 32) / 16));
    }

    /** All eight corners include every tier's maximum motion/width. Suppress before queueing. */
    public static boolean clearsFlight(Motif motif, Matrix4f boneToBody) {
        var b = motif.bounds();
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            boneToBody.transformPosition(corner.set((float) ((i & 1) == 0 ? b.minX() : b.maxX()),
                    (float) ((i & 2) == 0 ? b.minY() : b.maxY()),
                    (float) ((i & 4) == 0 ? b.minZ() : b.maxZ())));
            if (corner.z >= ArmorVisualStyle.FLIGHT_REAR_BOUNDARY) return false;
        }
        return true;
    }

    public static void render(Motif motif, EquipmentTier tier, double age, double seed,
                              int rgb, float detail, float visibility, PoseStack pose,
                              VertexConsumer planes, VertexConsumer lines) {
        if (visibility <= 0 || !ArmorVisualStyle.enabled(motif, tier)) return;
        var style = ArmorVisualStyle.tier(tier);
        // No rotation/orbit at the first three tiers; later motion only shifts gaps along the same form.
        double phase = -Math.PI / 2 + (style.motion() == 0 ? 0
                : ProceduralMotion.oscillate(age * style.motion() + seed, .06));
        float alpha = (float) (.10 + tier.ordinal() * .032) * visibility;
        if (planes != null) band(motif, pose, planes, style.sections(), style.completeness(), 1, .13, phase, rgb, alpha);
        if (planes != null && style.secondary())
            band(motif, pose, planes, 2, .55, 1.16, .07, -phase, rgb, alpha * .60f);
        if (planes != null && style.outer())
            band(motif, pose, planes, 4, style.completeness(), 1.30, .055, phase, rgb, alpha * .45f);
        if (detail <= 0) return;
        int luminous = ArmorVisualStyle.luminousColor(rgb);
        if (style.ticks() > 0) {
            if (planes != null) band(motif, pose, planes, style.sections(), style.completeness(), .99, .024,
                    phase, luminous, alpha * detail * 1.8f);
            for (int i = 0; lines != null && i < style.ticks(); i++) {
                double a = phase + TAU * i / style.ticks();
                double x = shapeX(motif, a), y = shapeY(motif, a);
                ProceduralGeometry.line(pose, lines,
                        motif.x + x * 1.04, motif.y + y * 1.04, motif.z - .002,
                        motif.x + x * 1.11, motif.y + y * 1.11, motif.z - .002,
                        luminous, .42f * detail * visibility);
            }
        }
        if (planes != null && style.satellites()) {
            // Two inset counter-moving planes, never a free orbit around the player.
            double a = -age * .014 + seed;
            for (int i = 0; i < 2; i++) {
                double x = motif.x + shapeX(motif, a + i * Math.PI) * 1.15;
                double y = motif.y + shapeY(motif, a + i * Math.PI) * 1.15;
                double w = motif.width * .07, h = motif.height * .09;
                ProceduralGeometry.quad(pose, planes,
                        x, y - h, motif.z - .006, x + w, y, motif.z - .006,
                        x, y + h, motif.z - .006, x - w, y, motif.z - .006,
                        luminous, .52f * detail * visibility);
            }
        }
    }

    private static void band(Motif m, PoseStack pose, VertexConsumer out, int sections,
                             double completeness, double scale, double thickness,
                             double phase, int rgb, float alpha) {
        for (int i = 0; i < sections; i++) {
            double start = phase + TAU * i / sections;
            // Fixed quarter-form budget: tiers complete the shape instead of accumulating clutter.
            double sweep = Math.PI / 2 * completeness;
            for (int j = 0; j < 4; j++) {
                double a = start + sweep * j / 4, b = start + sweep * (j + 1) / 4;
                double ax = shapeX(m, a), ay = shapeY(m, a), bx = shapeX(m, b), by = shapeY(m, b);
                double inner = scale - thickness;
                ProceduralGeometry.quad(pose, out,
                        m.x + ax * scale, m.y + ay * scale, m.z,
                        m.x + bx * scale, m.y + by * scale, m.z,
                        m.x + bx * inner, m.y + by * inner, m.z,
                        m.x + ax * inner, m.y + ay * inner, m.z, rgb, alpha);
            }
        }
    }

    private static double shapeX(Motif m, double angle) {
        double x = Math.cos(angle), y = Math.sin(angle);
        return m.width * x / (m == Motif.CREST ? 1 : Math.abs(x) + Math.abs(y));
    }
    private static double shapeY(Motif m, double angle) {
        double x = Math.cos(angle), y = Math.sin(angle);
        return m.height * y / (m == Motif.CREST ? 1 : Math.abs(x) + Math.abs(y));
    }
}
