package com.mistaboom.essence_ascendance.attunement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.*;

/** Recent source exposure measured in generated reference outcomes, never callback counts. */
final class AttunementSourceHistory {
    private final Map<String, Double> exposure = new TreeMap<>();

    double freshFraction(String source, double window) {
        return Math.clamp(1 - exposure.getOrDefault(source, 0.0) / window, 0, 1);
    }

    double otherFraction(String source, double window) {
        return Math.clamp(exposure.entrySet().stream().filter(e -> !e.getKey().equals(source))
                .mapToDouble(Map.Entry::getValue).sum() / window, 0, 1);
    }

    static double averageEfficiency(double fresh, double work, double window, double floor) {
        double ratio = work / window;
        double averageFreshness = ratio == 0 ? 1 : -Math.expm1(-ratio) / ratio;
        return floor + (1 - floor) * fresh * averageFreshness;
    }

    static double averageVariety(double fresh, double other, double work, double window, double floor, double strength) {
        double x = work / window;
        double first = x == 0 ? 1 : -Math.expm1(-x) / x;
        double second = x == 0 ? 1 : -Math.expm1(-2 * x) / (2 * x);
        double repetition = averageEfficiency(fresh, work, window, floor);
        // Integrate repetition * diversity together. Packet subdivision cannot refresh a bonus.
        return 1 + strength * other * (floor * first + (1 - floor) * fresh * second) / repetition;
    }

    void add(String source, double work, double window) {
        if (!(work > 0)) return;
        double decay = Math.exp(-work / window);
        exposure.replaceAll((key, value) -> value * decay);
        exposure.merge(source, window * -Math.expm1(-work / window), Double::sum);
        exposure.entrySet().removeIf(entry -> entry.getValue() < Math.ulp(window));
        while (exposure.size() > AttunementLedger.MAX_HISTORY) {
            String smallest = exposure.entrySet().stream().min(Map.Entry.<String, Double>comparingByValue()
                    .thenComparing(Map.Entry.comparingByKey())).orElseThrow().getKey();
            exposure.remove(smallest);
        }
    }

    ListTag save() {
        ListTag entries = new ListTag();
        exposure.forEach((source, work) -> {
            CompoundTag tag = new CompoundTag(); tag.putString("source", source); tag.putDouble("work", work); entries.add(tag);
        });
        return entries;
    }

    static AttunementSourceHistory load(ListTag entries) {
        var history = new AttunementSourceHistory();
        for (int i = 0; i < Math.min(entries.size(), AttunementLedger.MAX_HISTORY); i++) {
            CompoundTag tag = entries.getCompound(i); double work = tag.getDouble("work");
            if (tag.getString("source").length() <= 256 && Double.isFinite(work) && work > 0 && work <= AttunementLedger.MAX_HISTORY)
                history.exposure.put(tag.getString("source"), work);
        }
        return history;
    }
}
