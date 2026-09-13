package com.mistaboom.essence_ascendance.balance.engine;

/** Pure JDK executable checks: numeric composition, defense assumptions and bounded attenuation. */
public final class BuildCompositionTest {
    private static int assertions;

    public static void main(String[] args) {
        attackUnitsAndFeedback();
        armorAndRecovery();
        partialAndCompleteBuilds();
        boundedGuardAndOverrideRecheck();
        independentCombatChannels();
        invalidAndImpossibleInputs();
        System.out.println("BuildCompositionTest: " + assertions + " assertions passed");
    }

    private static void attackUnitsAndFeedback() {
        var equipment = new BuildComposition.Equipment(4, 2, 0, 0, 20, 0);
        var nexus = new BuildComposition.Modifier(2, 1.5, 1.25, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var skills = new BuildComposition.Modifier(0, 1.2, 1.1, .5, 1, 0, 0, 0, 0, 0, 2, .1);
        var result = BuildComposition.compose(equipment, nexus, skills, 10, 5);
        near(result.sustainedDamage(), 29.7, "Damage and attack-rate multipliers compose on actual equipment");
        near(result.burstDamage(), 16.2, "Burst uses one attack, not damage per second");
        near(result.areaDamage(), 29.7, "Area contains additional victim damage without inflating primary DPS");
        near(result.healingPerSecond(), 4.97, "Life steal reflects the final composed outgoing damage");
        near(result.sustainedHealth(), 44.85, "Finite-window recovery is accounted without asserting immortality");
        near(BuildComposition.compose(equipment, nexus.scaled(0), skills.scaled(0), 10, 5).sustainedDamage(),
                8, "Attenuation removes bonus power while preserving ordinary item damage and rate");
    }

    private static void armorAndRecovery() {
        near(BuildComposition.armorDamageFraction(10, 0, 0), 1, "Unarmored damage");
        near(BuildComposition.armorDamageFraction(10, 20, 0), .4, "Armor depends on incoming hit size");
        near(BuildComposition.armorDamageFraction(10, 20, 8), .3, "Toughness mitigates armor penetration by large hits");
        near(BuildComposition.armorDamageFraction(10, 1000, 1000), .2, "Extreme armor cannot bypass vanilla armor-only cap");
        var equipment = new BuildComposition.Equipment(4, 2, 20, 8, 20, 0);
        var nexus = new BuildComposition.Modifier(0, 1, 1, 0, 0, 20, 0, 0, .5, .2, 2, 0);
        var result = BuildComposition.compose(equipment, nexus, BuildComposition.Modifier.none(), 10, 10);
        near(result.effectiveHealth(), 40 / .12, "Health, armor, independent reduction and expected avoidance compose");
        near(result.sustainedHealth(), 60 / .12, "Healing uses the same declared mitigation model");
        check(BuildComposition.armorDamageFraction(100, 20, 0) > BuildComposition.armorDamageFraction(10, 20, 0),
                "A boss-sized hit evaluates different survival from a routine hit");
    }

    private static void partialAndCompleteBuilds() {
        var input = inputs(1.5, 1.5);
        var evaluation = BuildComposition.evaluate(input, generous());
        check(evaluation.scenarios().size() == 7, "All seven participation modes are evaluated explicitly");
        near(metric(evaluation, BuildComposition.Participation.EQUIPMENT_FOCUSED), 8, "Equipment-focused uses Ascendance equipment only");
        near(metric(evaluation, BuildComposition.Participation.BONUS_FOCUSED), 6, "Bonus-only uses the independent external item");
        near(metric(evaluation, BuildComposition.Participation.SKILL_FOCUSED), 6, "Skills-only uses the independent external item");
        near(metric(evaluation, BuildComposition.Participation.FULLY_COMBINED), 18, "Complete build receives actual combined values");
        near(metric(evaluation, BuildComposition.Participation.MIXED), 13.2, "Mixed uses caller-supplied reachable moderate skills");
        near(metric(evaluation, BuildComposition.Participation.BROAD_GENERALIST), 14.4, "Broad build uses caller-supplied moderate Nexus point");
        near(evaluation.scenarios().get(BuildComposition.Participation.CATEGORY_SPECIALIZED).effectiveHealth(), 20,
                "Offense category specialization does not claim defensive investment");
        check(evaluation.assumptions().stream().anyMatch(line -> line.contains("upper bound")), "Survival approximation is explicitly disclosed");
    }

    private static void boundedGuardAndOverrideRecheck() {
        var limits = new BuildComposition.Limits(12, 100, 100, 1000, 1000, 100);
        var input = inputs(1.5, 1.5);
        var guarded = BuildComposition.guard(input, limits);
        check(!guarded.before().safe() && guarded.after().safe(), "Guard resolves a real combined multiplier excess");
        near(guarded.attenuation(), 2 * (Math.sqrt(1.5) - 1), 1e-6, "Bounded solver retains the largest safe bonus allowance");
        check(guarded.attenuation() > 0, "Useful partial power survives composition attenuation");
        check(BuildComposition.guard(input, generous()).attenuation() == 1, "Safe generated values stay unchanged");
        check(BuildComposition.guard(input, limits).equals(guarded), "Deterministic solver produces identical results");
        check(BuildComposition.evaluate(inputs(4, 4), limits).violations().stream()
                .anyMatch(v -> v.participation() == BuildComposition.Participation.FULLY_COMBINED),
                "Changed exact numeric overrides are re-evaluated, not trusted via previous summaries");
        rejected(() -> BuildComposition.evaluate(inputs(4, 4), limits).requireSafe(), "Unsafe final override is rejected");
        near(metric(guarded.after(), BuildComposition.Participation.EQUIPMENT_FOCUSED), 8, "Guard preserves base equipment exactly");
    }

    private static void invalidAndImpossibleInputs() {
        rejected(() -> BuildComposition.guard(inputs(1.5, 1.5), new BuildComposition.Limits(1, 1, 0, 20, 20, 0)),
                "An impossible equipment baseline cannot be fixed by hiding all bonus power");
        rejected(() -> new BuildComposition.Modifier(0, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0),
                "Numeric immunity requires separate capability policy");
        rejected(() -> new BuildComposition.Equipment(Double.NaN, 1, 0, 0, 20, 0), "NaN cannot enter composition");
        rejected(() -> multiplier(2).scaled(-1), "Negative attenuation is invalid");
        rejected(() -> new BuildComposition.Limits(Double.POSITIVE_INFINITY, 1, 1, 1, 1, 1), "Infinite targets cannot silently disable validation");
    }

    private static void independentCombatChannels() {
        var equipment = new BuildComposition.Equipment(8, 1.6, 20, 8, 20, 0);
        var strong = new BuildComposition.Modifier(0, 1.2, 1.1, 0, 0, 40, 0, 0, .3, 0, 8, 0);
        var input = new BuildComposition.Inputs("apex/independent_channels", equipment, equipment,
                strong, strong, BuildComposition.Modifier.none(), BuildComposition.Modifier.none(), 10, 10);
        var limits = new BuildComposition.Limits(30, 20, 100, 130, 175, 2);
        var defense = BuildComposition.guard(input, limits, BuildComposition.Channel.DEFENSE);
        check(defense.attenuation() > 0 && defense.attenuation() < 1, "Excess defense receives its own bounded calibration");
        var defended = input.attenuated(defense.attenuation(), BuildComposition.Channel.DEFENSE);
        near(defended.nexusFull().damageMultiplier(), 1.2, "A survival limit cannot nerf damage");
        near(defended.nexusFull().attackRateMultiplier(), 1.1, "A survival limit cannot nerf attack speed");
        near(defended.nexusFull().healingPerSecond(), 8, "Defense calibration leaves recovery for its independent budget");
        var healing = BuildComposition.guard(defended, limits, BuildComposition.Channel.HEALING);
        var finalInputs = defended.attenuated(healing.attenuation(), BuildComposition.Channel.HEALING);
        check(BuildComposition.evaluate(finalInputs, limits).safe(), "Independent calibrations still enforce all final build limits");
        near(finalInputs.nexusFull().bonusHealth(), defended.nexusFull().bonusHealth(), "Healing limits preserve purchased maximum health");
        near(finalInputs.nexusFull().damageMultiplier(), strong.damageMultiplier(), "Recovery cannot erase unrelated weapon investment");
        near(BuildComposition.guard(finalInputs, limits, BuildComposition.Channel.OFFENSE).attenuation(), 1,
                "Already safe offense receives no penalty from unrelated channels");
    }

    private static BuildComposition.Inputs inputs(double nexusDamage, double skillDamage) {
        return new BuildComposition.Inputs("apex/melee/legal_selection",
                new BuildComposition.Equipment(2, 2, 0, 0, 20, 0),
                new BuildComposition.Equipment(4, 2, 0, 0, 20, 0), multiplier(nexusDamage), multiplier(1.2),
                multiplier(skillDamage), multiplier(1.1), 10, 5);
    }
    private static BuildComposition.Modifier multiplier(double damage) {
        return new BuildComposition.Modifier(0, damage, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
    private static BuildComposition.Limits generous() { return new BuildComposition.Limits(1000, 1000, 1000, 1000, 1000, 1000); }
    private static double metric(BuildComposition.Evaluation evaluation, BuildComposition.Participation participation) {
        return evaluation.scenarios().get(participation).sustainedDamage();
    }
    private static void near(double actual, double expected, String message) { near(actual, expected, 1e-8, message); }
    private static void near(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": expected " + expected + ", got " + actual);
    }
    private static void rejected(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); assertions++;
    }
}
