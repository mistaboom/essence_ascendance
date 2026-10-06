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
        var resources = Map.of("fixture:material", resource("fixture:material", AcquisitionSource.Kind.FARMING),
                "fixture:reward", resource("fixture:reward", AcquisitionSource.Kind.QUEST_REWARD));
        var cell = recipe("fixture:cell_recipe", "fixture:cell", "entry", List.of(json("{\"item\":\"fixture:material\"}")));
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
    private static ResourceEvidence finiteResource(String item, long count, double chance) {
        String source = "test:stock/" + item.replace(':', '/');
        var availability = new SourceAvailability(source, SourceAvailability.Category.FINITE_SHARED, SourceAvailability.Scope.SHARED,
                List.of(), List.of(), List.of(), new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of()),
                new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of()), true, chance, count, List.of());
        return new ResourceEvidence(item, ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE, true, true, 1, .9,
                List.of(new AcquisitionSource(source, AcquisitionSource.Kind.WORLD_GENERATION, ProgressionBand.ENTRY,
                        count, false, false, 0, .9, List.of(), "Finite test inventory", availability)), List.of());
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
        var source = new AcquisitionSource(id + "/source", kind, ProgressionBand.EARLY, 1, true, false, 0, .9, List.of(), "Independent fixture source");
        return new ResourceEvidence(id, ProgressionBand.EARLY, Availability.RENEWABLE_MANUAL, Automation.NONE, true, true, 1, .9, List.of(source), List.of());
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean value, String reason) { assertions++; if (!value) throw new AssertionError(reason); }
}
