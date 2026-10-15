package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.entity.EquipmentSlot;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/*
 * Coverage weights for stat effects supplied by worn armor.
 *
 * Physical Armor/Toughness also use this distribution in the baseline
 * service, but the concept is slot coverage rather than an ARMOR_SET conduit.
 */
public final class ArmorStatWeights {

    private static final Map<EquipmentSlot, Double> WEIGHTS;

    static {
        Map<EquipmentSlot, Double> weights =
                new EnumMap<>(EquipmentSlot.class);

        weights.put(EquipmentSlot.HEAD, 0.15);
        weights.put(EquipmentSlot.CHEST, 0.40);
        weights.put(EquipmentSlot.LEGS, 0.30);
        weights.put(EquipmentSlot.FEET, 0.15);

        WEIGHTS = Collections.unmodifiableMap(weights);
    }

    private ArmorStatWeights() {
    }

    public static void init() {
        double total = 0.0;
        for (double weight : WEIGHTS.values()) {
            if (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
                throw new IllegalStateException(
                        "Invalid Ascendance armor slot weight: " + weight
                );
            }
            total += weight;
        }

        if (Math.abs(total - 1.0) > 0.0000001) {
            throw new IllegalStateException(
                    "Ascendance armor slot weights must total 1.0, but total " + total
            );
        }
    }

    public static double weightFor(EquipmentSlot slot) {
        return WEIGHTS.getOrDefault(slot, 0.0);
    }

    public static Map<EquipmentSlot, Double> values() {
        return WEIGHTS;
    }

    /** Whole physical points, with deterministic largest-remainder allocation preserving the full-set total. */
    public static double physicalPointsForSlot(double fullSetPoints,EquipmentSlot requested) {
        if(!WEIGHTS.containsKey(requested)||fullSetPoints<=0)return 0;
        if(!Double.isFinite(fullSetPoints))throw new IllegalArgumentException("Physical armor points must be finite");
        // Exact input-double conversion avoids both long saturation and multi-point
        // floating multiplication errors at very large provider-supplied frontiers.
        var total=new java.math.BigDecimal(fullSetPoints).setScale(0,java.math.RoundingMode.HALF_UP).toBigIntegerExact();
        var allocated=java.math.BigInteger.ZERO;
        Map<EquipmentSlot,java.math.BigInteger> whole=new EnumMap<>(EquipmentSlot.class);
        Map<EquipmentSlot,java.math.BigDecimal> remainder=new EnumMap<>(EquipmentSlot.class);
        for(var entry:WEIGHTS.entrySet()) {
            var quota=new java.math.BigDecimal(total).multiply(java.math.BigDecimal.valueOf(entry.getValue()));
            var points=quota.setScale(0,java.math.RoundingMode.FLOOR).toBigIntegerExact();
            whole.put(entry.getKey(),points);allocated=allocated.add(points);
            remainder.put(entry.getKey(),quota.subtract(new java.math.BigDecimal(points)));
        }
        var priority=WEIGHTS.keySet().stream().sorted(java.util.Comparator
                .comparing((EquipmentSlot slot)->remainder.get(slot)).reversed()
                .thenComparing(EquipmentSlot::getName)).toList();
        int remaining=total.subtract(allocated).intValueExact();
        if(remaining<0||remaining>=priority.size())throw new IllegalStateException("Physical armor coverage weights must sum to one");
        for(int i=0;i<remaining;i++)whole.merge(priority.get(i),java.math.BigInteger.ONE,java.math.BigInteger::add);
        return whole.get(requested).doubleValue();
    }
}
