package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TntBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/*
 * Shared procedural economic analysis. Generated defaults and diagnostics
 * consume the same cached results.
 * Explicit mapping overrides and dissolution eligibility remain separate layers.
 * No normalization to legacy yields or later progression/machine costs occurs.
 */
public final class ProceduralValuationEngine {

    private static final Object INDEX_LOCK = new Object();

    private static volatile MinecraftServer indexedServer;
    private static volatile ProceduralValuationIndex index;
    private static volatile List<ProceduralValuationResult> cachedResults;
    private static GenerationDataSnapshot generationData;

    private ProceduralValuationEngine() {
    }

    public static ProceduralValuationResult evaluate(
            MinecraftServer server,
            ItemStack stack
    ) {
        if (server == null) {
            throw new IllegalArgumentException("Server cannot be null");
        }
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("ItemStack cannot be empty");
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return evaluateAll(server).stream().filter(result -> result.itemId().equals(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unregistered item: " + id));
    }

    /** One deterministic generation shared by diagnostics and generated defaults. */
    public static List<ProceduralValuationResult> evaluateAll(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("Server cannot be null");
        synchronized (INDEX_LOCK) {
            ProceduralValuationIndex snapshot;
            try (var phase = BalancePerformance.phase("valuation_index")) {
                snapshot = ensureIndex(server);
            }
            if (cachedResults != null) {
                BalancePerformance.flag("valuation_results_reused", true);
                BalancePerformance.increment("valuation_result_cache_hits");
                return cachedResults;
            }
            EvaluationContext context = new EvaluationContext(snapshot);
            List<Item> items = generationData(server).items().stream().filter(item -> item != Items.AIR)
                    .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
            long started = System.nanoTime();
            try (var phase = BalancePerformance.phase("acquisition_solve")) {
                solveAcquisitionGraph(items, context);
            }
            com.mistaboom.essence_ascendance.EssenceAscendance.LOGGER.info(
                    "Pack balance acquisition solved: {} items in {} ms; routing next", items.size(), (System.nanoTime() - started) / 1_000_000);
            started = System.nanoTime();
            List<ProceduralValuationResult> complete = new ArrayList<>(items.size());
            try (var phase = BalancePerformance.phase("essence_routing")) {
            for (Item item : items) {
                complete.add(evaluateItem(snapshot, item, context));
            }
            }
            BalancePerformance.count("valuation_items", items.size());
            com.mistaboom.essence_ascendance.EssenceAscendance.LOGGER.info(
                    "Pack balance routing complete: {} items in {} ms", items.size(), (System.nanoTime() - started) / 1_000_000);
            cachedResults = List.copyOf(complete);
            return cachedResults;
        }
    }

    public static void clear() {
        synchronized (INDEX_LOCK) {
            indexedServer = null;
            index = null;
            cachedResults = null;
            if (generationData != null) generationData.close();
            generationData = null;
            ValuationGenerationInputs.clear();
        }
    }

    public static void prepareGeneration(com.mistaboom.essence_ascendance.balance.config.BalanceOverrides overrides, GenerationDataSnapshot inputs) {
        synchronized (INDEX_LOCK) {
            clear();
            generationData = inputs;
            ValuationGenerationInputs.configure(overrides);
        }
    }

    /** Shared generation adapters reuse this snapshot instead of rescanning loaded pack data. */
    static ProceduralValuationIndex generationIndex(MinecraftServer server) {
        synchronized (INDEX_LOCK) { return ensureIndex(server); }
    }

    /* Synchronous relaxation over complete previous-round snapshots, not a DFS
     * cache whose first answer depends on the active recursion stack. Every input
     * alternative is visited. Each chosen path carries its acquisition ancestry;
     * a recipe/trade cannot establish a cheaper price through its own descendants.
     * Unresolved paths remain diagnostics rather than seeds of known acquisition.
     */
    private static void solveAcquisitionGraph(List<Item> items, EvaluationContext context) {
        context.solving = true;
        Map<Item, EvaluationNode> previous = new IdentityHashMap<>();
        boolean converged = false;
        for (int pass = 0; pass < ProceduralValuationSettings.MAX_GRAPH_PASSES; pass++) {
            context.previous = previous;
            Map<Item, EvaluationNode> next = new IdentityHashMap<>();
            for (Item item : items) {
                context.activeRoot = item;
                EvaluationNode candidate;
                try {
                    candidate = evaluateNode(item, context, new LinkedHashSet<>(), 0);
                } finally {
                    context.activeRoot = null;
                }
                EvaluationNode old = previous.get(item);
                next.put(item, preferPath(candidate, old));
            }
            normalizeGraphFamilies(items, next, context);
            boolean changed = false;
            for (Item item : items) {
                EvaluationNode old = previous.get(item), value = next.get(item);
                if (old == null || old.acquisitionValue() != value.acquisitionValue()
                        || old.knownAcquisition() != value.knownAcquisition()
                        || old.recipeDepth() != value.recipeDepth()
                        || !old.dependencies().equals(value.dependencies())) {
                    changed = true;
                    break;
                }
            }
            previous = next;
            if (!changed) { converged = true; break; }
        }
        context.solving = false;
        context.activeRoot = null;
        if (!converged) throw new IllegalStateException("Acquisition graph did not converge after "
                + ProceduralValuationSettings.MAX_GRAPH_PASSES + " passes; live generation was NOT replaced");
        context.memo().putAll(previous);
        context.conservationMemo().putAll(previous);
        context.solved = true;
    }

    private static EvaluationNode preferPath(EvaluationNode candidate, EvaluationNode old) {
        if (old == null) return candidate;
        if (candidate.knownAcquisition() != old.knownAcquisition())
            return candidate.knownAcquisition() ? candidate : old;
        if (candidate.acquisitionValue() != old.acquisitionValue())
            return candidate.acquisitionValue() < old.acquisitionValue() ? candidate : old;
        if (candidate.recipeDepth() != old.recipeDepth())
            return candidate.recipeDepth() < old.recipeDepth() ? candidate : old;
        if (candidate.dependencies().size() != old.dependencies().size())
            return candidate.dependencies().size() < old.dependencies().size() ? candidate : old;
        return old;
    }

    private static void normalizeGraphFamilies(List<Item> items, Map<Item, EvaluationNode> nodes,
                                               EvaluationContext context) {
        Set<Item> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Item item : items) {
            if (!seen.add(item)) continue;
            List<Item> family = context.index().conservationGroup(item);
            seen.addAll(family);
            if (family.size() < 2) continue;
            ProceduralConservationMath.Plan<Item> plan = context.index().conservationPlan(item);
            if (!plan.valid()) continue; // Exact payout policy rejects this family later.
            Item anchor = null;
            double bestUnit = Double.POSITIVE_INFINITY;
            boolean known = family.stream().anyMatch(member -> nodes.get(member).knownAcquisition());
            for (Item member : family) {
                EvaluationNode node = nodes.get(member);
                if (known && !node.knownAcquisition()) continue;
                double unit = (double) node.acquisitionValue() / plan.units().get(member);
                if (unit < bestUnit) { bestUnit = unit; anchor = member; }
            }
            if (anchor == null) continue;
            EvaluationNode anchorNode = nodes.get(anchor);
            Set<Item> dependencies = new LinkedHashSet<>(anchorNode.dependencies());
            dependencies.addAll(family);
            for (Item member : family) {
                EvaluationNode local = nodes.get(member);
                long converted = clampValue(Math.ceil(bestUnit * plan.units().get(member) - 0.0000001));
                // Ceil economics here; final integer unit/Essence payout rounds DOWN once.
                List<String> factors = new ArrayList<>(local.factors());
                if (member != anchor) {
                    factors = new ArrayList<>(intrinsic(member, context).factors());
                    factors.add("Conservation external anchor: " + BuiltInRegistries.ITEM.getKey(anchor)
                            + " -> " + converted + "; anchor costs propagated before recipe selection");
                    factors.addAll(anchorNode.factors().stream().filter(f -> !f.startsWith("Conservation external anchor"))
                            .limit(12).map(f -> "Anchor: " + f).toList());
                }
                EvaluationNode normalized = new EvaluationNode(local.intrinsicValue(), converted,
                        member == anchor ? anchorNode.recipeChoice() : Optional.empty(),
                        anchorNode.progressionBand(), anchorNode.confidence(), anchorNode.recipeDepth(),
                        anchorNode.knownAcquisition(), false, anchorNode.inferredProgressionScore(),
                        anchorNode.progressionEvidenceCount(), List.copyOf(factors), Set.copyOf(dependencies));
                nodes.put(member, normalized);
            }
        }
    }

    private static ProceduralValuationResult evaluateItem(
            ProceduralValuationIndex snapshot,
            Item item,
            EvaluationContext context
    ) {
        EvaluationNode node = evaluateNode(
                item,
                context,
                new LinkedHashSet<>(),
                0
        );
        node = normalizeConservationGroupCached(item, node, context);

        DownstreamInfo downstream = downstreamInfo(item, context);
        double downstreamMultiplier = conservationDownstreamMultiplier(item, context, downstream);
        long finalValue = clampValue(node.acquisitionValue() * downstreamMultiplier);

        RoutingResolution routing = resolveRouting(item, node, snapshot, context);
        RouteWeights route = routing.weights();

        FamilyPayout familyPayout = resolveFamilyPayout(item, context);
        Map<EssenceDefinition, Long> routed;
        if (familyPayout != null) {
            finalValue = familyPayout.total();
            routed = familyPayout.routed();
        } else {
            routed = allocate(finalValue, route);
        }
        boolean modeledAcquisition = node.knownAcquisition()
                && (familyPayout == null || familyPayout.modeled());
        List<String> factors = new ArrayList<>(node.factors());
        if (familyPayout != null) factors.add(familyPayout.explanation());
        if (downstream.recipeCount() > 0) {
            factors.add(
                    "Downstream demand: "
                            + downstream.recipeCount()
                            + " recipe(s), "
                            + downstream.significantCount()
                            + " significant, "
                            + downstream.crossModCount()
                            + " cross-mod -> x"
                            + format(downstreamMultiplier)
            );
        } else {
            factors.add("Downstream demand: no loaded non-conservation recipes consume this item");
        }
        if (snapshot.conservationGroup(item).size() > 1
                && downstreamMultiplier > downstream.multiplier() + 0.000001) {
            factors.add(
                    "Conservation-group demand: reversible forms share the strongest downstream modifier -> x"
                            + format(downstreamMultiplier)
            );
        }
        ProceduralValuationResult.RoutingDiagnostics diagnostics = routing.diagnostics();
        if (!diagnostics.nameHints().isEmpty()) {
            factors.add("Nomenclature hint [" + diagnostics.nameHintSource() + "]: "
                    + String.join("/", diagnostics.nameHints())
                    + " (routing only; no price, tier, rarity or acquisition-confidence boost)");
        }
        factors.add("Essence routing: " + String.join(" + ", diagnostics.evidence())
                + "; routing confidence " + diagnostics.confidence().name());
        if (diagnostics.evidence().contains("utility_fallback")) {
            factors.add("Essence routing: no functional evidence found; a single Utility fallback"
                    + " used before any weak acquisition-context vote");
        }
        factors.add("Essence routing: at most two dominant categories; a secondary category must carry"
                + " at least 25% of the strongest semantic vote; reversible forms share this selection");

        double confidence = node.confidence();
        // Confidence should reflect whether acquisition itself is actually
        // modeled, not whether the item happened to land in a semantic c: tag.
        // A fully resolved recipe/loot/trade/fishing path is at least medium
        // confidence even for unusual special/modded items. Unknown fallbacks
        // remain LOW.
        if (modeledAcquisition) {
            confidence = Math.max(confidence, 0.60);
            if (node.progressionEvidenceCount() > 0) {
                confidence = Math.max(confidence, 0.64);
            }
        }
        if (downstream.recipeCount() > 0) {
            confidence += 0.06;
        }
        // A name is a routing hypothesis, not proof that acquisition is modeled.
        if (!directRouting(item, context).structured().isEmpty()) {
            confidence += 0.08;
        }
        confidence = ProceduralValuationConfidence.bound(confidence, modeledAcquisition);
        if (!modeledAcquisition) factors.add("Economic confidence capped LOW: acquisition is unresolved; "
                + "classification, downstream uses and advancements cannot certify its price");

        return new ProceduralValuationResult(
                BuiltInRegistries.ITEM.getKey(item),
                finalValue,
                node.intrinsicValue(),
                node.recipeChoice(),
                downstreamMultiplier,
                node.progressionBand(),
                node.inferredProgressionScore(),
                node.progressionEvidenceCount(),
                confidence,
                renewability(item, snapshot).multiplier(),
                routed,
                snapshot.recipesProducing(item).size(),
                downstream.recipeCount(),
                downstream.significantCount(),
                downstream.crossModCount(),
                snapshot.dropSources(item).size(),
                snapshot.blockDropSources(item).size(),
                (int) snapshot.containerLootSources(item).stream().filter(source -> !source.archaeology()).count(),
                snapshot.fishingLootSources(item).size(),
                snapshot.tradeSources(item).size(),
                downstream.examples(),
                factors,
                diagnostics,
                modeledAcquisition,
                (int) snapshot.containerLootSources(item).stream().filter(ProceduralValuationIndex.ContainerLootSource::archaeology).count(),
                familyPayout == null ? "not_applicable" : familyPayout.status()
        );
    }

    private static FamilyPayout resolveFamilyPayout(Item item, EvaluationContext context) {
        List<Item> family = context.index().conservationGroup(item);
        if (family.size() < 2) return null;
        FamilyPayout cached = context.familyPayoutMemo().get(item);
        if (cached != null) return cached;
        ProceduralConservationMath.Plan<Item> plan = context.index().conservationPlan(item);
        if (!plan.valid()) {
            FamilyPayout rejected = new FamilyPayout(0, Map.of(), false, "invalid_family",
                    "Conservation payout withheld: " + plan.problem() + "; no guessed positive payout");
            family.forEach(member -> context.familyPayoutMemo().put(member, rejected));
            return rejected;
        }
        Map<Item, EvaluationNode> locals = new IdentityHashMap<>();
        boolean hasKnownAnchor = false;
        for (Item member : family) {
            EvaluationNode local = evaluateNode(member, context, new LinkedHashSet<>(), 0);
            locals.put(member, local);
            hasKnownAnchor |= local.knownAcquisition();
        }
        double perUnit = Double.POSITIVE_INFINITY;
        double demand = 1.0;
        long maximumUnits = 1;
        for (Item member : family) {
            EvaluationNode local = locals.get(member);
            long units = plan.units().get(member);
            maximumUnits = Math.max(maximumUnits, units);
            demand = Math.max(demand, downstreamInfo(member, context).multiplier());
            if (!hasKnownAnchor || local.knownAcquisition()) {
                perUnit = Math.min(perUnit, (double) local.acquisitionValue() / units);
            }
        }
        long unitValue = ProceduralConservationMath.unitPayout(perUnit * demand, maximumUnits,
                ProceduralValuationSettings.MAX_VALUE);
        Item first = family.getFirst();
        RouteWeights shared = resolveRouting(first, locals.get(first), context.index(), context).weights();
        Map<EssenceDefinition, Long> primitiveRoute = allocate(unitValue, shared);
        for (Item member : family) {
            long units = plan.units().get(member);
            Map<EssenceDefinition, Long> scaled = new LinkedHashMap<>();
            primitiveRoute.forEach((essence, amount) -> scaled.put(essence, Math.multiplyExact(amount, units)));
            String status = unitValue == 0 ? "below_integer_precision"
                    : hasKnownAnchor ? "exact" : "exact_unresolved_anchor";
            String explanation = "Conservation integer payout: " + units + " primitive unit(s) x " + unitValue
                    + "; total and every Essence scale exactly across all " + family.size() + " forms"
                    + (hasKnownAnchor ? "" : "; no resolved external anchor, diagnostic fallback only")
                    + (unitValue == 0 ? "; positive payout cannot be represented safely" : "");
            context.familyPayoutMemo().put(member, new FamilyPayout(Math.multiplyExact(unitValue, units),
                    Map.copyOf(scaled), hasKnownAnchor && unitValue > 0, status, explanation));
        }
        return context.familyPayoutMemo().get(item);
    }

    private static EvaluationNode normalizeConservationGroupCached(
            Item target,
            EvaluationNode original,
            EvaluationContext context
    ) {
        if (context.solving || context.solved) return original;
        EvaluationNode cached = context.conservationMemo().get(target);
        if (cached != null) {
            return cached;
        }

        // Conservation normalization is a graph traversal layered on top of
        // recipe evaluation. Re-entering the same target means a reversible
        // family loop was followed; keep the local node for that branch rather
        // than resetting cycle state and recursing forever.
        if (!context.conservationVisiting().add(target)) {
            return original;
        }

        try {
            EvaluationNode normalized = normalizeConservationGroup(
                    target,
                    context,
                    original
            );
            if (!normalized.contextSensitive()) {
                context.conservationMemo().put(target, normalized);
            }
            return normalized;
        } finally {
            context.conservationVisiting().remove(target);
        }
    }

    private static EvaluationNode normalizeConservationGroup(
            Item target,
            EvaluationContext context,
            EvaluationNode original
    ) {
        ProceduralValuationIndex index = context.index();
        List<Item> group = index.conservationGroup(target);
        if (group.size() <= 1) {
            return original;
        }

        EvaluationNode bestNode = original.knownAcquisition() ? original : null;
        Item bestSource = original.knownAcquisition() ? target : null;
        double bestFactor = 1.0;
        double bestValue = original.knownAcquisition()
                ? original.acquisitionValue()
                : Double.POSITIVE_INFINITY;

        for (Item member : group) {
            if (member == target) {
                continue;
            }
            double factor = index.conservationFactor(member, target);
            if (!Double.isFinite(factor) || factor <= 0.0) {
                continue;
            }
            LinkedHashSet<Item> memberVisiting = new LinkedHashSet<>();
            memberVisiting.add(target);
            EvaluationNode memberNode = evaluateNode(
                    member,
                    context,
                    memberVisiting,
                    0
            );
            if (!memberNode.knownAcquisition()) {
                continue;
            }
            double candidateValue = memberNode.acquisitionValue() * factor;
            if (candidateValue < bestValue) {
                bestValue = candidateValue;
                bestNode = memberNode;
                bestSource = member;
                bestFactor = factor;
            }
        }

        if (bestNode == null || bestSource == null) {
            List<String> factors = new ArrayList<>(original.factors());
            factors.add("Conservation group: no fully modeled external anchor found; local fallback retained");
            return new EvaluationNode(
                    original.intrinsicValue(),
                    original.acquisitionValue(),
                    original.recipeChoice(),
                    original.progressionBand(),
                    original.confidence(),
                    original.recipeDepth(),
                    original.knownAcquisition(),
                    original.contextSensitive(),
                    original.inferredProgressionScore(),
                    original.progressionEvidenceCount(),
                    List.copyOf(factors)
            );
        }

        long normalizedValue = clampValue(Math.max(1.0, bestValue));
        List<String> factors = new ArrayList<>(original.factors());
        ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(bestSource);
        if (bestSource == target) {
            factors.add(
                    "Conservation group: local acquisition is the cheapest external anchor across "
                            + group.size()
                            + " reversible form(s)"
            );
        } else {
            factors.add(
                    "Conservation group: cheapest external anchor is "
                            + sourceId
                            + " converted at value factor "
                            + format(bestFactor)
                            + " -> "
                            + formatLong(normalizedValue)
            );
            bestNode.factors().stream().limit(4).forEach(
                    factor -> factors.add("Conservation source: " + factor)
            );
        }

        return new EvaluationNode(
                original.intrinsicValue(),
                normalizedValue,
                bestSource == target ? original.recipeChoice() : Optional.empty(),
                ProceduralValuationResult.ProgressionBand.max(
                        original.progressionBand(),
                        bestNode.progressionBand()
                ),
                Math.max(original.confidence(), Math.min(0.94, bestNode.confidence())),
                bestSource == target ? original.recipeDepth() : Math.max(1, bestNode.recipeDepth() + 1),
                true,
                false,
                Math.max(original.inferredProgressionScore(), bestNode.inferredProgressionScore()),
                Math.max(original.progressionEvidenceCount(), bestNode.progressionEvidenceCount()),
                List.copyOf(factors)
        );
    }

    private static double conservationDownstreamMultiplier(
            Item item,
            EvaluationContext context,
            DownstreamInfo own
    ) {
        double multiplier = own.multiplier();
        for (Item member : context.index().conservationGroup(item)) {
            if (member == item) {
                continue;
            }
            multiplier = Math.max(multiplier, downstreamInfo(member, context).multiplier());
        }
        return multiplier;
    }

    public static IndexSummary rebuild(MinecraftServer server) {
        synchronized (INDEX_LOCK) {
            ProceduralValuationIndex built = ProceduralValuationIndex.build(server, generationData(server));
            indexedServer = server;
            index = built;
            cachedResults = null;
            return toPublicSummary(index.summary());
        }
    }

    public static IndexSummary summary(MinecraftServer server) {
        return toPublicSummary(ensureIndex(server).summary());
    }

    private static IndexSummary toPublicSummary(ProceduralValuationIndex.Summary summary) {
        return new IndexSummary(
                summary.recipeCount(),
                summary.skippedRecipeCount(),
                summary.outputItemCount(),
                summary.ingredientLinkCount(),
                summary.entityLootTableCount(),
                summary.dropSourceLinkCount(),
                summary.blockLootTableCount(),
                summary.blockDropSourceLinkCount(),
                summary.containerLootTableCount(),
                summary.containerLootSourceLinkCount(),
                summary.fishingLootTableCount(),
                summary.fishingLootSourceLinkCount(),
                summary.tradeProfessionTableCount(),
                summary.tradeListingCount(),
                summary.tradeOfferCount(),
                summary.advancementCount(),
                summary.consideredAdvancementCount(),
                summary.advancementTreeCount(),
                summary.advancementReferenceCount(),
                summary.advancementItemReferenceCount(),
                summary.advancementEntityReferenceCount(),
                summary.advancementDimensionReferenceCount()
        );
    }

    static GenerationDataSnapshot generationData(MinecraftServer server) {
        synchronized (INDEX_LOCK) {
            if (generationData == null || indexedServer != null && indexedServer != server) {
                clear();
                generationData = GenerationDataSnapshot.capture(server);
            }
            generationData.requireCurrent(server);
            return generationData;
        }
    }

    private static ProceduralValuationIndex ensureIndex(MinecraftServer server) {
        ProceduralValuationIndex current = index;
        if (current != null && indexedServer == server) {
            generationData.requireCurrent(server);
            BalancePerformance.increment("valuation_index_cache_hits");
            return current;
        }
        synchronized (INDEX_LOCK) {
            if (index == null || indexedServer != server) {
                BalancePerformance.increment("valuation_index_builds");
                ProceduralValuationIndex built = ProceduralValuationIndex.build(server, generationData(server));
                indexedServer = server;
                index = built;
                cachedResults = null;
            }
            return index;
        }
    }

    private static EvaluationNode evaluateNode(
            Item item,
            EvaluationContext context,
            Set<Item> visiting,
            int depth
    ) {
        if (!ValuationGenerationInputs.itemAllowed(item)) {
            Intrinsic base = intrinsic(item, context);
            return new EvaluationNode(base.value(), base.value(), Optional.empty(), base.progressionBand(),
                    1.0, 0, false, false, 0, 0, List.of("Acquisition excluded by factual generation override"), Set.of(item));
        }
        if (context.solving && !visiting.isEmpty()) {
            EvaluationNode input = context.previous.get(item);
            if (input != null && !input.dependencies().contains(context.activeRoot)) return input;
            Intrinsic base = intrinsic(item, context);
            return new EvaluationNode(base.value(), base.value(), Optional.empty(), base.progressionBand(),
                    Math.min(0.42, base.confidence()), 0, false, input != null, 0.0, 0,
                    List.of("Unresolved graph input; cannot establish acquisition evidence"), Set.of(item));
        }
        EvaluationNode memoized = context.memo().get(item);
        if (memoized != null) {
            return memoized;
        }

        Intrinsic intrinsic = intrinsic(item, context);

        if ((!context.solving && depth >= ProceduralValuationSettings.MAX_RECIPE_DEPTH) || visiting.contains(item)) {
            return new EvaluationNode(
                    intrinsic.value(),
                    intrinsic.value(),
                    Optional.empty(),
                    intrinsic.progressionBand(),
                    Math.min(0.42, intrinsic.confidence()),
                    Math.max(0, depth),
                    false,
                    true,
                    0.0,
                    0,
                    List.of("Recipe recursion stopped at safety depth/cycle; context-sensitive fallback not memoized")
            );
        }

        visiting.add(item);
        DirectSource directSource = directSource(
                item,
                context,
                visiting,
                depth,
                intrinsic
        );
        Double correctedValue = ValuationGenerationInputs.economicValue(item);
        if (ValuationGenerationInputs.declaresSource(item) || correctedValue != null) {
            double value = correctedValue == null ? directSource.value() : correctedValue;
            directSource = new DirectSource(value,
                    directSource.knownAcquisition() || ValuationGenerationInputs.declaresSource(item),
                    directSource.progressionBand(), ValuationGenerationInputs.confidence(item),
                    ValuationGenerationInputs.progression(item, directSource.inferredProgressionScore()),
                    directSource.progressionEvidenceCount(), false,
                    List.of("Factual source/economic override applied before recipe graph relaxation"), Set.of(item));
        }
        List<RecipeCandidate> recipeCandidates = new ArrayList<>();
        boolean contextSensitive = directSource.contextSensitive();

        for (ProceduralValuationIndex.RecipeModel recipe : context.index().recipesProducing(item)) {
            if (context.index().isReversibleTransform(recipe)) continue;
            RecipeAttempt attempt = evaluateRecipe(
                    recipe,
                    context,
                    visiting,
                    depth
            );
            contextSensitive |= attempt.contextSensitive();
            if (attempt.candidate() != null) {
                recipeCandidates.add(attempt.candidate());
            }
        }

        visiting.remove(item);

        recipeCandidates.sort(
                Comparator.comparingLong(RecipeCandidate::value)
                        .thenComparing(candidate -> candidate.recipe().id().toString())
        );

        RecipeCandidate cheapestReliableRecipe = recipeCandidates.stream()
                .filter(RecipeCandidate::fullyModeledIngredients)
                .findFirst()
                .orElse(null);
        RecipeCandidate cheapestRecipe = cheapestReliableRecipe != null
                ? cheapestReliableRecipe
                : (recipeCandidates.isEmpty() ? null : recipeCandidates.getFirst());

        double acquisition;
        boolean knownAcquisition;
        Optional<ProceduralValuationResult.RecipeChoice> recipeChoice = Optional.empty();
        ProceduralValuationResult.ProgressionBand progression = directSource.progressionBand();
        double confidence = directSource.confidence();
        double inferredProgressionScore = directSource.inferredProgressionScore();
        int progressionEvidenceCount = directSource.progressionEvidenceCount();
        Set<Item> selectedDependencies = directSource.dependencies();
        List<String> factors = new ArrayList<>();
        factors.addAll(intrinsic.factors());
        factors.addAll(directSource.factors());

        if (cheapestRecipe != null) {
            boolean directIsKnown = directSource.knownAcquisition();
            boolean recipeIsKnown = cheapestRecipe.fullyModeledIngredients();
            if ((!directIsKnown && (recipeIsKnown || cheapestReliableRecipe == null))
                    || (recipeIsKnown && cheapestRecipe.value() <= directSource.value())) {
                acquisition = cheapestRecipe.value();
                progression = cheapestRecipe.progressionBand();
                knownAcquisition = recipeIsKnown;
                recipeChoice = Optional.of(cheapestRecipe.toChoice());
                selectedDependencies = cheapestRecipe.dependencies();
                confidence = cheapestRecipe.confidence();
                inferredProgressionScore = cheapestRecipe.inferredProgressionScore();
                progressionEvidenceCount = cheapestRecipe.progressionEvidenceCount();
                factors.add(
                        "Acquisition path: cheapest loaded recipe "
                                + cheapestRecipe.recipe().id()
                                + " -> "
                                + formatLong(cheapestRecipe.value())
                                + " Essence/output"
                );
                factors.add(
                        "Recipe complexity: "
                                + cheapestRecipe.ingredientSlots()
                                + " slot(s), "
                                + cheapestRecipe.uniqueIngredients()
                                + " unique, "
                                + cheapestRecipe.easyIngredients()
                                + " easy, "
                                + cheapestRecipe.rareIngredients()
                                + " rare, "
                                + cheapestRecipe.modSpecificIngredients()
                                + " mod-specific, depth "
                                + cheapestRecipe.depth()
                );
                if (!cheapestRecipe.reversible()) {
                    factors.add("Recipe valuation: ingredient values already carry rarity/progression; only process + small structural/depth premium applied");
                }
                if (cheapestRecipe.recipe().id().getNamespace().equals("essence_ascendance")
                        && cheapestRecipe.recipe().id().getPath().startsWith("valuation/interaction/")) {
                    confidence = Math.min(confidence, 0.62);
                    factors.add("Runtime interaction edge: conversion confirmed by registered behavior/loaded recipe; "
                            + "reusable tool/access/time effort remains an estimate, not a hard-coded item price");
                }
                if (!cheapestRecipe.fullyModeledIngredients()) {
                    factors.add("Recipe reliability: one or more ingredient acquisition paths are unresolved; recipe kept as diagnostic fallback only");
                }
                if (cheapestRecipe.reversible()) {
                    factors.add("Recipe classification: reversible storage/transform; processing premium suppressed");
                }
            } else {
                acquisition = directSource.value();
                knownAcquisition = directSource.knownAcquisition();
                factors.add(
                        "Acquisition path: recognized direct source is cheaper than loaded recipes -> "
                                + formatLong(clampValue(directSource.value()))
                );
            }
        } else {
            acquisition = directSource.value();
            knownAcquisition = directSource.knownAcquisition();
            factors.add(
                    directSource.knownAcquisition()
                            ? "Acquisition path: recognized direct source"
                            : "Acquisition path: no fully modeled recipe/source metadata found; conservative fallback used"
            );
        }

        ProceduralProgressionIndex.ProgressionEvidence itemProgression =
                context.index().progressionForItem(item);
        if (itemProgression.present()) {
            double pathMultiplier = progressionMultiplier(inferredProgressionScore);
            double itemMultiplier = itemProgression.multiplier();
            if (itemProgression.score() > inferredProgressionScore + 0.000001) {
                acquisition *= itemMultiplier / Math.max(1.0, pathMultiplier);
                inferredProgressionScore = itemProgression.score();
            }
            progressionEvidenceCount += itemProgression.evidenceCount();
            // A progression gate says when, not how reliably we modeled acquisition.
            // It must not turn a fallback price into HIGH economic confidence.
            factors.add(
                    "Advancement acquisition progression: "
                            + formatPercent(itemProgression.score())
                            + " inferred from "
                            + itemProgression.evidenceCount()
                            + " semantic reference(s); effective x"
                            + format(progressionMultiplier(inferredProgressionScore))
            );
            for (String evidence : itemProgression.examples()) {
                factors.add("Advancement evidence: " + evidence);
            }
        }

        inferredProgressionScore = ValuationGenerationInputs.progression(item, inferredProgressionScore);
        Renewability renewability = renewability(item, context.index());
        if (renewability.multiplier() < 0.999999) {
            double floor = Math.max(1.0, intrinsic.floorValue());
            acquisition = floor + Math.max(0.0, acquisition - floor) * renewability.multiplier();
            confidence = Math.min(0.96, confidence + renewability.confidenceBonus());
            factors.add(
                    "Renewability: "
                            + renewability.label()
                            + " -> retain "
                            + formatPercent(renewability.multiplier())
                            + " of acquisition premium above intrinsic floor"
            );
        }

        long acquisitionFloor = recipeChoice
                .filter(ProceduralValuationResult.RecipeChoice::reversibleTransform)
                .isPresent()
                ? 1L
                : intrinsic.floorValue();
        long acquisitionValue = correctedValue == null ? clampValue(Math.max(acquisitionFloor, acquisition)) : clampValue(correctedValue);
        int resolvedDepth = recipeChoice
                .map(ProceduralValuationResult.RecipeChoice::depth)
                .orElse(0);
        boolean resolvedContextSensitive = !context.solving && contextSensitive;
        Set<Item> resultDependencies = new LinkedHashSet<>(selectedDependencies);
        resultDependencies.add(item);
        EvaluationNode result = new EvaluationNode(
                intrinsic.value(),
                acquisitionValue,
                recipeChoice,
                progression,
                ProceduralValuationConfidence.bound(confidence, knownAcquisition),
                resolvedDepth,
                knownAcquisition,
                resolvedContextSensitive,
                inferredProgressionScore,
                progressionEvidenceCount,
                List.copyOf(factors),
                Set.copyOf(resultDependencies)
        );

        // Only cache results proven independent of the current recursion stack.
        // This removes bulk-export order dependence from reversible/cyclic
        // recipe families without assigning arbitrary values to the cycle.
        if (!context.solving && !result.contextSensitive()) {
            context.memo().put(item, result);
        }
        return result;
    }

    private static RecipeAttempt evaluateRecipe(
            ProceduralValuationIndex.RecipeModel recipe,
            EvaluationContext context,
            Set<Item> visiting,
            int parentDepth
    ) {
        boolean reversible = context.index().isReversibleTransform(recipe);

        double ingredientTotal = 0.0;
        LinkedHashSet<Item> uniqueChosen = new LinkedHashSet<>();
        Set<Item> dependencies = new LinkedHashSet<>();
        int easy = 0;
        int rare = 0;
        int modSpecific = 0;
        int maxChainDepth = 0;
        double confidence = recipe.production() == null ? .91 : recipe.production().confidence();
        boolean allIngredientsKnown = recipe.production() == null || recipe.production().acquisitionComplete();
        ProceduralValuationResult.ProgressionBand progression =
                ProceduralValuationResult.ProgressionBand.OVERWORLD;

        for (ProceduralValuationIndex.IngredientChoice ingredient : recipe.ingredients()) {
            IngredientSelection selection = chooseIngredientCandidate(
                    ingredient.alternatives(),
                    context,
                    visiting,
                    parentDepth + 1
            );
            if (selection.item() == null || selection.node() == null) {
                return new RecipeAttempt(null, selection.contextSensitive());
            }

            Item chosen = selection.item();
            EvaluationNode child = selection.node();
            if (child.contextSensitive()) {
                return new RecipeAttempt(null, true);
            }

            /*
             * Recipe inputs must use the same conservation-normalized material
             * value that the top-level item uses. Otherwise a reversible form
             * (for example an iron block) can be evaluated here from a more
             * expensive local source even though nuggets/ingots provide a
             * cheaper equivalent acquisition path. That silently inflates every
             * assembled recipe that consumes the form. This is generic for any
             * reversible modded material family discovered by the recipe graph.
             */
            child = normalizeConservationGroupCached(chosen, child, context);

            // A reversible storage transform must anchor to a real external
            // acquisition/processing path. A circular intrinsic fallback is
            // never allowed to establish a conservation ratio.
            if (reversible && !child.knownAcquisition()) {
                return new RecipeAttempt(null, false);
            }
            allIngredientsKnown &= child.knownAcquisition();

            dependencies.addAll(child.dependencies());
            dependencies.add(chosen);
            if (ingredient.consumed()) ingredientTotal += child.acquisitionValue() * ingredient.count();
            uniqueChosen.add(chosen);
            if (child.acquisitionValue() <= ProceduralValuationSettings.EASY_INGREDIENT_THRESHOLD) {
                easy++;
            }
            if (child.acquisitionValue() >= ProceduralValuationSettings.RARE_INGREDIENT_THRESHOLD) {
                rare++;
            }
            ResourceLocation chosenId = BuiltInRegistries.ITEM.getKey(chosen);
            if (chosenId != null && !"minecraft".equals(chosenId.getNamespace())) {
                modSpecific++;
            }
            progression = ProceduralValuationResult.ProgressionBand.max(
                    progression,
                    child.progressionBand()
            );
            confidence = Math.min(confidence, child.confidence());
            maxChainDepth = Math.max(
                    maxChainDepth,
                    1 + child.recipeDepth()
            );
        }

        double processMultiplier = reversible
                ? 1.0
                : processMultiplier(recipe.type());

        double complexityMultiplier = 1.0;
        if (!reversible) {
            complexityMultiplier += Math.min(
                    ProceduralValuationSettings.UNIQUE_INGREDIENT_CAP,
                    Math.max(0, uniqueChosen.size() - 1)
                            * ProceduralValuationSettings.UNIQUE_INGREDIENT_STEP
            );
            // Ingredient rarity and mod namespace are diagnostic only here.
            // Their acquisition cost is already present in ingredientTotal, so
            // multiplying them again would double-count scarcity/progression.
            complexityMultiplier += Math.min(
                    ProceduralValuationSettings.RECIPE_DEPTH_CAP,
                    maxChainDepth * ProceduralValuationSettings.RECIPE_DEPTH_STEP
            );
        }

        double perOutput = ingredientTotal
                * processMultiplier
                * complexityMultiplier
                / recipe.expectedOutputCount();

        ProceduralProgressionIndex.ProgressionEvidence recipeProgression =
                context.index().progressionForRecipe(recipe.id());
        if (recipeProgression.present()) {
            perOutput *= recipeProgression.multiplier();
            // Do not substitute progression certainty for ingredient acquisition certainty.
        }

        return new RecipeAttempt(new RecipeCandidate(
                recipe,
                clampValue(perOutput),
                recipe.ingredients().size(),
                uniqueChosen.size(),
                easy,
                rare,
                modSpecific,
                Math.max(1, maxChainDepth),
                reversible,
                allIngredientsKnown,
                progression,
                ProceduralValuationConfidence.bound(Math.min(0.91, confidence + 0.10), allIngredientsKnown),
                recipeProgression.score(),
                recipeProgression.evidenceCount(),
                Set.copyOf(dependencies)
        ), false);
    }

    private static IngredientSelection chooseIngredientCandidate(
            List<Item> alternatives,
            EvaluationContext context,
            Set<Item> visiting,
            int depth
    ) {
        if (alternatives.isEmpty()) {
            return IngredientSelection.NONE;
        }

        // Ingredient lists are immutable in the index. Preserve their original
        // order for composition's separate capped traversal; canonicalize only
        // the solver's alternatives, once for this generation rather than per pass.
        List<Item> shortlist = context.ingredientOrder.computeIfAbsent(alternatives, values -> values.stream().distinct()
                .sorted(Comparator.comparing(candidate -> BuiltInRegistries.ITEM.getKey(candidate).toString())).toList());

        Item best = null;
        EvaluationNode bestNode = null;
        long bestValue = Long.MAX_VALUE;
        boolean blockedByCycle = false;

        for (Item candidate : shortlist) {
            if (visiting.contains(candidate)) {
                blockedByCycle = true;
                continue;
            }
            EvaluationNode node = evaluateNode(
                    candidate,
                    context,
                    visiting,
                    depth
            );
            if (!node.contextSensitive()) {
                node = normalizeConservationGroupCached(candidate, node, context);
            }
            if (node.contextSensitive()) {
                blockedByCycle = true;
                continue;
            }
            if (bestNode == null || (node.knownAcquisition() && !bestNode.knownAcquisition())
                    || (node.knownAcquisition() == bestNode.knownAcquisition()
                    && node.acquisitionValue() < bestValue)) {
                bestValue = node.acquisitionValue();
                best = candidate;
                bestNode = node;
            }
        }

        if (best != null) {
            return new IngredientSelection(best, bestNode, false);
        }
        return new IngredientSelection(null, null, blockedByCycle);
    }

    private static DirectSource directSource(
            Item item,
            EvaluationContext context,
            Set<Item> visiting,
            int depth,
            Intrinsic intrinsic
    ) {
        ProceduralValuationIndex index = context.index();
        double value = intrinsic.value();
        ItemStack stack = new ItemStack(item);
        boolean oreSource = isRecognizedOreSource(item, stack);
        boolean known = oreSource;
        boolean conditionalFallbackSelected = false;
        boolean contextSensitive = false;
        Set<Item> dependencies = Set.of();
        double confidence = intrinsic.confidence();
        ProceduralValuationResult.ProgressionBand progression = intrinsic.progressionBand();
        ProceduralProgressionIndex.ProgressionEvidence selectedProgression =
                ProceduralProgressionIndex.ProgressionEvidence.NONE;
        List<String> factors = new ArrayList<>();

        if (oreSource && stack.is(ProceduralValuationTags.ORE_RATE_DENSE)) {
            value *= ProceduralValuationSettings.ORE_RATE_DENSE_MULTIPLIER;
            confidence += 0.05;
            factors.add("Ore yield rate: dense -> x" + format(ProceduralValuationSettings.ORE_RATE_DENSE_MULTIPLIER));
        } else if (oreSource && stack.is(ProceduralValuationTags.ORE_RATE_SPARSE)) {
            value *= ProceduralValuationSettings.ORE_RATE_SPARSE_MULTIPLIER;
            confidence += 0.05;
            factors.add("Ore yield rate: sparse -> x" + format(ProceduralValuationSettings.ORE_RATE_SPARSE_MULTIPLIER));
        } else if (oreSource && stack.is(ProceduralValuationTags.ORE_RATE_SINGULAR)) {
            confidence += 0.04;
            factors.add("Ore yield rate: singular");
        }

        if (oreSource && item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block.defaultBlockState().is(ProceduralValuationTags.NEEDS_DIAMOND_TOOL)) {
                value *= ProceduralValuationSettings.NEEDS_DIAMOND_TOOL_MULTIPLIER;
                confidence += 0.06;
                factors.add("Harvest gate: diamond-tier tool -> x" + format(ProceduralValuationSettings.NEEDS_DIAMOND_TOOL_MULTIPLIER));
            } else if (block.defaultBlockState().is(ProceduralValuationTags.NEEDS_IRON_TOOL)) {
                value *= ProceduralValuationSettings.NEEDS_IRON_TOOL_MULTIPLIER;
                confidence += 0.06;
                factors.add("Harvest gate: iron-tier tool -> x" + format(ProceduralValuationSettings.NEEDS_IRON_TOOL_MULTIPLIER));
            } else if (block.defaultBlockState().is(ProceduralValuationTags.NEEDS_STONE_TOOL)) {
                value *= ProceduralValuationSettings.NEEDS_STONE_TOOL_MULTIPLIER;
                confidence += 0.05;
                factors.add("Harvest gate: stone-tier tool -> x" + format(ProceduralValuationSettings.NEEDS_STONE_TOOL_MULTIPLIER));
            }
        }

        List<BlockDropPath> blockPaths = index.blockDropSources(item).stream()
                .map(source -> {
                    Block sourceBlock = BuiltInRegistries.BLOCK.getOptional(source.blockId()).orElse(null);
                    ProceduralProgressionIndex.ProgressionEvidence sourceProgression =
                            sourceBlock == null
                                    ? ProceduralProgressionIndex.ProgressionEvidence.NONE
                                    : index.progressionForBlock(sourceBlock);
                    return evaluateBlockDropPath(
                            intrinsic.value(),
                            intrinsic.progressionBand(),
                            source,
                            sourceProgression,
                            context, visiting, depth
                    );
                })
                .toList();
        BlockDropPath selectedBlock = selectBlockPath(blockPaths);
        if (selectedBlock != null) {
            boolean reliable = selectedBlock.reliable();
            boolean shouldUse = reliable
                    ? (!known || selectedBlock.value() < value)
                    : (!known && (!conditionalFallbackSelected || selectedBlock.value() < value));
            if (shouldUse) {
                value = selectedBlock.value();
                dependencies = selectedBlock.dependencies();
                contextSensitive |= !selectedBlock.prerequisiteKnown();
                known = reliable;
                conditionalFallbackSelected = !reliable;
                ProceduralValuationIndex.BlockDropSource source = selectedBlock.source();
                selectedProgression = selectedBlock.progressionEvidence();
                confidence = intrinsic.confidence() + (reliable ? 0.18 : 0.04);
                progression = ProceduralValuationResult.ProgressionBand.max(
                        progression,
                        source.progressionBand()
                );
                factors.add(
                        "Block drop source: "
                                + source.blockId()
                                + " | chance "
                                + formatPercent(source.estimatedChance())
                                + ", count "
                                + format(source.expectedCount())
                                + (source.complexConditionCount() > 0
                                ? " | " + source.complexConditionCount() + " unresolved acquisition condition(s)"
                                : "")
                );
                if (!source.signals().isEmpty()) {
                    factors.add("Block source signals: " + String.join(", ", source.signals()));
                }
                List<String> naturalEvidence = index.naturalBlockEvidence(source.blockId());
                if (!naturalEvidence.isEmpty()) factors.add("Natural placement evidence: "
                        + String.join(", ", naturalEvidence) + "; source loot also required, density not estimated");
                factors.add(
                        "Block acquisition -> source x"
                                + format(selectedBlock.sourceMultiplier())
                                + ", rarity x"
                                + format(selectedBlock.rarityMultiplier())
                                + ", quantity x"
                                + format(selectedBlock.quantityMultiplier())
                                + ", condition x"
                                + format(selectedBlock.conditionMultiplier())
                );
                if (source.reusableTool() != null) {
                    factors.add("Reusable-tool wear: " + format(selectedBlock.reusableWear())
                            + " Essence/harvest; full tool is not consumed; prerequisite acquisition "
                            + (selectedBlock.prerequisiteKnown() ? "modeled" : "unresolved"));
                }
                if (!reliable) {
                    factors.add("Conditional block source is diagnostic fallback only; it cannot undercut a fully modeled recipe/source");
                }
            }
        }

        for (var biological : index.biologicalSources(item)) {
            var event = biological.event();
            boolean requirementsKnown = true;
            boolean requirementsSensitive = false;
            double prerequisiteCost = 0;
            Set<Item> requiredItems = new LinkedHashSet<>();
            double sourceConfidence = event.confidence();
            for (var requirement : event.requirements()) {
                EvaluationNode prerequisite = evaluateNode(requirement.item(), context, visiting, depth + 1);
                requirementsKnown &= prerequisite.knownAcquisition();
                requirementsSensitive |= prerequisite.contextSensitive();
                sourceConfidence = Math.min(sourceConfidence, prerequisite.confidence());
                requiredItems.add(requirement.item());
                requiredItems.addAll(prerequisite.dependencies());
                if (requirement.consumed()) prerequisiteCost += prerequisite.acquisitionValue() * requirement.count();
                else if (requirement.durabilityWear() > 0) {
                    int durability = new ItemStack(requirement.item()).getMaxDamage();
                    if (durability <= 0) requirementsKnown = false;
                    else prerequisiteCost += prerequisite.acquisitionValue() * requirement.count()
                            * requirement.durabilityWear() / durability;
                }
            }
            // Nonlethal production uses the existing material opportunity scale
            // and producer access. Combat stats do not price an animal's growth.
            contextSensitive |= requirementsSensitive;
            var producerProgression = index.progressionForEntity(event.producerId());
            double candidate = biologicalAcquisitionValue(intrinsic.value(), biological.spawnMultiplier(),
                    event.count(), prerequisiteCost) * producerProgression.multiplier();
            if (requirementsKnown && !requirementsSensitive && (!known || candidate < value)) {
                value = candidate;
                known = true;
                conditionalFallbackSelected = false;
                dependencies = Set.copyOf(requiredItems);
                confidence = sourceConfidence;
                selectedProgression = producerProgression;
                factors.add("Biological production source: " + event.id() + " | " + event.count()
                        + " per completed event; producer " + event.producerId());
                factors.add(event.reason());
                if (!biological.spawnSignals().isEmpty())
                    factors.add("Producer access: " + String.join("; ", biological.spawnSignals()));
                if (!event.requirements().isEmpty()) factors.add("Consumed containers and reusable-tool wear charged; all prerequisites must be obtainable");
            }
        }

        List<DropPath> dropPaths = index.dropSources(item).stream()
                .map(source -> evaluateDropPath(
                        Math.max(intrinsic.value(), ProceduralValuationSettings.MOB_DROP_BASE),
                        source,
                        index.progressionForEntity(source.entityId())
                ))
                .toList();
        DropPath selectedDrop = selectDropPath(dropPaths);
        if (selectedDrop != null) {
            boolean reliable = selectedDrop.source().complexConditionCount() == 0;
            boolean shouldUse = reliable
                    ? (!known || selectedDrop.value() < value)
                    : (!known && (!conditionalFallbackSelected || selectedDrop.value() < value));
            if (shouldUse) {
                value = selectedDrop.value();
                dependencies = Set.of();
                known = reliable;
                conditionalFallbackSelected = !reliable;
                ProceduralValuationIndex.DropSource source = selectedDrop.source();
                selectedProgression = selectedDrop.progressionEvidence();
                confidence = intrinsic.confidence() + (reliable ? 0.17 : 0.04);
                progression = ProceduralValuationResult.ProgressionBand.max(
                        progression,
                        selectedDrop.progressionBand()
                );
                factors.add(
                        "Entity drop source: "
                                + source.entityId()
                                + " | HP "
                                + format(source.maxHealth())
                                + ", attack "
                                + format(source.attackDamage())
                                + ", armor "
                                + format(source.armor())
                );
                factors.add(
                        "Drop estimate: chance "
                                + formatPercent(source.estimatedChance())
                                + ", count "
                                + format(source.expectedCount())
                                + (source.complexConditionCount() > 0
                                ? " | " + source.complexConditionCount() + " unresolved acquisition condition(s)"
                                : "")
                );
                if (!source.signals().isEmpty()) {
                    factors.add("Mob source signals: " + String.join(", ", source.signals()));
                }
                factors.add(
                        "Mob/drop acquisition -> difficulty x"
                                + format(selectedDrop.difficultyMultiplier())
                                + ", spawn/access x"
                                + format(selectedDrop.spawnMultiplier())
                                + ", special x"
                                + format(selectedDrop.specialMultiplier())
                                + ", rarity x"
                                + format(selectedDrop.rarityMultiplier())
                                + ", quantity x"
                                + format(selectedDrop.quantityMultiplier())
                                + ", condition x"
                                + format(selectedDrop.conditionMultiplier())
                );
                if (!reliable) {
                    factors.add("Conditional entity source is diagnostic fallback only; it cannot undercut a fully modeled recipe/source");
                }
            }
        }

        List<ContainerLootPath> containerPaths = index.containerLootSources(item).stream()
                .map(source -> evaluateContainerLootPath(
                        Math.max(intrinsic.value(), ProceduralValuationSettings.CONTAINER_LOOT_BASE),
                        intrinsic.progressionBand(),
                        source,
                        index.progressionForLootTable(source.lootTableId())
                ))
                .toList();
        ContainerLootPath selectedContainer = selectContainerPath(containerPaths);
        if (selectedContainer != null) {
            boolean reliable = selectedContainer.source().complexConditionCount() == 0;
            boolean shouldUse = reliable
                    ? (!known || selectedContainer.value() < value)
                    : (!known && (!conditionalFallbackSelected || selectedContainer.value() < value));
            if (shouldUse) {
                value = selectedContainer.value();
                dependencies = Set.of();
                known = reliable;
                conditionalFallbackSelected = !reliable;
                ProceduralValuationIndex.ContainerLootSource source = selectedContainer.source();
                selectedProgression = selectedContainer.progressionEvidence();
                confidence = intrinsic.confidence() + (reliable ? 0.16 : 0.04);
                progression = ProceduralValuationResult.ProgressionBand.max(
                        progression,
                        source.progressionBand()
                );
                factors.add(
                        ("FIXED_TREASURE".equals(source.tierLabel())
                                ? "Fixed structure source: "
                                : source.archaeology() ? "Archaeology loot source: " : "Container loot source: ")
                                + source.lootTableId()
                                + " | tier "
                                + source.tierLabel()
                                + " | chance "
                                + formatPercent(source.estimatedChance())
                                + ", expected count "
                                + format(source.expectedCount())
                                + (source.complexConditionCount() > 0
                                ? " | " + source.complexConditionCount() + " unresolved acquisition condition(s)"
                                : "")
                );
                if (source.structureId() != null) {
                    factors.add(
                            "Structure occurrence: "
                                    + source.structureId()
                                    + " | placement frequency "
                                    + (source.structureFrequencyKnown() ? "derived" : "unknown")
                                    + " | template loot refs "
                                    + source.templateReferenceCount()
                    );
                }
                if (!source.signals().isEmpty()) {
                    factors.add("Container context signals: " + String.join(", ", source.signals()));
                }
                factors.add(
                        "Container acquisition -> context x"
                                + format(selectedContainer.contextMultiplier())
                                + ", loot-frequency x"
                                + format(selectedContainer.rarityMultiplier())
                                + ", quantity x"
                                + format(selectedContainer.quantityMultiplier())
                                + ", condition x"
                                + format(selectedContainer.conditionMultiplier())
                );
                if (!reliable) {
                    factors.add("Conditional container source is diagnostic fallback only; it cannot undercut a fully modeled recipe/source");
                }
            }
        }

        List<FishingLootPath> fishingPaths = index.fishingLootSources(item).stream()
                .map(source -> evaluateFishingLootPath(
                        Math.max(intrinsic.value(), ProceduralValuationSettings.FISHING_LOOT_BASE),
                        intrinsic.progressionBand(),
                        source,
                        index.progressionForLootTable(source.lootTableId())
                ))
                .toList();
        FishingLootPath selectedFishing = selectFishingPath(fishingPaths);
        if (selectedFishing != null) {
            boolean reliable = selectedFishing.source().complexConditionCount() == 0;
            boolean shouldUse = reliable
                    ? (!known || selectedFishing.value() < value)
                    : (!known && (!conditionalFallbackSelected || selectedFishing.value() < value));
            if (shouldUse) {
                value = selectedFishing.value();
                dependencies = Set.of();
                known = reliable;
                conditionalFallbackSelected = !reliable;
                ProceduralValuationIndex.FishingLootSource source = selectedFishing.source();
                selectedProgression = selectedFishing.progressionEvidence();
                confidence = intrinsic.confidence() + (reliable ? 0.19 : 0.04);
                progression = ProceduralValuationResult.ProgressionBand.max(
                        progression,
                        source.progressionBand()
                );
                factors.add(
                        "Fishing loot source: "
                                + source.lootTableId()
                                + " | chance "
                                + formatPercent(source.estimatedChance())
                                + ", expected count "
                                + format(source.expectedCount())
                                + (source.complexConditionCount() > 0
                                ? " | " + source.complexConditionCount() + " unresolved acquisition condition(s)"
                                : "")
                );
                if (!source.signals().isEmpty()) {
                    factors.add("Fishing context signals: " + String.join(", ", source.signals()));
                }
                factors.add(
                        "Fishing acquisition -> context x"
                                + format(selectedFishing.contextMultiplier())
                                + ", loot-frequency x"
                                + format(selectedFishing.rarityMultiplier())
                                + ", quantity x"
                                + format(selectedFishing.quantityMultiplier())
                                + ", condition x"
                                + format(selectedFishing.conditionMultiplier())
                );
                if (!reliable) {
                    factors.add("Conditional fishing source is diagnostic fallback only; it cannot undercut a fully modeled recipe/source");
                }
            }
        }

        List<TradePath> tradePaths = new ArrayList<>();
        for (ProceduralTradeIndex.TradeSource source : index.tradeSources(item)) {
            TradeAttempt attempt = evaluateTradePath(
                    source,
                    context,
                    visiting,
                    depth + 1
            );
            contextSensitive |= attempt.contextSensitive();
            if (attempt.path() != null) {
                tradePaths.add(attempt.path());
            }
        }
        TradePath selectedTrade = selectTradePath(tradePaths);
        if (selectedTrade != null) {
            boolean reliable = selectedTrade.fullyModeledCosts();
            boolean shouldUse = reliable
                    ? (!known || selectedTrade.value() < value)
                    : (!known && (!conditionalFallbackSelected || selectedTrade.value() < value));
            if (shouldUse) {
                value = selectedTrade.value();
                dependencies = selectedTrade.dependencies();
                known = reliable;
                conditionalFallbackSelected = !reliable;
                confidence = Math.max(intrinsic.confidence(), selectedTrade.confidence());
                progression = ProceduralValuationResult.ProgressionBand.max(
                        progression,
                        selectedTrade.progressionBand()
                );
                // A trade is its own acquisition path. Do not accidentally carry
                // advancement evidence from a previously considered block/mob/container
                // source into the selected trade path. Trade-cost progression is already
                // propagated through the recursively valued cost items.
                selectedProgression = ProceduralProgressionIndex.ProgressionEvidence.NONE;

                ProceduralTradeIndex.TradeSource source = selectedTrade.source();
                ResourceLocation costAId = source.costA().isEmpty()
                        ? null
                        : BuiltInRegistries.ITEM.getKey(source.costA().getItem());
                ResourceLocation costBId = source.costB().isEmpty()
                        ? null
                        : BuiltInRegistries.ITEM.getKey(source.costB().getItem());

                factors.add(
                        "Trade source: "
                                + source.traderId()
                                + " level "
                                + source.level()
                                + (source.wandering() ? " (wandering)" : "")
                                + " | cost "
                                + (costAId == null
                                ? "none"
                                : source.costA().getCount() + "x " + costAId)
                                + (costBId == null
                                ? ""
                                : " + " + source.costB().getCount() + "x " + costBId)
                                + " -> "
                                + source.outputCount()
                                + " output"
                );
                factors.add(
                        "Trade acquisition -> level x"
                                + format(selectedTrade.levelMultiplier())
                                + ", listing x"
                                + format(selectedTrade.listingMultiplier())
                                + ", stock x"
                                + format(selectedTrade.stockMultiplier())
                                + ", trader x"
                                + format(selectedTrade.traderMultiplier())
                );
                if (!reliable) {
                    factors.add("Trade source is diagnostic fallback only because one or more trade-cost acquisition paths are unresolved");
                }
            }
        }

        if (selectedProgression.present()) {
            factors.add(
                    "Source advancement progression: "
                            + formatPercent(selectedProgression.score())
                            + " -> x"
                            + format(selectedProgression.multiplier())
            );
            for (String evidence : selectedProgression.examples()) {
                factors.add("Source advancement evidence: " + evidence);
            }
        }

        return new DirectSource(
                value,
                known,
                progression,
                Math.min(0.90, confidence),
                selectedProgression.score(),
                selectedProgression.evidenceCount(),
                contextSensitive,
                List.copyOf(factors),
                dependencies
        );
    }

    private static TradeAttempt evaluateTradePath(
            ProceduralTradeIndex.TradeSource source,
            EvaluationContext context,
            Set<Item> visiting,
            int depth
    ) {
        double costTotal = 0.0;
        boolean fullyModeled = true;
        boolean contextSensitive = false;
        double confidence = 0.84;
        Set<Item> dependencies = new LinkedHashSet<>();
        ProceduralValuationResult.ProgressionBand progression =
                ProceduralValuationResult.ProgressionBand.OVERWORLD;

        for (ItemStack cost : List.of(source.costA(), source.costB())) {
            if (cost == null || cost.isEmpty()) {
                continue;
            }

            Item costItem = cost.getItem();
            if (visiting.contains(costItem)) {
                contextSensitive = true;
                return new TradeAttempt(null, true);
            }

            EvaluationNode child = evaluateNode(
                    costItem,
                    context,
                    visiting,
                    depth
            );
            if (child.contextSensitive()) {
                contextSensitive = true;
                return new TradeAttempt(null, true);
            }

            dependencies.addAll(child.dependencies());
            dependencies.add(costItem);
            costTotal += child.acquisitionValue() * Math.max(1, cost.getCount());
            fullyModeled &= child.knownAcquisition();
            confidence = Math.min(confidence, child.confidence());
            progression = ProceduralValuationResult.ProgressionBand.max(
                    progression,
                    child.progressionBand()
            );
        }

        if (costTotal <= 0.0) {
            return new TradeAttempt(null, contextSensitive);
        }

        double levelMultiplier = Math.min(
                ProceduralValuationSettings.TRADE_LEVEL_MAX_MULTIPLIER,
                1.0 + Math.max(0, source.level() - 1) * ProceduralValuationSettings.TRADE_LEVEL_STEP
        );
        double listingRatio = Math.max(
                1.0,
                source.listingPoolSize() / 2.0
        );
        double listingMultiplier = Math.min(
                ProceduralValuationSettings.TRADE_LISTING_RARITY_MAX_MULTIPLIER,
                Math.pow(listingRatio, ProceduralValuationSettings.TRADE_LISTING_RARITY_EXPONENT)
        );
        double stockMultiplier = Math.min(
                ProceduralValuationSettings.TRADE_LOW_STOCK_MAX_MULTIPLIER,
                Math.pow(12.0 / Math.max(1.0, source.maxUses()), 0.08)
        );
        stockMultiplier = Math.max(1.0, stockMultiplier);
        double traderMultiplier = source.wandering()
                ? ProceduralValuationSettings.WANDERING_TRADER_MULTIPLIER
                : 1.0;

        double perOutput = costTotal
                * levelMultiplier
                * listingMultiplier
                * stockMultiplier
                * traderMultiplier
                / Math.max(1, source.outputCount());

        return new TradeAttempt(
                new TradePath(
                        source,
                        Math.max(1.0, perOutput),
                        levelMultiplier,
                        listingMultiplier,
                        stockMultiplier,
                        traderMultiplier,
                        fullyModeled,
                        progression,
                        ProceduralValuationConfidence.bound(Math.min(0.92, confidence + 0.08), fullyModeled),
                        Set.copyOf(dependencies)
                ),
                contextSensitive
        );
    }

    private static TradePath selectTradePath(List<TradePath> paths) {
        return paths.stream()
                .filter(TradePath::fullyModeledCosts)
                .min(Comparator.comparingDouble(TradePath::value))
                .orElseGet(() -> paths.stream()
                        .min(Comparator.comparingDouble(TradePath::value))
                        .orElse(null));
    }

    private static BlockDropPath selectBlockPath(List<BlockDropPath> paths) {
        return paths.stream()
                .filter(BlockDropPath::reliable)
                .min(Comparator.comparingDouble(BlockDropPath::value))
                .orElseGet(() -> paths.stream()
                        .min(Comparator.comparingDouble(BlockDropPath::value))
                        .orElse(null));
    }

    private static DropPath selectDropPath(List<DropPath> paths) {
        return paths.stream()
                .filter(path -> path.source().complexConditionCount() == 0)
                .min(Comparator.comparingDouble(DropPath::value))
                .orElseGet(() -> paths.stream()
                        .min(Comparator.comparingDouble(DropPath::value))
                        .orElse(null));
    }

    private static ContainerLootPath selectContainerPath(List<ContainerLootPath> paths) {
        return paths.stream()
                .filter(path -> path.source().complexConditionCount() == 0)
                .min(Comparator.comparingDouble(ContainerLootPath::value))
                .orElseGet(() -> paths.stream()
                        .min(Comparator.comparingDouble(ContainerLootPath::value))
                        .orElse(null));
    }

    private static FishingLootPath selectFishingPath(List<FishingLootPath> paths) {
        return paths.stream()
                .filter(path -> path.source().complexConditionCount() == 0)
                .min(Comparator.comparingDouble(FishingLootPath::value))
                .orElseGet(() -> paths.stream()
                        .min(Comparator.comparingDouble(FishingLootPath::value))
                        .orElse(null));
    }

    private static boolean isRecognizedOreSource(Item item, ItemStack stack) {
        if (stack.is(ProceduralValuationTags.ORES)
                || stack.is(ProceduralValuationTags.ORES_IN_STONE)
                || stack.is(ProceduralValuationTags.ORES_IN_DEEPSLATE)
                || stack.is(ProceduralValuationTags.ORES_IN_NETHERRACK)
                || stack.is(ProceduralValuationTags.ORES_IN_END_STONE)) {
            return true;
        }

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block.defaultBlockState().is(ProceduralValuationTags.BLOCK_ORES)
                    || block.defaultBlockState().is(ProceduralValuationTags.BLOCK_ORES_IN_STONE)
                    || block.defaultBlockState().is(ProceduralValuationTags.BLOCK_ORES_IN_DEEPSLATE)
                    || block.defaultBlockState().is(ProceduralValuationTags.BLOCK_ORES_IN_NETHERRACK)
                    || block.defaultBlockState().is(ProceduralValuationTags.BLOCK_ORES_IN_END_STONE)) {
                return true;
            }
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return itemId != null && itemId.getPath().contains("ancient_debris");
    }

    private static BlockDropPath evaluateBlockDropPath(
            double baseValue,
            ProceduralValuationResult.ProgressionBand intrinsicProgression,
            ProceduralValuationIndex.BlockDropSource source,
            ProceduralProgressionIndex.ProgressionEvidence progressionEvidence,
            EvaluationContext context, Set<Item> visiting, int depth
    ) {
        double sourceMultiplier = source.sourceMultiplier();
        if (intrinsicProgression.rank() >= source.progressionBand().rank()) {
            if (source.progressionBand() == ProceduralValuationResult.ProgressionBand.NETHER) {
                sourceMultiplier /= ProceduralValuationSettings.NETHER_MULTIPLIER;
            } else if (source.progressionBand() == ProceduralValuationResult.ProgressionBand.END) {
                sourceMultiplier /= ProceduralValuationSettings.END_MULTIPLIER;
            }
        }

        double rarityMultiplier = probabilityRarityMultiplier(source.estimatedChance());
        double quantityMultiplier = quantityMultiplier(source.expectedCount());
        double conditionMultiplier = unresolvedConditionMultiplier(source.complexConditionCount());

        boolean prerequisiteKnown = true;
        double reusableWear = 0.0;
        Set<Item> dependencies = Set.of();
        if (source.reusableTool() != null) {
            EvaluationNode tool = evaluateNode(source.reusableTool(), context, visiting, depth + 1);
            prerequisiteKnown = tool.knownAcquisition();
            dependencies = tool.dependencies();
            int durability = new ItemStack(source.reusableTool()).getMaxDamage();
            // Standard harvesting tools lose durability, not the complete tool per drop.
            // Non-damageable reusable tools have no modeled wear, but still require acquisition.
            reusableWear = durability > 0 ? (double) tool.acquisitionValue() / durability : 0.0;
        }
        if (source.silkTouchRequired()) sourceMultiplier *= ProceduralValuationSettings.SILK_TOUCH_HARVEST_MULTIPLIER;
        return new BlockDropPath(
                source,
                baseValue * sourceMultiplier * rarityMultiplier * quantityMultiplier
                        * conditionMultiplier * progressionEvidence.multiplier() + reusableWear,
                sourceMultiplier,
                rarityMultiplier,
                quantityMultiplier,
                conditionMultiplier,
                progressionEvidence,
                prerequisiteKnown, reusableWear, dependencies
        );
    }

    private static DropPath evaluateDropPath(
            double baseValue,
            ProceduralValuationIndex.DropSource source,
            ProceduralProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
        double difficultyMultiplier = 1.0 + Math.min(
                ProceduralValuationSettings.MOB_DIFFICULTY_MAX_MULTIPLIER - 1.0,
                Math.log1p(Math.max(0.0, source.difficultyScore())) * 0.34
        );
        double rarityMultiplier = probabilityRarityMultiplier(source.estimatedChance());
        double quantityMultiplier = quantityMultiplier(source.expectedCount());
        double bossMultiplier = source.bossScale()
                ? ProceduralValuationSettings.BOSS_SCALE_MULTIPLIER
                : 1.0;
        double spawnMultiplier = Math.max(0.10, source.spawnAvailabilityMultiplier());
        double specialMultiplier = Math.max(0.10, source.specialSourceMultiplier());
        double conditionMultiplier = unresolvedConditionMultiplier(source.complexConditionCount());

        String entityPath = source.entityId().getPath().toLowerCase(Locale.ROOT);
        ProceduralValuationResult.ProgressionBand progression;
        if (source.bossScale()) {
            progression = ProceduralValuationResult.ProgressionBand.BOSS_SCALE;
        } else if (looksNetherMob(entityPath)) {
            progression = ProceduralValuationResult.ProgressionBand.NETHER;
        } else if (looksEndMob(entityPath)) {
            progression = ProceduralValuationResult.ProgressionBand.END;
        } else {
            progression = ProceduralValuationResult.ProgressionBand.OVERWORLD;
        }

        return new DropPath(
                source,
                baseValue
                        * difficultyMultiplier
                        * rarityMultiplier
                        * quantityMultiplier
                        * bossMultiplier
                        * spawnMultiplier
                        * specialMultiplier
                        * conditionMultiplier
                        * progressionEvidence.multiplier(),
                difficultyMultiplier,
                rarityMultiplier,
                quantityMultiplier,
                spawnMultiplier,
                specialMultiplier * bossMultiplier,
                conditionMultiplier,
                progression,
                progressionEvidence
        );
    }

    private static ContainerLootPath evaluateContainerLootPath(
            double baseValue,
            ProceduralValuationResult.ProgressionBand intrinsicProgression,
            ProceduralValuationIndex.ContainerLootSource source,
            ProceduralProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
        double contextMultiplier = source.contextMultiplier();
        if (intrinsicProgression.rank() >= source.progressionBand().rank()) {
            if (source.progressionBand() == ProceduralValuationResult.ProgressionBand.NETHER) {
                contextMultiplier /= ProceduralValuationSettings.NETHER_MULTIPLIER;
            } else if (source.progressionBand() == ProceduralValuationResult.ProgressionBand.END) {
                contextMultiplier /= ProceduralValuationSettings.END_MULTIPLIER;
            }
        }

        double rarityMultiplier = probabilityRarityMultiplier(source.estimatedChance());
        double quantityMultiplier = quantityMultiplier(source.expectedCount());
        double conditionMultiplier = unresolvedConditionMultiplier(source.complexConditionCount());

        return new ContainerLootPath(
                source,
                baseValue * contextMultiplier * rarityMultiplier * quantityMultiplier
                        * conditionMultiplier * progressionEvidence.multiplier(),
                contextMultiplier,
                rarityMultiplier,
                quantityMultiplier,
                conditionMultiplier,
                progressionEvidence
        );
    }

    private static FishingLootPath evaluateFishingLootPath(
            double baseValue,
            ProceduralValuationResult.ProgressionBand intrinsicProgression,
            ProceduralValuationIndex.FishingLootSource source,
            ProceduralProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
        double contextMultiplier = source.contextMultiplier();
        if (intrinsicProgression.rank() >= source.progressionBand().rank()) {
            if (source.progressionBand() == ProceduralValuationResult.ProgressionBand.NETHER) {
                contextMultiplier /= ProceduralValuationSettings.NETHER_MULTIPLIER;
            } else if (source.progressionBand() == ProceduralValuationResult.ProgressionBand.END) {
                contextMultiplier /= ProceduralValuationSettings.END_MULTIPLIER;
            }
        }

        double rarityMultiplier = probabilityRarityMultiplier(source.estimatedChance());
        double quantityMultiplier = quantityMultiplier(source.expectedCount());
        double conditionMultiplier = unresolvedConditionMultiplier(source.complexConditionCount());

        return new FishingLootPath(
                source,
                baseValue * contextMultiplier * rarityMultiplier * quantityMultiplier
                        * conditionMultiplier * progressionEvidence.multiplier(),
                contextMultiplier,
                rarityMultiplier,
                quantityMultiplier,
                conditionMultiplier,
                progressionEvidence
        );
    }

    private static double probabilityRarityMultiplier(double estimatedChance) {
        double chance = Math.max(0.000001, Math.min(1.0, estimatedChance));
        return Math.min(
                ProceduralValuationSettings.DROP_RARITY_MAX_MULTIPLIER,
                Math.pow(1.0 / chance, ProceduralValuationSettings.DROP_RARITY_EXPONENT)
        );
    }

    private static double quantityMultiplier(double expectedCount) {
        return Math.max(
                ProceduralValuationSettings.DROP_QUANTITY_MIN_MULTIPLIER,
                1.0 / Math.sqrt(Math.max(1.0, expectedCount))
        );
    }

    private static double unresolvedConditionMultiplier(int complexConditionCount) {
        if (complexConditionCount <= 0) {
            return 1.0;
        }
        return Math.pow(
                ProceduralValuationSettings.UNRESOLVED_SOURCE_CONDITION_MULTIPLIER,
                Math.min(4, complexConditionCount)
        );
    }

    private static Renewability renewability(Item item, ProceduralValuationIndex index) {
        ItemStack stack = new ItemStack(item);
        Renewability best = Renewability.NONE;

        if (stack.is(ProceduralValuationTags.CROPS)) {
            best = strongerRenewability(best, new Renewability(
                    ProceduralValuationSettings.CROP_RENEWABLE_REMAINDER,
                    0.12,
                    "growable crop"
            ));
        }
        if (stack.is(ProceduralValuationTags.SEEDS)) {
            best = strongerRenewability(best, new Renewability(
                    ProceduralValuationSettings.SEED_RENEWABLE_REMAINDER,
                    0.10,
                    "replantable seed"
            ));
        }
        if (stack.is(ProceduralValuationTags.SAPLINGS)) {
            best = strongerRenewability(best, new Renewability(
                    ProceduralValuationSettings.SAPLING_RENEWABLE_REMAINDER,
                    0.10,
                    "renewable sapling"
            ));
        }
        if (stack.is(ProceduralValuationTags.LOGS)) {
            best = strongerRenewability(best, new Renewability(
                    ProceduralValuationSettings.LOG_RENEWABLE_REMAINDER,
                    0.09,
                    "tree-grown log"
            ));
        }
        if (stack.is(ProceduralValuationTags.LEAVES)) {
            best = strongerRenewability(best, new Renewability(
                    ProceduralValuationSettings.LEAF_RENEWABLE_REMAINDER,
                    0.08,
                    "tree-grown leaves"
            ));
        }

        if (index != null) {
            if (!index.biologicalSources(item).isEmpty()) {
                best = strongerRenewability(best, new Renewability(
                        ProceduralValuationSettings.COMMON_MOB_DROP_RENEWABLE_REMAINDER,
                        0.0, "repeatable biological production; qualitative farm pressure, not a measured rate"));
            }
            double bestRepeatableMobChance = index.dropSources(item).stream()
                    .filter(source -> source.complexConditionCount() == 0)
                    .filter(ProceduralValuationIndex.DropSource::repeatableSpawn)
                    .filter(source -> !source.bossScale())
                    .filter(source -> source.specialSourceMultiplier() <= 1.000001)
                    .mapToDouble(ProceduralValuationIndex.DropSource::estimatedChance)
                    .max()
                    .orElse(0.0);
            if (bestRepeatableMobChance > 0.0) {
                double remainder;
                if (bestRepeatableMobChance >= 0.50) {
                    remainder = ProceduralValuationSettings.COMMON_MOB_DROP_RENEWABLE_REMAINDER;
                } else if (bestRepeatableMobChance >= 0.10) {
                    remainder = ProceduralValuationSettings.UNCOMMON_MOB_DROP_RENEWABLE_REMAINDER;
                } else if (bestRepeatableMobChance >= 0.025) {
                    remainder = ProceduralValuationSettings.RARE_MOB_DROP_RENEWABLE_REMAINDER;
                } else {
                    remainder = ProceduralValuationSettings.VERY_RARE_MOB_DROP_RENEWABLE_REMAINDER;
                }
                best = strongerRenewability(best, new Renewability(
                        remainder,
                        0.12,
                        "repeatably farmable natural mob drop (best modeled chance "
                                + formatPercent(bestRepeatableMobChance) + ")"
                ));
            }

            boolean reliableFishing = index.fishingLootSources(item).stream()
                    .anyMatch(source -> source.complexConditionCount() == 0);
            if (reliableFishing) {
                best = strongerRenewability(best, new Renewability(
                        ProceduralValuationSettings.FISHING_RENEWABLE_REMAINDER,
                        0.11,
                        "repeatable fishing loot"
                ));
            }
        }

        return best;
    }

    private static Renewability strongerRenewability(
            Renewability current,
            Renewability candidate
    ) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.multiplier() < current.multiplier() - 0.000001) {
            return candidate;
        }
        return current;
    }

    private static double progressionMultiplier(double score) {
        double clamped = Math.max(0.0, Math.min(1.0, score));
        return 1.0 + clamped * (ProceduralValuationSettings.ADVANCEMENT_PROGRESSION_MAX_MULTIPLIER - 1.0);
    }

    private static Intrinsic intrinsic(Item item, EvaluationContext context) {
        return context.intrinsicMemo.computeIfAbsent(item, ProceduralValuationEngine::measureIntrinsic);
    }

    private static Intrinsic measureIntrinsic(Item item) {
        ItemStack stack = new ItemStack(item);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        long base = ProceduralValuationSettings.DEFAULT_BASE;
        boolean recognized = false;
        boolean explicitProgression = false;
        double confidence = 0.24;
        List<String> factors = new ArrayList<>();

        if (isRecognizedOreSource(item, stack)) {
            base = Math.max(base, ProceduralValuationSettings.ORE_BASE);
            recognized = true;
            confidence += 0.22;
            factors.add("Bucket: c:ores/source ore -> Gathering-biased resource baseline");
        }
        if (stack.is(ProceduralValuationTags.RAW_MATERIALS)) {
            base = Math.max(base, ProceduralValuationSettings.RAW_MATERIAL_BASE);
            recognized = true;
            confidence += 0.20;
            factors.add("Bucket: c:raw_materials");
        }
        if (stack.is(ProceduralValuationTags.INGOTS)) {
            base = Math.max(base, ProceduralValuationSettings.INGOT_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: c:ingots");
        }
        if (stack.is(ProceduralValuationTags.GEMS)) {
            base = Math.max(base, ProceduralValuationSettings.GEM_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: c:gems");
        }
        if (stack.is(ProceduralValuationTags.NUGGETS)) {
            base = Math.max(base, ProceduralValuationSettings.NUGGET_BASE);
            recognized = true;
            confidence += 0.16;
            factors.add("Bucket: c:nuggets");
        }
        if (stack.is(ProceduralValuationTags.STORAGE_BLOCKS)) {
            base = Math.max(base, ProceduralValuationSettings.STORAGE_BLOCK_BASE);
            recognized = true;
            confidence += 0.16;
            factors.add("Bucket: c:storage_blocks");
        }
        if (stack.is(ProceduralValuationTags.FOODS) || stack.has(DataComponents.FOOD)) {
            base = Math.max(base, ProceduralValuationSettings.FOOD_BASE);
            recognized = true;
            confidence += 0.14;
            factors.add("Bucket: food tag/component");
        }
        if (stack.is(ProceduralValuationTags.MINING_TOOLS) || item instanceof DiggerItem
                || item instanceof ShearsItem || item instanceof BrushItem || item instanceof FishingRodItem) {
            base = Math.max(base, ProceduralValuationSettings.TOOL_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: harvesting tool tag/runtime class");
        }
        if (stack.is(ProceduralValuationTags.MELEE_WEAPONS)) {
            base = Math.max(base, ProceduralValuationSettings.WEAPON_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: c:tools/melee_weapon");
        }
        if (stack.is(ProceduralValuationTags.RANGED_WEAPONS)) {
            base = Math.max(base, ProceduralValuationSettings.WEAPON_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: c:tools/ranged_weapon");
        }
        if (stack.is(ProceduralValuationTags.ARMORS) || item instanceof ArmorItem || item instanceof ShieldItem) {
            base = Math.max(base, ProceduralValuationSettings.ARMOR_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: armor/equipment");
        }
        if (stack.is(ProceduralValuationTags.REDSTONE_DUSTS)) {
            base = Math.max(base, ProceduralValuationSettings.REDSTONE_BASE);
            recognized = true;
            confidence += 0.14;
            factors.add("Bucket: redstone/automation material");
        }
        if (item instanceof BlockItem && !recognized) {
            base = Math.max(base, ProceduralValuationSettings.BLOCK_BASE);
            confidence += 0.05;
            factors.add("Shape: block item baseline");
        }

        if (item instanceof SwordItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof MaceItem || item instanceof ArrowItem) {
            base = Math.max(base, ProceduralValuationSettings.WEAPON_BASE);
            recognized = true;
            confidence += 0.10;
            factors.add("Shape: weapon class");
        }

        if (item instanceof BoatItem || item instanceof MinecartItem || item instanceof ElytraItem
                || item instanceof EnderpearlItem || item instanceof FireworkRocketItem
                || (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof BaseRailBlock)) {
            base = Math.max(base, ProceduralValuationSettings.TRANSPORT_BASE);
            recognized = true;
            confidence += 0.10;
            factors.add("Shape: transport/propulsion runtime class");
        }

        String path = itemId == null
                ? ""
                : itemId.getPath().toLowerCase(Locale.ROOT);

        // Nomenclature is intentionally absent from economic valuation. The
        // previous parser raised base prices/confidence for words such as
        // "blade" even in pottery-shard names. Functional name hints now live
        // exclusively in the separately diagnosed routing pass below.

        double rarityMultiplier = rarityMultiplier(stack.getRarity());
        if (rarityMultiplier != 1.0) {
            base = clampValue(base * rarityMultiplier);
            factors.add("Vanilla rarity: " + stack.getRarity() + " -> x" + format(rarityMultiplier));
        }

        ProceduralValuationResult.ProgressionBand progression =
                ProceduralValuationResult.ProgressionBand.OVERWORLD;

        boolean netherrackOre = stack.is(ProceduralValuationTags.ORES_IN_NETHERRACK);
        boolean endOre = stack.is(ProceduralValuationTags.ORES_IN_END_STONE);
        boolean deepslateOre = stack.is(ProceduralValuationTags.ORES_IN_DEEPSLATE);

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            netherrackOre |= block.defaultBlockState().is(ProceduralValuationTags.BLOCK_ORES_IN_NETHERRACK);
            endOre |= block.defaultBlockState().is(ProceduralValuationTags.BLOCK_ORES_IN_END_STONE);
            deepslateOre |= block.defaultBlockState().is(ProceduralValuationTags.BLOCK_ORES_IN_DEEPSLATE);
        }

        if (netherrackOre || looksNetherNative(path)) {
            base = clampValue(base * ProceduralValuationSettings.NETHER_MULTIPLIER);
            progression = ProceduralValuationResult.ProgressionBand.NETHER;
            explicitProgression = true;
            confidence += netherrackOre ? 0.15 : 0.05;
            factors.add(
                    netherrackOre
                            ? "Progression/source: c:ores_in_ground/netherrack -> Nether x"
                            + format(ProceduralValuationSettings.NETHER_MULTIPLIER)
                            : "Progression/source: Nether-native identity heuristic -> x"
                            + format(ProceduralValuationSettings.NETHER_MULTIPLIER)
            );
        }

        if (endOre || looksEndNative(path)) {
            base = clampValue(base * ProceduralValuationSettings.END_MULTIPLIER);
            progression = ProceduralValuationResult.ProgressionBand.END;
            explicitProgression = true;
            confidence += endOre ? 0.15 : 0.05;
            factors.add(
                    endOre
                            ? "Progression/source: c:ores_in_ground/end_stone -> End x"
                            + format(ProceduralValuationSettings.END_MULTIPLIER)
                            : "Progression/source: End-native identity heuristic -> x"
                            + format(ProceduralValuationSettings.END_MULTIPLIER)
            );
        }

        if (deepslateOre) {
            base = clampValue(base * ProceduralValuationSettings.DEEPSLATE_MULTIPLIER);
            explicitProgression = true;
            confidence += 0.05;
            factors.add("Acquisition depth: deepslate ore -> x" + format(ProceduralValuationSettings.DEEPSLATE_MULTIPLIER));
        }

        long floor = Math.max(1L, Math.round(base * 0.70));
        return new Intrinsic(
                base,
                floor,
                recognized,
                explicitProgression,
                progression,
                Math.min(0.82, confidence),
                List.copyOf(factors)
        );
    }

    private static RouteWeights directRoute(Item item, EvaluationContext context) {
        return directRouting(item, context).combined();
    }

    private static DirectRouting directRouting(Item item, EvaluationContext context) {
        DirectRouting cached = context.directRouteMemo().get(item);
        if (cached != null) {
            return cached;
        }
        ItemStack stack = new ItemStack(item);
        RouteWeights structured = new RouteWeights();
        List<String> signals = new ArrayList<>();

        if (isRecognizedOreSource(item, stack) || stack.is(ProceduralValuationTags.RAW_MATERIALS)) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            signals.add("resource/ore");
        }
        if (stack.is(ProceduralValuationTags.MINING_TOOLS)
                || stack.is(ProceduralValuationTags.AXES) || stack.is(ProceduralValuationTags.PICKAXES)
                || stack.is(ProceduralValuationTags.SHOVELS) || stack.is(ProceduralValuationTags.HOES)
                || item instanceof DiggerItem || item instanceof ShearsItem || item instanceof BrushItem) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            signals.add("harvesting_tool");
        }
        if (item instanceof FishingRodItem) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            signals.add("fishing_rod");
        }
        // Swords also carry a native TOOL component for cobwebs. Preserve their
        // established weapon identity; this fills missing modded harvesting evidence.
        if (stack.has(DataComponents.TOOL) && !signals.contains("harvesting_tool")
                && !(item instanceof SwordItem || item instanceof TridentItem || item instanceof MaceItem)
                && !stack.is(ProceduralValuationTags.MELEE_WEAPONS) && !stack.is(ProceduralValuationTags.SWORDS)) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            signals.add("tool_component");
        }
        if (item instanceof ShieldItem) {
            structured.add(EssenceTypes.DEFENSE, 8.0);
            structured.add(EssenceTypes.VITALITY, 1.0);
            signals.add("shield");
        }
        if (stack.is(ProceduralValuationTags.MELEE_WEAPONS) || stack.is(ProceduralValuationTags.SWORDS)
                || item instanceof SwordItem || item instanceof TridentItem || item instanceof MaceItem) {
            structured.add(EssenceTypes.OFFENSE, 7.0);
            signals.add("melee_weapon");
        }
        if (stack.is(ProceduralValuationTags.RANGED_WEAPONS) || stack.is(ProceduralValuationTags.ARROWS)
                || item instanceof BowItem || item instanceof CrossbowItem || item instanceof ArrowItem) {
            structured.add(EssenceTypes.OFFENSE, 7.0);
            signals.add("ranged_weapon/projectile");
        }
        if (stack.is(ProceduralValuationTags.ARMORS) || item instanceof ArmorItem) {
            structured.add(EssenceTypes.DEFENSE, 7.0);
            structured.add(EssenceTypes.VITALITY, 2.0);
            signals.add("armor");
        }
        if (stack.is(ProceduralValuationTags.FOODS) || stack.has(DataComponents.FOOD)) {
            structured.add(EssenceTypes.VITALITY, 8.0);
            signals.add("food_tag/component");
        }
        if (stack.is(ProceduralValuationTags.REDSTONE_DUSTS)) {
            structured.add(EssenceTypes.UTILITY, 7.0);
            signals.add("redstone_material");
        }
        if (item instanceof BoatItem || item instanceof MinecartItem
                || item instanceof ElytraItem || item instanceof EnderpearlItem) {
            structured.add(EssenceTypes.MOBILITY, 8.0);
            signals.add("transport_item");
        }
        if (item instanceof FireworkRocketItem) {
            structured.add(EssenceTypes.MOBILITY, 5.0);
            structured.add(EssenceTypes.OFFENSE, 1.0);
            structured.add(EssenceTypes.UTILITY, 2.0);
            signals.add("firework_propulsion/display");
        }
        Block block = item instanceof BlockItem blockItem ? blockItem.getBlock() : null;
        if (block != null && block.defaultBlockState().is(ProceduralValuationTags.CLIMBABLE)) {
            structured.add(EssenceTypes.MOBILITY, 7.0);
            signals.add("climbable_block");
        }
        if (block instanceof BaseRailBlock) {
            structured.add(EssenceTypes.MOBILITY, 8.0);
            signals.add("rail_block");
        }
        if (stack.is(ProceduralValuationTags.BEDS) || block instanceof BedBlock
                || block instanceof RespawnAnchorBlock) {
            structured.add(EssenceTypes.VITALITY, 7.0);
            structured.add(EssenceTypes.MOBILITY, 2.0);
            signals.add("rest/respawn_block");
        }
        if (block instanceof TntBlock) {
            structured.add(EssenceTypes.OFFENSE, 7.0);
            structured.add(EssenceTypes.GATHERING, 2.0);
            signals.add("explosive/demolition_block");
        }
        if (stack.is(ProceduralValuationTags.WOOL) || stack.is(ProceduralValuationTags.WOOL_CARPETS)) {
            structured.add(EssenceTypes.DEFENSE, 3.0);
            structured.add(EssenceTypes.VITALITY, 2.0);
            structured.add(EssenceTypes.UTILITY, 1.0);
            signals.add("protective/comfort_textile");
        }
        if (stack.is(ProceduralValuationTags.FENCES) || stack.is(ProceduralValuationTags.FENCE_GATES)
                || stack.is(ProceduralValuationTags.WALLS) || stack.is(ProceduralValuationTags.DOORS)
                || stack.is(ProceduralValuationTags.TRAPDOORS)) {
            structured.add(EssenceTypes.DEFENSE, 6.0);
            structured.add(EssenceTypes.UTILITY, 2.0);
            signals.add("physical_barrier");
        }
        if (stack.is(ProceduralValuationTags.CROPS) || stack.is(ProceduralValuationTags.SEEDS)
                || stack.is(ProceduralValuationTags.SAPLINGS)) {
            structured.add(EssenceTypes.GATHERING, 4.0);
            structured.add(EssenceTypes.VITALITY, 3.0);
            signals.add("cultivation_material");
        }
        if (item instanceof BoneMealItem || stack.is(ProceduralValuationTags.FERTILIZERS)) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            structured.add(EssenceTypes.VITALITY, 1.0);
            signals.add("fertilizer_item/tag");
        }
        if (stack.is(ProceduralValuationTags.FLOWERS) || stack.is(ProceduralValuationTags.LEAVES)) {
            structured.add(EssenceTypes.VITALITY, 3.0);
            structured.add(EssenceTypes.GATHERING, 2.0);
            structured.add(EssenceTypes.UTILITY, 1.0);
            signals.add("living_plant_material");
        }

        if (structured.isEmpty()) {
            // Plain modded items can expose equipped function through slot-filtered
            // native modifiers. These are function votes, never measured axis values.
            boolean[] function = {false, false};
            stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                        && modifier.amount() > 0) function[0] = true;
            });
            for (var slot : List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET)) {
                stack.forEachModifier(slot, (attribute, modifier) -> {
                    if (attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                            && modifier.amount() > 0) function[1] = true;
                });
            }
            if (function[0]) { structured.add(EssenceTypes.OFFENSE, 7); signals.add("positive_mainhand_attack_attribute"); }
            if (function[1]) { structured.add(EssenceTypes.DEFENSE, 7); signals.add("positive_armor_attribute"); }
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        ProceduralItemNomenclature.Analysis name = ProceduralItemNomenclature.analyze(
                id == null ? "" : id.toString(), item.getDescriptionId());
        RouteWeights hints = new RouteWeights();
        hints.add(EssenceTypes.OFFENSE, name.offense());
        hints.add(EssenceTypes.DEFENSE, name.defense());
        hints.add(EssenceTypes.VITALITY, name.vitality());
        hints.add(EssenceTypes.MOBILITY, name.mobility());
        hints.add(EssenceTypes.GATHERING, name.gathering());
        hints.add(EssenceTypes.UTILITY, name.utility());
        RouteWeights combined = structured.copy();
        double nameBudget = structured.isEmpty()
                ? ProceduralValuationSettings.ROUTING_NAME_WEIGHT_WITHOUT_STRUCTURED
                : ProceduralValuationSettings.ROUTING_NAME_WEIGHT_WITH_STRUCTURED;
        combined.addNormalized(hints, Math.min(nameBudget, hints.totalWeight()));
        DirectRouting result = new DirectRouting(structured, combined, name, List.copyOf(signals));
        context.directRouteMemo().put(item, result);
        return result;
    }

    /**
     * Resolve function -> downstream -> composition, THEN add weak acquisition
     * context. A sole trade/fishing hint must not be normalized into a 100%
     * Utility/Gathering item. Reversible forms share the whole final route,
     * including source context, rather than just sharing part of the vector.
     */
    private static RoutingResolution resolveRouting(
            Item item, EvaluationNode node, ProceduralValuationIndex snapshot, EvaluationContext context
    ) {
        RoutingResolution cached = context.resolvedRouteMemo().get(item);
        if (cached != null) {
            return cached;
        }
        List<Item> family = new ArrayList<>(snapshot.conservationGroup(item));
        if (family.isEmpty()) {
            family.add(item);
        }
        family.sort(Comparator.comparing(member -> BuiltInRegistries.ITEM.getKey(member).toString()));
        boolean conserved = family.size() > 1;
        boolean hasStructured = false;
        boolean hasName = false;
        boolean hasDownstream = false;
        boolean hasComposition = false;
        RouteWeights semantics = new RouteWeights();
        RouteWeights sources = new RouteWeights();
        LinkedHashSet<String> structuredSignals = new LinkedHashSet<>();
        for (Item member : family) {
            DirectRouting direct = directRouting(member, context);
            hasStructured |= !direct.structured().isEmpty();
            hasName |= direct.nomenclature().present();
            structuredSignals.addAll(direct.signals());
            RouteWeights memberRoute = semanticRoute(member, snapshot, context, new LinkedHashSet<>(), 0);
            hasDownstream |= memberRoute.totalWeight() > direct.combined().totalWeight() + 0.000001;
            if (conserved) {
                semantics.addNormalized(memberRoute, 1.0);
            } else {
                semantics.add(memberRoute);
            }
            sources.addNormalized(acquisitionRoute(member, snapshot), 1.0);
        }
        RouteWeights route = new RouteWeights();
        if (conserved) {
            route.addNormalized(semantics, ProceduralValuationSettings.ROUTING_CONSERVATION_WEIGHT);
        } else {
            route.add(semantics);
        }

        if (route.isEmpty()) {
            RouteWeights composition = new RouteWeights();
            for (Item member : family) {
                // Only obtain the local recipe choice. Do not re-enter final
                // conservation normalization while resolving a family's route.
                // Use LOCAL choices for every conserved member, including the
                // requested one: normalized nodes may have cleared their recipe
                // choice after choosing another form's cheaper source.
                EvaluationNode memberNode = conserved
                        ? evaluateNode(member, context, new LinkedHashSet<>(), 0)
                        : node;
                composition.addNormalized(recipeCompositionRoute(member, memberNode, snapshot, context), 1.0);
            }
            hasComposition = !composition.isEmpty();
            route.addNormalized(composition, ProceduralValuationSettings.ROUTING_COMPOSITION_WEIGHT);
        }
        boolean fallback = route.isEmpty();
        if (fallback) {
            addFallbackRoute(route);
        }
        route.addNormalized(sources, fallback
                ? ProceduralValuationSettings.ROUTING_ACQUISITION_WEIGHT_WITHOUT_DIRECT
                : ProceduralValuationSettings.ROUTING_ACQUISITION_WEIGHT_WITH_DIRECT);
        route.retainDominant();

        List<String> evidence = new ArrayList<>();
        if (hasStructured) evidence.add("structured_function");
        if (hasName) evidence.add("name_hint");
        boolean conflict = false;
        boolean ambiguous = false;
        for (Item member : family) {
            DirectRouting direct = directRouting(member, context);
            var name = direct.nomenclature();
            double[] hints = {name.offense(), name.defense(), name.vitality(), name.mobility(), name.gathering(), name.utility()};
            int positive = 0;
            double overlap = 0;
            List<EssenceDefinition> categories = List.of(EssenceTypes.OFFENSE, EssenceTypes.DEFENSE,
                    EssenceTypes.VITALITY, EssenceTypes.MOBILITY, EssenceTypes.GATHERING, EssenceTypes.UTILITY);
            for (int i = 0; i < hints.length; i++) if (hints[i] > 0) {
                positive++;
                overlap += direct.structured().values.getOrDefault(categories.get(i), 0.0);
            }
            conflict |= name.present() && !direct.structured().isEmpty() && overlap == 0;
            ambiguous |= direct.structured().isEmpty() && positive > EssenceRoutingPolicy.MAX_CATEGORIES;
        }
        if (conflict) evidence.add("name_structured_conflict");
        if (ambiguous) evidence.add("ambiguous_name_hint");
        if (hasDownstream) evidence.add("downstream_recipes");
        if (hasComposition) evidence.add("recipe_composition");
        if (conserved) evidence.add("conservation_family");
        if (fallback) evidence.add("utility_fallback");
        if (!sources.isEmpty()) evidence.add("weak_acquisition_context");
        evidence.add("dominant_categories");
        ProceduralValuationResult.ConfidenceBand confidence = hasStructured
                ? ProceduralValuationResult.ConfidenceBand.HIGH
                : (hasDownstream || hasComposition)
                ? ProceduralValuationResult.ConfidenceBand.MEDIUM
                : ProceduralValuationResult.ConfidenceBand.LOW;
        // Cache each form's own lexical diagnostics but one identical final
        // weight vector. No new route recursion is introduced by this cache.
        for (Item member : family) {
            ProceduralItemNomenclature.Analysis name = directRouting(member, context).nomenclature();
            ProceduralValuationResult.RoutingDiagnostics diagnostics = new ProceduralValuationResult.RoutingDiagnostics(
                    evidence, confidence, name.source(), name.matches(), List.copyOf(structuredSignals));
            context.resolvedRouteMemo().put(member, new RoutingResolution(route.copy(), diagnostics));
        }
        return context.resolvedRouteMemo().get(item);
    }

    private static RouteWeights semanticRoute(
            Item item,
            ProceduralValuationIndex index,
            EvaluationContext context,
            Set<Item> visiting,
            int depth
    ) {
        boolean topLevel = visiting.isEmpty();
        if (topLevel) {
            RouteWeights cached = context.routeMemo().get(item);
            if (cached != null) {
                return cached.copy();
            }
        }

        RouteWeights direct = directRoute(item, context);
        RouteWeights route = direct.copy();
        DirectRouting identity = directRouting(item, context);
        if (identity.structured().isEmpty() && identity.nomenclature().suppressesInheritedFunction()) {
            // Being consumed to make a whole machine does not give a named part
            // that machine's functionality. Actual native function still wins.
            if (topLevel) context.routeMemo().put(item, route.copy());
            return route;
        }
        if (depth >= ProceduralValuationSettings.MAX_ROUTING_DEPTH || !visiting.add(item)) {
            return route;
        }

        RouteWeights downstream = new RouteWeights();
        for (ProceduralValuationIndex.RecipeModel recipe : routingRecipes(item, context)) {
            Item output = recipe.outputItem();
            if (output == item) {
                continue;
            }

            RouteWeights outputRoute = directRoute(output, context);
            if (outputRoute.isEmpty() && depth + 1 < ProceduralValuationSettings.MAX_ROUTING_DEPTH) {
                outputRoute = semanticRoute(
                        output,
                        index,
                        context,
                        visiting,
                        depth + 1
                );
            }
            if (outputRoute.isEmpty()) {
                continue;
            }

            double vote = isProgressionOutput(output, context) ? 1.35 : 1.0;
            downstream.addNormalized(outputRoute, vote);
        }

        visiting.remove(item);

        if (!downstream.isEmpty()) {
            route.addNormalized(
                    downstream,
                    direct.isEmpty()
                            ? ProceduralValuationSettings.ROUTING_DOWNSTREAM_WEIGHT_WITHOUT_DIRECT
                            : ProceduralValuationSettings.ROUTING_DOWNSTREAM_WEIGHT_WITH_DIRECT
            );
        }

        if (topLevel) {
            context.routeMemo().put(item, route.copy());
        }
        return route;
    }

    private static List<ProceduralValuationIndex.RecipeModel> routingRecipes(Item item, EvaluationContext context) {
        return context.routingRecipes.computeIfAbsent(item, key -> {
            // Preserve the old stable recipe-ID ordering, distinct-ID semantics
            // and cap (including self-output slots). Recursive routes retain
            // their original ancestor/cycle checks; only fixed inputs are cached.
            var uses = new ArrayList<>(context.index().recipesUsing(key));
            uses.sort(Comparator.comparing(use -> use.recipe().id().toString()));
            Set<ResourceLocation> seen = new LinkedHashSet<>();
            List<ProceduralValuationIndex.RecipeModel> selected = new ArrayList<>();
            for (var use : uses) {
                var recipe = use.recipe();
                if (context.index().isReversibleTransform(recipe) || !seen.add(recipe.id())) continue;
                selected.add(recipe);
                if (selected.size() == ProceduralValuationSettings.MAX_ROUTING_DOWNSTREAM_RECIPES) break;
            }
            return List.copyOf(selected);
        });
    }

    private static RouteWeights recipeCompositionRoute(
            Item target,
            EvaluationNode node,
            ProceduralValuationIndex index,
            EvaluationContext context
    ) {
        if (node.recipeChoice().isEmpty() || node.recipeChoice().get().reversibleTransform()) {
            return new RouteWeights();
        }

        ResourceLocation chosenRecipeId = node.recipeChoice().get().recipeId();
        ProceduralValuationIndex.RecipeModel chosenRecipe = index.recipesProducing(target).stream()
                .filter(recipe -> recipe.id().equals(chosenRecipeId))
                .findFirst()
                .orElse(null);
        if (chosenRecipe == null) {
            return new RouteWeights();
        }

        RouteWeights composition = new RouteWeights();
        Set<Item> visiting = new LinkedHashSet<>();
        visiting.add(target);

        for (ProceduralValuationIndex.IngredientChoice ingredient : chosenRecipe.ingredients()) {
            if (!ingredient.consumed()) continue;
            RouteWeights alternatives = new RouteWeights();
            int considered = 0;
            for (Item alternative : ingredient.alternatives()) {
                if (++considered > ProceduralValuationSettings.MAX_ROUTING_ALTERNATIVES_PER_INGREDIENT) {
                    break;
                }
                RouteWeights alternativeRoute = semanticRoute(
                        alternative,
                        index,
                        context,
                        visiting,
                        1
                );
                alternatives.addNormalized(alternativeRoute, 1.0);
            }
            composition.addNormalized(alternatives, 1.0);
        }
        return composition;
    }

    private static void addFallbackRoute(RouteWeights route) {
        route.add(EssenceTypes.UTILITY, 1.0);
    }

    private static RouteWeights acquisitionRoute(Item item, ProceduralValuationIndex index) {
        RouteWeights route = new RouteWeights();

        boolean bossScale = index.dropSources(item).stream()
                .anyMatch(source -> source.bossScale()
                        || source.specialSourceMultiplier() >= ProceduralValuationSettings.PLAYER_SUMMONED_BOSS_MULTIPLIER);
        if (bossScale) {
            route.add(EssenceTypes.OFFENSE, 3.0);
            route.add(EssenceTypes.DEFENSE, 1.2);
            route.add(EssenceTypes.VITALITY, 1.2);
            route.add(EssenceTypes.UTILITY, 1.0);
        }

        boolean endContainer = index.containerLootSources(item).stream()
                .anyMatch(source -> source.progressionBand() == ProceduralValuationResult.ProgressionBand.END);
        if (endContainer) {
            route.add(EssenceTypes.MOBILITY, 0.8);
            route.add(EssenceTypes.UTILITY, 1.2);
        }

        if (!index.fishingLootSources(item).isEmpty()) {
            route.add(EssenceTypes.GATHERING, 0.8);
            route.add(EssenceTypes.UTILITY, 0.5);
        }
        if (!index.tradeSources(item).isEmpty()) {
            route.add(EssenceTypes.UTILITY, 0.5);
        }
        return route;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static DownstreamInfo downstreamInfo(
            Item item,
            EvaluationContext context
    ) {
        return context.downstreamMemo.computeIfAbsent(item, key -> measureDownstream(key, context));
    }

    private static DownstreamInfo measureDownstream(Item item, EvaluationContext context) {
        ProceduralValuationIndex index = context.index();
        List<ProceduralValuationIndex.RecipeUse> uses = index.recipesUsing(item);
        if (uses.isEmpty()) {
            return DownstreamInfo.EMPTY;
        }

        LinkedHashSet<ResourceLocation> uniqueRecipes = new LinkedHashSet<>();
        LinkedHashSet<ResourceLocation> examples = new LinkedHashSet<>();
        int significant = 0;
        int crossMod = 0;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        for (ProceduralValuationIndex.RecipeUse use : uses) {
            ProceduralValuationIndex.RecipeModel recipe = use.recipe();
            // Storage/compression recipes are conservation transforms, not
            // meaningful downstream demand. Counting them here would reward
            // ingots/nuggets/blocks simply for being reversible.
            if (index.isReversibleTransform(recipe)) {
                continue;
            }
            if (!uniqueRecipes.add(recipe.id())) {
                continue;
            }

            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(recipe.outputItem());
            if (outputId != null && examples.size() < ProceduralValuationSettings.MAX_DOWNSTREAM_EXAMPLES) {
                examples.add(outputId);
            }

            Intrinsic outputIntrinsic = intrinsic(recipe.outputItem(), context);

            if (outputIntrinsic.value() >= ProceduralValuationSettings.RARE_INGREDIENT_THRESHOLD
                    || isProgressionOutput(recipe.outputItem(), context)) {
                significant++;
            }

            if (itemId != null
                    && outputId != null
                    && !itemId.getNamespace().equals(outputId.getNamespace())) {
                crossMod++;
            }
        }

        int count = uniqueRecipes.size();
        double multiplier = 1.0
                + count * ProceduralValuationSettings.DOWNSTREAM_USE_STEP
                + significant * ProceduralValuationSettings.DOWNSTREAM_SIGNIFICANT_STEP
                + crossMod * ProceduralValuationSettings.DOWNSTREAM_CROSS_MOD_STEP;
        multiplier = Math.min(
                ProceduralValuationSettings.DOWNSTREAM_MAX_MULTIPLIER,
                multiplier
        );

        return new DownstreamInfo(
                count,
                significant,
                crossMod,
                multiplier,
                List.copyOf(examples)
        );
    }

    private static boolean isProgressionOutput(Item item, EvaluationContext context) {
        return context.progressionOutputMemo.computeIfAbsent(item, ProceduralValuationEngine::measureProgressionOutput);
    }

    private static boolean measureProgressionOutput(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(ProceduralValuationTags.ARMORS)
                || stack.is(ProceduralValuationTags.MINING_TOOLS)
                || stack.is(ProceduralValuationTags.MELEE_WEAPONS)
                || stack.is(ProceduralValuationTags.RANGED_WEAPONS)
                || item instanceof ArmorItem
                || item instanceof SwordItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem;
    }

    private static Map<EssenceDefinition, Long> allocate(long totalValue, RouteWeights route) {
        if (totalValue <= 0L) {
            return Map.of();
        }
        RouteWeights effective = route;
        if (effective.isEmpty()) {
            effective = new RouteWeights();
            addFallbackRoute(effective);
        }
        double weightTotal = effective.totalWeight();
        List<AllocationShare> shares = new ArrayList<>();
        Map<EssenceDefinition, Long> result = new LinkedHashMap<>();
        long assigned = 0L;
        for (Map.Entry<EssenceDefinition, Double> entry : effective.entries()) {
            double exact = totalValue * (entry.getValue() / weightTotal);
            long whole = Math.min(totalValue - assigned, Math.max(0L, (long) Math.floor(exact)));
            if (whole > 0L) {
                result.put(entry.getKey(), whole);
                assigned += whole;
            }
            shares.add(new AllocationShare(entry.getKey(), exact - Math.floor(exact)));
        }
        // Largest-remainder allocation preserves the total exactly without
        // dumping all rounding error into whichever Essence sorted last.
        shares.sort(Comparator.comparingDouble(AllocationShare::remainder).reversed()
                .thenComparing(share -> share.essence().id().toString()));
        long remaining = totalValue - assigned;
        for (int i = 0; i < remaining; i++) {
            result.merge(shares.get(i % shares.size()).essence(), 1L, Long::sum);
        }
        return result;
    }

    private record AllocationShare(EssenceDefinition essence, double remainder) {
    }

    private static double processMultiplier(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) {
            return ProceduralValuationSettings.CRAFTING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.SMELTING) {
            return ProceduralValuationSettings.SMELTING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.BLASTING) {
            return ProceduralValuationSettings.BLASTING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.SMOKING) {
            return ProceduralValuationSettings.SMOKING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.CAMPFIRE_COOKING) {
            return ProceduralValuationSettings.CAMPFIRE_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.STONECUTTING) {
            return ProceduralValuationSettings.STONECUTTING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.SMITHING) {
            return ProceduralValuationSettings.SMITHING_PROCESS_MULTIPLIER;
        }
        return ProceduralValuationSettings.UNKNOWN_PROCESS_MULTIPLIER;
    }

    private static String recipeTypeName(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) {
            return "crafting";
        }
        if (type == RecipeType.SMELTING) {
            return "smelting";
        }
        if (type == RecipeType.BLASTING) {
            return "blasting";
        }
        if (type == RecipeType.SMOKING) {
            return "smoking";
        }
        if (type == RecipeType.CAMPFIRE_COOKING) {
            return "campfire";
        }
        if (type == RecipeType.STONECUTTING) {
            return "stonecutting";
        }
        if (type == RecipeType.SMITHING) {
            return "smithing";
        }
        return type.toString();
    }

    private static double rarityMultiplier(Rarity rarity) {
        if (rarity == Rarity.UNCOMMON) {
            return ProceduralValuationSettings.UNCOMMON_MULTIPLIER;
        }
        if (rarity == Rarity.RARE) {
            return ProceduralValuationSettings.RARE_MULTIPLIER;
        }
        if (rarity == Rarity.EPIC) {
            return ProceduralValuationSettings.EPIC_MULTIPLIER;
        }
        return 1.0;
    }

    private static boolean looksNetherNative(String path) {
        return path.contains("ancient_debris")
                || path.contains("netherite_scrap")
                || path.contains("nether_quartz")
                || path.startsWith("quartz_")
                || path.equals("quartz")
                || path.contains("blaze_")
                || path.contains("ghast_")
                || path.contains("magma_")
                || path.contains("crimson_")
                || path.contains("warped_")
                || path.contains("nether_wart")
                || path.contains("wither_skeleton");
    }

    private static boolean looksEndNative(String path) {
        return path.contains("end_stone")
                || path.contains("chorus_")
                || path.contains("purpur_")
                || path.equals("shulker_shell")
                || path.equals("elytra")
                || path.equals("dragon_breath")
                || path.equals("dragon_egg");
    }

    private static boolean looksNetherMob(String path) {
        return path.contains("blaze")
                || path.contains("ghast")
                || path.contains("piglin")
                || path.contains("hoglin")
                || path.contains("magma_cube")
                || path.contains("wither_skeleton")
                || path.equals("wither")
                || path.contains("strider");
    }

    private static boolean looksEndMob(String path) {
        return path.contains("endermite")
                || path.contains("shulker")
                || path.contains("ender_dragon");
    }

    static double biologicalAcquisitionValue(double materialValue, double accessMultiplier,
                                             int eventOutput, double inputAndWearCost) {
        if (!Double.isFinite(materialValue) || materialValue < 0
                || !Double.isFinite(accessMultiplier) || accessMultiplier <= 0 || eventOutput <= 0
                || !Double.isFinite(inputAndWearCost) || inputAndWearCost < 0)
            throw new IllegalArgumentException("Invalid biological acquisition evidence");
        return (materialValue * accessMultiplier + inputAndWearCost) / eventOutput;
    }

    private static long clampValue(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 1L;
        }
        return Math.max(
                1L,
                Math.min(
                        ProceduralValuationSettings.MAX_VALUE,
                        Math.round(value)
                )
        );
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatLong(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static final class EvaluationContext {
        private final ProceduralValuationIndex index;
        // All measurements belong to this immutable generation, never to a world
        // tick or a later datapack reload. No previous-round graph nodes are cached here.
        private final Map<Item, Intrinsic> intrinsicMemo = new IdentityHashMap<>();
        private final Map<Item, Boolean> progressionOutputMemo = new IdentityHashMap<>();
        private final Map<Item, DownstreamInfo> downstreamMemo = new IdentityHashMap<>();
        private final Map<List<Item>, List<Item>> ingredientOrder = new IdentityHashMap<>();
        private final Map<Item, List<ProceduralValuationIndex.RecipeModel>> routingRecipes = new IdentityHashMap<>();
        private final Map<Item, EvaluationNode> memo = new IdentityHashMap<>();
        private final Map<Item, RouteWeights> routeMemo = new IdentityHashMap<>();
        private final Map<Item, DirectRouting> directRouteMemo = new IdentityHashMap<>();
        private final Map<Item, RoutingResolution> resolvedRouteMemo = new IdentityHashMap<>();
        private final Map<Item, EvaluationNode> conservationMemo = new IdentityHashMap<>();
        private final Set<Item> conservationVisiting = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Item, FamilyPayout> familyPayoutMemo = new IdentityHashMap<>();
        private Map<Item, EvaluationNode> previous = Map.of();
        private boolean solving, solved;
        private Item activeRoot;
        EvaluationContext(ProceduralValuationIndex index) { this.index = index; }
        ProceduralValuationIndex index() { return index; }
        Map<Item, EvaluationNode> memo() { return memo; }
        Map<Item, RouteWeights> routeMemo() { return routeMemo; }
        Map<Item, DirectRouting> directRouteMemo() { return directRouteMemo; }
        Map<Item, RoutingResolution> resolvedRouteMemo() { return resolvedRouteMemo; }
        Map<Item, EvaluationNode> conservationMemo() { return conservationMemo; }
        Set<Item> conservationVisiting() { return conservationVisiting; }
        Map<Item, FamilyPayout> familyPayoutMemo() { return familyPayoutMemo; }
    }

    private record FamilyPayout(long total, Map<EssenceDefinition, Long> routed,
                                boolean modeled, String status, String explanation) { }

    private record DirectRouting(
            RouteWeights structured,
            RouteWeights combined,
            ProceduralItemNomenclature.Analysis nomenclature,
            List<String> signals
    ) {
    }

    private record RoutingResolution(
            RouteWeights weights,
            ProceduralValuationResult.RoutingDiagnostics diagnostics
    ) {
    }

    private record EvaluationNode(
            long intrinsicValue,
            long acquisitionValue,
            Optional<ProceduralValuationResult.RecipeChoice> recipeChoice,
            ProceduralValuationResult.ProgressionBand progressionBand,
            double confidence,
            int recipeDepth,
            boolean knownAcquisition,
            boolean contextSensitive,
            double inferredProgressionScore,
            int progressionEvidenceCount,
            List<String> factors,
            Set<Item> dependencies
    ) {
        EvaluationNode(long intrinsicValue, long acquisitionValue,
                       Optional<ProceduralValuationResult.RecipeChoice> recipeChoice,
                       ProceduralValuationResult.ProgressionBand progressionBand, double confidence,
                       int recipeDepth, boolean knownAcquisition, boolean contextSensitive,
                       double inferredProgressionScore, int progressionEvidenceCount, List<String> factors) {
            this(intrinsicValue, acquisitionValue, recipeChoice, progressionBand, confidence, recipeDepth,
                    knownAcquisition, contextSensitive, inferredProgressionScore, progressionEvidenceCount,
                    factors, Set.of());
        }
    }

    private record Intrinsic(
            long value,
            long floorValue,
            boolean recognizedBucket,
            boolean explicitProgressionSignal,
            ProceduralValuationResult.ProgressionBand progressionBand,
            double confidence,
            List<String> factors
    ) {
    }

    private record DirectSource(
            double value,
            boolean knownAcquisition,
            ProceduralValuationResult.ProgressionBand progressionBand,
            double confidence,
            double inferredProgressionScore,
            int progressionEvidenceCount,
            boolean contextSensitive,
            List<String> factors,
            Set<Item> dependencies
    ) {
    }

    private record BlockDropPath(
            ProceduralValuationIndex.BlockDropSource source,
            double value,
            double sourceMultiplier,
            double rarityMultiplier,
            double quantityMultiplier,
            double conditionMultiplier,
            ProceduralProgressionIndex.ProgressionEvidence progressionEvidence,
            boolean prerequisiteKnown,
            double reusableWear,
            Set<Item> dependencies
    ) {
        boolean reliable() { return prerequisiteKnown && source.complexConditionCount() == 0; }
    }

    private record DropPath(
            ProceduralValuationIndex.DropSource source,
            double value,
            double difficultyMultiplier,
            double rarityMultiplier,
            double quantityMultiplier,
            double spawnMultiplier,
            double specialMultiplier,
            double conditionMultiplier,
            ProceduralValuationResult.ProgressionBand progressionBand,
            ProceduralProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
    }

    private record ContainerLootPath(
            ProceduralValuationIndex.ContainerLootSource source,
            double value,
            double contextMultiplier,
            double rarityMultiplier,
            double quantityMultiplier,
            double conditionMultiplier,
            ProceduralProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
    }

    private record FishingLootPath(
            ProceduralValuationIndex.FishingLootSource source,
            double value,
            double contextMultiplier,
            double rarityMultiplier,
            double quantityMultiplier,
            double conditionMultiplier,
            ProceduralProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
    }

    private record TradeAttempt(
            TradePath path,
            boolean contextSensitive
    ) {
    }

    private record TradePath(
            ProceduralTradeIndex.TradeSource source,
            double value,
            double levelMultiplier,
            double listingMultiplier,
            double stockMultiplier,
            double traderMultiplier,
            boolean fullyModeledCosts,
            ProceduralValuationResult.ProgressionBand progressionBand,
            double confidence,
            Set<Item> dependencies
    ) {
    }

    private record Renewability(
            double multiplier,
            double confidenceBonus,
            String label
    ) {
        private static final Renewability NONE = new Renewability(1.0, 0.0, "none");
    }

    private record RecipeAttempt(
            RecipeCandidate candidate,
            boolean contextSensitive
    ) {
    }

    private record IngredientSelection(
            Item item,
            EvaluationNode node,
            boolean contextSensitive
    ) {
        private static final IngredientSelection NONE = new IngredientSelection(null, null, false);
    }

    private record RecipeCandidate(
            ProceduralValuationIndex.RecipeModel recipe,
            long value,
            int ingredientSlots,
            int uniqueIngredients,
            int easyIngredients,
            int rareIngredients,
            int modSpecificIngredients,
            int depth,
            boolean reversible,
            boolean fullyModeledIngredients,
            ProceduralValuationResult.ProgressionBand progressionBand,
            double confidence,
            double inferredProgressionScore,
            int progressionEvidenceCount,
            Set<Item> dependencies
    ) {
        ProceduralValuationResult.RecipeChoice toChoice() {
            return new ProceduralValuationResult.RecipeChoice(
                    recipe.id(),
                    recipe.production() == null ? recipeTypeName(recipe.type()) : recipe.production().family(),
                    value,
                    recipe.outputCount(),
                    ingredientSlots,
                    uniqueIngredients,
                    easyIngredients,
                    rareIngredients,
                    modSpecificIngredients,
                    depth,
                    reversible
            );
        }
    }

    private record DownstreamInfo(
            int recipeCount,
            int significantCount,
            int crossModCount,
            double multiplier,
            List<ResourceLocation> examples
    ) {
        static final DownstreamInfo EMPTY = new DownstreamInfo(
                0,
                0,
                0,
                1.0,
                List.of()
        );
    }

    public record IndexSummary(
            int recipeCount,
            int skippedRecipeCount,
            int outputItemCount,
            int ingredientLinkCount,
            int entityLootTableCount,
            int dropSourceLinkCount,
            int blockLootTableCount,
            int blockDropSourceLinkCount,
            int containerLootTableCount,
            int containerLootSourceLinkCount,
            int fishingLootTableCount,
            int fishingLootSourceLinkCount,
            int tradeProfessionTableCount,
            int tradeListingCount,
            int tradeOfferCount,
            int advancementCount,
            int consideredAdvancementCount,
            int advancementTreeCount,
            int advancementReferenceCount,
            int advancementItemReferenceCount,
            int advancementEntityReferenceCount,
            int advancementDimensionReferenceCount
    ) {
    }

    private static final class RouteWeights {
        private final Map<EssenceDefinition, Double> values = new LinkedHashMap<>();

        void add(EssenceDefinition essence, double weight) {
            if (essence != null && Double.isFinite(weight) && weight > 0.0) {
                values.merge(essence, weight, Double::sum);
            }
        }

        void add(RouteWeights other) {
            addScaled(other, 1.0);
        }

        void addScaled(RouteWeights other, double scale) {
            if (other == null || !Double.isFinite(scale) || scale <= 0.0) {
                return;
            }
            other.values.forEach((essence, weight) -> add(essence, weight * scale));
        }

        void addNormalized(RouteWeights other, double totalWeight) {
            if (other == null || other.isEmpty() || !Double.isFinite(totalWeight) || totalWeight <= 0.0) {
                return;
            }
            double sum = other.totalWeight();
            if (!Double.isFinite(sum) || sum <= 0.0) {
                return;
            }
            other.values.forEach((essence, weight) -> add(essence, totalWeight * weight / sum));
        }

        double totalWeight() {
            return values.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        RouteWeights copy() {
            RouteWeights copy = new RouteWeights();
            copy.add(this);
            return copy;
        }

        void retainDominant() {
            Map<EssenceDefinition, Double> selected = EssenceRoutingPolicy.dominant(values,
                    essence -> essence.id().toString());
            values.clear();
            values.putAll(selected);
        }

        boolean isEmpty() {
            return values.isEmpty();
        }

        Set<Map.Entry<EssenceDefinition, Double>> entries() {
            return values.entrySet();
        }
    }
}
