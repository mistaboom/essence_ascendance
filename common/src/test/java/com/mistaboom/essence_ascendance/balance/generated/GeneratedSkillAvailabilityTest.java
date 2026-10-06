package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.TreeMap;

/** Saved candidate projections must not use the previously installed world's availability. */
public final class GeneratedSkillAvailabilityTest {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var original = RuntimeBalanceDefinition.bootstrap();
        SkillBalanceRuntime.install(original.skillCurves());
        try {
            var curves = new TreeMap<>(original.skillCurves());
            var growth = curves.get(SkillIds.VERDANT_STRIDE.toString());
            curves.put(SkillIds.VERDANT_STRIDE.toString(), new SkillBalanceRuntime.ResolvedSkill(growth.maximumRank(), growth.ranks(), AscendanceTiers.DORMANT.id()));
            var candidate = new RuntimeBalanceDefinition(original.config(), original.crucible(), original.pylons(), curves, original.composition(), original.attunement());
            check(!includesGrowth(GeneratedBalanceService.skillDiagnostics(original)), "Catalog growth appeared in Dormant projection");
            check(includesGrowth(GeneratedBalanceService.skillDiagnostics(candidate)), "Candidate's earlier growth missing from saved Dormant projection");
            check(SkillBalanceRuntime.snapshot().equals(original.skillCurves()), "Diagnostic generation installed candidate curves");
            check(SkillRegistry.require(SkillIds.VERDANT_STRIDE).requiredTierId().equals(growth.requiredTierId()), "Diagnostic scope did not restore previous access");
            System.out.println("GeneratedSkillAvailabilityTest: 4 candidate projection isolation checks PASS");
        } finally { SkillBalanceRuntime.clear(); }
    }
    private static boolean includesGrowth(JsonObject diagnostics) {
        for (var row : diagnostics.getAsJsonObject("reachableProjections").getAsJsonObject("dormant_current_effects").getAsJsonArray("scenarios"))
            if (row.getAsJsonObject().getAsJsonObject("activeRanks").has(SkillIds.VERDANT_STRIDE.toString())) return true;
        return false;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
