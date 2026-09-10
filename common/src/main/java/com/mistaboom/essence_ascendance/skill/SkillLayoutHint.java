package com.mistaboom.essence_ascendance.skill;

/**
 * A deliberately small hint vocabulary for the skill-tree renderer.
 * Required tier, prerequisite depth, and display order remain authoritative;
 * these values only help separate nearby branches.
 */
public enum SkillLayoutHint {
    AUTO,
    UPPER,
    CENTER,
    LOWER
}
