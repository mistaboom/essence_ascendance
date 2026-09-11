package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Full replacement snapshot: omitted cards are ineffective and disappear without grace. */
public record SkillEffectHudSnapshot(long serverGameTime, int playerEntityId,
                                     ResourceLocation dimension, List<SkillEffectHudEntry> entries) {
    public static final int MAX_ENTRIES = 256;

    public SkillEffectHudSnapshot {
        Objects.requireNonNull(dimension);
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many HUD entries");
        Set<ResourceLocation> ids = new HashSet<>();
        for (SkillEffectHudEntry entry : entries) {
            if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate HUD entry: " + entry.id());
        }
    }

    /** The clock is excluded so unchanged snapshots need only periodic reconciliation. */
    public boolean sameState(SkillEffectHudSnapshot other) {
        return other != null && playerEntityId == other.playerEntityId
                && dimension.equals(other.dimension) && entries.equals(other.entries);
    }
}
