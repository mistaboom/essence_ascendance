package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;

import java.util.Comparator;

/** Generated gameplay development, independent of a track's price and of current item ownership. */
public final class BonusDevelopment {
    private BonusDevelopment() { }

    public static double fraction(PlayerEssenceData data, StatCategory category, BalanceProfileDefinition profile) {
        return fraction(data, category.name(), profile, true);
    }

    public static double fraction(PlayerEssenceData data, String essenceId, BalanceProfileDefinition profile) {
        return fraction(data, essenceId, profile, false);
    }

    private static double fraction(PlayerEssenceData data, String category, BalanceProfileDefinition profile,
                                   boolean statCategory) {
        double realized = 0;
        double capacity = 0;
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            if (!(statCategory ? stat.category().name() : stat.essenceType().id().toString()).equals(category)) continue;
            double maximum = StatScalingService.maximumProgression(stat, data.getTier(), profile);
            if (maximum <= 0) continue;
            var track = profile.bonusTrack(stat.id());
            // The unit-weight fallback is only for explicit programmatic profiles without resolved tracks.
            double weight = track == null ? 1 : track.inputs().getOrDefault("marginal_power", 0.0);
            if (weight <= 0) continue;
            capacity += weight * maximum;
            realized += weight * StatScalingService.realizedProgressionForInvestment(stat, data.getInvested(stat), data.getTier(), profile);
        }
        return capacity <= 0 ? 0 : Math.clamp(realized / capacity, 0, 1);
    }

    /** Converts normalized development into the chapter's neutral accounting reference for mixing with receipts. */
    public static long reference(BalanceProfileDefinition profile, AscendanceTierDefinition tier, String essenceId) {
        AscendanceTierDefinition referenceTier = tier.grantsPower() ? tier : AscendanceTierRegistry.values().stream()
                .filter(AscendanceTierDefinition::grantsPower).min(Comparator.comparingInt(AscendanceTierDefinition::order))
                .orElseThrow(() -> new IllegalStateException("No powered tier for Bonus development reference"));
        long count = EssenceStatRegistry.values().stream().filter(stat -> stat.essenceType().id().toString().equals(essenceId)).count();
        // Category reference is a budget scale, never a sum of independently priced stat caps.
        double reference = Math.max(1, count) * (double) profile.getDefaultInvestmentCap(referenceTier);
        return Math.max(1, (long) Math.min(Long.MAX_VALUE / 1024, Math.ceil(reference)));
    }

    public static long equivalentInvestment(PlayerEssenceData data, String essenceId, BalanceProfileDefinition profile) {
        return Math.round(reference(profile, data.getTier(), essenceId) * fraction(data, essenceId, profile));
    }
}
