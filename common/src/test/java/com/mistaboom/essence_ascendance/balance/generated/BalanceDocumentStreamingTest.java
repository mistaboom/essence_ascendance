package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Runs with 128 MiB heap; exercises disk formats beyond the real pack's failed size. */
public final class BalanceDocumentStreamingTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("report-only")) { spreadsheetStreaming(); return; }
        if (args.length == 1 && args[0].equals("compact-only")) { packedSectionContract(); typedVerification(); return; }
        packedSectionContract();
        typedVerification();
        for (String value : List.of("{\"z\":null,\"a\":[null,true,1,1.0,-0.0,1e-20]}",
                "{\"unicode\":\"\\u2728\\u6f22\\u2028<>&='\\n\\t\\\"\",\"nested\":{\"b\":2,\"a\":1}}")) {
            var tree = JsonParser.parseString(value);
            check(BalanceDocument.hash(tree).equals(BalanceDocument.hash(BalanceDocument.GSON.toJson(BalanceDocument.canonical(tree)))),
                    "Streaming digest must preserve historical Gson bytes");
        }
        var root = content();
        root.getAsJsonObject("runtime").add("explicitNull", JsonNull.INSTANCE);
        var small = BalanceDocument.seal(root);
        check(small.text().equals(BalanceDocument.GSON.toJson(BalanceDocument.canonical(JsonParser.parseString(small.text()))) + "\n"),
                "Canonical text compatibility");
        root.getAsJsonObject("runtime").addProperty("changed", true);
        check(!small.section("runtime").has("changed"), "Public sealing retains input isolation");
        var section = small.section("runtime"); section.addProperty("changed", true);
        check(!small.section("runtime").has("changed"), "Public section retains output isolation");
        try { BalanceDocument.parse(small.text() + "{}"); throw new AssertionError("Trailing document accepted"); }
        catch (RuntimeException expectedTrailing) { check(true, "Pooled reader rejects trailing documents"); }

        String payload = "x".repeat(1024 * 1024);
        var large = new JsonObject();
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        try (var writer = new OutputStreamWriter(new DigestOutputStream(OutputStream.nullOutputStream(), expected), StandardCharsets.UTF_8)) {
            writer.write("{\n");
            for (int n = 0; n < 192; n++) {
                String key = String.format(Locale.ROOT, "item%03d", n);
                large.addProperty(key, payload);
                writer.write("  \"" + key + "\": \""); writer.write(payload);
                writer.write(n == 191 ? "\"\n" : "\",\n");
            }
            writer.write("}");
        }
        check(BalanceDocument.hash(large).equals(HexFormat.of().formatHex(expected.digest())), "192 MiB hash in constrained heap");
        var largeRoot = content(); largeRoot.add("evidence", large);
        var document = BalanceDocument.seal(largeRoot);
        Path folder = Files.createTempDirectory("balance-stream-test-");
        Path target = folder.resolve("generated_balance.json");
        Path live = BalanceProfileStore.profilePath(folder);
        Path compressed = folder.resolve("failure.json.gz");
        try {
            BalanceProfileStore.replace(target, small);
            BalanceProfileStore.replace(live, small);
            check(BalanceProfileStore.read(live).integrity().equals(small.integrity()), "Current-schema compressed profile round trip");
            check(BalanceProfileStore.read(target).integrity().equals(small.integrity()), "Compact storage retains historical canonical integrity");
            check(Files.size(target) < small.text().getBytes(StandardCharsets.UTF_8).length, "Compact storage omits formatting only");
            BalanceProfileStore.writeDiagnostic(compressed, small);
            check(BalanceProfileStore.read(compressed).integrity().equals(small.integrity()), "Compressed diagnostic round trip");
            byte[] gzip = Files.readAllBytes(compressed);
            Files.write(compressed, Arrays.copyOf(gzip, gzip.length - 4));
            try { BalanceProfileStore.read(compressed); throw new AssertionError("Truncated gzip trailer accepted"); }
            catch (IOException | JsonParseException expectedCorrupt) { check(true, "Read validates the gzip trailer"); }
            Files.write(target, new byte[]{(byte) 0xff});
            try { BalanceProfileStore.read(target); throw new AssertionError("Invalid UTF-8 accepted"); }
            catch (IOException | JsonParseException expectedCorrupt) { check(true, "Invalid UTF-8 fails closed"); }
            try (var bounded = BalanceProfileStore.limitedInput(new ByteArrayInputStream(new byte[17]), 16)) {
                try { bounded.transferTo(OutputStream.nullOutputStream()); throw new AssertionError("Inflation bound ignored"); }
                catch (IOException expectedLimit) { check(expectedLimit.getMessage().contains("Inflated"), "Inflated input is bounded"); }
            }
            String unicode = "text \u2728 \uD83C\uDF0D \uD800";
            BalanceProfileStore.writeAtomically(target, unicode);
            check(Arrays.equals(unicode.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target)), "UTF-8 encoder completes before durable commit");
            BalanceProfileStore.replace(target, document);
            check(Files.size(target) > 192L * 1024 * 1024, "192 MiB atomic streaming write");
            String previous = fileHash(target);
            for (int n = 192; n < 300; n++) large.addProperty(String.format(Locale.ROOT, "item%03d", n), payload);
            var oversized = BalanceDocument.seal(largeRoot);
            BalanceProfileStore.writeDiagnostic(compressed, oversized);
            check(Files.size(compressed) < 1024 * 1024, "300 MiB diagnostic streams to compressed storage with 128 MiB heap");
            try (var inflated = new java.util.zip.GZIPInputStream(Files.newInputStream(compressed))) {
                check(inflated.transferTo(OutputStream.nullOutputStream()) > 300L * 1024 * 1024,
                        "Oversized diagnostic retained full JSON and valid gzip trailer");
            }
            try { BalanceProfileStore.replace(target, oversized); throw new AssertionError("Oversized output accepted"); }
            catch (IOException expectedLimit) { check(expectedLimit.getMessage().contains("256 MiB"), "Unchanged output bound"); }
            check(previous.equals(fileHash(target)), "Oversized streamed candidate preserves prior file");
            for (int n = 300; n < 1100; n++) large.addProperty(String.format(Locale.ROOT, "item%04d", n), payload);
            var beyondPackFailure = BalanceDocument.seal(largeRoot);
            BalanceProfileStore.replace(live, beyondPackFailure);
            check(Files.size(live) < 2L * 1024 * 1024, "1100 MiB live profile is losslessly compressed");
            check(BalanceProfileStore.read(live).integrity().equals(beyondPackFailure.integrity()),
                    "1100 MiB live profile reopens and verifies in 128 MiB heap using bounded string pooling");
            String originalPackedFile = fileHash(live);
            var compacted = beyondPackFailure.compactEvidenceAndEconomy();
            check(compacted.integrity().equals(beyondPackFailure.integrity())
                            && compacted.sectionHash("evidence").equals(beyondPackFailure.sectionHash("evidence")),
                    "1100 MiB in-memory compaction preserves envelope and section digests in 128 MiB heap");
            BalanceProfileStore.replace(live, compacted);
            check(fileHash(live).equals(originalPackedFile),
                    "1100 MiB compacted document streams byte-identical full compressed profile without a section tree");
            try (var inflated = new java.util.zip.GZIPInputStream(Files.newInputStream(live))) {
                check(inflated.transferTo(OutputStream.nullOutputStream()) > 1100L * 1024 * 1024,
                        "Live profile retains all logical bytes and a valid gzip trailer");
            }
            String previousDiagnostic = fileHash(compressed);
            for (int n = 1100; n < 4097; n++) large.addProperty(String.format(Locale.ROOT, "item%04d", n), payload);
            var beyondInflatedBound = BalanceDocument.seal(largeRoot);
            try { BalanceProfileStore.writeDiagnostic(compressed, beyondInflatedBound); throw new AssertionError("Oversized diagnostic accepted"); }
            catch (IOException expectedLimit) { check(expectedLimit.getMessage().contains("4096 MiB"), "Compressed logical output is bounded"); }
            try { beyondInflatedBound.compactEvidenceAndEconomy(); throw new AssertionError("Oversized packed section accepted"); }
            catch (UncheckedIOException expectedLimit) { check(expectedLimit.getCause().getMessage().contains("4096 MiB"), "In-memory compaction retains inflated section bound"); }
            check(previousDiagnostic.equals(fileHash(compressed)), "Failed compressed replacement retains previous diagnostic");
            try (var files = Files.list(folder)) { check(files.count() == 3, "Temporary failed output cleaned up"); }
        } finally { Files.deleteIfExists(target); Files.deleteIfExists(compressed); Files.deleteIfExists(live); Files.delete(folder); }
        spreadsheetStreaming();
        System.out.println("BalanceDocumentStreamingTest: " + checks + " checks PASS; 1100 MiB live profile saved/reopened/compacted byte-exactly and 312 MiB CSV exported in 128 MiB heap; 256 MiB stored / 4 GiB inflated bounds preserve previous files");
    }
    private static void packedSectionContract() throws Exception {
        var root = content();
        root.add("evidence", JsonParser.parseString("{\"numbers\":[1,1.0,-0.0,1e3,1e-20],\"nested\":{\"z\":false,\"a\":[null,true]}}"));
        root.getAsJsonObject("evidence").addProperty("text", "emoji " + new String(new char[]{0xd83c, 0xdf0d})
                + " isolated " + (char) 0xd800 + " <>&='\n\t\" line " + (char) 0x2028);
        root.add("economy", JsonParser.parseString("{\"z\":{\"a\":2.50},\"a\":[\"first\",\"last\"]}"));
        var sealed = BalanceDocument.seal(root);
        // Valid parsed documents can retain explicit null object members. Historical integrity/output omits them.
        var incoming = JsonParser.parseString(sealed.text()).getAsJsonObject();
        incoming.getAsJsonObject("evidence").add("explicitNull", JsonNull.INSTANCE);
        var original = BalanceDocument.parse(new GsonBuilder().serializeNulls().disableHtmlEscaping().create().toJson(incoming));
        var compacted = original.compactEvidenceAndEconomy();
        check(compacted.integrity().equals(original.integrity()), "Compaction preserves existing canonical envelope integrity");
        check(compacted.text().equals(original.text()), "Compaction preserves every pretty output character, including numeric spellings and surrogates");
        var beforeCompact = new StringWriter(); original.writeCompactTo(beforeCompact);
        var afterCompact = new StringWriter(); compacted.writeCompactTo(afterCompact);
        check(afterCompact.toString().equals(beforeCompact.toString()), "Compaction preserves every compact output character");
        for (String key : List.of("evidence", "economy", "runtime", "metadata")) {
            check(compacted.section(key).equals(original.section(key)), "Packed and retained public section contents are unchanged: " + key);
            check(compacted.sectionHash(key).equals(original.sectionHash(key)), "Packed and retained section hashes are unchanged: " + key);
        }
        check(compacted.section("evidence").get("explicitNull").isJsonNull(), "Private compression preserves public null members");
        compacted.section("evidence").addProperty("changed", true);
        check(!compacted.section("evidence").has("changed") && compacted.text().equals(original.text()),
                "Inflated public sections are isolated from document ownership");
        check(compacted.compactEvidenceAndEconomy() == compacted, "Repeated compaction is idempotent");
        var documentField = BalanceDocument.class.getDeclaredField("document"); documentField.setAccessible(true);
        var retained = (JsonObject) documentField.get(compacted);
        check(!retained.has("evidence") && !retained.has("economy") && retained.has("runtime"),
                "Active document no longer retains duplicate evidence/economy JSON trees");

        Path folder = Files.createTempDirectory("balance-packed-section-");
        Path gzip = folder.resolve("profile.json.gz"), plain = folder.resolve("profile.json");
        try {
            BalanceProfileStore.replace(gzip, original); String expected = fileHash(gzip);
            BalanceProfileStore.replace(gzip, compacted);
            check(fileHash(gzip).equals(expected), "Existing gzip writer remains byte-identical after compaction");
            BalanceProfileStore.replace(plain, compacted);
            check(BalanceProfileStore.read(plain).integrity().equals(original.integrity()), "Compacted plain storage round trip");
            var detached = BalanceProfileStore.read(gzip).compactEvidenceAndEconomy();
            Files.delete(gzip); Files.delete(plain);
            // Disk serialization has always omitted explicit nulls and used UTF-8 replacement for isolated surrogates.
            check(detached.integrity().equals(original.integrity()), "Compacted gzip storage round trip retains historical integrity");
            check(!detached.section("economy").isEmpty(), "Compacted sections remain readable after source file removal");
        } finally { Files.deleteIfExists(gzip); Files.deleteIfExists(plain); Files.delete(folder); }
        System.out.println("Packed balance sections: exact output/digests, nulls/numbers/Unicode, detached ownership and API contracts PASS");
    }
    private static void spreadsheetStreaming() throws Exception {
        Path folder = Files.createTempDirectory("balance-report-stream-");
        try {
            var reports = new SpreadsheetReports();
            var table = reports.table("large.csv", "detail");
            String cell = "y".repeat(16_384);
            var expected = MessageDigest.getInstance("SHA-256");
            try (var output = new OutputStreamWriter(new DigestOutputStream(OutputStream.nullOutputStream(), expected), StandardCharsets.UTF_8)) {
                output.write("\"detail\"\n");
                for (int n = 0; n < 20_000; n++) {
                    table.row(cell);
                    output.write('"'); output.write(cell); output.write("\"\n");
                }
            }
            reports.write(folder.resolve("reports"), folder.resolve("diagnostics"));
            Path csv = folder.resolve("reports/large.csv");
            check(Files.size(csv) > 300L * 1024 * 1024, "Full large CSV is retained");
            check(fileHash(csv).equals(HexFormat.of().formatHex(expected.digest())), "Streamed CSV matches every expected byte");
            System.out.println("Spreadsheet streaming: 312 MiB complete CSV PASS with 128 MiB heap");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static JsonObject content() {
        var root = new JsonObject();
        for (String key : List.of("metadata", "settings", "overrides", "evidence", "runtime", "economy", "skills", "validation"))
            root.add(key, new JsonObject());
        return root;
    }
    private record EvidenceRow(String id, double amount, boolean reachable, List<String> dependencies, String absent) { }
    private static void typedVerification() {
        var row = new EvidenceRow("test:unicode_\u2728", 1.25, true, Arrays.asList("test:input", null), null);
        var root = content();
        root.add("evidence", BalanceDocument.GSON.toJsonTree(row));
        var document = BalanceDocument.sealOwned(root);
        document.verifySection("evidence", row);
        check(true, "Typed output matches nested arrays, nulls, numeric and Unicode values");
        var compacted = document.compactEvidenceAndEconomy();
        compacted.verifySection("evidence", row);
        check(compacted.decodeSection("evidence", EvidenceRow.class).equals(row),
                "Packed typed decoding and verification preserve the same record contents");
        for (var changed : List.of(new EvidenceRow("other", 1.25, true, row.dependencies(), null),
                new EvidenceRow(row.id(), 2, true, row.dependencies(), null),
                new EvidenceRow(row.id(), 1.25, false, row.dependencies(), null),
                new EvidenceRow(row.id(), 1.25, true, List.of("test:input"), null),
                new EvidenceRow(row.id(), 1.25, true, row.dependencies(), "unexpected"))) {
            try { document.verifySection("evidence", changed); throw new AssertionError("Typed mismatch accepted"); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().contains("differs"), "Typed mismatch rejected"); }
            try { compacted.verifySection("evidence", changed); throw new AssertionError("Packed typed mismatch accepted"); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().contains("differs"), "Packed typed mismatch rejected"); }
        }
        // Repeated immutable source records model large evidence without allocating
        // a second JSON object for each row. The old toJsonTree check exceeds this heap.
        var rows = new ArrayList<EvidenceRow>();
        var jsonRows = new JsonArray();
        var jsonRow = BalanceDocument.GSON.toJsonTree(row);
        for (int n = 0; n < 500_000; n++) { rows.add(row); jsonRows.add(jsonRow); }
        var bulk = content(); bulk.getAsJsonObject("evidence").add("rows", jsonRows);
        BalanceDocument.sealOwned(bulk).verifySection("evidence", Map.of("rows", rows));
        check(true, "500,000 typed evidence rows verified without a second tree under 128 MiB");
    }
    private static String fileHash(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(path), digest)) { input.transferTo(OutputStream.nullOutputStream()); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
