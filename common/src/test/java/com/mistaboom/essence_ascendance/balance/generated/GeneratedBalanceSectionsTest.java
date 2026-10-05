package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.mistaboom.essence_ascendance.balance.economy.DissolutionYield;
import com.mistaboom.essence_ascendance.balance.economy.EconomicValue;
import com.mistaboom.essence_ascendance.balance.economy.EconomyConservationSolver;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProcessingPolicy;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import com.mistaboom.essence_ascendance.balance.engine.Automation;
import com.mistaboom.essence_ascendance.balance.engine.Availability;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence;
import com.mistaboom.essence_ascendance.balance.engine.EnemyReference;
import com.mistaboom.essence_ascendance.balance.engine.EquipmentReference;
import com.mistaboom.essence_ascendance.balance.engine.EvidenceFact;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.engine.ResourceEvidence;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact differential checks against the existing whole-tree canonical serialization contract. */
public final class GeneratedBalanceSectionsTest {
    private static int assertions;
    private static final String SPECIAL = "quoted \" <>&=' \\ /\n\t\r\u2028\u2029 café \ud83c\udf0d \ud800";

    public static void main(String[] args) throws Exception {
        PackEvidence evidence = evidence();
        EconomyProfile economy = economy();
        for (boolean compact : new boolean[]{false, true}) {
            equivalent(evidence, out -> GeneratedBalanceSections.writeEvidence(evidence, out), compact);
            equivalent(economy, out -> GeneratedBalanceSections.writeEconomy(economy, out), compact);
            var emptyEvidence = new PackEvidence(Map.of(), List.of(), List.of(), Map.of(),
                    List.of(), List.of(), Map.of(), List.of());
            equivalent(emptyEvidence, out -> GeneratedBalanceSections.writeEvidence(emptyEvidence, out), compact);
            var emptyEconomy = new EconomyProfile(Map.of(), List.of(), List.of(), List.of(), 0,
                    new EconomyProcessingPolicy(1, 10_000));
            equivalent(emptyEconomy, out -> GeneratedBalanceSections.writeEconomy(emptyEconomy, out), compact);
        }

        var projected = JsonParser.parseString(render(out -> GeneratedBalanceSections.writeEvidence(evidence, out), true))
                .getAsJsonObject();
        check(projected.getAsJsonArray("equipment").get(0).getAsJsonObject().get("itemId").getAsString().equals("test:z_tool"),
                "Equipment array order was sorted instead of preserved");
        check(projected.getAsJsonObject("frontiers").keySet().iterator().next().equals("APEX"),
                "Enum-key map used enum declaration order instead of lexical order");
        check(!projected.getAsJsonArray("facts").get(0).getAsJsonObject().has("stage"),
                "Nullable record member was written instead of omitted");
        check(!projected.getAsJsonObject("graphSummary").has("omitted"), "Nullable map value was retained");
        check(projected.getAsJsonArray("facts").get(0).getAsJsonObject().get("reason").getAsString().equals(SPECIAL),
                "Escaped or surrogate text changed");
        check(projected.getAsJsonObject("graphSummary").get("large").getAsLong() == Long.MAX_VALUE,
                "Large integral value lost precision");
        lazyOutputFailure();
        envelopeRoundTrip(evidence, economy);
        if (List.of(args).contains("--stress")) stress();
        System.out.println("GeneratedBalanceSectionsTest: " + assertions + " canonical streaming assertions PASS");
    }

    private static com.google.gson.JsonObject envelope() {
        var root = new com.google.gson.JsonObject();
        for (String name : List.of("metadata", "settings", "overrides", "runtime", "skills", "validation"))
            root.add(name, new com.google.gson.JsonObject());
        return root;
    }

    private static void envelopeRoundTrip(PackEvidence evidence, EconomyProfile economy) throws Exception {
        var oldRoot = envelope();
        oldRoot.add("evidence", BalanceDocument.GSON.toJsonTree(evidence));
        oldRoot.add("economy", BalanceDocument.GSON.toJsonTree(economy));
        oldRoot.getAsJsonObject("metadata").addProperty("evidenceDigest", BalanceDocument.hash(oldRoot.get("evidence")));
        var previous = BalanceDocument.seal(oldRoot);
        var generated = BalanceDocument.sealGeneratedOwned(envelope(), evidence, economy);
        check(generated.integrity().equals(previous.integrity()), "Streamed envelope integrity differs from full-tree seal");
        check(generated.text().equals(previous.text()), "Streamed envelope differs from exact historical canonical JSON");
        for (String name : List.of("metadata", "settings", "overrides", "runtime", "skills", "validation", "evidence", "economy"))
            check(generated.sectionHash(name).equals(previous.sectionHash(name)), "Streamed section digest differs: " + name);
        var decoded = generated.decodeSection("evidence", PackEvidence.class);
        generated.verifySection("evidence", decoded);
        check(decoded.facts().getFirst().reason().equals(SPECIAL), "Generated typed roundtrip changed surrogate text");
        rejectSurrogateSubstitution(generated, decoded);
        rejectSurrogateSubstitution(previous.compactEvidenceAndEconomy(), decoded);
        generatedSnapshotsAreCompact(generated);
        var mismatch = new PackEvidence(Map.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());
        try { generated.verifySection("evidence", mismatch); throw new AssertionError("Changed evidence accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        var folder = java.nio.file.Files.createTempDirectory("generated-section-test-");
        var path = BalanceProfileStore.profilePath(folder);
        try {
            BalanceProfileStore.replace(path, previous);
            String before = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(java.nio.file.Files.readAllBytes(path)));
            try {
                var invalid = new PackEvidence(Map.of(), List.of(new EquipmentReference("test:invalid", "head", ProgressionBand.ENTRY,
                        Map.of(CapabilityAxis.ARMOR, Double.NaN), List.of(), true, true, 1, "")), List.of(), Map.of(), List.of(), List.of(), Map.of());
                BalanceProfileStore.replaceAndRetain(path, BalanceDocument.sealGeneratedOwned(envelope(), invalid, economy));
                throw new AssertionError("Invalid generated candidate committed");
            } catch (IllegalArgumentException expected) { assertions++; }
            check(before.equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(java.nio.file.Files.readAllBytes(path)))), "Failed generation changed previous authority");
            var retained = BalanceProfileStore.replaceAndRetain(path, generated);
            rejectSurrogateSubstitution(retained, decoded);
            var reopened = BalanceProfileStore.read(path);
            check(reopened.integrity().equals(previous.integrity()), "Stored streamed envelope changed integrity");
            check(reopened.decodeSection("evidence", PackEvidence.class).facts().getFirst().reason().equals(SPECIAL),
                    "Disk roundtrip replaced isolated surrogate with a question mark");
            java.nio.file.Files.delete(path);
            check(retained.text().equals(previous.text()), "Retained generated snapshot depends on the backing file");
            check(generated.text().equals(previous.text()), "Commit mutated input document");
        } finally { java.nio.file.Files.deleteIfExists(path); java.nio.file.Files.deleteIfExists(folder); }
    }

    private static void rejectSurrogateSubstitution(BalanceDocument document, PackEvidence original) {
        var facts = new java.util.ArrayList<>(original.facts());
        var fact = facts.getFirst();
        facts.set(0, new EvidenceFact(fact.subject(), fact.subjectId(), fact.property(), fact.value(), fact.provider(),
                fact.origin(), fact.confidence(), fact.priority(), fact.stage(), fact.dependencies(),
                fact.reason().replace('\ud800', '?')));
        var changed = new PackEvidence(original.resources(), original.equipment(), original.enemies(), original.frontiers(),
                facts, original.warnings(), original.graphSummary(), original.capabilities());
        // This lossy UTF-8 equivalence is historical and must remain for profile compatibility.
        check(document.sectionHash("evidence").equals(BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(changed))),
                "Fixture no longer exercises the historical malformed-surrogate hash equivalence");
        try { document.verifySection("evidence", changed); throw new AssertionError("Surrogate-to-question-mark change accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }

    private static void generatedSnapshotsAreCompact(BalanceDocument document) throws Exception {
        var sections = BalanceDocument.class.getDeclaredField("packedSections"); sections.setAccessible(true);
        var packed = (Map<?, ?>) sections.get(document);
        for (String name : List.of("evidence", "economy")) {
            Object section = packed.get(name);
            var compressed = section.getClass().getDeclaredField("compressed"); compressed.setAccessible(true);
            try (var input = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream((byte[]) compressed.get(section)))) {
                String stored = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                check(!stored.contains("\n"), "Generated section uses pretty whitespace within the logical-size limit: " + name);
                check(JsonParser.parseString(stored).equals(document.section(name)), "Compact generated section changed content: " + name);
            }
        }
    }

    private static PackEvidence evidence() {
        var source = new AcquisitionSource("test:recipe", AcquisitionSource.Kind.RECIPE, ProgressionBand.LATE,
                .25, false, false, -0.0, .82, List.of("test:z", "test:a", "test:z"), SPECIAL);
        var resource = new ResourceEvidence("test:ore", ProgressionBand.EARLY, Availability.FINITE,
                Automation.NONE, true, true, 1e-20, .5, List.of(source), List.of(SPECIAL));
        var resources = new LinkedHashMap<String, ResourceEvidence>();
        resources.put("test:z", resource); resources.put("test:a", resource); resources.put("test:omitted", null);
        var axes = new EnumMap<CapabilityAxis, Double>(CapabilityAxis.class);
        axes.put(CapabilityAxis.SUSTAINED_DAMAGE, -0.0); axes.put(CapabilityAxis.ARMOR, 1e-20);
        axes.put(CapabilityAxis.FLIGHT, 1.0);
        var frontiers = new EnumMap<ProgressionBand, Map<CapabilityAxis, Double>>(ProgressionBand.class);
        frontiers.put(ProgressionBand.ENTRY, axes); frontiers.put(ProgressionBand.APEX, axes);
        var graph = new LinkedHashMap<String, Long>();
        graph.put("z", 0L); graph.put("large", Long.MAX_VALUE); graph.put("omitted", null); graph.put("a", 1L);
        var facts = List.of(
                new EvidenceFact(EvidenceFact.Subject.ITEM, "test:ore", "message", EvidenceFact.Value.text(SPECIAL),
                        "test:provider", EvidenceFact.Origin.OBSERVED, .5, -2, null, List.of("z", "a"), SPECIAL),
                new EvidenceFact(EvidenceFact.Subject.CAPABILITY, "test:flight", "enabled", EvidenceFact.Value.flag(true),
                        "test:provider", EvidenceFact.Origin.OVERRIDE, 1, 9, ProgressionBand.APEX, List.of(), ""),
                new EvidenceFact(EvidenceFact.Subject.RECIPE, "test:recipe", "rate", EvidenceFact.Value.number(-0.0),
                        "test:provider", EvidenceFact.Origin.INFERRED, 0, 0, ProgressionBand.ENTRY, List.of(), ""));
        return new PackEvidence(resources,
                List.of(new EquipmentReference("test:z_tool", "mainhand", ProgressionBand.LATE, axes,
                                List.of("z", "a", "z"), true, true, .75, SPECIAL),
                        new EquipmentReference("test:a_tool", "head", ProgressionBand.ENTRY, Map.of(),
                                List.of(), false, false, 0, null)),
                List.of(new EnemyReference("test:z_enemy", EnemyReference.Encounter.BOSS, ProgressionBand.APEX,
                                axes, true, .8, List.of("z", "a"), SPECIAL),
                        new EnemyReference("test:a_enemy", EnemyReference.Encounter.UNKNOWN, ProgressionBand.ENTRY,
                                Map.of(), false, 0, List.of(), null)),
                frontiers, facts, List.of("z", SPECIAL, "a", "z"), graph,
                List.of(new CapabilityEvidence("test:flight", ProgressionBand.APEX, axes, true, 1, SPECIAL)));
    }

    private static EconomyProfile economy() {
        var value = new EconomyProfile.ResourceValue(new EconomicValue(10.25), DissolutionYield.of(3.25),
                Map.of("test:z", 1.25, "test:a", 2.0), List.of("z", SPECIAL, "a"));
        var resources = new LinkedHashMap<String, EconomyProfile.ResourceValue>();
        resources.put("test:z", value); resources.put("test:a", value); resources.put("test:omitted", null);
        var process = new ProductionGraph.Process("test:z_process", "recipe",
                List.of(new ProductionGraph.Input(List.of("test:z", "test:a", "test:z"), 1.25, true),
                        new ProductionGraph.Input(List.of("test:tool"), 1, false)),
                List.of(new ProductionGraph.Output("test:z", 2, .25, true),
                        new ProductionGraph.Output("test:a", 1, 1, false)),
                20.5, -0.0, "test:provider", .75, Map.of("z", SPECIAL, "a", ""));
        var other = new ProductionGraph.Process("test:a_process", null, List.of(),
                List.of(new ProductionGraph.Output("test:drop", 1, 1, false)), 0, 0, null, 1, Map.of());
        return new EconomyProfile(resources,
                List.of(new EconomyConservationSolver.Invariant("test:z_path", 10.25, 1.5, 2.0, -8.25, true, SPECIAL),
                        new EconomyConservationSolver.Invariant("test:a_path", -0.0, Double.MIN_VALUE, 1e-20, 0, false, null)),
                List.of(process, other), List.of("z", "a", SPECIAL, "z"), 256, new EconomyProcessingPolicy(8750, 9750));
    }

    private static void equivalent(Object source, Action action, boolean compact) throws IOException {
        String expected = render(out -> BalanceDocument.writeCanonical(BalanceDocument.GSON.toJsonTree(source), out), compact);
        String actual = render(action, compact);
        check(actual.equals(expected), source.getClass().getSimpleName() + " differs from canonical tree; compact=" + compact);
    }

    private static String render(Action action, boolean compact) throws IOException {
        var text = new StringWriter();
        try (var output = writer(text, compact)) { action.write(output); }
        return text.toString();
    }

    private static JsonWriter writer(Writer target, boolean compact) throws IOException {
        var output = BalanceDocument.GSON.newJsonWriter(target);
        output.setHtmlSafe(false); output.setSerializeNulls(false);
        if (compact) output.setIndent("");
        return output;
    }

    private static void lazyOutputFailure() throws IOException {
        // This later invalid record fails Gson serialization if the whole section is
        // materialized before the writer sees its prefix. A streaming producer reaches
        // the failed output first and never serializes that record.
        var invalid = new EquipmentReference("test:invalid", "head", ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.ARMOR, Double.NaN), List.of(), true, true, 1, "");
        var source = new PackEvidence(Map.of(), List.of(invalid), List.of(), Map.of(), List.of(), List.of(), Map.of());
        var failing = new Writer() {
            @Override public void write(char[] value, int offset, int length) throws IOException { throw new IOException("test output failure"); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        try {
            GeneratedBalanceSections.writeEvidence(source, writer(failing, true));
            throw new AssertionError("Output failure was not propagated");
        } catch (IOException expected) {
            check(expected.getMessage().equals("test output failure"), "Wrong output failure");
        }
    }

    private static void stress() throws IOException {
        // Run with a small heap: the typed list shares a record, whereas an accidental
        // complete JSON tree allocates distinct maps/primitives for every repeated row.
        var axes = new EnumMap<CapabilityAxis, Double>(CapabilityAxis.class);
        for (var axis : CapabilityAxis.values()) axes.put(axis, 1.0);
        var equipment = new EquipmentReference("test:repeated", "mainhand", ProgressionBand.APEX,
                axes, List.of("one", "two"), true, true, .75, SPECIAL);
        var source = new PackEvidence(Map.of(), Collections.nCopies(100_000, equipment), List.of(),
                Map.of(), List.of(), List.of(), Map.of());
        var counter = new CountingWriter();
        try (var output = writer(counter, true)) { GeneratedBalanceSections.writeEvidence(source, output); }
        check(counter.characters > 100_000_000L, "Stress fixture no longer exercises a large logical section");
        // Keep a previous generation alive while sealing/writing its replacement in the
        // constrained heap. The evidence list is deliberately much larger as JSON than as typed data.
        var previous = source;
        var economy = new EconomyProfile(Map.of(), List.of(), List.of(), List.of(), 0, new EconomyProcessingPolicy(1, 10_000));
        var generated = BalanceDocument.sealGeneratedOwned(envelope(), source, economy);
        var persisted = new CountingWriter();
        generated.writeCompactTo(persisted);
        check(persisted.characters > 100_000_000L, "Complete generated document did not retain every streamed row");
        check(previous.equipment().size() == 100_000, "Previous authority was discarded during generation");
    }

    private static final class CountingWriter extends Writer {
        private long characters;
        @Override public void write(char[] value, int offset, int length) { characters += length; }
        @Override public void write(int value) { characters++; }
        @Override public void write(String value, int offset, int length) { characters += length; }
        @Override public void flush() { }
        @Override public void close() { }
    }

    @FunctionalInterface private interface Action { void write(JsonWriter output) throws IOException; }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
