package com.mistaboom.essence_ascendance.balance.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Upper references within each acquisition band and slot, retaining usable earlier equipment.
 * Later gear is not averaged with obsolete tools; incompatible weapon families remain alternatives.
 */
public final class RobustFrontiers {
    private RobustFrontiers() { }

    public record CompetitiveFrontier(ProgressionBand band, String comparisonKey, double magnitude,
                                      double measuredMaximum, boolean capped, List<CapabilityEvidence.Functional> representatives) { }

    /** Comparable alternatives compete by maximum, never sum. One source has one vote even if it supplies
     * thousands of configurations. Keep actual witnesses so scope/cadence cannot be assembled from different gear. */
    public static List<CompetitiveFrontier> competitive(List<CapabilityEvidence.Functional> evidence, String policy) {
        List<CompetitiveFrontier> result = new ArrayList<>();
        Map<String, CompetitiveFrontier> retained = new TreeMap<>();
        for (ProgressionBand band : ProgressionBand.values()) {
            Map<String, Map<String, CapabilityEvidence.Functional>> groups = new TreeMap<>();
            for (var fact : evidence) {
                if (!fact.attainable() || !fact.source().reachable() || fact.source().confidence() < .5
                        || fact.source().stage() != band) continue;
                for (var m : fact.measurements()) {
                    if (m.magnitude() == null || m.origin() == CapabilityEvidence.Origin.NOMENCLATURE) continue;
                    String key = m.comparisonKey();
                    groups.computeIfAbsent(key, ignored -> new TreeMap<>()).merge(fact.source().subjectId(), fact,
                            (a, b) -> magnitude(a, key) >= magnitude(b, key) ? a : b);
                }
            }
            groups.forEach((key, sources) -> {
                var ordered = sources.values().stream().sorted(java.util.Comparator
                        .<CapabilityEvidence.Functional>comparingDouble(f -> magnitude(f, key)).reversed()
                        .thenComparing(f -> f.source().subjectId())).toList();
                double upper = percentile(ordered.stream().map(f -> magnitude(f, key)).toList(), 1, policy);
                double maximum = magnitude(ordered.getFirst(), key);
                List<CapabilityEvidence.Functional> representatives = ordered.stream()
                        .filter(f -> !policy.equals("EXCLUDE_UNSUPPORTED") || magnitude(f, key) <= upper).limit(3).toList();
                if (upper > 0) retained.merge(key, new CompetitiveFrontier(band, key, upper, maximum, upper < maximum, representatives),
                        (a, b) -> a.magnitude() >= b.magnitude() ? a : b);
            });
            retained.values().forEach(f -> result.add(new CompetitiveFrontier(band, f.comparisonKey(), f.magnitude(), f.measuredMaximum(), f.capped(), f.representatives())));
        }
        return List.copyOf(result);
    }
    private static double magnitude(CapabilityEvidence.Functional fact, String key) {
        return fact.measurements().stream().filter(m -> m.magnitude() != null && m.comparisonKey().equals(key)).mapToDouble(m -> m.magnitude()).max().orElse(0);
    }

    public static double percentile(List<Double> raw, double fraction) {
        return percentile(raw, fraction, "WINSORIZE");
    }
    public static double percentile(List<Double> raw, double fraction, String outlierPolicy) {
        List<Double> values = raw.stream().filter(v -> Double.isFinite(v) && v >= 0).sorted().toList();
        if (values.isEmpty()) return 0;
        // Median-relative ceiling resists a single absurd reachable item, including small populations.
        double median = quantile(values, 0.5);
        double ceiling = Math.max(median * 4, quantile(values, 0.25) * 8);
        if (values.size() >= 3 && ceiling > 0 && !outlierPolicy.equals("INCLUDE_ATTAINABLE")) {
            values = outlierPolicy.equals("WINSORIZE") ? values.stream().map(v -> Math.min(v, ceiling)).toList()
                    : values.stream().filter(v -> v <= ceiling).toList();
        }
        return quantile(values, fraction);
    }

    public static Map<ProgressionBand, Map<CapabilityAxis, Double>> build(List<EquipmentReference> equipment) {
        return build(equipment, "WINSORIZE");
    }
    public static Map<ProgressionBand, Map<CapabilityAxis, Double>> build(List<EquipmentReference> equipment, String outlierPolicy) {
        Map<ProgressionBand, Map<CapabilityAxis, Double>> output = new TreeMap<>();
        Map<String, Map<CapabilityAxis, Double>> retainedSlots = new TreeMap<>();
        List<WeaponCadence> usableWeapons = new ArrayList<>();
        for (ProgressionBand band : ProgressionBand.values()) {
            Map<String, Map<CapabilityAxis, List<Double>>> slots = new TreeMap<>();
            for (EquipmentReference ref : equipment) {
                if (!ref.included() || !ref.reachable() || ref.stage() != band) continue;
                Map<CapabilityAxis, List<Double>> axes = slots.computeIfAbsent(ref.slot(), ignored -> new EnumMap<>(CapabilityAxis.class));
                ref.axes().forEach((axis, value) -> axes.computeIfAbsent(axis, ignored -> new ArrayList<>()).add(value));
            }
            Map<String, Double> damageCeilings = new TreeMap<>();
            slots.forEach((slot, axes) -> axes.forEach((axis, values) -> {
                // A non-pickaxe's zero harvest level is not a competing harvesting tool.
                // This is a frontier: use the best accepted measurement, not the average catalog item.
                double value = upper(values, outlierPolicy);
                retainedSlots.computeIfAbsent(slot, ignored -> new EnumMap<>(CapabilityAxis.class)).merge(axis, value, Math::max);
                if (axis == CapabilityAxis.SUSTAINED_DAMAGE) damageCeilings.put(slot, value);
            }));
            for (EquipmentReference ref : equipment) {
                if (!ref.included() || !ref.reachable() || ref.stage() != band) continue;
                double sustained = ref.axes().getOrDefault(CapabilityAxis.SUSTAINED_DAMAGE, 0.0);
                double rate = ref.axes().getOrDefault(CapabilityAxis.ATTACK_RATE, 0.0);
                double ceiling = damageCeilings.getOrDefault(ref.slot(), 0.0);
                if (sustained <= 0 || rate <= 0 || ceiling <= 0) continue;
                // Winsorizing retains the weapon with capped damage, including its actual cadence.
                // Only exclusion removes an above-ceiling weapon from cadence selection.
                if (outlierPolicy.equals("EXCLUDE_UNSUPPORTED") && sustained > ceiling + 1e-9) continue;
                double policySustained = outlierPolicy.equals("WINSORIZE") ? Math.min(sustained, ceiling) : sustained;
                usableWeapons.add(new WeaponCadence(ref.itemId(), policySustained, rate));
            }
            Map<CapabilityAxis, Double> resolved = new EnumMap<>(CapabilityAxis.class);
            retainedSlots.forEach((slot, axes) -> axes.forEach((axis, value) -> {
                if (armorSlot(slot) && (axis == CapabilityAxis.ARMOR || axis == CapabilityAxis.TOUGHNESS))
                    resolved.merge(axis, value, Double::sum);
                else resolved.merge(axis, value, Math::max);
            }));
            // Taking a hoe's fastest rate and a sword's DPS invents a weaker per-hit weapon.
            // Match cadence to the actual strongest sustained weapon; burst remains its own envelope.
            usableWeapons.stream().max(java.util.Comparator
                    .comparingDouble(WeaponCadence::sustained)
                    .thenComparing(WeaponCadence::itemId))
                    .ifPresent(ref -> resolved.put(CapabilityAxis.ATTACK_RATE, ref.rate()));
            if (resolved.containsKey(CapabilityAxis.ARMOR)) {
                double armor = resolved.get(CapabilityAxis.ARMOR), toughness = resolved.getOrDefault(CapabilityAxis.TOUGHNESS, 0.0);
                double damageTaken = 1 - Math.min(20, Math.max(armor / 5, armor - 10 / (2 + toughness / 4))) / 25;
                resolved.put(CapabilityAxis.EFFECTIVE_HEALTH, 20 / damageTaken);
            }
            output.put(band, Map.copyOf(resolved));
        }
        return output;
    }

    private record WeaponCadence(String itemId, double sustained, double rate) { }

    static double upper(List<Double> values, String outlierPolicy) {
        return percentile(values.stream().filter(value -> value > 0).toList(), 1, outlierPolicy);
    }

    private static boolean armorSlot(String slot) { return List.of("head", "chest", "legs", "feet").contains(slot); }
    private static double quantile(List<Double> values, double fraction) {
        if (values.isEmpty()) return 0;
        double position = Math.max(0, Math.min(1, fraction)) * (values.size() - 1);
        int lower = (int) Math.floor(position), upper = (int) Math.ceil(position);
        return values.get(lower) + (values.get(upper) - values.get(lower)) * (position - lower);
    }
}
