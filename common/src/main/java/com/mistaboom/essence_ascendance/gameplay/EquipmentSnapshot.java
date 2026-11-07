package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import java.util.Objects;

/** Current actor-scoped equipment facts. No stack, owner, artifact history or durable item identity. */
public record EquipmentSnapshot(EquipmentTier playerTier, Piece head, Piece chest, Piece legs, Piece feet,
                                Piece mainHand, Piece offHand, boolean handsCompatible) {
    public enum Kind { NONE, ARMOR, SWORD, GLAIVE, RANGED_WEAPON, MAGIC_CASTER, SHIELD, OTHER }

    public record Piece(Kind kind, EquipmentTier tier, boolean functional) {
        public static final Piece EMPTY = new Piece(Kind.NONE, EquipmentTier.LATENT, false);
        public Piece { Objects.requireNonNull(kind); Objects.requireNonNull(tier); }
        public boolean functionalArmor() { return functional && kind == Kind.ARMOR; }
        public boolean functionalWeapon() {
            return functional && (kind == Kind.SWORD || kind == Kind.GLAIVE
                    || kind == Kind.RANGED_WEAPON || kind == Kind.MAGIC_CASTER);
        }
        public boolean transcendent() { return tier == EquipmentTier.TRANSCENDENT; }
    }

    public EquipmentSnapshot {
        Objects.requireNonNull(playerTier); Objects.requireNonNull(head); Objects.requireNonNull(chest);
        Objects.requireNonNull(legs); Objects.requireNonNull(feet);
        Objects.requireNonNull(mainHand); Objects.requireNonNull(offHand);
    }

    public boolean fullFunctionalArmor() {
        return head.functionalArmor() && chest.functionalArmor() && legs.functionalArmor() && feet.functionalArmor();
    }

    public boolean fullTranscendentArmor() {
        return fullFunctionalArmor() && head.transcendent() && chest.transcendent()
                && legs.transcendent() && feet.transcendent();
    }
}
