package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Real coordinator and conflict resolution with deterministic adapters; no optional classes or server stubs. */
public final class GenerationProviderContractTest {
    private static int checks;
    public static void main(String[] args) {
        absentDependency(); readinessStates(); failuresAndOnce(); conflicts();
        System.out.println("GenerationProviderContractTest: " + checks + " checks PASS");
    }
    private static GenerationProviders runs(Map<String, String> installed) { return new GenerationProviders(null, installed::get); }
    private static void absentDependency() {
        var lootr = runs(Map.of());
        check(!lootr.prepare("loot", new com.mistaboom.essence_ascendance.valuation.LootrProvider(), false), "Absent Lootr skips its reflective reader");
        check(lootr.diagnostics().get(0).getAsJsonObject().get("status").getAsString().equals("ABSENT"), "Lootr absence explicit in provider diagnostics");
        AtomicInteger probes = new AtomicInteger();
        GenerationProvider optional = new GenerationProvider() {
            public String id() { return "optional"; }
            public List<String> dependencyModIds() { return List.of("missing_mod"); }
            public ProviderReadiness readiness(GenerationDataSnapshot input) {
                probes.incrementAndGet(); throw new NoClassDefFoundError("Optional types must never load");
            }
        };
        var run = runs(Map.of());
        check(!run.prepare("evidence", optional, false) && probes.get() == 0, "Absent dependency skips adapter code entirely");
        var row = run.diagnostics().get(0).getAsJsonObject();
        check(row.get("status").getAsString().equals("ABSENT") && row.get("adapterAvailable").getAsBoolean(), "Adapter and mod installation are distinct");
        check(!row.get("dependencies").getAsJsonObject().get("missing_mod").getAsJsonObject().get("installed").getAsBoolean(), "Absent mod reported");
        check(!row.get("evidenceCollected").getAsBoolean() && !row.has("minimumConfidence"), "Absence is not authoritative zero evidence");
    }
    private static void readinessStates() {
        for (var state : List.of(ProviderReadiness.Status.NOT_READY, ProviderReadiness.Status.UNSUPPORTED, ProviderReadiness.Status.FAILED)) {
            var required = provider("required_" + state, new ProviderReadiness(state, "9.0", "Unsupported or later-event data"), true);
            var run = runs(Map.of());
            rejected(() -> run.prepare("evidence", required, false), "Required " + state + " aborts generation");
            check(run.diagnostics().get(0).getAsJsonObject().get("facts").getAsLong() == 0, "Unready provider emitted no claims");
            var advisory = provider("advisory_" + state, new ProviderReadiness(state, "9.0", "No claims emitted"), false);
            check(!runs(Map.of()).prepare("production", advisory, false), "Advisory " + state + " skips collection");
        }
        var incomplete = provider("partial_required", new ProviderReadiness(ProviderReadiness.Status.PARTIALLY_SUPPORTED, "1", "Recipes supported; required rates unknown"), true);
        rejected(() -> runs(Map.of()).prepare("production", incomplete, false), "Incomplete required subset cannot publish");
        var safe = provider("partial_safe", new ProviderReadiness(ProviderReadiness.Status.PARTIALLY_SUPPORTED, "1", "Supported typed recipes complete; player-only mechanics excluded", true), true);
        var run = runs(Map.of());
        check(run.prepare("production", safe, false), "Explicitly complete required evidence permits partial support");
        check(run.run("production", safe, "collect", () -> 42) == 42, "Supported subset collected");
        run.emitted("production", safe, 0, 1, .7);
        var diagnostic = run.diagnostics().get(0).getAsJsonObject();
        check(diagnostic.get("sources").getAsLong() == 1 && diagnostic.get("minimumConfidence").getAsDouble() == .7, "Source count and confidence measured separately");
        check(diagnostic.get("version").getAsString().equals("1") && diagnostic.get("requiredEvidenceComplete").getAsBoolean(), "Version and support provenance retained");
        check(diagnostic.get("probeNanos").getAsLong() >= 0 && diagnostic.get("collectionNanos").getAsLong() >= 0, "Probe and collection timings exposed");
        check(!run.warnings().isEmpty(), "Partial support remains visible");
        check(!runs(Map.of()).prepare("evidence", safe, true), "Disabled provider skipped before probe");
    }
    private static void failuresAndOnce() {
        AtomicInteger probes = new AtomicInteger(), hooks = new AtomicInteger();
        GenerationProvider provider = new GenerationProvider() {
            public String id() { return "once"; }
            public ProviderReadiness readiness(GenerationDataSnapshot input) { probes.incrementAndGet(); return ProviderReadiness.available(); }
        };
        var run = runs(Map.of());
        try (var operation = BalancePerformance.begin("explicit_rebuild", "provider_fixture")) {
            check(run.prepare("evidence", provider, false), "Available provider ready");
            run.run("evidence", provider, "before_acquisition", () -> hooks.incrementAndGet());
            run.run("evidence", provider, "collect", () -> hooks.incrementAndGet());
            operation.complete("fixture_collected");
        }
        check(probes.get() == 1 && hooks.get() == 2, "Probe once and each appropriate hook once");
        rejected(() -> run.prepare("evidence", provider, false), "Duplicate probe rejected");
        rejected(() -> run.run("evidence", provider, "collect", () -> hooks.incrementAndGet()), "Duplicate hook rejected before work");
        check(hooks.get() == 2, "Duplicate rejection emitted nothing");
        check(BalancePerformance.lastSnapshot().phases().stream().anyMatch(phase -> phase.path().contains("provider_readiness")), "Readiness instrumented");
        var failing = provider("failed_hook", ProviderReadiness.available(), false);
        var failedRun = runs(Map.of()); failedRun.prepare("evidence", failing, false);
        rejected(() -> failedRun.run("evidence", failing, "collect", () -> { throw new NoClassDefFoundError("Unsupported optional ABI"); }), "Entered hook failure aborts even advisory adapters");
        check(failedRun.diagnostics().get(0).getAsJsonObject().get("status").getAsString().equals("FAILED"), "Failure recorded as failure");
        check(!failedRun.diagnostics().get(0).getAsJsonObject().get("evidenceCollected").getAsBoolean(), "Failed hook cannot claim successful collection");
        var freshRun = runs(Map.of()); check(freshRun.prepare("evidence", provider, false), "Next generation probes independently");
        check(probes.get() == 2, "No readiness leaks between generation operations");
    }
    private static void conflicts() {
        var first = fact("a", 2, .4, 1); var second = fact("b", 1, .9, 2);
        var forward = new EvidenceSink(); forward.add(first); forward.add(second);
        var reverse = new EvidenceSink(); reverse.add(second); reverse.add(first);
        check(forward.facts().equals(reverse.facts()), "Conflict output independent of insertion order");
        check(forward.number(EvidenceFact.Subject.ITEM, "fixture:item", "resource_value", 0) == 1, "Explicit priority wins before confidence");
        check(forward.conflicts() == 1 && reverse.conflicts() == 1 && forward.facts().size() == 2, "Losing evidence and conflict count retained");
        var adjusted = new EvidenceSink(Map.of("b", 3)); adjusted.add(first); adjusted.add(second);
        check(adjusted.number(EvidenceFact.Subject.ITEM, "fixture:item", "resource_value", 0) == 2, "Provider factual priority respected");
        var emitted = adjusted.emittedSince(0);
        check(emitted.facts() == 2 && emitted.minimumConfidence() == .4, "All emitted evidence contributes confidence");
    }
    private static GenerationProvider provider(String id, ProviderReadiness readiness, boolean required) {
        return new GenerationProvider() {
            public String id() { return id; }
            public boolean requiredForGeneration() { return required; }
            public ProviderReadiness readiness(GenerationDataSnapshot input) { return readiness; }
        };
    }
    private static EvidenceFact fact(String provider, int priority, double confidence, int value) {
        return new EvidenceFact(EvidenceFact.Subject.ITEM, "fixture:item", "resource_value", EvidenceFact.Value.number(value), provider,
                EvidenceFact.Origin.OBSERVED, confidence, priority, ProgressionBand.ENTRY, List.of(), "Synthetic provider measurement");
    }
    private static void rejected(Runnable action, String message) { try { action.run(); throw new AssertionError(message); } catch (IllegalStateException expected) { checks++; } }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
