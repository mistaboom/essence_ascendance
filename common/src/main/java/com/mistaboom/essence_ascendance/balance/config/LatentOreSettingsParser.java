package com.mistaboom.essence_ascendance.balance.config;

import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings.DimensionOverride;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings.DimensionSettings;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reuses the existing bounded TOML grammar and error reporting for ore generation inputs. */
final class LatentOreSettingsParser {
    private static final Set<String> DISTRIBUTION = Set.of("enabled", "vein_size", "veins_per_chunk", "min_y", "max_y", "discard_chance_on_air_exposure");
    private static final Set<String> DIMENSION = Set.of("dimension", "hosts", "enabled", "vein_size", "veins_per_chunk", "min_y", "max_y", "discard_chance_on_air_exposure");
    private LatentOreSettingsParser() { }

    static LatentOreWorldgenSettings parse(List<BalanceToml.Table> tables, String source) {
        var defaults = LatentOreWorldgenSettings.defaults();
        DimensionSettings overworld = defaults.overworld(), nether = defaults.nether(), end = defaults.end();
        boolean automatic = true;
        Map<ResourceLocation, DimensionOverride> overrides = new LinkedHashMap<>();
        // Resolve shared vanilla distributions first, independently of TOML table order.
        for (var table : tables.stream().sorted(java.util.Comparator.comparingInt(table ->
                table.name().equals("latent_ore.dimension") ? 1 : 0)).toList()) {
            try {
                if (table.name().equals("latent_ore") && !table.array()) {
                    BalanceToml.requireKeys(table, Set.of("automatic_dimensions"), source);
                    automatic = bool(table, "automatic_dimensions", true, source);
                } else if (table.name().equals("latent_ore.dimension") && table.array()) {
                    BalanceToml.requireKeys(table, DIMENSION, source);
                    var dimension = id(value(table, "dimension", source), "dimension");
                    List<ResourceLocation> hosts = new ArrayList<>();
                    if (table.values().containsKey("hosts")) {
                        Object raw = value(table, "hosts", source);
                        if (!(raw instanceof List<?> entries)) throw new IllegalArgumentException("hosts must be an array of exact block ids");
                        for (Object entry : entries) hosts.add(id(entry, "hosts"));
                    }
                    boolean distributionSpecified = table.values().keySet().stream()
                            .anyMatch(key -> DISTRIBUTION.contains(key) && !key.equals("enabled"));
                    DimensionSettings distribution = distributionSpecified
                            ? distribution(table, new LatentOreWorldgenSettings(overworld, nether, end, automatic, Map.of())
                                    .distribution(dimension, -2048, 2048), source) : null;
                    if (overrides.putIfAbsent(dimension, new DimensionOverride(bool(table, "enabled", true, source), hosts, distribution)) != null)
                        throw new IllegalArgumentException("Duplicate Latent Ore override for " + dimension);
                } else if (!table.array()) {
                    BalanceToml.requireKeys(table, DISTRIBUTION, source);
                    switch (table.name()) {
                        case "latent_ore.overworld" -> overworld = distribution(table, defaults.overworld(), source);
                        case "latent_ore.nether" -> nether = distribution(table, defaults.nether(), source);
                        case "latent_ore.end" -> end = distribution(table, defaults.end(), source);
                        default -> throw new IllegalArgumentException("Unknown Latent Ore table " + table.name());
                    }
                } else throw new IllegalArgumentException("Expected [[latent_ore.dimension]] for an exact dimension override");
            } catch (BalanceConfigException exception) { throw exception; }
            catch (IllegalArgumentException exception) { throw BalanceToml.error(source, table.line(), exception.getMessage()); }
        }
        return new LatentOreWorldgenSettings(overworld, nether, end, automatic, overrides);
    }

    private static DimensionSettings distribution(BalanceToml.Table table, DimensionSettings defaults, String source) {
        return new DimensionSettings(bool(table, "enabled", defaults.enabled(), source),
                integer(table, "vein_size", defaults.veinSize()), integer(table, "veins_per_chunk", defaults.veinsPerChunk()),
                integer(table, "min_y", defaults.minY()), integer(table, "max_y", defaults.maxY()),
                number(table, "discard_chance_on_air_exposure", defaults.discardChanceOnAirExposure()));
    }
    private static Object value(BalanceToml.Table table, String key, String source) {
        var value = table.values().get(key);
        if (value == null) throw BalanceToml.error(source, table.line(), "Missing " + key);
        return value.value();
    }
    private static ResourceLocation id(Object value, String key) {
        if (!(value instanceof String text) || !text.contains(":")) throw new IllegalArgumentException(key + " requires a namespaced id");
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) throw new IllegalArgumentException("Invalid " + key + " id: " + text);
        return id;
    }
    private static boolean bool(BalanceToml.Table table, String key, boolean fallback, String source) {
        if (!table.values().containsKey(key)) return fallback;
        Object value = value(table, key, source);
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException(key + " must be true or false");
        return flag;
    }
    private static int integer(BalanceToml.Table table, String key, int fallback) {
        var value = table.values().get(key);
        if (value == null) return fallback;
        if (!(value.value() instanceof Long number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
            throw new IllegalArgumentException(key + " must be a whole integer");
        return number.intValue();
    }
    private static double number(BalanceToml.Table table, String key, double fallback) {
        var value = table.values().get(key);
        if (value == null) return fallback;
        if (!(value.value() instanceof Number number)) throw new IllegalArgumentException(key + " must be a number");
        return number.doubleValue();
    }
}
