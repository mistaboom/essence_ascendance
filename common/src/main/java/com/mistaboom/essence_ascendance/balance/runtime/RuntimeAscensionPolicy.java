package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementDefinition;
import com.mistaboom.essence_ascendance.progression.HarvestProgressionSafety;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Economic qualification for player tiers; no assumed external mining or boss checklist. */
public final class RuntimeAscensionPolicy {
    public static final String COMPOSITION_KEY = "ascension_essence_qualification";
    private RuntimeAscensionPolicy() { }

    public static boolean usesEssenceQualification(RuntimeBalanceDefinition runtime) {
        return runtime.composition().getOrDefault(COMPOSITION_KEY, 0.0) == 1.0;
    }

    public static Map<ResourceLocation, AscendanceAdvancementDefinition> generate(BalanceProfileDefinition profile) {
        List<AscendanceTierDefinition> tiers = orderedTiers();
        Map<ResourceLocation, AscendanceAdvancementDefinition> result = new LinkedHashMap<>();
        long previousRequired = 0;
        for (int index = 0; index + 1 < tiers.size(); index++) {
            var from = tiers.get(index); var to = tiers.get(index + 1);
            long cap = profile.getDefaultInvestmentCap(from);
            long target = Math.max(profile.getDefaultInvestmentCap(to), Math.addExact(previousRequired, 1));
            // The existing advancement schema expresses cost as a current-cap multiple.
            // Ceiling division reaches the next generated tier's budget without a table.
            long multiplier = 1 + (target - 1) / cap;
            var id = ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,
                    from.id().getPath() + "_to_" + to.id().getPath());
            var definition = new AscendanceAdvancementDefinition(id, from.id(), to.id(),
                    multiplier, 0, 0, 1.0, MilestoneRequirement.always());
            previousRequired = definition.getRequiredInvestment(cap);
            result.put(id, definition);
        }
        return result;
    }

    /** New profiles fail generation/decoding instead of installing a known impossible chain. */
    static void validate(RuntimeBalanceDefinition runtime) {
        Double mode = runtime.composition().get(COMPOSITION_KEY);
        if (mode == null) return; // Previously saved profiles keep their original policy until rebuild.
        if (mode != 1.0) throw new IllegalArgumentException("Unsupported generated ascension qualification policy");
        var config = runtime.config(); var tiers = orderedTiers();
        if (config.advancements().size() != tiers.size() - 1)
            throw new IllegalArgumentException("Ascension progression must contain every adjacent tier transition exactly once");
        long previous = 0;
        for (int index = 0; index + 1 < tiers.size(); index++) {
            var from = tiers.get(index); var to = tiers.get(index + 1);
            var matches = config.advancements().values().stream().filter(a -> a.fromTierId().equals(from.id())).toList();
            if (matches.size() != 1 || !matches.getFirst().toTierId().equals(to.id()) || to.order() != from.order() + 1)
                throw new IllegalArgumentException("Missing, duplicate or skipped ascension transition from " + from.id());
            var advancement = matches.getFirst();
            long required = advancement.getRequiredInvestment(config.balanceProfile().getDefaultInvestmentCap(from));
            if (required <= previous)
                throw new IllegalArgumentException("Ascension qualification thresholds must strictly increase: " + advancement.id());
            previous = required;
            var usableStats = EssenceStatRegistry.values().stream()
                    .filter(stat -> config.balanceProfile().getInvestmentCap(from, stat) > 0).toList();
            long categories = usableStats.stream().map(stat -> stat.category()).distinct().count();
            if (advancement.minimumDevelopedStats() > usableStats.size()
                    || advancement.minimumRepresentedCategories() > categories)
                throw new IllegalArgumentException("Ascension breadth exceeds available stats/categories: " + advancement.id());
        }
        var miningLocks = HarvestProgressionSafety.evaluate(config);
        if (!miningLocks.isEmpty())
            throw new IllegalArgumentException("Ascension requires mining capability unlocked by a later tier: " + miningLocks);
    }

    private static List<AscendanceTierDefinition> orderedTiers() {
        return AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
    }
}
