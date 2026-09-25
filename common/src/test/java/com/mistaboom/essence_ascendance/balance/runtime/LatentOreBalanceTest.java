package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.economy.DissolutionYield;
import com.mistaboom.essence_ascendance.balance.economy.EconomicValue;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProcessingPolicy;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import com.mistaboom.essence_ascendance.balance.engine.Automation;
import com.mistaboom.essence_ascendance.balance.engine.Availability;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.engine.ResourceEvidence;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings.DimensionOverride;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings.DimensionSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/** Direct acquisition fixtures exercise category scarcity independently of the large balance generator. */
public final class LatentOreBalanceTest {
    private static final List<String> ESSENCES = List.of("offense", "defense", "vitality", "mobility", "gathering", "utility")
            .stream().map(name -> "essence_ascendance:" + name).toList();
    private static final String OFFENSE = ESSENCES.getFirst();
    private static int assertions;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EssenceTypes.init();
        scarcityAndMonotonicity();
        sourceFamilyDeduplication();
        materialFamilyDeduplication();
        jointSourceMaterialMatching();
        exhaustiveSmallMatchingFixtures();
        evidenceExclusions();
        dependenciesAndStrength();
        generationAndOverrides();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("LatentOreBalanceTest: " + assertions + " checks PASS");
    }

    private static void scarcityAndMonotonicity() {
        var empty = new Fixture().analyze();
        near(empty.multiplier(), 1.5, "Absent direct supply must receive the scarcity ceiling");
        near(empty.referenceYield(), 1.0, "The usable-yield reference must stay fixed at one Essence unit");
        check(empty.categories().size() == ESSENCES.size(), "Missing categories must remain visible in analysis");
        check(!empty.assumptions().isEmpty(), "Analysis must explain its opportunity proxy");
        String assumptions = String.join(" ", empty.assumptions()).toLowerCase(java.util.Locale.ROOT);
        check(assumptions.contains("throughput") || assumptions.contains("per second") || assumptions.contains("not measured rates"),
                "Analysis must distinguish source opportunity from measured throughput");

        double previous = empty.multiplier();
        for (int perCategory = 1; perCategory <= 6; perCategory++) {
            var analysis = rich(perCategory).analyze();
            check(analysis.multiplier() <= previous, "Adding independent early sources must not make scarcity worse");
            check(analysis.multiplier() >= 0.75 && analysis.multiplier() <= 1.5, "Scarcity multiplier stays bounded");
            for (var category : analysis.categories()) {
                check(category.sourceFamilies() == Math.min(4, perCategory), "Count distinct matched opportunities up to full coverage");
                near(category.coverage(), Math.min(1, category.effectiveSources() / 4), "Four effective sources define category coverage");
            }
            previous = analysis.multiplier();
        }
        near(rich(4).analyze().multiplier(), 0.75, "Every category with four strong sources reaches the abundance floor");

        Fixture missing = new Fixture();
        for (int category = 0; category < ESSENCES.size() - 1; category++)
            missing.addSources(ESSENCES.get(category), 4);
        near(missing.analyze().multiplier(), 1.5, "Abundant categories cannot hide one missing category");
        for (int index = 0; index < 200; index++)
            missing.add("test:abundant_" + index, source("test:abundant_source_" + index), Map.of(OFFENSE, 1_000_000.0));
        near(missing.analyze().multiplier(), 1.5, "Huge abundance in one category must not dilute a missing category");

        Fixture growing = rich(2);
        double before = growing.analyze().multiplier();
        growing.add("test:high_yield", source("test:high_yield_source"), Map.of(OFFENSE, 1_000_000.0));
        var after = growing.analyze();
        check(after.multiplier() <= before, "A high-yield addition must not rebase existing sources downward");
        near(after.referenceYield(), 1, "Yield reference must not shift with population abundance");
        check(category(after, OFFENSE).effectiveSources() <= 3, "One large source contributes at most one effective source");
    }

    private static void sourceFamilyDeduplication() {
        Fixture fixture = rich(1);
        var before = fixture.analyze();
        AcquisitionSource shared = source(family(OFFENSE, 0));
        for (int index = 0; index < 100; index++)
            fixture.add("test:variant_" + index, shared, Map.of(OFFENSE, 1.0));
        var after = fixture.analyze();
        near(after.multiplier(), before.multiplier(), "Item variants of a source cannot change scarcity");
        near(category(after, OFFENSE).effectiveSources(), category(before, OFFENSE).effectiveSources(),
                "Duplicate item outputs cannot inflate effective supply");
        check(category(after, OFFENSE).sourceFamilies() == 1, "Source identity is kind plus id, not item id");
        fixture.add("test:different_kind", source(family(OFFENSE, 0), AcquisitionSource.Kind.FARMING,
                ProgressionBand.ENTRY, false, 0, 1, List.of()), Map.of(OFFENSE, 1.0));
        check(category(fixture.analyze(), OFFENSE).sourceFamilies() == 2, "Equal source ids with different kinds are distinct families");

        Fixture split = new Fixture();
        split.add("test:split", source("test:shared"), Map.of(OFFENSE, 0.5, ESSENCES.get(1), 0.5));
        split.add("test:alternate", source("test:shared"), Map.of(OFFENSE, 1.0));
        var splitAnalysis = split.analyze();
        check(category(splitAnalysis, OFFENSE).sourceFamilies() == 1
                        && category(splitAnalysis, ESSENCES.get(1)).sourceFamilies() == 1,
                "A source may supply multiple categories but counts once within each");
        check(category(splitAnalysis, OFFENSE).effectiveSources() <= 1,
                "Alternate output variants select their best usable output without summing a family twice");
        check(fixture.analyze().equals(fixture.analyze()), "Complete analysis remains deterministic");
    }

    private static void materialFamilyDeduplication() {
        Fixture geology = new Fixture();
        geology.add("test:raw_iron", source("test:stone_iron"), Map.of(OFFENSE, 1.0));
        ResourceEvidence iron = geology.resources.get("test:raw_iron");
        geology.resources.put(iron.itemId(), new ResourceEvidence(iron.itemId(), iron.stage(), iron.availability(),
                iron.automation(), iron.reachable(), iron.external(), iron.economicValue(), iron.confidence(),
                List.of(source("test:stone_iron"), source("test:deepslate_iron")), iron.warnings()));
        var sameMaterial = category(geology.analyze(), OFFENSE);
        check(sameMaterial.sourceFamilies() == 1, "Two geological targets for one material must not create material diversity");
        near(sameMaterial.effectiveSources(), 1, "Repeated acquisition routes for the same item count once");

        Fixture compressed = new Fixture();
        compressed.add("test:raw_iron", source("test:raw_iron_source"), Map.of(OFFENSE, 1.0));
        compressed.add("test:raw_iron_block", source("test:raw_iron_block_source"), Map.of(OFFENSE, 9.0));
        check(category(compressed.analyze(), OFFENSE).sourceFamilies() == 2,
                "Unrelated direct items stay distinct without evidence of reversible conversion");
        compressed.processes.add(conversion("test:compress", "test:raw_iron", 9, "test:raw_iron_block", 1));
        compressed.processes.add(conversion("test:uncompress", "test:raw_iron_block", 1, "test:raw_iron", 9));
        var canonical = category(compressed.analyze(), OFFENSE);
        check(canonical.sourceFamilies() == 1, "Exact reversible 9:1 forms share one material family");
        near(canonical.effectiveSources(), 1, "Compressed payout cannot inflate direct material diversity");

        Fixture oneEvent = new Fixture();
        for (int index = 0; index < 20; index++)
            oneEvent.add("test:event_output_" + index, source("test:one_source_event"), Map.of(OFFENSE, 1.0));
        near(category(oneEvent.analyze(), OFFENSE).effectiveSources(), 1,
                "Many distinct materials from one source event still provide only one source opportunity");
    }

    private static ProductionGraph.Process conversion(String id, String input, double inputCount, String output, double outputCount) {
        return new ProductionGraph.Process(id, "crafting", List.of(new ProductionGraph.Input(List.of(input), inputCount, true)),
                List.of(new ProductionGraph.Output(output, outputCount, 1, false)), 0, 0, "local_fixture", 1, Map.of());
    }

    private static void jointSourceMaterialMatching() {
        Fixture bottleneck = new Fixture();
        for (String essence : ESSENCES.subList(1, ESSENCES.size())) bottleneck.addSources(essence, 4);
        bottleneck.addWithSources("test:material_1", List.of(source("test:a"), source("test:b"),
                source("test:c"), source("test:d")), 1);
        for (int material = 2; material <= 4; material++)
            bottleneck.addWithSources("test:material_" + material, List.of(source("test:a")), 1);
        var constrained = category(bottleneck.analyze(), OFFENSE);
        near(constrained.effectiveSources(), 2,
                "A broad source plus three sources for its same material have only two independent opportunities");
        near(constrained.coverage(), 0.5, "Independent source and material totals cannot conceal a joint bottleneck");
        near(bottleneck.analyze().multiplier(), 1.125,
                "The joint bottleneck cannot falsely produce full coverage and the 0.75 abundance floor");

        for (int permutation = 0; permutation < 8; permutation++) {
            near(category(reroutingFixture(permutation, 0.75).analyze(), OFFENSE).effectiveSources(), 1.5,
                    "Matching must reroute a strong first edge to admit two alternatives, permutation " + permutation);
            near(category(reroutingFixture(permutation, 0.5).analyze(), OFFENSE).effectiveSources(), 1.25,
                    "Lower-weight alternative still permits a better joint assignment, permutation " + permutation);
        }
        Fixture weighted = new Fixture();
        weighted.addWithSources("test:preferred", List.of(source("test:a"),
                source("test:b", AcquisitionSource.Kind.LOOT, ProgressionBand.ENTRY, false, 0, 0.5, List.of())), 1);
        weighted.addWithSources("test:weak_alternative", List.of(weightedSource("test:a", 0.5)), 0.25);
        near(category(weighted.analyze(), OFFENSE).effectiveSources(), 1,
                "Maximum weight must retain one strong opportunity instead of two weaker pairs totaling only 0.25");

        Fixture growing = new Fixture();
        growing.addWithSources("test:m1", List.of(source("test:a")), 1);
        double previous = category(growing.analyze(), OFFENSE).effectiveSources();
        growing.addWithSources("test:m2", List.of(weightedSource("test:a", 0.75)), 1);
        previous = nondecreasing(growing, previous, "Adding an alternative material");
        growing.addWithSources("test:m1", List.of(source("test:a"), weightedSource("test:b", 0.75)), 1);
        previous = nondecreasing(growing, previous, "Adding a source requiring an alternating reassignment");
        near(previous, 1.5, "The newly admitted source should improve usable supply");
        growing.addWithSources("test:m2", List.of(weightedSource("test:a", 0.75), source("test:b")), 1);
        previous = nondecreasing(growing, previous, "Adding a stronger edge");
        near(previous, 2, "The added edge permits two strong independent opportunities");
        growing.addWithSources("test:m2", List.of(weightedSource("test:a", 0.75), source("test:b"), source("test:c")), 1);
        previous = nondecreasing(growing, previous, "Adding a source without a new independent material");
        growing.addWithSources("test:m3", List.of(source("test:c")), 1);
        near(nondecreasing(growing, previous, "Adding a material for the new source"), 3,
                "Three independent usable opportunities should now be recognized");
    }

    private static Fixture reroutingFixture(int permutation, double alternative) {
        String a = (permutation & 1) == 0 ? "test:a" : "test:z";
        String b = (permutation & 1) == 0 ? "test:z" : "test:a";
        String m1 = (permutation & 2) == 0 ? "test:m_a" : "test:m_z";
        String m2 = (permutation & 2) == 0 ? "test:m_z" : "test:m_a";
        Fixture fixture = new Fixture();
        List<AcquisitionSource> shared = (permutation & 4) == 0
                ? List.of(source(a), weightedSource(b, 0.75)) : List.of(weightedSource(b, 0.75), source(a));
        if ((permutation & 4) == 0) {
            fixture.addWithSources(m1, shared, 1);
            fixture.addWithSources(m2, List.of(weightedSource(a, alternative)), 1);
        } else {
            fixture.addWithSources(m2, List.of(weightedSource(a, alternative)), 1);
            fixture.addWithSources(m1, shared, 1);
        }
        return fixture;
    }

    private static double nondecreasing(Fixture fixture, double previous, String change) {
        double current = category(fixture.analyze(), OFFENSE).effectiveSources();
        check(current >= previous, change + " cannot reduce jointly attainable supply");
        return current;
    }

    /** Enumerate all assignments on tiny graphs as an independent oracle, not the production path algorithm. */
    private static void exhaustiveSmallMatchingFixtures() {
        java.util.Random random = new java.util.Random(0xEA0915L);
        for (int seed = 0; seed < 20; seed++) {
            int size = 3 + seed % 2;
            double[][] weights = new double[size][size];
            for (int source = 0; source < size; source++) for (int material = 0; material < size; material++) {
                weights[source][material] = switch (random.nextInt(5)) {
                    case 2 -> 0.5;
                    case 3 -> 0.75;
                    case 4 -> 1;
                    default -> 0;
                };
            }
            double expected = exhaustiveBest(weights, 0, 0);
            double actual = category(matrixFixture(weights, false).analyze(), OFFENSE).effectiveSources();
            near(actual, expected, "Public supply analysis differs from exhaustive matching oracle, fixture " + seed);
            near(category(matrixFixture(weights, true).analyze(), OFFENSE).effectiveSources(), expected,
                    "Renaming/reversing source and material order changes the optimal supply, fixture " + seed);
            int changedSource = random.nextInt(size), changedMaterial = random.nextInt(size);
            weights[changedSource][changedMaterial] = 1;
            double augmented = category(matrixFixture(weights, false).analyze(), OFFENSE).effectiveSources();
            near(augmented, exhaustiveBest(weights, 0, 0), "Adding or strengthening an edge must still match exhaustive optimum");
            check(augmented >= actual, "An additional admitted edge must not reduce effective source supply");
        }
    }

    private static double exhaustiveBest(double[][] weights, int source, int usedMaterials) {
        if (source == weights.length) return 0;
        double best = exhaustiveBest(weights, source + 1, usedMaterials);
        for (int material = 0; material < weights[source].length; material++) {
            if ((usedMaterials & 1 << material) == 0 && weights[source][material] > 0)
                best = Math.max(best, weights[source][material]
                        + exhaustiveBest(weights, source + 1, usedMaterials | 1 << material));
        }
        return best;
    }

    private static Fixture matrixFixture(double[][] weights, boolean reverse) {
        Fixture fixture = new Fixture();
        int size = weights.length;
        for (int row = 0; row < size; row++) {
            int material = reverse ? size - 1 - row : row;
            List<AcquisitionSource> sources = new ArrayList<>();
            for (int column = 0; column < size; column++) {
                int source = reverse ? size - 1 - column : column;
                if (weights[source][material] > 0)
                    sources.add(weightedSource("test:matrix_source_" + (reverse ? size - 1 - source : source), weights[source][material]));
            }
            if (!sources.isEmpty()) fixture.addWithSources("test:matrix_material_" + (reverse ? size - 1 - material : material), sources, 1);
        }
        return fixture;
    }

    private static AcquisitionSource weightedSource(String id, double confidence) {
        return source(id, AcquisitionSource.Kind.WORLD_GENERATION, ProgressionBand.ENTRY, false, 0, confidence, List.of());
    }

    private static void evidenceExclusions() {
        for (AcquisitionSource.Kind kind : List.of(AcquisitionSource.Kind.RECIPE, AcquisitionSource.Kind.PLAYER_ACTION,
                AcquisitionSource.Kind.MACHINE, AcquisitionSource.Kind.BYPRODUCT, AcquisitionSource.Kind.ADMINISTRATIVE)) {
            Fixture fixture = new Fixture();
            fixture.add("test:excluded", source("test:excluded", kind, ProgressionBand.ENTRY, false, 0, 1, List.of()),
                    Map.of(OFFENSE, 1.0));
            absent(fixture, "Non-direct source kind " + kind);
        }
        for (AcquisitionSource.Kind kind : List.of(AcquisitionSource.Kind.WORLD_GENERATION, AcquisitionSource.Kind.FARMING,
                AcquisitionSource.Kind.MOB_DROP, AcquisitionSource.Kind.FISHING, AcquisitionSource.Kind.TRADE,
                AcquisitionSource.Kind.LOOT, AcquisitionSource.Kind.PASSIVE_GENERATION, AcquisitionSource.Kind.INFINITE_BULK)) {
            Fixture fixture = new Fixture();
            fixture.add("test:direct", source("test:direct", kind, ProgressionBand.EARLY, false, 0, 1, List.of()), Map.of(OFFENSE, 1.0));
            check(category(fixture.analyze(), OFFENSE).effectiveSources() > 0, "Recognized early direct source contributes: " + kind);
        }
        for (String exclusion : List.of("internal", "unreachable", "late_resource", "late_source", "zero_rate", "zero_yield", "unrouted")) {
            Fixture fixture = new Fixture();
            AcquisitionSource source = source("test:source", AcquisitionSource.Kind.WORLD_GENERATION,
                    exclusion.equals("late_source") ? ProgressionBand.MID : ProgressionBand.ENTRY,
                    exclusion.equals("zero_rate"), 0, 1, List.of());
            fixture.add("test:excluded", source, exclusion.equals("zero_yield") ? Map.of()
                            : exclusion.equals("unrouted") ? Map.of("test:unrequested_category", 1.0) : Map.of(OFFENSE, 1.0),
                    !exclusion.equals("unreachable"), !exclusion.equals("internal"),
                    exclusion.equals("late_resource") ? ProgressionBand.MID : ProgressionBand.ENTRY,
                    Availability.FINITE, 1);
            absent(fixture, exclusion);
        }
        Fixture noFinalValue = new Fixture();
        noFinalValue.add("test:no_final_value", source("test:source"), Map.of(OFFENSE, 1.0));
        noFinalValue.values.clear();
        absent(noFinalValue, "Raw economic evidence cannot substitute for final routed yield");
        Fixture noOutput = new Fixture();
        noOutput.add("test:no_output", new AcquisitionSource("test:empty_source", AcquisitionSource.Kind.WORLD_GENERATION,
                ProgressionBand.ENTRY, 0, false, false, 0, 1, List.of(), "No productive output"), Map.of(OFFENSE, 1.0));
        absent(noOutput, "A direct source with no output cannot supply usable Essence");
    }

    private static void dependenciesAndStrength() {
        for (String status : List.of("unknown", "internal", "unreachable", "late")) {
            Fixture fixture = new Fixture();
            fixture.add("test:output", source("test:dependent", AcquisitionSource.Kind.FARMING, ProgressionBand.ENTRY,
                    false, 0, 1, List.of("test:dependency")), Map.of(OFFENSE, 1.0));
            if (!status.equals("unknown")) fixture.dependency("test:dependency", !status.equals("unreachable"),
                    !status.equals("internal"), status.equals("late") ? ProgressionBand.MID : ProgressionBand.ENTRY);
            absent(fixture, "Dependency must be reachable external early evidence: " + status);
        }
        Fixture accessible = new Fixture();
        accessible.add("test:output", source("test:dependent", AcquisitionSource.Kind.FARMING, ProgressionBand.EARLY,
                false, 0, 1, List.of("test:entry_dependency", "test:early_dependency")), Map.of(OFFENSE, 1.0));
        accessible.dependency("test:entry_dependency", true, true, ProgressionBand.ENTRY);
        accessible.dependency("test:early_dependency", true, true, ProgressionBand.EARLY);
        check(category(accessible.analyze(), OFFENSE).effectiveSources() > 0, "All accessible early dependencies permit a source");
        accessible.dependency("test:early_dependency", true, true, ProgressionBand.LATE);
        absent(accessible, "One late dependency disqualifies the whole source");

        double strong = strength(Availability.FINITE, 1, 1, 1);
        near(strength(Availability.UNKNOWN, 1, 1, 1), 0, "Unknown availability cannot establish abundance");
        near(strength(Availability.ADMINISTRATIVE, 1, 1, 1), 0, "Administrative availability cannot establish abundance");
        near(strength(Availability.FINITE, 0.25, 1, 1), 0, "Low-confidence resource evidence cannot establish abundance");
        near(strength(Availability.FINITE, 1, 0.25, 1), 0, "Low-confidence source evidence cannot establish abundance");
        check(strength(Availability.FINITE, 0.75, 1, 1) < strong, "Admitted resource confidence constrains source strength");
        check(strength(Availability.FINITE, 1, 0.75, 1) < strong, "Admitted source confidence constrains source strength");
        check(strength(Availability.FINITE, 1, 1, 0.25) < strong, "Fractional final yield counts less than a usable whole Essence unit");
        near(strength(Availability.FINITE, 0, 1, 1), 0, "Zero-confidence resource supplies no effective coverage");
        near(strength(Availability.FINITE, 1, 0, 1), 0, "Zero-confidence source supplies no effective coverage");
        Fixture uncertain = new Fixture();
        for (int index = 0; index < 200; index++)
            uncertain.add("test:uncertain_" + index, source("test:uncertain_source_" + index), Map.of(OFFENSE, 1.0),
                    true, true, ProgressionBand.ENTRY, index % 2 == 0 ? Availability.UNKNOWN : Availability.FINITE,
                    index % 2 == 0 ? 1 : 0.49);
        absent(uncertain, "Accumulated unknown or weak evidence cannot manufacture abundance");

        Fixture unknownRate = new Fixture();
        unknownRate.add("test:output", source("test:source"), Map.of(OFFENSE, 1.0));
        Fixture knownRate = new Fixture();
        knownRate.add("test:output", source("test:source", AcquisitionSource.Kind.WORLD_GENERATION,
                ProgressionBand.ENTRY, true, 100_000, 1, List.of()), Map.of(OFFENSE, 1.0));
        near(category(unknownRate.analyze(), OFFENSE).effectiveSources(), category(knownRate.analyze(), OFFENSE).effectiveSources(),
                "Positive measured rates must not turn this opportunity proxy into throughput balancing");
    }

    private static void generationAndOverrides() {
        LatentOreWorldgenSettings defaults = LatentOreWorldgenSettings.defaults();
        for (DimensionSettings dimension : List.of(defaults.overworld(), defaults.nether(), defaults.end())) {
            check(dimension.veinSize() == 9 && dimension.veinsPerChunk() == 16, "Default opportunity is size 9 with 16 attempts");
            near(dimension.discardChanceOnAirExposure(), 0, "Default ore remains visible at exposed cave surfaces");
        }
        Fixture scarce = new Fixture();
        Fixture abundant = rich(4);
        LatentOreWorldgenSettings high = scarce.generate(defaults);
        LatentOreWorldgenSettings low = abundant.generate(defaults);
        check(high.overworld().veinsPerChunk() == 24 && low.overworld().veinsPerChunk() == 12,
                "Generated default attempts span the stated 12-to-24 opportunity range");
        for (DimensionSettings dimension : List.of(low.overworld(), low.nether(), low.end())) {
            check(dimension.veinsPerChunk() >= 12 && dimension.veinSize() >= 9, "Automatic ore remains frequent and grouped in rich packs");
        }
        ResourceLocation custom = ResourceLocation.parse("test:custom_dimension");
        DimensionSettings customDistribution = high.distribution(custom, -320, 511);
        check(customDistribution.veinSize() == high.overworld().veinSize()
                        && customDistribution.veinsPerChunk() == high.overworld().veinsPerChunk(),
                "Automatic custom dimensions inherit generated opportunity counts");
        check(customDistribution.minY() == -320 && customDistribution.maxY() == 511,
                "Custom dimensions retain their real usable vertical range");

        DimensionSettings exact = new DimensionSettings(true, 3, 1, -200, 250, 0.8);
        ResourceLocation disabledId = ResourceLocation.parse("test:excluded_dimension");
        Map<ResourceLocation, DimensionOverride> overrides = Map.of(
                custom, new DimensionOverride(true, List.of(ResourceLocation.parse("test:primary_rock")), exact),
                disabledId, new DimensionOverride(false, List.of(), null));
        var configured = new LatentOreWorldgenSettings(new DimensionSettings(false, 9, 16, -48, 16, 0),
                new DimensionSettings(true, 9, 0, 16, 112, 0), defaults.end(), false, overrides);
        var generated = scarce.generate(configured);
        check(!generated.overworld().enabled(), "Disabled vanilla generation stays disabled");
        check(generated.nether().veinsPerChunk() == 0, "Explicit zero-attempt generation stays off");
        check(!generated.automaticDimensions(), "Automatic-dimension exclusion remains intact");
        check(generated.dimensions().equals(overrides), "Explicit dimension rules and host overrides remain unchanged");
        check(generated.distribution(custom, -320, 511).equals(exact), "A complete explicit distribution bypasses automatic scaling");
        check(!generated.distribution(disabledId, -320, 511).enabled(), "An explicit disabled dimension remains disabled");
    }

    private static Fixture rich(int count) {
        Fixture fixture = new Fixture();
        for (String essence : ESSENCES) fixture.addSources(essence, count);
        return fixture;
    }

    private static double strength(Availability availability, double resourceConfidence, double sourceConfidence, double yield) {
        Fixture fixture = new Fixture();
        fixture.add("test:output", source("test:source", AcquisitionSource.Kind.WORLD_GENERATION,
                ProgressionBand.ENTRY, false, 0, sourceConfidence, List.of()), Map.of(OFFENSE, yield),
                true, true, ProgressionBand.ENTRY, availability, resourceConfidence);
        return category(fixture.analyze(), OFFENSE).effectiveSources();
    }

    private static void absent(Fixture fixture, String message) {
        var category = category(fixture.analyze(), OFFENSE);
        near(category.effectiveSources(), 0, message);
        near(category.coverage(), 0, "Excluded evidence cannot provide coverage: " + message);
    }

    private static LatentOreBalanceGenerator.CategorySupply category(LatentOreBalanceGenerator.Analysis analysis, String essence) {
        return analysis.categories().stream().filter(value -> value.essenceId().equals(essence)).findFirst().orElseThrow();
    }

    private static String family(String essence, int index) {
        return "test:" + essence.substring(essence.indexOf(':') + 1) + "_source_" + index;
    }

    private static AcquisitionSource source(String id) {
        return source(id, AcquisitionSource.Kind.WORLD_GENERATION, ProgressionBand.ENTRY, false, 0, 1, List.of());
    }

    private static AcquisitionSource source(String id, AcquisitionSource.Kind kind, ProgressionBand stage,
                                             boolean rateKnown, double rate, double confidence, List<String> dependencies) {
        return new AcquisitionSource(id, kind, stage, 1, false, rateKnown, rate, confidence, dependencies,
                "Local direct-acquisition balance fixture");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double actual, double expected, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1.0E-9,
                message + ": expected " + expected + ", got " + actual);
    }

    private static final class Fixture {
        private final Map<String, ResourceEvidence> resources = new TreeMap<>();
        private final Map<String, EconomyProfile.ResourceValue> values = new TreeMap<>();
        private final List<ProductionGraph.Process> processes = new ArrayList<>();

        private void addSources(String essence, int count) {
            for (int index = 0; index < count; index++) add(family(essence, index) + "_item",
                    source(family(essence, index)), Map.of(essence, 1.0));
        }

        private void add(String item, AcquisitionSource source, Map<String, Double> routes) {
            add(item, source, routes, true, true, ProgressionBand.ENTRY, Availability.FINITE, 1);
        }

        private void addWithSources(String item, List<AcquisitionSource> sources, double yield) {
            add(item, sources.getFirst(), Map.of(OFFENSE, yield));
            ResourceEvidence value = resources.get(item);
            resources.put(item, new ResourceEvidence(item, value.stage(), value.availability(), value.automation(),
                    value.reachable(), value.external(), value.economicValue(), value.confidence(), sources, value.warnings()));
        }

        private void add(String item, AcquisitionSource source, Map<String, Double> routes,
                         boolean reachable, boolean external, ProgressionBand stage, Availability availability, double confidence) {
            resources.put(item, new ResourceEvidence(item, stage, availability, Automation.NONE, reachable, external,
                    100, confidence, List.of(source), List.of()));
            double total = routes.values().stream().mapToDouble(Double::doubleValue).sum();
            values.put(item, new EconomyProfile.ResourceValue(new EconomicValue(100), DissolutionYield.of(total), routes, List.of()));
        }

        private void dependency(String item, boolean reachable, boolean external, ProgressionBand stage) {
            resources.put(item, new ResourceEvidence(item, stage, Availability.FINITE, Automation.NONE, reachable, external,
                    1, 1, List.of(), List.of()));
        }

        private PackEvidence evidence() { return new PackEvidence(resources, List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of()); }
        private EconomyProfile economy() { return new EconomyProfile(values, List.of(), processes, List.of(), 0, EconomyProcessingPolicy.defaults()); }
        private LatentOreBalanceGenerator.Analysis analyze() { return LatentOreBalanceGenerator.analyze(evidence(), economy(), ESSENCES); }
        private LatentOreWorldgenSettings generate(LatentOreWorldgenSettings settings) {
            return LatentOreBalanceGenerator.generate(evidence(), economy(), settings);
        }
    }
}
