package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Function;

/** Capability-only witnesses for static native crafting and exact configured stacks.
 * Never modifies production completeness, material values, conservation or global acquisition.
 * Custom transforms, callbacks, opaque predicates and finite quest inventories remain unknown. */
public final class ConfiguredRecipeAccess {
    private static final int MAX_DEPTH = 32, MAX_VISITS = 4096;
    private final ConfigurationAccess.Resolver ordinary;
    private final Map<String, ResourceEvidence> resources;
    private final Set<String> constrained;
    private final Map<String, Integer> fuels;
    private final Map<String, String> remainders;
    private final Map<String, List<String>> tags;
    private final Function<JsonObject, ?> components;
    private final Map<String, List<Candidate>> producers = new TreeMap<>();
    // Component identity is immutable within this generation. Stage probes must not
    // repeatedly decode the same native item/recipe defaults in every ledger branch.
    private final Map<String, Object> defaultComponents = new HashMap<>();
    private final Map<JsonObject, Object> recipeComponents = new IdentityHashMap<>();
    private final Map<StackKey, ConfigurationAccess.Proof> proven = new HashMap<>();
    private final Map<String, ConfigurationAccess.Proof> setups = new HashMap<>();
    // Independent renewable facts are constant for this provider operation. Do not
    // rebuild their proofs inside every ingredient/fuel comparator and search branch.
    // Finite inventories, stage ceilings and ledger-dependent results stay uncached.
    private final Map<String, ConfigurationAccess.Proof> directSupplies = new HashMap<>();
    private List<Map.Entry<String, Integer>> orderedFuels;
    private int visits;
    private PlanChoices choices = new PlanChoices(List.of());
    private static final int MAX_PLANS = 128;
    private boolean conditionalExploration;
    private ProgressionBand sourceCeiling = ProgressionBand.APEX;
    private final LinkedHashMap<String, FiniteProof> explorationBills = new LinkedHashMap<>();
    /** Bounded whole-bill backtracking: a locally valid ingredient may be needed by a later slot. */
    private static final class PlanChoices {
        private final List<Integer> plan, used = new ArrayList<>(), sizes = new ArrayList<>();
        PlanChoices(List<Integer> plan) { this.plan = plan; }
        <T> List<T> one(List<T> alternatives) {
            if (alternatives.size() <= 1) return alternatives;
            int position = used.size(); int selected = position < plan.size() ? plan.get(position) : 0;
            if (selected >= alternatives.size()) selected = 0;
            used.add(selected); sizes.add(alternatives.size()); return List.of(alternatives.get(selected));
        }
        List<Integer> next() {
            for (int i = used.size() - 1; i >= 0; i--) if (used.get(i) + 1 < sizes.get(i)) {
                var next = new ArrayList<>(used.subList(0, i + 1)); next.set(i, next.get(i) + 1); return next;
            }
            return null;
        }
    }
    private record Candidate(String id, JsonObject definition, JsonObject output, double confidence, String station, int cookingTicks) { }
    private record StackKey(String item, Object components) { }

    public static ConfiguredRecipeAccess nativeCrafting(PackEvidenceContext context, Map<String, ResourceEvidence> resources) {
        return nativeCrafting(context, resources, Set.of());
    }
    /** Additional targets must be produced by an exact supported recipe rather than an
     * item-only source row whose possible stack components have not been established. */
    public static ConfiguredRecipeAccess nativeCrafting(PackEvidenceContext context, Map<String, ResourceEvidence> resources, Set<String> recipeOnly) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, context.server().registryAccess());
        var constrained = new HashSet<>(context.inputs().configurationConstrainedItems()); constrained.addAll(recipeOnly);
        return new ConfiguredRecipeAccess(context.inputs().production(), resources, context.inputs().itemTags(),
                constrained, definition -> {
                    // A ledger request can exceed one inventory stack. Quantity is validated/accounted
                    // separately; the native component codec needs a single representative stack.
                    var identity = definition.deepCopy(); identity.remove("count");
                    ItemStack stack = ItemStack.CODEC.parse(ops, identity).getOrThrow();
                    if (stack.isEmpty()) throw new IllegalArgumentException("Empty configured crafting output");
                    return nativeComponents(stack);
                }, nativeFuels(), nativeRemainders());
    }
    /** Component normalizer must retain item defaults, not merely the result's component patch. */
    public ConfiguredRecipeAccess(ProductionGraph graph, Map<String, ResourceEvidence> resources,
            Map<String, List<String>> tags, Set<String> constrained, Function<JsonObject, ?> components) {
        this(graph, resources, tags, constrained, components, Map.of(), Map.of());
    }
    public ConfiguredRecipeAccess(ProductionGraph graph, Map<String, ResourceEvidence> resources,
            Map<String, List<String>> tags, Set<String> constrained, Function<JsonObject, ?> components,
            Map<String, Integer> fuels, Map<String, String> remainders) {
        // The repeatable API admits renewable leaves only. requireFinite has a separate joint ledger.
        this.ordinary = new ConfigurationAccess.Resolver(renewableResources(resources), constrained);
        this.resources = resources; this.constrained = Set.copyOf(constrained);
        this.fuels = new TreeMap<>(fuels); this.remainders = Map.copyOf(remainders);
        this.tags = tags; this.components = components;
        if (graph == null) return;
        for (var process : graph.processes()) {
            if (!process.metadata().containsKey("effective_definition")) continue;
            var definition = process.effectiveDefinition();
            String projection = text(definition, "projection"), runtime = text(definition, "runtime_class");
            boolean nativeRecipe = projection.equals("native_shaped_public_fields") && runtime.equals("net.minecraft.world.item.crafting.ShapedRecipe")
                    || projection.equals("native_shapeless_public_fields") && runtime.equals("net.minecraft.world.item.crafting.ShapelessRecipe");
            nativeRecipe |= auditedHooklessCrafting(definition, projection, runtime);
            String station = ""; int cookingTicks = 0;
            boolean cooking = projection.equals("native_cooking_public_fields");
            if (cooking) {
                station = switch (runtime) {
                    case "net.minecraft.world.item.crafting.SmeltingRecipe" -> "minecraft:furnace";
                    case "net.minecraft.world.item.crafting.BlastingRecipe" -> "minecraft:blast_furnace";
                    case "net.minecraft.world.item.crafting.SmokingRecipe" -> "minecraft:smoker";
                    default -> "";
                };
                nativeRecipe = !station.isEmpty() && definition.has("cookingtime");
                if (nativeRecipe) cookingTicks = definition.get("cookingtime").getAsInt();
                if (cookingTicks < 1) continue;
                // Smoker/blast furnace consume native fuel at twice the furnace rate.
                if (!station.equals("minecraft:furnace")) cookingTicks = Math.multiplyExact(cookingTicks, 2);
            }
            if (!nativeRecipe || definition.has("custom_behavior_unresolved") || definition.has("variant_transform")
                    || process.metadata().containsKey("conditions_unresolved") || !process.resources().isEmpty()
                    || !definition.has("ingredients") || !definition.has("result")) continue;
            var output = definition.get("result");
            if (!output.isJsonObject() || !output.getAsJsonObject().has("id")) continue;
            var stack = output.getAsJsonObject();
            if (stack.has("chance") || stack.has("probability") || stack.has("tag")) continue;
            boolean table;
            if (cooking) table = false;
            else if (projection.equals("native_shaped_public_fields")) {
                if (!definition.has("width") || !definition.has("height")) continue;
                int width = definition.get("width").getAsInt(), height = definition.get("height").getAsInt();
                if (width < 1 || width > 3 || height < 1 || height > 3) continue;
                table = width > 2 || height > 2;
            } else table = definition.getAsJsonArray("ingredients").size() > 4;
            producers.computeIfAbsent(text(stack, "id"), ignored -> new ArrayList<>())
                    .add(new Candidate(process.id(), definition, stack, process.confidence(), table ? "minecraft:crafting_table" : station, cookingTicks));
        }
    }
    private static Map<String, ResourceEvidence> renewableResources(Map<String, ResourceEvidence> resources) {
        var renewable = new TreeMap<String, ResourceEvidence>();
        resources.forEach((id, resource) -> {
            var sources = resource.sources().stream().filter(s -> s.renewable()
                    && s.kind() != AcquisitionSource.Kind.QUEST_REWARD && s.kind() != AcquisitionSource.Kind.ADMINISTRATIVE
                    // An item-only trade row does not prove NPC/jobsite/stock renewal, or its payments.
                    && (s.kind() != AcquisitionSource.Kind.TRADE || s.availability() != null)
                    && s.expectedOutput() > 0 && s.confidence() >= .5
                    && (s.availability() == null || s.availability().accessProven() && s.availability().uncertainty().isEmpty()
                    && (s.availability().category() == SourceAvailability.Category.REFRESHABLE
                    || s.availability().category() == SourceAvailability.Category.CONDITIONAL_RENEWABLE))).toList();
            if (sources.isEmpty()) return;
            double confidence = Math.min(resource.confidence(), sources.stream().mapToDouble(AcquisitionSource::confidence).min().orElse(0));
            renewable.put(id, new ResourceEvidence(id, resource.stage(), resource.availability(), resource.automation(),
                    resource.reachable(), resource.external(), resource.economicValue(), confidence, sources, resource.warnings()));
        });
        return renewable;
    }
    /** Capture the native fuel and container contracts once per provider operation. A failed capture
     * is handled by the caller's optional integration boundary, never replaced by guessed fuel. */
    private static Map<String, Integer> nativeFuels() {
        var result = new TreeMap<String, Integer>();
        net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel().forEach((item, ticks) ->
                result.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(), ticks));
        return result;
    }
    private static Map<String, String> nativeRemainders() {
        var result = new TreeMap<String, String>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM)
            if (item.hasCraftingRemainingItem()) result.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getCraftingRemainingItem()).toString());
        return result;
    }
    /** Repeatable operation requires a renewable fuel path. A finite bootstrap is never a fuel supply. */
    public ConfigurationAccess.Proof requireFuel() {
        visits = 0;
        return best(fuels.entrySet().stream().filter(f -> f.getValue() > 0)
                .map(f -> require(f.getKey(), null, new HashSet<>(), 0)).toList());
    }
    public record FiniteProof(ConfigurationAccess.Proof access, Map<String, Long> remaining,
                              Map<String, Long> drawnStocks) { }
    private final Map<StackKey, String> stockKeys = new LinkedHashMap<>();
    private String stockKey(String item, Object exact) {
        return stockKeys.computeIfAbsent(new StackKey(item, exact), ignored -> item + "#" + stockKeys.size());
    }
    private Object defaults(String item) {
        return defaultComponents.computeIfAbsent(item, id -> { var stack = new JsonObject(); stack.addProperty("id", id); return components.apply(stack); });
    }
    private Object outputComponents(Candidate candidate) { return recipeComponents.computeIfAbsent(candidate.output(), components); }
    /** Prove this finite joint bill. Calls are independent hypothetical plans, never a player's inventory.
     * Output count and ingredient multiplicity share one ledger, including reusable stations and remainders. */
    public FiniteProof requireFinite(List<JsonObject> stacks) {
        return finitePlans(stacks, false);
    }
    /** Conditional finite capability setup: a successful item from each of enough distinct,
     * accessible structure-loot opportunities. This is an explicit exploration assumption,
     * not a guaranteed stock, fixed player's inventory, renewal cycle or search-time estimate.
     * One positive-probability integer item per opportunity; expected counts are never stocks. */
    public FiniteProof requireExploration(List<JsonObject> stacks) {
        String key = stacks.toString(); var cached = explorationBills.get(key); if (cached != null) return cached;
        FiniteProof best;
        try {
            sourceCeiling = ProgressionBand.APEX;
            best = finitePlans(stacks, true);
            // A late direct container must not hide an earlier material/crafting route.
            // Each probe has its own full joint ledger and bounded backtracking budget.
            // A failed probe is not proof of absence/global optimality.
            int low = 0, high = best.access().placement().reachable() ? best.access().placement().stage().ordinal() - 1 : -1;
            while (low <= high) {
                int middle = (low + high) / 2; sourceCeiling = ProgressionBand.at(middle);
                var earlier = finitePlans(stacks, true);
                if (earlier.access().placement().reachable()) { best = earlier; high = earlier.access().placement().stage().ordinal() - 1; }
                else low = middle + 1;
            }
        } finally { sourceCeiling = ProgressionBand.APEX; }
        if (explorationBills.size() >= 512) explorationBills.pollFirstEntry();
        explorationBills.put(key, best); return best;
    }
    private FiniteProof finitePlans(List<JsonObject> stacks, boolean exploration) {
        conditionalExploration = exploration;
        List<Integer> plan = List.of();
        for (int attempt = 0; attempt < MAX_PLANS; attempt++) {
            choices = new PlanChoices(plan); stockKeys.clear();
            var result = finiteBill(stacks);
            if (result.access().placement().reachable()) return result;
            plan = choices.next(); if (plan == null) return result;
        }
        return new FiniteProof(unknown("Joint acquisition search reached its bounded plan limit; access remains unproven, not absent"), Map.of(), Map.of());
    }
    private FiniteProof finiteBill(List<JsonObject> stacks) {
        visits = 0; var ledger = new AcquisitionLedger(); var proofs = new ArrayList<ConfigurationAccess.Proof>();
        for (var stack : stacks) {
            long count = quantity(stack);
            if (count < 1 || count > 1_000_000) return new FiniteProof(unknown("Unsupported finite request quantity"), Map.of(), Map.of());
            var proof = finite(text(stack, "id"), stack.has("components") ? components.apply(stack) : null,
                    count, true, ledger, new HashSet<>(), 0);
            proofs.add(proof);
            // Later requests cannot repair this failed branch. Exploring them would
            // bury its useful earlier choice behind unrelated downstream alternatives.
            if (!proof.placement().reachable()) break;
        }
        var proof = combine(proofs, "joint finite acquisition; no renewal inferred", 1);
        return new FiniteProof(proof, proof.placement().reachable() ? Map.copyOf(ledger.inventory()) : Map.of(),
                proof.placement().reachable() ? Map.copyOf(ledger.consumedStocks()) : Map.of());
    }
    private ConfigurationAccess.Proof finite(String item, Object exact, long count, boolean consume,
            AcquisitionLedger ledger, Set<StackKey> path, int depth) {
        if (count < 1 || depth >= MAX_DEPTH || ++visits > MAX_VISITS) return unknown("Finite acquisition exceeded bounded work");
        var existing = stockKeys.entrySet().stream().filter(entry -> entry.getKey().item().equals(item)
                && (exact == null || exact.equals(entry.getKey().components())) && ledger.held(entry.getValue()) >= count).toList();
        for (var entry : choices.one(existing)) {
            if (consume) ledger.consume(entry.getValue(), count);
            return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY, true, 1, List.of()),
                    List.of("reused stock " + item), List.of());
        }
        var pathKey = new StackKey(item, exact);
        if (!path.add(pathKey)) return unknown("Finite recipe cycle has no independent bootstrap");
        try {
            Object base = defaults(item); String baseKey = stockKey(item, base);
            if (exact == null || exact.equals(base)) {
                var renewable = directSupply(item);
                if (renewable.placement().reachable() && renewable.placement().stage().ordinal() <= sourceCeiling.ordinal()) {
                    ledger.add(baseKey, count); if (consume) ledger.consume(baseKey, count); return renewable;
                }
                var resource = resources.get(item);
                if (resource != null && resource.external() && resource.reachable() && resource.confidence() >= .5
                        && !constrained.contains(item)) {
                    if (conditionalExploration) {
                        var opportunity = resource.sources().stream().filter(ConfiguredRecipeAccess::explorableLoot)
                                .filter(s -> s.stage().ordinal() <= sourceCeiling.ordinal())
                                .min(Comparator.comparing(AcquisitionSource::stage).thenComparing(AcquisitionSource::id));
                        if (opportunity.isPresent()) {
                            var source = opportunity.orElseThrow();
                            // This quantity denotes distinct successful encounters, not source.expectedOutput().
                            ledger.add(baseKey, count); if (consume) ledger.consume(baseKey, count);
                            return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(source.stage(), true,
                                    Math.min(resource.confidence(), source.confidence()), List.of(source)),
                                    List.of(item, source.id(), "Conditional exploration policy: " + count
                                            + " distinct successful unlooted structure opportunities, reserve one " + item
                                            + " from each; native placement/dimension conditions apply; enough accessible occurrences is assumed"
                                            + "; no guaranteed finite stock, renewable supply, chance-to-count conversion or search rate"), List.of());
                        }
                    }
                    var stockBranch = ledger.copy(); var stockProofs = new ArrayList<ConfigurationAccess.Proof>();
                    for (var source : resource.sources().stream().sorted(Comparator.comparing(AcquisitionSource::stage)
                            .thenComparing(AcquisitionSource::id)).toList()) {
                        var a = source.availability();
                        if (source.renewable() || source.stage().ordinal() > sourceCeiling.ordinal() || source.confidence() < .5 || source.kind() == AcquisitionSource.Kind.QUEST_REWARD
                                || source.kind() == AcquisitionSource.Kind.ADMINISTRATIVE || a == null || !a.accessProven()
                                || !a.uncertainty().isEmpty() || a.occurrenceChance() != 1
                                || a.category() != SourceAvailability.Category.FINITE_SHARED && a.category() != SourceAvailability.Category.FINITE_PERSONALIZED
                                || source.expectedOutput() != Math.rint(source.expectedOutput()) || source.expectedOutput() > Long.MAX_VALUE) continue;
                        long missing = Math.max(0, count - stockBranch.held(baseKey));
                        long available = stockBranch.remaining(source.id(), baseKey, (long)source.expectedOutput());
                        long taken = Math.min(missing, available);
                        if (taken < 1 || !stockBranch.draw(source.id(), baseKey, (long)source.expectedOutput(), taken)) continue;
                        stockProofs.add(new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(source.stage(), true,
                                Math.min(resource.confidence(), source.confidence()), List.of(source)), List.of(source.id(), item), List.of()));
                        if (stockBranch.held(baseKey) >= count) {
                            if (consume) stockBranch.consume(baseKey, count);
                            ledger.commit(stockBranch); return combine(stockProofs, "joint independent finite stocks", 1);
                        }
                    }
                }
            }
            for (var candidate : choices.one(producers.getOrDefault(item, List.of()))) {
                Object outputComponents = outputComponents(candidate);
                if (exact != null && !exact.equals(outputComponents)) continue;
                String outputKey = stockKey(item, outputComponents);
                long output = quantity(candidate.output());
                if (output < 1 || output > 1_000_000 || candidate.confidence() < .5) continue;
                long missing = Math.max(0, count - ledger.held(outputKey));
                long crafts = (missing + output - 1) / output;
                var branch = ledger.copy(); var requirements = new ArrayList<ConfigurationAccess.Proof>();
                String heldStation = null;
                if (!candidate.station().isEmpty()) {
                    var station = finite(candidate.station(), null, 1, false, branch, path, depth + 1);
                    if (!station.placement().reachable()) continue;
                    requirements.add(station);
                    heldStation = stockKeys.entrySet().stream().filter(e -> e.getKey().item().equals(candidate.station())
                            && branch.held(e.getValue()) >= 1).map(Map.Entry::getValue).findFirst().orElseThrow();
                    branch.consume(heldStation, 1); // reserve for the full operation, unavailable to inputs/fuel
                }
                // Remainders return after every craft, never before all simultaneous slots are paid.
                // Ordinary no-remainder recipes can still use a single counted batch.
                boolean returning = mayReturn(candidate.definition().getAsJsonArray("ingredients"));
                for (var input : candidate.definition().getAsJsonArray("ingredients")) returning |= hasAlternatives(input);
                long batches = returning ? crafts : 1, batchSize = returning ? 1 : crafts;
                boolean materials = true;
                for (long batch = 0; batch < batches; batch++) {
                    var returns = new TreeMap<String, Long>();
                    for (var input : candidate.definition().getAsJsonArray("ingredients")) if (!input.isJsonNull()) {
                        var proof = finiteIngredient(input, batchSize, branch, path, depth + 1, returns);
                        requirements.add(proof); if (!proof.placement().reachable()) { materials = false; break; }
                    }
                    if (!materials) break;
                    returns.forEach(branch::add);
                }
                if (!materials) continue;
                if (candidate.cookingTicks() > 0) {
                    ConfigurationAccess.Proof fuel = unknown("No fuel meets this finite cooking bill");
                    for (var entry : choices.one(orderedFuels())) {
                        var fuelBranch = branch.copy();
                        long needed = (Math.multiplyExact(crafts, candidate.cookingTicks()) + entry.getValue() - 1) / entry.getValue();
                        var next = finite(entry.getKey(), null, needed, true, fuelBranch, path, depth + 1);
                        if (next.placement().reachable()) {
                            var remainder = remainders.get(entry.getKey());
                            if (remainder != null) fuelBranch.add(stockKey(remainder, defaults(remainder)), needed);
                            branch.commit(fuelBranch); fuel = next; break;
                        }
                    }
                    requirements.add(fuel);
                }
                var proof = combine(requirements, candidate.id(), candidate.confidence());
                if (!proof.placement().reachable()) continue;
                if (heldStation != null) branch.add(heldStation, 1);
                branch.add(outputKey, Math.multiplyExact(crafts, output));
                if (consume && !branch.consume(outputKey, count)) throw new IllegalStateException("Finite output conservation failure");
                ledger.commit(branch); return proof;
            }
            return unknown("Insufficient guaranteed finite or renewable material for " + count + " " + item);
        } finally { path.remove(pathKey); }
    }
    private ConfigurationAccess.Proof finiteIngredient(JsonElement value, long count, AcquisitionLedger ledger,
            Set<StackKey> path, int depth, Map<String, Long> returns) {
        List<JsonElement> alternatives = new ArrayList<>();
        if (value.isJsonArray()) value.getAsJsonArray().forEach(alternatives::add); else alternatives.add(value);
        for (var alternative : choices.one(alternatives)) {
            if (!alternative.isJsonObject()) continue;
            var object = alternative.getAsJsonObject();
            List<String> choices; JsonObject componentPatch = null;
            if (object.size() == 1 && object.has("item")) choices = List.of(text(object, "item"));
            else if (object.size() == 1 && object.has("tag")) choices = tags.getOrDefault(text(object, "tag"), List.of());
            else if (text(object, "type").equals("neoforge:components") && object.has("strict") && object.get("strict").getAsBoolean()
                    && object.has("components") && object.has("items")
                    && Set.of("type", "strict", "components", "items").containsAll(object.keySet())) {
                componentPatch = object.getAsJsonObject("components");
                if (componentPatch.keySet().stream().anyMatch(k -> k.startsWith("!"))) continue;
                choices = items(object.get("items"));
            } else continue;
            for (String item : this.choices.one(choices.stream().distinct()
                    .sorted(Comparator.comparingInt(this::directSupplyOrder).thenComparing(Comparator.naturalOrder())).toList())) {
                var branch = ledger.copy(); Object exact = null;
                if (componentPatch != null) { var stack = new JsonObject(); stack.addProperty("id", item); stack.add("components", componentPatch); exact = components.apply(stack); }
                String remainder = remainders.get(item);
                var proof = finite(item, exact, count, true, branch, path, depth);
                if (!proof.placement().reachable()) continue;
                if (remainder != null) returns.merge(stockKey(remainder, defaults(remainder)), count, Math::addExact);
                ledger.commit(branch); return proof;
            }
        }
        return unknown("Finite ingredient has unsupported predicates or insufficient shared stock");
    }
    private int directSupplyOrder(String item) {
        var placement = directSupply(item).placement();
        return placement.reachable() ? placement.stage().ordinal() : ProgressionBand.values().length;
    }
    private ConfigurationAccess.Proof directSupply(String item) {
        return directSupplies.computeIfAbsent(item, id -> ordinary.require(List.of(List.of(id))));
    }
    private List<Map.Entry<String, Integer>> orderedFuels() {
        if (orderedFuels == null) orderedFuels = fuels.entrySet().stream().filter(f -> f.getValue() > 0)
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> f) -> directSupplyOrder(f.getKey()))
                        .thenComparing(Map.Entry::getKey)).toList();
        return orderedFuels;
    }
    private static boolean explorableLoot(AcquisitionSource source) {
        var a = source.availability();
        return source.kind() == AcquisitionSource.Kind.LOOT && !source.renewable() && source.confidence() >= .5
                && source.expectedOutput() > 0 && a != null && a.accessProven()
                && a.conditions().contains("native_unlocked_container_binding")
                && a.uncertainty().isEmpty() && a.occurrenceChance() > 0 && !a.structures().isEmpty() && !a.dimensions().isEmpty()
                && (a.category() == SourceAvailability.Category.FINITE_SHARED || a.category() == SourceAvailability.Category.FINITE_PERSONALIZED);
    }
    private boolean mayReturn(JsonElement value) {
        if (value.isJsonArray()) { for (var element : value.getAsJsonArray()) if (mayReturn(element)) return true; }
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("tag") && tags.getOrDefault(text(object, "tag"), List.of()).stream().anyMatch(remainders::containsKey)) return true;
            for (var entry : object.entrySet()) if (mayReturn(entry.getValue())) return true;
        }
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && (remainders.containsKey(value.getAsString()) || value.getAsString().startsWith("#")
                && tags.getOrDefault(value.getAsString().substring(1), List.of()).stream().anyMatch(remainders::containsKey));
    }
    private boolean hasAlternatives(JsonElement value) {
        if (value.isJsonArray()) return value.getAsJsonArray().size() > 1;
        if (!value.isJsonObject()) return false;
        var object = value.getAsJsonObject();
        return object.has("tag") || object.has("items") && (object.get("items").isJsonArray()
                || object.get("items").isJsonPrimitive() && object.get("items").getAsString().startsWith("#"));
    }
    private static long quantity(JsonObject stack) {
        if (!stack.has("count")) return 1;
        try { return stack.get("count").getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException | NumberFormatException | UnsupportedOperationException failure) { return -1; }
    }
    public ConfigurationAccess.Proof requireStack(JsonObject stack) {
        visits = 0;
        return require(text(stack, "id"), components.apply(stack), new HashSet<>(), 0);
    }
    public ConfigurationAccess.Proof requireItem(String item) {
        visits = 0;
        return require(item, null, new HashSet<>(), 0);
    }
    private ConfigurationAccess.Proof require(String item, Object exact, Set<StackKey> path, int depth) {
        var key = new StackKey(item, exact);
        var cached = proven.get(key); if (cached != null) return cached;
        if (depth >= MAX_DEPTH || ++visits > MAX_VISITS) return unknown("Configured craft proof exceeded bounded work");
        if (!path.add(key)) return unknown("Recipe cycle has no independent configured source");
        try {
            if (exact == null) {
                var direct = directSupply(item);
                if (direct.placement().reachable()) { proven.put(key, direct); return direct; }
            }
            ConfigurationAccess.Proof best = null;
            var failures = new TreeSet<String>();
            for (var candidate : producers.getOrDefault(item, List.of())) {
                if (exact != null && !exact.equals(outputComponents(candidate))) continue;
                List<ConfigurationAccess.Proof> inputs = new ArrayList<>();
                if (!candidate.station().isEmpty()) inputs.add(setups.computeIfAbsent(candidate.station(), station -> {
                    // The operating station is held, not consumed each cycle. A finite bootstrap is sufficient;
                    // all consumed materials and fuels below still require independently renewable supply.
                    int priorVisits = visits;
                    var stack = new JsonObject(); stack.addProperty("id", station);
                    try { return requireFinite(List.of(stack)).access(); }
                    finally { visits += priorVisits; }
                }));
                if (candidate.cookingTicks() > 0) inputs.add(best(fuels.entrySet().stream().filter(f -> f.getValue() > 0)
                        .map(f -> require(f.getKey(), null, path, depth + 1)).toList()));
                for (var input : candidate.definition().getAsJsonArray("ingredients")) {
                    if (!input.isJsonNull()) inputs.add(ingredient(input, path, depth + 1));
                }
                var proof = combine(inputs, candidate.id(), candidate.confidence());
                if (proof.placement().reachable() && (best == null || earlier(proof, best))) best = proof;
                if (!proof.placement().reachable()) proof.unknown().stream().limit(8).forEach(failures::add);
                if (visits > MAX_VISITS) break;
            }
            if (best != null) { proven.put(key, best); return best; }
            failures.add("No independently supported static crafting witness for " + item + (exact == null ? "" : " with exact components"));
            return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.APEX, false, 0, List.of()),
                    List.of(), failures.stream().limit(8).toList());
        } finally { path.remove(key); }
    }
    private ConfigurationAccess.Proof ingredient(JsonElement element, Set<StackKey> path, int depth) {
        if (element.isJsonArray()) {
            var choices = new ArrayList<ConfigurationAccess.Proof>();
            for (var alternative : element.getAsJsonArray()) choices.add(ingredient(alternative, path, depth));
            return best(choices);
        }
        if (!element.isJsonObject()) return unknown("Opaque crafting ingredient");
        var object = element.getAsJsonObject();
        if (object.has("type")) {
            if (!text(object, "type").equals("neoforge:components") || !object.has("strict") || !object.get("strict").getAsBoolean()
                    || !object.has("components") || !object.get("components").isJsonObject() || !object.has("items")
                    || !Set.of("type", "items", "components", "strict").containsAll(object.keySet()))
                return unknown("Unsupported configured ingredient predicate");
            List<String> items = items(object.get("items"));
            var expected = object.getAsJsonObject("components");
            if (expected.keySet().stream().anyMatch(key -> key.startsWith("!")))
                return unknown("Component predicate removals are not native positive component expectations");
            // Native strict matching builds an ItemStack(item, 1, predicate.asPatch()),
            // so defaults belong on both sides of the full-component equality comparison.
            return best(items.stream().map(item -> {
                var stack = new JsonObject(); stack.addProperty("id", item); stack.add("components", expected.deepCopy());
                return require(item, components.apply(stack), path, depth);
            }).toList());
        }
        if (object.size() != 1) return unknown("Additional crafting ingredient semantics unresolved");
        List<String> items = object.has("item") ? List.of(text(object, "item"))
                : object.has("tag") ? tags.getOrDefault(text(object, "tag"), List.of()) : List.of();
        var direct = ordinary.require(List.of(items));
        if (direct.placement().reachable()) return direct;
        return best(items.stream().map(item -> require(item, null, path, depth)).toList());
    }
    private List<String> items(JsonElement value) {
        if (value.isJsonPrimitive()) {
            String id = value.getAsString(); return id.startsWith("#") ? tags.getOrDefault(id.substring(1), List.of()) : List.of(id);
        }
        if (value.isJsonArray()) {
            var result = new TreeSet<String>(); for (var entry : value.getAsJsonArray()) result.addAll(items(entry)); return List.copyOf(result);
        }
        return List.of();
    }
    public static ConfigurationAccess.Proof combine(List<ConfigurationAccess.Proof> requirements, String setup, double confidence) {
        var selected = new TreeSet<String>(); var acquisition = new LinkedHashSet<AcquisitionSource>(); var unknown = new TreeSet<String>();
        var stage = ProgressionBand.ENTRY;
        for (var proof : requirements) {
            selected.addAll(proof.selected()); acquisition.addAll(proof.placement().acquisition()); unknown.addAll(proof.unknown());
            stage = ProgressionBand.at(Math.max(stage.ordinal(), proof.placement().stage().ordinal()));
            confidence = Math.min(confidence, proof.placement().confidence());
            if (!proof.placement().reachable()) unknown.add("Required configured setup is unreachable");
        }
        if (requirements.isEmpty()) unknown.add("Crafting has no independently supported material source");
        if (setup != null && !setup.isBlank()) selected.add(setup);
        return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(stage, unknown.isEmpty(), unknown.isEmpty() ? confidence : 0,
                List.copyOf(acquisition)), List.copyOf(selected), List.copyOf(unknown));
    }
    private static ConfigurationAccess.Proof best(List<ConfigurationAccess.Proof> choices) {
        return choices.stream().filter(p -> p.placement().reachable()).min(Comparator
                .comparing((ConfigurationAccess.Proof p) -> p.placement().stage())
                .thenComparing(Comparator.comparingDouble((ConfigurationAccess.Proof p) -> p.placement().confidence()).reversed())
                .thenComparing(p -> p.selected().toString())).orElseGet(() -> unknown("No supported ingredient alternative"));
    }
    private static boolean earlier(ConfigurationAccess.Proof a, ConfigurationAccess.Proof b) {
        return a.placement().stage().ordinal() < b.placement().stage().ordinal()
                || a.placement().stage() == b.placement().stage() && a.placement().confidence() > b.placement().confidence();
    }
    private static ConfigurationAccess.Proof unknown(String reason) {
        return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.APEX, false, 0, List.of()), List.of(), List.of(reason));
    }
    private static String text(JsonObject value, String name) { return value.has(name) ? value.get(name).getAsString() : ""; }
    private static boolean auditedHooklessCrafting(JsonObject definition, String projection, String runtime) {
        boolean supportedClass = projection.equals("native_shaped_public_fields")
                && runtime.equals("dev.latvian.mods.kubejs.recipe.special.ShapedKubeJSRecipe")
                || projection.equals("native_shapeless_public_fields")
                && runtime.equals("dev.latvian.mods.kubejs.recipe.special.ShapelessKubeJSRecipe");
        return supportedClass && text(definition, "behavior_adapter").equals("kubejs:published_crafting_hooks")
                && text(definition, "behavior_api_version").equals("2101.7.2-build.374")
                && definition.has("ingredient_action_count") && definition.get("ingredient_action_count").getAsInt() == 0
                && definition.has("modify_result_present") && !definition.get("modify_result_present").getAsBoolean();
    }
    /** Full native equality must not serialize defaults through their persistence codecs.
     * Some native defaults (for example a zero wear pool) are valid in memory while
     * their codecs reject them. Keep every component and its typed value instead. */
    static Map<DataComponentType<?>, Object> nativeComponents(ItemStack stack) {
        Map<DataComponentType<?>, Object> values = new HashMap<>();
        for (var component : stack.getComponents()) values.put(component.type(), component.value());
        return Map.copyOf(values);
    }
}
