package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.*;

/** Wide pack frontiers can put native early equipment above its external reference. */
public final class NativeEquipmentHeadroomTest {
    public static void main(String[] args) {
        var output = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(output));
        if (RuntimeBuildScenarios.withNativeAllowance(16, 24, 1.5) != 32
                || RuntimeBuildScenarios.withNativeAllowance(16, 24, 1) != 24
                || RuntimeBuildScenarios.withNativeAllowance(16, 12, 1.5) != 24)
            throw new AssertionError("Native allowance must retain exactly the existing external added-power budget");
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init(); EquipmentProfiles.init();
        var resources = new TreeMap<String, ResourceEvidence>();
        var values = new TreeMap<String, EconomyProfile.ResourceValue>();
        int index = 0;
        for (var essence : EssenceRegistry.values()) {
            String item = "test:resource_" + index++;
            resources.put(item, new ResourceEvidence(item, ProgressionBand.ENTRY, Availability.FINITE,
                    Automation.NONE, true, true, 100, .9, List.of(), List.of()));
            values.put(item, new EconomyProfile.ResourceValue(new EconomicValue(100), DissolutionYield.of(10),
                    Map.of(essence.id().toString(), 10.0), List.of()));
        }
        var references = new EnumMap<ProgressionBand, Map<CapabilityAxis, Double>>(ProgressionBand.class);
        var equipment = new ArrayList<EquipmentReference>();
        for (var band : ProgressionBand.values()) {
            double damage = new double[]{8, 16, 32, 64, 128}[band.ordinal()];
            equipment.add(new EquipmentReference("test:melee_" + band, "mainhand_melee", band,
                    Map.of(CapabilityAxis.ATTACK_RATE, 1.6, CapabilityAxis.SUSTAINED_DAMAGE, damage * 1.6,
                            CapabilityAxis.BURST_DAMAGE, damage), List.of(), true, true, 1, "synthetic family observation"));
            // A stronger early ranged family affects the original shared physical curve,
            // while the matching melee comparison correctly remains on its own family.
            damage = Math.max(32, damage);
            references.put(band, Map.of(CapabilityAxis.ATTACK_RATE, 1.6,
                    CapabilityAxis.SUSTAINED_DAMAGE, damage * 1.6, CapabilityAxis.BURST_DAMAGE, damage,
                    CapabilityAxis.ARMOR, 7 + band.ordinal() * 3.5, CapabilityAxis.TOUGHNESS, band.ordinal() * 2.0,
                    CapabilityAxis.MINING_SPEED, 4 + band.ordinal() * 2.0, CapabilityAxis.DURABILITY, 180 * Math.pow(1.8, band.ordinal()),
                    CapabilityAxis.HARVEST_LEVEL, (double) Math.min(3, band.ordinal())));
        }
        var evidence = new PackEvidence(resources, equipment, List.of(), references, List.of(), List.of(), Map.of());
        var economy = new EconomyProfile(values, List.of(), List.of(), List.of(), 0, EconomyProcessingPolicy.defaults());
        var uncalibrated = RuntimeBalanceDefinition.generate(evidence, null, BalanceSettings.defaults(), BalanceOverrides.empty());
        var runtime = RuntimeBalanceDefinition.generate(evidence, economy, BalanceSettings.defaults(), BalanceOverrides.empty());
        runtime.generationAnalysis().requireSafe();
        if (!runtime.config().equipmentBaselineConfig().tierBaselines().equals(uncalibrated.config().equipmentBaselineConfig().tierBaselines()))
            throw new AssertionError("Calibration must preserve the physical equipment curve");
        double nativeHit = EquipmentBaselineService.resolvedValue(
                runtime.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.DORMANT).meleeDamage(),
                EquipmentProfiles.MELEE_WEAPON, EquipmentBaselineProperty.MELEE_DAMAGE);
        if (nativeHit <= 8 * 1.5) throw new AssertionError("Fixture no longer exercises a native floor above the external ceiling");
        if (runtime.composition().get("offense_calibration") < .02)
            throw new AssertionError("First purchase must retain the enforced minimum power");
        output.println("NativeEquipmentHeadroomTest: wide-frontier default generation PASS; physical curve unchanged; native entry hit=" + nativeHit + ", old external ceiling=12");
    }
}
