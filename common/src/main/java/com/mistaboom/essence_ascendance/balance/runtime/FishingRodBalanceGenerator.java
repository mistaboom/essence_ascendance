package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.config.FishingRodBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Native bobber range/gravity and generated equipment fractions seed an explicit grapple design model.
 * Reachable mobility competitors enter the existing calibrated capability pipeline, never fishing evidence. */
public final class FishingRodBalanceGenerator {
    private FishingRodBalanceGenerator() { }
    public static FishingRodBalanceSettings generate(Map<ResourceLocation, Double> fractions,
            Map<ResourceLocation, EquipmentBaselineConfig.TierBaseline> equipment,
            BalanceSettings settings, AdaptiveCompetitionCalibration competition) {
        var rows = new EnumMap<EquipmentTier, FishingRodBalanceSettings.Tier>(EquipmentTier.class);
        double power = Math.sqrt(settings.overallPower() * settings.apexPower());
        double priorRange = 0, priorCast = 0, priorAcceleration = 0, priorForce = 0, priorSpeed = 0;
        int priorDurability = 0;
        double priorGravity = .031;
        for (var tier : EquipmentTier.values()) {
            double fraction = fractions.get(tier.ascendanceTier().id());
            double range = competition.rodValue("range", tier, 12 + 84 * fraction * power, 12, 256);
            double speed = competition.rodValue("reelSpeed", tier, .3 + 1.2 * fraction * power, .3, 3);
            double acceleration = competition.rodValue("acceleration", tier, .012 + .048 * fraction * power, .012, .5);
            double force = competition.rodValue("maximumForce", tier, .12 + .28 * fraction * power, .12, 1);
            double cast = 1.1 + 2.9 * fraction * Math.min(2, power);
            // Strictly improving quantized chassis; ceilings reserve space for later tiers.
            int remaining = EquipmentTier.values().length - tier.ordinal() - 1;
            range = Math.min(256 - remaining, Math.max(priorRange + 1, Math.rint(range)));
            cast = Math.min(8 - remaining * .01, Math.max(priorCast + .01, round(cast)));
            speed = Math.min(3 - remaining * .01, Math.max(priorSpeed + .01, round(speed)));
            acceleration = Math.min(.5 - remaining * .001, Math.max(priorAcceleration + .001, round(acceleration)));
            force = Math.min(1 - remaining * .001, Math.max(priorForce + .001, round(force)));
            int durability = Math.min(Integer.MAX_VALUE / 64 - remaining,
                    Math.max(Math.max(64, priorDurability + 1), equipment.get(tier.ascendanceTier().id()).durability()));
            double gravity = Math.max(.001 + remaining * .001, Math.min(priorGravity - .001, round(.03 / (1 + 4 * fraction))));
            rows.put(tier, new FishingRodBalanceSettings.Tier(range, cast, gravity,
                    acceleration, Math.max(acceleration, force), speed, durability));
            priorRange = range; priorCast = cast; priorAcceleration = acceleration; priorForce = force;
            priorSpeed = speed; priorDurability = durability;
            priorGravity = gravity;
        }
        // Timing/control constants are published design inputs; no item or client owns a second table.
        return new FishingRodBalanceSettings(rows, 10, .75, .75, 20, 1, .06, 32);
    }
    private static double round(double value) { return Math.rint(value * 1000) / 1000; }
}
