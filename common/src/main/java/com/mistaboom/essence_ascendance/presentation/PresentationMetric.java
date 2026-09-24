package com.mistaboom.essence_ascendance.presentation;

import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * One typed, read-only numeric fact used by prose and tabular presentation.
 * The reader returns the native value; conversion is display-only.
 */
public record PresentationMetric<S>(
        String id,
        Component label,
        ToDoubleFunction<S> read,
        SemanticValueType valueType,
        DisplayConversion display,
        Predicate<S> applies
) {
    public PresentationMetric {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Metric ID cannot be blank");
        Objects.requireNonNull(label, "Metric label");
        Objects.requireNonNull(read, "Metric reader");
        Objects.requireNonNull(valueType, "Metric semantic value type");
        Objects.requireNonNull(display, "Metric display conversion");
        Objects.requireNonNull(applies, "Metric applicability");
    }

    public Reading read(S source) {
        Objects.requireNonNull(source, "Metric source");
        if (!applies.test(source)) return Reading.notApplicable(valueType);
        double raw = read.applyAsDouble(source);
        if (!Double.isFinite(raw)) throw new IllegalArgumentException("Nonfinite presentation metric " + id);
        double converted = display.convert(raw);
        return new Reading(true, raw, converted, display.formatConverted(converted), valueType);
    }

    public enum SemanticValueType {
        SCALAR,
        FRACTION,
        PERCENTAGE,
        PERCENTAGE_POINTS,
        MULTIPLIER,
        COUNT,
        TICKS,
        SECONDS,
        BLOCKS,
        ESSENCE
    }

    /** Scale and significant-figure policy are explicit so small nonzero values survive. */
    public record DisplayConversion(double scale, int significantFigures) {
        public static final DisplayConversion NATIVE = new DisplayConversion(1, 4);
        public static final DisplayConversion FRACTION_TO_PERCENT = new DisplayConversion(100, 4);
        public static final DisplayConversion TICKS_TO_SECONDS = new DisplayConversion(1.0 / 20.0, 4);

        public DisplayConversion {
            if (!Double.isFinite(scale) || scale == 0 || significantFigures < 1 || significantFigures > 16)
                throw new IllegalArgumentException("Invalid metric display conversion");
        }

        public double convert(double raw) { return raw * scale; }

        public String format(double raw) { return formatConverted(convert(raw)); }

        public String formatConverted(double converted) {
            if (!Double.isFinite(converted)) throw new IllegalArgumentException("Nonfinite display value");
            return BigDecimal.valueOf(converted)
                    .round(new MathContext(significantFigures, RoundingMode.HALF_UP))
                    .stripTrailingZeros().toPlainString();
        }
    }

    public record Reading(boolean applicable, double rawValue, double displayValue,
                          String formatted, SemanticValueType valueType) {
        public Reading {
            Objects.requireNonNull(formatted, "Formatted metric value");
            Objects.requireNonNull(valueType, "Metric semantic value type");
        }

        private static Reading notApplicable(SemanticValueType type) {
            return new Reading(false, 0, 0, "", type);
        }
    }

    public static <S> PresentationMetric<S> always(String id, Component label,
                                                    ToDoubleFunction<S> read,
                                                    SemanticValueType valueType,
                                                    DisplayConversion display) {
        return new PresentationMetric<>(id, label, read, valueType, display, ignored -> true);
    }
}
