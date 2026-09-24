package com.mistaboom.essence_ascendance.presentation;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Predicate;

/** A localized nonnumeric behavior carried beside numeric metric bindings. */
public record PresentationBehavior<S>(String id, Component description, Predicate<S> applies) {
    public PresentationBehavior {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Behavior ID cannot be blank");
        Objects.requireNonNull(description, "Behavior description");
        Objects.requireNonNull(applies, "Behavior applicability");
    }

    public boolean appliesTo(S source) { return applies.test(Objects.requireNonNull(source)); }
}
