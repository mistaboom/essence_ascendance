package com.mistaboom.essence_ascendance.mapping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Disk representation only: no valuation, registry lookups or live publication.
 * Includes nonpayable candidates so current eligibility/removal settings can be
 * applied without recomputing the economy. Explicit overrides are never stored.
 */
final class GeneratedMappingCache {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Set<String> ROOT_FIELDS = Set.of("schema_version", "minecraft_version",
            "generated_at", "entry_count", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of("item", "total_value", "modeled_acquisition",
            "conservation_status", "outputs");

    private GeneratedMappingCache() { }

    record Entry(String itemId, long totalValue, boolean modeledAcquisition,
                 String conservationStatus, Map<String, Long> outputs) {
        Entry {
            requireId(itemId);
            Objects.requireNonNull(conservationStatus, "conservation_status");
            if (conservationStatus.isBlank()) throw new IllegalArgumentException("Empty conservation_status for " + itemId);
            outputs = Map.copyOf(outputs);
            long total = 0;
            for (var output : outputs.entrySet()) {
                requireId(output.getKey());
                if (output.getValue() < 0) throw new IllegalArgumentException("Negative output for " + itemId);
                total = Math.addExact(total, output.getValue());
            }
            if (totalValue < 0 || total != totalValue)
                throw new IllegalArgumentException("Invalid total or output sum for " + itemId);
        }
    }

    record Snapshot(String minecraftVersion, String generatedAt, List<Entry> entries) {
        Snapshot {
            Objects.requireNonNull(minecraftVersion, "minecraft_version");
            Instant.parse(generatedAt);
            entries = List.copyOf(entries);
            Set<String> ids = new HashSet<>();
            for (Entry entry : entries) {
                if (!ids.add(entry.itemId())) throw new IllegalArgumentException("Duplicate cached item: " + entry.itemId());
            }
        }
    }

    record Loaded(Snapshot snapshot, boolean generated) { }

    /** An invalid/unreadable existing file is NOT silently regenerated. Only a
     * genuinely absent file or the explicit rebuild path calls the supplier.
     */
    static Loaded loadOrGenerate(Path path, String minecraftVersion, boolean force,
                                 Supplier<List<Entry>> generator) throws IOException {
        if (!force && !Files.notExists(path)) {
            Snapshot snapshot = read(path);
            if (!snapshot.minecraftVersion().equals(minecraftVersion))
                throw new IllegalArgumentException("Saved mappings target Minecraft " + snapshot.minecraftVersion()
                        + ", not " + minecraftVersion + "; use /essence debug valuation rebuild");
            return new Loaded(snapshot, false);
        }
        return new Loaded(new Snapshot(minecraftVersion, Instant.now().toString(), generator.get()), true);
    }

    static Snapshot read(Path path) throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            Set<String> seen = new HashSet<>();
            Long schema = null, count = null;
            String version = null, generatedAt = null;
            List<Entry> entries = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = field(reader, seen);
                switch (field) {
                    case "_comment" -> string(reader);
                    case "schema_version" -> schema = number(reader);
                    case "minecraft_version" -> version = string(reader);
                    case "generated_at" -> generatedAt = string(reader);
                    case "entry_count" -> count = number(reader);
                    case "entries" -> {
                        entries = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) entries.add(readEntry(reader));
                        reader.endArray();
                    }
                    default -> throw new IllegalArgumentException("Unknown cache field: " + field);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing cache content");
            if (!seen.containsAll(ROOT_FIELDS)) throw new IllegalArgumentException("Incomplete generated-mapping cache");
            if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported generated-mapping cache schema: " + schema);
            if (count != entries.size()) throw new IllegalArgumentException("Cache entry_count does not match entries");
            return new Snapshot(version, generatedAt, entries);
        }
    }

    private static Entry readEntry(JsonReader reader) throws IOException {
        Set<String> seen = new HashSet<>();
        String item = null, conservation = null;
        Long total = null;
        Boolean modeled = null;
        Map<String, Long> outputs = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = field(reader, seen);
            switch (field) {
                case "item" -> item = string(reader);
                case "total_value" -> total = number(reader);
                case "modeled_acquisition" -> {
                    if (reader.peek() != JsonToken.BOOLEAN) throw new IllegalArgumentException("modeled_acquisition must be boolean");
                    modeled = reader.nextBoolean();
                }
                case "conservation_status" -> conservation = string(reader);
                case "outputs" -> {
                    Set<String> outputIds = new HashSet<>();
                    reader.beginObject();
                    while (reader.hasNext()) outputs.put(field(reader, outputIds), number(reader));
                    reader.endObject();
                }
                default -> throw new IllegalArgumentException("Unknown cache entry field: " + field);
            }
        }
        reader.endObject();
        if (!seen.containsAll(ENTRY_FIELDS)) throw new IllegalArgumentException("Incomplete cached item: " + item);
        return new Entry(item, total, modeled, conservation, outputs);
    }

    private static String field(JsonReader reader, Set<String> seen) throws IOException {
        String field = reader.nextName();
        if (!seen.add(field)) throw new IllegalArgumentException("Duplicate JSON field: " + field);
        return field;
    }

    private static String string(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw new IllegalArgumentException("Expected a JSON string");
        return reader.nextString();
    }

    private static long number(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw new IllegalArgumentException("Expected an exact JSON integer");
        return new BigDecimal(reader.nextString()).longValueExact();
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid cached resource ID: " + id);
    }

    /** Called only after the existing registry has validated the complete merged
     * replacement. No truncate/delete-first path and no non-atomic fallback.
     * Atomic-move failure keeps the prior file and prevents live publication.
     */
    static void writeAtomically(Path path, Snapshot snapshot) {
        Path temporary = null;
        try {
            Path target = path.toAbsolutePath();
            Files.createDirectories(target.getParent());
            byte[] bytes = encode(snapshot).getBytes(StandardCharsets.UTF_8);
            temporary = Files.createTempFile(target.getParent(), ".generated_mappings-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not atomically save generated mappings at " + path, exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { /* An orphan .tmp is never loaded as a cache. */ }
            }
        }
    }

    static String encode(Snapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Generated baseline candidates, NOT explicit overrides. Eligibility still applies. "
                + "Regenerate with /essence debug valuation rebuild; put manual overrides in item_mappings/.");
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("minecraft_version", snapshot.minecraftVersion());
        root.addProperty("generated_at", snapshot.generatedAt());
        root.addProperty("entry_count", snapshot.entries().size());
        JsonArray entries = new JsonArray();
        for (Entry entry : snapshot.entries().stream().sorted(Comparator.comparing(Entry::itemId)).toList()) {
            JsonObject value = new JsonObject();
            value.addProperty("item", entry.itemId());
            value.addProperty("total_value", entry.totalValue());
            value.addProperty("modeled_acquisition", entry.modeledAcquisition());
            value.addProperty("conservation_status", entry.conservationStatus());
            JsonObject outputs = new JsonObject();
            entry.outputs().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(output -> outputs.addProperty(output.getKey(), output.getValue()));
            value.add("outputs", outputs);
            entries.add(value);
        }
        root.add("entries", entries);
        return GSON.toJson(root) + "\n";
    }
}
