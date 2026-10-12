package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.*;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import java.util.*;

/** Published loadout projection: bounded full-shoal/maximum-owned-rank scenario, never measured uptime. */
public final class FishingBalanceDiagnostics {
    private FishingBalanceDiagnostics() { }
    public static JsonObject project(RuntimeBalanceDefinition runtime) {
        JsonObject result = new JsonObject();
        result.addProperty("policy", "Native water fishing only. Full-shoal upper-bound scenario; Lure subtracts native initial time before this multiplier. No invented loot rates or simultaneous fishing/grappling rewards. Binary Surface Tension changes rod surface access, not skill ranks or equipment speed.");
        result.add("rod", runtime.toJson().get("fishingRod"));
        result.addProperty("attachmentControl", "The forceRampTicks opening window front-loads pull force up to reel speed and weights camera-relative sideways/vertical steering by steeringFraction, fading into a straight inward reel. Near-target easing, resistance, momentum preservation and the sustained reel-speed limit apply throughout. Native water fishing does not enter this path.");
        var track = runtime.config().balanceProfile().bonusTrack(EssenceStats.FISHING_SPEED.id());
        result.add("fishingSpeed", BonusTrackGenerator.toJson(Map.of(track.statId(), track)).get(track.statId().toString()));
        JsonArray tiers = new JsonArray();
        for (var tier : EquipmentTier.values()) {
            Map<net.minecraft.resources.ResourceLocation, Integer> ranks = new HashMap<>();
            for (var id : List.of(SkillIds.FISHING_INSTINCT, SkillIds.FISHERS_CALL, SkillIds.SURFACE_TENSION)) {
                var skill = SkillRegistry.require(id); var curve = runtime.skillCurves().get(id.toString());
                int legal = 0;
                for (int rank = 1; rank <= curve.maximumRank(); rank++) {
                    var gate = com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.get(SkillProgressionGraph.gated(skill, curve.requiredTierId(), rank)).orElseThrow();
                    if (tier.ascendanceTier().order() >= gate.order()) legal = rank;
                }
                if (legal > 0) ranks.put(id, legal);
            }
            var effects = SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(), ranks, runtime.skillCurves()).gathering();
            double speed = 1 + track.maximumEffect() * track.checkpoint(tier.ascendanceTier().id()).effectFraction() / 100;
            if (ranks.containsKey(SkillIds.FISHING_INSTINCT)) speed *= effects.fishingInstinct().biteSpeedMultiplier();
            if (ranks.containsKey(SkillIds.FISHERS_CALL)) speed *= effects.fishersCall().maximumBiteSpeedMultiplier();
            JsonObject row = new JsonObject(); row.addProperty("equipmentTier", tier.serializedName());
            row.add("effectiveRanksAssumingPrerequisites", new Gson().toJsonTree(ranks));
            row.addProperty("countdownMultiplierAtFullShoal", speed);
            row.addProperty("rodSurfaceSupportAssumingSkillEffective", ranks.containsKey(SkillIds.SURFACE_TENSION));
            row.addProperty("stationaryAnchorPoweredSpeed", runtime.config().fishingRodBalance().tier(tier).reelSpeed());
            tiers.add(row);
        }
        result.add("tierLoadouts", tiers); return result;
    }
}
