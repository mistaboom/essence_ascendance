package com.mistaboom.essence_ascendance.gameplay;

import java.util.UUID;
import java.util.Objects;

/** Immutable successful server observations. No award, entity, stack or durable item identity. */
public sealed interface CombatOutcome {
    Context context();
    record Weapon(String itemId, String profileId, int tier, String mode) {
        public static final Weapon UNKNOWN = new Weapon("", "", -1, "UNKNOWN");
        public Weapon {
            text(itemId, true); text(profileId, true); text(mode, false);
            if (tier < -1 || tier > 5) throw new IllegalArgumentException("Invalid action weapon tier");
            if (!profileId.isEmpty() && tier == -1)
                throw new IllegalArgumentException("Known weapon profile needs an action tier");
        }
        public boolean glaive() { return profileId.equals("essence_ascendance:glaive"); }
        public boolean ascendanceWeapon() {
            return glaive() || profileId.equals("essence_ascendance:sword")
                    || profileId.equals("essence_ascendance:ranged_weapon")
                    || profileId.equals("essence_ascendance:magic_caster");
        }
    }
    /**
     * Actor/action identity. Eligibility belongs to the originating action and follows delayed effects;
     * changing game mode cannot turn administrative or Creative damage into a survival action.
     * A future validated durable-weapon identity provider belongs here.
     */
    record Cause(String actionId, UUID actor, Weapon weapon, boolean eligibleOrigin) {
        public Cause { text(actionId, false); Objects.requireNonNull(actor); Objects.requireNonNull(weapon); }
        public Cause(String actionId, UUID actor, Weapon weapon) { this(actionId, actor, weapon, true); }
    }
    record Context(String actionId, UUID actor, UUID victim, String victimType, boolean hostile,
                   String dimension, long committedTick, Weapon weapon) {
        public Context {
            text(actionId, false); text(victimType, false); text(dimension, false);
            Objects.requireNonNull(actor); Objects.requireNonNull(victim); Objects.requireNonNull(weapon);
            if (committedTick < 0) throw new IllegalArgumentException("Invalid combat commit tick");
        }
    }
    record Damage(Context context, double contactDistance, double loss) implements CombatOutcome {
        public Damage {
            Objects.requireNonNull(context);
            // NaN explicitly represents a non-blade hit whose contact distance is unknown.
            if ((!Double.isNaN(contactDistance) && (!Double.isFinite(contactDistance) || contactDistance < 0))
                    || !Double.isFinite(loss) || loss <= 0)
                throw new IllegalArgumentException("Damage requires accepted positive loss and a valid/unknown distance");
        }
    }
    record Kill(Context context, double victimY, double actorY, boolean noBoots, boolean desperation,
                double healthFraction, boolean ledgerEligible, int sameActionHostileKills) implements CombatOutcome {
        public Kill {
            Objects.requireNonNull(context);
            if (!Double.isFinite(victimY) || !Double.isFinite(actorY) || !Double.isFinite(healthFraction)
                    || healthFraction < 0 || sameActionHostileKills < 0 || sameActionHostileKills > CombatSequences.MAX_VICTIMS)
                throw new IllegalArgumentException("Invalid confirmed kill snapshot");
        }
    }
    record Freeze(Context context) implements CombatOutcome { public Freeze { Objects.requireNonNull(context); } }
    record Pacified(Context context, boolean targetedActor, boolean undamagedEncounter) implements CombatOutcome {
        public Pacified { Objects.requireNonNull(context); }
    }
    private static void text(String value, boolean emptyAllowed) {
        if (value == null || value.length() > 256 || (value.isBlank() && (!emptyAllowed || !value.isEmpty())))
            throw new IllegalArgumentException("Invalid bounded combat identity");
    }
}
