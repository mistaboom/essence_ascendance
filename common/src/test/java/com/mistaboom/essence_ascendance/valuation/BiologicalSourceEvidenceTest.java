package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/** Actual registered mechanics; no entity spawning, world mutation or invented farm throughput. */
public final class BiologicalSourceEvidenceTest {
    private static int assertions;

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) ->
                failure.printStackTrace(new PrintStream(new FileOutputStream(FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        var indexed = ProceduralValuationIndex.biologicalSources((ProceduralMobSpawnIndex) null);
        for (var item : List.of(Items.TURTLE_SCUTE, Items.ARMADILLO_SCUTE, Items.EGG,
                Items.HONEYCOMB, Items.HONEY_BOTTLE)) {
            check(indexed.containsKey(item), "nonlethal output has a modeled source: " + item);
            var source = indexed.get(item).getFirst();
            AcquisitionSource projection = ValuationEvidenceSnapshot.biologicalSource(source, ProgressionBand.ENTRY);
            check(projection.kind() == AcquisitionSource.Kind.FARMING && projection.renewable(),
                    "a completed biological event is renewable farming");
            check(!projection.rateKnown() && projection.unitsPerSecond() == 0,
                    "event quantities must never masquerade as measured per-second throughput");
            check(projection.expectedOutput() == source.event().count(), "event count survives diagnostic projection");
        }
        check(indexed.get(Items.TURTLE_SCUTE).getFirst().event().producer() == EntityType.TURTLE,
                "growth output retains actual producer instead of pretending to be killing loot");
        var comb = indexed.get(Items.HONEYCOMB).getFirst().event();
        check(comb.count() == 3, "full-hive honeycomb quantity is the engine's three-item event");
        check(comb.requirements().getFirst().item() == Items.SHEARS
                && !comb.requirements().getFirst().consumed()
                && comb.requirements().getFirst().durabilityWear() == 1, "honeycomb charges reusable shears wear");
        var honey = indexed.get(Items.HONEY_BOTTLE).getFirst().event();
        check(honey.requirements().getFirst().item() == Items.GLASS_BOTTLE
                && honey.requirements().getFirst().consumed(), "honey consumes an actual bottle, not a reusable catalyst");
        check(!indexed.containsKey(Items.COBBLESTONE) && !indexed.containsKey(Items.SAND),
                "biological sources cannot confer attainability on arbitrary bulk materials");

        check(ProceduralValuationEngine.biologicalAcquisitionValue(30, 2, 3, 6) == 22,
                "material opportunity, producer access, event quantity and inputs determine value");
        check(ProceduralValuationEngine.biologicalAcquisitionValue(60, 2, 3, 12) == 44,
                "scaling observed material and prerequisite effort scales the result without fixed payouts");
        check(ProceduralValuationEngine.biologicalAcquisitionValue(0, 1, 1, 0) == 0,
                "source calculation does not impose an artificial positive-value floor");
        reject(() -> ProceduralValuationEngine.biologicalAcquisitionValue(30, Double.NaN, 1, 0));
        reject(() -> ProceduralValuationEngine.biologicalAcquisitionValue(30, 1, 0, 0));

        for (var block : List.of(Blocks.WHEAT, Blocks.COCOA, Blocks.SWEET_BERRY_BUSH,
                Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.SUGAR_CANE, Blocks.CACTUS))
            check(ValuationEvidenceSnapshot.renewableGrowth(block), "known growth capability is renewable: " + block);
        check(!ValuationEvidenceSnapshot.renewableGrowth(Blocks.COBBLESTONE), "ordinary solid block is not biological growth");
        blockSourceProjection();
        check(ProceduralBlockHarvest.supportedProperties(Blocks.CAVE_VINES).contains("berries")
                && ProceduralBlockHarvest.supportedProperties(Blocks.CAVE_VINES_PLANT).contains("berries"),
                "reachable cave-vine berry state is included in loot evaluation");
        check(!ProceduralBlockHarvest.supportedProperties(Blocks.BEEHIVE).contains("honey_level"),
                "bee production needs its typed source and tool/container prerequisites, not a free state assumption");

        var disabled = new BalanceOverrides.FactOverride("disable_scute", BalanceOverrides.SubjectKind.ITEM,
                "minecraft:turtle_scute", 100, Map.of("attainable", false), "fixture", 0);
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(disabled), Map.of()));
        check(!ProceduralValuationIndex.biologicalSources((ProceduralMobSpawnIndex) null).containsKey(Items.TURTLE_SCUTE),
                "authoritative item exclusion also excludes the runtime source");
        ValuationGenerationInputs.configure(BalanceOverrides.empty());

        var extension = new BiologicalAcquisitionSources.Source(ResourceLocation.parse("fixture:renewable_fiber"),
                EntityType.SHEEP, Items.STRING, 2, BiologicalAcquisitionSources.Event.PERIODIC_PRODUCTION,
                List.of(), "Fixture provider explicitly declares a future production mechanic");
        BiologicalAcquisitionSources.register(extension);
        check(ProceduralValuationIndex.biologicalSources((ProceduralMobSpawnIndex) null).get(Items.STRING)
                .getFirst().event().equals(extension), "future providers reuse the same source collection and value pipeline");
        reject(() -> BiologicalAcquisitionSources.register(extension));
        new PrintStream(new FileOutputStream(FileDescriptor.out)).println("BiologicalSourceEvidenceTest: "
                + assertions + " farming, prerequisite, exact-event and provider checks PASS");
    }

    private static void reject(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid or duplicate evidence rejected");
    }

    private static void blockSourceProjection() {
        var clay = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:clay"),
                1, 4, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, false,
                List.of("fixture verified clay-ball drop"), null, false);
        var natural = ValuationEvidenceSnapshot.blockSource(clay, Blocks.CLAY, List.of("configured feature minecraft:disk_clay"));
        check(natural.kind() == AcquisitionSource.Kind.WORLD_GENERATION && natural.dependencies().isEmpty(),
                "Collected placement evidence establishes a direct natural block source");
        check(natural.reason().contains("minecraft:disk_clay"), "Natural-source provenance remains visible");
        var supplied = ValuationEvidenceSnapshot.blockSource(clay, Blocks.CLAY, List.of());
        check(supplied.kind() == AcquisitionSource.Kind.PLAYER_ACTION,
                "A manufactured or unproven block break cannot masquerade as world generation");
        check(supplied.dependencies().equals(List.of("minecraft:clay")),
                "An unproven clay-ball break route requires an actual clay block");
        check(supplied.stage() == natural.stage() && supplied.expectedOutput() == natural.expectedOutput()
                        && supplied.confidence() == natural.confidence(),
                "Natural-provenance correction preserves existing stage, count and confidence observations");
        check(!supplied.rateKnown() && supplied.unitsPerSecond() == 0,
                "Missing natural provenance never invents a source rate");
        var crop = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:wheat"),
                1, 1, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, false,
                List.of("fixture crop drop"), Items.SHEARS, false);
        var growing = ValuationEvidenceSnapshot.blockSource(crop, Blocks.WHEAT, List.of("fixture explicitly placed crop"));
        check(growing.kind() == AcquisitionSource.Kind.FARMING && growing.renewable(),
                "Known growth plus natural placement is a renewable farming source");
        check(growing.dependencies().equals(List.of("minecraft:shears")), "Known farming retains real tool prerequisites");
        var planted = ValuationEvidenceSnapshot.blockSource(crop, Blocks.WHEAT, List.of());
        check(planted.kind() == AcquisitionSource.Kind.PLAYER_ACTION && planted.renewable(),
                "Unproven planting retains growth capability without claiming independent natural access");
        check(planted.dependencies().contains("minecraft:shears") && planted.dependencies().contains("minecraft:wheat_seeds"),
                "Player-grown crops retain tool and planting-item prerequisites");
        var withoutItem = ValuationEvidenceSnapshot.blockSource(crop, Blocks.WATER, List.of());
        check(!withoutItem.dependencies().contains("minecraft:air"), "Blocks without items do not invent an air dependency");
        var silkSource = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:stone"),
                1, 1, 0, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, false,
                List.of("fixture silk drop"), Items.IRON_PICKAXE, true);
        var silk = ValuationEvidenceSnapshot.blockSource(silkSource, Blocks.STONE, List.of("terrain definition minecraft:overworld"));
        check(silk.kind() == AcquisitionSource.Kind.PLAYER_ACTION && silk.reason().contains("Silk Touch"),
                "Natural placement alone cannot prove early access to a Silk Touch drop");
        check(silk.dependencies().equals(List.of("minecraft:iron_pickaxe")) && silk.stage() == ProgressionBand.ENTRY,
                "Silk uncertainty preserves original stage and observed tool without inventing an enchantment item");
        var conditionalSource = new ProceduralValuationIndex.BlockDropSource(ResourceLocation.parse("minecraft:clay"),
                1, 4, 2, ProceduralValuationResult.ProgressionBand.NETHER, 1, false,
                List.of("fixture conditional drop"), null, false);
        var conditional = ValuationEvidenceSnapshot.blockSource(conditionalSource, Blocks.CLAY, List.of("fixture terrain placement"));
        check(conditional.kind() == AcquisitionSource.Kind.PLAYER_ACTION && conditional.reason().contains("Unresolved harvest conditions: 2"),
                "Unresolved loot conditions cannot establish directly accessible natural supply");
        check(conditional.stage() == ProgressionBand.LATE && conditional.expectedOutput() == 4 && conditional.dependencies().isEmpty(),
                "Conditional source preserves observations without fabricated prerequisites or stage changes");
    }
    private static void check(boolean condition, String detail) {
        assertions++;
        if (!condition) throw new AssertionError(detail);
    }
}
