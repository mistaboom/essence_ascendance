package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-light executable invariants; also wired into Gradle check. */
public final class BalanceDocumentTest {
    public static void main(String[] args) throws Exception {
        JsonObject a = content(false), b = content(true);
        BalanceDocument first = BalanceDocument.seal(a);
        check(first.text().equals(BalanceDocument.seal(b).text()), "map insertion order changes profile bytes");
        check(first.text().equals(BalanceDocument.parse(first.text()).text()), "serialization round trip");
        // Exercise the two canonical digest streams with non-ASCII, escaping, nulls,
        // negative zero/exponents and surrogate pairs spanning the encoder buffer.
        var complex = content(false);
        complex.add("evidence", com.google.gson.JsonParser.parseString(
                "{\"z\":null,\"numbers\":[null,1,1.0,-0.0,1e-20],\"nested\":{\"b\":true,\"a\":[]}}"));
        complex.getAsJsonObject("evidence").addProperty("unicode", "x".repeat(65_510) + "\ud83c\udf0d\ud800\n\t\u2028<>&='\"");
        var sealed = BalanceDocument.seal(complex);
        var read = BalanceDocument.parse(sealed.text());
        for (String key : new String[]{"metadata", "settings", "overrides", "evidence", "runtime", "economy", "skills", "validation"})
            check(read.sectionHash(key).equals(BalanceDocument.hash(sealed.section(key))), "Envelope-derived section hash " + key);
        var unsorted = new JsonObject();
        var sorted = com.google.gson.JsonParser.parseString(sealed.text()).getAsJsonObject();
        sorted.entrySet().stream().sorted(java.util.Map.Entry.<String, com.google.gson.JsonElement>comparingByKey().reversed())
                .forEach(entry -> unsorted.add(entry.getKey(), entry.getValue()));
        check(BalanceDocument.parse(BalanceDocument.GSON.toJson(unsorted)).integrity().equals(sealed.integrity()), "Unsorted valid input remains supported");
        a.getAsJsonObject("runtime").addProperty("value", 90);
        check(!first.integrity().equals(BalanceDocument.seal(a).integrity()), "runtime changes must invalidate digest");
        rejected(() -> BalanceDocument.parse(first.text().replace("\"value\": 1", "\"value\": 9")), "tampering accepted");
        check(BalanceDocument.SCHEMA == 2, "current generated profile schema must be two");
        rejected(() -> BalanceDocument.parse(first.text().replace("\"schema\": 2", "\"schema\": 99")), "unknown schema accepted");
        JsonObject oldSchema = com.google.gson.JsonParser.parseString(first.text()).getAsJsonObject();
        oldSchema.addProperty("schema", 1);
        oldSchema.remove("integrity");
        oldSchema.addProperty("integrity", BalanceDocument.hash(oldSchema));
        rejected(() -> BalanceDocument.parse(BalanceDocument.GSON.toJson(oldSchema)), "schema-one profile with valid integrity accepted");
        rejected(() -> BalanceDocument.parse(first.text().replace("\"value\": 1", "\"value\": 99, \"value\": 1")),
                "Ambiguous duplicate members cannot be retained as a validated snapshot");
        Path folder = Files.createTempDirectory("balance-store-test");
        Path target = folder.resolve("generated_balance.json");
        try {
            BalanceProfileStore.replace(target, first);
            String original = Files.readString(target);
            try { BalanceDocument.parse(original.replace("\"value\": 1", "\"value\": 9")); }
            catch (IllegalArgumentException expected) { }
            check(original.equals(Files.readString(target)), "invalid candidate replaced previous valid profile");
            Path impossible = folder.resolve("not-a-directory");
            Files.writeString(impossible, "block");
            try { BalanceProfileStore.replace(impossible.resolve("generated_balance.json"), first); throw new AssertionError("failed write accepted"); }
            catch (java.io.IOException expected) { }
            check(original.equals(Files.readString(target)), "failed IO damaged valid profile");
            BalanceProfileStore.replace(target, BalanceDocument.seal(a));
            check(BalanceProfileStore.read(target).integrity().equals(BalanceDocument.seal(a).integrity()), "atomic replacement lost candidate");
            Files.delete(impossible);
        } finally { Files.deleteIfExists(target); Files.delete(folder); }
        System.out.println("BalanceDocumentTest: deterministic serialization, integrity, schema, round trip, failed-candidate and failed-write preservation PASS");
    }
    private static JsonObject content(boolean reverse) {
        JsonObject root = new JsonObject();
        String[] keys = {"metadata", "settings", "overrides", "evidence", "runtime", "economy", "skills", "validation"};
        for (int i = 0; i < keys.length; i++) {
            JsonObject section = new JsonObject(); section.addProperty("value", 1);
            root.add(keys[reverse ? keys.length - i - 1 : i], section);
        }
        return root;
    }
    private static void rejected(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
