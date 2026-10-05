package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** Retains realistic tool alternatives in a bounded heap; no server or world is created. */
public final class BlockSourceMemoryTest {
    private static int checks;
    private static final int ITEM_GROUPS = 20;
    private static final int BLOCKS_PER_ITEM = 100;
    private static final int ALTERNATIVES_PER_BLOCK = 150;
    private static final int SOURCE_COUNT = ITEM_GROUPS * BLOCKS_PER_ITEM * ALTERNATIVES_PER_BLOCK;

    public static void main(String[] args) {
        var out = new PrintStream(new FileOutputStream(FileDescriptor.out));
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) ->
                failure.printStackTrace(new PrintStream(new FileOutputStream(FileDescriptor.err))));
        boolean unshared = args.length == 1 && args[0].equals("--unshared");
        if (args.length > 1 || args.length == 1 && !unshared)
            throw new IllegalArgumentException("Only --unshared is supported");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        projectionEquivalence();
        long started = System.nanoTime();
        out.println("Block source stress: mode=" + (unshared ? "unshared reproduction" : "shared")
                + ", sources=" + SOURCE_COUNT + ", maxHeapBytes=" + Runtime.getRuntime().maxMemory());
        retainedSources(unshared, out);
        out.println("BlockSourceMemoryTest PASS: " + checks + " checks; elapsedMillis="
                + (System.nanoTime() - started) / 1_000_000 + "; heapUsedBytes=" + usedHeap());
    }

    private static void projectionEquivalence() {
        var projection = new ValuationEvidenceSnapshot.BlockSourceProjection();
        var block = ResourceLocation.parse("fixture:ore");
        var signals = List.of("native base-drop count", "reusable harvest tool; tool acquisition required, wear amortized");
        var natural = List.of("configured feature fixture:ore", "terrain definition fixture:surface");
        var wooden = source(block, Items.WOODEN_PICKAXE, false, 0, signals, .125, 2.75);
        var diamond = source(block, Items.DIAMOND_PICKAXE, false, 0, new ArrayList<>(signals), .5, 4.25);
        var first = projection.source(wooden, Blocks.STONE, natural);
        var second = projection.source(diamond, Blocks.STONE, new ArrayList<>(natural));
        check(first.equals(ValuationEvidenceSnapshot.blockSource(wooden, Blocks.STONE, natural)),
                "Shared projection preserves every original source field and full reason");
        check(second.equals(ValuationEvidenceSnapshot.blockSource(diamond, Blocks.STONE, natural)),
                "Different yield/tool source preserves every original field");
        check(first.reason() == second.reason(), "Equal diagnostic facts share the exact String object across tools");
        check(first.dependencies().equals(List.of("minecraft:wooden_pickaxe"))
                        && second.dependencies().equals(List.of("minecraft:diamond_pickaxe")),
                "Tool prerequisites remain distinct alternatives");
        check(first.expectedOutput() == 2.75 && second.expectedOutput() == 4.25
                        && first.confidence() == .76 && second.confidence() == .76,
                "Per-event count and confidence are preserved, not multiplied by occurrence chance");
        check(wooden.estimatedChance() == .125 && diamond.estimatedChance() == .5,
                "Projection does not mutate original occurrence chances");
        check(first.reason().equals(String.join("; ", signals) + "; Natural placement evidence: "
                        + String.join("; ", natural)), "Full original diagnostic text and ordering survive sharing");

        assertDifferent(projection, wooden, List.of("different native fact"), natural, false, 0, first.reason(), "signals");
        assertDifferent(projection, wooden, signals, List.of("configured feature fixture:other"), false, 0, first.reason(), "natural evidence");
        assertDifferent(projection, wooden, signals, List.of(), false, 0, first.reason(), "missing natural evidence");
        assertDifferent(projection, wooden, signals, natural, true, 0, first.reason(), "Silk Touch");
        assertDifferent(projection, wooden, signals, natural, false, 1, first.reason(), "unresolved count");
        var oneUnknown = projection.source(source(block, Items.WOODEN_PICKAXE, false, 1, signals, .125, 2.75), Blocks.STONE, natural);
        var twoUnknown = projection.source(source(block, Items.WOODEN_PICKAXE, false, 2, signals, .125, 2.75), Blocks.STONE, natural);
        check(!oneUnknown.reason().equals(twoUnknown.reason()), "Distinct unresolved counts retain distinct text");

        var other = source(ResourceLocation.parse("fixture:other_ore"), Items.WOODEN_PICKAXE, false, 0, signals, .125, 2.75);
        var afterEviction = projection.source(other, Blocks.STONE, natural);
        check(afterEviction.reason().equals(first.reason()) && afterEviction.reason() != first.reason(),
                "Moving to another block releases the previous block's reason cache");
        var revisited = projection.source(wooden, Blocks.STONE, natural);
        check(revisited.equals(first) && revisited.reason() != first.reason(),
                "Revisited block is rebuilt correctly after cache eviction");
    }

    private static void assertDifferent(ValuationEvidenceSnapshot.BlockSourceProjection projection,
                                        ProceduralValuationIndex.BlockDropSource original, List<String> signals,
                                        List<String> natural, boolean silk, int unresolved, String previous,
                                        String changed) {
        var changedSource = source(original.blockId(), original.reusableTool(), silk, unresolved,
                signals, original.estimatedChance(), original.expectedCount());
        var result = projection.source(changedSource, Blocks.STONE, natural);
        check(result.equals(ValuationEvidenceSnapshot.blockSource(changedSource, Blocks.STONE, natural)),
                "Original projection preserved after changing " + changed);
        check(!result.reason().equals(previous), "Reason changes with " + changed);
    }

    private static void retainedSources(boolean unshared, PrintStream out) {
        Item[] tools = { Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE };
        String[] toolIds = new String[tools.length];
        for (int i = 0; i < tools.length; i++) toolIds[i] = BuiltInRegistries.ITEM.getKey(tools[i]).toString();
        var projection = new ValuationEvidenceSnapshot.BlockSourceProjection();
        List<AcquisitionSource> retained = new ArrayList<>(SOURCE_COUNT);
        // Each item visits grouped block alternatives. Later items revisit the same blocks,
        // so this exercises cache eviction and reuse without a global intern table.
        for (int item = 0; item < ITEM_GROUPS; item++) {
            for (int block = 0; block < BLOCKS_PER_ITEM; block++) {
                var id = ResourceLocation.parse("fixture:block_" + block);
                List<String> signals = diagnosticSignals(id);
                List<String> natural = List.of("configured feature fixture:placement_" + block);
                String firstReason = null;
                for (int alternative = 0; alternative < ALTERNATIVES_PER_BLOCK; alternative++) {
                    var source = source(id, tools[alternative % tools.length], false, 1, signals,
                            .25 + .25 * (alternative % 3), 1 + alternative % 4);
                    var projected = unshared ? ValuationEvidenceSnapshot.blockSource(source, Blocks.STONE, natural)
                            : projection.source(source, Blocks.STONE, natural);
                    if (firstReason == null) {
                        firstReason = projected.reason();
                        check(firstReason.length() >= 1_000, "Stress diagnostic payload remains at least 1KB");
                    } else if (!unshared) check(projected.reason() == firstReason, "Stress tool alternatives share full diagnostics");
                    check(projected.dependencies().equals(List.of(toolIds[alternative % tools.length])),
                            "Stress source keeps its exact tool dependency");
                    check(projected.expectedOutput() == 1 + alternative % 4 && projected.confidence() == .76,
                            "Stress source keeps its own count and confidence");
                    retained.add(projected);
                }
            }
            if ((item + 1) % 5 == 0)
                out.println("Retained sources=" + retained.size() + "; heapUsedBytes=" + usedHeap());
        }
        check(retained.size() == SOURCE_COUNT, "Every source remains retained through completion");
        long reasonCharacters = 0;
        double totalExpectedOutput = 0;
        for (AcquisitionSource source : retained) {
            reasonCharacters += source.reason().length();
            totalExpectedOutput += source.expectedOutput();
        }
        check(reasonCharacters >= (long) SOURCE_COUNT * 1_000, "All full diagnostic text remains accessible");
        check(totalExpectedOutput == (long) ITEM_GROUPS * BLOCKS_PER_ITEM * 373,
                "Retained per-event output counts remain intact");
        out.println("Retained diagnostic characters=" + reasonCharacters + "; expectedOutputSum=" + totalExpectedOutput);
        java.lang.ref.Reference.reachabilityFence(retained);
    }

    private static List<String> diagnosticSignals(ResourceLocation id) {
        List<String> signals = new ArrayList<>();
        signals.add("effective block table " + id);
        for (int i = 0; i < 9; i++)
            signals.add("Audited predicate " + i + ": loaded source identity, conditional branch, base drop count, "
                    + "and unresolved runtime modifier definitions remain visible for this block harvest");
        signals.add("reusable harvest tool; tool acquisition required, wear amortized");
        return List.copyOf(signals);
    }

    private static ProceduralValuationIndex.BlockDropSource source(ResourceLocation id, Item tool, boolean silk,
                                                                   int unresolved, List<String> signals,
                                                                   double chance, double count) {
        return new ProceduralValuationIndex.BlockDropSource(id, chance, count, unresolved,
                ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, false, signals, tool, silk);
    }

    private static long usedHeap() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
