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
        absentDependency(); readinessStates(); failuresAndOnce(); conflicts(); transactions(); allIntegrationsReported(); diagnosticScopes();
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
            check(!run.prepare("evidence", required, false), "Required " + state + " excludes optional evidence and permits generation");
            if (state == ProviderReadiness.Status.NOT_READY) check(run.unavailableReason("evidence", required) == CapabilitySink.Reason.DATA_NOT_READY,
                    "A configuration waiting for its publication epoch is not reported as an unsupported API or read exception");
            check(!run.warnings().isEmpty(), "Failed compatibility remains visible");
            check(run.diagnostics().get(0).getAsJsonObject().get("facts").getAsLong() == 0, "Unready provider emitted no claims");
            var advisory = provider("advisory_" + state, new ProviderReadiness(state, "9.0", "No claims emitted"), false);
            check(!runs(Map.of()).prepare("production", advisory, false), "Advisory " + state + " skips collection");
        }
        var incomplete = provider("partial_required", new ProviderReadiness(ProviderReadiness.Status.PARTIALLY_SUPPORTED, "1", "Recipes supported; required rates unknown"), true);
        check(runs(Map.of()).prepare("production", incomplete, false), "Explicit partial support collects only its declared supported subset");
        var safe = provider("partial_safe", new ProviderReadiness(ProviderReadiness.Status.PARTIALLY_SUPPORTED, "1", "Supported typed recipes complete; player-only mechanics excluded", true), true);
        var run = runs(Map.of());
        check(run.prepare("production", safe, false), "Explicitly complete required evidence permits partial support");
        check(run.run("production", safe, "collect", () -> 42).orElseThrow() == 42, "Supported subset collected");
        run.emitted("production", safe, 0, 1, .7);
        var diagnostic = run.diagnostics().get(0).getAsJsonObject();
        check(diagnostic.get("sources").getAsLong() == 1 && diagnostic.get("minimumConfidence").getAsDouble() == .7, "Source count and confidence measured separately");
        check(diagnostic.get("version").getAsString().equals("1") && diagnostic.get("requiredEvidenceComplete").getAsBoolean(), "Version and support provenance retained");
        check(diagnostic.get("probeNanos").getAsLong() >= 0 && diagnostic.get("collectionNanos").getAsLong() >= 0, "Probe and collection timings exposed");
        check(!run.warnings().isEmpty(), "Partial support remains visible");
        check(!runs(Map.of()).prepare("evidence", safe, true), "Disabled provider skipped before probe");
        var disabled = runs(Map.of()); disabled.prepare("evidence", safe, true);
        check(disabled.unavailableReason("evidence", safe) == CapabilitySink.Reason.CONFIGURATION_DISABLED,
                "An intentional disabled-provider override is distinct from compatibility failure");
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
        check(failedRun.run("evidence", failing, "collect", () -> { throw new NoClassDefFoundError("Unsupported optional ABI"); }).isEmpty(), "Entered hook failure excludes optional result");
        check(failedRun.diagnostics().get(0).getAsJsonObject().get("status").getAsString().equals("FAILED"), "Failure recorded as failure");
        check(!failedRun.diagnostics().get(0).getAsJsonObject().get("evidenceCollected").getAsBoolean(), "Failed hook cannot claim successful collection");
        AtomicInteger later = new AtomicInteger();
        check(failedRun.run("evidence", failing, "later", later::incrementAndGet).isEmpty() && later.get() == 0, "Later hooks skip failed integration");
        var brokenProbe = new GenerationProvider() {
            public String id() { return "broken_probe"; }
            public ProviderReadiness readiness(GenerationDataSnapshot input) { throw new NoSuchMethodError("Changed optional API"); }
        };
        var probeRun = runs(Map.of());
        check(!probeRun.prepare("evidence", brokenProbe, false), "Changed API in readiness is excluded");
        check(probeRun.diagnostics().get(0).getAsJsonObject().get("status").getAsString().equals("FAILED"), "Probe failure remains distinct from absence");
        check(!OptionalIntegration.attempt("fixture", "null", () -> null).succeeded(), "Null result is failure, never successful empty evidence");
        try {
            OptionalIntegration.attempt("fixture", "fatal", () -> { throw new OutOfMemoryError("fixture"); });
            throw new AssertionError("VM failures must propagate");
        } catch (OutOfMemoryError expected) { checks++; }
        var freshRun = runs(Map.of()); check(freshRun.prepare("evidence", provider, false), "Next generation probes independently");
        check(probes.get() == 2, "No readiness leaks between generation operations");
    }
    private static void transactions() {
        var failed = provider("partial_failure", ProviderReadiness.available(), true);
        var run = runs(Map.of()); run.prepare("evidence", failed, false);
        var sink = new EvidenceSink(); sink.add(fact("core", 1, 1, 3));
        check(!run.collect("evidence", failed, "collect", sink::staged, staged -> {
            check(staged.number(EvidenceFact.Subject.ITEM, "fixture:item", "resource_value", 0) == 3,
                    "Staged integration can read prior core facts without changing them");
            staged.add(fact("partial_failure", 100, 1, 1000)); staged.warn("partial warning");
            throw new IllegalArgumentException("broken custom output");
        }, sink::merge), "Partial sink failure is nonfatal");
        check(sink.size() == 1 && sink.number(EvidenceFact.Subject.ITEM, "fixture:item", "resource_value", 0) == 3,
                "Failed high-priority evidence cannot contaminate resolved core value");
        check(sink.warnings().isEmpty() && sink.conflicts() == 0, "Failed staged output cannot leak warnings/conflicts");
        var healthy = provider("healthy", ProviderReadiness.available(), true); run.prepare("evidence", healthy, false);
        check(run.collect("evidence", healthy, "collect", sink::staged, staged -> staged.add(fact("healthy", 2, .8, 4)), sink::merge),
                "Healthy integration after failure is collected");
        check(sink.size() == 2 && sink.number(EvidenceFact.Subject.ITEM, "fixture:item", "resource_value", 0) == 4,
                "Complete stage merges through ordinary conflict policy");
        var capability = new CapabilitySink();
        var badCapability = provider("bad_capability", ProviderReadiness.available(), true); run.prepare("capability", badCapability, false);
        check(!run.collect("capability", badCapability, "collect", CapabilitySink::new, staged -> {
            staged.analyzed(); staged.candidate("partial", "bad_capability", "partial unknown");
            staged.definition("bad_capability", "partial", new com.google.gson.JsonObject());
            throw new NoSuchMethodError("changed native API");
        }, capability::merge), "Capability failures are isolated by the same generic transaction");
        check(capability.counts().get("sourcesAnalyzed") == 0 && capability.unsupportedCounts().isEmpty()
                && capability.definitions().isEmpty(), "Failed capabilities leak no definitions/counts/candidates");
        var goodCapability = provider("good_capability", ProviderReadiness.available(), true); run.prepare("capability", goodCapability, false);
        check(run.collect("capability", goodCapability, "collect", CapabilitySink::new, staged -> {
            staged.analyzed();
            for (int i = 0; i < 75; i++) staged.candidate("sample" + i, "good_capability", "unsupported mechanic");
        }, capability::merge), "Complete capability stage merges");
        check(capability.counts().get("candidateCount") == 75 && capability.candidates().size() == 64
                && capability.counts().get("sourcesAnalyzed") == 1, "Complete counts survive capped samples without double counting");
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
    private static void allIntegrationsReported() {
        AtomicInteger probes = new AtomicInteger(), collections = new AtomicInteger();
        GenerationProvider brokenProbe = new GenerationProvider() {
            public String id() { return "a_broken_probe"; }
            public ProviderReadiness readiness(GenerationDataSnapshot input) {
                probes.incrementAndGet(); throw new NoSuchMethodError("changed optional API");
            }
        };
        var unsupported = provider("b_unsupported", new ProviderReadiness(ProviderReadiness.Status.UNSUPPORTED, "2", "Unknown definition format"), true);
        var notReady = provider("c_not_ready", new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "2", "No definition epoch yet"), true);
        var firstFailure = provider("d_failed_read", ProviderReadiness.available(), true);
        var secondFailure = provider("e_failed_read", ProviderReadiness.available(), true);
        var healthy = provider("f_healthy", ProviderReadiness.available(), true);
        var run = runs(Map.of());
        for (var integration : List.of(brokenProbe, unsupported, notReady, firstFailure, secondFailure, healthy)) {
            if (!run.prepare("evidence", integration, false)) continue;
            run.run("evidence", integration, "collect", () -> {
                collections.incrementAndGet();
                if (integration == firstFailure) throw new IllegalArgumentException("unrecognized recipe definition");
                if (integration == secondFailure) throw new NoClassDefFoundError("changed optional type");
                return 42;
            });
        }
        check(probes.get() == 1 && collections.get() == 3, "One pass tries every usable independent integration despite multiple failures");
        var rows = run.diagnostics();
        check(rows.size() == 6 && run.warnings().size() == 5, "Every adapter has a report row and every failed/unready adapter has a warning");
        check(rows.get(0).getAsJsonObject().get("status").getAsString().equals("FAILED")
                && rows.get(1).getAsJsonObject().get("status").getAsString().equals("UNSUPPORTED")
                && rows.get(2).getAsJsonObject().get("status").getAsString().equals("NOT_READY"), "Probe failure, unsupported data and unavailable epoch remain distinct");
        check(rows.get(3).getAsJsonObject().get("status").getAsString().equals("FAILED")
                && rows.get(4).getAsJsonObject().get("status").getAsString().equals("FAILED")
                && rows.get(5).getAsJsonObject().get("evidenceCollected").getAsBoolean(), "Both independent read failures and the final successful adapter are retained");
        run.run("evidence", firstFailure, "dependent_later_hook", () -> { throw new AssertionError("Failed adapter should not resume"); });
        var failed = run.diagnostics().get(3).getAsJsonObject();
        check(failed.getAsJsonArray("attemptedHooks").get(0).getAsString().equals("collect")
                && failed.getAsJsonArray("completedHooks").isEmpty()
                && failed.getAsJsonArray("skippedHooks").get(0).getAsString().equals("dependent_later_hook"), "Attempted, failed and skipped dependent hooks are explicit");
        for (int i = 0; i < 80; i++) {
            var adapter = provider("unready_" + i, new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "2", "Unready definition"), false);
            run.prepare("capability", adapter, false);
        }
        check(run.diagnostics().size() == 86 && run.warnings().size() == 85, "Provider diagnostics and compatibility warnings are never sample capped");
        var summary = new EvidenceSink(); run.warnings().forEach(summary::warn);
        var lateFailure = provider("late_capability_failure", ProviderReadiness.available(), false);
        run.prepare("capability", lateFailure, false);
        run.run("capability", lateFailure, "collect", () -> { throw new NoSuchMethodError("changed late optional API"); });
        run.warnings().forEach(summary::warn);
        check(summary.warnings().size() == 86 && summary.warnings().stream().anyMatch(w -> w.contains("late_capability_failure")),
                "Final warning refresh includes late capability problems and deduplicates earlier warnings");
    }
    private static void diagnosticScopes() {
        var declared = new java.util.HashSet<>(java.util.Set.of(CapabilityAxis.FLIGHT));
        AtomicInteger declarations = new AtomicInteger();
        var scoped = new GenerationProvider() {
            public String id() { return "scoped"; }
            public java.util.Set<CapabilityAxis> capabilityAxes() { declarations.incrementAndGet(); return declared; }
        };
        var run = runs(Map.of());
        check(run.prepare("capability", scoped, false), "A diagnostic axis declaration does not require factual capability output");
        declared.add(CapabilityAxis.GLIDING);
        check(run.capabilityAxes("capability", scoped).equals(java.util.Set.of(CapabilityAxis.FLIGHT)) && declarations.get() == 1,
                "Declarations are captured once and isolated from later provider mutation");
        check(run.diagnostics().get(0).getAsJsonObject().getAsJsonArray("capabilityAxes").size() == 1,
                "Readiness provenance retains the intended adapter domain without inventing facts");
        var stable = run.capabilityDiagnostics().get(0).getAsJsonObject();
        check(!stable.has("probeNanos") && !stable.has("collectionNanos")
                        && run.diagnostics().get(0).getAsJsonObject().has("probeNanos"),
                "Balance provenance omits measurements of elapsed work while the operational report preserves them");
        AtomicInteger attempts = new AtomicInteger();
        var broken = new GenerationProvider() {
            public String id() { return "broken_declaration"; }
            public java.util.Set<CapabilityAxis> capabilityAxes() { attempts.incrementAndGet(); throw new NoClassDefFoundError("changed declaration"); }
        };
        check(!run.prepare("capability", broken, false) && run.status("capability", broken) == ProviderReadiness.Status.FAILED,
                "A broken optional diagnostic declaration remains a nonfatal recorded read failure");
        check(run.capabilityAxes("capability", broken).isEmpty() && attempts.get() == 1,
                "Formatting a failed adapter does not re-enter its broken declaration");
        var absent = new GenerationProvider() {
            public String id() { return "absent_declaration"; }
            public java.util.List<String> dependencyModIds() { return List.of("missing"); }
            public java.util.Set<CapabilityAxis> capabilityAxes() { throw new AssertionError("Absent adapter must not be entered"); }
        };
        check(!run.prepare("capability", absent, false) && run.status("capability", absent) == ProviderReadiness.Status.ABSENT,
                "Absent dependencies skip diagnostic declarations as well as native readiness");
        check(!run.dependenciesInstalled("capability", absent) && run.dependenciesInstalled("capability", scoped),
                "Captured dependency installation distinguishes an absent dependency from a declared dependency-free adapter");
        AtomicInteger dependencyReads = new AtomicInteger();
        var brokenDependencies = new GenerationProvider() {
            public String id() { return "broken_dependencies"; }
            public java.util.List<String> dependencyModIds() { dependencyReads.incrementAndGet(); throw new NoClassDefFoundError("changed dependency declaration"); }
        };
        check(!run.prepare("capability", brokenDependencies, false)
                        && run.status("capability", brokenDependencies) == ProviderReadiness.Status.FAILED
                        && !run.dependenciesInstalled("capability", brokenDependencies) && dependencyReads.get() == 1,
                "Failed dependency declarations remain unknown and are not re-entered when formatting unavailable-provider diagnostics");
        var next = provider("next_after_bad_declaration", ProviderReadiness.available(), false);
        check(run.prepare("capability", next, false) && run.run("capability", next, "collect", () -> 42).orElseThrow() == 42,
                "Independent integrations still run after a failed diagnostic declaration");
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
