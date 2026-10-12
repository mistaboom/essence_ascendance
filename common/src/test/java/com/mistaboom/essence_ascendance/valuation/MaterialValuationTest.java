package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.economy.EconomyGenerator;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.engine.Automation;
import com.mistaboom.essence_ascendance.balance.engine.Availability;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.engine.ResourceEvidence;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/** Actual native outputs and acquisition graph; no server/world is opened. */
public final class MaterialValuationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init();
        cosmeticAndFunctionalDemand();
        harvestQuantityAndKnownCost();
        suppliedBlockDependencies();
        oreYieldTagEvidence();
        recipeMultiplicityAndCycles();
        zeroPayoutPreservesAcquisition();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "MaterialValuationTest: " + checks + " application, quantity, fallback and graph checks PASS");
    }

    private static void cosmeticAndFunctionalDemand() throws Exception {
        List<DownstreamDemand.Application> decorative = new ArrayList<>();
        for (Item output : List.of(Items.CUT_COPPER, Items.CUT_COPPER_SLAB, Items.CUT_COPPER_STAIRS,
                Items.WAXED_CUT_COPPER, Items.OXIDIZED_CUT_COPPER, Items.COPPER_GRATE,
                Items.WHITE_CONCRETE, Items.RED_CONCRETE, Items.STONE_BRICKS))
            decorative.add(DownstreamDemand.application(Items.COPPER_INGOT, output, false));
        var one = DownstreamDemand.summarize(List.of(decorative.getFirst()));
        var many = DownstreamDemand.summarize(decorative);
        check(one.equals(many), "Material, shape, color and oxidation variants share one construction application");
        near(many.weightedApplications(), .25, "Construction has a small useful application without a variant multiplier");
        var duplicateRecipes = new ArrayList<>(Collections.nCopies(500, decorative.getFirst()));
        check(DownstreamDemand.summarize(duplicateRecipes).equals(one), "Alternate methods cannot inflate application demand");
        var functional = List.of(
                DownstreamDemand.application(Items.COPPER_INGOT, Items.CHEST, false),
                DownstreamDemand.application(Items.COPPER_INGOT, Items.FURNACE, false),
                DownstreamDemand.application(Items.COPPER_INGOT, Items.LIGHTNING_ROD, false),
                DownstreamDemand.application(Items.COPPER_INGOT, Items.SPYGLASS, false),
                DownstreamDemand.application(Items.COPPER_INGOT, Items.IRON_SWORD, true));
        var uses = DownstreamDemand.summarize(functional);
        check(uses.applicationCount() == 5 && uses.multiplier() > many.multiplier(), "Distinct native functional applications retain demand");
        check(uses.significantCount() == 1, "Actual progression equipment is significant; block price alone is not");
        var family = DownstreamDemand.summarize(List.of(
                DownstreamDemand.application(Items.COPPER_INGOT, Items.COPPER_DOOR, false),
                DownstreamDemand.application(Items.COPPER_INGOT, Items.WAXED_COPPER_DOOR, false),
                DownstreamDemand.application(Items.COPPER_INGOT, Items.OXIDIZED_COPPER_DOOR, false)));
        check(family.applicationCount() == 1 && family.weightedApplications() == 1, "Door mechanics survive cosmetic deduplication");
        var local = new DownstreamDemand.Application("operating_machine", 1, false, false);
        var foreign = new DownstreamDemand.Application("operating_machine", 1, false, true);
        near(DownstreamDemand.summarize(List.of(local)).multiplier(),
                DownstreamDemand.summarize(List.of(foreign)).multiplier(), "Namespace alone cannot increase value");
        var order = new ArrayList<>(functional); Collections.reverse(order);
        check(DownstreamDemand.summarize(functional).equals(DownstreamDemand.summarize(order)), "Application ordering is deterministic");
        // Test JVM only: distinct modded registrations can share one native machine class.
        // The class or namespace alone must neither collapse those machines nor boost them.
        var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen"); frozen.setAccessible(true);
        var intrusive = net.minecraft.core.MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders"); intrusive.setAccessible(true);
        frozen.set(registry, false); intrusive.set(registry, new IdentityHashMap<>());
        List<DownstreamDemand.Application> moddedMachines = new ArrayList<>();
        List<DownstreamDemand.Application> moddedShapes = new ArrayList<>();
        for (int n = 0; n < 20; n++) {
            var machine = net.minecraft.core.Registry.register(registry, ResourceLocation.parse("fixture_" + n + ":opaque_machine"),
                    new net.minecraft.world.item.BlockItem(net.minecraft.world.level.block.Blocks.FURNACE, new Item.Properties()));
            moddedMachines.add(DownstreamDemand.application(Items.COPPER_INGOT, machine, false));
            var shape = net.minecraft.core.Registry.register(registry, ResourceLocation.parse("fixture_" + n + ":ultimate_generator"),
                    new net.minecraft.world.item.BlockItem(net.minecraft.world.level.block.Blocks.STONE, new Item.Properties()));
            moddedShapes.add(DownstreamDemand.application(Items.COPPER_INGOT, shape, false));
        }
        check(DownstreamDemand.summarize(moddedMachines).applicationCount() == 20,
                "Distinct modded operating machines keep functional demand even when implemented by the same block class");
        check(DownstreamDemand.summarize(moddedShapes).equals(one),
                "Many namespaces and impressive names do not turn plain building outputs into functional machines");
    }

    private static void harvestQuantityAndKnownCost() throws Exception {
        near(ProceduralValuationEngine.blockQuantityMultiplier(1), 1, "Single outputs preserve event effort");
        near(ProceduralValuationEngine.blockQuantityMultiplier(3.5), 1 / 3.5, "Uniform two-to-five harvest output shares event effort");
        near(ProceduralValuationEngine.blockQuantityMultiplier(64) * 64, 1, "Large output events cannot create more acquisition effort");
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            boolean rejected = false;
            try { ProceduralValuationEngine.blockQuantityMultiplier(invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Invalid harvest quantity rejected");
        }
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        var single = graph(List.of(), Map.of(Items.RAW_COPPER, List.of(blockDrop(1))));
        var batch = graph(List.of(), Map.of(Items.RAW_COPPER, List.of(blockDrop(3.5))));
        var singleValue = single.value(Items.RAW_COPPER);
        var batchValue = batch.value(Items.RAW_COPPER);
        check(batchValue.modeledAcquisition() && batchValue.totalValue() < singleValue.totalValue(),
                "Actual graph honors counted drop outputs below the intrinsic category floor");
        check(Math.abs(singleValue.totalValue() - batchValue.totalValue() * 3.5) <= 2,
                "Whole rounding preserves per-event effort within one output's rounding allowance");
        var missing = graph(List.of(), Map.of()).value(Items.RAW_COPPER);
        check(!missing.modeledAcquisition() && missing.totalValue() >= batchValue.totalValue(),
                "Unresolved source keeps its conservative fallback without claiming batch acquisition");
        var uncertain = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:copper_ore"),
                1, 64, 1, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, true, List.of(), null, false);
        check(!graph(List.of(), Map.of(Items.RAW_COPPER, List.of(uncertain))).value(Items.RAW_COPPER).modeledAcquisition(),
                "An unresolved condition never promotes a cheap batch to reliable acquisition");
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(new BalanceOverrides.FactOverride(
                "fixture_tool", BalanceOverrides.SubjectKind.ITEM, "minecraft:iron_pickaxe", 1000,
                Map.of("resource_value", 10000.0, "attainable", true), "fixture", 1)), Map.of()));
        var toolSingle = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:copper_ore"),
                1, 1, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, true, List.of(), Items.IRON_PICKAXE, false);
        var toolBatch = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:copper_ore"),
                1, 4, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, true, List.of(), Items.IRON_PICKAXE, false);
        long singleWear = graph(List.of(), Map.of(Items.RAW_COPPER, List.of(toolSingle))).value(Items.RAW_COPPER).totalValue();
        long batchWear = graph(List.of(), Map.of(Items.RAW_COPPER, List.of(toolBatch))).value(Items.RAW_COPPER).totalValue();
        check(Math.abs(singleWear - batchWear * 4) <= 2, "Reusable wear is shared across harvest outputs exactly once");
    }

    private static void recipeMultiplicityAndCycles() throws Exception {
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(new BalanceOverrides.FactOverride(
                "fixture_source", BalanceOverrides.SubjectKind.ITEM, "minecraft:clay_ball", 1000,
                Map.of("resource_value", 8.0, "attainable", true), "fixture", 1)), Map.of()));
        var production = recipe("brick", Items.CLAY_BALL, Items.BRICK, 4);
        var cheap = graph(List.of(production), Map.of()).value(Items.BRICK);
        check(cheap.modeledAcquisition() && cheap.totalValue() == 2, "A complete cheap recipe beats output category fallback");
        var uses = new ArrayList<ProceduralValuationIndex.RecipeModel>();
        uses.add(production);
        for (int i = 0; i < 50; i++) uses.add(recipe("alternate_" + i, Items.CLAY_BALL, Items.BRICK, 4));
        uses.add(new ProceduralValuationIndex.RecipeModel(ResourceLocation.parse("fixture:other_station"), RecipeType.SMELTING,
                Items.BRICK, 4, List.of(new ProceduralValuationIndex.IngredientChoice(List.of(Items.CLAY_BALL)))));
        var one = graph(List.of(production), Map.of()).value(Items.CLAY_BALL);
        var repeated = graph(uses, Map.of()).value(Items.CLAY_BALL);
        near(one.downstreamMultiplier(), repeated.downstreamMultiplier(), "Graph counts a repeated output once across actual recipe IDs");
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        var cycle = graph(List.of(recipe("a", Items.BRICK, Items.CLAY_BALL, 4),
                recipe("b", Items.CLAY_BALL, Items.BRICK, 4)), Map.of());
        check(!cycle.value(Items.BRICK).modeledAcquisition() && !cycle.value(Items.CLAY_BALL).modeledAcquisition(),
                "A recipe-only amplification cycle does not manufacture known acquisition");
    }

    private static void zeroPayoutPreservesAcquisition() throws Exception {
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(new BalanceOverrides.FactOverride(
                "tiny_source", BalanceOverrides.SubjectKind.ITEM, "minecraft:clay", 1000,
                Map.of("resource_value", 1.0, "attainable", true), "fixture", 1)), Map.of()));
        var split = recipe("split_clay", Items.CLAY, Items.CLAY_BALL, 4);
        var pack = new ProceduralValuationIndex.RecipeModel(ResourceLocation.parse("fixture:pack_clay"),
                RecipeType.CRAFTING, Items.CLAY, 1,
                Collections.nCopies(4, new ProceduralValuationIndex.IngredientChoice(List.of(Items.CLAY_BALL))));
        var recipes = List.of(split, pack, recipe("use_clay", Items.CLAY_BALL, Items.BRICK, 1));
        var cheap = graph(recipes, Map.of());
        for (Item item : List.of(Items.CLAY, Items.CLAY_BALL)) {
            var result = cheap.value(item);
            check(result.modeledAcquisition(), "A cheap independently acquired conversion family remains obtainable");
            check(result.totalValue() == 0 && result.routedEssence().values().stream().allMatch(v -> v == 0),
                    "Sub-unit family values retain a safe zero whole payout in every category");
            check(result.conservationStatus().equals("below_integer_precision"),
                    "Zero precision remains an explicit payout limitation, not missing source evidence");
        }
        var output = cheap.value(Items.BRICK);
        check(output.modeledAcquisition() && output.totalValue() > 0,
                "A downstream recipe retains the positive internal acquisition effort of its zero-payout ingredient");

        Map<String, ResourceEvidence> resources = new TreeMap<>();
        Map<String, Map<String, Long>> routes = new TreeMap<>();
        for (Item item : List.of(Items.CLAY_BALL, Items.BRICK)) {
            var result = cheap.value(item); String id = result.itemId().toString();
            resources.put(id, new ResourceEvidence(id, ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE,
                    result.modeledAcquisition(), true, result.totalValue(), result.confidence(), List.of(), List.of()));
            routes.put(id, Map.of(EssenceTypes.UTILITY.id().toString(), 1L));
        }
        var process = new ProductionGraph.Process("fixture:zero_input", "minecraft:crafting",
                List.of(new ProductionGraph.Input(List.of("minecraft:clay_ball"), 1, true)),
                List.of(new ProductionGraph.Output("minecraft:brick", 1, 1, false)),
                0, 0, "fixture", 1, Map.of());
        var economy = EconomyGenerator.generate(new PackEvidence(resources, List.of(), List.of(), Map.of(),
                        List.of(), List.of(), Map.of()), new ProductionGraph(List.of(process), List.of()),
                routes, BalanceSettings.defaults(), BalanceOverrides.empty());
        check(economy.resources().values().stream().allMatch(v -> v.dissolutionYield().microUnits() == 0),
                "Reachable zero-value ingredients do not create positive downstream essence payouts");
        check(economy.invariants().stream().allMatch(v -> v.passed()),
                "Zero-payout acquisition remains subject to ordinary production conservation");

        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        var unknown = graph(recipes, Map.of());
        check(!unknown.value(Items.CLAY).modeledAcquisition() && !unknown.value(Items.CLAY_BALL).modeledAcquisition()
                        && !unknown.value(Items.BRICK).modeledAcquisition(),
                "An unanchored conversion cycle and its descendants remain unresolved");
    }

    private static void suppliedBlockDependencies() throws Exception {
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        var shelf = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:bookshelf"),
                1, 3, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, false, List.of(), null, false);
        var drops = Map.of(Items.BOOK, List.of(shelf));
        check(!graph(List.of(), drops).value(Items.BOOK).modeledAcquisition(),
                "Breaking an unproven supplied block is not an independent three-book source");
        var cycle = graph(List.of(recipe("shelf_cycle", Items.BOOK, Items.BOOKSHELF, 1)), drops);
        check(!cycle.value(Items.BOOK).modeledAcquisition() && !cycle.value(Items.BOOKSHELF).modeledAcquisition(),
                "Craft-and-break amplification cannot establish initial acquisition through itself");
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(new BalanceOverrides.FactOverride(
                "supplied_block", BalanceOverrides.SubjectKind.ITEM, "minecraft:bookshelf", 1000,
                Map.of("resource_value", 90.0, "attainable", true, "stage", "late"), "fixture", 1)), Map.of()));
        var paid = graph(List.of(), drops).value(Items.BOOK);
        check(paid.modeledAcquisition() && paid.totalValue() == 30,
                "A supplied block's full acquisition cost is shared among its three outputs");
        check(paid.factors().stream().anyMatch(f -> f.contains("Supplied block acquisition") && f.contains("90")),
                "A priced block dependency remains visible in valuation diagnostics");
        near(paid.inferredProgressionScore(), .75, "Supplied block progression is inherited by its harvested output");
        var staged = graph(List.of(), drops, Map.of(
                Items.BOOKSHELF, new ProceduralProgressionIndex.ProgressionEvidence(.75, .8, 3, List.of("fixture shelf gate")),
                Items.BOOK, new ProceduralProgressionIndex.ProgressionEvidence(.75, .8, 2, List.of("fixture book gate"))
        )).value(Items.BOOK);
        check(staged.totalValue() == 30 && staged.progressionEvidenceCount() >= 5,
                "Equal prerequisite and output advancement gates carry evidence without charging progression twice");
        var rare = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:bookshelf"),
                .5, 3, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, false, List.of(), null, false);
        check(graph(List.of(), Map.of(Items.BOOK, List.of(rare))).value(Items.BOOK).totalValue() == 60,
                "Failed supplied-block harvest attempts pay for every consumed block");
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        var anchored = graph(List.of(recipe("source_shelf", Items.RAW_COPPER, Items.BOOKSHELF, 1)),
                Map.of(Items.RAW_COPPER, List.of(blockDrop(3.5)), Items.BOOK, List.of(shelf)));
        check(anchored.value(Items.BOOK).modeledAcquisition(),
                "A supplied-block path becomes known after its complete recipe inputs resolve");
        check(Math.abs(anchored.value(Items.BOOK).totalValue() * 3 - anchored.value(Items.BOOKSHELF).totalValue()) <= 2,
                "A supplied-block path pays its actual graph dependency rather than an output intrinsic estimate");
        var unplaceable = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:water"),
                1, 3, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, false, List.of(), null, false);
        check(!graph(List.of(), Map.of(Items.BOOK, List.of(unplaceable))).value(Items.BOOK).modeledAcquisition(),
                "A block with no supplied item or natural placement stays unresolved");
        var crop = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:wheat"),
                1, 1, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, false, List.of(), null, false);
        check(!graph(List.of(), Map.of(Items.WHEAT, List.of(crop))).value(Items.WHEAT).modeledAcquisition(),
                "An unproven planted crop cannot invent its initial seed");
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(new BalanceOverrides.FactOverride(
                "known_seed", BalanceOverrides.SubjectKind.ITEM, "minecraft:wheat_seeds", 1000,
                Map.of("resource_value", 10.0, "attainable", true), "fixture", 1)), Map.of()));
        var planted = graph(List.of(), Map.of(Items.WHEAT, List.of(crop))).value(Items.WHEAT);
        check(planted.modeledAcquisition() && planted.totalValue() == 10,
                "An independently obtainable planting item preserves a complete native crop route");
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
    }

    private static void oreYieldTagEvidence() throws Exception {
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var items = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var ore = net.minecraft.world.level.block.Blocks.COPPER_ORE;
        Method stats = ProceduralValuationIndex.class.getDeclaredMethod("blockSourceStats", ResourceLocation.class,
                net.minecraft.world.level.block.Block.class);
        stats.setAccessible(true);
        Object baseline = stats.invoke(null, ResourceLocation.parse("minecraft:copper_ore"), ore);
        Method multiplier = baseline.getClass().getDeclaredMethod("sourceMultiplier"); multiplier.setAccessible(true);
        Method signals = baseline.getClass().getDeclaredMethod("signals"); signals.setAccessible(true);
        double baselineMultiplier = (double) multiplier.invoke(baseline);
        for (var yieldTag : List.of(ProceduralValuationTags.BLOCK_ORE_RATE_DENSE, ProceduralValuationTags.BLOCK_ORE_RATE_SPARSE)) {
            blocks.bindTags(Map.of(ProceduralValuationTags.BLOCK_ORE_RATE_DENSE, List.of(),
                    ProceduralValuationTags.BLOCK_ORE_RATE_SPARSE, List.of()));
            blocks.bindTags(Map.of(yieldTag, List.of(ore.builtInRegistryHolder())));
            Object tagged = stats.invoke(null, ResourceLocation.parse("minecraft:copper_ore"), ore);
            near((double) multiplier.invoke(tagged), baselineMultiplier,
                    "Measured loot quantity must not also receive coarse ore-yield weighting");
            check(signals.invoke(tagged).toString().contains("actual loot quantity"),
                    "Yield tag remains provenance rather than invented geological abundance");
        }
        blocks.bindTags(Map.of(ProceduralValuationTags.BLOCK_ORE_RATE_DENSE, List.of(),
                ProceduralValuationTags.BLOCK_ORE_RATE_SPARSE, List.of()));
        items.bindTags(Map.of(ProceduralValuationTags.ORES, List.of(Items.COPPER_ORE.builtInRegistryHolder())));
        long ordinaryFallback = graph(List.of(), Map.of()).value(Items.COPPER_ORE).totalValue();
        items.bindTags(Map.of(ProceduralValuationTags.ORE_RATE_DENSE, List.of(Items.COPPER_ORE.builtInRegistryHolder())));
        long denseFallback = graph(List.of(), Map.of()).value(Items.COPPER_ORE).totalValue();
        check(denseFallback < ordinaryFallback, "Coarse yield evidence still informs fallback when no counted loot source exists");
        items.bindTags(Map.of(ProceduralValuationTags.ORES, List.of(), ProceduralValuationTags.ORE_RATE_DENSE, List.of()));
    }

    private static ProceduralValuationIndex.BlockDropSource blockDrop(double output) {
        return new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:copper_ore"),
                1, output, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, true, List.of(), null, false);
    }

    private static ProceduralValuationIndex.RecipeModel recipe(String id, Item input, Item output, int count) {
        return new ProceduralValuationIndex.RecipeModel(ResourceLocation.parse("fixture:" + id), RecipeType.CRAFTING,
                output, count, List.of(new ProceduralValuationIndex.IngredientChoice(List.of(input))));
    }

    private static Graph graph(List<ProceduralValuationIndex.RecipeModel> recipes,
                               Map<Item, List<ProceduralValuationIndex.BlockDropSource>> drops) throws Exception {
        return graph(recipes, drops, Map.of());
    }

    private static Graph graph(List<ProceduralValuationIndex.RecipeModel> recipes,
                               Map<Item, List<ProceduralValuationIndex.BlockDropSource>> drops,
                               Map<Item, ProceduralProgressionIndex.ProgressionEvidence> progression) throws Exception {
        Map<Item, List<ProceduralValuationIndex.RecipeModel>> outputs = new IdentityHashMap<>();
        Map<Item, List<ProceduralValuationIndex.RecipeUse>> uses = new IdentityHashMap<>();
        Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
        items.addAll(List.of(Items.RAW_COPPER, Items.CLAY_BALL, Items.BRICK));
        items.addAll(drops.keySet());
        drops.values().forEach(sources -> sources.forEach(source -> {
            if (source.reusableTool() != null) items.add(source.reusableTool());
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(source.blockId());
            block.map(net.minecraft.world.level.block.Block::asItem).filter(item -> item != Items.AIR).ifPresent(items::add);
        }));
        for (var recipe : recipes) {
            items.add(recipe.outputItem());
            outputs.computeIfAbsent(recipe.outputItem(), key -> new ArrayList<>()).add(recipe);
            for (var ingredient : recipe.ingredients()) for (Item item : ingredient.alternatives()) {
                items.add(item);
                uses.computeIfAbsent(item, key -> new ArrayList<>()).add(new ProceduralValuationIndex.RecipeUse(recipe));
            }
        }
        Constructor<?> constructor = ProceduralValuationIndex.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var terrain = new com.google.gson.JsonObject();
        var state = new com.google.gson.JsonObject(); state.addProperty("Name", "minecraft:copper_ore");
        terrain.add("default_block", state);
        var natural = ProceduralNaturalBlockIndex.fromData(Map.of(), Map.of(), Map.of(),
                Map.of(ResourceLocation.parse("fixture:terrain"), terrain));
        Constructor<?> progressionConstructor = ProceduralProgressionIndex.class.getDeclaredConstructors()[0];
        progressionConstructor.setAccessible(true);
        var progressionIndex = (ProceduralProgressionIndex) progressionConstructor.newInstance(
                progression, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null);
        Method buildEdges = ProceduralValuationIndex.class.getDeclaredMethod("buildConservationEdges", Map.class);
        buildEdges.setAccessible(true); Object edges = buildEdges.invoke(null, outputs);
        Method buildGroups = ProceduralValuationIndex.class.getDeclaredMethod("buildConservationGroups", Map.class);
        buildGroups.setAccessible(true); Object groups = buildGroups.invoke(null, edges);
        var index = (ProceduralValuationIndex) constructor.newInstance(outputs, uses, Map.of(), drops, Map.of(),
                Map.of(), Map.of(), groups, edges, natural, null, progressionIndex, null);
        Class<?> contextType = Class.forName(ProceduralValuationEngine.class.getName() + "$EvaluationContext");
        Constructor<?> contextConstructor = contextType.getDeclaredConstructor(ProceduralValuationIndex.class);
        contextConstructor.setAccessible(true);
        Object context = contextConstructor.newInstance(index);
        Method solve = ProceduralValuationEngine.class.getDeclaredMethod("solveAcquisitionGraph", List.class, contextType);
        solve.setAccessible(true); solve.invoke(null, new ArrayList<>(items), context);
        Method evaluate = ProceduralValuationEngine.class.getDeclaredMethod("evaluateItem", ProceduralValuationIndex.class, Item.class, contextType);
        evaluate.setAccessible(true);
        return new Graph(index, context, evaluate);
    }

    private record Graph(ProceduralValuationIndex index, Object context, Method evaluate) {
        ProceduralValuationResult value(Item item) throws Exception {
            return (ProceduralValuationResult) evaluate.invoke(null, index, item, context);
        }
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < .0000001, message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
