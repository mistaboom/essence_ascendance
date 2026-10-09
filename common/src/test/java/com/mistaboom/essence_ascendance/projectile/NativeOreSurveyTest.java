package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.balance.generated.GeneratedBalanceService;
import com.mistaboom.essence_ascendance.balance.generated.GenerationRecipeReadiness;
import com.mistaboom.essence_ascendance.gathering.NaturalOreDropService;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/** Saved-world ore lookup after the generation cache has been released, on both loaders. */
public final class NativeOreSurveyTest {
    private static int checks;

    public static void run() throws ReflectiveOperationException {
        var f = new ProjectileNativeInterceptionTest.Fixture();
        NativeGuardOutcomeTest.registry(f);
        var active = GeneratedBalanceService.class.getDeclaredField("active");
        active.setAccessible(true);
        Object previous = active.get(null);
        try {
            ProceduralValuationEngine.clear();
            var clear = GenerationRecipeReadiness.class.getDeclaredMethod("clear");
            clear.setAccessible(true);
            clear.invoke(null);
            assertGenerationUnprepared(f);

            active.set(null, published(40));
            var ores = NaturalOreDropService.surveyOreValues(f.player);
            check(ores.get(Blocks.DIAMOND_ORE) == 40L, "saved economic value drives ore rarity");
            check(ores.get(Blocks.IRON_ORE) == 5L, "relative ore weighting survives a saved-profile load");
            check(ores.get(Blocks.COAL_ORE) == 1L, "unmapped tagged/vanilla ores retain the minimum weight");
            check(ores.get(Blocks.NETHER_QUARTZ_ORE) == 1L, "cross-dimension detection remains available");

            var guest = f.player(new Vec3(0, 0, 3), "lan_guest");
            check(NaturalOreDropService.surveyOreValues(guest).equals(ores),
                    "a second connected player reads the same published values without generation");
            active.set(null, published(80));
            check(NaturalOreDropService.surveyOreValues(guest).get(Blocks.DIAMOND_ORE) == 80L,
                    "publishing a replacement profile updates ore weighting without a stale cache");
            assertGenerationUnprepared(f);
        } finally {
            active.set(null, previous);
            f.close();
        }
        System.out.println("Native saved-profile ore survey passed: " + checks + " (two players; generation unavailable; no world)");
    }

    private static GeneratedBalanceService.Active published(double diamond) {
        var economy = new EconomyProfile(Map.of(
                "minecraft:diamond_ore", value(diamond), "minecraft:iron_ore", value(5)),
                List.of(), List.of(), List.of(), 0, EconomyProcessingPolicy.defaults());
        return new GeneratedBalanceService.Active(null, null, economy, null);
    }

    private static EconomyProfile.ResourceValue value(double amount) {
        return new EconomyProfile.ResourceValue(new EconomicValue(amount), new DissolutionYield(0), Map.of(), List.of());
    }

    private static void assertGenerationUnprepared(ProjectileNativeInterceptionTest.Fixture f) {
        try {
            GenerationRecipeReadiness.excludedRecipeNamespaces(f.server);
            throw new AssertionError("Fixture must reproduce the unprepared recipe epoch from the host log");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("requires recipe preparation"), "exact reported readiness failure");
        }
    }

    private static void check(boolean okay, String message) {
        checks++;
        if (!okay) throw new AssertionError(message);
    }
}
