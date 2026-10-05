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
    public static final int SCHEMA = 2;
    public static final String GENERATOR = "pack-balance-1";
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocationJsonAdapter()).create();
    private static final Set<String> SECTIONS = Set.of("metadata", "settings", "overrides", "evidence",
            "runtime", "economy", "skills", "validation");
    private final JsonObject document;
    private final Map<String, PackedSection> packedSections;
    private final byte[] sourceGzip;
    // A document owns immutable trees; public readers receive copies. Keep only
    // the eight possible digest strings, never external inputs or another tree.
    private final java.util.concurrent.ConcurrentMap<String, String> sectionHashes = new java.util.concurrent.ConcurrentHashMap<>();

    // Only private parsed trees or explicitly transferred trees reach this constructor.
    private BalanceDocument(JsonObject document) { this(document, Map.of(), null); }
    private BalanceDocument(JsonObject document, Map<String, PackedSection> packedSections, byte[] sourceGzip) {
        this.document = document;
        this.packedSections = Map.copyOf(packedSections);
        this.sourceGzip = sourceGzip;
    }

    /** Release duplicate JSON trees only after typed validation. Bytes belong to this document, never a file. */
    BalanceDocument compactEvidenceAndEconomy() {
        if (!packedSections.isEmpty()) return this;
        if (sourceGzip != null) {
            return compactFromSnapshot(sourceGzip);
        }
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
        return new BalanceDocument(retained, packed, null);
    }

    /** Store-only ownership transfer of the validated canonical bytes just read or committed. */
    BalanceDocument compactFromSnapshot(byte[] snapshot) {
        var packed = Map.of("evidence", new PackedSection(snapshot, sectionHashes.get("evidence"), "evidence"),
                "economy", new PackedSection(snapshot, sectionHashes.get("economy"), "economy"));
        var retained = new JsonObject();
        document.entrySet().forEach(entry -> {
            if (!packed.containsKey(entry.getKey())) retained.add(entry.getKey(), entry.getValue());
        });
        BalancePerformance.increment("profile_compressed_snapshot_reuses");
        BalancePerformance.count("retained_profile_snapshot_bytes", snapshot.length);
        return new BalanceDocument(retained, packed, null);
    }

    private static final class PackedSection {
        private final byte[] compressed;
        private volatile String hash;
        private volatile String verificationHash;
        private final String sourceSection;
        private PackedSection(byte[] compressed, String hash) { this(compressed, hash, null); }
        private PackedSection(byte[] compressed, String hash, String sourceSection) {
            this(compressed, hash, sourceSection, null);
        }
        private PackedSection(byte[] compressed, String hash, String sourceSection, String verificationHash) {
            this.compressed = compressed; this.hash = hash; this.sourceSection = sourceSection;
            this.verificationHash = verificationHash;
        }

        static PackedSection create(JsonObject section, String hash) {
            var bytes = new ByteArrayOutputStream();
            try {
                try (var phase = BalancePerformance.phase("section_serialization_gzip")) {
                var stored = BalanceProfileStore.limitedOutput(bytes, BalanceProfileStore.MAX_PROFILE_BYTES, false);
                var bounded = BalanceProfileStore.limitedOutput(new GZIPOutputStream(stored), BalanceProfileStore.MAX_JSON_BYTES, true);
                try (var output = new BalanceJsonBuffer(new OutputStreamWriter(bounded, StandardCharsets.UTF_8))) {
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

        /** Canonicalize one typed record at a time; never construct the whole section tree. */
        static PackedSection create(SectionWriter action) {
            var bytes = new ByteArrayOutputStream();
            try {
                var digest = MessageDigest.getInstance("SHA-256");
                var verification = MessageDigest.getInstance("SHA-256");
                var stored = BalanceProfileStore.limitedOutput(bytes, BalanceProfileStore.MAX_PROFILE_BYTES, false);
                var bounded = BalanceProfileStore.limitedOutput(new GZIPOutputStream(stored), BalanceProfileStore.MAX_JSON_BYTES, true);
                try (var output = new BalanceJsonBuffer(new OutputStreamWriter(
                             new DigestOutputStream(bounded, verification), StandardCharsets.UTF_8));
                     var hashing = new BalanceJsonBuffer(new OutputStreamWriter(
                              new DigestOutputStream(OutputStream.nullOutputStream(), digest), StandardCharsets.UTF_8))) {
                    // The persisted-size limit counts compact, surrogate-safe bytes. The
                    // historical section digest still sees the exact original pretty form.
                    var compact = writer(new SurrogateEscapingWriter(output));
                    compact.setIndent("");
                    var json = new TeeJsonWriter(compact, writer(hashing));
                    action.write(json); json.flush();
                }
                return new PackedSection(bytes.toByteArray(), HexFormat.of().formatHex(digest.digest()), null,
                        HexFormat.of().formatHex(verification.digest()));
            } catch (IOException error) { throw new UncheckedIOException("Cannot serialize generated balance section", error); }
            catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }

        JsonReader reader() throws IOException {
            var input = new PooledJsonReader(new InputStreamReader(BalanceProfileStore.limitedInput(
                    new GZIPInputStream(new ByteArrayInputStream(compressed)), BalanceProfileStore.MAX_JSON_BYTES),
                    StandardCharsets.UTF_8.newDecoder()));
            if (sourceSection != null) {
                input.beginObject();
                while (input.hasNext()) {
                    if (input.nextName().equals(sourceSection)) return input;
                    input.skipValue();
                }
                input.close();
                throw new IllegalStateException("Validated snapshot lost section " + sourceSection);
            }
            return input;
        }

        void finish(JsonReader input) throws IOException {
            // The complete original stream/trailer was checked on read, before publication.
            if (sourceSection == null) requireEnd(input);
        }

        JsonObject readTree() {
            try (var input = reader()) {
                JsonObject value = JsonParser.parseReader(input).getAsJsonObject();
                finish(input);
                return value;
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }

        String verificationHash() {
            if (verificationHash == null) synchronized (this) {
                if (verificationHash == null) verificationHash = hashWritten(json -> {
                    try (var input = reader()) { copyCanonical(input, json); finish(input); }
                }, true);
            }
            return verificationHash;
        }
    }

    @FunctionalInterface
    private interface SectionWriter { void write(JsonWriter output) throws IOException; }

    /** One canonical token traversal, with independent storage and historical-hash formatting. */
    private static final class TeeJsonWriter extends JsonWriter {
        private final JsonWriter storage, hashing;
        TeeJsonWriter(JsonWriter storage, JsonWriter hashing) {
            super(Writer.nullWriter()); this.storage = storage; this.hashing = hashing;
        }
        @Override public JsonWriter beginObject() throws IOException { storage.beginObject(); hashing.beginObject(); return this; }
        @Override public JsonWriter endObject() throws IOException { storage.endObject(); hashing.endObject(); return this; }
        @Override public JsonWriter beginArray() throws IOException { storage.beginArray(); hashing.beginArray(); return this; }
        @Override public JsonWriter endArray() throws IOException { storage.endArray(); hashing.endArray(); return this; }
        @Override public JsonWriter name(String name) throws IOException { storage.name(name); hashing.name(name); return this; }
        @Override public JsonWriter nullValue() throws IOException { storage.nullValue(); hashing.nullValue(); return this; }
        @Override public JsonWriter value(String value) throws IOException { storage.value(value); hashing.value(value); return this; }
        @Override public JsonWriter value(boolean value) throws IOException { storage.value(value); hashing.value(value); return this; }
        @Override public JsonWriter value(Boolean value) throws IOException { storage.value(value); hashing.value(value); return this; }
        @Override public JsonWriter value(double value) throws IOException { storage.value(value); hashing.value(value); return this; }
        @Override public JsonWriter value(long value) throws IOException { storage.value(value); hashing.value(value); return this; }
        @Override public JsonWriter value(Number value) throws IOException { storage.value(value); hashing.value(value); return this; }
        @Override public JsonWriter jsonValue(String value) throws IOException { storage.jsonValue(value); hashing.jsonValue(value); return this; }
        @Override public void flush() throws IOException { storage.flush(); hashing.flush(); }
        @Override public void close() throws IOException { try { storage.close(); } finally { hashing.close(); } }
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

    /** Generation owns the small envelope; large sections are transient compressed views of that same authority. */
    static BalanceDocument sealGeneratedOwned(JsonObject content,
            com.mistaboom.essence_ascendance.balance.engine.PackEvidence evidence,
            com.mistaboom.essence_ascendance.balance.economy.EconomyProfile economy) {
        if (content.has("evidence") || content.has("economy"))
            throw new IllegalArgumentException("Generated large sections must not be materialized as JSON trees");
        PackedSection packedEvidence, packedEconomy;
        try (var phase = BalancePerformance.phase("evidence_streaming_serialization")) {
            packedEvidence = PackedSection.create(json -> GeneratedBalanceSections.writeEvidence(evidence, json));
        }
        try (var phase = BalancePerformance.phase("economy_streaming_serialization")) {
            packedEconomy = PackedSection.create(json -> GeneratedBalanceSections.writeEconomy(economy, json));
        }
        content.getAsJsonObject("metadata").addProperty("evidenceDigest", packedEvidence.hash);
        content.addProperty("schema", SCHEMA); content.addProperty("generator", GENERATOR); content.remove("integrity");
        omitNullMembers(content);
        // Validate the complete envelope shape without creating large placeholder trees.
        content.add("evidence", new JsonObject()); content.add("economy", new JsonObject());
        validateSections(content); content.remove("evidence"); content.remove("economy");
        var document = new BalanceDocument(content, Map.of("evidence", packedEvidence, "economy", packedEconomy), null);
        document.sectionHashes.put("evidence", packedEvidence.hash); document.sectionHashes.put("economy", packedEconomy.hash);
        try (var phase = BalancePerformance.phase("seal_integrity")) {
            content.addProperty("integrity", hashWritten(document::writeContents));
        }
        BalancePerformance.count("generated_evidence_snapshot_bytes", packedEvidence.compressed.length);
        BalancePerformance.count("generated_economy_snapshot_bytes", packedEconomy.compressed.length);
        return document;
    }

    public static BalanceDocument parse(String text) {
        return parse(new StringReader(text));
    }

    static BalanceDocument parse(Reader input) {
        return parse(input, null);
    }

    /** Ownership transfer from the bounded store read; bytes are never exposed or mutated. */
    static BalanceDocument parse(Reader input, byte[] sourceGzip) {
        BalancePerformance.increment("profile_json_parses");
        var reader = new PooledJsonReader(input);
        JsonElement parsed;
        try (var phase = BalancePerformance.phase("json_parse_and_decompress")) {
            // A retained source must have the same unambiguous members as the validated
            // tree. Gson's ordinary tree adapter silently overwrites duplicate names.
            try { parsed = readUniqueTree(reader); }
            catch (IOException error) { throw new com.google.gson.JsonIOException(error); }
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
        boolean[] canonicalOrder = {true};
        Map<String, String> actualSections = new java.util.HashMap<>();
        String actual = envelopeHash(root, canonicalOrder, actualSections);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("Generated balance integrity mismatch. Edit the TOML inputs and explicitly rebuild; generated JSON editing is unsupported");
        root.addProperty("integrity", expected);
        // Only an already canonical source may later be streamed without sorting.
        var result = new BalanceDocument(root, Map.of(), canonicalOrder[0] ? sourceGzip : null);
        // These digests came from the validated bytes, never the supplied metadata.
        result.sectionHashes.putAll(actualSections);
        return result;
        }
    }

    /** Reject ambiguous members before a validated source can be retained and streamed later. */
    private static JsonElement readUniqueTree(JsonReader input) throws IOException {
        return switch (input.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); input.beginObject();
                while (input.hasNext()) {
                    String name = input.nextName();
                    if (object.has(name)) throw new IllegalArgumentException("Duplicate generated profile member: " + name);
                    object.add(name, readUniqueTree(input));
                }
                input.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); input.beginArray();
                while (input.hasNext()) array.add(readUniqueTree(input));
                input.endArray(); yield array;
            }
            case STRING -> new com.google.gson.JsonPrimitive(input.nextString());
            case NUMBER -> new com.google.gson.JsonPrimitive(new LazilyParsedNumber(input.nextString()));
            case BOOLEAN -> new com.google.gson.JsonPrimitive(input.nextBoolean());
            case NULL -> { input.nextNull(); yield com.google.gson.JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Invalid generated profile token " + input.peek());
        };
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
            throw new IllegalArgumentException("Unsupported generated balance schema or generator; schema 2 is required. Preserve generated_balance.json.gz for diagnosis, then explicitly rebuild with /essence admin balance rebuild or remove that generated file while the game is closed. No migration is supported");
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
            if (packed.hash == null) synchronized (packed) {
                if (packed.hash == null) {
                    BalancePerformance.increment("section_hash_computations/" + key);
                    packed.hash = hash(packed.readTree());
                }
            }
            else BalancePerformance.increment("section_hash_cache_hits/" + key);
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
            packed.finish(input);
            return value;
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    void verifySection(String key, Object value) {
        if (packedSections.containsKey(key) && value instanceof com.mistaboom.essence_ascendance.balance.engine.PackEvidence evidence) {
            if (!packedSections.get(key).verificationHash().equals(
                    hashWritten(json -> GeneratedBalanceSections.writeEvidence(evidence, json), true)))
                throw new IllegalArgumentException("Evidence serialization differs from generated section");
            return;
        }
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
        // UTF-8 encoders replace isolated surrogate code units. Escape them in stored
        // JSON so the authoritative bytes preserve the exact validated Java strings.
        writeTo(new SurrogateEscapingWriter(output), true);
    }
    private void writeTo(Writer output, boolean compact) throws IOException {
        var json = writer(output);
        if (compact) json.setIndent("");
        writeContents(json);
        json.flush();
        output.write('\n');
    }
    private void writeContents(JsonWriter json) throws IOException {
        if (packedSections.isEmpty()) writeCanonical(document, json);
        else {
            var keys = new TreeSet<>(document.keySet()); keys.addAll(packedSections.keySet());
            json.beginObject();
            for (String key : keys) {
                json.name(key);
                PackedSection packed = packedSections.get(key);
                if (packed == null) writeCanonical(document.get(key), json);
                else try (var input = packed.reader()) { copyCanonical(input, json); packed.finish(input); }
            }
            json.endObject();
        }
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
    static void writeCanonical(JsonElement value, JsonWriter output) throws IOException {
        writeCanonical(value, output, null);
    }
    private static void writeCanonical(JsonElement value, JsonWriter output, boolean[] canonicalOrder) throws IOException {
        if (value.isJsonObject()) {
            output.beginObject();
            var keys = value.getAsJsonObject().keySet().toArray(String[]::new);
            boolean sorted = true;
            for (int i = 1; i < keys.length; i++) if (keys[i - 1].compareTo(keys[i]) > 0) { sorted = false; break; }
            if (!sorted) {
                java.util.Arrays.sort(keys);
                if (canonicalOrder != null) canonicalOrder[0] = false;
            }
            for (String key : keys) {
                output.name(key);
                writeCanonical(value.getAsJsonObject().get(key), output, canonicalOrder);
            }
            output.endObject();
        } else if (value.isJsonArray()) {
            output.beginArray();
            for (var child : value.getAsJsonArray()) writeCanonical(child, output, canonicalOrder);
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
        return hash(value, null);
    }

    /** Hash each section while it is already being serialized for envelope integrity.
     * Root indentation adds two spaces to each canonical section line. Section hashes
     * see the original unindented stream; the envelope sees the historically exact bytes. */
    private static String envelopeHash(JsonObject root, boolean[] canonicalOrder, Map<String, String> sections) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output = new BalanceJsonBuffer(new OutputStreamWriter(
                    new DigestOutputStream(OutputStream.nullOutputStream(), digest), StandardCharsets.UTF_8))) {
                output.write('{'); boolean first = true;
                for (String key : new TreeSet<>(root.keySet())) {
                    JsonElement value = root.get(key);
                    if (value.isJsonNull()) continue; // Historical Gson object-null policy.
                    if (!first) output.write(','); first = false;
                    output.write("\n  "); output.write(GSON.toJson(key)); output.write(": ");
                    var sectionDigest = MessageDigest.getInstance("SHA-256");
                    try (var section = new BalanceJsonBuffer(new OutputStreamWriter(
                            new DigestOutputStream(OutputStream.nullOutputStream(), sectionDigest), StandardCharsets.UTF_8))) {
                        var json = writer(new IndentedTee(output, section));
                        writeCanonical(value, json, canonicalOrder); json.flush();
                    }
                    if (SECTIONS.contains(key)) {
                        sections.put(key, HexFormat.of().formatHex(sectionDigest.digest()));
                        BalancePerformance.increment("section_hash_computations/" + key);
                    }
                }
                if (!first) output.write('\n'); output.write('}');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static final class IndentedTee extends Writer {
        private final Writer root, section;
        private final boolean indentRoot;
        IndentedTee(Writer root, Writer section) { this(root, section, true); }
        IndentedTee(Writer root, Writer section, boolean indentRoot) { this.root = root; this.section = section; this.indentRoot = indentRoot; }
        @Override public void write(int value) throws IOException {
            root.write(value); section.write(value);
            if (indentRoot && value == '\n') root.write("  ");
        }
        @Override public void write(String value, int offset, int length) throws IOException {
            section.write(value, offset, length);
            if (!indentRoot) { root.write(value, offset, length); return; }
            int end = offset + length;
            while (offset < end) {
                int newline = value.indexOf('\n', offset);
                if (newline < 0 || newline >= end) { root.write(value, offset, end - offset); break; }
                root.write(value, offset, newline - offset + 1); root.write("  "); offset = newline + 1;
            }
        }
        @Override public void write(char[] value, int offset, int length) throws IOException {
            section.write(value, offset, length);
            if (!indentRoot) { root.write(value, offset, length); return; }
            int end = offset + length, start = offset;
            for (int i = offset; i < end; i++) if (value[i] == '\n') {
                root.write(value, start, i - start + 1); root.write("  "); start = i + 1;
            }
            root.write(value, start, end - start);
        }
        @Override public void flush() throws IOException { root.flush(); section.flush(); }
        @Override public void close() throws IOException { flush(); }
    }

    private static String hash(JsonElement value, boolean[] canonicalOrder) {
        return hashWritten(json -> writeCanonical(value, json, canonicalOrder));
    }

    private static String hashWritten(SectionWriter action) {
        return hashWritten(action, false);
    }

    private static String hashWritten(SectionWriter action, boolean surrogateSafe) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // JsonWriter emits punctuation/indentation in tiny writes. Encode chunks, not
            // millions of individual tokens, while retaining the exact canonical bytes.
            try (var output = new BalanceJsonBuffer(new OutputStreamWriter(
                    new DigestOutputStream(OutputStream.nullOutputStream(), digest), StandardCharsets.UTF_8))) {
                var json = writer(surrogateSafe ? new SurrogateEscapingWriter(output) : output);
                if (surrogateSafe) json.setIndent("");
                action.write(json);
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
