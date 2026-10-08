package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Exact configuration access stays separate from material acquisition and conservation. */
public final class ConfiguredRecipeAccessTest {
    private static int assertions;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        nativeContracts();
        var oldJet = new ItemStack(Items.LEATHER_CHESTPLATE);
        oldJet.set(DataComponents.CUSTOM_NAME, Component.literal("old configuration")); oldJet.setDamageValue(7);
        var newJet = new ItemStack(Items.DIAMOND_CHESTPLATE);
        newJet.set(DataComponents.CUSTOM_NAME, Component.literal("new configuration"));
        var copied = ConfiguredRecipeAccess.copiedUpgrade(newJet, oldJet, DataComponents.CUSTOM_NAME);
        check(copied.is(Items.DIAMOND_CHESTPLATE) && copied.getDamageValue() == 7
                        && copied.get(DataComponents.CUSTOM_NAME).equals(newJet.get(DataComponents.CUSTOM_NAME)),
                "Audited upgrade projection keeps the declared output item/identity and copies other exact input components");
        check(oldJet.getDamageValue() == 7 && newJet.getDamageValue() == 0
                        && oldJet.get(DataComponents.CUSTOM_NAME).getString().equals("old configuration"),
                "Upgrade projection does not mutate either source stack");
        boundedSupplyPreference();
        renewableBranchPreference();
        independentRenewalAndFiniteClaims();
        configuredBranchPruning();
        countedOperatingFuel();
        conditionalExploration();
        nativeHarvestBills();
        var resources = Map.of("fixture:material", resource("fixture:material", AcquisitionSource.Kind.FARMING),
                "fixture:reward", resource("fixture:reward", AcquisitionSource.Kind.QUEST_REWARD));
        var cell = recipe("fixture:cell_recipe", "fixture:cell", "entry", List.of(json("{\"item\":\"fixture:material\"}")));
        earlierRecipeThanLoot();
        check(!access(new ProductionGraph(List.of(cell), List.of()), Map.of("fixture:material", resource("fixture:material", AcquisitionSource.Kind.TRADE)))
                        .requireStack(stack("fixture:cell", "entry")).placement().reachable(),
                "An item-only repeatable trade row cannot skip NPC access and its payments");
        var device = recipe("fixture:device_recipe", "fixture:device", "entry", List.of(exact("fixture:cell", "entry", true)));
        var graph = new ProductionGraph(List.of(cell, device), List.of());
        var access = access(graph, resources);
        var proof = access.requireStack(stack("fixture:device", "entry"));
        check(proof.placement().reachable() && proof.placement().stage() == ProgressionBand.EARLY,
                "A concrete static configured route works even though its default item is absent/unreachable");
        check(proof.selected().containsAll(List.of("fixture:material", "fixture:cell_recipe", "fixture:device_recipe")),
                "Configuration carries the complete material and recipe witness");
        check(!cell.acquisitionComplete() && !device.conservationComplete(), "Capability proof never rewrites global production completeness");
        check(!resources.containsKey("fixture:device"), "Configuration proof does not insert global resource access");
        check(!access.requireStack(stack("fixture:device", "late")).placement().reachable(),
                "A cheap base item cannot certify another configuration");

        var loose = recipe("fixture:loose", "fixture:loose_device", "entry", List.of(exact("fixture:cell", "entry", false)));
        var opaque = recipe("fixture:opaque", "fixture:opaque_device", "entry", List.of(json("{\"type\":\"fixture:callback\",\"item\":\"fixture:material\"}")));
        var reward = recipe("fixture:reward_recipe", "fixture:reward_device", "entry", List.of(json("{\"item\":\"fixture:reward\"}")));
        var a = recipe("fixture:cycle_a", "fixture:a", "entry", List.of(exact("fixture:b", "entry", true)));
        var b = recipe("fixture:cycle_b", "fixture:b", "entry", List.of(exact("fixture:a", "entry", true)));
        var customDefinition = json(device.metadata().get("effective_definition"));
        customDefinition.addProperty("runtime_class", "fixture.CustomShapedRecipe");
        customDefinition.add("result", stack("fixture:custom_device", "entry"));
        var custom = process("fixture:custom", customDefinition);
        var unknownAccess = access(new ProductionGraph(List.of(cell, loose, opaque, reward, a, b, custom), List.of()), resources);
        for (String id : List.of("fixture:loose_device", "fixture:opaque_device", "fixture:reward_device", "fixture:a", "fixture:custom_device"))
            check(!unknownAccess.requireStack(stack(id, "entry")).placement().reachable(),
                    "Unproven predicate/callback/joint reward/cycle/custom transform stays unknown: " + id);
        var extra = exact("fixture:cell", "entry", true); extra.addProperty("extra_condition", "unknown");
        var extraRecipe = recipe("fixture:extra", "fixture:extra_device", "entry", List.of(extra));
        check(!access(new ProductionGraph(List.of(cell, extraRecipe), List.of()), resources)
                        .requireStack(stack("fixture:extra_device", "entry")).placement().reachable(),
                "Additional predicate fields cannot silently disappear");
        var noDefaults = new ConfiguredRecipeAccess(graph, resources, Map.of(), Set.of(), s -> s.getAsJsonObject("components").deepCopy());
        check(!noDefaults.requireStack(stack("fixture:device", "entry")).placement().reachable(),
                "Strict predicates require the full default component map, not a result patch");
        var sparse = exact("fixture:cell", "entry", true); sparse.getAsJsonObject("components").remove("minecraft:rarity");
        var sparseRecipe = recipe("fixture:sparse", "fixture:sparse_device", "entry", List.of(sparse));
        check(access(new ProductionGraph(List.of(cell, sparseRecipe), List.of()), resources)
                        .requireStack(stack("fixture:sparse_device", "entry")).placement().reachable(),
                "Strict expected stacks retain item defaults even when their positive predicate omits defaults");
        var finiteSource = new AcquisitionSource("fixture:starter/source", AcquisitionSource.Kind.WORLD_GENERATION,
                ProgressionBand.ENTRY, 4, false, false, 0, .9, List.of(), "Exactly four finite starter materials");
        var finite = new ResourceEvidence("fixture:material", ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE,
                true, true, 1, .9, List.of(finiteSource), List.of());
        check(!access(graph, Map.of("fixture:material", finite)).requireStack(stack("fixture:device", "entry")).placement().reachable(),
                "A finite starter inventory cannot establish quantity-unbounded configured crafting");
        var renewableSource = resource("fixture:material", AcquisitionSource.Kind.FARMING).sources().getFirst();
        var mixed = new ResourceEvidence("fixture:material", ProgressionBand.ENTRY, Availability.RENEWABLE_MANUAL, Automation.NONE,
                true, true, 1, .9, List.of(finiteSource, renewableSource), List.of());
        var mixedProof = access(graph, Map.of("fixture:material", mixed)).requireStack(stack("fixture:device", "entry"));
        check(mixedProof.placement().reachable() && mixedProof.placement().stage() == ProgressionBand.EARLY
                        && !mixedProof.placement().acquisition().contains(finiteSource),
                "An independent renewable alternative determines placement without borrowing the earlier finite starter gate");
        var tableDefinition = json(device.metadata().get("effective_definition"));
        tableDefinition.addProperty("width", 3); tableDefinition.addProperty("height", 3);
        tableDefinition.add("result", stack("fixture:table_device", "entry"));
        var tableRecipe = process("fixture:table_recipe", tableDefinition);
        check(!access(new ProductionGraph(List.of(cell, tableRecipe), List.of()), resources)
                        .requireStack(stack("fixture:table_device", "entry")).placement().reachable(),
                "A larger native crafting grid requires independent crafting-table access");
        var tableResources = new HashMap<>(resources);
        tableResources.put("minecraft:crafting_table", resource("minecraft:crafting_table", AcquisitionSource.Kind.FARMING));
        check(access(new ProductionGraph(List.of(cell, tableRecipe), List.of()), tableResources)
                        .requireStack(stack("fixture:table_device", "entry")).placement().reachable(),
                "An independently supported crafting table completes the larger crafting-grid setup");
        nativeZeroDefaults(graph, resources);
        auditedHooklessRecipes(cell, device, resources);
        finiteAndCooking();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("ConfiguredRecipeAccessTest: " + assertions + " assertions passed");
    }
    private static void nativeContracts() {
        try (var stream = ConfiguredRecipeAccessTest.class.getResourceAsStream("/data/minecraft/worldgen/configured_feature/birch.json")) {
            if (stream == null) throw new AssertionError("Native vanilla feature fixture absent");
            var definition = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var shape = com.mistaboom.essence_ascendance.valuation.NativeTreeRenewal.shape(definition).orElseThrow();
            check(shape.log().equals("minecraft:birch_log") && shape.leaf().equals("minecraft:birch_leaves")
                            && shape.minimumLogs() == 5 && shape.minimumLeaves() == 49,
                    "Read actual packaged native birch providers and conservative blob foliage geometry");
            definition.getAsJsonObject("config").getAsJsonObject("foliage_placer").addProperty("type", "test:custom_foliage");
            check(com.mistaboom.essence_ascendance.valuation.NativeTreeRenewal.shape(definition).isEmpty(), "Custom foliage cannot borrow native canopy yield");
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        check(NativeItemActions.describe(Items.BONE_MEAL).stream().map(NativeItemActions.Action::axis).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of(CapabilityAxis.CROP_ACCELERATION, CapabilityAxis.TREE_ACCELERATION)), "Bone meal has two native target contracts");
        check(NativeItemActions.describe(Items.ENDER_PEARL).getFirst().consumesItem()
                        && NativeItemActions.describe(Items.ENDER_PEARL).getFirst().axis() == CapabilityAxis.TELEPORTATION,
                "Pearl consumption and teleportation remain distinct from renewable flight");
        check(NativeItemActions.describe(Items.COMPASS).getFirst().target().equals("world_spawn")
                        && !NativeItemActions.describe(Items.COMPASS).getFirst().consumesItem(), "Compass is reusable but cannot detect arbitrary hidden targets");
        check(NativeItemActions.describe(Items.STICK).isEmpty(), "Native registry membership alone proves no operation");
    }
    private static void finiteAndCooking() {
        finiteRemainders();
        finiteAlternatives();
        var starter = new AcquisitionSource("fixture:guaranteed_start", AcquisitionSource.Kind.WORLD_GENERATION,
                ProgressionBand.ENTRY, 2, false, false, 0, .9, List.of(), "Guaranteed common starting stock",
                new SourceAvailability("fixture:guaranteed_start", SourceAvailability.Category.FINITE_SHARED,
                        SourceAvailability.Scope.TEAM, List.of(), List.of(), List.of(),
                        new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of()),
                        new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of()), true, 1, 2, List.of()));
        var stock = new ResourceEvidence("fixture:log", ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE,
                true, true, 1, .9, List.of(starter), List.of());
        var planks = json(recipe("fixture:planks", "fixture:plank", "base", List.of(json("{\"item\":\"fixture:log\"}")))
                .metadata().get("effective_definition"));
        planks.getAsJsonObject("result").addProperty("count", 4);
        var table = recipe("fixture:table", "minecraft:crafting_table", "base", Collections.nCopies(4, json("{\"item\":\"fixture:plank\"}")));
        var device = json(recipe("fixture:device", "fixture:finite_device", "base", List.of(json("{\"item\":\"fixture:plank\"}")))
                .metadata().get("effective_definition"));
        device.addProperty("width", 3);
        var graph = new ProductionGraph(List.of(process("fixture:planks", planks), table, process("fixture:device", device)), List.of());
        var access = access(graph, Map.of("fixture:log", stock));
        var four = stack("fixture:finite_device", "base"); four.addProperty("count", 4);
        var successful = access.requireFinite(List.of(four));
        check(successful.access().placement().reachable(), "Two finite logs fund table plus four devices through counted plank outputs");
        check(successful.drawnStocks().values().stream().mapToLong(Long::longValue).sum() == 2,
                "Finite source is debited exactly twice; station is reusable");
        var five = four.deepCopy(); five.addProperty("count", 5);
        check(!access.requireFinite(List.of(five)).access().placement().reachable(), "Insufficient stock cannot borrow the crafting table as material");
        check(!access.requireFinite(List.of(four, four)).access().placement().reachable(), "Joint bills cannot double-spend a finite source");
        check(access.requireFinite(List.of(four)).equals(successful), "Independent finite queries are deterministic and do not retain spent player state");
        check(!access.requireStack(four).placement().reachable(), "Finite bootstrap never becomes unlimited production");

        var cooking = json("{\"projection\":\"native_cooking_public_fields\",\"runtime_class\":\"net.minecraft.world.item.crafting.SmeltingRecipe\",\"cookingtime\":200,\"ingredients\":[{\"item\":\"fixture:ore\"}],\"result\":{\"id\":\"fixture:metal\",\"count\":1}}");
        var renewable = Map.of("fixture:ore", resource("fixture:ore", AcquisitionSource.Kind.WORLD_GENERATION),
                "fixture:wood_fuel", resource("fixture:wood_fuel", AcquisitionSource.Kind.FARMING),
                "minecraft:furnace", resource("minecraft:furnace", AcquisitionSource.Kind.WORLD_GENERATION));
        var cookingGraph = new ProductionGraph(List.of(process("fixture:smelt", cooking)), List.of());
        var smelter = new ConfiguredRecipeAccess(cookingGraph, renewable, Map.of(), Set.of(), s -> new JsonObject(), Map.of("fixture:wood_fuel", 300), Map.of());
        check(smelter.requireItem("fixture:metal").placement().reachable(), "Native smelting accepts independently accessible non-coal standard fuel");
        var finiteStation = new TreeMap<>(renewable); finiteStation.put("minecraft:furnace", finiteResource("minecraft:furnace", 1, 1));
        check(new ConfiguredRecipeAccess(cookingGraph, finiteStation, Map.of(), Set.of(), s -> new JsonObject(), Map.of("fixture:wood_fuel", 300), Map.of())
                        .requireItem("fixture:metal").placement().reachable(),
                "One finite furnace supports repeated smelting with separately renewable ore and fuel");
        finiteStation.put("fixture:wood_fuel", finiteResource("fixture:wood_fuel", 1, 1));
        check(!new ConfiguredRecipeAccess(cookingGraph, finiteStation, Map.of(), Set.of(), s -> new JsonObject(), Map.of("fixture:wood_fuel", 300), Map.of())
                        .requireItem("fixture:metal").placement().reachable(),
                "Reusable finite setup does not relax the renewal requirement for consumed fuel");
        check(smelter.requireFinite(List.of(json("{\"id\":\"fixture:metal\",\"count\":4}"))).access().placement().reachable(), "Finite smelting counts ore/fuel with a reusable furnace");
        check(!new ConfiguredRecipeAccess(cookingGraph, renewable, Map.of(), Set.of(), s -> new JsonObject(), Map.of(), Map.of())
                .requireItem("fixture:metal").placement().reachable(), "Missing fuel evidence cannot prove native smelting");
        cooking.addProperty("runtime_class", "fixture:custom_cooking");
        check(!new ConfiguredRecipeAccess(new ProductionGraph(List.of(process("fixture:custom", cooking)), List.of()), renewable,
                Map.of(), Set.of(), s -> new JsonObject(), Map.of("fixture:wood_fuel", 300), Map.of()).requireItem("fixture:metal").placement().reachable(),
                "Custom cooking hooks remain excluded");
    }
    private static void finiteAlternatives() {
        var definition = json("{\"projection\":\"native_shapeless_public_fields\",\"runtime_class\":\"net.minecraft.world.item.crafting.ShapelessRecipe\",\"ingredients\":[{\"tag\":\"fixture:wood\"},{\"item\":\"fixture:a\"}],\"result\":{\"id\":\"fixture:product\"}}");
        var stock = Map.of("fixture:a", finiteResource("fixture:a", 1, 1), "fixture:b", finiteResource("fixture:b", 1, 1));
        var tags = Map.of("fixture:wood", List.of("fixture:a", "fixture:b"));
        var access = new ConfiguredRecipeAccess(new ProductionGraph(List.of(process("fixture:mixed", definition)), List.of()), stock,
                tags, Set.of(), s -> new JsonObject());
        check(access.requireFinite(List.of(json("{\"id\":\"fixture:product\"}"))).access().placement().reachable(),
                "Tag choice backtracks to preserve the only exact ingredient for a later slot");
        definition.getAsJsonArray("ingredients").remove(1);
        var mixed = new ConfiguredRecipeAccess(new ProductionGraph(List.of(process("fixture:tag", definition)), List.of()), stock,
                tags, Set.of(), s -> new JsonObject());
        check(mixed.requireFinite(List.of(json("{\"id\":\"fixture:product\",\"count\":2}"))).access().placement().reachable(),
                "Sequential crafts can use different members of the same ingredient tag");
        check(!mixed.requireFinite(List.of(json("{\"id\":\"fixture:product\",\"count\":3}"))).access().placement().reachable(),
                "Backtracking cannot create a third unit from two finite tag members");
        var first = definition.deepCopy(); first.getAsJsonArray("ingredients").set(0, json("{\"item\":\"fixture:a\"}"));
        var second = definition.deepCopy(); second.getAsJsonArray("ingredients").set(0, json("{\"item\":\"fixture:b\"}"));
        var producers = new ConfiguredRecipeAccess(new ProductionGraph(List.of(process("fixture:first", first), process("fixture:second", second)), List.of()),
                stock, tags, Set.of(), s -> new JsonObject());
        check(producers.requireFinite(List.of(json("{\"id\":\"fixture:product\"}"), json("{\"id\":\"fixture:a\"}"))).access().placement().reachable(),
                "Whole-bill backtracking chooses the producer that leaves a later requested item available");
        check(!mixed.requireFinite(List.of(json("{\"id\":\"fixture:product\",\"count\":1.5}"))).access().placement().reachable(),
                "Fractional request counts cannot truncate into supported whole items");
        var original = finiteResource("fixture:a", 1, 1); var source = original.sources().getFirst();
        var a = source.availability();
        var extra = new AcquisitionSource("fixture:second_stock", source.kind(), source.stage(), source.expectedOutput(), source.renewable(),
                source.rateKnown(), source.unitsPerSecond(), source.confidence(), source.dependencies(), source.reason(),
                new SourceAvailability("fixture:second_stock", a.category(), a.scope(), a.structures(), a.dimensions(), a.conditions(),
                        a.refresh(), a.decay(), a.accessProven(), a.occurrenceChance(), a.expectedPerEvent(), a.uncertainty()));
        var combined = new ResourceEvidence(original.itemId(), original.stage(), original.availability(), original.automation(),
                true, true, original.economicValue(), original.confidence(), List.of(source, extra), List.of());
        var independent = new ConfiguredRecipeAccess(new ProductionGraph(List.of(), List.of()), Map.of("fixture:a", combined),
                Map.of(), Set.of(), s -> new JsonObject());
        check(independent.requireFinite(List.of(json("{\"id\":\"fixture:a\",\"count\":2}"))).access().placement().reachable(),
                "Independent guaranteed source quantities combine in one finite bill");
        check(!independent.requireFinite(List.of(json("{\"id\":\"fixture:a\",\"count\":3}"))).access().placement().reachable(),
                "Combined finite source quotas cannot be overdrawn");
    }
    private static void finiteRemainders() {
        var one = json("{\"projection\":\"native_shapeless_public_fields\",\"runtime_class\":\"net.minecraft.world.item.crafting.ShapelessRecipe\",\"ingredients\":[{\"item\":\"fixture:tool\"}],\"result\":{\"id\":\"fixture:product\"}}");
        var graph = new ProductionGraph(List.of(process("fixture:tool_recipe", one)), List.of());
        var materials = Map.of("fixture:tool", finiteResource("fixture:tool", 1, 1));
        var access = new ConfiguredRecipeAccess(graph, materials, Map.of(), Set.of(), s -> new JsonObject(), Map.of(), Map.of("fixture:tool", "fixture:tool"));
        check(access.requireFinite(List.of(json("{\"id\":\"fixture:product\",\"count\":3}"))).access().placement().reachable(),
                "A reusable catalyst can support sequential crafts without three starting copies");
        one.getAsJsonArray("ingredients").add(json("{\"item\":\"fixture:tool\"}"));
        var doubleSlot = new ConfiguredRecipeAccess(new ProductionGraph(List.of(process("fixture:double", one)), List.of()),
                materials, Map.of(), Set.of(), s -> new JsonObject(), Map.of(), Map.of("fixture:tool", "fixture:tool"));
        check(!doubleSlot.requireFinite(List.of(json("{\"id\":\"fixture:product\"}"))).access().placement().reachable(),
                "One catalyst cannot occupy two simultaneous ingredient slots");
        one.getAsJsonArray("ingredients").remove(1);
        var container = new ConfiguredRecipeAccess(new ProductionGraph(List.of(process("fixture:container", one)), List.of()),
                materials, Map.of(), Set.of(), s -> new JsonObject(), Map.of(), Map.of("fixture:tool", "fixture:empty"));
        check(container.requireFinite(List.of(json("{\"id\":\"fixture:product\"}"), json("{\"id\":\"fixture:empty\"}"))).access().placement().reachable(),
                "Consumed containers return one separately usable remainder");
        check(!container.requireFinite(List.of(json("{\"id\":\"fixture:product\",\"count\":2}"))).access().placement().reachable(),
                "An empty container does not refill its own consumed contents");
        var lucky = new ConfiguredRecipeAccess(graph, Map.of("fixture:tool", finiteResource("fixture:tool", 1, .5)), Map.of(), Set.of(), s -> new JsonObject());
        check(!lucky.requireFinite(List.of(json("{\"id\":\"fixture:product\"}"))).access().placement().reachable(),
                "Expected chance output cannot be drawn as guaranteed finite inventory");
        var smelt = json("{\"projection\":\"native_cooking_public_fields\",\"runtime_class\":\"net.minecraft.world.item.crafting.SmeltingRecipe\",\"cookingtime\":200,\"ingredients\":[{\"item\":\"fixture:ore\"}],\"result\":{\"id\":\"fixture:metal\"}}");
        var stock = new TreeMap<String, ResourceEvidence>(); stock.put("fixture:ore", finiteResource("fixture:ore", 4, 1));
        stock.put("fixture:stick", finiteResource("fixture:stick", 2, 1)); stock.put("minecraft:furnace", finiteResource("minecraft:furnace", 1, 1));
        var smelting = new ProductionGraph(List.of(process("fixture:finite_smelt", smelt)), List.of());
        var furnace = new ConfiguredRecipeAccess(smelting, stock, Map.of(), Set.of(), s -> new JsonObject(), Map.of("fixture:stick", 300), Map.of());
        check(furnace.requireFinite(List.of(json("{\"id\":\"fixture:metal\",\"count\":3}"))).access().placement().reachable(),
                "Counted native burn duration funds exactly three finite operations");
        check(!furnace.requireFinite(List.of(json("{\"id\":\"fixture:metal\",\"count\":4}"))).access().placement().reachable(),
                "A finite burn budget cannot fund a fourth operation");
        var eatsStation = new ConfiguredRecipeAccess(smelting, stock, Map.of(), Set.of(), s -> new JsonObject(), Map.of("minecraft:furnace", 1000), Map.of());
        check(!eatsStation.requireFinite(List.of(json("{\"id\":\"fixture:metal\"}"))).access().placement().reachable(),
                "The only operating station cannot also be consumed as fuel");
    }
    private static void conditionalExploration() {
        String item = "fixture:rare", sourceId = "fixture:chests/ruin";
        var timer = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var availability = new SourceAvailability(sourceId, SourceAvailability.Category.FINITE_SHARED, SourceAvailability.Scope.SHARED,
                List.of("fixture:ruin"), List.of("minecraft:overworld"), List.of("supported distinct structure placement", "native_unlocked_container_binding"),
                timer, timer, true, .01, .03, List.of());
        var source = new AcquisitionSource(sourceId, AcquisitionSource.Kind.LOOT, ProgressionBand.LATE, .03, false, false, 0, .8,
                List.of(), "rare native structure loot", availability);
        var resource = new ResourceEvidence(item, ProgressionBand.LATE, Availability.FINITE, Automation.NONE, true, true, 1, .8, List.of(source), List.of());
        var planner = new ConfiguredRecipeAccess(new ProductionGraph(List.of(), List.of()), Map.of(item, resource), Map.of(), Set.of(), s -> new JsonObject());
        var bill = List.of(json("{\"id\":\"fixture:rare\",\"count\":4}"));
        check(!planner.requireFinite(bill).access().placement().reachable(), "Chance loot cannot become guaranteed finite stock");
        var conditional = planner.requireExploration(bill);
        check(conditional.access().placement().reachable() && conditional.access().placement().stage() == ProgressionBand.LATE
                        && conditional.access().selected().stream().anyMatch(s -> s.contains("4 distinct successful") && s.contains("assumed")),
                "Conditional exploration must retain quantity, actual source stage and its explicit distinct-opportunity assumption");
        check(conditional.drawnStocks().isEmpty(), "Conditional encounters were falsely reported as guaranteed inventory draws");
        check(!planner.requireFinite(bill).access().placement().reachable(), "Exploration mode leaked into the strict finite planner");
        var exclusive = new SourceAvailability(sourceId, availability.category(), availability.scope(), List.of(), List.of(), List.of(), timer, timer, true, .01, .03, List.of());
        var noPlacement = new AcquisitionSource(sourceId, source.kind(), source.stage(), .03, false, false, 0, .8, List.of(), "fixed stock", exclusive);
        var fixed = new ResourceEvidence(item, resource.stage(), resource.availability(), resource.automation(), true, true, 1, .8, List.of(noPlacement), List.of());
        var fixedPlanner = new ConfiguredRecipeAccess(new ProductionGraph(List.of(), List.of()), Map.of(item, fixed), Map.of(), Set.of(), s -> new JsonObject());
        check(!fixedPlanner.requireExploration(bill).access().placement().reachable(), "One fixed stock without recurring structure placement became distinct exploration opportunities");
        check(!planner.requireItem(item).placement().reachable(), "Finite exploration established renewable material access");
        var certainLoot = new SourceAvailability(sourceId, availability.category(), availability.scope(), availability.structures(),
                availability.dimensions(), List.of("native_structure_block_binding"), timer, timer, true, 1, 4, List.of());
        var certainSource = new AcquisitionSource(sourceId, source.kind(), source.stage(), 4, false, false, 0, .8,
                List.of(), "Certain drop only after a conditional structure encounter", certainLoot);
        var certainResource = new ResourceEvidence(item, resource.stage(), resource.availability(), resource.automation(), true, true,
                1, .8, List.of(certainSource), List.of());
        var certainPlanner = new ConfiguredRecipeAccess(new ProductionGraph(List.of(), List.of()), Map.of(item, certainResource), Map.of(), Set.of(), s -> new JsonObject());
        check(!certainPlanner.requireFinite(bill).access().placement().reachable(), "Certain conditional drop became guaranteed existing stock");
        check(certainPlanner.requireExploration(bill).access().placement().reachable(), "Audited one-block structure loot lost its conditional route");
    }
    private static void independentRenewalAndFiniteClaims() {
        var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var available = new SourceAvailability("fixture:native", SourceAvailability.Category.CONDITIONAL_RENEWABLE, SourceAvailability.Scope.SHARED,
                List.of(), List.of(), List.of("Independent input and station proof"), off, off, true, .5, 1, List.of());
        var source = new AcquisitionSource("fixture:native", AcquisitionSource.Kind.PLAYER_ACTION, ProgressionBand.ENTRY,
                1, true, false, 0, .9, List.of(), "Constructive native manual operation", available);
        var resource = new ResourceEvidence("fixture:material", ProgressionBand.ENTRY, Availability.RENEWABLE_MANUAL, Automation.PLAYER_GATED,
                true, true, 1, .9, List.of(source), List.of());
        var claims = Set.of(resource.itemId()); var resources = Map.of(resource.itemId(), resource);
        check(ConfigurationAccess.remainingFiniteClaims(resources, claims).isEmpty(), "Independent native renewal supersedes a finite quest-only lineage");
        check(!new ConfiguredRecipeAccess(new ProductionGraph(List.of(), List.of()), resources, Map.of(), claims, s -> new JsonObject())
                        .requireItem(resource.itemId()).placement().reachable(), "Explicit recipe-only requirements remain binding");
        var uncertain = new SourceAvailability(available.underlyingSource(), available.category(), available.scope(), List.of(), List.of(), List.of(),
                off, off, true, .5, 1, List.of("Unproved setup"));
        var unproven = new AcquisitionSource(source.id(), source.kind(), source.stage(), 1, true, false, 0, .9, List.of(), "advisory", uncertain);
        var advisory = new ResourceEvidence(resource.itemId(), resource.stage(), resource.availability(), resource.automation(), true, true, 1, .9, List.of(unproven), List.of());
        check(ConfigurationAccess.remainingFiniteClaims(Map.of(resource.itemId(), advisory), claims).equals(claims), "Uncertain renewal cannot clear a finite lineage restriction");
        check(ConfigurationAccess.remainingFiniteClaims(Map.of(resource.itemId(), resource(resource.itemId(), AcquisitionSource.Kind.QUEST_REWARD)), claims).equals(claims),
                "Reward-only renewable labels cannot bypass independent acquisition");
        for (var kind : List.of(AcquisitionSource.Kind.INFINITE_BULK, AcquisitionSource.Kind.FARMING, AcquisitionSource.Kind.MACHINE)) {
            var unbound = new AcquisitionSource("fixture:classification", kind, ProgressionBand.ENTRY, 1, true, false, 0, .99,
                    List.of(), "Renewability classified but setup not established");
            var row = new ResourceEvidence(resource.itemId(), resource.stage(), resource.availability(), resource.automation(),
                    true, true, 1, .99, List.of(unbound), List.of());
            var planner = access(new ProductionGraph(List.of(), List.of()), Map.of(row.itemId(), row));
            check(!planner.requireItem(row.itemId()).placement().reachable(), "Unbound renewable classification cannot pay a repeated material: " + kind);
            check(!planner.requireFinite(List.of(NativeConsumables.request(row.itemId(), 1))).access().placement().reachable(),
                    "Unbound renewable classification cannot invent finite stock: " + kind);
        }
    }
    private static void renewableBranchPreference() {
        var recipes = new ArrayList<ProductionGraph.Process>();
        for (int i = 0; i < 600; i++) recipes.add(recipe("fixture:a_dead_" + i, "fixture:product", "entry",
                Collections.nCopies(8, json("{\"item\":\"fixture:absent\"}"))));
        recipes.add(recipe("fixture:z_live", "fixture:product", "entry", List.of(json("{\"item\":\"fixture:material\"}"))));
        var planner = access(new ProductionGraph(recipes, List.of()), Map.of("fixture:material", resource("fixture:material", AcquisitionSource.Kind.FARMING)));
        check(planner.requireItem("fixture:product").placement().reachable(),
                "An independently renewable route precedes thousands of irrelevant recursive visits without increasing the work bound");
        check(!planner.requireItem("fixture:absent").placement().reachable(), "Optimistic ordering must not invent a source");
        var deep = new ArrayList<ProductionGraph.Process>(); String previous = "fixture:material";
        for (int i = 0; i < 16; i++) {
            String next = "fixture:layer_" + String.format(java.util.Locale.ROOT, "%02d", i);
            var definition = new JsonObject(); definition.addProperty("projection", "native_shaped_public_fields");
            definition.addProperty("runtime_class", "net.minecraft.world.item.crafting.ShapedRecipe");
            definition.addProperty("width", 2); definition.addProperty("height", 1);
            var ingredients = new JsonArray(); var ingredient = new JsonObject(); ingredient.addProperty("item", previous);
            ingredients.add(ingredient); ingredients.add(ingredient.deepCopy()); definition.add("ingredients", ingredients);
            var output = new JsonObject(); output.addProperty("id", next); definition.add("result", output);
            deep.add(process(next, definition)); previous = next;
        }
        check(access(new ProductionGraph(deep, List.of()), Map.of("fixture:material", resource("fixture:material", AcquisitionSource.Kind.FARMING)))
                        .requireItem(previous).placement().reachable(),
                "Large optimistic ordering costs must saturate without becoming false unreachable-path evidence");
    }
    private static void boundedSupplyPreference() {
        var names = new ArrayList<String>();
        for (int i = 0; i < 160; i++) names.add("fixture:absent_" + i);
        names.add("fixture:renewable");
        var definition = json("{\"projection\":\"native_shapeless_public_fields\",\"runtime_class\":\"net.minecraft.world.item.crafting.ShapelessRecipe\",\"ingredients\":[{\"tag\":\"fixture:options\"}],\"result\":{\"id\":\"fixture:product\"}}");
        var planner = new ConfiguredRecipeAccess(new ProductionGraph(List.of(process("fixture:preferred", definition)), List.of()),
                Map.of("fixture:renewable", resource("fixture:renewable", AcquisitionSource.Kind.FARMING)),
                Map.of("fixture:options", names), Set.of(), s -> new JsonObject());
        check(planner.requireFinite(List.of(json("{\"id\":\"fixture:product\"}"))).access().placement().reachable(),
                "Known repeatable alternatives must precede more than the bounded number of unproven tag entries");
        check(!planner.requireFinite(List.of(json("{\"id\":\"fixture:absent\"}"), json("{\"id\":\"fixture:product\"}")))
                        .access().placement().reachable(), "Later obtainable materials cannot repair a missing required stack");
        names.removeLast(); names.add("fixture:crafted");
        var fromStock = json("{\"projection\":\"native_shapeless_public_fields\",\"runtime_class\":\"net.minecraft.world.item.crafting.ShapelessRecipe\",\"ingredients\":[{\"item\":\"fixture:finite\"}],\"result\":{\"id\":\"fixture:crafted\"}}");
        var finitePlanner = new ConfiguredRecipeAccess(new ProductionGraph(List.of(process("fixture:preferred", definition), process("fixture:from_stock", fromStock)), List.of()),
                Map.of("fixture:finite", finiteResource("fixture:finite", 1, 1)), Map.of("fixture:options", names), Set.of(), s -> new JsonObject());
        check(finitePlanner.requireFinite(List.of(json("{\"id\":\"fixture:product\"}"))).access().placement().reachable(),
                "A supported finite crafting route must precede unrelated tag choices under the unchanged plan bound");
        check(!finitePlanner.requireFinite(List.of(json("{\"id\":\"fixture:product\",\"count\":2}"))).access().placement().reachable(),
                "Optimistic ordering cannot authorize reusing an exhausted finite stock");
    }
    private static ResourceEvidence finiteResource(String item, long count, double chance) {
        String source = "test:stock/" + item.replace(':', '/');
        var availability = new SourceAvailability(source, SourceAvailability.Category.FINITE_SHARED, SourceAvailability.Scope.SHARED,
                List.of(), List.of(), List.of(), new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of()),
                new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of()), true, chance, count, List.of());
        return new ResourceEvidence(item, ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE, true, true, 1, .9,
                List.of(new AcquisitionSource(source, AcquisitionSource.Kind.WORLD_GENERATION, ProgressionBand.ENTRY,
                        count, false, false, 0, .9, List.of(), "Finite test inventory", availability)), List.of());
    }
    private static void nativeHarvestBills() {
        var source = new AcquisitionSource("minecraft:lapis_ore", AcquisitionSource.Kind.WORLD_GENERATION,
                ProgressionBand.ENTRY, 6.5, false, false, 0, .9, List.of(), "Observed native placement");
        var ore = new ResourceEvidence("fixture:gem", ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE,
                true, true, 1, .9, List.of(source), List.of());
        var resources = Map.of("fixture:gem", ore, "fixture:pick", finiteResource("fixture:pick", 1, 1));
        var routes = Map.of("fixture:gem", List.of(new com.mistaboom.essence_ascendance.valuation.NativeHarvestSupplies.Route(
                "minecraft:lapis_ore", "fixture:pick", .5, 6.5, source)));
        var planner = access(new ProductionGraph(List.of(), List.of()), resources).harvests(routes);
        check(!planner.requireFinite(List.of(NativeConsumables.request("fixture:gem", 1))).access().placement().reachable(),
                "Observed natural placement is not a guaranteed finite inventory");
        var proof = planner.requireExploration(List.of(NativeConsumables.request("fixture:gem", 1)));
        check(proof.access().placement().reachable() && !proof.drawnStocks().isEmpty(), "Conditional harvest pays a fresh tool from the joint finite ledger");
        check(!planner.requireExploration(List.of(NativeConsumables.request("fixture:gem", 2))).access().placement().reachable(),
                "Expected multi-drop yield cannot fabricate multiple guaranteed items or skip the conservative tool bill");
        check(!planner.requireExploration(List.of(NativeConsumables.request("fixture:gem", 1), NativeConsumables.request("fixture:pick", 1))).access().placement().reachable(),
                "A tool already consumed by harvesting cannot simultaneously satisfy later equipment");
        check(!access(new ProductionGraph(List.of(), List.of()), Map.of("fixture:gem", ore)).harvests(routes)
                        .requireExploration(List.of(NativeConsumables.request("fixture:gem", 1))).access().placement().reachable(),
                "A native ore observation alone cannot invent its required tool");
        check(!planner.requireItem("fixture:gem").placement().reachable(), "Conditional natural harvest does not become renewable supply");
        var advisory = new ResourceEvidence(ore.itemId(), ore.stage(), ore.availability(), ore.automation(), true, true, 1, .35, ore.sources(), List.of());
        check(access(new ProductionGraph(List.of(), List.of()), Map.of("fixture:gem", advisory, "fixture:pick", finiteResource("fixture:pick", 1, 1)))
                        .harvests(routes).requireExploration(List.of(NativeConsumables.request("fixture:gem", 1))).access().placement().reachable(),
                "A re-evaluated native natural/tool witness is not erased by an unrelated low-confidence aggregate recipe row");
    }
    private static void auditedHooklessRecipes(ProductionGraph.Process cell, ProductionGraph.Process device,
            Map<String, ResourceEvidence> resources) {
        var definition = json(device.metadata().get("effective_definition"));
        definition.addProperty("runtime_class", "dev.latvian.mods.kubejs.recipe.special.ShapedKubeJSRecipe");
        definition.addProperty("behavior_adapter", "kubejs:published_crafting_hooks");
        definition.addProperty("behavior_api_version", "2101.7.2-build.374");
        definition.addProperty("ingredient_action_count", 0);
        definition.addProperty("modify_result_present", false);
        check(access(new ProductionGraph(List.of(cell, process("fixture:hookless", definition)), List.of()), resources)
                        .requireStack(stack("fixture:device", "entry")).placement().reachable(),
                "Audited exact hookless native crafting subclasses retain supported static configured output");
        for (String field : List.of("runtime_class", "behavior_adapter", "behavior_api_version", "ingredient_action_count", "modify_result_present")) {
            var changed = definition.deepCopy();
            if (field.equals("ingredient_action_count")) changed.addProperty(field, 1);
            else if (field.equals("modify_result_present")) changed.addProperty(field, true);
            else changed.addProperty(field, "fixture:unsupported");
            check(!access(new ProductionGraph(List.of(cell, process("fixture:unsupported_" + field, changed)), List.of()), resources)
                            .requireStack(stack("fixture:device", "entry")).placement().reachable(),
                    "An unproved class/version/adapter or installed crafting hook cannot become an exact static witness: " + field);
        }
    }
    private static void nativeZeroDefaults(ProductionGraph graph, Map<String, ResourceEvidence> resources) {
        var zeroWear = DataComponentMap.builder().set(DataComponents.MAX_DAMAGE, 0).build();
        check(DataComponentMap.CODEC.encodeStart(JsonOps.INSTANCE, zeroWear).error().isPresent(),
                "The native zero-wear default reproduces the full-component persistence-codec rejection");
        var source = new ItemStack(Items.LEATHER_CHESTPLATE);
        source.set(DataComponents.MAX_DAMAGE, 0);
        var snapshot = ConfiguredRecipeAccess.nativeComponents(source);
        check(snapshot.get(DataComponents.MAX_DAMAGE).equals(0), "The complete native snapshot retains a zero wear pool");
        source.set(DataComponents.MAX_DAMAGE, 1);
        check(snapshot.get(DataComponents.MAX_DAMAGE).equals(0)
                        && !snapshot.equals(ConfiguredRecipeAccess.nativeComponents(source)),
                "Native component snapshots retain identity after the source changes; zero cannot collapse into positive wear");
        var nativeAccess = new ConfiguredRecipeAccess(graph, resources, Map.of(), Set.of(), definition -> {
            var item = new ItemStack(Items.LEATHER_CHESTPLATE);
            item.set(DataComponents.MAX_DAMAGE, 0);
            item.set(DataComponents.CUSTOM_NAME, Component.literal(definition.getAsJsonObject("components").get("fixture:tier").getAsString()));
            return ConfiguredRecipeAccess.nativeComponents(item);
        });
        check(nativeAccess.requireStack(stack("fixture:device", "entry")).placement().reachable(),
                "Native exact configured crafting retains all defaults without serializing a zero wear pool");
        check(!nativeAccess.requireStack(stack("fixture:device", "late")).placement().reachable(),
                "Avoiding default serialization does not relax strict configuration equality or reuse another configuration's cache proof");
    }
    private static ConfiguredRecipeAccess access(ProductionGraph graph, Map<String, ResourceEvidence> resources) {
        return new ConfiguredRecipeAccess(graph, resources, Map.of(), Set.of(), s -> {
            var value = s.has("components") ? s.getAsJsonObject("components").deepCopy() : new JsonObject();
            value.addProperty("minecraft:rarity", "common"); return value;
        });
    }
    private static void configuredBranchPruning() {
        var recipes = new ArrayList<ProductionGraph.Process>();
        for (int i = 0; i < 150; i++) recipes.add(recipe("fixture:wrong_" + i, "fixture:cell", "wrong_" + i,
                List.of(json("{\"item\":\"fixture:missing\"}"))));
        recipes.add(recipe("fixture:matching", "fixture:cell", "entry", List.of(json("{\"item\":\"fixture:material\"}"))));
        recipes.add(recipe("fixture:device", "fixture:device", "entry", List.of(exact("fixture:cell", "entry", true))));
        var access = access(new ProductionGraph(recipes, List.of()), Map.of("fixture:material", resource("fixture:material", AcquisitionSource.Kind.FARMING)));
        var proof = access.requireFinite(List.of(stack("fixture:device", "entry"))).access();
        check(proof.placement().reachable() && proof.selected().contains("fixture:matching"),
                "Impossible component variants must not exhaust the unchanged 128-plan bound");
        check(!access.requireFinite(List.of(stack("fixture:cell", "absent"))).access().placement().reachable(),
                "Filtering cannot substitute another configuration for a missing one");
    }
    private static void countedOperatingFuel() {
        var resources = Map.of("fixture:wood", finiteResource("fixture:wood", 3, 1));
        var access = new ConfiguredRecipeAccess(new ProductionGraph(List.of(), List.of()), resources, Map.of(), Set.of(),
                s -> new JsonObject(), Map.of("fixture:wood", 100), Map.of());
        var setup = List.of(json("{\"id\":\"fixture:wood\",\"count\":2}"));
        check(access.requireFueledSetup(setup, 1250, 50, 625).placement().reachable(),
                "One remaining whole fuel item pays two actual generating ticks");
        check(!access.requireFueledSetup(setup, 1251, 50, 625).placement().reachable(),
                "Crafting and operating cannot spend the same finite wood twice or round down fuel count");
        var shortFuel = new ConfiguredRecipeAccess(new ProductionGraph(List.of(), List.of()), resources, Map.of(), Set.of(),
                s -> new JsonObject(), Map.of("fixture:wood", 49), Map.of());
        check(!shortFuel.requireFueledSetup(setup, 1, 50, 625).placement().reachable(),
                "Fuel shorter than one conversion operation provides zero usable energy");
    }
    private static void earlierRecipeThanLoot() {
        var timer = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var source = new AcquisitionSource("fixture:late_loot", AcquisitionSource.Kind.LOOT, ProgressionBand.LATE, 1, false, false, 0, .9, List.of(), "Late optional loot",
                new SourceAvailability("fixture:late_loot", SourceAvailability.Category.FINITE_SHARED, SourceAvailability.Scope.SHARED,
                        List.of("fixture:late_structure"), List.of("minecraft:overworld"), List.of("native_unlocked_container_binding"), timer, timer, true, .5, 1, List.of()));
        var late = new ResourceEvidence("fixture:ingot_block", ProgressionBand.LATE, Availability.FINITE, Automation.NONE, true, true, 1, .9, List.of(source), List.of());
        var definition = json(recipe("fixture:block", "fixture:ingot_block", "unused", List.of(json("{\"item\":\"fixture:material\"}"))).metadata().get("effective_definition"));
        definition.getAsJsonObject("result").remove("components");
        var graph = new ProductionGraph(List.of(process("fixture:block", definition)), List.of());
        var request = json("{\"id\":\"fixture:ingot_block\",\"count\":3}");
        var access = access(graph, Map.of("fixture:ingot_block", late, "fixture:material", resource("fixture:material", AcquisitionSource.Kind.FARMING)));
        var proof = access.requireExploration(List.of(request));
        check(proof.access().placement().reachable() && proof.access().placement().stage() == ProgressionBand.EARLY,
                "Late direct loot hid an independently earlier craft route");
        check(proof.access().placement().acquisition().stream().noneMatch(s -> s.id().equals(source.id())), "Earlier route silently retained late loot dependency");
        var blocked = access(graph, Map.of("fixture:ingot_block", late));
        check(blocked.requireExploration(List.of(request)).access().placement().stage() == ProgressionBand.LATE, "Unfunded recipe lowered actual loot gate");
        check(!blocked.requireFinite(List.of(request)).access().placement().reachable(), "Cached conditional/ceiling proof leaked into guaranteed-stock request");
        check(access.requireExploration(List.of(request)).equals(proof), "Independent repeated bill cache changed its finite ledger or placement");
    }
    private static ProductionGraph.Process recipe(String id, String item, String tier, List<JsonObject> ingredients) {
        var definition = new JsonObject();
        definition.addProperty("projection", "native_shaped_public_fields"); definition.addProperty("runtime_class", "net.minecraft.world.item.crafting.ShapedRecipe");
        definition.addProperty("width", 1); definition.addProperty("height", 1);
        var inputs = new JsonArray(); ingredients.forEach(inputs::add); definition.add("ingredients", inputs); definition.add("result", stack(item, tier));
        return process(id, definition);
    }
    private static ProductionGraph.Process process(String id, JsonObject definition) {
        return new ProductionGraph.Process(id, "minecraft:crafting", List.of(),
                List.of(new ProductionGraph.Output(definition.getAsJsonObject("result").get("id").getAsString(), 1, 1, false)), 0, 0,
                "fixture", .9, Map.of("effective_definition", definition.toString(), "acquisition_complete", "false", "conservation_complete", "false"));
    }
    private static JsonObject stack(String item, String tier) {
        var value = new JsonObject(); value.addProperty("id", item); value.addProperty("count", 1);
        var components = new JsonObject(); components.addProperty("fixture:tier", tier); value.add("components", components); return value;
    }
    private static JsonObject exact(String item, String tier, boolean strict) {
        var value = new JsonObject(); value.addProperty("type", "neoforge:components"); value.addProperty("items", item); value.addProperty("strict", strict);
        var components = stack(item, tier).getAsJsonObject("components"); components.addProperty("minecraft:rarity", "common"); value.add("components", components); return value;
    }
    private static ResourceEvidence resource(String id, AcquisitionSource.Kind kind) {
        var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var available = kind == AcquisitionSource.Kind.TRADE ? null : new SourceAvailability(id + "/source",
                SourceAvailability.Category.CONDITIONAL_RENEWABLE, SourceAvailability.Scope.SHARED,
                List.of(), List.of(), List.of("Synthetic complete setup/input contract"), off, off, true, 1, 1, List.of());
        var source = new AcquisitionSource(id + "/source", kind, ProgressionBand.EARLY, 1, true, false, 0, .9, List.of(), "Independent fixture source", available);
        return new ResourceEvidence(id, ProgressionBand.EARLY, Availability.RENEWABLE_MANUAL, Automation.NONE, true, true, 1, .9, List.of(source), List.of());
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean value, String reason) { assertions++; if (!value) throw new AssertionError(reason); }
}
