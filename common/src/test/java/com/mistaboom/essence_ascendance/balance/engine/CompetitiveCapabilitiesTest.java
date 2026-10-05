package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.capability.InstalledCapabilityProviders;
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
        progression(); alternativesAndOutliers(); semantics(); configurations(); production(); nativeEnchantments(); adapters(); reports();
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
    }
    private static ResourceEvidence resource(String id, ProgressionBand band, List<AcquisitionSource> sources) {
        return new ResourceEvidence(id, band, Availability.FINITE, Automation.NONE, true, true, 1, .9, sources, List.of());
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
    }
    private static void adapters() {
        var runs = new GenerationProviders(null, ignored -> null);
        for (var provider : InstalledCapabilityProviders.all()) check(!runs.prepare("capability", provider, false), "Absent optional API loaded");
        check(!InstalledCapabilityProviders.version("future", "21.1.12", true, "API").collectable(), "Unaudited version accepted");
        check(!InstalledCapabilityProviders.version("21.1.12", "21.1.12", false, "API").collectable(), "Unaudited loader accepted");
        var torch = InstalledCapabilityProviders.torch(64, true, true, List.of("minecraft:zombie"),
                new CompetitiveCapabilities.Placement(ProgressionBand.EARLY, true, .9, List.of()));
        check(torch.measurements().getFirst().axis() == CapabilityAxis.SPAWN_SUPPRESSION && torch.measurements().getFirst().scope().radiusBlocks() == 64,
                "Typed torch facts lack generic radius/suppression");
        CapabilitySink sink = new CapabilitySink();
        InstalledCapabilityProviders.material("test:material", JsonParser.parseString("{\"properties\":{\"silentgear:main\":{\"attack_damage\":90,\"durability\":5000,\"harvest_speed\":80}}}").getAsJsonObject(), sink);
        check(sink.evidence().getFirst().measurements().size() == 3, "Typed material facts lose multi-axis strength");
        check(RobustFrontiers.competitive(sink.evidence(), "WINSORIZE").isEmpty(), "Component maxima fabricated final gear");
        String adapters = FilesRead.read("common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/InstalledCapabilityProviders.java");
        check(!adapters.contains("BonusTrackGenerator") && !adapters.contains("RuntimeBalanceDefinition") && !adapters.contains("StatScalingService"), "Adapter contains Essence calibration rules");
    }
    private static void reports() throws Exception {
        CapabilitySink sink = new CapabilitySink(); sink.add(fact("test:gear", ProgressionBand.EARLY, CapabilityAxis.MELEE_DAMAGE, 10));
        sink.candidate("test:opaque", "typed", "Unknown rate");
        JsonObject report = CompetitiveCapabilities.report(sink, "WINSORIZE");
        Path output = argsOutput(); CompetitiveCapabilityReports.write(output, report);
        check(Files.readString(output.resolve("reports/competitive_capabilities.md")).contains("EARLY"), "Band report absent");
        check(Files.readString(output.resolve("reports/competitive_candidates.csv")).contains("Unknown rate"), "Unsupported report absent");
        check(report.getAsJsonArray("frontiers").size() == 4, "Cumulative bands missing");
        var roundtrip = BalanceDocument.GSON.fromJson(report.getAsJsonArray("evidence").get(0), Functional.class);
        check(roundtrip.equals(sink.evidence().getFirst()), "Rich capability roundtrip lost facts");
    }
    private static Path argsOutput() { return Path.of("build/chat07b-synthetic"); }
    private static class FilesRead {
        static String read(String path) { try { Path p = Path.of(path); if (!Files.exists(p)) p = Path.of("..").resolve(path); return Files.readString(p); }
            catch (Exception e) { throw new AssertionError(e); } }
    }
}
