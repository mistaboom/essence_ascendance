package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.mistaboom.essence_ascendance.balance.config.ResourceLocationJsonAdapter;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.io.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Stable serialization and integrity envelope. Input settings never become runtime overlays. */
public final class BalanceDocument {
    public static final int SCHEMA = 1;
    public static final String GENERATOR = "pack-balance-1";
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocationJsonAdapter()).create();
    private static final Set<String> SECTIONS = Set.of("metadata", "settings", "overrides", "evidence",
            "runtime", "economy", "skills", "validation");
    private final JsonObject document;
    private final Map<String, PackedSection> packedSections;
    // A document owns immutable trees; public readers receive copies. Keep only
    // the eight possible digest strings, never external inputs or another tree.
    private final java.util.concurrent.ConcurrentMap<String, String> sectionHashes = new java.util.concurrent.ConcurrentHashMap<>();

    // Only private parsed trees or explicitly transferred trees reach this constructor.
    private BalanceDocument(JsonObject document) { this(document, Map.of()); }
    private BalanceDocument(JsonObject document, Map<String, PackedSection> packedSections) {
        this.document = document;
        this.packedSections = Map.copyOf(packedSections);
    }

    /** Release duplicate JSON trees only after typed validation. Bytes belong to this document, never a file. */
    BalanceDocument compactEvidenceAndEconomy() {
        if (!packedSections.isEmpty()) return this;
        PackedSection evidence, economy;
        try (var phase = BalancePerformance.phase("compact_evidence")) {
            evidence = PackedSection.create(document.getAsJsonObject("evidence"), sectionHash("evidence"));
        }
        try (var phase = BalancePerformance.phase("compact_economy")) {
            economy = PackedSection.create(document.getAsJsonObject("economy"), sectionHash("economy"));
        }
        var packed = Map.of("evidence", evidence, "economy", economy);
        BalancePerformance.count("compacted_evidence_bytes", evidence.compressed.length);
        BalancePerformance.count("compacted_economy_bytes", economy.compressed.length);
        // Public access always copies; the remaining owned immutable sections can be shared safely.
        var retained = new JsonObject();
        document.entrySet().forEach(entry -> {
            if (!packed.containsKey(entry.getKey())) retained.add(entry.getKey(), entry.getValue());
        });
        return new BalanceDocument(retained, packed);
    }

    private static final class PackedSection {
        private final byte[] compressed;
        private final String hash;
        private PackedSection(byte[] compressed, String hash) { this.compressed = compressed; this.hash = hash; }

        static PackedSection create(JsonObject section, String hash) {
            var bytes = new ByteArrayOutputStream();
            try {
                try (var phase = BalancePerformance.phase("section_serialization_gzip")) {
                var stored = BalanceProfileStore.limitedOutput(bytes, BalanceProfileStore.MAX_PROFILE_BYTES, false);
                var bounded = BalanceProfileStore.limitedOutput(new GZIPOutputStream(stored), BalanceProfileStore.MAX_JSON_BYTES, true);
                try (var output = new BufferedWriter(new OutputStreamWriter(bounded, StandardCharsets.UTF_8))) {
                    // JSON can contain isolated surrogate escapes. Preserve those code units internally too.
                    var json = writer(new SurrogateEscapingWriter(output));
                    json.setIndent("");
                    json.setSerializeNulls(true);
                    writeCanonical(section, json);
                    json.flush();
                }
                }
                return new PackedSection(bytes.toByteArray(), hash);
            } catch (IOException error) { throw new UncheckedIOException("Cannot compact validated balance section", error); }
        }

        JsonReader reader() throws IOException {
            return new PooledJsonReader(new InputStreamReader(BalanceProfileStore.limitedInput(
                    new GZIPInputStream(new ByteArrayInputStream(compressed)), BalanceProfileStore.MAX_JSON_BYTES),
                    StandardCharsets.UTF_8.newDecoder()));
        }

        JsonObject readTree() {
            try (var input = reader()) {
                JsonObject value = JsonParser.parseReader(input).getAsJsonObject();
                requireEnd(input);
                return value;
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }
    }

    /** Escaping every surrogate code unit preserves both pairs and isolated units across UTF-8 compression. */
    private static final class SurrogateEscapingWriter extends FilterWriter {
        private static final char[] HEX = "0123456789abcdef".toCharArray();
        SurrogateEscapingWriter(Writer output) { super(output); }
        @Override public void write(int value) throws IOException {
            if (Character.isSurrogate((char) value)) {
                out.write('\\'); out.write('u');
                for (int shift = 12; shift >= 0; shift -= 4) out.write(HEX[(value >> shift) & 15]);
            } else out.write(value);
        }
        @Override public void write(char[] value, int offset, int length) throws IOException {
            int start = offset, end = offset + length;
            for (int index = offset; index < end; index++) if (Character.isSurrogate(value[index])) {
                out.write(value, start, index - start); write(value[index]); start = index + 1;
            }
            out.write(value, start, end - start);
        }
        @Override public void write(String value, int offset, int length) throws IOException {
            int start = offset, end = offset + length;
            for (int index = offset; index < end; index++) if (Character.isSurrogate(value.charAt(index))) {
                out.write(value, start, index - start); write(value.charAt(index)); start = index + 1;
            }
            out.write(value, start, end - start);
        }
    }

    public static BalanceDocument seal(JsonObject content) {
        return sealOwned(content.deepCopy());
    }

    /** Package-private ownership transfer for freshly constructed generation output. */
    static BalanceDocument sealOwned(JsonObject copy) {
        omitNullMembers(copy);
        copy.addProperty("schema", SCHEMA);
        copy.addProperty("generator", GENERATOR);
        copy.remove("integrity");
        copy.addProperty("integrity", hash(copy));
        validateSections(copy);
        return new BalanceDocument(copy);
    }

    public static BalanceDocument parse(String text) {
        return parse(new StringReader(text));
    }

    static BalanceDocument parse(Reader input) {
        BalancePerformance.increment("profile_json_parses");
        var reader = new PooledJsonReader(input);
        JsonElement parsed;
        try (var phase = BalancePerformance.phase("json_parse_and_decompress")) {
            parsed = JsonParser.parseReader(reader);
            try {
                if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
                    throw new IllegalArgumentException("Trailing data after generated balance document");
            } catch (IOException error) { throw new com.google.gson.JsonIOException(error); }
        }
        try (var phase = BalancePerformance.phase("schema_and_integrity_validation")) {
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Generated balance root must be an object");
        JsonObject root = parsed.getAsJsonObject();
        validateSections(root);
        if (!root.has("integrity")) throw new IllegalArgumentException("Missing generated profile integrity");
        String expected = root.remove("integrity").getAsString();
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), hash(root).getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("Generated balance integrity mismatch. Edit the TOML inputs and explicitly rebuild; generated JSON editing is unsupported");
        root.addProperty("integrity", expected);
        return new BalanceDocument(root);
        }
    }

    /** Per-read, bounded pooling avoids retaining a new copy of every repeated evidence string. */
    private static final class PooledJsonReader extends com.google.gson.stream.JsonReader {
        private static final int MAX_CHARACTERS = 8 * 1024 * 1024, MAX_ENTRIES = 65_536;
        private final java.util.LinkedHashMap<String, String> strings = new java.util.LinkedHashMap<>(256, .75f, true);
        private int characters;
        PooledJsonReader(Reader input) { super(input); }
        @Override public String nextName() throws IOException { return pooled(super.nextName()); }
        @Override public String nextString() throws IOException { return pooled(super.nextString()); }
        private String pooled(String value) {
            String existing = strings.get(value);
            if (existing != null) return existing;
            if (value.length() > MAX_CHARACTERS) return value;
            while (!strings.isEmpty() && (strings.size() >= MAX_ENTRIES || characters + value.length() > MAX_CHARACTERS)) {
                var entries = strings.entrySet().iterator();
                characters -= entries.next().getKey().length(); entries.remove();
            }
            strings.put(value, value); characters += value.length();
            return value;
        }
    }

    private static void validateSections(JsonObject root) {
        if (!root.has("schema") || root.get("schema").getAsInt() != SCHEMA
                || !root.has("generator") || !GENERATOR.equals(root.get("generator").getAsString())) {
            throw new IllegalArgumentException("Incompatible development balance profile; delete it or run /essence admin balance rebuild. No migration is supported");
        }
        for (String key : SECTIONS) {
            if (!root.has(key) || !root.get(key).isJsonObject())
                throw new IllegalArgumentException("Missing generated profile section: " + key);
        }
        for (String key : root.keySet()) {
            if (!SECTIONS.contains(key) && !Set.of("schema", "generator", "integrity").contains(key))
                throw new IllegalArgumentException("Unknown generated profile section: " + key);
        }
    }

    public JsonObject section(String key) {
        PackedSection packed = packedSections.get(key);
        return packed == null ? document.getAsJsonObject(key).deepCopy() : packed.readTree();
    }
    public String sectionHash(String key) {
        PackedSection packed = packedSections.get(key);
        if (packed != null) {
            BalancePerformance.increment("section_hash_cache_hits/" + key);
            return packed.hash;
        }
        // Preserve unsupported-key behavior without letting arbitrary public
        // requests grow the cache. No supplied metadata digest seeds this cache.
        if (!SECTIONS.contains(key)) return hash(document.getAsJsonObject(key));
        boolean[] computed = {false};
        String result = sectionHashes.computeIfAbsent(key, section -> {
            computed[0] = true;
            BalancePerformance.increment("section_hash_computations/" + section);
            try (var phase = BalancePerformance.phase("section_canonical_hash")) {
                return hash(document.getAsJsonObject(section));
            }
        });
        if (!computed[0]) BalancePerformance.increment("section_hash_cache_hits/" + key);
        return result;
    }
    /** Typed decoding reads the owned tree without allocating an intermediate tree copy. */
    <T> T decodeSection(String key, Class<T> type) {
        PackedSection packed = packedSections.get(key);
        if (packed == null) return GSON.fromJson(document.get(key), type);
        try (var input = packed.reader()) {
            T value = GSON.fromJson(input, type);
            requireEnd(input);
            return value;
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    void verifySection(String key, Object value) {
        // Active publication verifies before compaction. Explicit later verification may inflate one section.
        PackedSection packed = packedSections.get(key);
        var verifier = new BalanceJsonVerifier(packed == null ? document.get(key) : packed.readTree());
        GSON.toJson(value, value.getClass(), verifier);
        verifier.finish();
    }
    public String integrity() { return document.get("integrity").getAsString(); }
    public String text() {
        var output = new StringWriter();
        try { writeTo(output); } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return output.toString();
    }
    void writeTo(Writer output) throws IOException {
        writeTo(output, false);
    }
    /** Whitespace is not part of the envelope contract; integrity keeps its historical canonical form. */
    void writeCompactTo(Writer output) throws IOException {
        writeTo(output, true);
    }
    private void writeTo(Writer output, boolean compact) throws IOException {
        var json = writer(output);
        if (compact) json.setIndent("");
        if (packedSections.isEmpty()) writeCanonical(document, json);
        else {
            var keys = new TreeSet<>(document.keySet()); keys.addAll(packedSections.keySet());
            json.beginObject();
            for (String key : keys) {
                json.name(key);
                PackedSection packed = packedSections.get(key);
                if (packed == null) writeCanonical(document.get(key), json);
                else try (var input = packed.reader()) { copyCanonical(input, json); requireEnd(input); }
            }
            json.endObject();
        }
        json.flush();
        output.write('\n');
    }
    private static JsonWriter writer(Writer output) throws IOException {
        var json = GSON.newJsonWriter(output);
        json.setHtmlSafe(false);
        json.setSerializeNulls(false);
        return json;
    }
    private static void requireEnd(JsonReader input) throws IOException {
        if (input.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing packed balance section data");
    }
    /** Packed keys were already sorted. Keep token spellings and let the enclosing writer own indentation/null policy. */
    private static void copyCanonical(JsonReader input, JsonWriter output) throws IOException {
        switch (input.peek()) {
            case BEGIN_OBJECT -> {
                input.beginObject(); output.beginObject();
                while (input.hasNext()) { output.name(input.nextName()); copyCanonical(input, output); }
                input.endObject(); output.endObject();
            }
            case BEGIN_ARRAY -> {
                input.beginArray(); output.beginArray();
                while (input.hasNext()) copyCanonical(input, output);
                input.endArray(); output.endArray();
            }
            case STRING -> output.value(input.nextString());
            case NUMBER -> {
                var number = new LazilyParsedNumber(input.nextString());
                if (!Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Non-finite generated profile number");
                output.value(number);
            }
            case BOOLEAN -> output.value(input.nextBoolean());
            case NULL -> { input.nextNull(); output.nullValue(); }
            default -> throw new IllegalArgumentException("Invalid packed balance section token: " + input.peek());
        }
    }
    private static void omitNullMembers(JsonElement value) {
        if (value.isJsonObject()) {
            var entries = value.getAsJsonObject().entrySet().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (entry.getValue().isJsonNull()) entries.remove();
                else omitNullMembers(entry.getValue());
            }
        } else if (value.isJsonArray()) for (var child : value.getAsJsonArray()) omitNullMembers(child);
    }
    private static void writeCanonical(JsonElement value, JsonWriter output) throws IOException {
        if (value.isJsonObject()) {
            output.beginObject();
            for (String key : value.getAsJsonObject().keySet().stream().sorted().toList()) {
                output.name(key);
                writeCanonical(value.getAsJsonObject().get(key), output);
            }
            output.endObject();
        } else if (value.isJsonArray()) {
            output.beginArray();
            for (var child : value.getAsJsonArray()) writeCanonical(child, output);
            output.endArray();
        } else if (value.isJsonNull()) output.nullValue();
        else {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                if (!Double.isFinite(primitive.getAsDouble())) throw new IllegalArgumentException("Non-finite generated profile number");
                output.value(primitive.getAsNumber());
            } else if (primitive.isBoolean()) output.value(primitive.getAsBoolean());
            else output.value(primitive.getAsString());
        }
    }

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

    public static String hash(JsonElement value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output = new OutputStreamWriter(new DigestOutputStream(OutputStream.nullOutputStream(), digest), StandardCharsets.UTF_8)) {
                var json = writer(output);
                writeCanonical(value, json);
                json.flush();
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
