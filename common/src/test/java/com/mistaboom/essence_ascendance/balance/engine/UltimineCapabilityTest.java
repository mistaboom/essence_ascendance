package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.capability.UltimineCapabilityProvider;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import java.util.*;

public final class UltimineCapabilityTest {
    private static int checks;
    public static void main(String[] args) {
        var config = new UltimineCapabilityProvider.Configuration(64, 20, 0, 0, false, false, 3);
        check(config.supported() && Math.abs(config.exhaustionPerTarget() - .105) < 1e-10, "Exhaustion multiplier confused with food points");
        for (double xp : List.of(-1.0, 1.0, Double.NaN))
            check(!new UltimineCapabilityProvider.Configuration(64, 20, xp, 0, false, false, 3).supported(), "Unproven XP operation admitted");
        check(!new UltimineCapabilityProvider.Configuration(1, 20, 0, 0, false, false, 3).supported(), "One-block operation is not vein mining");
        check(!new UltimineCapabilityProvider.Configuration(64, Double.NaN, 0, 0, false, false, 3).supported(), "Unknown exhaustion became free");
        for (String key : List.of("*", "*.*", "ftbultimine", "ftbultimine.*", "ftbultimine.*.*", "ftbultimine.max_blocks"))
            check(UltimineCapabilityProvider.relevantRankPermission(key), "Rank override was ignored: " + key);
        check(!UltimineCapabilityProvider.relevantRankPermission("ftbranks.name_format"), "Unrelated rank permission blocked Ultimine");
        var definitions = new net.minecraft.nbt.CompoundTag();
        var member = new net.minecraft.nbt.CompoundTag(); member.putString("ftbranks.name_format", "{name}"); definitions.put("member", member);
        check(UltimineCapabilityProvider.rankDefinitionProblem(definitions) == null, "Cosmetic rank definition blocked action access");
        member.putBoolean("ftbultimine.*", false);
        check(UltimineCapabilityProvider.rankDefinitionProblem(definitions) != null, "Pre-start wildcard override ignored");
        member.remove("ftbultimine.*"); definitions.putInt("malformed", 1);
        check(UltimineCapabilityProvider.rankDefinitionProblem(definitions) != null, "Malformed rank source treated as no override");
        var freeAccess = UltimineCapabilityProvider.bareHandActionAccess(config);
        check(freeAccess.placement().reachable() && freeAccess.placement().stage() == ProgressionBand.ENTRY,
                "Enabled native action was incorrectly gated on renewable target item supply");
        check(freeAccess.placement().acquisition().stream().noneMatch(AcquisitionSource::renewable), "Action access invented renewable target supply");
        var gated = new UltimineCapabilityProvider.Configuration(64, 20, 0, 0, true, false, 3);
        try { UltimineCapabilityProvider.bareHandActionAccess(gated); throw new AssertionError("Tool gate bypassed"); }
        catch (IllegalArgumentException expected) { checks++; }
        String conduit = "com.enderio.enderio.compat.ftb_ultimine.ConduitBlockBreakHandler";
        check(UltimineCapabilityProvider.passesNativeBlock("BlockBreakingRegistry", conduit, "8.2.12-beta"), "Audited non-conduit pass-through excluded");
        check(!UltimineCapabilityProvider.passesNativeBlock("BlockBreakingRegistry", conduit, "future"), "Unaudited handler version admitted");
        check(!UltimineCapabilityProvider.passesNativeBlock("RestrictionHandlerRegistry", conduit, "8.2.12-beta"), "Unrelated restriction ignored");
        String chisel = "com.leclowndu93150.chisel.compat.ftbultimine.ChiselBlockSelectionHandler";
        check(UltimineCapabilityProvider.preservesSameBlockSelection("BlockSelectionRegistry", chisel, "1.4.1"), "Audited same-block selection excluded");
        for (String version : Arrays.asList(null, "", "1.4.2", "future"))
            check(!UltimineCapabilityProvider.preservesSameBlockSelection("BlockSelectionRegistry", chisel, version), "Unaudited Chisel version admitted");
        check(!UltimineCapabilityProvider.preservesSameBlockSelection("RestrictionHandlerRegistry", chisel, "1.4.1"), "Chisel restriction assumed safe");
        check(!UltimineCapabilityProvider.preservesSameBlockSelection("BlockSelectionRegistry", chisel + "$Custom", "1.4.1"), "Custom Chisel handler assumed safe");
        var proof = new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.MID, true, .8, List.of()),
                List.of("fixture:renewable_log", "fixture:tool"), List.of());
        var fact = UltimineCapabilityProvider.operation(config, proof, 56);
        check(fact.source().stage() == ProgressionBand.MID, "Provider hardcoded early access despite later setup");
        check(fact.measurements().getFirst().magnitude() == 56, "Tool-wear capacity was replaced by configured maximum");
        check(fact.measurements().getFirst().operation().unitsPerSecond() == null, "Batch size fabricated throughput");
        check(fact.measurements().getFirst().operation().exhaustionPerTarget().equals(config.exhaustionPerTarget()), "Cost lost from evidence");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(fact), CapabilityEvidence.Functional.class).equals(fact), "Cost evidence lost on profile roundtrip");
        var legacy = BalanceDocument.GSON.toJsonTree(CapabilityEvidence.Operation.manual()).getAsJsonObject(); legacy.remove("exhaustionPerTarget");
        check(BalanceDocument.GSON.fromJson(legacy, CapabilityEvidence.Operation.class).exhaustionPerTarget() == null, "Older profiles invented a zero cost");
        var unproven = new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY, false, 0, List.of()), List.of(), List.of("missing setup"));
        try { UltimineCapabilityProvider.operation(config, unproven, 64); throw new AssertionError("Unproven operation admitted"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("UltimineCapabilityTest: " + checks + " cost, access, rank, configuration and saved-profile checks PASS");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
