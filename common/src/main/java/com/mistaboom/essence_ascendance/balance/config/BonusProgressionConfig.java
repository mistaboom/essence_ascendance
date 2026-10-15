package com.mistaboom.essence_ascendance.balance.config;

import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeValueQuantization;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Optional human Bonus bounds. Defaults are emitted from the first valid generated profile, never before it. */
public record BonusProgressionConfig(Map<String, Entry> entries) {
    private static final Set<String> KEYS = Set.of("id", "min_tier", "max_tier", "bonus_at_max", "minimum_bonus_at_lowest");
    private static final java.util.List<String> TIERS = java.util.List.of("essence_ascendance:dormant",
            "essence_ascendance:awakened", "essence_ascendance:resonant", "essence_ascendance:ascendant", "essence_ascendance:transcendent");

    public BonusProgressionConfig { entries = Collections.unmodifiableMap(new TreeMap<>(entries)); }
    public record Entry(String minTier, String maxTier, double bonusAtMax, double minimumBonusAtLowest) {
        public Entry {
            int start = TIERS.indexOf(minTier), end = TIERS.indexOf(maxTier);
            if (start < 0 || end < start) throw new IllegalArgumentException("min_tier and max_tier must be powered tiers in ascending order");
            if (!Double.isFinite(bonusAtMax) || bonusAtMax <= 0 || bonusAtMax > 1_000_000
                    || !Double.isFinite(minimumBonusAtLowest) || minimumBonusAtLowest <= 0 || minimumBonusAtLowest > bonusAtMax)
                throw new IllegalArgumentException("Bonuses must satisfy 0 < minimum_bonus_at_lowest <= bonus_at_max <= 1000000");
        }
    }

    public static BonusProgressionConfig automatic() { return new BonusProgressionConfig(Map.of()); }
    public static Path path(Path configDirectory) { return configDirectory.resolve("essence_ascendance/bonus_progression.toml"); }

    /** One scalar authority for automatic planning and emitted controls, before fraction round-trip noise. */
    public static Entry generatedEntry(BonusTrackDefinition track) {
        double quantum = track.inputs().containsKey("native_base_step_height") ? .000001 : RuntimeValueQuantization.statStep(track.unit());
        double first = RuntimeValueQuantization.reconstructedGridValue(track.activeValues().getFirst(), quantum);
        return new Entry(track.startTier().toString(), track.completionTier().toString(), track.maximumEffect(), first);
    }

    public static BonusProgressionConfig read(Path configDirectory) throws IOException {
        Path file = path(configDirectory);
        if (!Files.exists(file)) return automatic();
        if (Files.size(file) > 4_000_000) throw new IOException(file + ": TOML exceeds 4 MB");
        return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString());
    }

    public static BonusProgressionConfig parse(String text, String source) {
        var entries = new TreeMap<String, Entry>();
        for (var table : BalanceToml.parse(text, source)) {
            if (table.name().isEmpty() && !table.array()) {
                BalanceToml.requireKeys(table, Set.of("schema_version"), source);
                var version = table.values().get("schema_version");
                if (version != null && (!(version.value() instanceof Long value) || value != 1))
                    throw BalanceToml.error(source, version.line(), "schema_version must be the integer 1");
                continue;
            }
            if (!table.array() || !table.name().equals("bonus"))
                throw BalanceToml.error(source, table.line(), "Expected [[bonus]] entries");
            BalanceToml.requireKeys(table, KEYS, source);
            for (String key : KEYS) if (!table.values().containsKey(key))
                throw BalanceToml.error(source, table.line(), "Missing Bonus setting '" + key + "'");
            String id = string(table, "id", source);
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw BalanceToml.error(source, table.line(), "Invalid Bonus ID '" + id + "'");
            try {
                var entry = new Entry(string(table, "min_tier", source), string(table, "max_tier", source),
                        number(table, "bonus_at_max", source), number(table, "minimum_bonus_at_lowest", source));
                if (entries.putIfAbsent(id, entry) != null) throw new IllegalArgumentException("Duplicate Bonus ID '" + id + "'");
            } catch (IllegalArgumentException invalid) {
                if (invalid instanceof BalanceConfigException config) throw config;
                throw BalanceToml.error(source, table.line(), id + ": " + invalid.getMessage());
            }
        }
        return new BonusProgressionConfig(entries);
    }

    /** Called only after validation and durable publication of the first generated profile. */
    public static void writeGeneratedDefaults(Path configDirectory, Map<ResourceLocation, BonusTrackDefinition> tracks) throws IOException {
        Path file = path(configDirectory);
        if (Files.exists(file)) return;
        var text = new StringBuilder("""
                # Generated Bonus progression defaults. Existing files are never overwritten.
                # Edit these four controls, then explicitly rebuild balance to apply them.
                # Omitted Bonuses are generated automatically. Values use each Bonus's displayed unit.
                # min_tier/max_tier are exact active endpoints; intermediate tiers must each provide a meaningful improvement.
                # bonus_at_max is the exact completion value; minimum_bonus_at_lowest is a lower bound.
                # Authored minimums and native limits still apply. Incompatible edits fail without changing the saved profile.
                schema_version = 1
                """);
        for (var track : new TreeMap<>(tracks).values()) {
            if (track.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE) {
                text.append("\n# ").append(track.statId()).append(" has no generated native state; remains automatic.\n");
                continue;
            }
            Entry generated = generatedEntry(track);
            text.append("\n[[bonus]]\n# Unit: ").append(track.unit().name().toLowerCase(java.util.Locale.ROOT))
                    .append("\nid = \"").append(track.statId()).append("\"\nmin_tier = \"").append(track.startTier())
                    .append("\"\nmax_tier = \"").append(track.completionTier()).append("\"\nbonus_at_max = ").append(track.maximumEffect())
                    .append("\nminimum_bonus_at_lowest = ").append(generated.minimumBonusAtLowest()).append('\n');
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".bonus-progression-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            // No REPLACE_EXISTING or ATOMIC_MOVE: a concurrently created human file must win.
            // The destination is never opened while its contents are still being written.
            try { Files.move(temporary, file); }
            catch (FileAlreadyExistsException ignored) { /* A human/concurrent file always wins. */ }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String string(BalanceToml.Table table, String key, String source) {
        var value = table.values().get(key);
        if (!(value.value() instanceof String text) || text.isBlank()) throw BalanceToml.error(source, value.line(), key + " must be a nonempty string");
        return text;
    }
    private static double number(BalanceToml.Table table, String key, String source) {
        var value = table.values().get(key);
        if (!(value.value() instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw BalanceToml.error(source, value.line(), key + " must be a finite number");
        return number.doubleValue();
    }
}
