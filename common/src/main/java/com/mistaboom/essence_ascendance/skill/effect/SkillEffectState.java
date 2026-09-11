package com.mistaboom.essence_ascendance.skill.effect;

/** Runtime-only effect state and cleanup contract; never serialized into player or world data. */
public interface SkillEffectState {
    default void clear() { }
}
