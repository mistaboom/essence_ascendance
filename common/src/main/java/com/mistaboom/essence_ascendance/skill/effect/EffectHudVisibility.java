package com.mistaboom.essence_ascendance.skill.effect;

import java.util.HashMap;
import java.util.Map;

/** Generic presentation-only active/default/removed lifecycle; no skill IDs or Minecraft dependency. */
public final class EffectHudVisibility<K> {
    private final long graceTicks;
    private final Map<K, State> states = new HashMap<>();

    public EffectHudVisibility(long graceTicks) {
        if (graceTicks < 0L) throw new IllegalArgumentException("Negative HUD grace duration");
        this.graceTicks = graceTicks;
    }

    public void replace(Map<K, Boolean> entries, long now) {
        states.keySet().retainAll(entries.keySet());
        entries.forEach((id, active) -> {
            State previous = states.get(id);
            if (active) states.put(id, new State(true, 0L));
            else if (previous != null && previous.active()) states.put(id, new State(false, now));
            // Initially-default cards stay hidden; repeated default packets never extend grace.
        });
    }

    public boolean visible(K id, long now) {
        State state = states.get(id);
        return state != null && (state.active()
                || (now >= state.defaultSince() && now - state.defaultSince() < graceTicks));
    }

    public void clear() { states.clear(); }
    private record State(boolean active, long defaultSince) { }
}
