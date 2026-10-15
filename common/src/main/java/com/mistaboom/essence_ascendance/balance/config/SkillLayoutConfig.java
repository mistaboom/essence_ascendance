package com.mistaboom.essence_ascendance.balance.config;

import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
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

/** Human-authored unlock tiers. Reading/scaffolding does not initialize the gameplay registries. */
public final class SkillLayoutConfig {
    private static final String DEFAULT_TEXT = defaultText();
    private static final Map<String, String> AUTHORED = entries(DEFAULT_TEXT, "bundled skill_layout.toml");
    private final Map<String, String> tiers;
    private final String source;

    private SkillLayoutConfig(Map<String, String> tiers, String source) {
        this.tiers = Collections.unmodifiableMap(new TreeMap<>(tiers));
        this.source = source;
    }

    public static SkillLayoutConfig defaults() {
        return new SkillLayoutConfig(AUTHORED, "authored skill layout");
    }

    public static Path path(Path configDirectory) {
        return configDirectory.resolve("essence_ascendance/skill_layout.toml");
    }

    public static void scaffold(Path configDirectory) throws IOException {
        Path file = path(configDirectory);
        if (Files.exists(file)) return;
        Files.createDirectories(file.getParent());
        try {
            Files.writeString(file, DEFAULT_TEXT, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) {
            // Another startup may have created it. Human files always win.
        }
    }

    public static SkillLayoutConfig read(Path configDirectory) throws IOException {
        scaffold(configDirectory);
        Path file = path(configDirectory);
        if (Files.size(file) > 4_000_000) throw new IOException(file + ": TOML exceeds 4 MB");
        return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString());
    }

    public static SkillLayoutConfig parse(String text, String source) {
        Map<String, String> result = new TreeMap<>(AUTHORED);
        result.putAll(entries(text, source));
        return new SkillLayoutConfig(result, source);
    }

    /** Resolve only after normal catalog startup; invalid prerequisite order fails instead of moving other skills. */
    public Map<ResourceLocation, ResourceLocation> requiredTiers() {
        var result = new TreeMap<ResourceLocation, ResourceLocation>();
        tiers.forEach((skill, tier) -> result.put(ResourceLocation.parse(skill), ResourceLocation.parse(tier)));
        try {
            return SkillBalanceRuntime.withRequiredTiers(result,
                    () -> Collections.unmodifiableMap(result));
        } catch (IllegalArgumentException invalid) {
            throw new BalanceConfigException(source, 0, invalid.getMessage());
        }
    }

    private static Map<String, String> entries(String text, String source) {
        var result = new TreeMap<String, String>();
        for (var table : BalanceToml.parse(text, source)) {
            if (table.array() || !(table.name().isEmpty() || table.name().equals("skills")))
                throw BalanceToml.error(source, table.line(), "Unknown skill layout table '" + table.name() + "'");
            if (table.name().isEmpty()) {
                BalanceToml.requireKeys(table, Set.of("schema_version"), source);
                var version = table.values().get("schema_version");
                if (version != null && (!(version.value() instanceof Long number) || number != 1))
                    throw BalanceToml.error(source, version.line(), "schema_version must be the integer 1");
                continue;
            }
            for (var entry : table.values().entrySet()) {
                String id = entry.getKey();
                var value = entry.getValue();
                if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || (AUTHORED != null && !AUTHORED.containsKey(id)))
                    throw BalanceToml.error(source, value.line(), "Unknown skill ID '" + id + "'");
                if (!(value.value() instanceof String tier) || !Set.of("essence_ascendance:dormant",
                        "essence_ascendance:awakened", "essence_ascendance:resonant",
                        "essence_ascendance:ascendant", "essence_ascendance:transcendent").contains(tier))
                    throw BalanceToml.error(source, value.line(), "Skill '" + id + "' must name a powered Ascendance tier");
                result.put(id, tier);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String defaultText() {
        try (var input = SkillLayoutConfig.class.getResourceAsStream("/balance/skill_layout.toml")) {
            if (input == null) throw new IllegalStateException("Missing bundled skill_layout.toml");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read bundled skill_layout.toml", failure);
        }
    }

    @Override public boolean equals(Object other) {
        return other instanceof SkillLayoutConfig layout && tiers.equals(layout.tiers);
    }
    @Override public int hashCode() { return tiers.hashCode(); }
    @Override public String toString() { return "SkillLayoutConfig" + tiers; }
}
