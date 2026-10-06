package com.mistaboom.essence_ascendance.balance.engine;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Reusable boundary for optional APIs. Callers stage output and publish only successful attempts.
 * Failure is unknown/excluded evidence, never an authoritative empty result. VM failures propagate. */
public final class OptionalIntegration {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("essence_ascendance");
    private OptionalIntegration() { }
    public record Attempt<T>(Optional<T> value, String failure) {
        public boolean succeeded() { return value.isPresent(); }
    }
    public static <T> Attempt<T> attempt(String integration, String operation, Supplier<T> action) {
        try {
            return new Attempt<>(Optional.of(Objects.requireNonNull(action.get(), "Integration returned null evidence")), "");
        } catch (RuntimeException | LinkageError failure) {
            String detail = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            warn(integration, operation + ": " + detail);
            return new Attempt<>(Optional.empty(), detail);
        }
    }
    public static void warn(String integration, String detail) {
        LOGGER.warn("Balance compatibility [{}]: {}; affected evidence excluded or advisory, accuracy may be reduced", integration, detail);
    }
}
