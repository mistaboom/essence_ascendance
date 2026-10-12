package com.mistaboom.essence_ascendance.config;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import java.util.*;

/** Published native-unit rod chassis. The force ramp also sets the opening hook-set duration;
 * steeringFraction weights camera-relative steering during that opening only. */
public record FishingRodBalanceSettings(Map<EquipmentTier, Tier> tiers, int forceRampTicks,
        double settlingDistance, double steeringFraction, int wearIntervalTicks, int wearPerInterval,
        double buoyancySpeed, double waterSkiingDistance) {
    public FishingRodBalanceSettings {
        Objects.requireNonNull(tiers, "Missing fishing rod balance; explicitly rebuild generated balance");
        var copy = new EnumMap<EquipmentTier, Tier>(EquipmentTier.class); copy.putAll(tiers);
        if (copy.size() != EquipmentTier.values().length) throw new IllegalArgumentException("Incomplete rod tiers");
        Tier previous = null;
        for (EquipmentTier tier : EquipmentTier.values()) {
            Tier next = Objects.requireNonNull(copy.get(tier), "Missing rod tier " + tier);
            if (previous != null && (next.range() <= previous.range() || next.castSpeed() <= previous.castSpeed()
                    || next.gravity() >= previous.gravity() || next.acceleration() <= previous.acceleration()
                    || next.maximumForce() <= previous.maximumForce() || next.reelSpeed() <= previous.reelSpeed()
                    || next.durability() <= previous.durability()))
                throw new IllegalArgumentException("Rod progression must improve every physical property: " + tier);
            previous = next;
        }
        if (forceRampTicks < 1 || forceRampTicks > 40 || wearIntervalTicks < 1 || wearIntervalTicks > 1200
                || wearPerInterval < 1 || wearPerInterval > 100) throw new IllegalArgumentException("Invalid rod tick policy");
        bounded(settlingDistance, .1, 4); bounded(steeringFraction, 0, .95);
        bounded(buoyancySpeed, .001, .15); bounded(waterSkiingDistance, 10, 1000);
        tiers = Collections.unmodifiableMap(copy);
    }
    public Tier tier(EquipmentTier tier) { return Objects.requireNonNull(tiers.get(tier)); }
    public record Tier(double range, double castSpeed, double gravity, double acceleration,
                       double maximumForce, double reelSpeed, int durability) {
        public Tier {
            bounded(range, 1, 256); bounded(castSpeed, .1, 8); bounded(gravity, .001, .1);
            bounded(acceleration, .001, .5); bounded(maximumForce, acceleration, 1);
            bounded(reelSpeed, .05, 3);
            if (durability < 2 || durability > Integer.MAX_VALUE / 64) throw new IllegalArgumentException("Invalid rod durability");
        }
    }
    private static void bounded(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Rod native-unit bound: " + value);
    }
}
