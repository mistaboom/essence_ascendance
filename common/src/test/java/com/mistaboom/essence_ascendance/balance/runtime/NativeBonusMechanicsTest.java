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
        var shield = com.mistaboom.essence_ascendance.config.ShieldBalanceSettings.defaults();
        var absent = new EnumMap<com.mistaboom.essence_ascendance.equipment.EquipmentTier, Double>(com.mistaboom.essence_ascendance.equipment.EquipmentTier.class);
        for (var tier : com.mistaboom.essence_ascendance.equipment.EquipmentTier.values()) absent.put(tier, 0.0);
        try {
            new com.mistaboom.essence_ascendance.config.ShieldBalanceSettings(0, shield.minimumDisableTicks(), shield.durability(), absent, shield.blockAmplification());
            throw new AssertionError("No native reflection route accepted");
        } catch (IllegalArgumentException expected) { }
        var step = NativeBonusMechanics.step(1);
        check(step.source().equals("registered_block_collision_shapes+native_attribute"), "did not use registered shapes");
        check(step.thresholds().size() == 2, "nearby farmland surface became an imperceptible extra progression checkpoint");
        check(Math.abs(step.base() + step.thresholds().getFirst() - 1) < 1e-7, "first native block boundary not reached");
        check(Math.abs(step.base() + step.maximum() - 1.5) < 1e-7, "fence boundary not reached");
        check(step.evidence().stream().anyMatch(line -> line.contains("minecraft:oak_fence")), "observed provenance omitted");
        check(step.equals(NativeBonusMechanics.step(1)), "native measurement is not deterministic");
        var fallback = RuntimeReferencePolicy.withBootstrapReferences(() -> NativeBonusMechanics.step(1));
        check(fallback.source().contains("fallback") && fallback.confidence() < step.confidence(), "Bootstrap fallback not honestly marked");
        var atypical = NativeBonusMechanics.meaningfulThresholds(.625, 1, List.of(.75, .9375, 1.25));
        check(atypical.equals(List.of(.3125, .625)), "modded/nonstandard native heights replaced with guessed grid boundaries");
        check(NativeBonusMechanics.meaningfulThresholds(.6, .1, List.of(1.0, 1.5)).isEmpty(), "attribute headroom exceeded");
        double waterMaximum = NativeBonusMechanics.additivePercentHeadroom(Attributes.WATER_MOVEMENT_EFFICIENCY);
        check(waterMaximum > 0 && waterMaximum <= 100, "native additive water movement saturation ignored");
        double sneakMaximum = NativeBonusMechanics.multipliedBasePercentHeadroom(Attributes.SNEAKING_SPEED);
        double sneakBase = NativeBonusMechanics.baseline(Attributes.SNEAKING_SPEED);
        check(Math.abs(sneakBase * (1 + sneakMaximum / 100)
                - Attributes.SNEAKING_SPEED.value().sanitizeValue(Double.MAX_VALUE)) < 1e-9,
                "multiplicative headroom must use the native baseline, not treat percent as additive points");
        for (var attribute : List.of(Attributes.ATTACK_KNOCKBACK, Attributes.KNOCKBACK_RESISTANCE,
                Attributes.MAX_HEALTH, Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE, Attributes.LUCK)) {
            double base = NativeBonusMechanics.baseline(attribute);
            double headroom = NativeBonusMechanics.additiveHeadroom(attribute);
            check(attribute.value().sanitizeValue(base + headroom) == attribute.value().sanitizeValue(Double.MAX_VALUE),
                    "Additive headroom must exactly reach native saturation: " + attribute);
            check(attribute.value().sanitizeValue(base + headroom + 1000) == attribute.value().sanitizeValue(base + headroom),
                    "Additional over-cap points must have no native effect: " + attribute);
        }
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.BLOCK_BREAK_SPEED, Attributes.JUMP_STRENGTH)) {
            double base = NativeBonusMechanics.baseline(attribute);
            double ceiling = attribute.value().sanitizeValue(Double.MAX_VALUE);
            check(Math.abs(base * (1 + NativeBonusMechanics.multipliedBasePercentHeadroom(attribute) / 100) - ceiling) < 1e-8,
                    "Multiplicative headroom must use its actual base: " + attribute);
        }
        check(NativeBonusMechanics.guardReadinessMaximum(20) == 80
                        && NativeBonusMechanics.guardReadinessMaximum(1) == 99
                        && NativeBonusMechanics.guardReadinessMaximum(100) == 80,
                "guard ceiling must retain whichever supported native timing still improves");
        for (int percent = 0; percent <= 100; percent++) {
            check(com.mistaboom.essence_ascendance.equipment.ShieldMath.disableTicks(100, percent, 1)
                    == Math.max(1, 100 - percent), "whole percent must save exact whole native ticks: " + percent);
        }
        check(com.mistaboom.essence_ascendance.equipment.ShieldMath.raiseDelayTicks(5, 20, 1) == 4
                        && com.mistaboom.essence_ascendance.equipment.ShieldMath.raiseDelayTicks(5, 19.999999, 1) == 5,
                "native ceil must preserve timing boundaries without rounding up an unearned improvement");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "NativeBonusMechanicsTest PASS: registered native collision thresholds, grouping, limits, fallback and determinism");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
