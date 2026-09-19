package com.mistaboom.essence_ascendance.skill.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable transient progress reservoir keyed by a gameplay identity such as an
 * entity type, block family, or dimension. Values saturate at the caller's
 * current generated maximum so rank/config changes never create invalid state.
 */
public final class KeyedProgressState<K> implements SkillEffectState {
    private final Map<K, Integer> values = new HashMap<>();
    private K lastKey;

    public int value(K key) {
        return key == null ? 0 : Math.max(0, values.getOrDefault(key, 0));
    }

    public int advance(K key, int maximum) {
        Objects.requireNonNull(key, "Progress key cannot be null");
        int cap = Math.max(1, maximum);
        int next = Math.min(cap, value(key) + 1);
        values.put(key, next);
        lastKey = key;
        return next;
    }

    public double fraction(K key, int maximum) {
        int cap = Math.max(1, maximum);
        return Math.clamp(value(key) / (double) cap, 0.0, 1.0);
    }

    public K lastKey() { return lastKey; }

    @Override public void clear() {
        values.clear();
        lastKey = null;
    }
}
