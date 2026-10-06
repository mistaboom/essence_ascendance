package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import java.util.*;

/** Price-only bounded response to explicitly measured, external recurring supply.
 * The one-item/second normalization and square-root elasticity are design policy. Unknown rates
 * retain the resource-effort budget; they are never imputed as observed player throughput. */
public final class SkillEconomyAccess {
    private SkillEconomyAccess() { }
    public record Rate(String item, String source, double unitsPerSecond, double categoryYield, double essencePerSecond) { }
    public record Decision(double priceFactor, List<Rate> supportedRates, int unknownRateSources, String policy) { }
    public static Decision evaluate(PackEvidence evidence, EconomyProfile economy, ProgressionBand band, String essence) {
        var rates = new ArrayList<Rate>(); int unknown = 0;
        if (economy != null) for (var resource : evidence.resources().values()) {
            var yield = economy.resources().get(resource.itemId());
            if (yield == null || !resource.external() || !resource.reachable() || resource.confidence() < .5) continue;
            double category = yield.routedYields().getOrDefault(essence, 0.0);
            if (category <= 0) continue;
            for (var source : resource.sources()) {
                var access = source.availability();
                if (!source.renewable() || source.stage().ordinal() > band.ordinal() || source.confidence() < .5
                        || source.kind() == AcquisitionSource.Kind.ADMINISTRATIVE || source.kind() == AcquisitionSource.Kind.QUEST_REWARD
                        || access != null && (!access.accessProven() || !access.uncertainty().isEmpty())) continue;
                if (!source.rateKnown() || !Double.isFinite(source.unitsPerSecond()) || source.unitsPerSecond() <= 0) { unknown++; continue; }
                double throughput = category * source.unitsPerSecond();
                if (Double.isFinite(throughput)) rates.add(new Rate(resource.itemId(), source.id(), source.unitsPerSecond(), category, throughput));
            }
        }
        rates.sort(Comparator.comparing(Rate::item).thenComparing(Rate::source));
        // Median resists a single extreme source; independent alternatives are not added together.
        double[] normalized = rates.stream().mapToDouble(Rate::unitsPerSecond).sorted().toArray();
        double factor = normalized.length == 0 ? 1 : Math.clamp(Math.sqrt(normalized[normalized.length / 2]), .5, 4);
        return new Decision(factor, List.copyOf(rates), unknown,
                "Base price uses generated per-item Essence and declared resource effort. Known recurring rates add bounded sqrt(median items/s / 1 item/s) price pressure; unknown rates are neutral, never measured as 1 item/s. Availability and strength do not consume this factor.");
    }
}
