package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.mixin.SaplingGrowerAccess;
import com.mistaboom.essence_ascendance.mixin.TreeGrowerAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Bounded native cultivation contract. Single sapling, no nearby flowers, prepared clear space.
 * Only exact straight trunks/blob foliage/simple states without decorators are supported.
 * A positive surplus is a conditional stochastic renewal path, never a guaranteed finite inventory
 * or a production rate. Unsupported grower branches remain excluded independently. */
public final class NativeTreeRenewal {
    private NativeTreeRenewal() { }
    public record Shape(String log, String leaf, int minimumLogs, int minimumLeaves) { }
    public static Optional<Shape> shape(JsonObject feature) {
        if (feature == null || !text(feature, "type").equals("minecraft:tree")) return Optional.empty();
        var c = feature.getAsJsonObject("config");
        if (c == null || c.has("root_placer") || c.has("decorators") && !c.getAsJsonArray("decorators").isEmpty()) return Optional.empty();
        var t = c.getAsJsonObject("trunk_placer"); var f = c.getAsJsonObject("foliage_placer");
        if (t == null || f == null || !text(t,"type").equals("minecraft:straight_trunk_placer")
                || !text(f,"type").equals("minecraft:blob_foliage_placer")) return Optional.empty();
        Integer height = integer(t,"base_height"), radius = integer(f,"radius"), offset = integer(f,"offset"), foliage = integer(f,"height");
        if (height == null || radius == null || offset == null || foliage == null || height < foliage || height < 1
                || radius < 1 || radius > 16 || offset != 0 || foliage < 1 || foliage > 16) return Optional.empty();
        String log = simple(c.getAsJsonObject("trunk_provider")), leaf = simple(c.getAsJsonObject("foliage_provider"));
        if (log.isEmpty() || leaf.isEmpty()) return Optional.empty();
        int leaves = 0;
        // Exact BlobFoliagePlacer rows; conservatively skip all random corners and occupied trunk centers.
        for (int y = 0; y >= -foliage; y--) {
            int row = Math.max(0, radius - 1 - y / 2);
            if (row > 0) leaves += (2 * row + 1) * (2 * row + 1) - 4 - (y < 0 ? 1 : 0);
        }
        return leaves > 0 ? Optional.of(new Shape(log, leaf, height, leaves)) : Optional.empty();
    }
    public static Map<String, ResourceEvidence> enrich(PackEvidenceContext context, Map<String, ResourceEvidence> original, EvidenceSink sink) {
        var result = new TreeMap<>(original);
        var bootstrapAccess = OptionalIntegration.attempt("native_tree_renewal", "joint crafting/bootstrap inputs",
                () -> ConfiguredRecipeAccess.nativeCrafting(context, original));
        if (!bootstrapAccess.succeeded()) { sink.warn("Native cultivation bootstrap READ_FAILED: " + bootstrapAccess.failure()); return original; }
        var resolver = bootstrapAccess.value().orElseThrow(); // No newly inferred cycle can bootstrap another cycle.
        for (var item : context.inputs().items()) {
            if (!(item instanceof BlockItem block) || block.getBlock().getClass() != SaplingBlock.class) continue;
            String seed = BuiltInRegistries.ITEM.getKey(item).toString();
            var attempt = OptionalIntegration.attempt("native_tree_renewal", seed, () -> {
                var proof = bootstrap(seed, resolver, sink);
                if (!proof.placement().reachable()) return new Cycle(seed, proof, Map.of(), List.of("independent seed/soil setup unproven"));
                var grower = ((SaplingGrowerAccess) block.getBlock()).essenceAscendance$treeGrower();
                var access = (TreeGrowerAccess) (Object) grower;
                var primary = access.essenceAscendance$tree();
                if (primary.isEmpty()) return new Cycle(seed, proof, Map.of(), List.of("no audited single-sapling primary tree"));
                double secondary = access.essenceAscendance$secondaryTree().isPresent() ? access.essenceAscendance$secondaryChance() : 0;
                if (!Double.isFinite(secondary) || secondary < 0 || secondary > 1) throw new IllegalArgumentException("Invalid native tree branch probability");
                var features = new TreeMap<net.minecraft.resources.ResourceLocation, Double>();
                features.put(primary.orElseThrow().location(), 1 - secondary);
                if (secondary > 0) features.merge(access.essenceAscendance$secondaryTree().orElseThrow().location(), secondary, Double::sum);
                Map<String, Harvest> outputs = new TreeMap<>(); var definitions = new ArrayList<String>();
                var canopies = new TreeSet<String>();
                double expectedSeeds = 0, surplusChance = 0;
                for (var feature : features.entrySet()) {
                    var id = feature.getKey(); double branchChance = feature.getValue(); if (branchChance <= 0) continue;
                    var definition = context.inputs().definition("worldgen/configured_feature", id); var shape = shape(definition);
                    if (shape.isEmpty()) { definitions.add("Unmodeled branch " + id + " probability=" + branchChance
                            + ": consumes its seed with zero credited outputs in the lower bound"); continue; }
                    var config = definition.getAsJsonObject("config");
                    BlockState trunk = BlockState.CODEC.parse(JsonOps.INSTANCE, config.getAsJsonObject("trunk_provider").get("state")).getOrThrow();
                    BlockState leaves = BlockState.CODEC.parse(JsonOps.INSTANCE, config.getAsJsonObject("foliage_provider").get("state")).getOrThrow();
                    var excludedDrops = new ArrayList<String>();
                    var leafDrops = StartingBlockDrops.supportedHandDrops(context.inputs(), leaves, "", "", Set.of(), excludedDrops::add);
                    var seedDrop = leafDrops.get(seed);
                    double renewal = seedDrop == null ? 0 : seedDrop.expectedCount() * shape.orElseThrow().minimumLeaves();
                    if (!singleSeedDrop(seedDrop)) { definitions.add("Unproven seed drops " + id + ": " + seedDrop
                            + "; " + excludedDrops.stream().filter(reason -> reason.contains(seed)).limit(4).toList()
                            + "; zero credited outputs"); continue; }
                    var branch = new TreeMap<String, Harvest>();
                    StartingBlockDrops.guaranteedSingleHandDrops(context.inputs(), trunk).forEach((log, count) ->
                            branch.put(log, new Harvest(1, count * shape.orElseThrow().minimumLogs())));
                    if (branch.isEmpty()) { definitions.add("Unproven trunk drops " + id + ": zero credited outputs"); continue; }
                    canopies.add(shape.orElseThrow().leaf());
                    double probability = seedDrop.chance(); int leavesCount = shape.orElseThrow().minimumLeaves();
                    double branchSurplusChance = 1 - Math.pow(1 - probability, leavesCount)
                            - leavesCount * probability * Math.pow(1 - probability, leavesCount - 1);
                    expectedSeeds += branchChance * renewal;
                    surplusChance += branchChance * Math.clamp(branchSurplusChance, 0, 1);
                    branch.forEach((key, value) -> outputs.merge(key, new Harvest(branchChance * value.chance(), branchChance * value.expected()),
                            (a, b) -> new Harvest(a.chance() + b.chance(), a.expected() + b.expected())));
                    definitions.add(id + "; native branch probability=" + branchChance + "; lower-bound canopy=" + shape.orElseThrow().minimumLeaves()
                            + "; sapling chance per leaf=" + seedDrop.chance() + "; expected seeds=" + renewal);
                }
                // Reserve one input seed for EVERY branch, including those with no credited
                // output. Thus unmodeled branches cannot inflate expected renewable supply.
                if (expectedSeeds <= 1) { definitions.add("No positive weighted seed surplus after paying every branch"); return new Cycle(seed, proof, Map.of(), definitions); }
                outputs.put(seed, new Harvest(surplusChance, expectedSeeds - 1));
                return new Cycle(seed, proof, outputs, definitions, canopies);
            });
            if (!attempt.succeeded()) { sink.warn("Native cultivation READ_FAILED " + seed + ": " + attempt.failure()); continue; }
            var cycle = attempt.value().orElseThrow();
            if (cycle.outputs().isEmpty()) { sink.warn("Native cultivation not admitted " + seed + ": " + cycle.details()); continue; }
            String sourceId = "native_tree_cycle:" + seed;
            var conditions = new ArrayList<>(cycle.details());
            conditions.add("Plant one sapling on reusable valid soil, no flowers/2x2 group, native light/clearance, manual hand harvest and replant one seed");
            conditions.add("Stochastic extinction remains possible from a finite seed stock; expected surplus is not a guarantee or a measured rate");
            sink.add(new EvidenceFact(EvidenceFact.Subject.SOURCE, sourceId, "conditional_renewal", EvidenceFact.Value.text(BalanceDocument.GSON.toJson(cycle)),
                    "native_tree_renewal", EvidenceFact.Origin.OBSERVED, cycle.proof().placement().confidence(), 0,
                    cycle.proof().placement().stage(), cycle.proof().selected(), String.join("; ", conditions)));
            for (String canopy : cycle.canopies()) sink.add(new EvidenceFact(EvidenceFact.Subject.BLOCK, canopy,
                    "native_renewable_canopy", EvidenceFact.Value.text(seed), "native_tree_renewal", EvidenceFact.Origin.OBSERVED,
                    cycle.proof().placement().confidence(), 0, cycle.proof().placement().stage(), List.of(seed, sourceId),
                    "Grow additional trees using the proved surplus seeds after reserving the cultivation cycle. The native supported branch produces this canopy; consumes one surplus seed per attempt. Conditional stochastic renewal, not an item-form leaf, guaranteed harvest or rate."));
            for (var output : cycle.outputs().entrySet()) {
                var resource = result.get(output.getKey()); if (resource == null || !resource.external()) continue;
                var override = sink.get(EvidenceFact.Subject.ITEM, resource.itemId(), "renewable");
                var attainable = sink.get(EvidenceFact.Subject.ITEM, resource.itemId(), "attainable");
                if (override != null && override.origin() == EvidenceFact.Origin.OVERRIDE && !override.value().flag()
                        || attainable != null && attainable.origin() == EvidenceFact.Origin.OVERRIDE && !attainable.value().flag()) continue;
                var stageOverride = sink.get(EvidenceFact.Subject.ITEM, resource.itemId(), "stage");
                var stage = stageOverride != null && stageOverride.origin() == EvidenceFact.Origin.OVERRIDE ? resource.stage() : cycle.proof().placement().stage();
                var timer = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
                var availability = new SourceAvailability(sourceId, SourceAvailability.Category.CONDITIONAL_RENEWABLE, SourceAvailability.Scope.SHARED,
                        List.of(), List.of(), conditions, timer, timer, true, output.getValue().chance(), output.getValue().expected(), List.of());
                var sources = new ArrayList<>(resource.sources());
                sources.add(new AcquisitionSource(sourceId, AcquisitionSource.Kind.FARMING, stage, output.getValue().expected(), true, false, 0,
                        cycle.proof().placement().confidence(), cycle.proof().selected(), String.join("; ", conditions), availability));
                result.put(resource.itemId(), new ResourceEvidence(resource.itemId(), resource.reachable() && resource.stage().ordinal() < stage.ordinal() ? resource.stage() : stage,
                        Availability.RENEWABLE_MANUAL, resource.automation(), true, true, resource.economicValue(),
                        Math.max(resource.confidence(), cycle.proof().placement().confidence()), sources, resource.warnings()));
            }
        }
        return result;
    }
    private record Harvest(double chance, double expected) { }
    private static ConfigurationAccess.Proof bootstrap(String seed, ConfiguredRecipeAccess access, EvidenceSink sink) {
        var seedStack = NativeConsumables.request(seed, 1);
        var proofs = new ArrayList<ConfigurationAccess.Proof>();
        for (String soil : List.of("minecraft:dirt", "minecraft:grass_block")) {
            var soilStack = NativeConsumables.request(soil, 1);
            var joint = access.requireExploration(List.of(seedStack, soilStack)).access();
            if (joint.placement().reachable()) return joint;
            proofs.add(joint);
            var chance = sink.get(EvidenceFact.Subject.ITEM, seed, "native_initial_seed_chance");
            if (chance == null || chance.origin() != EvidenceFact.Origin.OBSERVED || chance.value().number() <= 0 || chance.value().number() > 1) continue;
            var ground = access.requireFinite(List.of(soilStack)).access();
            if (!ground.placement().reachable()) continue;
            String id = chance.provider() + ":conditional_initial_seed:" + seed;
            var conditions = List.of(chance.reason(), "One successful native starter harvest supplies the first seed; finite bootstrap can fail. Soil is proved separately and reserved.");
            var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
            var source = new AcquisitionSource(id, AcquisitionSource.Kind.WORLD_GENERATION, chance.stage(), chance.value().number(), false, false, 0,
                    chance.confidence(), List.of(), String.join("; ", conditions), new SourceAvailability(id, SourceAvailability.Category.FINITE_SHARED,
                    SourceAvailability.Scope.TEAM, List.of(), List.of("minecraft:overworld"), conditions, off, off, true,
                    chance.value().number(), chance.value().number(), List.of()));
            var starter = new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(chance.stage(), true, chance.confidence(), List.of(source)),
                    List.of(id, seed), List.of());
            return ConfiguredRecipeAccess.combine(List.of(starter, ground), "conditional native cultivation bootstrap", 1);
        }
        return ConfiguredRecipeAccess.combine(proofs, "independent joint seed/soil setup", 1);
    }
    static boolean singleSeedDrop(StartingBlockDrops.ExpectedDrop drop) {
        // Chance is a complement of a product; expectation is a weighted sum.
        // Equivalent one-item distributions need not have bit-identical doubles.
        return drop != null && drop.chance() > 0 && Math.abs(drop.expectedCount() - drop.chance()) <= 1e-12;
    }
    private record Cycle(String seed, ConfigurationAccess.Proof proof, Map<String, Harvest> outputs, List<String> details, Set<String> canopies) {
        Cycle(String seed, ConfigurationAccess.Proof proof, Map<String, Harvest> outputs, List<String> details) {
            this(seed, proof, outputs, details, Set.of());
        }
    }
    private static String text(JsonObject object, String key) { return object.has(key) ? object.get(key).getAsString() : ""; }
    private static Integer integer(JsonObject object, String key) {
        var value = object.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                && value.getAsDouble() == Math.rint(value.getAsDouble()) ? value.getAsInt() : null;
    }
    private static String simple(JsonObject provider) {
        return provider != null && text(provider,"type").equals("minecraft:simple_state_provider")
                && provider.has("state") ? text(provider.getAsJsonObject("state"),"Name") : "";
    }
}
