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
}
