package com.mistaboom.essence_ascendance.equipment.personalized;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.*;

/** Shared, immutable gift definitions. Also read by the asset compiler so palettes have one owner. */
public record PersonalizedEquipmentDefinition(String id, UUID playerId, String itemId, String tier,
        String name, int baseRgb, int accentRgb, Map<String, Double> perks, String flavor) {
    /** Existing definitions and callers do not need flavor text. */
    public PersonalizedEquipmentDefinition(String id, UUID playerId, String itemId, String tier,
            String name, int baseRgb, int accentRgb, Map<String, Double> perks) {
        this(id, playerId, itemId, tier, name, baseRgb, accentRgb, perks, "");
    }

    public PersonalizedEquipmentDefinition {
        if (id == null || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid gift ID");
        Objects.requireNonNull(playerId);
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid gift item");
        if (!Set.of("latent", "dormant", "awakened", "resonant", "ascendant", "transcendent").contains(tier))
            throw new IllegalArgumentException("Invalid gift tier");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Gift name is required");
        flavor = flavor == null ? "" : flavor.strip();
        if ((baseRgb & 0xFF000000) != 0 || (accentRgb & 0xFF000000) != 0)
            throw new IllegalArgumentException("Gift colors must be RGB");
        perks = Map.copyOf(perks);
        perks.forEach((key, value) -> {
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid gift perk: " + key);
        });
    }

    public double perk(String id, double fallback) { return perks.getOrDefault(id, fallback); }

    public static List<PersonalizedEquipmentDefinition> read(Reader reader) {
        var root = JsonParser.parseReader(reader).getAsJsonObject();
        if (root.get("schema_version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported gift schema");
        List<PersonalizedEquipmentDefinition> definitions = new ArrayList<>();
        Set<String> ids = new HashSet<>(), assignments = new HashSet<>();
        for (var entry : root.getAsJsonArray("profiles")) {
            var json = entry.getAsJsonObject();
            Map<String, Double> perks = new LinkedHashMap<>();
            json.getAsJsonObject("perks").entrySet().forEach(e -> perks.put(e.getKey(), e.getValue().getAsDouble()));
            var definition = new PersonalizedEquipmentDefinition(json.get("id").getAsString(),
                    UUID.fromString(json.get("player_uuid").getAsString()), json.get("item").getAsString(),
                    json.get("tier").getAsString(), json.get("name").getAsString(),
                    color(json.get("base_color").getAsString()), color(json.get("accent_color").getAsString()), perks,
                    json.has("flavor") && !json.get("flavor").isJsonNull() ? json.get("flavor").getAsString() : "");
            if (!ids.add(definition.id()) || !assignments.add(definition.playerId() + "/" + definition.itemId() + "/" + definition.tier()))
                throw new IllegalArgumentException("Duplicate gift definition: " + definition.id());
            definitions.add(definition);
        }
        return List.copyOf(definitions);
    }

    private static int color(String value) {
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid gift color");
        return Integer.parseInt(value.substring(1), 16);
    }
}
