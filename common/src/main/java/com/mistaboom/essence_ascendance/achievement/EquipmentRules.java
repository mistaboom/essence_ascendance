package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshot;
import static com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshot.Kind;

/** Predicates over immutable current equipment facts; no inventory/history or award side effects. */
public final class EquipmentRules {
    private EquipmentRules() { }

    public static boolean fullArmor(EquipmentSnapshot equipment) { return equipment.fullFunctionalArmor(); }

    public static boolean transcendentSetAndWeapon(EquipmentSnapshot equipment) {
        return equipment.fullTranscendentArmor() && equipment.handsCompatible()
                && equipment.mainHand().functionalWeapon() && equipment.mainHand().transcendent();
    }

    public static boolean transcendentGlaive(EquipmentSnapshot equipment) {
        return equipment.playerTier() == EquipmentTier.TRANSCENDENT && equipment.handsCompatible()
                && equipment.mainHand().functional() && equipment.mainHand().kind() == Kind.GLAIVE
                && equipment.mainHand().transcendent();
    }

    public static boolean swordAndShield(EquipmentSnapshot equipment) {
        return equipment.handsCompatible() && equipment.mainHand().functional() && equipment.offHand().functional()
                && equipment.mainHand().kind() == Kind.SWORD && equipment.offHand().kind() == Kind.SHIELD;
    }
}
