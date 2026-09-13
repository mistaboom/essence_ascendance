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
        a.getAsJsonObject("runtime").addProperty("value", 90);
        check(!first.integrity().equals(BalanceDocument.seal(a).integrity()), "runtime changes must invalidate digest");
        rejected(() -> BalanceDocument.parse(first.text().replace("\"value\": 1", "\"value\": 9")), "tampering accepted");
        rejected(() -> BalanceDocument.parse(first.text().replace("\"schema\": 1", "\"schema\": 99")), "obsolete schema accepted");
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
