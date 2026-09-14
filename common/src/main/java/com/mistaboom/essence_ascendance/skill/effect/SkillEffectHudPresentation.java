package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared, testable presentation lifecycle. No game/client authority or skill-specific branches. */
public final class SkillEffectHudPresentation {
    public static final long DEFAULT_GRACE_TICKS = 60L;
    private final EffectHudVisibility<ResourceLocation> visibility = new EffectHudVisibility<>(DEFAULT_GRACE_TICKS);
    private final Map<ResourceLocation, SkillEffectHudEntry> displayed = new LinkedHashMap<>();
    private List<SkillEffectHudEntry> entries = List.of();

    public void replace(List<SkillEffectHudEntry> incoming, long now) {
        Map<ResourceLocation, Boolean> active = new LinkedHashMap<>();
        for (SkillEffectHudEntry entry : incoming) {
            active.put(entry.id(), entry.active());
            // Preserve the accepted offense behavior: freeze the last active card
            // during closing; repeated inactive snapshots must not restart its delay.
            if (entry.active() || !displayed.containsKey(entry.id())) displayed.put(entry.id(), entry);
        }
        displayed.keySet().retainAll(active.keySet());
        visibility.replace(active, now);
        entries = List.copyOf(incoming);
    }

    public List<SkillEffectHudEntry> visibleEntries(long now) {
        return entries.stream().filter(entry -> visibility.visible(entry.id(), now))
                .map(entry -> displayed.getOrDefault(entry.id(), entry)).toList();
    }

    public void clear() {
        entries = List.of();
        displayed.clear();
        visibility.clear();
    }
}
