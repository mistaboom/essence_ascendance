package com.mistaboom.essence_ascendance.skill;

public enum SkillRequirementKind {
    PLAYER_ATTUNEMENT,
    PERMANENT_MILESTONE,
    BONUS_INVESTMENT,
    DISCOVERY;

    public String translationKey() {
        return "skill_requirement.essence_ascendance.type."
                + name().toLowerCase(java.util.Locale.ROOT);
    }
}
