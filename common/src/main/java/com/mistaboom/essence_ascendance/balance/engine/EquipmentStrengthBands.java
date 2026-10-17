package com.mistaboom.essence_ascendance.balance.engine;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Relative catalog groups; a secondary capability cannot assign a weapon's combat band. */
public final class EquipmentStrengthBands {
    private EquipmentStrengthBands() { }
    public static final String EXPLICIT_STAGE = "explicit_catalog_stage";

    static List<EquipmentReference> primary(List<EquipmentReference> rows, String policy) {
        var ceilings = RobustFrontiers.equipmentCeilings(rows, policy);
        var bands = rank(rows, EquipmentReference::slot, row -> strength(row, ceilings, policy));
        return rows.stream().map(row -> new EquipmentReference(row.itemId(), row.slot(),
                row.capabilities().contains(EXPLICIT_STAGE) ? row.stage() : bands.get(row.itemId()),
                row.axes(), row.capabilities(), row.reachable(), row.included(), row.confidence(), row.reason(), row.attributeModifiers())).toList();
    }

    /** Mining is independently ranked: weak combat damage does not make a fast pickaxe an entry tool. */
    public static Map<String, ProgressionBand> mining(List<EquipmentReference> rows, String policy) {
        var miners = rows.stream().filter(row -> row.axes().containsKey(CapabilityAxis.MINING_SPEED)).toList();
        var ceilings = RobustFrontiers.equipmentCeilings(rows, policy);
        var result = rank(miners, ignored -> "mining", row -> adjusted(row, CapabilityAxis.MINING_SPEED, ceilings, policy));
        for (var row : miners) if (!row.capabilities().contains(EquipmentReference.ESTIMATED_CATALOG_REFERENCE)
                || row.capabilities().contains(EXPLICIT_STAGE)) result.put(row.itemId(), row.stage());
        return result;
    }

    private static Map<String, ProgressionBand> rank(List<EquipmentReference> rows,
            Function<EquipmentReference, String> group, ToDoubleFunction<EquipmentReference> strength) {
        Map<String, List<Double>> populations = new TreeMap<>();
        for (var row : rows) {
            double value = strength.applyAsDouble(row);
            if (row.usableReference() && Double.isFinite(value) && value > 0)
                populations.computeIfAbsent(group.apply(row), ignored -> new ArrayList<>()).add(value);
        }
        populations.replaceAll((key, values) -> values.stream().distinct().sorted().toList());
        Map<String, ProgressionBand> result = new HashMap<>();
        for (var row : rows) {
            var values = populations.getOrDefault(group.apply(row), List.of());
            double value = strength.applyAsDouble(row);
            int index = Collections.binarySearch(values, value);
            // Rejected measurements remain visible at APEX but cannot crowd accepted strength bands.
            int band = !Double.isFinite(value) ? 4 : values.size() <= 1 || index < 0 ? 0
                    : (int) Math.round(index * 4.0 / (values.size() - 1));
            result.put(row.itemId(), ProgressionBand.at(band));
        }
        return result;
    }

    private static double strength(EquipmentReference row, Map<String, Map<CapabilityAxis, Double>> ceilings, String policy) {
        var axes = row.axes();
        if (axes.containsKey(CapabilityAxis.ARMOR)) return adjusted(row, CapabilityAxis.ARMOR, ceilings, policy)
                + adjusted(row, CapabilityAxis.TOUGHNESS, ceilings, policy);
        if (row.slot().equals("mainhand_tool") && axes.containsKey(CapabilityAxis.MINING_SPEED))
            return adjusted(row, CapabilityAxis.MINING_SPEED, ceilings, policy);
        var axis = axes.containsKey(CapabilityAxis.SUSTAINED_DAMAGE) ? CapabilityAxis.SUSTAINED_DAMAGE
                : axes.containsKey(CapabilityAxis.MINING_SPEED) ? CapabilityAxis.MINING_SPEED : CapabilityAxis.DURABILITY;
        return adjusted(row, axis, ceilings, policy);
    }

    private static double adjusted(EquipmentReference row, CapabilityAxis axis,
                                   Map<String, Map<CapabilityAxis, Double>> ceilings, String policy) {
        double value = row.axes().getOrDefault(axis, 0.0);
        double ceiling = ceilings.getOrDefault(row.slot(), Map.of()).getOrDefault(axis, 0.0);
        if (!Double.isFinite(value) || value < 0 || policy.equals("EXCLUDE_UNSUPPORTED") && value > ceiling) return Double.NaN;
        return policy.equals("WINSORIZE") ? Math.min(value, ceiling) : value;
    }
}
