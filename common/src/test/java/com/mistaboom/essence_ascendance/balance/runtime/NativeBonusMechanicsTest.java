package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.engine.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.*;

/** Real registered collision shapes and attribute limits, without loading or modifying a world. */
public final class NativeBonusMechanicsTest {
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) ->
                error.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var input = evidence(true);
        var step = NativeBonusMechanics.step(input, 1);
        check(step.source().equals("reachable_block_collision_shapes+native_attribute"), "did not use registered shapes");
        check(step.thresholds().size() == 2, "nearby farmland surface became an imperceptible extra progression checkpoint");
        check(Math.abs(step.base() + step.thresholds().getFirst() - 1) < 1e-7, "first native block boundary not reached");
        check(Math.abs(step.base() + step.maximum() - 1.5) < 1e-7, "fence boundary not reached");
        check(step.evidence().stream().anyMatch(line -> line.contains("minecraft:oak_fence")), "observed provenance omitted");
        check(step.equals(NativeBonusMechanics.step(input, 1)), "native measurement is not deterministic");
        var unreachable = NativeBonusMechanics.step(evidence(false), 1);
        check(unreachable.thresholds().size() == 1, "unreachable tall block restructured progression");
        var fallback = NativeBonusMechanics.step(new PackEvidence(Map.of(), List.of(), List.of(), Map.of(),
                List.of(), List.of(), Map.of()), 1);
        check(fallback.source().contains("fallback") && fallback.confidence() < step.confidence(), "empty evidence not honestly marked");
        var atypical = NativeBonusMechanics.meaningfulThresholds(.625, 1, List.of(.75, .9375, 1.25));
        check(atypical.equals(List.of(.3125, .625)), "modded/nonstandard native heights replaced with guessed grid boundaries");
        check(NativeBonusMechanics.meaningfulThresholds(.6, .1, List.of(1.0, 1.5)).isEmpty(), "attribute headroom exceeded");
        double waterMaximum = NativeBonusMechanics.additivePercentHeadroom(Attributes.WATER_MOVEMENT_EFFICIENCY);
        check(waterMaximum > 0 && waterMaximum <= 100, "native additive water movement saturation ignored");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "NativeBonusMechanicsTest PASS: reachable native collision thresholds, grouping, limits, fallback and determinism");
    }
    private static PackEvidence evidence(boolean fenceReachable) {
        Map<String, ResourceEvidence> resources = new TreeMap<>();
        for (String id : List.of("minecraft:stone", "minecraft:farmland", "minecraft:oak_fence"))
            resources.put(id, new ResourceEvidence(id, ProgressionBand.ENTRY, Availability.RENEWABLE_MANUAL,
                    Automation.NONE, !id.equals("minecraft:oak_fence") || fenceReachable, true, 1, .9, List.of(), List.of()));
        return new PackEvidence(resources, List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
