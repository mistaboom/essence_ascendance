package com.mistaboom.essence_ascendance.balance.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Dependency-free contract checks; runnable with Java 21 even when Loom cannot resolve. */
public final class BalanceEvidenceSelfTest {
    private static int checks;
    public static void main(String[] args) {
        inaccessibleAndCreativeOutliers();
        compatibleSlotsAndAlternatives();
        progressionAndDeterminism();
        outlierPolicies();
        usefulEquipmentFrontiers();
        policyAdjustedWeaponCadence();
        evidenceResolution();
        distinctCapabilities();
        System.out.println("Balance evidence checks passed: " + checks);
    }
    private static void inaccessibleAndCreativeOutliers() {
        EquipmentReference normal = equipment("pack:sword", "mainhand_melee", ProgressionBand.ENTRY, CapabilityAxis.BURST_DAMAGE, 6, true, true);
        EquipmentReference inaccessible = equipment("pack:unobtainable", "mainhand_melee", ProgressionBand.ENTRY, CapabilityAxis.BURST_DAMAGE, 1e12, false, true);
        EquipmentReference creative = equipment("pack:creative", "mainhand_melee", ProgressionBand.ENTRY, CapabilityAxis.BURST_DAMAGE, 1e9, true, false);
        for (String policy : List.of("EXCLUDE_UNSUPPORTED", "WINSORIZE", "INCLUDE_ATTAINABLE"))
            require(RobustFrontiers.build(List.of(normal, inaccessible, creative), policy).get(ProgressionBand.APEX).get(CapabilityAxis.BURST_DAMAGE) == 6,
                    "Forbidden references must not influence any outlier policy");
    }
    private static void compatibleSlotsAndAlternatives() {
        List<EquipmentReference> refs = List.of(
                equipment("pack:helmet", "head", ProgressionBand.MID, CapabilityAxis.ARMOR, 2, true, true),
                equipment("pack:chestplate", "chest", ProgressionBand.MID, CapabilityAxis.ARMOR, 6, true, true),
                equipment("pack:leggings", "legs", ProgressionBand.MID, CapabilityAxis.ARMOR, 5, true, true),
                equipment("pack:boots", "feet", ProgressionBand.MID, CapabilityAxis.ARMOR, 2, true, true),
                equipment("pack:sword", "mainhand_melee", ProgressionBand.MID, CapabilityAxis.SUSTAINED_DAMAGE, 12, true, true),
                equipment("pack:bow", "mainhand_bow", ProgressionBand.MID, CapabilityAxis.SUSTAINED_DAMAGE, 8, true, true));
        Map<CapabilityAxis, Double> axes = RobustFrontiers.build(refs).get(ProgressionBand.MID);
        require(axes.get(CapabilityAxis.ARMOR) == 15, "Armor reference must account for four compatible slots");
        require(axes.get(CapabilityAxis.SUSTAINED_DAMAGE) == 12, "Alternative weapons cannot be added together");
        require(axes.get(CapabilityAxis.EFFECTIVE_HEALTH) > 20 && axes.get(CapabilityAxis.EFFECTIVE_HEALTH) < 100,
                "Armor effective health uses bounded vanilla reduction at the declared reference hit");
    }
    private static void progressionAndDeterminism() {
        List<EquipmentReference> refs = new ArrayList<>();
        for (ProgressionBand band : ProgressionBand.values()) refs.add(equipment("pack:" + band, "mainhand_melee", band,
                CapabilityAxis.BURST_DAMAGE, 4 + band.ordinal() * 2, true, true));
        var first = RobustFrontiers.build(refs); Collections.reverse(refs); var second = RobustFrontiers.build(refs);
        require(first.equals(second), "Registry enumeration order must not affect reference values");
        double previous = 0;
        for (ProgressionBand band : ProgressionBand.values()) {
            double current = first.get(band).get(CapabilityAxis.BURST_DAMAGE);
            require(current >= previous, "Later progression cannot lose the reachable earlier frontier"); previous = current;
        }
    }
    private static void outlierPolicies() {
        List<Double> values = List.of(4.0, 5.0, 6.0, 1e12);
        double exclude = RobustFrontiers.percentile(values, .75, "EXCLUDE_UNSUPPORTED");
        double winsor = RobustFrontiers.percentile(values, .75, "WINSORIZE");
        double include = RobustFrontiers.percentile(values, .75, "INCLUDE_ATTAINABLE");
        require(exclude <= 6 && winsor < 100 && include > 1e6, "Policies must produce distinct, intentional extreme-item behavior");
    }
    private static void usefulEquipmentFrontiers() {
        List<EquipmentReference> refs = new ArrayList<>();
        for (int i = 0; i < 100; i++) refs.add(new EquipmentReference("pack:old_tool_" + i, "mainhand_melee", ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.DURABILITY, 32.0, CapabilityAxis.HARVEST_LEVEL, 0.0, CapabilityAxis.MINING_SPEED, 1.0),
                List.of(), true, true, .9, "earlier obsolete tool"));
        refs.add(new EquipmentReference("pack:late_pickaxe", "mainhand_melee", ProgressionBand.LATE,
                Map.of(CapabilityAxis.DURABILITY, 1561.0, CapabilityAxis.HARVEST_LEVEL, 3.0, CapabilityAxis.MINING_SPEED, 8.0),
                List.of(), true, true, .9, "observed late harvesting tool"));
        refs.add(new EquipmentReference("pack:late_sword", "mainhand_melee", ProgressionBand.LATE,
                Map.of(CapabilityAxis.SUSTAINED_DAMAGE, 11.2, CapabilityAxis.ATTACK_RATE, 1.6, CapabilityAxis.BURST_DAMAGE, 7.0),
                List.of(), true, true, .9, "observed late weapon"));
        refs.add(new EquipmentReference("pack:late_hoe", "mainhand_melee", ProgressionBand.LATE,
                Map.of(CapabilityAxis.SUSTAINED_DAMAGE, 4.0, CapabilityAxis.ATTACK_RATE, 4.0, CapabilityAxis.BURST_DAMAGE, 1.0,
                        CapabilityAxis.HARVEST_LEVEL, 0.0, CapabilityAxis.MINING_SPEED, 1.0),
                List.of(), true, true, .9, "fast tool with weak damage"));
        for (String policy : List.of("EXCLUDE_UNSUPPORTED", "WINSORIZE", "INCLUDE_ATTAINABLE")) {
            var frontier = RobustFrontiers.build(refs, policy).get(ProgressionBand.LATE);
            require(frontier.get(CapabilityAxis.HARVEST_LEVEL) == 3, "Non-pickaxes cannot erase an available harvest capability");
            require(frontier.get(CapabilityAxis.DURABILITY) == 1561, "Obsolete early gear cannot dilute the late durability frontier");
            require(frontier.get(CapabilityAxis.MINING_SPEED) == 8, "Actual capable tools establish useful mining speed");
            require(frontier.get(CapabilityAxis.SUSTAINED_DAMAGE) == 11.2 && frontier.get(CapabilityAxis.ATTACK_RATE) == 1.6,
                    "Cadence belongs to the strongest sustained weapon, never an unrelated fast hoe");
        }
        refs.add(equipment("pack:late_helmet", "head", ProgressionBand.LATE, CapabilityAxis.ARMOR, 3, true, true));
        refs.add(equipment("pack:early_chest", "chest", ProgressionBand.ENTRY, CapabilityAxis.ARMOR, 6, true, true));
        require(RobustFrontiers.build(refs).get(ProgressionBand.LATE).get(CapabilityAxis.ARMOR) == 9,
                "New armor slots combine with still-usable earlier pieces");
    }

    private static void policyAdjustedWeaponCadence() {
        List<EquipmentReference> refs = new ArrayList<>();
        for (double[] measurement : List.of(new double[]{10, 1}, new double[]{11, 1}, new double[]{1000, 10}))
            refs.add(new EquipmentReference("pack:weapon_" + (int) measurement[0], "mainhand_melee", ProgressionBand.MID,
                    Map.of(CapabilityAxis.SUSTAINED_DAMAGE, measurement[0], CapabilityAxis.ATTACK_RATE, measurement[1]),
                    List.of(), true, true, .9, "measured damage and cadence"));
        var capped = RobustFrontiers.build(refs, "WINSORIZE");
        require(capped.get(ProgressionBand.MID).get(CapabilityAxis.SUSTAINED_DAMAGE) == 84
                        && capped.get(ProgressionBand.MID).get(CapabilityAxis.ATTACK_RATE) == 10,
                "Winsorized damage retains the winning weapon's cadence, avoiding a synthetic 84-damage slow hit");
        require(capped.get(ProgressionBand.APEX).get(CapabilityAxis.ATTACK_RATE) == 10,
                "The policy-adjusted weapon and its cadence stay paired in later bands");
        var excluded = RobustFrontiers.build(refs, "EXCLUDE_UNSUPPORTED").get(ProgressionBand.MID);
        require(excluded.get(CapabilityAxis.SUSTAINED_DAMAGE) == 11 && excluded.get(CapabilityAxis.ATTACK_RATE) == 1,
                "Exclusion removes the outlier from both sustained damage and cadence selection");
        var included = RobustFrontiers.build(refs, "INCLUDE_ATTAINABLE").get(ProgressionBand.MID);
        require(included.get(CapabilityAxis.SUSTAINED_DAMAGE) == 1000 && included.get(CapabilityAxis.ATTACK_RATE) == 10,
                "Include-attainable keeps the complete original weapon measurement");
        Collections.reverse(refs);
        require(capped.equals(RobustFrontiers.build(refs, "WINSORIZE")), "Policy-adjusted cadence is independent of registry order");
    }

    private static void evidenceResolution() {
        EvidenceFact generic = claim("generic", .99, 0, 1);
        EvidenceFact specialized = claim("specialized", .6, 10, 2);
        EvidenceFact override = claim("human", 1, 1000, 3);
        EvidenceSink a = new EvidenceSink(), b = new EvidenceSink();
        List.of(generic, specialized, override).forEach(a::add); List.of(override, generic, specialized).forEach(b::add);
        require(a.number(EvidenceFact.Subject.ITEM, "pack:item", "value", 0) == 3, "Higher-priority factual override must win");
        require(a.facts().equals(b.facts()), "Fact serialization must be deterministic independent of provider order");
        EvidenceSink priorities = new EvidenceSink(Map.of("specialized", 2000));
        List.of(generic, specialized, override).forEach(priorities::add);
        require(priorities.number(EvidenceFact.Subject.ITEM, "pack:item", "value", 0) == 2, "Explicit provider priority correction must be honored");
        boolean rejected = false;
        try { EvidenceFact.Value.number(Double.NaN); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Nonfinite evidence must not enter deterministic JSON");
    }
    private static void distinctCapabilities() {
        EquipmentReference wings = equipment("pack:glider", "chest", ProgressionBand.LATE, CapabilityAxis.GLIDING, 1, true, true);
        Map<CapabilityAxis, Double> axes = RobustFrontiers.build(List.of(wings)).get(ProgressionBand.LATE);
        require(axes.get(CapabilityAxis.GLIDING) == 1 && !axes.containsKey(CapabilityAxis.SUSTAINED_DAMAGE),
                "Transformative movement must remain a separate capability axis");
    }
    private static EquipmentReference equipment(String id, String slot, ProgressionBand stage, CapabilityAxis axis, double value,
                                                  boolean reachable, boolean included) {
        return new EquipmentReference(id, slot, stage, Map.of(axis, value), List.of(), reachable, included, .9, "synthetic observed reference");
    }
    private static EvidenceFact claim(String provider, double confidence, int priority, double value) {
        return new EvidenceFact(EvidenceFact.Subject.ITEM, "pack:item", "value", EvidenceFact.Value.number(value), provider,
                EvidenceFact.Origin.OBSERVED, confidence, priority, ProgressionBand.EARLY, List.of(), "synthetic independent evidence");
    }
    private static void require(boolean passed, String message) { checks++; if (!passed) throw new AssertionError(message); }
}
