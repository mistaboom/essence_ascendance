package com.mistaboom.essence_ascendance.balance.config;

import com.google.gson.GsonBuilder;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.skill.ProgressionRequirements;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Authored placement plus optional generated rank/stat controls. Never rewrites a human file. */
public final class SkillSettingsConfig {
    private static final Set<String> TIERS = Set.of("essence_ascendance:dormant", "essence_ascendance:awakened",
            "essence_ascendance:resonant", "essence_ascendance:ascendant", "essence_ascendance:transcendent");
    private static final Map<String, Entry> AUTHORED = parseEntries(defaultText(), "bundled skill_settings.toml");
    private final Map<String, Entry> entries;
    private final String source;
    public record Stat(double atFirstRank, double atMaxRank) {
        public Stat {
            if (!Double.isFinite(atFirstRank) || !Double.isFinite(atMaxRank))
                throw new IllegalArgumentException("Stat endpoints must be finite");
        }
    }
    public record Entry(String tier, Integer ranks, Map<String, Stat> stats) {
        public Entry { stats = Collections.unmodifiableMap(new TreeMap<>(stats)); }
    }
    private SkillSettingsConfig(Map<String, Entry> entries, String source) {
        this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
        this.source = source;
    }
    public Map<String, Entry> entries() { return entries; }
    public static SkillSettingsConfig defaults() { return new SkillSettingsConfig(AUTHORED, "authored skill defaults"); }
    public static Path path(Path directory) { return directory.resolve("essence_ascendance/skill_settings.toml"); }
    public static SkillSettingsConfig read(Path directory) throws IOException {
        Path file = path(directory);
        if (!Files.exists(file)) return defaults();
        if (Files.size(file) > 4_000_000) throw new IOException(file + ": TOML exceeds 4 MB");
        return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString());
    }
    public static SkillSettingsConfig parse(String text, String source) {
        var result = new TreeMap<>(AUTHORED);
        parseEntries(text, source).forEach((id, entry) -> {
            Entry authored = AUTHORED.get(id);
            result.put(id, new Entry(entry.tier() == null ? authored.tier() : entry.tier(), entry.ranks(), entry.stats()));
        });
        return new SkillSettingsConfig(result, source);
    }
    /** Resolve after catalog startup; prerequisite order is validated, never silently rearranged. */
    public Map<ResourceLocation, ResourceLocation> requiredTiers() {
        var result = new TreeMap<ResourceLocation, ResourceLocation>();
        entries.forEach((id, entry) -> result.put(ResourceLocation.parse(id), ResourceLocation.parse(entry.tier())));
        try { return SkillBalanceRuntime.withRequiredTiers(result, () -> Collections.unmodifiableMap(result)); }
        catch (IllegalArgumentException invalid) { throw new BalanceConfigException(source, 0, invalid.getMessage()); }
    }

    /** Explicit controls follow allocation. Native floors/caps still validate the entire result. */
    public RuntimeBalanceDefinition apply(RuntimeBalanceDefinition generated) {
        if (entries.values().stream().allMatch(e -> e.ranks() == null && e.stats().isEmpty())) return generated;
        var json = generated.toJson();
        var firstEffects = json.getAsJsonObject("effects");
        var curves = new TreeMap<>(generated.skillCurves());
        for (var configured : entries.entrySet()) {
            String id = configured.getKey();
            Entry entry = configured.getValue();
            var original = curves.get(id);
            int count = entry.ranks() == null ? original.maximumRank() : entry.ranks();
            var first = original.ranks().getFirst();
            var last = original.ranks().getLast();
            if (!first.parameters().keySet().containsAll(entry.stats().keySet()))
                throw new BalanceConfigException(source, 0, id + ": unknown or non-rank stat "
                        + entry.stats().keySet().stream().filter(k -> !first.parameters().containsKey(k)).toList());
            boolean unchanged = count == original.maximumRank() && entry.stats().entrySet().stream().allMatch(e ->
                    close(e.getValue().atFirstRank(), first.parameters().get(e.getKey()))
                    && close(e.getValue().atMaxRank(), last.parameters().get(e.getKey())));
            if (unchanged) continue; // Emitted defaults preserve exact middle states and prices.
            var skill = SkillRegistry.require(ResourceLocation.parse(id));
            var outcomes = new TreeMap<String, ProgressionRequirements.Outcome>();
            skill.progressionRequirements().outcomes().forEach(o -> outcomes.put(o.path(), o));
            var ranks = new ArrayList<SkillBalanceRuntime.ResolvedRank>();
            for (int rank = 1; rank <= count; rank++) {
                double position = count == 1 ? 1 : (rank - 1.0) / (count - 1.0);
                var parameters = new TreeMap<String, Double>();
                for (String parameter : first.parameters().keySet()) {
                    double start = first.parameters().get(parameter), end = last.parameters().get(parameter);
                    Stat setting = entry.stats().get(parameter);
                    double from = setting == null ? start : setting.atFirstRank();
                    double to = setting == null ? end : setting.atMaxRank();
                    if (count == 1 && setting != null && !close(from, to))
                        throw new BalanceConfigException(source, 0, id + "/" + parameter
                                + ": a single rank needs equal at_first_rank and at_max_rank");
                    double sampled = sample(original, parameter, position);
                    double progress = close(start, end) ? position : (sampled - start) / (end - start);
                    double value = from + (to - from) * progress;
                    var outcome = outcomes.get(parameter);
                    double step = outcome == null ? 0 : outcome.quantum();
                    var node = firstEffects;
                    String[] components = parameter.split("/");
                    for (int part = 0; part < components.length - 1; part++) node = node.getAsJsonObject(components[part]);
                    Number nativeValue = node.get(components[components.length - 1]).getAsNumber();
                    if (nativeValue instanceof Integer || nativeValue instanceof Long) step = Math.max(1, step);
                    if (step > 0) value = Math.rint(value / step) * step;
                    if ((rank == 1 || rank == count) && setting != null
                            && !close(value, rank == 1 && count > 1 ? from : to))
                        throw new BalanceConfigException(source, 0, id + "/" + parameter + ": endpoint is off its native gameplay step");
                    parameters.put(parameter, value);
                }
                long cost = count == original.maximumRank() ? original.ranks().get(rank - 1).cost()
                        : skill.rankPolicy().curve().cost(first.cost(), rank);
                double power = count == original.maximumRank() ? original.ranks().get(rank - 1).powerMultiplier()
                        : rank == 1 ? 1 : 1 + (last.powerMultiplier() - 1) * position;
                ranks.add(new SkillBalanceRuntime.ResolvedRank(rank, cost, power, parameters));
            }
            ranks.getFirst().parameters().forEach((path, value) -> ProgressionRequirements.write(firstEffects, path, value));
            curves.put(id, new SkillBalanceRuntime.ResolvedSkill(count, ranks, original.requiredTierId()));
        }
        var gson = new GsonBuilder().registerTypeAdapter(ResourceLocation.class, new ResourceLocationJsonAdapter()).create();
        json.add("skillCurves", gson.toJsonTree(curves));
        try { return RuntimeBalanceDefinition.fromJson(json); }
        catch (IllegalArgumentException invalid) {
            throw new BalanceConfigException(source, 0, "Configured ranks/stats violate a native floor, cap or prerequisite: " + invalid.getMessage());
        }
    }
    private static double sample(SkillBalanceRuntime.ResolvedSkill curve, String path, double position) {
        double index = position * (curve.ranks().size() - 1);
        int low = (int) Math.floor(index), high = Math.min(curve.ranks().size() - 1, low + 1);
        double from = curve.ranks().get(low).parameters().get(path), to = curve.ranks().get(high).parameters().get(path);
        return from + (to - from) * (index - low);
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) <= Math.max(1e-9, 4 * Math.max(Math.ulp(a), Math.ulp(b))); }

    private static Map<String, Entry> parseEntries(String text, String source) {
        var result = new TreeMap<String, Entry>();
        String current = null;
        for (var table : BalanceToml.parse(text, source)) {
            if (table.name().isEmpty() && !table.array()) {
                BalanceToml.requireKeys(table, Set.of("schema_version"), source);
                var version = table.values().get("schema_version");
                if (version != null && (!(version.value() instanceof Long v) || v != 1))
                    throw BalanceToml.error(source, version.line(), "schema_version must be the integer 1");
            } else if (table.array() && table.name().equals("skill")) {
                BalanceToml.requireKeys(table, Set.of("id", "tier", "ranks"), source);
                current = string(table, "id", source);
                if (!current.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || (AUTHORED != null && !AUTHORED.containsKey(current)))
                    throw BalanceToml.error(source, table.line(), "Unknown skill ID '" + current + "'");
                String tier = table.values().containsKey("tier") ? string(table, "tier", source) : null;
                if ((tier != null && !TIERS.contains(tier)) || (AUTHORED == null && tier == null))
                    throw BalanceToml.error(source, table.line(), "Skill must name a powered Ascendance tier");
                Integer ranks = null;
                if (table.values().containsKey("ranks")) {
                    var value = table.values().get("ranks");
                    if (!(value.value() instanceof Long count) || count < 1 || count > 64)
                        throw BalanceToml.error(source, value.line(), "ranks must be an integer in [1,64]");
                    ranks = ((Long) value.value()).intValue();
                }
                if (result.putIfAbsent(current, new Entry(tier, ranks, Map.of())) != null)
                    throw BalanceToml.error(source, table.line(), "Duplicate skill ID '" + current + "'");
            } else if (table.array() && table.name().equals("skill.stat")) {
                if (current == null) throw BalanceToml.error(source, table.line(), "[[skill.stat]] requires a preceding [[skill]]");
                BalanceToml.requireKeys(table, Set.of("path", "at_first_rank", "at_max_rank"), source);
                String path = string(table, "path", source);
                var stat = new Stat(number(table, "at_first_rank", source), number(table, "at_max_rank", source));
                var entry = result.get(current);
                var stats = new TreeMap<>(entry.stats());
                if (stats.putIfAbsent(path, stat) != null) throw BalanceToml.error(source, table.line(), "Duplicate stat '" + path + "'");
                result.put(current, new Entry(entry.tier(), entry.ranks(), stats));
            } else throw BalanceToml.error(source, table.line(), "Expected [[skill]] or [[skill.stat]]");
        }
        return Collections.unmodifiableMap(result);
    }

    public static void writeGeneratedDefaults(Path directory, Map<String, SkillBalanceRuntime.ResolvedSkill> curves) throws IOException {
        Path file = path(directory);
        if (Files.exists(file)) return;
        var text = new StringBuilder("""
                # Generated Skill settings. Existing files are never overwritten.
                # Edit, then run /essence admin balance rebuild. Restart alone reuses the saved profile.
                # Omitted skills use authored tiers and automatic ranks/stats. Omitted controls stay automatic.
                # Each [[skill.stat]] belongs to the preceding [[skill]]. Values are native parameter values:
                # Percent fields use percentage points; Fraction/Chance use fractions (0.1 = 10%);
                # Multiplier/Scale are factors; Ticks use 20 per second; Blocks use blocks.
                # Cooldowns can decrease as ranks improve. First/last endpoints are exact on their native step.
                # Native meaningful minima, mechanical caps and prerequisites remain required.
                # Binary skills must keep one rank; one-rank stat endpoints must be equal.
                # Costs remain generated. These explicit settings may exceed automatic soft power budgets.
                schema_version = 1
                """);
        for (var entry : new TreeMap<>(curves).entrySet()) {
            var curve = entry.getValue();
            text.append("\n[[skill]]\nid = \"").append(entry.getKey()).append("\"\ntier = \"")
                    .append(curve.requiredTierId()).append("\"\nranks = ").append(curve.maximumRank()).append('\n');
            var outcomes = SkillRegistry.require(ResourceLocation.parse(entry.getKey())).progressionRequirements().outcomes();
            for (var parameter : curve.ranks().getFirst().parameters().entrySet()) {
                text.append("\n[[skill.stat]]\n");
                outcomes.stream().filter(o -> o.path().equals(parameter.getKey())).findFirst().ifPresent(o ->
                        text.append("# Native range: ").append(o.minimum()).append(" .. ").append(o.maximum())
                                .append("; step: ").append(o.quantum()).append('\n'));
                text.append("path = \"").append(parameter.getKey()).append("\"\nat_first_rank = ").append(parameter.getValue())
                        .append("\nat_max_rank = ").append(curve.ranks().getLast().parameters().get(parameter.getKey())).append('\n');
            }
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".skill-settings-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temporary, file); }
            catch (FileAlreadyExistsException ignored) { /* Concurrent human file wins. */ }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String string(BalanceToml.Table table, String key, String source) {
        var value = table.values().get(key);
        if (value == null || !(value.value() instanceof String text) || text.isBlank())
            throw BalanceToml.error(source, table.line(), key + " must be a nonempty string");
        return (String) value.value();
    }
    private static double number(BalanceToml.Table table, String key, String source) {
        var value = table.values().get(key);
        if (value == null || !(value.value() instanceof Number n) || !Double.isFinite(n.doubleValue()))
            throw BalanceToml.error(source, table.line(), key + " must be a finite number");
        return ((Number) value.value()).doubleValue();
    }
    private static String defaultText() {
        try (var input = SkillSettingsConfig.class.getResourceAsStream("/balance/skill_settings.toml")) {
            if (input == null) throw new IllegalStateException("Missing bundled skill_settings.toml");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new IllegalStateException("Cannot read bundled skill_settings.toml", failure); }
    }
    @Override public boolean equals(Object other) { return other instanceof SkillSettingsConfig c && entries.equals(c.entries); }
    @Override public int hashCode() { return entries.hashCode(); }
    @Override public String toString() { return "SkillSettingsConfig" + entries; }
}
