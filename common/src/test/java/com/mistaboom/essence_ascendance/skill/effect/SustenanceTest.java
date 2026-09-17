package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;

/** Deterministic clock/eligibility invariants complement real transformed native food fixtures. */
public final class SustenanceTest {
    private static int checks;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for (int duration = 1; duration <= 100; duration++) {
            int fast = SustenanceMath.useDuration(duration, .25);
            check(fast >= 1 && fast <= duration, "duration never zero or longer than native");
            check(fast == (int) Math.ceil(duration * .25), "deterministic duration rounding");
        }
        check(SustenanceMath.useDuration(32, Double.NaN) == 32, "invalid multiplier never truncates item use");
        check(SustenanceMath.useDuration(0, .25) == 0, "non-usable item remains non-usable");
        check(SustenanceMath.full(20,20) && !SustenanceMath.full(19,20) && !SustenanceMath.full(20,19.99f)
                && !SustenanceMath.full(20,Float.NaN), "full means native food and saturation capacity");
        check(SustenanceMath.automaticMeal(1,0,false), "real health drop and empty meal");
        check(!SustenanceMath.automaticMeal(0,0,false) && !SustenanceMath.automaticMeal(Double.NaN,0,false)
                && !SustenanceMath.automaticMeal(1,.01f,false) && !SustenanceMath.automaticMeal(1,0,true),
                "zero/invalid damage, remaining saturation and active item use are ineligible");
        check(SustenanceMath.automaticMeal(1, 6, 4, false)
                        && !SustenanceMath.automaticMeal(1, 20, 0, false)
                        && !SustenanceMath.automaticMeal(1, -1, 0, false),
                "Feast uses an actually non-full food bar, even when a saturation reserve remains");
        check(SustenanceMath.automaticMealOpportunity(true, 10, 8, false)
                        && SustenanceMath.automaticMealOpportunity(true, 10, 7, false)
                        && SustenanceMath.automaticMealOpportunity(true, 0, 8, false)
                        && !SustenanceMath.automaticMealOpportunity(true, 18, 8, false)
                        && SustenanceMath.automaticMealOpportunity(false, 15, 8, false)
                        && !SustenanceMath.automaticMealOpportunity(false, 16, 8, false)
                        && !SustenanceMath.automaticMealOpportunity(false, 20, 8, false),
                "Feast accepts successive useful meals while hurt, or food with strictly less than half wasted nutrition when healthy");
        var clock = new SustenanceMath.RecoveryClock();
        for (int tick = 0; tick < 159; tick++) check(!clock.tick(tick,false,160), "recovery waits full quiet interval");
        check(clock.tick(159,false,160), "recovery at exact interval");
        check(!clock.tick(159,false,160), "duplicate tick cannot restore twice");
        check(!clock.tick(160,true,160) && clock.progress() == 0, "combat resets partial progress");
        for (int tick = 161; tick < 320; tick++) check(!clock.tick(tick,false,160), "new quiet interval after combat");
        check(clock.tick(320,false,160), "first post-combat recovery exact");
        clock.tick(321,false,160); clock.tick(500,false,160);
        check(clock.progress() == 1, "offline skipped ticks never catch up");
        clock.clear(); check(clock.progress() == 0, "skill removal/death resets partial hunger credit");
        var combat = new RecentHostileCombat();
        var overworld = ResourceLocation.parse("minecraft:overworld");
        var nether = ResourceLocation.parse("minecraft:the_nether");
        combat.mark(100,overworld);
        check(combat.remaining(100,overworld,200) == 200 && combat.remaining(299,overworld,200) == 1
                && combat.remaining(300,overworld,200) == 0, "combat lock exact expiry");
        combat.mark(100,overworld); check(combat.remaining(101,nether,200) == 0, "dimension clears combat");
        combat.mark(100,overworld); check(combat.remaining(99,overworld,200) == 0, "reversed time clears combat");
        combat.mark(100,overworld); combat.clear(); check(combat.remaining(100,overworld,200) == 0, "lifecycle clears combat");
        var feast = new VitalitySustenanceEffects.FeastState();
        check(feast.claim(100) && !feast.claim(100) && feast.claim(101), "one automatic attempt per owner tick");
        feast.clear(); check(!feast.active(), "lifecycle discards automatic slot ownership");
        System.out.println("Sustenance invariants passed: " + checks);
    }
    private static void check(boolean okay, String message) { checks++; if (!okay) throw new AssertionError(message); }
}
