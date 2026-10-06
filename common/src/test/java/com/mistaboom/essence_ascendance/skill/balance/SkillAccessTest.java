package com.mistaboom.essence_ascendance.skill.balance;

import com.google.gson.GsonBuilder;
import com.mistaboom.essence_ascendance.balance.config.ResourceLocationJsonAdapter;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Real-catalog checks for saved availability and isolated candidate generation. */
public final class SkillAccessTest {
    private static int assertions;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init(); AscendanceTiers.init();
        com.mistaboom.essence_ascendance.stat.EssenceStats.init();
        com.mistaboom.essence_ascendance.progression.MilestoneProviders.init();
        com.mistaboom.essence_ascendance.progression.Milestones.init(); Skills.init();
        var flight = SkillRegistry.require(SkillIds.FATIGUE_FLIGHT);
        var costs = new TreeMap<ResourceLocation, Long>();
        AscendanceTierRegistry.powerTiers().forEach(t -> costs.put(t.id(), 100L * t.order()));
        var original = SkillBalanceGenerator.generate(costs, 1);
        SkillBalanceRuntime.install(original);
        check(flight.requiredTierId().equals(AscendanceTiers.RESONANT.id()), "Catalog placement is the initial fallback");

        var earlier = SkillBalanceRuntime.withRequiredTiers(Map.of(flight.id(), AscendanceTiers.DORMANT.id()), () -> {
            check(flight.requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Candidate placement reaches the catalog accessor");
            check(flight.catalogRequiredTierId().equals(AscendanceTiers.RESONANT.id()), "Catalog placement stays immutable");
            var generated = SkillBalanceGenerator.generate(costs, 1);
            check(generated.get(flight.id().toString()).ranks().getFirst().cost()
                    < original.get(flight.id().toString()).ranks().getFirst().cost(), "Prices use the same earlier placement");
            return generated;
        });
        check(flight.requiredTierId().equals(AscendanceTiers.RESONANT.id()), "Candidate generation does not install availability");
        SkillBalanceRuntime.install(earlier);
        check(flight.requiredTierId(1).equals(AscendanceTiers.DORMANT.id()), "Installed placement governs actual rank access");
        SkillBalanceRuntime.withRequiredTiers(Map.of(), () -> {
            check(flight.requiredTierId().equals(AscendanceTiers.RESONANT.id()), "New generation ignores the previous installed placement");
            check(SkillBalanceRuntime.snapshot().isEmpty(), "Fresh generation cannot inherit installed prices or rank limits");
            return null;
        });
        check(flight.requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Generation restores the installed profile");

        var gson = new GsonBuilder().registerTypeAdapter(ResourceLocation.class, new ResourceLocationJsonAdapter()).create();
        var wire = gson.toJson(earlier.get(flight.id().toString()));
        var loaded = gson.fromJson(wire, SkillBalanceRuntime.ResolvedSkill.class);
        check(loaded.requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Saved placement survives a typed JSON round trip");
        var legacyJson = gson.toJsonTree(original.get(flight.id().toString())).getAsJsonObject();
        legacyJson.remove("requiredTierId");
        var legacy = gson.fromJson(legacyJson, SkillBalanceRuntime.ResolvedSkill.class);
        check(legacy.requiredTierId() == null, "Old profiles need no regeneration to decode");
        var legacyCurves = new TreeMap<>(original);
        legacyCurves.put(flight.id().toString(), legacy);
        SkillBalanceRuntime.withCurves(legacyCurves, () -> {
            check(flight.requiredTierId().equals(AscendanceTiers.RESONANT.id()), "Missing saved placement falls back to catalog, not installed data");
            check(flight.cost(null) == legacy.ranks().getFirst().cost(), "Read-only candidate prices come from candidate curves");
            return null;
        });
        check(flight.requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Loaded validation scope restores installed data");
        SkillBalanceRuntime.withRequiredTiers(Map.of(), () -> {
            var otherThread = java.util.concurrent.CompletableFuture.supplyAsync(flight::requiredTierId).join();
            check(otherThread.equals(AscendanceTiers.DORMANT.id()), "Candidate placement never leaks into another thread");
            SkillBalanceRuntime.withCurves(legacyCurves, () -> {
                check(flight.requiredTierId().equals(AscendanceTiers.RESONANT.id()), "Nested saved validation uses its own profile");
                return null;
            });
            check(flight.requiredTierId().equals(AscendanceTiers.RESONANT.id()), "Nested validation restores outer generation placement");
            return null;
        });
        check(flight.requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Nested scopes restore the installed profile");

        rejects(() -> SkillBalanceRuntime.withRequiredTiers(Map.of(flight.id(), AscendanceTiers.LATENT.id()), () -> null),
                "Generated availability cannot expose a non-power tier");
        SkillBalanceRuntime.withRequiredTiers(Map.of(flight.id(), AscendanceTiers.ASCENDANT.id()), () -> {
            check(flight.requiredTierId().equals(AscendanceTiers.ASCENDANT.id()), "Supported later placement is allowed"); return null;
        });
        rejects(() -> SkillBalanceRuntime.withRequiredTiers(Map.of(flight.id(), ResourceLocation.parse("fixture:missing_tier")), () -> null),
                "Unknown saved placement cannot be silently treated as a powered tier");
        rejects(() -> SkillBalanceRuntime.withRequiredTiers(Map.of(SkillIds.VECTOR_JUMP, AscendanceTiers.DORMANT.id()), () -> null),
                "A child cannot precede its required parent");
        var invalid = new TreeMap<>(earlier);
        var vector = invalid.get(SkillIds.VECTOR_JUMP.toString());
        invalid.put(SkillIds.VECTOR_JUMP.toString(), new SkillBalanceRuntime.ResolvedSkill(vector.maximumRank(), vector.ranks(), AscendanceTiers.DORMANT.id()));
        rejects(() -> SkillBalanceRuntime.validate(invalid), "Saved candidate validation checks prerequisite closure");
        check(SkillBalanceRuntime.snapshot().equals(earlier), "Rejected candidates never alter installed curves");
        rejects(() -> SkillBalanceRuntime.withRequiredTiers(Map.of(flight.id(), AscendanceTiers.DORMANT.id()), () -> {
            throw new IllegalArgumentException("Scoped failure");
        }), "Scoped exceptions propagate");
        check(flight.requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Exceptional scope exits restore availability");

        var ranked = new SkillDefinition(flight.id(), flight.essenceId(), flight.nameTranslationKey(), flight.descriptionTranslationKey(),
                flight.catalogRequiredTierId(), flight.costBand(), flight.prerequisites(), flight.requirements(), flight.choiceGroup(),
                flight.replacementTarget(), flight.activationPolicy(), flight.displayOrder(), flight.layoutHint(),
                new SkillRankPolicy(2, 2, SkillRankCurve.developed(), SkillRankPolicy.RefundRule.NONE,
                        Map.of(2, new SkillRankPolicy.Gates(AscendanceTiers.ASCENDANT.id(), Map.of(), List.of()))));
        check(ranked.requiredTierId(1).equals(AscendanceTiers.DORMANT.id()), "Rank one uses saved base placement");
        check(ranked.requiredTierId(2).equals(AscendanceTiers.ASCENDANT.id()), "Explicit later-rank gates remain authoritative");
        SkillBalanceRuntime.clear();
        System.out.println("SkillAccessTest: " + assertions + " assertions passed");
    }
    private static void check(boolean value, String reason) { assertions++; if (!value) throw new AssertionError(reason); }
    private static void rejects(Runnable operation, String reason) {
        assertions++;
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(reason);
    }
}
