package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectMath;

/** Pure boundary/attribute invariants; no game bootstrap, world, network or external test framework. */
public final class TraversalRulesTest {
    private static int checks;
    public static void main(String[] args) {
        check(TraversalRules.withoutPenalty(0.4F) == 1, "Terrain drag returns native identity");
        check(TraversalRules.withoutPenalty(0) == 1, "Complete terrain suppression is restored");
        check(TraversalRules.withoutPenalty(1) == 1, "Identity is stable");
        check(TraversalRules.withoutPenalty(1.7F) == 1.7F, "Stronger external factors remain");
        check(Float.isNaN(TraversalRules.withoutPenalty(Float.NaN)), "Invalid external value is not manufactured into power");
        check(TraversalRules.withoutPenalty(-1) == -1, "Invalid negative input is untouched");
        for (int mask = 0; mask < 16; mask++) {
            boolean skill = bit(mask, 0), mode = bit(mask, 1), sprint = bit(mask, 2), sneak = bit(mask, 3);
            check(TraversalRules.surfaceEnabled(skill, mode, sprint, sneak) == (mask == 7),
                    "Surface requires committed ability, normal mode and sprint; Sneak always releases");
        }
        check(TraversalRules.exposedSurface(8.0 / 9, true), "Native source height accepted");
        check(TraversalRules.exposedSurface(2.0 / 9, true), "Native flowing height accepted");
        check(!TraversalRules.exposedSurface(8.0 / 9, false), "No internal stacked-fluid platform");
        for (double height : new double[]{0, -1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
            check(!TraversalRules.exposedSurface(height, true), "Invalid heights fail closed");
        check(TraversalRules.canStepOntoSurface(20, 20.5, .6, true, false), "Native step permits a rising neighboring surface");
        check(!TraversalRules.canStepOntoSurface(20, 20.7, .6, true, false), "No bonus beyond actual step height");
        check(!TraversalRules.canStepOntoSurface(20, 20.5, .6, false, false), "No airborne step/lift");
        check(!TraversalRules.canStepOntoSurface(20, 20.5, .6, true, true), "No immersed lift even while grounded");
        check(!TraversalRules.canStepOntoSurface(20, 20, .6, true, false), "Native isAbove handles equal surfaces, not stepping");
        check(!TraversalRules.canStepOntoSurface(20, 20.5, Double.NaN, true, false), "Invalid step fails closed");
        // Exhaust the independent damage capabilities/source gates, including simultaneous skills.
        for (int mask = 0; mask < 128; mask++) {
            boolean terrain = bit(mask, 0), lava = bit(mask, 1), immersed = bit(mask, 2),
                    terrainSource = bit(mask, 3), lavaSource = bit(mask, 4), attacker = bit(mask, 5), unavoidable = bit(mask, 6);
            boolean protectedHit = TraversalRules.protectsContact(terrain, lava, immersed, terrainSource, lavaSource, attacker, unavoidable);
            boolean eligible = !attacker && !unavoidable && ((terrain && terrainSource) || (lava && immersed && lavaSource));
            check(protectedHit == eligible, "Only eligible source-less environmental contact is suppressed");
        }
        check(TraversalRules.protectsContact(false, true, true, false, true, false, false), "Lava contact protected inside");
        check(!TraversalRules.protectsContact(false, true, false, false, true, false, false), "Same fire source resumes immediately outside");
        near(SkillEffectMath.attributeFloorAddition(.2, 1, 1), .8, "Restore only missing native efficiency");
        near(SkillEffectMath.attributeFloorAddition(.6, 2, 1), 0, "Preserve stronger multiplied efficiency");
        near(SkillEffectMath.attributeFloorAddition(.2, 2, 1), .3, "Floor accounts for multiplicative equipment");
        near(SkillEffectMath.attributeFloorAddition(.2, 1, 0), 0, "Deactivation removes the owned floor");
        System.out.println("TraversalRulesTest: " + checks + " checks passed");
    }
    private static boolean bit(int value, int bit) { return (value & (1 << bit)) != 0; }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1.0e-9, message); }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
