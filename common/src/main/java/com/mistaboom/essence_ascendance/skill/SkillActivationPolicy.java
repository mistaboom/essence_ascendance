package com.mistaboom.essence_ascendance.skill;

/**
 * Describes how an owned skill participates in the player's active loadout.
 * This is framework metadata only; skill gameplay effects are implemented
 * separately.
 */
public enum SkillActivationPolicy {
    /**
     * Needs no direct loadout selection; it becomes effective automatically
     * while its live requirements and inherited prerequisite branch are active.
     */
    AUTOMATIC,

    /** Requires selection through a mutually exclusive choice group. */
    SELECTABLE,

    /** May be enabled or disabled freely after purchase. */
    TOGGLE;

    public String translationKey() {
        return "skill_activation.essence_ascendance."
                + name().toLowerCase(java.util.Locale.ROOT);
    }
}
