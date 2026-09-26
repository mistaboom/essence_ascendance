package com.mistaboom.essence_ascendance.visual;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;

/** Bounded design data shared by the renderer and headless geometry invariants. Units are blocks. */
public final class ArmorVisualStyle {
    private ArmorVisualStyle() { }

    public record Tier(int emission, int sections, double completeness, int ticks,
                       boolean secondary, boolean outer, boolean satellites, double motion) { }
    private static final Tier[] TIERS = {
            new Tier(0,   1, .12, 0, false, false, false, 0),
            new Tier(22,  2, .24, 0, false, false, false, 0),
            new Tier(45,  4, .78, 2, false, false, false, 0),
            new Tier(67,  4, .82, 2, true,  false, false, .018),
            new Tier(90,  4, .88, 4, true,  true,  false, .024),
            new Tier(112, 4, .94, 4, true,  true,  true,  .030)
    };

    public static Tier tier(EquipmentTier tier) { return TIERS[tier.ordinal()]; }

    /** Same 3:1 color/white luminous treatment as the Focus full-bright overlay. */
    public static int luminousColor(int rgb) {
        return (((rgb >> 16 & 255) * 3 + 255) / 4) << 16
                | (((rgb >> 8 & 255) * 3 + 255) / 4) << 8
                | ((rgb & 255) * 3 + 255) / 4;
    }

    // Every motif lies in a frontal XY plane. The helmet crest stays above the eyes.
    // Dimensions include the largest tier; secondary/outer layers are bounded by 1.32x.
    public enum Motif {
        CREST("head", "head", 0, -.59, -.15, .30, .105),
        COLLAR("chest", "body", 0, -.06, -.255, .26, .13),
        LEFT_PAULDRON("left_arm", "left_arm", .04, -.23, -.24, .14, .105),
        RIGHT_PAULDRON("right_arm", "right_arm", -.04, -.23, -.24, .14, .105),
        WAIST("belt", "body", 0, .66, -.235, .28, .065),
        LEFT_TASSET("left_leg", "left_leg", .105, .18, -.225, .085, .15),
        RIGHT_TASSET("right_leg", "right_leg", -.105, .18, -.225, .085, .15),
        LEFT_FIN("left_foot", "left_leg", .08, .60, -.26, .105, .09),
        RIGHT_FIN("right_foot", "right_leg", -.08, .60, -.26, .105, .09);

        public final String mesh, bone;
        public final double x, y, z, width, height;
        Motif(String mesh, String bone, double x, double y, double z, double width, double height) {
            this.mesh = mesh; this.bone = bone;
            this.x = x; this.y = y; this.z = z; this.width = width; this.height = height;
        }
        public Bounds bounds() {
            return new Bounds(x - width * 1.4, y - height * 1.4, z - .02,
                    x + width * 1.4, y + height * 1.4, z + .02);
        }
    }
    private static final Motif[] MOTIFS = Motif.values();
    public static Motif motif(String mesh) {
        for (Motif motif : MOTIFS) if (motif.mesh.equals(mesh)) return motif;
        throw new IllegalArgumentException("Missing armor motif: " + mesh);
    }
    public static boolean enabled(Motif motif, EquipmentTier tier) {
        return switch (motif) {
            case LEFT_PAULDRON, RIGHT_PAULDRON, LEFT_TASSET, RIGHT_TASSET -> tier.ordinal() >= 2;
            default -> true;
        };
    }
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) { }

    /** Conservative front boundary, before the translated harness, wing fan and boosted thrusters. */
    public static final double FLIGHT_REAR_BOUNDARY = .12;
}
