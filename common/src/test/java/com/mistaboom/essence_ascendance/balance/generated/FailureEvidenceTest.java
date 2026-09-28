package com.mistaboom.essence_ascendance.balance.generated;

import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBuildScenarios;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.BuildComposition.*;

/** Failure captures remain replay inputs, never authority; diagnostics name the rejected channel. */
public final class FailureEvidenceTest {
    private static int checks;
    public static void main(String[] args) {
        var inputs = new BalanceInputs(BalanceSettings.defaults(), new BalanceOverrides(List.of(), Map.of()), "settings", "overrides");
        var evidence = new PackEvidence(Map.of(), List.of(), List.of(), Map.of(), List.of(), List.of("captured warning"), Map.of());
        var economy = new EconomyProfile(Map.of(), List.of(), List.of(), List.of(), 1, EconomyProcessingPolicy.defaults());
        var environment = new PackFingerprint("pack", Map.of("fixture", "1"), "1.21.1", "neoforge", List.of(), "registry", "recipes", "tags");
        var snapshot = SavedEvidenceRegenerator.failureSnapshot(environment, inputs, evidence, economy,
                new IllegalArgumentException("offense constraint"));
        var parsed = BalanceDocument.parse(snapshot.text());
        check(parsed.integrity().equals(snapshot.integrity()), "Failure envelope round trip");
        check(parsed.section("evidence").equals(BalanceDocument.GSON.toJsonTree(evidence)), "Exact evidence retained");
        check(parsed.section("economy").equals(BalanceDocument.GSON.toJsonTree(economy)), "Exact economy retained");
        check(parsed.decodeSection("evidence", PackEvidence.class).equals(evidence), "Direct typed evidence decoding");
        parsed.verifySection("evidence", evidence);
        check(true, "Typed evidence and saved tree agree without serialization allocation");
        check(parsed.sectionHash("evidence").equals(BalanceDocument.hash(parsed.section("evidence"))), "Section digest without tree copy");
        check(parsed.section("settings").equals(BalanceDocument.GSON.toJsonTree(inputs.settings())), "Settings retained");
        check(parsed.section("metadata").get("evidenceDigest").getAsString().equals(BalanceDocument.hash(parsed.section("evidence"))), "Evidence digest retained");
        check(parsed.section("runtime").isEmpty() && parsed.section("skills").isEmpty(), "No fabricated runtime");
        try { GeneratedBalanceService.decode(parsed); throw new AssertionError("Diagnostic installed"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("diagnostic only"), "Explicit installation rejection"); }
        var analysis = new RuntimeBuildScenarios.Analysis(1, List.of(
                row("health-first", Metric.EFFECTIVE_HEALTH), row("offense-second", Metric.BURST_DAMAGE)), List.of());
        check(analysis.firstViolation(Channel.OFFENSE).startsWith("offense-second"), "Offense skips unrelated first case");
        check(!analysis.firstViolation(Channel.OFFENSE).contains("EFFECTIVE_HEALTH"), "Offense excludes health details");
        check(analysis.firstViolation(Channel.DEFENSE).startsWith("health-first"), "Defense retains relevant case");
        check(analysis.safeFor(Channel.HEALING) && analysis.firstViolation(Channel.HEALING).startsWith("no "), "Safe channel is not blamed");
        System.out.println("FailureEvidenceTest: " + checks + " checks PASS");
    }
    private static RuntimeBuildScenarios.Case row(String id, Metric metric) {
        var values = new EnumMap<Participation, Metrics>(Participation.class);
        var limits = new EnumMap<Participation, Limits>(Participation.class);
        var defense = new EnumMap<Participation, RuntimeBuildScenarios.DefensivePressure>(Participation.class);
        for (var participation : Participation.values()) {
            values.put(participation, new Metrics(1, 1, 0, 20, 20, 0));
            limits.put(participation, new Limits(2, 2, 0, 30, 30, 0));
            defense.put(participation, new RuntimeBuildScenarios.DefensivePressure(0, 0, false, 0, 0, 0, 0));
        }
        return new RuntimeBuildScenarios.Case("fixture", "fixture", new Evaluation(id, values,
                List.of(new Violation(Participation.FULLY_COMBINED, metric, 50, 30)), List.of()), limits, defense);
    }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
