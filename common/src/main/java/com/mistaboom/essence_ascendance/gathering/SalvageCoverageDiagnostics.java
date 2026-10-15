package com.mistaboom.essence_ascendance.gathering;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.*;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Opt-in live-recipe diagnostic only. Never called by routine generation, loading, or player ticks. */
public final class SalvageCoverageDiagnostics {
    private static final Gson JSON = BalanceDocument.GSON;
    private static final double EPSILON = 1e-9;
    private SalvageCoverageDiagnostics() { }

    public record Export(Path path, int eligibleItems, int materialBills, int passedRanks, int failedRanks, int unknownRanks) { }

    public static Export export(MinecraftServer server) throws java.io.IOException {
        var active = GeneratedBalanceService.active();
        var report = collect(server.getRecipeManager().getRecipes(), server.registryAccess(), active.runtime(), active.evidence().resources());
        report.addProperty("profileIntegrity", active.document().integrity());
        report.addProperty("createdUtc", Instant.now().toString());
        report.addProperty("source", "Explicit live-server command: current loaded native recipes and installed saved profile evidence; no regeneration");
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC).format(Instant.now());
        Path path = BalanceReportLayout.diagnostics(GeneratedBalanceService.directory()).resolve("salvage-coverage-" + stamp + ".json");
        BalanceProfileStore.writeAtomically(path, JSON.toJson(report) + "\n");
        var summary = report.getAsJsonObject("summary");
        return new Export(path, summary.get("eligibleItems").getAsInt(), summary.get("materialBills").getAsInt(),
                summary.get("passedRanks").getAsInt(), summary.get("failedRanks").getAsInt(), summary.get("unknownRanks").getAsInt());
    }

    /** Index outputs once: O(recipes + eligible matching recipes), not an all-item/all-recipe product. */
    public static JsonObject collect(Collection<RecipeHolder<?>> recipes, HolderLookup.Provider registries,
                                     RuntimeBalanceDefinition runtime, Map<String, ResourceEvidence> resources) {
        var indexed = new IdentityHashMap<Item, List<RecipeHolder<?>>>();
        int opaqueOutputs = 0;
        for (var holder : recipes) {
            try {
                var output = holder.value().getResultItem(registries);
                if (output != null && !output.isEmpty()) indexed.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(holder);
            } catch (RuntimeException unavailable) { opaqueOutputs++; }
        }
        var ranks = ranks(runtime);
        var rankSummaries = new ArrayList<JsonObject>();
        for (var rank : ranks) {
            var row = new JsonObject();
            row.addProperty("rank", rank.number()); row.addProperty("requiredTier", rank.tier());
            row.addProperty("requiredAcquisitionBand", rank.band().name()); row.addProperty("configuredFraction", rank.fraction());
            row.addProperty("requiredEffectiveFractionIncrease", rank.number() == 1 ? 0 : .1);
            for (String list : List.of("supportedItemIds", "confirmedUnderFloorItemIds", "accessUnknownPassingItemIds", "mechanicallyPassingItemIds")) row.add(list, new JsonArray());
            for (String count : List.of("saturatedRows", "changedRows", "maskedRows", "underFloorRows")) row.addProperty(count, 0);
            rankSummaries.add(row);
        }
        var items = new JsonArray();
        int eligible = 0, bills = 0, itemErrors = 0;
        for (var item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            try { if (!EquipmentMaintenanceService.eligible(item.getDefaultInstance())) continue; }
            catch (RuntimeException unavailable) { itemErrors++; continue; }
            eligible++;
            var row = new JsonObject(); row.addProperty("item", id);
            var candidates = indexed.getOrDefault(item, List.of());
            row.add("loadedOutputRecipes", JSON.toJsonTree(candidates.stream().map(r -> r.id().toString()).toList()));
            final List<ConstructionMaterialResolver.MaterialAmount> materials;
            try { materials = ConstructionMaterialResolver.resolveRecipes(item, candidates, registries); }
            catch (RuntimeException unavailable) {
                itemErrors++; row.addProperty("materialEligibility", "UNKNOWN_RESOLVER_EXCEPTION");
                row.addProperty("detail", unavailable.toString()); items.add(row); continue;
            }
            if (materials.isEmpty()) {
                row.addProperty("materialEligibility", "NO_PROVEN_LOSSY_COMMON_BILL");
                row.addProperty("detail", candidates.isEmpty() ? "No observed output recipe; opaque/dynamic output remains unknown"
                        : "Resolver denies material recovery: ambiguous consumption, disjoint/too-small bills, or opaque alternative");
                items.add(row); continue;
            }
            bills++;
            double units = materials.stream().mapToDouble(ConstructionMaterialResolver.MaterialAmount::amount).sum();
            row.addProperty("materialEligibility", "PROVEN_COMMON_BILL"); row.addProperty("constructionUnitsPerOutput", units);
            var materialRows = new JsonArray();
            for (var material : materials) {
                var part = new JsonObject(); part.addProperty("item", BuiltInRegistries.ITEM.getKey(material.item()).toString());
                part.addProperty("amountPerOutput", material.amount()); materialRows.add(part);
            }
            row.add("materials", materialRows);
            var evidence = resources.get(id);
            row.add("savedAccessEvidence", JSON.toJsonTree(evidence));
            var outcomes = new JsonArray(); double previous = 0;
            for (int i = 0; i < ranks.size(); i++) {
                var rank = ranks.get(i); var aggregate = rankSummaries.get(i);
                double count = GatheringSalvageService.expectedMaterialRecovery(units, rank.fraction());
                double fraction = count / units, increment = fraction - previous;
                boolean floor = i == 0 ? count + EPSILON >= 1 : increment + EPSILON >= .1;
                boolean saturated = count + EPSILON >= GatheringSalvageService.materialRecoveryLimit(units);
                boolean masked = i > 0 && increment <= EPSILON;
                var access = access(evidence, rank.band());
                var outcome = new JsonObject();
                outcome.addProperty("rank", rank.number()); outcome.addProperty("expectedMaterials", count);
                outcome.addProperty("effectiveFraction", fraction); outcome.addProperty("incrementFromPreviousRank", increment);
                outcome.addProperty("guaranteedAtLeastOneMaterial", rank.fraction() > 0 && count >= 1);
                outcome.addProperty("strictExpectedMaterialLoss", count < units);
                outcome.addProperty("nativeFloorStatus", floor ? "PASS" : "FAIL");
                outcome.addProperty("accessStatus", access); outcome.addProperty("saturated", saturated); outcome.addProperty("masked", masked);
                outcomes.add(outcome);
                if (saturated) increment(aggregate, "saturatedRows");
                if (i > 0 && increment > EPSILON) increment(aggregate, "changedRows");
                if (masked) increment(aggregate, "maskedRows");
                if (!floor) increment(aggregate, "underFloorRows");
                if (floor) {
                    aggregate.getAsJsonArray("mechanicallyPassingItemIds").add(id);
                    aggregate.getAsJsonArray(access.equals("SUPPORTED_SOURCE_OPPORTUNITY") ? "supportedItemIds" : "accessUnknownPassingItemIds").add(id);
                } else if (access.equals("SUPPORTED_SOURCE_OPPORTUNITY")) aggregate.getAsJsonArray("confirmedUnderFloorItemIds").add(id);
                previous = fraction;
            }
            row.add("ranks", outcomes); items.add(row);
        }
        int passed = 0, failed = 0, unknown = 0;
        var rankRows = new JsonArray();
        for (var row : rankSummaries) {
            String status;
            if (!row.getAsJsonArray("supportedItemIds").isEmpty()) { status = "PASS_CONDITIONAL_SUPPORTED_INPUT"; passed++; }
            else if (row.getAsJsonArray("mechanicallyPassingItemIds").isEmpty() && itemErrors == 0) {
                status = "FAIL_NO_LOADED_MATERIAL_BILL_MEETS_NATIVE_FLOOR"; failed++;
            } else { status = "UNKNOWN_NO_STAGE_COMPATIBLE_SOURCE_WITNESS"; unknown++; }
            row.addProperty("status", status); rankRows.add(row);
        }
        var report = new JsonObject();
        report.addProperty("coverage", "Every registered eligible default equipment stack and observed loaded output recipe; runtime expectation consumer, not sampled luck or live item acquisition");
        report.addProperty("minimumPolicy", "First rank guarantees one useful material on each valid material bill; later rank needs >=10 percentage points effective expected recovery on a stage-compatible supported input. Saturated eligible inputs remain usable.");
        report.addProperty("accessPolicy", "Supported witness requires saved reachable resource, compatible aggregate and source stage, confidence >=0.5, positive expected source output, explicit accessProven availability with no uncertainty and positive occurrence chance. This proves a conditional opportunity, not guaranteed finite inventory or renewable supply. Null/absent availability is UNKNOWN.");
        report.addProperty("conservationPolicy", "Expected per-material conservation, not a worst-case single-roll value cap. Count is rolled only after input consumption.");
        report.addProperty("unknownDynamicOutputs", opaqueOutputs);
        report.addProperty("uninspectableItemDefaults", itemErrors);
        report.add("ranks", rankRows); report.add("items", items);
        var summary = new JsonObject(); summary.addProperty("loadedRecipes", recipes.size()); summary.addProperty("eligibleItems", eligible);
        summary.addProperty("materialBills", bills); summary.addProperty("incompatibleOrUnprovedItems", eligible - bills);
        summary.addProperty("passedRanks", passed); summary.addProperty("failedRanks", failed); summary.addProperty("unknownRanks", unknown);
        report.add("summary", summary); return report;
    }

    private static String access(ResourceEvidence evidence, ProgressionBand band) {
        if (evidence == null || !evidence.reachable() || evidence.confidence() < .5) return "UNKNOWN_SAVED_ACCESS";
        if (evidence.stage().ordinal() > band.ordinal()) return "LATER_SAVED_STAGE";
        for (var source : evidence.sources()) {
            var availability = source.availability();
            if (source.kind() != AcquisitionSource.Kind.ADMINISTRATIVE && source.confidence() >= .5 && source.expectedOutput() > 0
                    && source.stage().ordinal() <= band.ordinal() && availability != null && availability.accessProven()
                    && availability.uncertainty().isEmpty() && availability.occurrenceChance() > 0 && availability.expectedPerEvent() > 0)
                return "SUPPORTED_SOURCE_OPPORTUNITY";
        }
        return "UNKNOWN_CONCRETE_SOURCE";
    }

    private record Rank(int number, String tier, ProgressionBand band, double fraction) { }
    private static List<Rank> ranks(RuntimeBalanceDefinition runtime) {
        return SkillBalanceRuntime.withCurves(runtime.skillCurves(), () -> {
            var result = new ArrayList<Rank>(); var skill = SkillRegistry.require(SkillIds.SALVAGERS_CRAFT);
            var curve = runtime.skillCurves().get(skill.id().toString());
            var tiers = AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(t -> t.order())).toList();
            for (var rank : curve.ranks()) {
                if (rank.rank() > curve.maximumRank()) continue;
                var tier = AscendanceTierRegistry.get(skill.requiredTierId(rank.rank())).orElseThrow();
                var effects = SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(), Map.of(skill.id(), rank.rank()), runtime.skillCurves());
                result.add(new Rank(rank.rank(), tier.id().toString(), ProgressionBand.at(tiers.indexOf(tier)), effects.gathering().salvagersCraft().materialRecoveryFraction()));
            }
            return List.copyOf(result);
        });
    }
    private static void increment(JsonObject row, String key) { row.addProperty(key, row.get(key).getAsInt() + 1); }
}
