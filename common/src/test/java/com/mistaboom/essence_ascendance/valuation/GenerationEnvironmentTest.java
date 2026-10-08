package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Native generator projections and shared acquisition semantics; synthetic concepts, not pack certification. */
public final class GenerationEnvironmentTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        HolderLookup.Provider registries = VanillaRegistries.createLookup();
        var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var ordinary = GenerationDimensionData.capture(id("fixture:ordinary"), new NoiseBasedChunkGenerator(new FixedBiomeSource(plains),
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD)), true, registries);
        var geology = ProceduralNaturalBlockIndex.fromData(ordinary.naturalBiomes(), Map.of(), Map.of(), ordinary.terrain());
        check(geology.contains(id("minecraft:stone")), "Effective noise terrain proves ordinary stone geology");
        check(!ordinary.biomes().containsKey(id("minecraft:nether_wastes")), "Registered unrelated biome excluded");
        check(ordinary.evidence().biomes().equals(List.of("minecraft:plains")), "Only possible generator biomes captured");
        check(ordinary.evidence().levelAvailable(), "Actual early level availability explicit");

        Map<ResourceLocation, JsonObject> sharedBiomes = new java.util.TreeMap<>();
        var firstDimension = GenerationDimensionData.capture(id("fixture:shared_first"), new NoiseBasedChunkGenerator(new FixedBiomeSource(plains),
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD)), true, registries, sharedBiomes);
        var secondDimension = GenerationDimensionData.capture(id("fixture:shared_second"), new NoiseBasedChunkGenerator(new FixedBiomeSource(plains),
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD)), false, registries, sharedBiomes);
        check(sharedBiomes.size() == 1, "Common biome normalized once across configured dimensions");
        check(firstDimension.biomes().get(id("minecraft:plains")) == secondDimension.biomes().get(id("minecraft:plains")), "Indexes share the normalized biome value");
        try { firstDimension.biomes().clear(); throw new AssertionError("Snapshot map must be immutable"); }
        catch (UnsupportedOperationException expected) { checks++; }

        var empty = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct(List.of())), plains, List.of());
        var voidWorld = GenerationDimensionData.capture(id("fixture:void"), new FlatLevelSource(empty), true, registries);
        var voidGeology = ProceduralNaturalBlockIndex.fromData(voidWorld.naturalBiomes(), Map.of(), Map.of(), voidWorld.terrain());
        check(!voidGeology.contains(id("minecraft:stone")), "Void world does not inherit registered noise settings");
        check(voidWorld.evidence().geology().equals("proven_no_flat_terrain"), "Supported empty layers distinguish proven absence from unknown");
        check(voidWorld.naturalBiomes().values().stream().allMatch(root -> root.getAsJsonArray("features").isEmpty()
                || root.getAsJsonArray("features").asList().stream().allMatch(step -> step.getAsJsonArray().isEmpty())), "Disabled flat decoration cannot inherit plains geology");
        var watery = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct(List.of())), plains, List.of());
        watery.getLayersInfo().add(new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(2, net.minecraft.world.level.block.Blocks.WATER));
        watery.updateLayers();
        var waterySource = new FlatLevelSource(watery);
        var originalLayers = new java.util.ArrayList<>(watery.getLayers());
        var firstWater = GenerationDimensionData.capture(id("fixture:water"), waterySource, true, registries);
        var repeatedWater = GenerationDimensionData.capture(id("fixture:water"), waterySource, true, registries);
        check(firstWater.equals(repeatedWater), "Repeated flat projection changed after native layer adjustment");
        check(originalLayers.equals(watery.getLayers()), "Read-only projection mutated live non-motion-blocking layers");
        check(firstWater.terrain().get(id("fixture:water")).toString().contains("minecraft:water"), "Deferred flat water layers disappeared from geological definitions");

        var nether = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.NETHER_WASTES);
        var constrained = GenerationDimensionData.capture(id("fixture:constrained"), new NoiseBasedChunkGenerator(new FixedBiomeSource(nether),
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.NETHER)), false, registries);
        var constrainedGeology = ProceduralNaturalBlockIndex.fromData(constrained.naturalBiomes(), Map.of(), Map.of(), constrained.terrain());
        check(constrainedGeology.contains(id("minecraft:netherrack")) && !constrainedGeology.contains(id("minecraft:stone")), "Constrained dimension uses its configured substrate only");
        check(!constrained.evidence().levelAvailable() && constrained.evidence().support().equals("supported_data"), "Configured generator data usable without loading its level");

        var unreachableBiome = Map.of(id("fixture:unused"), json("{\"features\":[[\"fixture:ore\"]]}"));
        var placement = Map.of(id("fixture:ore"), json("{\"feature\":{\"type\":\"minecraft:ore\",\"config\":{\"state\":{\"Name\":\"minecraft:diamond_ore\"}}},\"placement\":[]}"));
        check(ProceduralNaturalBlockIndex.fromData(unreachableBiome, placement, Map.of(), Map.of()).contains(id("minecraft:diamond_ore")), "Fixture has configured ore evidence when its biome participates");
        check(!ProceduralNaturalBlockIndex.fromData(voidWorld.naturalBiomes(), placement, Map.of(), voidWorld.terrain()).contains(id("minecraft:diamond_ore")), "Unused configured ore cannot seed void acquisition");

        var production = new ProductionGraph(List.of(new ProductionGraph.Process("fixture:alternate_resource", "fixture:generator",
                List.of(new ProductionGraph.Input(List.of("fixture:seed", "fixture:alternate_seed"), 2, false)),
                List.of(new ProductionGraph.Output("fixture:resource", 4, .5, false)), 40, 10, "fixture:provider", .8,
                Map.of("condition", "requires configured catalyst and power"))), List.of());
        check(production.processes().getFirst().inputs().getFirst().alternatives().size() == 2, "Alternate production retains alternatives");
        check(!production.processes().getFirst().inputs().getFirst().consumed() && production.processes().getFirst().outputs().getFirst().expectedCount() == 2, "Catalyst and expected quantity preserved");
        var source = new AcquisitionSource("fixture:alternate_resource", AcquisitionSource.Kind.MACHINE, ProgressionBand.MID, 2,
                true, false, 0, .8, List.of("fixture:seed"), "Configured production requires catalyst; rate remains unknown");
        check(source.renewable() && !source.rateKnown() && !source.dependencies().isEmpty(), "Renewable and gated source need no invented throughput");
        var noise = new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD));
        rejected(() -> GenerationStructureData.capture(noise, registries, true), "Unbound construction-time biome tags must fail readiness instead of returning zero structures");
        check(GenerationStructureData.capture(new FlatLevelSource(empty), registries, true).structures().isEmpty(), "Empty flat structure overrides exclude registered structures");
        check(GenerationStructureData.capture(noise, registries, false).structures().isEmpty(), "Disabled structures cannot authorize generated content");
        epochIsolation();
        recipeFamilyDiagnostics();
        structureSettingsProjection(registries);
        processorDefinitions(registries);
        directHolderSerialization(registries);
        lootEvidenceProjection(registries);
        loadedBiomePredicate(registries);
        lootUncertaintyPropagation();
        System.out.println("GenerationEnvironmentTest: " + checks + " checks PASS (synthetic; no real-pack claim)");
    }
    private static void processorDefinitions(HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var nativeCodec = net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.LIST_OBJECT_CODEC;
        var processors = registries.lookupOrThrow(Registries.PROCESSOR_LIST).listElements().toList();
        check(!processors.isEmpty(), "Native processor registry fixture must contain definitions");
        for (var holder : processors) {
            var value = holder.value();
            var encoded = GenerationDataSnapshot.encodeDefinition(GenerationDataSnapshot.processorDefinitionCodec(), value, ops, holder.key().location().toString());
            check(encoded.getAsJsonArray("processors").size() == value.list().size(),
                    "Every native processor definition, including the empty list, normalizes without exclusions: " + holder.key().location());
            check(encoded.get("processors").equals(nativeCodec.encodeStart(ops, value).getOrThrow()),
                    "Normalized processor fields retain the complete native definition: " + holder.key().location());
        }
        // Construction-time vanilla tags are unbound, so decode round trips use tag-free native lists.
        for (var value : List.of(
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList(List.of()),
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList(List.of(
                        new net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor(.75f))))) {
            var encoded = GenerationDataSnapshot.encodeDefinition(GenerationDataSnapshot.processorDefinitionCodec(), value, ops, "fixture:processor_round_trip");
            var decoded = nativeCodec.parse(ops, encoded.get("processors")).getOrThrow();
            check(nativeCodec.encodeStart(ops, decoded).getOrThrow().equals(encoded.get("processors")),
                    "Empty and nonempty normalized definitions round trip through the native processor codec");
        }
    }
    private static void loadedBiomePredicate(HolderLookup.Provider registries) {
        var biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DEEP_OCEAN);
        var input = new com.almostreliable.lootjs.loot.condition.MatchBiome(HolderSet.direct(biome));
        var original = com.mojang.serialization.MapCodec.unit(input);
        // Model native dispatch caching the codec before generation starts.
        var wrapped = GenerationRegistrySerialization.lootConditionCodec(original);
        check(wrapped.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, input).getOrThrow().getAsJsonObject().isEmpty(), "Gameplay condition encoding changed outside evidence capture");
        try (var scope = GenerationRegistrySerialization.open(registries, true, Map.of("lootjs", "1.21.1-3.7.0"))) {
            var value = wrapped.codec().encodeStart(scope.ops(), input).getOrThrow().getAsJsonObject();
            check(value.getAsJsonArray("biomes").get(0).getAsString().equals("minecraft:deep_ocean"), "Actual predicate HolderSet disappears through its unit codec");
            check(wrapped.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, input).getOrThrow().getAsJsonObject().isEmpty(), "Private predicate projection leaks to foreign encoding ops");
        }
        check(wrapped.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, input).getOrThrow().getAsJsonObject().isEmpty(), "Closed capture can still change condition encoding");
        try (var scope = GenerationRegistrySerialization.open(registries, true, Map.of("lootjs", "unknown"))) {
            check(GenerationRegistrySerialization.lootConditionCodec(original).codec().encodeStart(scope.ops(), input).getOrThrow().getAsJsonObject().isEmpty(), "Unknown optional predicate version was certified");
        }
    }

    private static void structureSettingsProjection(HolderLookup.Provider registries) {
        var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var spawn = new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(net.minecraft.world.entity.EntityType.ZOMBIE, 17, 2, 5);
        var overrides = Map.of(net.minecraft.world.entity.MobCategory.MONSTER,
                new net.minecraft.world.level.levelgen.structure.StructureSpawnOverride(
                        net.minecraft.world.level.levelgen.structure.StructureSpawnOverride.BoundingBoxType.STRUCTURE,
                        net.minecraft.util.random.WeightedRandomList.create(spawn)));
        var settings = new net.minecraft.world.level.levelgen.structure.Structure.StructureSettings(HolderSet.direct(plains), overrides,
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES,
                net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_THIN);
        var linkage = new IllegalAccessError("Synthetic optional encoder accesses a private Minecraft field");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var structure = new net.minecraft.world.level.levelgen.structure.Structure(settings) {
            @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
                throw new AssertionError("Projection must not generate terrain");
            }
            @Override public net.minecraft.world.level.levelgen.structure.StructureType<?> type() {
                calls.incrementAndGet(); throw linkage;
            }
        };
        var projected = GenerationStructureData.settings(id("fixture:broken_placement_encoder"), structure, registries);
        check(calls.get() == 0, "Public generic settings never invoke optional placement codec dispatch");
        var decoded = net.minecraft.world.level.levelgen.structure.Structure.StructureSettings.CODEC.codec()
                .parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), projected).getOrThrow();
        var observed = decoded.spawnOverrides().get(net.minecraft.world.entity.MobCategory.MONSTER).spawns().unwrap().getFirst();
        check(observed.type == spawn.type && observed.getWeight().asInt() == 17 && observed.minCount == 2 && observed.maxCount == 5,
                "Shared projection preserves exact spawn types, weights and quantities");
        check(decoded.biomes().contains(plains) && decoded.step() == settings.step() && decoded.terrainAdaptation() == settings.terrainAdaptation(),
                "Projection preserves effective biome, decoration and terrain settings");
        try {
            GenerationDataSnapshot.encodeDefinition(net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC, structure,
                    registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), "fixture:broken_placement_encoder");
            throw new AssertionError("Required broken codec cannot return an empty success");
        } catch (IllegalStateException rejected) {
            check(rejected.getCause() == linkage && rejected.getMessage().contains("fixture:broken_placement_encoder"),
                    "Linkage failure becomes a contextual candidate rejection preserving the original cause");
        }
        check(calls.get() == 1, "Failing codec attempted once without retry or fallback evidence");
    }
    private static void lootEvidenceProjection(HolderLookup.Provider registries) {
        var complete = com.mojang.serialization.Codec.STRING.fieldOf("payload");
        var broken = com.mojang.serialization.Codec.STRING.flatComapMap(java.util.function.Function.identity(),
                value -> com.mojang.serialization.DataResult.<String>error(() -> "fixture unrepresentable custom parameters", "partial payload"))
                .fieldOf("payload");
        check(GenerationRegistrySerialization.lootFunctionCodec(broken) == broken, "Ordinary full codecs remain untouched");
        try (var fullScope = GenerationRegistrySerialization.open(registries)) {
            check(GenerationRegistrySerialization.lootFunctionCodec(broken) == broken, "Full definitions do not opt into acquisition projection");
            check(broken.codec().encodeStart(fullScope.ops(), "fixture").error().isPresent(), "Full required parameters still reject");
        }
        try (var scope = GenerationRegistrySerialization.open(registries, true)) {
            scope.definition("fixture:effective_loot");
            var projected = GenerationRegistrySerialization.lootFunctionCodec(broken);
            var output = projected.codec().encodeStart(scope.ops(), "fixture").getOrThrow().getAsJsonObject();
            check(output.get(GenerationLootEvidence.UNRESOLVED).getAsBoolean(), "Unsupported function stays explicit unresolved evidence");
            check(!output.has("payload"), "Partial codec parameters cannot leak into a successful model");
            check(output.has("essence_evidence_failure") && output.has("essence_evidence_function_class"), "Function representation failure retains provenance");
            check(scope.unsupportedLootFunctions().values().stream().mapToLong(Long::longValue).sum() == 1,
                    "Unsupported function recorded exactly once with its enclosing effective definition");
            check(projected.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, "fixture").error().isPresent(),
                    "Projection wrapper cannot change another ops context");
            var clean = GenerationRegistrySerialization.lootFunctionCodec(complete);
            check(clean.codec().encodeStart(scope.ops(), "preserved").getOrThrow()
                    .equals(complete.codec().encodeStart(scope.ops(), "preserved").getOrThrow()), "Successful function parameters remain byte-equivalent JSON");
            check(clean.codec().parse(scope.ops(), json("{\"payload\":\"preserved\"}")).getOrThrow().equals("preserved"), "Decode retains native behavior");
            var throwsFailure = com.mojang.serialization.Codec.STRING.flatComapMap(java.util.function.Function.identity(), value -> {
                throw new IllegalStateException("fixture thrown encoder failure");
            }).fieldOf("payload");
            rejected(() -> GenerationRegistrySerialization.lootFunctionCodec(throwsFailure).codec().encodeStart(scope.ops(), "fixture"),
                    "Thrown collection/encoder failures are not suppressed as representation limitations");
        }
        check(GenerationRegistrySerialization.lootFunctionCodec(broken) == broken, "Failure/success scope cleanup leaves ordinary codecs intact");
    }

    private static void lootUncertaintyPropagation() {
        var marker = json("{\"function\":\"minecraft:set_count\",\"essence_evidence_unresolved_function\":true}");
        var base = json("{\"pools\":[{\"rolls\":2,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"}]}]}");
        check(containerComplexity(base, net.minecraft.world.item.Items.DIAMOND) == 0, "Complete container source remains reliable");
        for (String location : List.of("table", "pool", "entry")) {
            var table = base.deepCopy(); var pool = table.getAsJsonArray("pools").get(0).getAsJsonObject();
            var target = location.equals("table") ? table : location.equals("pool") ? pool : pool.getAsJsonArray("entries").get(0).getAsJsonObject();
            var functions = new com.google.gson.JsonArray(); functions.add(marker.deepCopy()); target.add("functions", functions);
            check(containerComplexity(table, net.minecraft.world.item.Items.DIAMOND) > 0, "Unrepresentable " + location + " function cannot certify container acquisition");
            @SuppressWarnings("unchecked") Map<String, Object> root = new com.google.gson.Gson().fromJson(table, Map.class);
            var drops = ProceduralBlockLoot.estimate(root, new ProceduralBlockLoot.Context("fixture:block", Map.of(), java.util.Set.of(),
                    "minecraft:iron_pickaxe", java.util.Set.of(), Map.of()), ignored -> Map.of(), ignored -> List.of());
            check(drops.get("minecraft:diamond").unresolved() > 0, "Unrepresentable " + location + " function cannot certify harvest acquisition");
        }
        var mixed = base.deepCopy(); var entries = mixed.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        var unresolved = new com.google.gson.JsonArray(); unresolved.add(marker); entries.get(0).getAsJsonObject().add("functions", unresolved);
        entries.add(json("{\"type\":\"minecraft:item\",\"name\":\"minecraft:emerald\"}"));
        check(containerComplexity(mixed, net.minecraft.world.item.Items.DIAMOND) > 0 && containerComplexity(mixed, net.minecraft.world.item.Items.EMERALD) == 0,
                "Unsupported sibling retains table contents without tainting an independent complete entry");
        check(GenerationLootEvidence.unresolvedFunctions(json("{\"functions\":[{\"functions\":[{\"essence_evidence_unresolved_function\":true}]}]}")) == 1,
                "Nested function chains retain uncertainty");
        check(containerAcquisition(0).modeledAcquisition(), "Complete loot source can seed the actual acquisition graph");
        check(!containerAcquisition(containerComplexity(mixed, net.minecraft.world.item.Items.DIAMOND)).modeledAcquisition(),
                "Unrepresentable function cannot seed the actual acquisition graph as a known source");
    }

    private static ProceduralValuationResult containerAcquisition(int unresolved) {
        try {
            com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
            var item = net.minecraft.world.item.Items.DIAMOND;
            var source = new ProceduralValuationIndex.ContainerLootSource(id("fixture:chests/effective"), 1, 2, unresolved,
                    "FIXED_TREASURE", ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, true, id("fixture:structure"), 1, List.of(), false);
            var constructor = ProceduralValuationIndex.class.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var index = (ProceduralValuationIndex) constructor.newInstance(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(item, List.of(source)),
                    Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null);
            var contextClass = Class.forName(ProceduralValuationEngine.class.getName() + "$EvaluationContext");
            var contextConstructor = contextClass.getDeclaredConstructor(ProceduralValuationIndex.class); contextConstructor.setAccessible(true);
            var context = contextConstructor.newInstance(index);
            var solve = ProceduralValuationEngine.class.getDeclaredMethod("solveAcquisitionGraph", List.class, contextClass); solve.setAccessible(true);
            solve.invoke(null, List.of(item), context);
            var evaluate = ProceduralValuationEngine.class.getDeclaredMethod("evaluateItem", ProceduralValuationIndex.class, net.minecraft.world.item.Item.class, contextClass);
            evaluate.setAccessible(true);
            return (ProceduralValuationResult) evaluate.invoke(null, index, item, context);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static int containerComplexity(JsonObject table, net.minecraft.world.item.Item item) {
        try {
            var estimator = ProceduralValuationIndex.class.getDeclaredMethod("estimateContainerTable", ResourceLocation.class, Map.class, Map.class, java.util.Set.class);
            estimator.setAccessible(true); var id = id("fixture:chests/effective");
            var estimates = (Map<?, ?>) estimator.invoke(null, id, Map.of(id, table), new java.util.HashMap<>(), new java.util.HashSet<>());
            var estimate = estimates.get(item); var complexity = estimate.getClass().getDeclaredMethod("complexConditionCount"); complexity.setAccessible(true);
            return (Integer) complexity.invoke(estimate);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void directHolderSerialization(HolderLookup.Provider registries) {
        var direct = net.minecraft.core.Holder.direct(net.minecraft.world.level.block.Blocks.AIR);
        var condition = new net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition(direct, Optional.empty());
        var vanillaOps = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var codec = net.minecraft.resources.RegistryFixedCodec.create(Registries.BLOCK);
        var originalNamedCodec = net.minecraft.core.registries.BuiltInRegistries.BLOCK.holderByNameCodec();
        var namedCodec = GenerationRegistrySerialization.namedHolderCodec(net.minecraft.core.registries.BuiltInRegistries.BLOCK, originalNamedCodec);
        check(codec.encodeStart(vanillaOps, direct).error().isPresent(), "Native fixed codec reproduces direct registered-holder rejection");
        check(net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.CODEC.codec()
                .encodeStart(vanillaOps, condition).error().isPresent(), "Native loot condition reproduces the observed direct-block failure");
        check(GenerationRegistrySerialization.fixedHolder(Registries.BLOCK, direct, vanillaOps) == direct, "Ordinary serialization is unchanged");
        check(namedCodec.encodeStart(vanillaOps, direct).error().isPresent(), "Named codec retains native rejection outside generation");
        try (var scope = GenerationRegistrySerialization.open(registries)) {
            var canonical = GenerationRegistrySerialization.fixedHolder(Registries.BLOCK, direct, scope.ops());
            check(canonical.kind() == net.minecraft.core.Holder.Kind.REFERENCE && canonical.value() == direct.value(), "Only the exact registered object gains its real reference");
            check(codec.encodeStart(scope.ops(), canonical).getOrThrow().getAsString().equals("minecraft:air"), "Canonical fixed codec retains actual block ID");
            check(namedCodec.encodeStart(scope.ops(), direct).getOrThrow().getAsString().equals("minecraft:air"),
                    "Actual registry named-holder codec handles registered direct values during generation");
            check(namedCodec.parse(scope.ops(), new com.google.gson.JsonPrimitive("minecraft:air")).getOrThrow().value() == direct.value(),
                    "Named codec decode delegates to the original native decoder");
            check(namedCodec.encodeStart(vanillaOps, direct).error().isPresent(), "Named codec does not canonicalize unrelated ops");
            var conditionCodec = com.mojang.serialization.codecs.RecordCodecBuilder.<net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition>create(instance -> instance.group(
                    namedCodec.fieldOf("block").forGetter(net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition::block),
                    net.minecraft.advancements.critereon.StatePropertiesPredicate.CODEC.optionalFieldOf("properties")
                            .forGetter(net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition::properties)
            ).apply(instance, net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition::new));
            check(conditionCodec.encodeStart(scope.ops(), condition).getOrThrow().getAsJsonObject().get("block").getAsString().equals("minecraft:air"),
                    "Named field encoding preserves the original direct-holder condition without copying or mutation");
            check(GenerationRegistrySerialization.fixedHolder(Registries.BLOCK, canonical, scope.ops()) == canonical, "Named references retain native semantics");
            check(GenerationRegistrySerialization.fixedHolder(Registries.BLOCK, direct, vanillaOps) == direct, "Unrelated ops remain outside the serialization scope");
            check(java.util.concurrent.CompletableFuture.supplyAsync(() -> GenerationRegistrySerialization.fixedHolder(Registries.BLOCK, direct, scope.ops())).join() == direct,
                    "Concurrent threads cannot inherit generation serialization");
            var copied = new net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition(canonical, condition.properties());
            var table = holderFixtureTable(copied);
            var encoded = GenerationDataSnapshot.encodeDefinition(net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC, table, scope.ops(), "fixture:direct_block_loot");
            var pool = encoded.getAsJsonArray("pools").get(0).getAsJsonObject();
            check(pool.getAsJsonArray("conditions").get(0).getAsJsonObject().get("block").getAsString().equals("minecraft:air"),
                    "Full native loot encoding preserves the original effective block condition");
            check(pool.get("rolls").getAsFloat() == 3 && pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString().equals("minecraft:diamond"),
                    "Full loot graph retains observed rolls and item identity");
            var decoded = net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(scope.ops(), encoded).getOrThrow();
            check(GenerationDataSnapshot.encodeDefinition(net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC, decoded, scope.ops(), "fixture:round_trip").equals(encoded),
                    "Effective loot graph round-trips through native codecs without omitted data");
            check(condition.block() == direct && condition.block().kind() == net.minecraft.core.Holder.Kind.DIRECT, "Original loot condition remains unmodified");
            try { GenerationRegistrySerialization.open(registries); throw new AssertionError("Nested scope must reject"); }
            catch (IllegalStateException expected) { checks++; }
        }
        check(GenerationRegistrySerialization.fixedHolder(Registries.BLOCK, direct, vanillaOps) == direct, "Scope close removes canonicalization state");
        try (var scope = GenerationRegistrySerialization.open(registries)) { throw new IllegalStateException("fixture failure"); }
        catch (IllegalStateException expected) { checks++; }
        try (var scope = GenerationRegistrySerialization.open(registries)) {
            check(GenerationRegistrySerialization.fixedHolder(Registries.BLOCK, direct, scope.ops()).value() == direct.value(), "Failure releases scope for the next independent operation");
        }
        var key = net.minecraft.resources.ResourceKey.<String>createRegistryKey(id("fixture:arbitrary_values"));
        var registry = new net.minecraft.core.MappedRegistry<String>(key, com.mojang.serialization.Lifecycle.stable());
        var registeredValue = new String("same content");
        net.minecraft.core.Registry.register(registry, id("fixture:known_value"), registeredValue);
        registry.freeze();
        var isolatedRegistries = HolderLookup.Provider.create(java.util.stream.Stream.of(registry.asLookup()));
        var fixedCodec = net.minecraft.resources.RegistryFixedCodec.create(key);
        var genericNamedCodec = GenerationRegistrySerialization.namedHolderCodec(registry, registry.holderByNameCodec());
        try (var scope = GenerationRegistrySerialization.open(isolatedRegistries)) {
            var known = net.minecraft.core.Holder.direct(registeredValue);
            var unknown = net.minecraft.core.Holder.direct(new String("same content"));
            check(GenerationRegistrySerialization.fixedHolder(key, known, scope.ops()).value() == registeredValue,
                    "Generic canonicalization applies to arbitrary registries beyond blocks or any mod");
            check(fixedCodec.encodeStart(scope.ops(), GenerationRegistrySerialization.fixedHolder(key, known, scope.ops()))
                    .getOrThrow().getAsString().equals("fixture:known_value"), "Arbitrary registry keeps its actual ID");
            check(GenerationRegistrySerialization.fixedHolder(key, unknown, scope.ops()) == unknown,
                    "Equal but unregistered objects cannot borrow another value's registry identity");
            check(fixedCodec.encodeStart(scope.ops(), unknown).error().isPresent(), "Unregistered required values still reject rather than use a default");
            check(genericNamedCodec.encodeStart(scope.ops(), known).getOrThrow().getAsString().equals("fixture:known_value"),
                    "Named-holder repair is generic across arbitrary registry types");
            check(genericNamedCodec.encodeStart(scope.ops(), unknown).error().isPresent(), "Named-holder codec still rejects unregistered equal values");
        }
    }

    private static net.minecraft.world.level.storage.loot.LootTable holderFixtureTable(net.minecraft.world.level.storage.loot.predicates.LootItemCondition condition) {
        return net.minecraft.world.level.storage.loot.LootTable.lootTable()
                .setParamSet(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK)
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                        .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(3))
                        .when(() -> condition)
                        .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(net.minecraft.world.item.Items.DIAMOND)))
                .build();
    }

    private static void recipeFamilyDiagnostics() {
        var unknown = new net.minecraft.world.item.crafting.RecipeType<net.minecraft.world.item.crafting.CraftingRecipe>() {
            @Override public String toString() { throw new AssertionError("Unregistered type diagnostic must not call optional toString code"); }
        };
        check(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(unknown) == null, "Native fixture reproduces missing recipe registry ID");
        String family = ValuationGenerationInputs.recipeFamily(unknown);
        check(family.equals("unregistered_type:" + unknown.getClass().getName()), "Unknown family preserves type-class provenance without inventing a registry ID");
        check(ValuationGenerationInputs.recipeFamily(net.minecraft.world.item.crafting.RecipeType.CRAFTING).equals("minecraft:crafting"), "Registered recipe identity remains authoritative");
        check(ValuationGenerationInputs.recipeFamily(null).equals("unregistered_type:null"), "Missing type remains explicitly unknown");
        check(ValuationGenerationInputs.recipeAllowed(id("fixture:unregistered_type"), unknown), "Missing family identity does not silently disable otherwise usable recipe data");
        check(ValuationGenerationInputs.outputCount(id("fixture:unregistered_type"), unknown, 3) == 3, "Unknown family retains observed quantity");
    }

    private static void epochIsolation() {
        Object server = new Object(), resources = new Object(), recipes = new Object(), registries = new Object(), reloadable = new Object();
        var first = new GenerationEpoch(server, resources, recipes, registries, reloadable);
        first.require(server, resources, recipes, registries, reloadable); checks++;
        rejected(() -> first.require(new Object(), resources, recipes, registries, reloadable), "No cross-server snapshot reuse");
        rejected(() -> first.require(server, new Object(), recipes, registries, reloadable), "No cross-resource snapshot reuse");
        rejected(() -> first.require(server, resources, new Object(), registries, reloadable), "No cross-recipe snapshot reuse");
        rejected(() -> first.require(server, resources, recipes, registries, new Object()), "No cross-reloadable registry reuse");
        first.clear(); rejected(() -> first.require(server, resources, recipes, registries, reloadable), "Release invalidates same-identity access");
        var next = new GenerationEpoch(server, resources, recipes, registries, reloadable);
        next.require(server, resources, recipes, registries, reloadable); checks++;
        ValuationGenerationInputs.configure(new com.mistaboom.essence_ascendance.balance.config.BalanceOverrides(List.of(
                new com.mistaboom.essence_ascendance.balance.config.BalanceOverrides.FactOverride("blocked", com.mistaboom.essence_ascendance.balance.config.BalanceOverrides.SubjectKind.SOURCE,
                        "fixture:blocked", 0, Map.of("disabled", true), "fixture", 1)), Map.of()));
        check(!ValuationGenerationInputs.sourceAllowed("fixture:blocked"), "Generation factual inputs actually applied");
        ValuationGenerationInputs.clear();
        check(ValuationGenerationInputs.sourceAllowed("fixture:blocked"), "Cleared generation does not leak prior factual inputs");
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static ResourceLocation id(String text) { return ResourceLocation.parse(text); }
    private static void rejected(Runnable action, String message) { try { action.run(); throw new AssertionError(message); } catch (IllegalStateException expected) { checks++; } }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
