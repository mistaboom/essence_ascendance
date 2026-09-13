package com.mistaboom.essence_ascendance.balance.economy;

import java.util.ArrayList;
import java.util.List;

/** Conservative source categories; declared costs use the same units as economic value. */
public final class SourcePressurePolicy {
    private SourcePressurePolicy() { }

    public record Inputs(Double unitsPerSecond, Double startupCost, Double marginalCost,
                         Double parallelizability, Double playerAttention) {
        public Inputs {
            for (Double value : new Double[]{unitsPerSecond, startupCost, marginalCost, parallelizability, playerAttention})
                if (value != null && (!Double.isFinite(value) || value < 0)) throw new IllegalArgumentException("Invalid source-pressure observation");
            if (playerAttention != null && playerAttention > 1) throw new IllegalArgumentException("Player attention must be in [0,1]");
        }
    }

    public record Result(double multiplier, List<String> explanations) {
        public Result { explanations = List.copyOf(explanations); }
    }

    public static Result evaluate(Inputs inputs, EconomicValue economicValue, double pressure) {
        if (!Double.isFinite(pressure) || pressure < 0 || pressure > 1) throw new IllegalArgumentException("Invalid source pressure");
        List<String> reasons = new ArrayList<>();
        double load = 0;
        double value = Math.max(.000001, economicValue.amount());
        if (inputs.unitsPerSecond() != null) {
            load += Math.log1p(inputs.unitsPerSecond()) / Math.log(2);
            reasons.add("Declared throughput " + inputs.unitsPerSecond() + "/sec adds logarithmic supply pressure; no rate is inferred from per-event loot counts");
        }
        if (inputs.parallelizability() != null) {
            load += Math.log1p(Math.max(0, inputs.parallelizability() - 1)) / Math.log(2);
            reasons.add("Declared parallelizability " + inputs.parallelizability() + " adds scalable-source pressure");
        }
        if (inputs.playerAttention() != null) {
            load += 1 - inputs.playerAttention();
            reasons.add("Declared player attention " + inputs.playerAttention() + " reserves active versus passive production effort");
        }
        if (inputs.marginalCost() != null) {
            double ratio = Math.min(1, inputs.marginalCost() / value);
            load += 1 - ratio;
            reasons.add("Declared marginal cost " + inputs.marginalCost() + " relative to opportunity value reduces the pressure on expensive inputs; never raises yield above its base");
        }
        if (inputs.startupCost() != null) {
            double ratio = Math.min(1_000_000, inputs.startupCost() / value);
            load += .5 / (1 + Math.log1p(ratio));
            reasons.add("Declared startup cost " + inputs.startupCost() + " classifies infrastructure accessibility; sunk setup is never credited again per produced item");
        }
        return new Result(1 / (1 + pressure * load), reasons);
    }
}
