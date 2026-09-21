package com.mistaboom.essence_ascendance.progression;

import java.util.ArrayList;
import java.util.List;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeValueQuantization;

/** Selects complete native outcomes from nominal allocations. Floors never feed allocation or pricing. */
public final class MeaningfulProgression {
    private MeaningfulProgression() { }
    public static double first(double nominal, double floor, double quantum, double ceiling) {
        if (!Double.isFinite(floor) || floor < 0 || !Double.isFinite(ceiling) || ceiling < floor)
            throw new IllegalArgumentException("Meaningful floor exceeds mechanical ceiling");
        double value = Math.max(RuntimeValueQuantization.down(Math.min(nominal, ceiling), quantum),
                Math.ceil((floor - 1e-10) / quantum) * quantum);
        if (value > ceiling + 1e-9) throw new IllegalArgumentException("Quantized floor exceeds mechanical ceiling");
        return value;
    }
    public static boolean improves(double previous, double candidate, double floor) {
        if (!Double.isFinite(previous) || !Double.isFinite(candidate) || !Double.isFinite(floor) || floor <= 0)
            throw new IllegalArgumentException("Invalid meaningful improvement");
        return candidate - previous + 1e-9 >= floor;
    }
    /** No interpolation/subdivision: a strong allocation remains one state. */
    public static List<Double> select(List<Double> nominal, double firstFloor, double laterFloor,
                                      double quantum, double ceiling) {
        if (nominal.isEmpty()) throw new IllegalArgumentException("Missing nominal states");
        var result = new ArrayList<Double>();
        result.add(first(nominal.getFirst(), firstFloor, quantum, ceiling));
        for (int i = 1; i < nominal.size(); i++) {
            double value = RuntimeValueQuantization.down(Math.min(nominal.get(i), ceiling), quantum);
            if (improves(result.getLast(), value, laterFloor)) result.add(value);
        }
        return List.copyOf(result);
    }
}
