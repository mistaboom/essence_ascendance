package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/** Stable serialization and integrity envelope. Input settings never become runtime overlays. */
public final class BalanceDocument {
    public static final int SCHEMA = 1;
    public static final String GENERATOR = "pack-balance-1";
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Set<String> SECTIONS = Set.of("metadata", "settings", "overrides", "evidence",
            "runtime", "economy", "skills", "validation");
    private final JsonObject document;

    private BalanceDocument(JsonObject document) { this.document = document.deepCopy(); }

    public static BalanceDocument seal(JsonObject content) {
        JsonObject copy = content.deepCopy();
        copy.addProperty("schema", SCHEMA);
        copy.addProperty("generator", GENERATOR);
        copy.remove("integrity");
        copy.addProperty("integrity", hash(canonical(copy)));
        return parse(GSON.toJson(canonical(copy)));
    }

    public static BalanceDocument parse(String text) {
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Generated balance root must be an object");
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("schema") || root.get("schema").getAsInt() != SCHEMA
                || !root.has("generator") || !GENERATOR.equals(root.get("generator").getAsString())) {
            throw new IllegalArgumentException("Incompatible development balance profile; delete it or run /essence debug balance rebuild. No migration is supported");
        }
        for (String key : SECTIONS) {
            if (!root.has(key) || !root.get(key).isJsonObject())
                throw new IllegalArgumentException("Missing generated profile section: " + key);
        }
        for (String key : root.keySet()) {
            if (!SECTIONS.contains(key) && !Set.of("schema", "generator", "integrity").contains(key))
                throw new IllegalArgumentException("Unknown generated profile section: " + key);
        }
        if (!root.has("integrity")) throw new IllegalArgumentException("Missing generated profile integrity");
        String expected = root.remove("integrity").getAsString();
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), hash(canonical(root)).getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("Generated balance integrity mismatch. Edit the TOML inputs and explicitly rebuild; generated JSON editing is unsupported");
        root.addProperty("integrity", expected);
        return new BalanceDocument(root);
    }

    public JsonObject section(String key) { return document.getAsJsonObject(key).deepCopy(); }
    public String integrity() { return document.get("integrity").getAsString(); }
    public String text() { return GSON.toJson(canonical(document)) + "\n"; }

    public static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key -> result.add(key, canonical(value.getAsJsonObject().get(key))));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(canonical(child)));
            return result;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                && !Double.isFinite(value.getAsDouble()))
            throw new IllegalArgumentException("Non-finite generated profile number");
        return value.deepCopy();
    }

    public static String hash(JsonElement value) { return hash(GSON.toJson(canonical(value))); }
    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
