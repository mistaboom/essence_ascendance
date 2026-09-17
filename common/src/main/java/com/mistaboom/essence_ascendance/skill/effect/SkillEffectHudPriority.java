package com.mistaboom.essence_ascendance.skill.effect;

import java.util.Comparator;
import java.util.List;

/** Overflow ordering is shared by all cards: live short timers before ready meters and closing cards.
 * Stable ties retain the established registry order; nothing here names a skill or changes gameplay. */
public final class SkillEffectHudPriority {
    private SkillEffectHudPriority() { }
    public static List<SkillEffectHudEntry> order(List<SkillEffectHudEntry> entries, long now) {
        return entries.stream().sorted(Comparator.comparingInt((SkillEffectHudEntry entry) -> bucket(entry, now))
                .thenComparingLong(entry -> bucket(entry, now) == 0 ? entry.meter().expiresAt() : Long.MAX_VALUE)).toList();
    }
    private static int bucket(SkillEffectHudEntry entry, long now) {
        if (!entry.active()) return 2;
        return entry.meter().kind() == SkillEffectHudEntry.MeterKind.TIMER && entry.meter().expiresAt() > now ? 0 : 1;
    }
}
