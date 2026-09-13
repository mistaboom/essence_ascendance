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
    private static void check(boolean condition, String detail) {
        assertions++;
        if (!condition) throw new AssertionError(detail);
    }
}
