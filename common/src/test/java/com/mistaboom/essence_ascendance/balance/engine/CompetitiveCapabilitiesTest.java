package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.capability.InstalledCapabilityProviders;
import com.mistaboom.essence_ascendance.balance.capability.TimeBottleCapabilityProvider;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.generated.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.*;
import java.util.*;
import java.nio.file.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

public final class CompetitiveCapabilitiesTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> e.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        progression(); alternativesAndOutliers(); semantics(); candidateCoverage(); configurations(); configurationAccess(); configuredStacks(); production(); nativeEnchantments(); preservation(); timeBottle(); adapters(); reports();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("CompetitiveCapabilitiesTest: " + checks + " checks passed");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static Functional fact(String id, ProgressionBand band, CapabilityAxis axis, double magnitude) {
        var m = CompetitiveCapabilities.measurement(axis, magnitude, "test_unit", "test_supported_behavior", Scope.self(), Operation.manual(),
                "synthetic_provider", Origin.TYPED_ADAPTER, List.of());
        return new Functional(new CapabilityEvidence(id, band, Map.of(axis, magnitude), true, .9, "Synthetic attainable witness"),
                "configuration", List.of(m), List.of(new AcquisitionSource("recipe:" + id, AcquisitionSource.Kind.RECIPE, band, 1, false, false, 0,
                .9, List.of(), "Supported recipe witness")), true);
    }
    private static double frontier(List<Functional> facts, ProgressionBand band, CapabilityAxis axis) {
        return RobustFrontiers.competitive(facts, "WINSORIZE").stream().filter(f -> f.band() == band && f.comparisonKey().contains("/" + axis + "/"))
                .mapToDouble(RobustFrontiers.CompetitiveFrontier::magnitude).max().orElse(0);
    }
    private static void progression() {
        var early = fact("test:sword", ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE, 12);
        var late = fact("test:superweapon", ProgressionBand.APEX, CapabilityAxis.MELEE_DAMAGE, 12000);
        var facts = List.of(early, late);
        check(frontier(facts, ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE) == 12, "Late competitor leaked into entry");
        check(frontier(facts, ProgressionBand.LATE, CapabilityAxis.MELEE_DAMAGE) == 12, "Earlier usable source was lost");
        check(frontier(facts, ProgressionBand.APEX, CapabilityAxis.MELEE_DAMAGE) == 12000, "Late cohort was incorrectly capped against obsolete gear");
        var strongEarly = fact("test:early_power", ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE, 100);
        check(frontier(List.of(strongEarly), ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE) == 100, "Strong early source ignored");
        var source = new AcquisitionSource("quest:early", AcquisitionSource.Kind.QUEST_REWARD, ProgressionBand.EARLY, 1, false, false, 0,
                .9, List.of(), "Independent early reward");
        var resources = Map.of("test:superweapon", resource("test:superweapon", ProgressionBand.APEX, List.of(source)));
        check(CompetitiveCapabilities.placement(resources, "test:superweapon").stage() == ProgressionBand.EARLY, "Late craft route delayed early quest reward");
        for (var kind : List.of(AcquisitionSource.Kind.RECIPE, AcquisitionSource.Kind.MACHINE, AcquisitionSource.Kind.PLAYER_ACTION)) {
            var placeholder = new AcquisitionSource("placeholder:route",kind,ProgressionBand.ENTRY,1,false,false,0,.9,List.of(),"Nominal source row; route band unresolved");
            check(CompetitiveCapabilities.placement(Map.of("test:late",resource("test:late",ProgressionBand.LATE,List.of(placeholder))),"test:late")
                    .stage() == ProgressionBand.LATE,"Nominal ENTRY source bypassed solved progression for " + kind);
        }
        check(!CompetitiveCapabilities.placement(Map.of(), "test:creative").reachable(), "Registry-only source claimed access");
        var unreachable = new Functional(new CapabilityEvidence("test:admin", ProgressionBand.ENTRY, Map.of(), false, 1, "Administrative"),
                "admin", strongEarly.measurements(), List.of(), false);
        check(frontier(List.of(early, unreachable), ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE) == 12, "Admin source affected frontier");
    }
    private static void alternativesAndOutliers() {
        List<Functional> equivalent = new ArrayList<>();
        for (int i = 0; i < 3; i++) equivalent.add(fact("test:alternative" + i, ProgressionBand.ENTRY, CapabilityAxis.CROP_ACCELERATION, 10));
        check(frontier(equivalent, ProgressionBand.ENTRY, CapabilityAxis.CROP_ACCELERATION) == 10, "Equivalent mods summed power");
        var normal = List.of(fact("test:a", ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE, 10),
                fact("test:b", ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE, 12), fact("test:c", ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE, 14),
                fact("test:bad", ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE, 1e9));
        check(frontier(normal, ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE) <= 100, "Pathological outlier defined whole band");
        check(RobustFrontiers.competitive(normal, "WINSORIZE").getFirst().capped(), "Cap/raw maximum not reported");
        check(RobustFrontiers.competitive(normal, "INCLUDE_ATTAINABLE").getFirst().magnitude() == 1e9, "Explicit outlier policy ignored");
    }
    private static void semantics() {
        CapabilitySink sink = new CapabilitySink();
        for (CapabilityAxis axis : List.of(CapabilityAxis.AUTOMATED_EXTRACTION, CapabilityAxis.AUTOMATED_FISHING, CapabilityAxis.CROP_ACCELERATION,
                CapabilityAxis.SPAWN_SUPPRESSION, CapabilityAxis.MACHINE_ACCELERATION, CapabilityAxis.INDESTRUCTIBILITY)) {
            var f = fact("test:" + axis, ProgressionBand.MID, axis, 10); sink.add(f);
            check(frontier(sink.evidence(), ProgressionBand.MID, axis) == 10, "Missing generic domain " + axis);
        }
        var spread = fact("test:multidomain", ProgressionBand.LATE, CapabilityAxis.MELEE_DAMAGE, 40);
        var measurements = new ArrayList<>(spread.measurements());
        measurements.addAll(fact("test:x", ProgressionBand.LATE, CapabilityAxis.FLIGHT, 1).measurements());
        measurements.addAll(fact("test:y", ProgressionBand.LATE, CapabilityAxis.SHIELD_CAPACITY, 400).measurements());
        var multi = new Functional(spread.source(), "modular", measurements, spread.acquisition(), true);
        check(frontier(List.of(multi), ProgressionBand.LATE, CapabilityAxis.FLIGHT) == 1, "Multi-domain source lost mobility");
        check(frontier(List.of(multi), ProgressionBand.LATE, CapabilityAxis.SHIELD_CAPACITY) == 400, "Multi-domain source lost defense");
        var unknown = CompetitiveCapabilities.measurement(CapabilityAxis.MACHINE_ACCELERATION, null, "unknown", "opaque", Scope.unknown(), Operation.manual(),
                "opaque_adapter", Origin.TYPED_ADAPTER, List.of("Custom callback unavailable"));
        sink.add(new Functional(spread.source(), "opaque", List.of(unknown), List.of(), false));
        NativeCapabilityReader.candidateName("future:quarry_quarry", sink);
        check(sink.candidates().stream().anyMatch(c -> c.provider().equals("nomenclature")), "Nomenclature candidate missing/repeated token failed");
        check(frontier(sink.evidence(), ProgressionBand.MID, CapabilityAxis.MACHINE_ACCELERATION) == 10, "Opaque evidence became numeric power");
        boolean rejected = false;
        try { new Measurement(CapabilityFamily.RESOURCE_AUTOMATION, CapabilityAxis.AUTOMATED_FISHING, 600.0, "fish/hour", "name", Scope.unknown(),
                Operation.manual(), "nomenclature", Origin.NOMENCLATURE, List.of()); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Names can establish throughput");
        for (int i = 0; i < 2000; i++) sink.candidate("test:unknown" + i, "opaque", "Unknown behavior");
        check(sink.candidates().size() <= 512 && sink.counts().get("candidateCount") >= 2000, "Unbounded unknown diagnostics or lost census count");
        // Reproduce the installed report: large early sources filled all 512 slots before typed providers.
        sink = new CapabilitySink();
        for (int provider = 0; provider < 12; provider++) for (int i = 0; i < 100; i++)
            sink.candidate("test:early" + i, "earlier" + provider, "unknown");
        sink.candidate("test:high_impact", "later_typed_provider", "configured gear unsupported");
        check(sink.candidates().size() == 512 && sink.candidates().stream().anyMatch(c -> c.provider().equals("later_typed_provider")),
                "Late typed provider starved behind earlier diagnostics");
        check(sink.counts().get("candidateCount") == 1201, "Fair sampling lost complete unknown counts");
    }
    private static void candidateCoverage() {
        CapabilitySink completed = new CapabilitySink();
        for (int i = 0; i < 200; i++) completed.candidate("test:unknown" + i, "scoped", "No supported operating contract",
                Set.of(CapabilityAxis.FLIGHT, CapabilityAxis.SHIELD_CAPACITY), CapabilitySink.Reason.UNKNOWN_BEHAVIOR);
        for (int i = 0; i < 73; i++) completed.candidate("test:legacy" + i, "legacy", "Unknown scope");
        check(completed.candidates().stream().filter(c -> c.provider().equals("scoped")).count() == 64
                && completed.candidateCoverage().get(CapabilityAxis.FLIGHT).get(CapabilitySink.Reason.UNKNOWN_BEHAVIOR) == 200
                && completed.candidateCoverage().get(CapabilityAxis.SHIELD_CAPACITY).get(CapabilitySink.Reason.UNKNOWN_BEHAVIOR) == 200,
                "Scoped reason census lost candidates beyond the bounded diagnostic sample");
        check(completed.unscopedCandidateReasons().get(CapabilitySink.Reason.UNCLASSIFIED) == 73
                && completed.counts().get("candidateCount") == 273, "Unscoped legacy census or multi-axis candidate total was miscounted");
        CapabilitySink merged = new CapabilitySink();
        merged.candidate("test:gate", "scoped", "Acquisition not proven", Set.of(CapabilityAxis.FLIGHT), CapabilitySink.Reason.ACCESS_UNPROVEN);
        merged.merge(completed);
        check(merged.counts().get("candidateCount") == 274
                && merged.candidateCoverage().get(CapabilityAxis.FLIGHT).get(CapabilitySink.Reason.UNKNOWN_BEHAVIOR) == 200
                && merged.candidateCoverage().get(CapabilityAxis.FLIGHT).get(CapabilitySink.Reason.ACCESS_UNPROVEN) == 1
                && merged.unscopedCandidateReasons().get(CapabilitySink.Reason.UNCLASSIFIED) == 73,
                "Completed output merge recounted sampled diagnostics or lost full reason counts");
        check(merged.candidates().stream().filter(c -> c.provider().equals("scoped")).count() == 64 && merged.evidence().isEmpty(),
                "Merged diagnostics exceeded the provider bound or became measured capability evidence");
        var sourceAxes = EnumSet.of(CapabilityAxis.FLIGHT);
        var candidate = new CapabilitySink.Candidate("test:copy", "typed", "Unsupported API", sourceAxes, CapabilitySink.Reason.UNSUPPORTED_API);
        sourceAxes.clear(); sourceAxes.add(CapabilityAxis.ARMOR);
        check(candidate.axes().equals(Set.of(CapabilityAxis.FLIGHT)), "Source axis collection mutations leaked into saved candidate scope");
        boolean rejected = false;
        try { candidate.axes().add(CapabilityAxis.ARMOR); } catch (UnsupportedOperationException expected) { rejected = true; }
        check(rejected, "Candidate scope permits mutation after publication");
        var legacy = BalanceDocument.GSON.fromJson("{\"subject\":\"test:old\",\"provider\":\"saved_provider\",\"detail\":\"Legacy diagnostic\"}",
                CapabilitySink.Candidate.class);
        check(legacy.axes().isEmpty() && legacy.reason() == CapabilitySink.Reason.UNCLASSIFIED
                && legacy.subject().equals("test:old") && legacy.provider().equals("saved_provider"),
                "Legacy three-field candidate did not retain explicit unclassified scope");
    }
    private static void configurations() {
        var base = fact("test:modular", ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE, 10);
        var stronger = fact("test:modular", ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE, 40);
        var late = fact("test:modular", ProgressionBand.APEX, CapabilityAxis.MELEE_DAMAGE, 400);
        CapabilitySink sink = new CapabilitySink(); sink.configurations(List.of(base, stronger, late));
        check(sink.evidence().size() == 2, "Dominance pruning failed or removed earlier configuration");
        check(sink.counts().get("prunedConfigurations") == 1, "Pruned count missing");
        check(frontier(sink.evidence(), ProgressionBand.ENTRY, CapabilityAxis.MELEE_DAMAGE) == 40, "Modular strongest early configuration not retained");
        var costs = stronger.measurements().getFirst();
        var expensive = new Measurement(costs.family(), costs.axis(), 80.0, costs.unit(), costs.applicability(), costs.scope(),
                new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.UNKNOWN, null, null, null, null, List.of("100 energy/hit"), List.of("equip/use source")),
                costs.provider(), costs.origin(), List.of());
        sink = new CapabilitySink(); sink.configurations(List.of(stronger, new Functional(stronger.source(), "expensive", List.of(expensive), stronger.acquisition(), true)));
        check(sink.evidence().size() == 2, "Operating-cost tradeoff incorrectly dominated");
        boolean rejected = false; try { sink.configurations(Collections.nCopies(4097, base)); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Unbounded modular enumeration accepted");
        // Distinct equipment hosts cannot dominate one another. The maximum admitted batch
        // must preserve their alternatives without pairwise cross-host enumeration.
        var distinct = new ArrayList<Functional>();
        for (int i = 0; i < 4096; i++) distinct.add(fact("test:host" + i, ProgressionBand.MID, CapabilityAxis.MELEE_DAMAGE, 40));
        sink = new CapabilitySink(); long started = System.nanoTime(); sink.configurations(distinct);
        long elapsed = System.nanoTime() - started;
        check(sink.evidenceCount() == 4096 && sink.counts().get("prunedConfigurations") == 0, "Independent equipment alternatives lost at batch bound");
        check(sink.counts().get("configurationDominanceComparisons") == 0, "Unrelated hosts caused quadratic configuration comparisons");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Configuration scalability: 4096 independent hosts; "
                + elapsed + " ns; 0 cross-host dominance comparisons (synthetic, not native census timing)");
    }
    private static ResourceEvidence resource(String id, ProgressionBand band, List<AcquisitionSource> sources) {
        return new ResourceEvidence(id, band, Availability.FINITE, Automation.NONE, true, true, 1, .9, sources, List.of());
    }
    private static ResourceEvidence independent(String id, ProgressionBand band) {
        return resource(id, band, List.of(new AcquisitionSource("source:" + id, AcquisitionSource.Kind.WORLD_GENERATION, band, 1,
                false, false, 0, .9, List.of(), "Independent supported source")));
    }
    private static void configurationAccess() {
        check(InstalledCapabilityProviders.productiveCrop(1200, 1, 0, 1, 1, 0), "Ordinary planter excluded");
        check(!InstalledCapabilityProviders.productiveCrop(1200, 0, 0, 1, 1, 0), "Disabled growth claimed farming");
        check(!InstalledCapabilityProviders.productiveCrop(1200, 1, 0, 1, 1, -1), "Zero configured harvest claimed farming");
        check(!InstalledCapabilityProviders.productiveCrop(1200, Double.NaN, 0, 1, 1, 0), "Invalid loaded growth accepted");
        check(!InstalledCapabilityProviders.productiveDrop(true, 0) && !InstalledCapabilityProviders.productiveDrop(false, 1)
                && InstalledCapabilityProviders.productiveDrop(true, .01), "Zero/empty/random crop harvest witness mishandled");
        var resources = Map.of("test:machine", independent("test:machine", ProgressionBand.ENTRY),
                "test:seed", independent("test:seed", ProgressionBand.EARLY), "test:soil", independent("test:soil", ProgressionBand.LATE));
        var resolver = new ConfigurationAccess.Resolver(resources);
        var proof = resolver.require(List.of(List.of("test:machine"), List.of("test:missing", "test:seed"), List.of("test:soil")));
        check(proof.placement().reachable() && proof.placement().stage() == ProgressionBand.LATE && proof.selected().size() == 3,
                "Configuration proof ignored latest required component or alternate access");
        check(!resolver.require(List.of(List.of("test:machine"), List.of("test:missing"))).placement().reachable(), "Missing setup falsely reachable");
        check(!resolver.require(List.of()).placement().reachable(), "Empty requirements proved setup");
        var finiteDescendant = resource("test:crafted_choice", ProgressionBand.ENTRY, List.of(new AcquisitionSource("recipe:choice", AcquisitionSource.Kind.RECIPE,
                ProgressionBand.ENTRY, 1, false, false, 0, .9, List.of("test:exclusive_reward"), "Crafted descendant of finite choice")));
        check(!new ConfigurationAccess.Resolver(Map.of("test:crafted_choice", finiteDescendant), Set.of("test:crafted_choice"))
                .require(List.of(List.of("test:crafted_choice"))).placement().reachable(), "Crafted quest descendant fabricated joint setup");
        var reward = resource("test:choice", ProgressionBand.ENTRY, List.of(new AcquisitionSource("quest:choice", AcquisitionSource.Kind.QUEST_REWARD,
                ProgressionBand.ENTRY, 1, false, false, 0, .9, List.of(), "Choice without joint inventory proof")));
        check(!ConfigurationAccess.require(Map.of("test:choice", reward), List.of(List.of("test:choice"))).placement().reachable(),
                "Reward-only components fabricated a joint configuration");
        var planter = InstalledCapabilityProviders.planter("test:machine", "test:crop", proof);
        check(frontier(List.of(planter), ProgressionBand.EARLY, CapabilityAxis.AUTOMATED_FARMING) == 0,
                "Late soil configuration leaked into early farming");
        check(frontier(List.of(planter), ProgressionBand.LATE, CapabilityAxis.AUTOMATED_FARMING) == 1
                && planter.measurements().getFirst().operation().unitsPerSecond() == null, "Planter presence invented rate or was excluded");
    }
    private static void configuredStacks() {
        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        JsonObject definition = JsonParser.parseString("{\"result\":{\"id\":\"minecraft:diamond_pickaxe\",\"components\":{\"minecraft:unbreakable\":{},\"minecraft:max_damage\":100000}}}").getAsJsonObject();
        Map<String,String> meta = new TreeMap<>(Map.of("effective_definition", definition.toString(), "acquisition_complete", "true"));
        var process = new ProductionGraph.Process("test:configured", "minecraft:crafting", List.of(new ProductionGraph.Input(List.of("test:component"),1,true)),
                List.of(new ProductionGraph.Output("minecraft:diamond_pickaxe",1,1,false)),0,0,"test:typed",.9,meta);
        var graph = new ProductionGraph(List.of(process),List.of());
        var resources = Map.of("test:component", independent("test:component", ProgressionBand.LATE),
                "minecraft:crafting_table", independent("minecraft:crafting_table", ProgressionBand.ENTRY),
                "minecraft:diamond_pickaxe", independent("minecraft:diamond_pickaxe", ProgressionBand.ENTRY));
        CapabilitySink sink = new CapabilitySink(); ConfiguredStackCapabilities.collect(graph, registries, resources, sink);
        check(frontier(sink.evidence(),ProgressionBand.ENTRY,CapabilityAxis.INDESTRUCTIBILITY) == 0, "Default item access supplied late configuration early");
        check(frontier(sink.evidence(),ProgressionBand.LATE,CapabilityAxis.INDESTRUCTIBILITY) == 1
                && frontier(sink.evidence(),ProgressionBand.LATE,CapabilityAxis.DURABILITY) == 100000, "Static native configured components were missed");
        sink = new CapabilitySink(); ConfiguredStackCapabilities.collect(graph,registries,Map.of(),sink);
        check(sink.evidence().isEmpty() && !sink.candidates().isEmpty(),"Unavailable configuration recipe defined power");
        meta.put("variant_transform","base_components_copied");
        var transferred = new ProductionGraph.Process(process.id(),process.family(),process.inputs(),process.outputs(),0,0,process.provider(),.9,meta);
        sink = new CapabilitySink(); ConfiguredStackCapabilities.collect(new ProductionGraph(List.of(transferred),List.of()),registries,resources,sink);
        check(sink.evidence().isEmpty(),"Copied base invented independent static configuration");
        var brokenDefinition = definition.deepCopy();
        brokenDefinition.getAsJsonObject("result").getAsJsonObject("components").addProperty("minecraft:max_damage", -10);
        var broken = new ProductionGraph.Process("test:broken_configured", process.family(), process.inputs(), process.outputs(), 0, 0, process.provider(), .9,
                Map.of("effective_definition", brokenDefinition.toString(), "acquisition_complete", "true"));
        sink = new CapabilitySink(); ConfiguredStackCapabilities.collect(new ProductionGraph(List.of(broken, process), List.of()), registries, resources, sink);
        check(frontier(sink.evidence(), ProgressionBand.LATE, CapabilityAxis.DURABILITY) == 100000
                && sink.evidence().stream().noneMatch(f -> f.configuration().contains("test:broken_configured")),
                "A broken configured output cannot discard healthy configurations or contribute partial power");
        check(sink.candidates().stream().anyMatch(c -> c.subject().equals("test:broken_configured") && c.detail().contains("UNKNOWN")),
                "Broken configured output has explicit identity and unknown diagnostics");
    }
    private static void production() {
        for (String function : List.of("automated_resource_extraction", "automated_fishing", "automated_farming", "crop_growth_acceleration", "machine_tick_acceleration")) {
            var process = new ProductionGraph.Process("test:process", "source", List.of(), List.of(new ProductionGraph.Output("test:fish", 2, 1, false)),
                    20, 0, "audited_production", .9, Map.of("function", function, "capability_source_item", "test:machine", "duration_known", "true"));
            CapabilitySink sink = new CapabilitySink();
            CompetitiveCapabilities.production(new ProductionGraph(List.of(process), List.of()), Map.of("test:machine", resource("test:machine", ProgressionBand.MID, List.of())), sink);
            var m = sink.evidence().getFirst().measurements().getFirst();
            check(m.magnitude() == 1 && m.unit().equals("presence") && m.operation().unitsPerSecond() == null, "Nominal recipe time fabricated source throughput");
            check(m.family() != CapabilityFamily.COMBAT_OFFENSE, "Automation routed to combat");
            sink = new CapabilitySink(); CompetitiveCapabilities.production(new ProductionGraph(List.of(process), List.of()), Map.of(), sink);
            check(RobustFrontiers.competitive(sink.evidence(), "WINSORIZE").isEmpty(), "Output existence fabricated machine access");
        }
    }
    private static void nativeEnchantments() {
        var definition = Enchantment.definition(HolderSet.direct(Items.DIAMOND_PICKAXE.builtInRegistryHolder()), 1, 5,
                Enchantment.constantCost(1), Enchantment.constantCost(1), 1, EquipmentSlotGroup.MAINHAND);
        var attributes = new EnchantmentAttributeEffect(net.minecraft.resources.ResourceLocation.parse("test:native_mining"), Attributes.MINING_EFFICIENCY,
                LevelBasedValue.perLevel(2), AttributeModifier.Operation.ADD_VALUE);
        var effects = DataComponentMap.builder().set(EnchantmentEffectComponents.ATTRIBUTES, List.of(attributes))
                .set(EnchantmentEffectComponents.ITEM_DAMAGE, List.of(new ConditionalEffect<EnchantmentValueEffect>(new RemoveBinomial(LevelBasedValue.constant(.75f)), Optional.empty()))).build();
        var enchantment = new Enchantment(net.minecraft.network.chat.Component.literal("Unrelated display name"), definition, HolderSet.direct(), effects);
        CapabilitySink sink = new CapabilitySink();
        var placement = new CompetitiveCapabilities.Placement(ProgressionBand.EARLY, true, .9, List.of());
        NativeCapabilityReader.readEnchantment("test:configured_pick", "test:unknown_name", enchantment, 5, placement, true, sink);
        check(sink.evidence().getFirst().measurements().stream().anyMatch(m -> m.axis() == CapabilityAxis.MINING_SPEED && m.magnitude() == 10),
                "Behavior-based mining enchantment not detected");
        check(frontier(sink.evidence(), ProgressionBand.EARLY, CapabilityAxis.DURABILITY_REDUCTION) == .75, "Durability binomial semantics lost");
        sink = new CapabilitySink(); NativeCapabilityReader.readEnchantment("test:enchantment", "test:unknown_name", enchantment, 5, placement, false, sink);
        check(RobustFrontiers.competitive(sink.evidence(), "WINSORIZE").isEmpty(), "Definition max implies obtainable maximum");
        var wear = DataComponentMap.builder().set(EnchantmentEffectComponents.ITEM_DAMAGE,
                List.of(new ConditionalEffect<EnchantmentValueEffect>(new AddValue(LevelBasedValue.constant(5)), Optional.empty()),
                        new ConditionalEffect<EnchantmentValueEffect>(new SetValue(LevelBasedValue.constant(0)), Optional.empty()))).build();
        sink = new CapabilitySink(); NativeCapabilityReader.readEnchantment("test:wear", "test:wear", new Enchantment(enchantment.description(), definition, HolderSet.direct(), wear),
                1, placement, true, sink);
        check(frontier(sink.evidence(), ProgressionBand.EARLY, CapabilityAxis.DURABILITY_REDUCTION) == 0, "Increased wear counted as preservation");
        check(frontier(sink.evidence(), ProgressionBand.EARLY, CapabilityAxis.INDESTRUCTIBILITY) == 1, "Native zero-wear effect missed");
        EnchantmentValueEffect opaque = new EnchantmentValueEffect() {
            public float process(int level, net.minecraft.util.RandomSource random, float value) { throw new AssertionError("Executed opaque gameplay effect"); }
            public com.mojang.serialization.MapCodec<? extends EnchantmentValueEffect> codec() { throw new UnsupportedOperationException(); }
        };
        var custom = DataComponentMap.builder().set(EnchantmentEffectComponents.ITEM_DAMAGE,
                List.of(new ConditionalEffect<EnchantmentValueEffect>(opaque, Optional.empty()))).build();
        sink = new CapabilitySink(); NativeCapabilityReader.readEnchantment("test:custom", "test:custom", new Enchantment(enchantment.description(), definition, HolderSet.direct(), custom),
                1, placement, true, sink);
        check(sink.evidence().isEmpty() && sink.candidates().size() == 1, "Opaque effect executed or fabricated power");
        var stack = new net.minecraft.world.item.ItemStack(Items.STICK);
        stack.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                .add(Attributes.MAX_HEALTH, new AttributeModifier(net.minecraft.resources.ResourceLocation.parse("test:health"), 30, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .add(Attributes.MAX_HEALTH, new AttributeModifier(net.minecraft.resources.ResourceLocation.parse("test:more_health"), 10, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .add(Attributes.MAX_HEALTH, new AttributeModifier(net.minecraft.resources.ResourceLocation.parse("test:body"), 999, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.BODY).build());
        sink = new CapabilitySink(); NativeCapabilityReader.stackAttributes(stack, "test:attributes", placement, sink);
        check(sink.evidence().getFirst().measurements().size() == 1 && sink.evidence().getFirst().measurements().getFirst().magnitude() == 40,
                "Native slot/filter/composed operation contributions incorrect");
        stack.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, -10);
        int healthyRecords = sink.evidenceCount();
        NativeCapabilityReader.stack(stack, "test:broken_stack", "fixture:invalid_damage", placement, sink);
        check(sink.evidenceCount() == healthyRecords && sink.evidence().stream().noneMatch(f -> f.source().subjectId().equals("test:broken_stack")),
                "Failure after reading attributes cannot leak a partial configured stack");
        check(sink.candidates().stream().anyMatch(c -> c.subject().equals("test:broken_stack") && c.detail().contains("UNKNOWN")),
                "Broken native component records unknown instead of aborting all items");
        var brokenEffects = new DataComponentMap() {
            @SuppressWarnings("unchecked") public <T> T get(net.minecraft.core.component.DataComponentType<? extends T> type) {
                if (type == EnchantmentEffectComponents.ATTRIBUTES) return (T) List.of(attributes,
                        new EnchantmentAttributeEffect(net.minecraft.resources.ResourceLocation.parse("test:unsupported_luck"), Attributes.LUCK,
                                LevelBasedValue.constant(1), AttributeModifier.Operation.ADD_VALUE));
                if (type == EnchantmentEffectComponents.DAMAGE) throw new NoClassDefFoundError("fixture incompatible effect component");
                return null;
            }
            public Set<net.minecraft.core.component.DataComponentType<?>> keySet() {
                return Set.of(EnchantmentEffectComponents.ATTRIBUTES, EnchantmentEffectComponents.DAMAGE);
            }
        };
        sink = new CapabilitySink();
        NativeCapabilityReader.readEnchantment("test:before", "test:healthy_before", enchantment, 5, placement, true, sink);
        NativeCapabilityReader.readEnchantment("test:broken", "test:broken_enchantment",
                new Enchantment(enchantment.description(), definition, HolderSet.direct(), brokenEffects), 5, placement, true, sink);
        NativeCapabilityReader.readEnchantment("test:after", "test:healthy_after", enchantment, 5, placement, true, sink);
        check(sink.evidenceCount() == 2 && sink.evidence().stream().noneMatch(f -> f.source().subjectId().equals("test:broken")),
                "One incompatible enchantment cannot discard healthy neighboring enchantments or leak numeric effects");
        check(sink.candidates().size() == 1 && sink.candidates().getFirst().subject().equals("test:broken_enchantment")
                && sink.candidates().getFirst().detail().contains("fixture incompatible effect component"),
                "Failed enchantment replaces partial candidates with explicit unknown provenance");
    }
    private static void preservation() {
        var placement = new CompetitiveCapabilities.Placement(ProgressionBand.EARLY, true, .9, List.of());
        var noWear = new net.minecraft.world.item.ItemStack(Items.STICK);
        noWear.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true));
        CapabilitySink sink = new CapabilitySink();
        NativeCapabilityReader.stack(noWear, "test:energy_armor", "effective_default", placement, sink);
        check(frontier(sink.evidence(), ProgressionBand.EARLY, CapabilityAxis.INDESTRUCTIBILITY) == 0,
                "UNBREAKABLE without a native wear pool invented preservation");
        noWear.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, 0);
        NativeCapabilityReader.stack(noWear, "test:zero_wear", "effective_default", placement, sink);
        check(frontier(sink.evidence(), ProgressionBand.EARLY, CapabilityAxis.INDESTRUCTIBILITY) == 0,
                "Zero native wear pool invented preservation");
        var armor = new net.minecraft.world.item.ItemStack(Items.DIAMOND_CHESTPLATE);
        armor.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true));
        NativeCapabilityReader.stack(armor, "test:unbreakable_chest", "effective_default", placement, sink);
        var preserved = sink.evidence().stream().filter(f -> f.source().subjectId().equals("test:unbreakable_chest")
                && f.measurements().stream().anyMatch(m -> m.axis() == CapabilityAxis.INDESTRUCTIBILITY)).findFirst().orElseThrow();
        var measurement = preserved.measurements().stream().filter(m -> m.axis() == CapabilityAxis.INDESTRUCTIBILITY).findFirst().orElseThrow();
        check(measurement.scope().targets().equals("source_equipment:test:unbreakable_chest") && measurement.applicability().contains("slot=chest"),
                "Native preservation lost its actual equipment applicability and source boundary");
        check(frontier(sink.evidence(), ProgressionBand.EARLY, CapabilityAxis.INDESTRUCTIBILITY) == 1
                && measurement.unsupported().stream().anyMatch(s -> s.contains("does not repair")),
                "A real wear pool was missed or claimed universal repair");
        sink = new CapabilitySink();
        NativeCapabilityReader.stack(armor, "test:late_chest", "effective_default",
                new CompetitiveCapabilities.Placement(ProgressionBand.LATE, true, .9, List.of()), sink);
        check(frontier(sink.evidence(), ProgressionBand.EARLY, CapabilityAxis.INDESTRUCTIBILITY) == 0,
                "Real late preservation leaked into the early frontier");
    }
    private static void timeBottle() {
        check(new TimeBottleCapabilityProvider().capabilityAxes().equals(Set.of(CapabilityAxis.BLOCK_ENTITY_ACCELERATION)),
                "Time bottle declaration claims productive rate or unrelated operating domains");
        var budget = TimeBottleCapabilityProvider.budget(622080000, 20, 30, 8);
        check(budget.extraCalls() == 128 && budget.durationTicks() == 600 && budget.ladderTicks() == 76800,
                "Config comment invented 256 calls or discarded the native stored-time cost ladder");
        var capped = TimeBottleCapabilityProvider.budget(1200, 20, 30, 8);
        check(capped.extraCalls() == 2 && capped.ladderTicks() == 1200, "Stored-time capacity ignored by reachable peak");
        var resources = Map.of("tiab:time_in_a_bottle", independent("tiab:time_in_a_bottle", ProgressionBand.EARLY),
                "minecraft:furnace", independent("minecraft:furnace", ProgressionBand.ENTRY));
        var proof = ConfigurationAccess.require(resources, List.of(List.of("tiab:time_in_a_bottle"), List.of("minecraft:furnace")));
        var fact = TimeBottleCapabilityProvider.project(budget, proof);
        var m = fact.measurements().getFirst();
        check(frontier(List.of(fact), ProgressionBand.ENTRY, CapabilityAxis.BLOCK_ENTITY_ACCELERATION) == 0
                && frontier(List.of(fact), ProgressionBand.EARLY, CapabilityAxis.BLOCK_ENTITY_ACCELERATION) == 128,
                "Bounded bottle host configuration lost access stage");
        check(m.unit().equals("extra_tick_calls_per_server_tick") && m.operation().unitsPerSecond() == null
                && m.operation().uptimeFraction() == null && m.operation().durationSeconds() == 30,
                "Ticker burst became continuous production throughput");
        check(m.applicability().contains("conditional=") && m.scope().targets().equals("configured_furnace_block_entity")
                && m.operation().recurringCosts().getFirst().contains("76800"), "Bottle omitted target condition or recurring charge budget");
        var unavailable = ConfigurationAccess.require(Map.of("tiab:time_in_a_bottle", resources.get("tiab:time_in_a_bottle")),
                List.of(List.of("tiab:time_in_a_bottle"), List.of("minecraft:furnace")));
        check(RobustFrontiers.competitive(List.of(TimeBottleCapabilityProvider.project(budget, unavailable)), "WINSORIZE").isEmpty(),
                "Bottle item access fabricated an independently accessible target host");
        for (int[] invalid : List.of(new int[]{599,20,30,8}, new int[]{1200,0,30,8}, new int[]{1200,20,0,8},
                new int[]{1200,20,30,0}, new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE,30,8}, new int[]{1200,20,30,32})) {
            boolean rejected = false;
            try { TimeBottleCapabilityProvider.budget(invalid[0], invalid[1], invalid[2], invalid[3]); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Invalid or overflowing accelerator config claimed free/positive power");
        }
    }
    private static void adapters() {
        var runs = new GenerationProviders(null, ignored -> null);
        for (var provider : InstalledCapabilityProviders.all()) {
            check(!runs.prepare("capability", provider, false), "Absent optional API loaded");
            check(!provider.capabilityAxes().isEmpty(), "Optional provider lost its intended coverage domain: " + provider.id());
            boolean rejected = false;
            try { provider.capabilityAxes().clear(); } catch (UnsupportedOperationException expected) { rejected = true; }
            check(rejected, "Optional provider declaration is mutable: " + provider.id());
        }
        var declared = new HashMap<String, Set<CapabilityAxis>>();
        InstalledCapabilityProviders.all().forEach(provider -> declared.put(provider.id(), provider.capabilityAxes()));
        check(declared.get("botanypots_capabilities").equals(Set.of(CapabilityAxis.AUTOMATED_FARMING))
                && declared.get("torchmaster_capabilities").equals(Set.of(CapabilityAxis.SPAWN_SUPPRESSION))
                && declared.get("forbidden_arcanus_capabilities").equals(Set.of(CapabilityAxis.INDESTRUCTIBILITY)),
                "Optional provider metadata expanded a bounded mechanic into unrelated domains");
        check(declared.get("draconicevolution_capabilities").containsAll(Set.of(CapabilityAxis.SHIELD_CAPACITY, CapabilityAxis.FLIGHT))
                && declared.get("ars_nouveau_capabilities").containsAll(Set.of(CapabilityAxis.MAGIC_DAMAGE, CapabilityAxis.FLIGHT, CapabilityAxis.AUTOMATION_INTERACTION))
                && declared.get("apotheosis_capabilities").containsAll(Set.of(CapabilityAxis.ARMOR, CapabilityAxis.MELEE_DAMAGE)),
                "Explicit unresolved configuration systems lost their intended coverage domains");
        check(!InstalledCapabilityProviders.version("future", "21.1.12", true, "API").collectable(), "Unaudited version accepted");
        check(!InstalledCapabilityProviders.version("21.1.12", "21.1.12", false, "API").collectable(), "Unaudited loader accepted");
        var torch = InstalledCapabilityProviders.torch(64, true, true, List.of("minecraft:zombie"),
                new CompetitiveCapabilities.Placement(ProgressionBand.EARLY, true, .9, List.of()));
        check(torch.measurements().getFirst().axis() == CapabilityAxis.SPAWN_SUPPRESSION && torch.measurements().getFirst().scope().radiusBlocks() == 64,
                "Typed torch facts lack generic radius/suppression");
        var dread = InstalledCapabilityProviders.blockingLight("torchmaster:dreadlamp", 64, true, false, List.of("minecraft:bat"),
                new CompetitiveCapabilities.Placement(ProgressionBand.EARLY, true, .9, List.of()));
        check(dread.source().subjectId().equals("torchmaster:dreadlamp")
                && !dread.measurements().getFirst().comparisonKey().equals(torch.measurements().getFirst().comparisonKey())
                && dread.measurements().getFirst().applicability().contains("village_sieges=false"),
                "Dread Lamp passive filter was promoted into Mega Torch hostile protection or siege blocking");
        var placement = new CompetitiveCapabilities.Placement(ProgressionBand.EARLY, true, .9, List.of());
        var hostile = InstalledCapabilityProviders.blockingLight("test:hostile_light", 64, true, false, List.of("minecraft:zombie"),
                Map.of("minecraft:zombie", net.minecraft.world.entity.MobCategory.MONSTER), placement);
        var passive = InstalledCapabilityProviders.blockingLight("test:passive_light", 64, true, false, List.of("minecraft:bat"),
                Map.of("minecraft:bat", net.minecraft.world.entity.MobCategory.AMBIENT), placement);
        var unresolved = InstalledCapabilityProviders.blockingLight("test:unknown_light", 64, true, false, List.of("test:unknown_entity"), Map.of(), placement);
        check(hostile.measurements().getFirst().applicability().contains("affected_monsters=true"), "Loaded hostile filter lost native category applicability");
        check(passive.measurements().getFirst().applicability().contains("affected_monsters=false"), "Passive spawn suppression became hostile protection");
        check(unresolved.measurements().getFirst().applicability().contains("affected_monsters=unknown")
                && unresolved.measurements().getFirst().unsupported().stream().anyMatch(s -> s.contains("categories unresolved")),
                "Unknown entity filter fabricated hostile applicability");
        CapabilitySink sink = new CapabilitySink();
        InstalledCapabilityProviders.material("test:material", JsonParser.parseString("{\"properties\":{\"silentgear:main\":{\"attack_damage\":90,\"durability\":5000,\"harvest_speed\":80}}}").getAsJsonObject(), sink);
        check(sink.evidence().getFirst().measurements().size() == 3, "Typed material facts lose multi-axis strength");
        check(RobustFrontiers.competitive(sink.evidence(), "WINSORIZE").isEmpty(), "Component maxima fabricated final gear");
        check(sink.candidates().getFirst().reason() == CapabilitySink.Reason.ACCESS_UNPROVEN
                && sink.candidates().getFirst().axes().equals(declared.get("silentgear_capabilities")),
                "Known material components lost the missing full-gear access diagnostic");
        CapabilitySink inherited = new CapabilitySink();
        InstalledCapabilityProviders.material("test:inherited", new JsonObject(), inherited);
        check(inherited.evidence().isEmpty() && inherited.candidates().getFirst().reason() == CapabilitySink.Reason.UNKNOWN_BEHAVIOR
                && inherited.candidates().getFirst().axes().equals(declared.get("silentgear_capabilities")),
                "Opaque material composition was mistaken for supported behavior or a missing craft route");
        String adapters = FilesRead.read("common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/InstalledCapabilityProviders.java");
        check(!adapters.contains("BonusTrackGenerator") && !adapters.contains("RuntimeBalanceDefinition") && !adapters.contains("StatScalingService"), "Adapter contains Essence calibration rules");
    }
    private static void reports() throws Exception {
        CapabilitySink sink = new CapabilitySink(); sink.add(fact("test:gear", ProgressionBand.EARLY, CapabilityAxis.MELEE_DAMAGE, 10));
        sink.candidate("test:opaque", "typed", "Unknown rate");
        sink.candidate("test:flight", "typed", "Unproven acquisition", Set.of(CapabilityAxis.FLIGHT), CapabilitySink.Reason.ACCESS_UNPROVEN);
        JsonObject report = CompetitiveCapabilities.report(sink, "WINSORIZE");
        Path output = argsOutput(); CompetitiveCapabilityReports.write(output, report);
        check(Files.readString(output.resolve("reports/competitive_capabilities.md")).contains("EARLY"), "Band report absent");
        check(Files.readString(output.resolve("reports/competitive_candidates.csv")).contains("Unknown rate"), "Unsupported report absent");
        check(report.getAsJsonArray("frontiers").size() == 4, "Cumulative bands missing");
        check(report.getAsJsonObject("candidateCoverage").getAsJsonObject("FLIGHT").get("ACCESS_UNPROVEN").getAsLong() == 1
                && report.getAsJsonObject("unscopedCandidateReasons").get("UNCLASSIFIED").getAsLong() == 1,
                "Published report lost typed reason census or legacy unscoped census");
        var roundtrip = BalanceDocument.GSON.fromJson(report.getAsJsonArray("evidence").get(0), Functional.class);
        check(roundtrip.equals(sink.evidence().getFirst()), "Rich capability roundtrip lost facts");
    }
    private static Path argsOutput() { return Path.of("build/chat07b-synthetic"); }
    private static class FilesRead {
        static String read(String path) { try { Path p = Path.of(path); if (!Files.exists(p)) p = Path.of("..").resolve(path); return Files.readString(p); }
            catch (Exception e) { throw new AssertionError(e); } }
    }
}
