package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshot;
import com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshot.Kind;
import com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshot.Piece;

/** Pure equipment-rule outcomes. Native slot/binding/fracture capture is exercised by the server fixture. */
public final class EquipmentAchievementTest {
    private static int checks;
    private static final EquipmentTier TOP = EquipmentTier.TRANSCENDENT;
    private static final Piece EMPTY = Piece.EMPTY;
    public static void main(String[] args) {
        fullArmor(); transcendentEquipment(); glaive(); swordShield();
        System.out.println("Equipment achievement rules: " + checks + " checks passed");
    }

    private static Piece piece(Kind kind, EquipmentTier tier) { return new Piece(kind, tier, true); }
    private static Piece armor(EquipmentTier tier) { return piece(Kind.ARMOR, tier); }
    private static EquipmentSnapshot snapshot(EquipmentTier player, Piece[] armor, Piece main, Piece off, boolean compatible) {
        return new EquipmentSnapshot(player, armor[0], armor[1], armor[2], armor[3], main, off, compatible);
    }
    private static Piece[] set(EquipmentTier tier) { return new Piece[]{armor(tier), armor(tier), armor(tier), armor(tier)}; }

    private static void fullArmor() {
        for (var tier : EquipmentTier.values()) {
            check(EquipmentRules.fullArmor(snapshot(EquipmentTier.LATENT, set(tier), EMPTY, EMPTY, false)),
                    "All intact armor tiers work independently of hands/player tier: " + tier);
            for (int slot = 0; slot < 4; slot++) {
                Piece[] pieces = set(tier); pieces[slot] = EMPTY;
                check(!EquipmentRules.fullArmor(snapshot(TOP, pieces, EMPTY, EMPTY, true)), "Each armor slot is required");
                pieces[slot] = new Piece(Kind.ARMOR, tier, false);
                check(!EquipmentRules.fullArmor(snapshot(TOP, pieces, EMPTY, EMPTY, true)), "Each armor slot must be functional");
                pieces[slot] = piece(Kind.SHIELD, tier);
                check(!EquipmentRules.fullArmor(snapshot(TOP, pieces, EMPTY, EMPTY, true)), "Nonarmor cannot replace a slot");
            }
        }
        var mixed = new Piece[]{armor(EquipmentTier.LATENT), armor(EquipmentTier.DORMANT), armor(EquipmentTier.RESONANT), armor(TOP)};
        check(EquipmentRules.fullArmor(snapshot(TOP, mixed, EMPTY, EMPTY, true)), "Full armor does not require matching tiers");
    }

    private static void transcendentEquipment() {
        for (var kind : Kind.values()) {
            var fact = snapshot(EquipmentTier.LATENT, set(TOP), piece(kind, TOP), EMPTY, true);
            boolean weapon = kind == Kind.SWORD || kind == Kind.GLAIVE || kind == Kind.RANGED_WEAPON || kind == Kind.MAGIC_CASTER;
            check(EquipmentRules.transcendentSetAndWeapon(fact) == weapon, "Only actual weapon archetypes count: " + kind);
            if (!weapon) continue;
            check(!EquipmentRules.transcendentSetAndWeapon(snapshot(TOP, set(TOP), EMPTY, piece(kind, TOP), true)),
                    "Offhand weapon is not the mainhand weapon");
            check(!EquipmentRules.transcendentSetAndWeapon(snapshot(TOP, set(TOP), new Piece(kind, TOP, false), EMPTY, true)),
                    "Fractured/exhausted main weapon rejects");
            check(!EquipmentRules.transcendentSetAndWeapon(snapshot(TOP, set(TOP), piece(kind, TOP), EMPTY, false)),
                    "Incompatible hand loadout cannot wield functional weapon");
            for (var tier : EquipmentTier.values()) {
                boolean top = tier == TOP;
                check(EquipmentRules.transcendentSetAndWeapon(snapshot(TOP, set(TOP), piece(kind, tier), EMPTY, true)) == top,
                        "Current completed mainhand tier required");
                for (int slot = 0; slot < 4; slot++) {
                    Piece[] pieces = set(TOP); pieces[slot] = armor(tier);
                    check(EquipmentRules.transcendentSetAndWeapon(snapshot(TOP, pieces, piece(kind, TOP), EMPTY, true)) == top,
                            "All four pieces must have completed Transcendent tier");
                }
            }
        }
        var fact = snapshot(EquipmentTier.LATENT, set(TOP), piece(Kind.SWORD, TOP), EMPTY, true);
        check(EquipmentRules.transcendentSetAndWeapon(fact), "No sixth gathering tool, history or player tier requirement");
    }

    private static void glaive() {
        for (var playerTier : EquipmentTier.values()) for (var itemTier : EquipmentTier.values()) {
            boolean expected = playerTier == TOP && itemTier == TOP;
            check(EquipmentRules.transcendentGlaive(snapshot(playerTier, set(EquipmentTier.LATENT), piece(Kind.GLAIVE, itemTier), EMPTY, true)) == expected,
                    "Both player tier and current glaive tier required");
        }
        check(!EquipmentRules.transcendentGlaive(snapshot(TOP, set(TOP), new Piece(Kind.GLAIVE, TOP, false), EMPTY, true)), "Nonfunctional glaive rejects");
        check(!EquipmentRules.transcendentGlaive(snapshot(TOP, set(TOP), piece(Kind.SWORD, TOP), EMPTY, true)), "Sword is not a glaive");
        check(!EquipmentRules.transcendentGlaive(snapshot(TOP, set(TOP), EMPTY, piece(Kind.GLAIVE, TOP), true)), "Offhand glaive is not wielded");
        check(!EquipmentRules.transcendentGlaive(snapshot(TOP, set(TOP), piece(Kind.GLAIVE, TOP), piece(Kind.SHIELD, TOP), false)), "Blocked two-handed glaive rejects");
    }

    private static void swordShield() {
        for (var swordTier : EquipmentTier.values()) for (var shieldTier : EquipmentTier.values()) {
            check(EquipmentRules.swordAndShield(snapshot(EquipmentTier.LATENT, set(EquipmentTier.LATENT), piece(Kind.SWORD, swordTier), piece(Kind.SHIELD, shieldTier), true)),
                    "Any functional main sword and off shield tiers work");
        }
        check(!EquipmentRules.swordAndShield(snapshot(TOP, set(TOP), piece(Kind.SHIELD, TOP), piece(Kind.SWORD, TOP), true)), "Reversed hands reject");
        check(!EquipmentRules.swordAndShield(snapshot(TOP, set(TOP), new Piece(Kind.SWORD, TOP, false), piece(Kind.SHIELD, TOP), true)), "Broken sword rejects");
        check(!EquipmentRules.swordAndShield(snapshot(TOP, set(TOP), piece(Kind.SWORD, TOP), new Piece(Kind.SHIELD, TOP, false), true)), "Broken shield rejects");
        check(!EquipmentRules.swordAndShield(snapshot(TOP, set(TOP), piece(Kind.GLAIVE, TOP), piece(Kind.SHIELD, TOP), false)), "Glaive cannot replace sword");
    }

    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
