package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic profiling contracts; never starts Minecraft or a heartbeat thread. */
public final class BalancePerformanceTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        nestingAndOutcomes();
        failureAndIsolation();
        boundedDiagnostics();
        profileReadInstrumentation();
        sectionDigestReuseAndIsolation();
        concurrentSectionDigestReuse();
        System.out.println("BalancePerformanceTest: " + checks + " checks PASS");
    }

    private static void nestingAndOutcomes() {
        AtomicLong clock = new AtomicLong();
        var log = new ArrayList<String>();
        BalancePerformance.Operation operation = BalancePerformance.begin("load_attempt", "test", clock::get, log::add);
        check(BalancePerformance.currentSnapshot().activePhase().isEmpty(), "initial active phase");
        clock.set(10);
        try (var parent = BalancePerformance.phase("parent")) {
            clock.set(15);
            try (var child = BalancePerformance.phase("child")) {
                check(operation.snapshot().activePhase().equals("parent/child"), "exact live nested phase");
                clock.set(22);
            }
            clock.set(25);
            try (var child = BalancePerformance.phase("child")) { clock.set(36); }
            clock.set(40);
        }
        BalancePerformance.count("items", 64341);
        BalancePerformance.increment("decodes");
        BalancePerformance.increment("decodes");
        BalancePerformance.flag("saved_data_reused", true);
        BalancePerformance.detail("profile_integrity", "same");
        operation.type("saved_profile_load");
        operation.complete("loaded_validated");
        clock.set(42); operation.close(); operation.close();
        var result = operation.snapshot();
        check(result.elapsedNanos() == 42, "monotonic elapsed");
        check(result.unattributedNanos() == 12, "root gaps accounted once");
        var parent = result.phases().stream().filter(p -> p.path().equals("parent")).findFirst().orElseThrow();
        var child = result.phases().stream().filter(p -> p.path().equals("parent/child")).findFirst().orElseThrow();
        check(parent.inclusiveNanos() == 30 && parent.exclusiveNanos() == 12, "parent excludes nested time");
        check(child.invocations() == 2 && child.inclusiveNanos() == 18 && child.exclusiveNanos() == 18, "repeat aggregation");
        check(result.phases().stream().mapToLong(BalancePerformance.PhaseTiming::exclusiveNanos).sum()
                + result.unattributedNanos() == result.elapsedNanos(), "no nested double count");
        check(result.finished() && result.activePhase().isEmpty(), "closed status");
        check(result.operation().equals("saved_profile_load") && result.outcome().equals("loaded_validated"), "classify and retain validated load outcome");
        check(result.counts().get("items") == 64341 && result.counts().get("decodes") == 2, "workloads and invocation counts");
        check(result.flags().get("saved_data_reused") && result.details().get("profile_integrity").equals("same"), "reuse identity");
        check(result.equals(BalancePerformance.lastSnapshot()) && BalancePerformance.currentSnapshot() == null, "completed operation detached");
        check(log.size() == 2 && JsonParser.parseString(log.get(1)).getAsJsonObject().get("event").getAsString().equals("end"), "bounded begin end structured logging");
    }

    private static void failureAndIsolation() {
        AtomicLong clock = new AtomicLong();
        var log = new ArrayList<String>();
        String previousId = BalancePerformance.lastSnapshot().id();
        try (var operation = BalancePerformance.begin("explicit_rebuild", "command", clock::get, log::add)) {
            try {
                try (var outer = BalancePerformance.phase("validate")) {
                    try (var inner = BalancePerformance.phase("evidence")) {
                        clock.set(5); throw new IllegalArgumentException("example");
                    }
                }
            } catch (IllegalArgumentException expected) {
                BalancePerformance.flag("previous_active_retained", true);
                operation.fail(expected);
            }
        }
        var failed = BalancePerformance.lastSnapshot();
        check(!failed.id().equals(previousId), "attempt identities unique within JVM");
        check(failed.outcome().equals("failed") && failed.failureType().equals(IllegalArgumentException.class.getName()), "failure outcome type");
        check(failed.lastEnteredPhase().equals("validate/evidence"), "last phase survives exception unwind");
        check(failed.failurePhase().equals("validate/evidence"), "failure phase retained independently of later capture");
        check(failed.flags().get("previous_active_retained"), "failure retention recorded");
        check(JsonParser.parseString(log.getLast()).getAsJsonObject().get("event").getAsString().equals("failure"), "failure event");
        try (var ignored = BalancePerformance.phase("not_an_operation")) { BalancePerformance.increment("ignored"); }
        check(BalancePerformance.currentSnapshot() == null && failed.equals(BalancePerformance.lastSnapshot()), "uninstrumented callers stay no-op");
        try (var operation = BalancePerformance.begin("probe", "broken_logger", clock::get, line -> { throw new IllegalStateException(); })) {
            operation.complete("validated");
        }
        check(BalancePerformance.lastSnapshot().outcome().equals("validated"), "logger failure cannot invalidate operation");
        try (var outer = BalancePerformance.begin("load", "outer", clock::get, ignored -> { })) {
            String outerId = outer.snapshot().id();
            try (var inner = BalancePerformance.begin("profile_validation", "nested", clock::get, ignored -> { })) {
                check(inner.snapshot().details().get("parent_operation_id").equals(outerId), "unexpected nested operation identified");
                inner.complete("validated");
            }
            check(BalancePerformance.currentSnapshot().id().equals(outerId), "nested operation restores outer context");
            outer.complete("loaded_validated");
        }
    }

    private static void boundedDiagnostics() {
        AtomicLong clock = new AtomicLong();
        var log = new ArrayList<String>();
        try (var operation = BalancePerformance.begin("probe", "bounds", clock::get, log::add)) {
            for (int index = 0; index < 300; index++) {
                BalancePerformance.count("count" + index, index);
                BalancePerformance.flag("flag" + index, true);
                BalancePerformance.detail("detail" + index, "x".repeat(500));
                clock.addAndGet(2_000_000_000L);
                try (var ignored = BalancePerformance.phase("phase" + index)) { clock.incrementAndGet(); }
            }
            operation.complete("done");
        }
        var result = BalancePerformance.lastSnapshot();
        check(result.counts().size() == 128 && result.flags().size() == 128 && result.details().size() == 128, "bounded workload fields");
        check(result.details().values().stream().allMatch(value -> value.length() <= 256), "bounded diagnostic strings");
        check(result.phases().size() == 256, "bounded phase cardinality");
        check(log.size() == 130 && result.suppressedProgressEvents() > 0, "progress log cap excludes required begin end");
        check(result.phases().stream().mapToLong(BalancePerformance.PhaseTiming::exclusiveNanos).sum()
                + result.unattributedNanos() == result.elapsedNanos(), "bounds preserve timing accounting");
    }

    private static void profileReadInstrumentation() throws Exception {
        JsonObject content = new JsonObject();
        for (String name : new String[]{"metadata", "settings", "overrides", "evidence", "runtime", "economy", "skills", "validation"}) {
            JsonObject section = new JsonObject(); section.addProperty("value", 1); content.add(name, section);
        }
        var original = BalanceDocument.seal(content);
        var directory = Files.createTempDirectory("balance-performance-test");
        var target = directory.resolve(BalanceProfileStore.PROFILE_FILE);
        try {
            BalanceProfileStore.replace(target, original);
            try (var operation = BalancePerformance.begin("offline_saved_profile_read", "test", System::nanoTime, ignored -> { })) {
                var parsed = BalanceProfileStore.read(target);
                check(parsed.integrity().equals(original.integrity()), "instrumented read preserves verified integrity");
                check(parsed.compactEvidenceAndEconomy().integrity().equals(original.integrity()), "instrumented compaction preserves integrity");
                operation.complete("verified");
            }
            var result = BalancePerformance.lastSnapshot();
            check(result.counts().get("profile_reads") == 1 && result.counts().get("profile_json_parses") == 1, "actual read parse invocation counts");
            check(result.counts().get("profile_stored_bytes") == Files.size(target), "actual stored byte workload");
            for (String name : new String[]{"profile_locate", "profile_open", "profile_reader_open", "json_parse_and_decompress",
                    "schema_and_integrity_validation"})
                check(result.phases().stream().anyMatch(phase -> phase.path().equals(name) && phase.invocations() == 1), "actual phase " + name);
            check(result.counts().get("profile_compressed_snapshot_reuses") == 1, "reuse validated compressed authority once");
            check(result.phases().stream().noneMatch(phase -> phase.path().contains("section_serialization_gzip")),
                    "saved profile must not recompress validated evidence");
            check(result.counts().get("section_hash_computations/economy") == 1, "economy digest piggybacks mandatory envelope validation");
            BalanceDocument retained;
            try (var operation = BalancePerformance.begin("generated_commit", "test", System::nanoTime, ignored -> { })) {
                retained = BalanceProfileStore.replaceAndRetain(target, original);
                operation.complete("committed");
            }
            var commit = BalancePerformance.lastSnapshot();
            check(commit.counts().get("profile_compressed_snapshot_reuses") == 1, "generated commit reuses exactly its written bytes");
            check(!commit.counts().containsKey("profile_reads"), "generated commit never rereads/parses the file");
            Files.delete(target);
            check(retained.text().equals(original.text()), "committed snapshot survives file deletion with exact canonical output");
            check(retained.sectionHash("economy").equals(original.sectionHash("economy")), "lazy economy hash retains exact contract");
            retained.section("evidence").addProperty("value", 77);
            check(retained.section("evidence").equals(original.section("evidence")), "snapshot public readers stay isolated");
        } finally { Files.deleteIfExists(target); Files.deleteIfExists(directory); }
    }

    private static void sectionDigestReuseAndIsolation() throws Exception {
        JsonObject content = digestFixture();
        var document = BalanceDocument.seal(content);
        String originalText = document.text();
        String expectedEvidenceHash = BalanceDocument.hash(document.section("evidence"));
        try (var operation = BalancePerformance.begin("offline_digest_reuse", "test", System::nanoTime, ignored -> { })) {
            // Native decode first checks the evidence digest, then compacts that same owned tree.
            check(document.sectionHash("evidence").equals(expectedEvidenceHash), "required initial digest is computed");
            content.getAsJsonObject("evidence").addProperty("value", "caller mutation");
            document.section("evidence").addProperty("value", "public section mutation");
            var compacted = document.compactEvidenceAndEconomy();
            check(compacted.sectionHash("evidence").equals(expectedEvidenceHash), "compaction reuses checked evidence digest");
            check(compacted.text().equals(originalText), "cached compaction preserves every serialized byte");
            check(compacted.integrity().equals(document.integrity()), "cached compaction preserves full integrity");
            check(BalanceDocument.parse(compacted.text()).sectionHash("evidence").equals(expectedEvidenceHash), "full parse integrity still checked");
            check(!BalanceDocument.seal(content).sectionHash("evidence").equals(expectedEvidenceHash), "new document cannot reuse another document's digest");
            operation.complete("verified");
        }
        // Separate attempt counts just the production sequence, without fresh documents above.
        document = BalanceDocument.seal(digestFixture());
        try (var operation = BalancePerformance.begin("offline_digest_count", "test", System::nanoTime, ignored -> { })) {
            document.sectionHash("evidence");
            document.compactEvidenceAndEconomy();
            operation.complete("verified");
        }
        var counts = BalancePerformance.lastSnapshot().counts();
        check(counts.get("section_hash_computations/evidence") == 1, "one evidence hash across prevalidation and compaction");
        check(counts.get("section_hash_cache_hits/evidence") == 1, "compaction records evidence digest reuse");
        check(counts.get("section_hash_computations/economy") == 1, "uncomputed economy digest remains required");
    }

    private static void concurrentSectionDigestReuse() throws Exception {
        var document = BalanceDocument.seal(digestFixture());
        String expected = BalanceDocument.hash(document.section("evidence"));
        var ready = new java.util.concurrent.CountDownLatch(8);
        var start = new java.util.concurrent.CountDownLatch(1);
        record ReadResult(String hash, BalancePerformance.Snapshot snapshot) { }
        var results = new ArrayList<java.util.concurrent.Future<ReadResult>>();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 8; index++) results.add(executor.submit(() -> {
                ready.countDown(); start.await();
                var operation = BalancePerformance.begin("concurrent_digest", "test", System::nanoTime, ignored -> { });
                String hash;
                try (operation) {
                    hash = document.sectionHash("evidence");
                    document.section("evidence").addProperty("value", "private reader mutation");
                    operation.complete("verified");
                }
                return new ReadResult(hash, operation.snapshot());
            }));
            ready.await(); start.countDown();
            long computations = 0, hits = 0;
            for (var result : results) {
                var value = result.get();
                check(value.hash().equals(expected), "concurrent digest identity");
                computations += value.snapshot().counts().getOrDefault("section_hash_computations/evidence", 0L);
                hits += value.snapshot().counts().getOrDefault("section_hash_cache_hits/evidence", 0L);
            }
            check(computations == 1 && hits == 7, "concurrent requests compute one digest and reuse seven times");
        }
        check(document.sectionHash("evidence").equals(expected), "concurrent reader copies cannot mutate cached content");
    }

    private static JsonObject digestFixture() {
        JsonObject content = new JsonObject();
        for (String name : new String[]{"metadata", "settings", "overrides", "evidence", "runtime", "economy", "skills", "validation"}) {
            JsonObject section = new JsonObject(); section.addProperty("value", "owned " + name); content.add(name, section);
        }
        content.getAsJsonObject("evidence").addProperty("payload", "repeated evidence text ".repeat(1000));
        return content;
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
