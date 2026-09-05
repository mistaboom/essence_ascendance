package com.mistaboom.essence_ascendance.valuation;

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
 * Shared procedural economic analysis. Historical Shadow* names remain to keep
 * source/API churn small; generated defaults now consume the same cached results.
 * Explicit mapping overrides and dissolution eligibility remain separate layers.
 * No normalization to legacy yields or later progression/machine costs occurs.
 */
public final class ShadowValuationEngine {

    private static final Object INDEX_LOCK = new Object();

    private static volatile MinecraftServer indexedServer;
    private static volatile ShadowValuationIndex index;
    private static volatile List<ShadowValuationResult> cachedResults;

    private ShadowValuationEngine() {
    }

    public static ShadowValuationResult evaluate(
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
    public static List<ShadowValuationResult> evaluateAll(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("Server cannot be null");
        synchronized (INDEX_LOCK) {
            ShadowValuationIndex snapshot = ensureIndex(server);
            if (cachedResults != null) return cachedResults;
            EvaluationContext context = new EvaluationContext(snapshot);
            List<Item> items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                    .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
            solveAcquisitionGraph(items, context);
            List<ShadowValuationResult> complete = items.stream()
                    .map(item -> evaluateItem(snapshot, item, context)).toList();
            cachedResults = complete;
            return complete;
        }
    }

    public static void clear() {
        synchronized (INDEX_LOCK) {
            indexedServer = null;
            index = null;
            cachedResults = null;
        }
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
        for (int pass = 0; pass < ShadowValuationSettings.MAX_GRAPH_PASSES; pass++) {
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
                + ShadowValuationSettings.MAX_GRAPH_PASSES + " passes; live generation was NOT replaced");
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
            ShadowConservationMath.Plan<Item> plan = context.index().conservationPlan(item);
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
                    factors = new ArrayList<>(intrinsic(member).factors());
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

    private static ShadowValuationResult evaluateItem(
            ShadowValuationIndex snapshot,
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

        DownstreamInfo downstream = downstreamInfo(item, snapshot);
        double downstreamMultiplier = conservationDownstreamMultiplier(item, snapshot, downstream);
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
        ShadowValuationResult.RoutingDiagnostics diagnostics = routing.diagnostics();
        if (!diagnostics.nameHints().isEmpty()) {
            factors.add("Nomenclature hint [" + diagnostics.nameHintSource() + "]: "
                    + String.join("/", diagnostics.nameHints())
                    + " (routing only; no price, tier, rarity or acquisition-confidence boost)");
        }
        factors.add("Essence routing: " + String.join(" + ", diagnostics.evidence())
                + "; routing confidence " + diagnostics.confidence().name());
        if (diagnostics.evidence().contains("neutral_fallback")) {
            factors.add("Essence routing: no functional evidence found; neutral six-Essence fallback"
                    + " used before any weak acquisition-context vote");
        }

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
        confidence = ShadowValuationConfidence.bound(confidence, modeledAcquisition);
        if (!modeledAcquisition) factors.add("Economic confidence capped LOW: acquisition is unresolved; "
                + "classification, downstream uses and advancements cannot certify its price");

        return new ShadowValuationResult(
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
                (int) snapshot.containerLootSources(item).stream().filter(ShadowValuationIndex.ContainerLootSource::archaeology).count(),
                familyPayout == null ? "not_applicable" : familyPayout.status()
        );
    }

    private static FamilyPayout resolveFamilyPayout(Item item, EvaluationContext context) {
        List<Item> family = context.index().conservationGroup(item);
        if (family.size() < 2) return null;
        FamilyPayout cached = context.familyPayoutMemo().get(item);
        if (cached != null) return cached;
        ShadowConservationMath.Plan<Item> plan = context.index().conservationPlan(item);
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
            demand = Math.max(demand, downstreamInfo(member, context.index()).multiplier());
            if (!hasKnownAnchor || local.knownAcquisition()) {
                perUnit = Math.min(perUnit, (double) local.acquisitionValue() / units);
            }
        }
        long unitValue = ShadowConservationMath.unitPayout(perUnit * demand, maximumUnits,
                ShadowValuationSettings.MAX_VALUE);
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
        ShadowValuationIndex index = context.index();
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
                ShadowValuationResult.ProgressionBand.max(
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
            ShadowValuationIndex index,
            DownstreamInfo own
    ) {
        double multiplier = own.multiplier();
        for (Item member : index.conservationGroup(item)) {
            if (member == item) {
                continue;
            }
            multiplier = Math.max(multiplier, downstreamInfo(member, index).multiplier());
        }
        return multiplier;
    }

    public static IndexSummary rebuild(MinecraftServer server) {
        synchronized (INDEX_LOCK) {
            ShadowValuationIndex built = ShadowValuationIndex.build(server);
            indexedServer = server;
            index = built;
            cachedResults = null;
            return toPublicSummary(index.summary());
        }
    }

    public static IndexSummary summary(MinecraftServer server) {
        return toPublicSummary(ensureIndex(server).summary());
    }

    private static IndexSummary toPublicSummary(ShadowValuationIndex.Summary summary) {
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

    private static ShadowValuationIndex ensureIndex(MinecraftServer server) {
        ShadowValuationIndex current = index;
        if (current != null && indexedServer == server) {
            return current;
        }
        synchronized (INDEX_LOCK) {
            if (index == null || indexedServer != server) {
                ShadowValuationIndex built = ShadowValuationIndex.build(server);
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
        if (context.solving && !visiting.isEmpty()) {
            EvaluationNode input = context.previous.get(item);
            if (input != null && !input.dependencies().contains(context.activeRoot)) return input;
            Intrinsic base = intrinsic(item);
            return new EvaluationNode(base.value(), base.value(), Optional.empty(), base.progressionBand(),
                    Math.min(0.42, base.confidence()), 0, false, input != null, 0.0, 0,
                    List.of("Unresolved graph input; cannot establish acquisition evidence"), Set.of(item));
        }
        EvaluationNode memoized = context.memo().get(item);
        if (memoized != null) {
            return memoized;
        }

        Intrinsic intrinsic = intrinsic(item);

        if ((!context.solving && depth >= ShadowValuationSettings.MAX_RECIPE_DEPTH) || visiting.contains(item)) {
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
        List<RecipeCandidate> recipeCandidates = new ArrayList<>();
        boolean contextSensitive = directSource.contextSensitive();

        for (ShadowValuationIndex.RecipeModel recipe : context.index().recipesProducing(item)) {
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
        Optional<ShadowValuationResult.RecipeChoice> recipeChoice = Optional.empty();
        ShadowValuationResult.ProgressionBand progression = directSource.progressionBand();
        double confidence = directSource.confidence();
        double inferredProgressionScore = directSource.inferredProgressionScore();
        int progressionEvidenceCount = directSource.progressionEvidenceCount();
        Set<Item> selectedDependencies = directSource.dependencies();
        List<String> factors = new ArrayList<>();
        factors.addAll(intrinsic.factors());
        factors.addAll(directSource.factors());

        if (cheapestRecipe != null) {
            progression = ShadowValuationResult.ProgressionBand.max(
                    progression,
                    cheapestRecipe.progressionBand()
            );

            boolean directIsKnown = directSource.knownAcquisition();
            boolean recipeIsKnown = cheapestRecipe.fullyModeledIngredients();
            if ((!directIsKnown && (recipeIsKnown || cheapestReliableRecipe == null))
                    || (recipeIsKnown && cheapestRecipe.value() <= directSource.value())) {
                acquisition = cheapestRecipe.value();
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

        ShadowProgressionIndex.ProgressionEvidence itemProgression =
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
                .filter(ShadowValuationResult.RecipeChoice::reversibleTransform)
                .isPresent()
                ? 1L
                : intrinsic.floorValue();
        long acquisitionValue = clampValue(Math.max(acquisitionFloor, acquisition));
        int resolvedDepth = recipeChoice
                .map(ShadowValuationResult.RecipeChoice::depth)
                .orElse(0);
        boolean resolvedContextSensitive = !context.solving && contextSensitive;
        Set<Item> resultDependencies = new LinkedHashSet<>(selectedDependencies);
        resultDependencies.add(item);
        EvaluationNode result = new EvaluationNode(
                intrinsic.value(),
                acquisitionValue,
                recipeChoice,
                progression,
                ShadowValuationConfidence.bound(confidence, knownAcquisition),
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
            ShadowValuationIndex.RecipeModel recipe,
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
        double confidence = 0.91;
        boolean allIngredientsKnown = true;
        ShadowValuationResult.ProgressionBand progression =
                ShadowValuationResult.ProgressionBand.OVERWORLD;

        for (ShadowValuationIndex.IngredientChoice ingredient : recipe.ingredients()) {
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
            ingredientTotal += child.acquisitionValue();
            uniqueChosen.add(chosen);
            if (child.acquisitionValue() <= ShadowValuationSettings.EASY_INGREDIENT_THRESHOLD) {
                easy++;
            }
            if (child.acquisitionValue() >= ShadowValuationSettings.RARE_INGREDIENT_THRESHOLD) {
                rare++;
            }
            ResourceLocation chosenId = BuiltInRegistries.ITEM.getKey(chosen);
            if (chosenId != null && !"minecraft".equals(chosenId.getNamespace())) {
                modSpecific++;
            }
            progression = ShadowValuationResult.ProgressionBand.max(
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
                    ShadowValuationSettings.UNIQUE_INGREDIENT_CAP,
                    Math.max(0, uniqueChosen.size() - 1)
                            * ShadowValuationSettings.UNIQUE_INGREDIENT_STEP
            );
            // Ingredient rarity and mod namespace are diagnostic only here.
            // Their acquisition cost is already present in ingredientTotal, so
            // multiplying them again would double-count scarcity/progression.
            complexityMultiplier += Math.min(
                    ShadowValuationSettings.RECIPE_DEPTH_CAP,
                    maxChainDepth * ShadowValuationSettings.RECIPE_DEPTH_STEP
            );
        }

        double perOutput = ingredientTotal
                * processMultiplier
                * complexityMultiplier
                / Math.max(1, recipe.outputCount());

        ShadowProgressionIndex.ProgressionEvidence recipeProgression =
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
                ShadowValuationConfidence.bound(Math.min(0.91, confidence + 0.10), allIngredientsKnown),
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

        List<Item> shortlist = alternatives.stream().distinct()
                .sorted(Comparator.comparing(candidate -> BuiltInRegistries.ITEM.getKey(candidate).toString()))
                .toList();

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
        ShadowValuationIndex index = context.index();
        double value = intrinsic.value();
        ItemStack stack = new ItemStack(item);
        boolean oreSource = isRecognizedOreSource(item, stack);
        boolean known = oreSource;
        boolean conditionalFallbackSelected = false;
        boolean contextSensitive = false;
        Set<Item> dependencies = Set.of();
        double confidence = intrinsic.confidence();
        ShadowValuationResult.ProgressionBand progression = intrinsic.progressionBand();
        ShadowProgressionIndex.ProgressionEvidence selectedProgression =
                ShadowProgressionIndex.ProgressionEvidence.NONE;
        List<String> factors = new ArrayList<>();

        if (oreSource && stack.is(ShadowValuationTags.ORE_RATE_DENSE)) {
            value *= ShadowValuationSettings.ORE_RATE_DENSE_MULTIPLIER;
            confidence += 0.05;
            factors.add("Ore yield rate: dense -> x" + format(ShadowValuationSettings.ORE_RATE_DENSE_MULTIPLIER));
        } else if (oreSource && stack.is(ShadowValuationTags.ORE_RATE_SPARSE)) {
            value *= ShadowValuationSettings.ORE_RATE_SPARSE_MULTIPLIER;
            confidence += 0.05;
            factors.add("Ore yield rate: sparse -> x" + format(ShadowValuationSettings.ORE_RATE_SPARSE_MULTIPLIER));
        } else if (oreSource && stack.is(ShadowValuationTags.ORE_RATE_SINGULAR)) {
            confidence += 0.04;
            factors.add("Ore yield rate: singular");
        }

        if (oreSource && item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block.defaultBlockState().is(ShadowValuationTags.NEEDS_DIAMOND_TOOL)) {
                value *= ShadowValuationSettings.NEEDS_DIAMOND_TOOL_MULTIPLIER;
                confidence += 0.06;
                factors.add("Harvest gate: diamond-tier tool -> x" + format(ShadowValuationSettings.NEEDS_DIAMOND_TOOL_MULTIPLIER));
            } else if (block.defaultBlockState().is(ShadowValuationTags.NEEDS_IRON_TOOL)) {
                value *= ShadowValuationSettings.NEEDS_IRON_TOOL_MULTIPLIER;
                confidence += 0.06;
                factors.add("Harvest gate: iron-tier tool -> x" + format(ShadowValuationSettings.NEEDS_IRON_TOOL_MULTIPLIER));
            } else if (block.defaultBlockState().is(ShadowValuationTags.NEEDS_STONE_TOOL)) {
                value *= ShadowValuationSettings.NEEDS_STONE_TOOL_MULTIPLIER;
                confidence += 0.05;
                factors.add("Harvest gate: stone-tier tool -> x" + format(ShadowValuationSettings.NEEDS_STONE_TOOL_MULTIPLIER));
            }
        }

        List<BlockDropPath> blockPaths = index.blockDropSources(item).stream()
                .map(source -> {
                    Block sourceBlock = BuiltInRegistries.BLOCK.getOptional(source.blockId()).orElse(null);
                    ShadowProgressionIndex.ProgressionEvidence sourceProgression =
                            sourceBlock == null
                                    ? ShadowProgressionIndex.ProgressionEvidence.NONE
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
                ShadowValuationIndex.BlockDropSource source = selectedBlock.source();
                selectedProgression = selectedBlock.progressionEvidence();
                confidence = intrinsic.confidence() + (reliable ? 0.18 : 0.04);
                progression = ShadowValuationResult.ProgressionBand.max(
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

        List<DropPath> dropPaths = index.dropSources(item).stream()
                .map(source -> evaluateDropPath(
                        Math.max(intrinsic.value(), ShadowValuationSettings.MOB_DROP_BASE),
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
                ShadowValuationIndex.DropSource source = selectedDrop.source();
                selectedProgression = selectedDrop.progressionEvidence();
                confidence = intrinsic.confidence() + (reliable ? 0.17 : 0.04);
                progression = ShadowValuationResult.ProgressionBand.max(
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
                        Math.max(intrinsic.value(), ShadowValuationSettings.CONTAINER_LOOT_BASE),
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
                ShadowValuationIndex.ContainerLootSource source = selectedContainer.source();
                selectedProgression = selectedContainer.progressionEvidence();
                confidence = intrinsic.confidence() + (reliable ? 0.16 : 0.04);
                progression = ShadowValuationResult.ProgressionBand.max(
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
                        Math.max(intrinsic.value(), ShadowValuationSettings.FISHING_LOOT_BASE),
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
                ShadowValuationIndex.FishingLootSource source = selectedFishing.source();
                selectedProgression = selectedFishing.progressionEvidence();
                confidence = intrinsic.confidence() + (reliable ? 0.19 : 0.04);
                progression = ShadowValuationResult.ProgressionBand.max(
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
        for (ShadowTradeIndex.TradeSource source : index.tradeSources(item)) {
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
                progression = ShadowValuationResult.ProgressionBand.max(
                        progression,
                        selectedTrade.progressionBand()
                );
                // A trade is its own acquisition path. Do not accidentally carry
                // advancement evidence from a previously considered block/mob/container
                // source into the selected trade path. Trade-cost progression is already
                // propagated through the recursively valued cost items.
                selectedProgression = ShadowProgressionIndex.ProgressionEvidence.NONE;

                ShadowTradeIndex.TradeSource source = selectedTrade.source();
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
            ShadowTradeIndex.TradeSource source,
            EvaluationContext context,
            Set<Item> visiting,
            int depth
    ) {
        double costTotal = 0.0;
        boolean fullyModeled = true;
        boolean contextSensitive = false;
        double confidence = 0.84;
        Set<Item> dependencies = new LinkedHashSet<>();
        ShadowValuationResult.ProgressionBand progression =
                ShadowValuationResult.ProgressionBand.OVERWORLD;

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
            progression = ShadowValuationResult.ProgressionBand.max(
                    progression,
                    child.progressionBand()
            );
        }

        if (costTotal <= 0.0) {
            return new TradeAttempt(null, contextSensitive);
        }

        double levelMultiplier = Math.min(
                ShadowValuationSettings.TRADE_LEVEL_MAX_MULTIPLIER,
                1.0 + Math.max(0, source.level() - 1) * ShadowValuationSettings.TRADE_LEVEL_STEP
        );
        double listingRatio = Math.max(
                1.0,
                source.listingPoolSize() / 2.0
        );
        double listingMultiplier = Math.min(
                ShadowValuationSettings.TRADE_LISTING_RARITY_MAX_MULTIPLIER,
                Math.pow(listingRatio, ShadowValuationSettings.TRADE_LISTING_RARITY_EXPONENT)
        );
        double stockMultiplier = Math.min(
                ShadowValuationSettings.TRADE_LOW_STOCK_MAX_MULTIPLIER,
                Math.pow(12.0 / Math.max(1.0, source.maxUses()), 0.08)
        );
        stockMultiplier = Math.max(1.0, stockMultiplier);
        double traderMultiplier = source.wandering()
                ? ShadowValuationSettings.WANDERING_TRADER_MULTIPLIER
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
                        ShadowValuationConfidence.bound(Math.min(0.92, confidence + 0.08), fullyModeled),
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
        if (stack.is(ShadowValuationTags.ORES)
                || stack.is(ShadowValuationTags.ORES_IN_STONE)
                || stack.is(ShadowValuationTags.ORES_IN_DEEPSLATE)
                || stack.is(ShadowValuationTags.ORES_IN_NETHERRACK)
                || stack.is(ShadowValuationTags.ORES_IN_END_STONE)) {
            return true;
        }

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block.defaultBlockState().is(ShadowValuationTags.BLOCK_ORES)
                    || block.defaultBlockState().is(ShadowValuationTags.BLOCK_ORES_IN_STONE)
                    || block.defaultBlockState().is(ShadowValuationTags.BLOCK_ORES_IN_DEEPSLATE)
                    || block.defaultBlockState().is(ShadowValuationTags.BLOCK_ORES_IN_NETHERRACK)
                    || block.defaultBlockState().is(ShadowValuationTags.BLOCK_ORES_IN_END_STONE)) {
                return true;
            }
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return itemId != null && itemId.getPath().contains("ancient_debris");
    }

    private static BlockDropPath evaluateBlockDropPath(
            double baseValue,
            ShadowValuationResult.ProgressionBand intrinsicProgression,
            ShadowValuationIndex.BlockDropSource source,
            ShadowProgressionIndex.ProgressionEvidence progressionEvidence,
            EvaluationContext context, Set<Item> visiting, int depth
    ) {
        double sourceMultiplier = source.sourceMultiplier();
        if (intrinsicProgression.rank() >= source.progressionBand().rank()) {
            if (source.progressionBand() == ShadowValuationResult.ProgressionBand.NETHER) {
                sourceMultiplier /= ShadowValuationSettings.NETHER_MULTIPLIER;
            } else if (source.progressionBand() == ShadowValuationResult.ProgressionBand.END) {
                sourceMultiplier /= ShadowValuationSettings.END_MULTIPLIER;
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
        if (source.silkTouchRequired()) sourceMultiplier *= ShadowValuationSettings.SILK_TOUCH_HARVEST_MULTIPLIER;
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
            ShadowValuationIndex.DropSource source,
            ShadowProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
        double difficultyMultiplier = 1.0 + Math.min(
                ShadowValuationSettings.MOB_DIFFICULTY_MAX_MULTIPLIER - 1.0,
                Math.log1p(Math.max(0.0, source.difficultyScore())) * 0.34
        );
        double rarityMultiplier = probabilityRarityMultiplier(source.estimatedChance());
        double quantityMultiplier = quantityMultiplier(source.expectedCount());
        double bossMultiplier = source.bossScale()
                ? ShadowValuationSettings.BOSS_SCALE_MULTIPLIER
                : 1.0;
        double spawnMultiplier = Math.max(0.10, source.spawnAvailabilityMultiplier());
        double specialMultiplier = Math.max(0.10, source.specialSourceMultiplier());
        double conditionMultiplier = unresolvedConditionMultiplier(source.complexConditionCount());

        String entityPath = source.entityId().getPath().toLowerCase(Locale.ROOT);
        ShadowValuationResult.ProgressionBand progression;
        if (source.bossScale()) {
            progression = ShadowValuationResult.ProgressionBand.BOSS_SCALE;
        } else if (looksNetherMob(entityPath)) {
            progression = ShadowValuationResult.ProgressionBand.NETHER;
        } else if (looksEndMob(entityPath)) {
            progression = ShadowValuationResult.ProgressionBand.END;
        } else {
            progression = ShadowValuationResult.ProgressionBand.OVERWORLD;
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
            ShadowValuationResult.ProgressionBand intrinsicProgression,
            ShadowValuationIndex.ContainerLootSource source,
            ShadowProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
        double contextMultiplier = source.contextMultiplier();
        if (intrinsicProgression.rank() >= source.progressionBand().rank()) {
            if (source.progressionBand() == ShadowValuationResult.ProgressionBand.NETHER) {
                contextMultiplier /= ShadowValuationSettings.NETHER_MULTIPLIER;
            } else if (source.progressionBand() == ShadowValuationResult.ProgressionBand.END) {
                contextMultiplier /= ShadowValuationSettings.END_MULTIPLIER;
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
            ShadowValuationResult.ProgressionBand intrinsicProgression,
            ShadowValuationIndex.FishingLootSource source,
            ShadowProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
        double contextMultiplier = source.contextMultiplier();
        if (intrinsicProgression.rank() >= source.progressionBand().rank()) {
            if (source.progressionBand() == ShadowValuationResult.ProgressionBand.NETHER) {
                contextMultiplier /= ShadowValuationSettings.NETHER_MULTIPLIER;
            } else if (source.progressionBand() == ShadowValuationResult.ProgressionBand.END) {
                contextMultiplier /= ShadowValuationSettings.END_MULTIPLIER;
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
                ShadowValuationSettings.DROP_RARITY_MAX_MULTIPLIER,
                Math.pow(1.0 / chance, ShadowValuationSettings.DROP_RARITY_EXPONENT)
        );
    }

    private static double quantityMultiplier(double expectedCount) {
        return Math.max(
                ShadowValuationSettings.DROP_QUANTITY_MIN_MULTIPLIER,
                1.0 / Math.sqrt(Math.max(1.0, expectedCount))
        );
    }

    private static double unresolvedConditionMultiplier(int complexConditionCount) {
        if (complexConditionCount <= 0) {
            return 1.0;
        }
        return Math.pow(
                ShadowValuationSettings.UNRESOLVED_SOURCE_CONDITION_MULTIPLIER,
                Math.min(4, complexConditionCount)
        );
    }

    private static Renewability renewability(Item item, ShadowValuationIndex index) {
        ItemStack stack = new ItemStack(item);
        Renewability best = Renewability.NONE;

        if (stack.is(ShadowValuationTags.CROPS)) {
            best = strongerRenewability(best, new Renewability(
                    ShadowValuationSettings.CROP_RENEWABLE_REMAINDER,
                    0.12,
                    "growable crop"
            ));
        }
        if (stack.is(ShadowValuationTags.SEEDS)) {
            best = strongerRenewability(best, new Renewability(
                    ShadowValuationSettings.SEED_RENEWABLE_REMAINDER,
                    0.10,
                    "replantable seed"
            ));
        }
        if (stack.is(ShadowValuationTags.SAPLINGS)) {
            best = strongerRenewability(best, new Renewability(
                    ShadowValuationSettings.SAPLING_RENEWABLE_REMAINDER,
                    0.10,
                    "renewable sapling"
            ));
        }
        if (stack.is(ShadowValuationTags.LOGS)) {
            best = strongerRenewability(best, new Renewability(
                    ShadowValuationSettings.LOG_RENEWABLE_REMAINDER,
                    0.09,
                    "tree-grown log"
            ));
        }
        if (stack.is(ShadowValuationTags.LEAVES)) {
            best = strongerRenewability(best, new Renewability(
                    ShadowValuationSettings.LEAF_RENEWABLE_REMAINDER,
                    0.08,
                    "tree-grown leaves"
            ));
        }

        if (index != null) {
            double bestRepeatableMobChance = index.dropSources(item).stream()
                    .filter(source -> source.complexConditionCount() == 0)
                    .filter(ShadowValuationIndex.DropSource::repeatableSpawn)
                    .filter(source -> !source.bossScale())
                    .filter(source -> source.specialSourceMultiplier() <= 1.000001)
                    .mapToDouble(ShadowValuationIndex.DropSource::estimatedChance)
                    .max()
                    .orElse(0.0);
            if (bestRepeatableMobChance > 0.0) {
                double remainder;
                if (bestRepeatableMobChance >= 0.50) {
                    remainder = ShadowValuationSettings.COMMON_MOB_DROP_RENEWABLE_REMAINDER;
                } else if (bestRepeatableMobChance >= 0.10) {
                    remainder = ShadowValuationSettings.UNCOMMON_MOB_DROP_RENEWABLE_REMAINDER;
                } else if (bestRepeatableMobChance >= 0.025) {
                    remainder = ShadowValuationSettings.RARE_MOB_DROP_RENEWABLE_REMAINDER;
                } else {
                    remainder = ShadowValuationSettings.VERY_RARE_MOB_DROP_RENEWABLE_REMAINDER;
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
                        ShadowValuationSettings.FISHING_RENEWABLE_REMAINDER,
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
        return 1.0 + clamped * (ShadowValuationSettings.ADVANCEMENT_PROGRESSION_MAX_MULTIPLIER - 1.0);
    }

    private static Intrinsic intrinsic(Item item) {
        ItemStack stack = new ItemStack(item);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        long base = ShadowValuationSettings.DEFAULT_BASE;
        boolean recognized = false;
        boolean explicitProgression = false;
        double confidence = 0.24;
        List<String> factors = new ArrayList<>();

        if (isRecognizedOreSource(item, stack)) {
            base = Math.max(base, ShadowValuationSettings.ORE_BASE);
            recognized = true;
            confidence += 0.22;
            factors.add("Bucket: c:ores/source ore -> Gathering-biased resource baseline");
        }
        if (stack.is(ShadowValuationTags.RAW_MATERIALS)) {
            base = Math.max(base, ShadowValuationSettings.RAW_MATERIAL_BASE);
            recognized = true;
            confidence += 0.20;
            factors.add("Bucket: c:raw_materials");
        }
        if (stack.is(ShadowValuationTags.INGOTS)) {
            base = Math.max(base, ShadowValuationSettings.INGOT_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: c:ingots");
        }
        if (stack.is(ShadowValuationTags.GEMS)) {
            base = Math.max(base, ShadowValuationSettings.GEM_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: c:gems");
        }
        if (stack.is(ShadowValuationTags.NUGGETS)) {
            base = Math.max(base, ShadowValuationSettings.NUGGET_BASE);
            recognized = true;
            confidence += 0.16;
            factors.add("Bucket: c:nuggets");
        }
        if (stack.is(ShadowValuationTags.STORAGE_BLOCKS)) {
            base = Math.max(base, ShadowValuationSettings.STORAGE_BLOCK_BASE);
            recognized = true;
            confidence += 0.16;
            factors.add("Bucket: c:storage_blocks");
        }
        if (stack.is(ShadowValuationTags.FOODS) || stack.has(DataComponents.FOOD)) {
            base = Math.max(base, ShadowValuationSettings.FOOD_BASE);
            recognized = true;
            confidence += 0.14;
            factors.add("Bucket: food tag/component");
        }
        if (stack.is(ShadowValuationTags.MINING_TOOLS) || item instanceof DiggerItem
                || item instanceof ShearsItem || item instanceof BrushItem || item instanceof FishingRodItem) {
            base = Math.max(base, ShadowValuationSettings.TOOL_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: harvesting tool tag/runtime class");
        }
        if (stack.is(ShadowValuationTags.MELEE_WEAPONS)) {
            base = Math.max(base, ShadowValuationSettings.WEAPON_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: c:tools/melee_weapon");
        }
        if (stack.is(ShadowValuationTags.RANGED_WEAPONS)) {
            base = Math.max(base, ShadowValuationSettings.WEAPON_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: c:tools/ranged_weapon");
        }
        if (stack.is(ShadowValuationTags.ARMORS) || item instanceof ArmorItem || item instanceof ShieldItem) {
            base = Math.max(base, ShadowValuationSettings.ARMOR_BASE);
            recognized = true;
            confidence += 0.18;
            factors.add("Bucket: armor/equipment");
        }
        if (stack.is(ShadowValuationTags.REDSTONE_DUSTS)) {
            base = Math.max(base, ShadowValuationSettings.REDSTONE_BASE);
            recognized = true;
            confidence += 0.14;
            factors.add("Bucket: redstone/automation material");
        }
        if (item instanceof BlockItem && !recognized) {
            base = Math.max(base, ShadowValuationSettings.BLOCK_BASE);
            confidence += 0.05;
            factors.add("Shape: block item baseline");
        }

        if (item instanceof SwordItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof MaceItem || item instanceof ArrowItem) {
            base = Math.max(base, ShadowValuationSettings.WEAPON_BASE);
            recognized = true;
            confidence += 0.10;
            factors.add("Shape: weapon class");
        }

        if (item instanceof BoatItem || item instanceof MinecartItem || item instanceof ElytraItem
                || item instanceof EnderpearlItem || item instanceof FireworkRocketItem
                || (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof BaseRailBlock)) {
            base = Math.max(base, ShadowValuationSettings.TRANSPORT_BASE);
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

        ShadowValuationResult.ProgressionBand progression =
                ShadowValuationResult.ProgressionBand.OVERWORLD;

        boolean netherrackOre = stack.is(ShadowValuationTags.ORES_IN_NETHERRACK);
        boolean endOre = stack.is(ShadowValuationTags.ORES_IN_END_STONE);
        boolean deepslateOre = stack.is(ShadowValuationTags.ORES_IN_DEEPSLATE);

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            netherrackOre |= block.defaultBlockState().is(ShadowValuationTags.BLOCK_ORES_IN_NETHERRACK);
            endOre |= block.defaultBlockState().is(ShadowValuationTags.BLOCK_ORES_IN_END_STONE);
            deepslateOre |= block.defaultBlockState().is(ShadowValuationTags.BLOCK_ORES_IN_DEEPSLATE);
        }

        if (netherrackOre || looksNetherNative(path)) {
            base = clampValue(base * ShadowValuationSettings.NETHER_MULTIPLIER);
            progression = ShadowValuationResult.ProgressionBand.NETHER;
            explicitProgression = true;
            confidence += netherrackOre ? 0.15 : 0.05;
            factors.add(
                    netherrackOre
                            ? "Progression/source: c:ores_in_ground/netherrack -> Nether x"
                            + format(ShadowValuationSettings.NETHER_MULTIPLIER)
                            : "Progression/source: Nether-native identity heuristic -> x"
                            + format(ShadowValuationSettings.NETHER_MULTIPLIER)
            );
        }

        if (endOre || looksEndNative(path)) {
            base = clampValue(base * ShadowValuationSettings.END_MULTIPLIER);
            progression = ShadowValuationResult.ProgressionBand.END;
            explicitProgression = true;
            confidence += endOre ? 0.15 : 0.05;
            factors.add(
                    endOre
                            ? "Progression/source: c:ores_in_ground/end_stone -> End x"
                            + format(ShadowValuationSettings.END_MULTIPLIER)
                            : "Progression/source: End-native identity heuristic -> x"
                            + format(ShadowValuationSettings.END_MULTIPLIER)
            );
        }

        if (deepslateOre) {
            base = clampValue(base * ShadowValuationSettings.DEEPSLATE_MULTIPLIER);
            explicitProgression = true;
            confidence += 0.05;
            factors.add("Acquisition depth: deepslate ore -> x" + format(ShadowValuationSettings.DEEPSLATE_MULTIPLIER));
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

        if (isRecognizedOreSource(item, stack) || stack.is(ShadowValuationTags.RAW_MATERIALS)) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            signals.add("resource/ore");
        }
        if (stack.is(ShadowValuationTags.MINING_TOOLS)
                || stack.is(ShadowValuationTags.AXES) || stack.is(ShadowValuationTags.PICKAXES)
                || stack.is(ShadowValuationTags.SHOVELS) || stack.is(ShadowValuationTags.HOES)
                || item instanceof DiggerItem || item instanceof ShearsItem || item instanceof BrushItem) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            signals.add("harvesting_tool");
        }
        if (item instanceof FishingRodItem) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            signals.add("fishing_rod");
        }
        if (item instanceof ShieldItem) {
            structured.add(EssenceTypes.DEFENSE, 8.0);
            structured.add(EssenceTypes.VITALITY, 1.0);
            signals.add("shield");
        }
        if (stack.is(ShadowValuationTags.MELEE_WEAPONS) || stack.is(ShadowValuationTags.SWORDS)
                || item instanceof SwordItem || item instanceof TridentItem || item instanceof MaceItem) {
            structured.add(EssenceTypes.OFFENSE, 7.0);
            signals.add("melee_weapon");
        }
        if (stack.is(ShadowValuationTags.RANGED_WEAPONS) || stack.is(ShadowValuationTags.ARROWS)
                || item instanceof BowItem || item instanceof CrossbowItem || item instanceof ArrowItem) {
            structured.add(EssenceTypes.OFFENSE, 7.0);
            signals.add("ranged_weapon/projectile");
        }
        if (stack.is(ShadowValuationTags.ARMORS) || item instanceof ArmorItem) {
            structured.add(EssenceTypes.DEFENSE, 7.0);
            structured.add(EssenceTypes.VITALITY, 2.0);
            signals.add("armor");
        }
        if (stack.is(ShadowValuationTags.FOODS) || stack.has(DataComponents.FOOD)) {
            structured.add(EssenceTypes.VITALITY, 8.0);
            signals.add("food_tag/component");
        }
        if (stack.is(ShadowValuationTags.REDSTONE_DUSTS)) {
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
        if (block != null && block.defaultBlockState().is(ShadowValuationTags.CLIMBABLE)) {
            structured.add(EssenceTypes.MOBILITY, 7.0);
            signals.add("climbable_block");
        }
        if (block instanceof BaseRailBlock) {
            structured.add(EssenceTypes.MOBILITY, 8.0);
            signals.add("rail_block");
        }
        if (stack.is(ShadowValuationTags.BEDS) || block instanceof BedBlock
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
        if (stack.is(ShadowValuationTags.WOOL) || stack.is(ShadowValuationTags.WOOL_CARPETS)) {
            structured.add(EssenceTypes.DEFENSE, 3.0);
            structured.add(EssenceTypes.VITALITY, 2.0);
            structured.add(EssenceTypes.UTILITY, 1.0);
            signals.add("protective/comfort_textile");
        }
        if (stack.is(ShadowValuationTags.FENCES) || stack.is(ShadowValuationTags.FENCE_GATES)
                || stack.is(ShadowValuationTags.WALLS) || stack.is(ShadowValuationTags.DOORS)
                || stack.is(ShadowValuationTags.TRAPDOORS)) {
            structured.add(EssenceTypes.DEFENSE, 6.0);
            structured.add(EssenceTypes.UTILITY, 2.0);
            signals.add("physical_barrier");
        }
        if (stack.is(ShadowValuationTags.CROPS) || stack.is(ShadowValuationTags.SEEDS)
                || stack.is(ShadowValuationTags.SAPLINGS)) {
            structured.add(EssenceTypes.GATHERING, 4.0);
            structured.add(EssenceTypes.VITALITY, 3.0);
            signals.add("cultivation_material");
        }
        if (item instanceof BoneMealItem || stack.is(ShadowValuationTags.FERTILIZERS)) {
            structured.add(EssenceTypes.GATHERING, 7.0);
            structured.add(EssenceTypes.VITALITY, 1.0);
            signals.add("fertilizer_item/tag");
        }
        if (stack.is(ShadowValuationTags.FLOWERS) || stack.is(ShadowValuationTags.LEAVES)) {
            structured.add(EssenceTypes.VITALITY, 3.0);
            structured.add(EssenceTypes.GATHERING, 2.0);
            structured.add(EssenceTypes.UTILITY, 1.0);
            signals.add("living_plant_material");
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        ShadowItemNomenclature.Analysis name = ShadowItemNomenclature.analyze(
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
                ? ShadowValuationSettings.ROUTING_NAME_WEIGHT_WITHOUT_STRUCTURED
                : ShadowValuationSettings.ROUTING_NAME_WEIGHT_WITH_STRUCTURED;
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
            Item item, EvaluationNode node, ShadowValuationIndex snapshot, EvaluationContext context
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
            route.addNormalized(semantics, ShadowValuationSettings.ROUTING_CONSERVATION_WEIGHT);
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
            route.addNormalized(composition, ShadowValuationSettings.ROUTING_COMPOSITION_WEIGHT);
        }
        boolean neutral = route.isEmpty();
        if (neutral) {
            addNeutralRoute(route);
        }
        route.addNormalized(sources, neutral
                ? ShadowValuationSettings.ROUTING_ACQUISITION_WEIGHT_WITHOUT_DIRECT
                : ShadowValuationSettings.ROUTING_ACQUISITION_WEIGHT_WITH_DIRECT);

        List<String> evidence = new ArrayList<>();
        if (hasStructured) evidence.add("structured_function");
        if (hasName) evidence.add("name_hint");
        if (hasDownstream) evidence.add("downstream_recipes");
        if (hasComposition) evidence.add("recipe_composition");
        if (conserved) evidence.add("conservation_family");
        if (neutral) evidence.add("neutral_fallback");
        if (!sources.isEmpty()) evidence.add("weak_acquisition_context");
        ShadowValuationResult.ConfidenceBand confidence = hasStructured
                ? ShadowValuationResult.ConfidenceBand.HIGH
                : (hasDownstream || hasComposition)
                ? ShadowValuationResult.ConfidenceBand.MEDIUM
                : ShadowValuationResult.ConfidenceBand.LOW;
        // Cache each form's own lexical diagnostics but one identical final
        // weight vector. No new route recursion is introduced by this cache.
        for (Item member : family) {
            ShadowItemNomenclature.Analysis name = directRouting(member, context).nomenclature();
            ShadowValuationResult.RoutingDiagnostics diagnostics = new ShadowValuationResult.RoutingDiagnostics(
                    evidence, confidence, name.source(), name.matches(), List.copyOf(structuredSignals));
            context.resolvedRouteMemo().put(member, new RoutingResolution(route.copy(), diagnostics));
        }
        return context.resolvedRouteMemo().get(item);
    }

    private static RouteWeights semanticRoute(
            Item item,
            ShadowValuationIndex index,
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
        if (depth >= ShadowValuationSettings.MAX_ROUTING_DEPTH || !visiting.add(item)) {
            return route;
        }

        RouteWeights downstream = new RouteWeights();
        LinkedHashSet<ResourceLocation> seenRecipes = new LinkedHashSet<>();
        List<ShadowValuationIndex.RecipeUse> uses = new ArrayList<>(index.recipesUsing(item));
        uses.sort(Comparator.comparing((ShadowValuationIndex.RecipeUse use) -> use.recipe().id().toString()));

        int considered = 0;
        for (ShadowValuationIndex.RecipeUse use : uses) {
            ShadowValuationIndex.RecipeModel recipe = use.recipe();
            if (index.isReversibleTransform(recipe) || !seenRecipes.add(recipe.id())) {
                continue;
            }
            if (++considered > ShadowValuationSettings.MAX_ROUTING_DOWNSTREAM_RECIPES) {
                break;
            }

            Item output = recipe.outputItem();
            if (output == item) {
                continue;
            }

            RouteWeights outputRoute = directRoute(output, context);
            if (outputRoute.isEmpty() && depth + 1 < ShadowValuationSettings.MAX_ROUTING_DEPTH) {
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

            double vote = isProgressionOutput(output) ? 1.35 : 1.0;
            downstream.addNormalized(outputRoute, vote);
        }

        visiting.remove(item);

        if (!downstream.isEmpty()) {
            route.addNormalized(
                    downstream,
                    direct.isEmpty()
                            ? ShadowValuationSettings.ROUTING_DOWNSTREAM_WEIGHT_WITHOUT_DIRECT
                            : ShadowValuationSettings.ROUTING_DOWNSTREAM_WEIGHT_WITH_DIRECT
            );
        }

        if (topLevel) {
            context.routeMemo().put(item, route.copy());
        }
        return route;
    }

    private static RouteWeights recipeCompositionRoute(
            Item target,
            EvaluationNode node,
            ShadowValuationIndex index,
            EvaluationContext context
    ) {
        if (node.recipeChoice().isEmpty() || node.recipeChoice().get().reversibleTransform()) {
            return new RouteWeights();
        }

        ResourceLocation chosenRecipeId = node.recipeChoice().get().recipeId();
        ShadowValuationIndex.RecipeModel chosenRecipe = index.recipesProducing(target).stream()
                .filter(recipe -> recipe.id().equals(chosenRecipeId))
                .findFirst()
                .orElse(null);
        if (chosenRecipe == null) {
            return new RouteWeights();
        }

        RouteWeights composition = new RouteWeights();
        Set<Item> visiting = new LinkedHashSet<>();
        visiting.add(target);

        for (ShadowValuationIndex.IngredientChoice ingredient : chosenRecipe.ingredients()) {
            RouteWeights alternatives = new RouteWeights();
            int considered = 0;
            for (Item alternative : ingredient.alternatives()) {
                if (++considered > ShadowValuationSettings.MAX_ROUTING_ALTERNATIVES_PER_INGREDIENT) {
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

    private static void addNeutralRoute(RouteWeights route) {
        route.add(EssenceTypes.OFFENSE, 1.0);
        route.add(EssenceTypes.DEFENSE, 1.0);
        route.add(EssenceTypes.VITALITY, 1.0);
        route.add(EssenceTypes.MOBILITY, 1.0);
        route.add(EssenceTypes.GATHERING, 1.0);
        route.add(EssenceTypes.UTILITY, 1.0);
    }

    private static RouteWeights acquisitionRoute(Item item, ShadowValuationIndex index) {
        RouteWeights route = new RouteWeights();

        boolean bossScale = index.dropSources(item).stream()
                .anyMatch(source -> source.bossScale()
                        || source.specialSourceMultiplier() >= ShadowValuationSettings.PLAYER_SUMMONED_BOSS_MULTIPLIER);
        if (bossScale) {
            route.add(EssenceTypes.OFFENSE, 3.0);
            route.add(EssenceTypes.DEFENSE, 1.2);
            route.add(EssenceTypes.VITALITY, 1.2);
            route.add(EssenceTypes.UTILITY, 1.0);
        }

        boolean endContainer = index.containerLootSources(item).stream()
                .anyMatch(source -> source.progressionBand() == ShadowValuationResult.ProgressionBand.END);
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
            ShadowValuationIndex index
    ) {
        List<ShadowValuationIndex.RecipeUse> uses = index.recipesUsing(item);
        if (uses.isEmpty()) {
            return DownstreamInfo.EMPTY;
        }

        LinkedHashSet<ResourceLocation> uniqueRecipes = new LinkedHashSet<>();
        LinkedHashSet<ResourceLocation> examples = new LinkedHashSet<>();
        int significant = 0;
        int crossMod = 0;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        for (ShadowValuationIndex.RecipeUse use : uses) {
            ShadowValuationIndex.RecipeModel recipe = use.recipe();
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
            if (outputId != null && examples.size() < ShadowValuationSettings.MAX_DOWNSTREAM_EXAMPLES) {
                examples.add(outputId);
            }

            Intrinsic outputIntrinsic = intrinsic(recipe.outputItem());

            if (outputIntrinsic.value() >= ShadowValuationSettings.RARE_INGREDIENT_THRESHOLD
                    || isProgressionOutput(recipe.outputItem())) {
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
                + count * ShadowValuationSettings.DOWNSTREAM_USE_STEP
                + significant * ShadowValuationSettings.DOWNSTREAM_SIGNIFICANT_STEP
                + crossMod * ShadowValuationSettings.DOWNSTREAM_CROSS_MOD_STEP;
        multiplier = Math.min(
                ShadowValuationSettings.DOWNSTREAM_MAX_MULTIPLIER,
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

    private static boolean isProgressionOutput(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(ShadowValuationTags.ARMORS)
                || stack.is(ShadowValuationTags.MINING_TOOLS)
                || stack.is(ShadowValuationTags.MELEE_WEAPONS)
                || stack.is(ShadowValuationTags.RANGED_WEAPONS)
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
            addNeutralRoute(effective);
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
            return ShadowValuationSettings.CRAFTING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.SMELTING) {
            return ShadowValuationSettings.SMELTING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.BLASTING) {
            return ShadowValuationSettings.BLASTING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.SMOKING) {
            return ShadowValuationSettings.SMOKING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.CAMPFIRE_COOKING) {
            return ShadowValuationSettings.CAMPFIRE_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.STONECUTTING) {
            return ShadowValuationSettings.STONECUTTING_PROCESS_MULTIPLIER;
        }
        if (type == RecipeType.SMITHING) {
            return ShadowValuationSettings.SMITHING_PROCESS_MULTIPLIER;
        }
        return ShadowValuationSettings.UNKNOWN_PROCESS_MULTIPLIER;
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
            return ShadowValuationSettings.UNCOMMON_MULTIPLIER;
        }
        if (rarity == Rarity.RARE) {
            return ShadowValuationSettings.RARE_MULTIPLIER;
        }
        if (rarity == Rarity.EPIC) {
            return ShadowValuationSettings.EPIC_MULTIPLIER;
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

    private static long clampValue(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 1L;
        }
        return Math.max(
                1L,
                Math.min(
                        ShadowValuationSettings.MAX_VALUE,
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
        private final ShadowValuationIndex index;
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
        EvaluationContext(ShadowValuationIndex index) { this.index = index; }
        ShadowValuationIndex index() { return index; }
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
            ShadowItemNomenclature.Analysis nomenclature,
            List<String> signals
    ) {
    }

    private record RoutingResolution(
            RouteWeights weights,
            ShadowValuationResult.RoutingDiagnostics diagnostics
    ) {
    }

    private record EvaluationNode(
            long intrinsicValue,
            long acquisitionValue,
            Optional<ShadowValuationResult.RecipeChoice> recipeChoice,
            ShadowValuationResult.ProgressionBand progressionBand,
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
                       Optional<ShadowValuationResult.RecipeChoice> recipeChoice,
                       ShadowValuationResult.ProgressionBand progressionBand, double confidence,
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
            ShadowValuationResult.ProgressionBand progressionBand,
            double confidence,
            List<String> factors
    ) {
    }

    private record DirectSource(
            double value,
            boolean knownAcquisition,
            ShadowValuationResult.ProgressionBand progressionBand,
            double confidence,
            double inferredProgressionScore,
            int progressionEvidenceCount,
            boolean contextSensitive,
            List<String> factors,
            Set<Item> dependencies
    ) {
    }

    private record BlockDropPath(
            ShadowValuationIndex.BlockDropSource source,
            double value,
            double sourceMultiplier,
            double rarityMultiplier,
            double quantityMultiplier,
            double conditionMultiplier,
            ShadowProgressionIndex.ProgressionEvidence progressionEvidence,
            boolean prerequisiteKnown,
            double reusableWear,
            Set<Item> dependencies
    ) {
        boolean reliable() { return prerequisiteKnown && source.complexConditionCount() == 0; }
    }

    private record DropPath(
            ShadowValuationIndex.DropSource source,
            double value,
            double difficultyMultiplier,
            double rarityMultiplier,
            double quantityMultiplier,
            double spawnMultiplier,
            double specialMultiplier,
            double conditionMultiplier,
            ShadowValuationResult.ProgressionBand progressionBand,
            ShadowProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
    }

    private record ContainerLootPath(
            ShadowValuationIndex.ContainerLootSource source,
            double value,
            double contextMultiplier,
            double rarityMultiplier,
            double quantityMultiplier,
            double conditionMultiplier,
            ShadowProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
    }

    private record FishingLootPath(
            ShadowValuationIndex.FishingLootSource source,
            double value,
            double contextMultiplier,
            double rarityMultiplier,
            double quantityMultiplier,
            double conditionMultiplier,
            ShadowProgressionIndex.ProgressionEvidence progressionEvidence
    ) {
    }

    private record TradeAttempt(
            TradePath path,
            boolean contextSensitive
    ) {
    }

    private record TradePath(
            ShadowTradeIndex.TradeSource source,
            double value,
            double levelMultiplier,
            double listingMultiplier,
            double stockMultiplier,
            double traderMultiplier,
            boolean fullyModeledCosts,
            ShadowValuationResult.ProgressionBand progressionBand,
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
            ShadowValuationIndex.RecipeModel recipe,
            long value,
            int ingredientSlots,
            int uniqueIngredients,
            int easyIngredients,
            int rareIngredients,
            int modSpecificIngredients,
            int depth,
            boolean reversible,
            boolean fullyModeledIngredients,
            ShadowValuationResult.ProgressionBand progressionBand,
            double confidence,
            double inferredProgressionScore,
            int progressionEvidenceCount,
            Set<Item> dependencies
    ) {
        ShadowValuationResult.RecipeChoice toChoice() {
            return new ShadowValuationResult.RecipeChoice(
                    recipe.id(),
                    recipeTypeName(recipe.type()),
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

        boolean isEmpty() {
            return values.isEmpty();
        }

        Set<Map.Entry<EssenceDefinition, Double>> entries() {
            return values.entrySet();
        }
    }
}
