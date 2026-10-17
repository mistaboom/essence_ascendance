package com.mistaboom.essence_ascendance.balance.engine;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import java.util.*;

/** Native modifier identities and operations, without repeating an entity's base value per item. */
public record NativeAttributeContribution(Map<String, Modifier> modifiers) {
    public static final NativeAttributeContribution EMPTY = new NativeAttributeContribution(Map.of());
    public record Modifier(double amount, AttributeModifier.Operation operation) {
        public Modifier {
            if (!Double.isFinite(amount) || operation == null) throw new IllegalArgumentException("Invalid native attribute modifier");
        }
    }
    public NativeAttributeContribution {
        modifiers = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(modifiers)));
    }
    public NativeAttributeContribution combine(NativeAttributeContribution other) {
        if (other.modifiers.isEmpty()) return this;
        if (modifiers.isEmpty()) return other;
        var combined = new TreeMap<>(modifiers);
        // A native attribute has one modifier per ID. Later native equipment slots replace an earlier ID.
        combined.putAll(other.modifiers);
        return new NativeAttributeContribution(combined);
    }
    public double apply(double base) {
        double addition = 0, multipliedBase = 0, multipliedTotal = 1;
        for (var modifier : modifiers.values()) switch (modifier.operation()) {
            case ADD_VALUE -> addition += modifier.amount();
            case ADD_MULTIPLIED_BASE -> multipliedBase += modifier.amount();
            case ADD_MULTIPLIED_TOTAL -> multipliedTotal *= 1 + modifier.amount();
        }
        double value = (base + addition) * (1 + multipliedBase) * multipliedTotal;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Native attribute arithmetic overflow");
        return value;
    }
}
