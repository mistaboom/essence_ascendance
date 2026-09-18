package com.mistaboom.essence_ascendance.movement;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudHandler;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Uses actual committed evaluation, catalog, generated semantics and localization. Not a live-world test. */
public final class TraversalBatchContractTest {
    private static int checks;
    private static final List<ResourceLocation> IDS = List.of(SkillIds.TERRAIN_FREEDOM, SkillIds.AQUATIC_BODY,
            SkillIds.WATER_WALKING, SkillIds.LAVABORN);
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init(); MilestoneProviders.init(); Milestones.init(); Skills.init();
        check(TraversalCapabilities.profiles().stream().map(TraversalCapabilities.Profile::skill).toList().equals(IDS), "Batch is exactly the reviewed terrain/fluid branch");
        check(SkillRegistry.size() == 90, "Catalog and other branches remain intact");
        var defaults = SkillEffectBalanceSettings.defaults();
        try (var input = Objects.requireNonNull(TraversalBatchContractTest.class.getResourceAsStream("/assets/essence_ascendance/lang/en_us.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(reader).getAsJsonObject();
            for (var id : IDS) {
                var skill = SkillRegistry.require(id);
                check(SkillEffectRegistry.isImplemented(id), "Runtime implementation registered");
                check(skill.essenceId().equals(EssenceTypes.MOBILITY.id()), "Mobility category preserved");
                check(skill.maximumRank() == 1 && skill.rankPolicy().projectionRanks() == 5, "One purchase; provisional projections only");
                check(!SkillRankEffectScaling.supports(id), "No invented numeric rank consumer");
                check(SkillBalanceSemantics.require(id).contributions().stream().allMatch(c -> c.form() == SkillBalanceSemantics.Form.CAPABILITY), "Restorative capabilities cannot become unconditional magnitudes");
                for (double scale : new double[]{.1, 1, 4}) check(SkillRankEffectScaling.generatedPressureFactor(defaults, id, scale) == 1, "Offense generation never amplifies binary access");
                check(!SkillTooltipRegistry.lines(id).isEmpty(), "Resolved tooltip is present");
                for (var line : SkillTooltipRegistry.lines(id)) check(language.has(line.key()) && line.values().isEmpty(), "Localized passive semantics, no fabricated tuning values");
            }
        }
        check(SkillBalanceSemantics.require(SkillIds.AQUATIC_BODY).contributions().stream()
                .noneMatch(c -> c.axis() == com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.GROUND_SPEED),
                "Aquatic Body does not reserve or project a swimming-speed contribution");
        check(SkillEffectRegistry.handlers().stream().filter(h -> IDS.contains(h.id())).noneMatch(h -> h instanceof SkillEffectHudHandler), "No passive permanent HUD cards");
        var all = new LinkedHashMap<ResourceLocation, Integer>(); IDS.forEach(id -> all.put(id, 1));
        var full = evaluate(all);
        check(IDS.stream().allMatch(id -> full.get(id).effective()), "Committed full branch becomes effective");
        check(IDS.stream().noneMatch(id -> evaluate(Map.of()).get(id).effective()), "Unowned abilities are ineffective");
        var noRoot = new LinkedHashMap<>(all); noRoot.remove(SkillIds.TERRAIN_FREEDOM);
        check(IDS.stream().noneMatch(id -> evaluate(noRoot).get(id).effective()), "Losing Terrain Freedom disables all descendants");
        var noAqua = new LinkedHashMap<>(all); noAqua.remove(SkillIds.AQUATIC_BODY);
        var withoutAqua = evaluate(noAqua);
        check(withoutAqua.get(SkillIds.TERRAIN_FREEDOM).effective() && !withoutAqua.get(SkillIds.WATER_WALKING).effective()
                && !withoutAqua.get(SkillIds.LAVABORN).effective(), "Both aquatic descendants require the active parent");
        com.mistaboom.essence_ascendance.client.SingleSidedFluidVertexConsumerTest.main(args);
        System.out.println("TraversalBatchContractTest: " + checks + " checks passed");
    }
    private static Map<ResourceLocation, SkillEvaluationResult> evaluate(Map<ResourceLocation, Integer> ranks) {
        return SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(), ranks,
                Map.of(), Set.of(), Set.of(), Map.of()));
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
