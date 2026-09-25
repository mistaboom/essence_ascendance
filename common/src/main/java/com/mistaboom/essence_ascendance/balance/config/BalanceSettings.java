package com.mistaboom.essence_ascendance.balance.config;

import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** High-level generation intent. Gameplay consumes the resolved profile instead. */
public record BalanceSettings(
        double overallPower, double earlyPower, double midPower, double latePower, double apexPower,
        double progressionLength, double costPressure, double partialBuildViability,
        double equipmentShare, double nexusShare, double skillShare,
        double automationPressure, double bulkResourcePenalty, double conversionLossPressure,
        double compositionSafeguard, FlightPolicy flightPolicy, MiningPolicy miningPolicy,
        ResourcePolicy resourcePolicy, OutlierPolicy outlierPolicy,
        double warningConfidence, boolean expandedDiagnostics, RuntimeGenerationPolicy generation, AttunementPolicy attunement,
        LatentOreWorldgenSettings latentOre
) {
    public enum FlightPolicy { PRESERVE_PROGRESSION, MATCH_PACK, RESTRICT }
    public enum MiningPolicy { PRESERVE_PROGRESSION, MATCH_PACK, RESTRICT }
    public enum ResourcePolicy { CONSERVATIVE, BALANCED, ABUNDANCE_AWARE }
    public enum OutlierPolicy { EXCLUDE_UNSUPPORTED, WINSORIZE, INCLUDE_ATTAINABLE }

    private static final Map<String, Set<String>> KEYS = Map.of(
            "", Set.of("schema_version"),
            "power", Set.of("overall", "early", "mid", "late", "apex"),
            "progression", Set.of("length", "cost_pressure"),
            "builds", Set.of("partial_viability", "composition_safeguard"),
            "budget", Set.of("equipment", "nexus", "skills"),
            "economy", Set.of("automation_pressure", "bulk_resource_penalty", "conversion_loss_pressure"),
            "generation", Set.of("routine_seconds", "boss_seconds", "survival_seconds", "entry_resource_effort", "effort_growth"),
            "attunement", Set.of("pace", "maximum_acceleration", "repetition_floor", "variety_strength", "history_window", "early_effort_fraction", "onboarding_effort_fraction", "breadth_exponent"),
            "policies", Set.of("flight", "mining", "resources", "outliers"),
            "diagnostics", Set.of("warning_confidence", "expanded")
    );

    public BalanceSettings {
        if(generation==null)generation=RuntimeGenerationPolicy.defaults();
        if(attunement==null)attunement=AttunementPolicy.defaults();
        java.util.Objects.requireNonNull(latentOre, "Latent Ore settings are required");
        range("power.overall", overallPower, 0.1, 4.0);
        range("power.early", earlyPower, 0.1, 4.0);
        range("power.mid", midPower, 0.1, 4.0);
        range("power.late", latePower, 0.1, 4.0);
        range("power.apex", apexPower, 0.1, 4.0);
        range("progression.length", progressionLength, 0.1, 10.0);
        range("progression.cost_pressure", costPressure, 0.1, 10.0);
        range("builds.partial_viability", partialBuildViability, 0.1, 1.0);
        range("budget.equipment", equipmentShare, 0.01, 0.98);
        range("budget.nexus", nexusShare, 0.01, 0.98);
        range("budget.skills", skillShare, 0.01, 0.98);
        if (Math.abs(equipmentShare + nexusShare + skillShare - 1.0) > 0.000001) {
            throw new IllegalArgumentException("budget.equipment + budget.nexus + budget.skills must equal 1.0; actual sum is "
                    + (equipmentShare + nexusShare + skillShare));
        }
        range("economy.automation_pressure", automationPressure, 0.0, 1.0);
        range("economy.bulk_resource_penalty", bulkResourcePenalty, 0.0, 1.0);
        range("economy.conversion_loss_pressure", conversionLossPressure, 0.0, 0.95);
        range("builds.composition_safeguard", compositionSafeguard, 0.0, 1.0);
        range("diagnostics.warning_confidence", warningConfidence, 0.0, 1.0);
        if (flightPolicy == null || miningPolicy == null || resourcePolicy == null || outlierPolicy == null) {
            throw new IllegalArgumentException("All balance policies must be specified");
        }
    }

    /** Source-compatible high-level settings constructor; older TOML keeps the documented policy defaults. */
    public BalanceSettings(double overallPower,double earlyPower,double midPower,double latePower,double apexPower,
                           double progressionLength,double costPressure,double partialBuildViability,
                           double equipmentShare,double nexusShare,double skillShare,double automationPressure,
                           double bulkResourcePenalty,double conversionLossPressure,double compositionSafeguard,
                           FlightPolicy flightPolicy,MiningPolicy miningPolicy,ResourcePolicy resourcePolicy,
                           OutlierPolicy outlierPolicy,double warningConfidence,boolean expandedDiagnostics) {
        this(overallPower,earlyPower,midPower,latePower,apexPower,progressionLength,costPressure,partialBuildViability,
                equipmentShare,nexusShare,skillShare,automationPressure,bulkResourcePenalty,conversionLossPressure,
                compositionSafeguard,flightPolicy,miningPolicy,resourcePolicy,outlierPolicy,warningConfidence,
                expandedDiagnostics,RuntimeGenerationPolicy.defaults(),AttunementPolicy.defaults(),LatentOreWorldgenSettings.defaults());
    }

    public BalanceSettings(double overallPower,double earlyPower,double midPower,double latePower,double apexPower,
                           double progressionLength,double costPressure,double partialBuildViability,
                           double equipmentShare,double nexusShare,double skillShare,double automationPressure,
                           double bulkResourcePenalty,double conversionLossPressure,double compositionSafeguard,
                           FlightPolicy flightPolicy,MiningPolicy miningPolicy,ResourcePolicy resourcePolicy,
                           OutlierPolicy outlierPolicy,double warningConfidence,boolean expandedDiagnostics,
                           RuntimeGenerationPolicy generation) {
        this(overallPower,earlyPower,midPower,latePower,apexPower,progressionLength,costPressure,partialBuildViability,
                equipmentShare,nexusShare,skillShare,automationPressure,bulkResourcePenalty,conversionLossPressure,
                compositionSafeguard,flightPolicy,miningPolicy,resourcePolicy,outlierPolicy,warningConfidence,
                expandedDiagnostics,generation,AttunementPolicy.defaults(),LatentOreWorldgenSettings.defaults());
    }

    public static BalanceSettings defaults() {
        return new BalanceSettings(1.0, 0.8, 0.9, 1.0, 1.1,
                1.0, 1.0, 0.65, 0.40, 0.35, 0.25,
                0.65, 0.60, 0.10, 0.75,
                FlightPolicy.PRESERVE_PROGRESSION, MiningPolicy.PRESERVE_PROGRESSION,
                ResourcePolicy.BALANCED, OutlierPolicy.EXCLUDE_UNSUPPORTED,
                0.60, true);
    }

    public static BalanceSettings parse(String toml, String source) {
        Map<String, BalanceToml.Value> values = new LinkedHashMap<>();
        Map<String, Integer> tableLines = new LinkedHashMap<>();
        java.util.List<BalanceToml.Table> oreTables = new java.util.ArrayList<>();
        for (BalanceToml.Table table : BalanceToml.parse(toml, source)) {
            if (table.name().equals("latent_ore") || table.name().startsWith("latent_ore.")) {
                oreTables.add(table);
                continue;
            }
            if (table.array() || !KEYS.containsKey(table.name())) {
                throw BalanceToml.error(source, table.line(), "Unknown settings table '" + table.name()
                        + "'; expected power, progression, builds, budget, economy, generation, attunement, policies or diagnostics");
            }
            BalanceToml.requireKeys(table, KEYS.get(table.name()), source);
            tableLines.put(table.name(), table.line());
            table.values().forEach((key, value) -> values.put(table.name().isEmpty() ? key : table.name() + "." + key, value));
        }
        SettingsReader read = new SettingsReader(values, source);
        read.schema();
        BalanceSettings d = defaults();
        try {
            return new BalanceSettings(
                    read.number("power.overall", d.overallPower), read.number("power.early", d.earlyPower),
                    read.number("power.mid", d.midPower), read.number("power.late", d.latePower), read.number("power.apex", d.apexPower),
                    read.number("progression.length", d.progressionLength), read.number("progression.cost_pressure", d.costPressure),
                    read.number("builds.partial_viability", d.partialBuildViability),
                    read.number("budget.equipment", d.equipmentShare), read.number("budget.nexus", d.nexusShare), read.number("budget.skills", d.skillShare),
                    read.number("economy.automation_pressure", d.automationPressure), read.number("economy.bulk_resource_penalty", d.bulkResourcePenalty),
                    read.number("economy.conversion_loss_pressure", d.conversionLossPressure), read.number("builds.composition_safeguard", d.compositionSafeguard),
                    read.policy("policies.flight", d.flightPolicy, FlightPolicy.class), read.policy("policies.mining", d.miningPolicy, MiningPolicy.class),
                    read.policy("policies.resources", d.resourcePolicy, ResourcePolicy.class), read.policy("policies.outliers", d.outlierPolicy, OutlierPolicy.class),
                    read.number("diagnostics.warning_confidence", d.warningConfidence), read.bool("diagnostics.expanded", d.expandedDiagnostics),
                    new RuntimeGenerationPolicy(read.number("generation.routine_seconds",d.generation.routineEncounterSeconds()),
                            read.number("generation.boss_seconds",d.generation.bossEncounterSeconds()),
                            read.number("generation.survival_seconds",d.generation.survivalWindowSeconds()),
                            read.number("generation.entry_resource_effort",d.generation.entryResourceEffort()),
                            read.number("generation.effort_growth",d.generation.effortGrowth())),
                    new AttunementPolicy(read.number("attunement.pace",d.attunement.pace()),
                            read.number("attunement.maximum_acceleration",d.attunement.maximumAcceleration()),
                            read.number("attunement.repetition_floor",d.attunement.repetitionFloor()),
                            read.number("attunement.variety_strength",d.attunement.varietyStrength()),
                            read.integer("attunement.history_window",d.attunement.historyWindow()),
                            read.number("attunement.early_effort_fraction",d.attunement.earlyEffortFraction()),
                            read.number("attunement.onboarding_effort_fraction",d.attunement.onboardingEffortFraction()),
                            read.number("attunement.breadth_exponent",d.attunement.breadthExponent())),
                    LatentOreSettingsParser.parse(oreTables, source));
        } catch (BalanceConfigException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            int line = 0;
            for (Map.Entry<String, BalanceToml.Value> entry : values.entrySet()) {
                if (message.startsWith(entry.getKey())) { line = entry.getValue().line(); break; }
            }
            if (line == 0 && message.startsWith("budget.")) line = tableLines.getOrDefault("budget", 0);
            throw BalanceToml.error(source, line, message);
        }
    }

    private static void range(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + "; found " + value);
        }
    }

    private record SettingsReader(Map<String, BalanceToml.Value> values, String source) {
        void schema() {
            BalanceToml.Value value = values.get("schema_version");
            if (value != null && (!(value.value() instanceof Long version) || version != 1L)) {
                throw BalanceToml.error(source, value.line(), "schema_version must be integer 1; replace obsolete development inputs");
            }
        }

        double number(String key, double fallback) {
            BalanceToml.Value value = values.get(key);
            if (value == null) return fallback;
            if (!(value.value() instanceof Number number)) throw BalanceToml.error(source, value.line(), key + " must be a number");
            return number.doubleValue();
        }

        boolean bool(String key, boolean fallback) {
            BalanceToml.Value value = values.get(key);
            if (value == null) return fallback;
            if (!(value.value() instanceof Boolean flag)) throw BalanceToml.error(source, value.line(), key + " must be true or false");
            return flag;
        }

        int integer(String key, int fallback) {
            BalanceToml.Value value = values.get(key);
            if (value == null) return fallback;
            if (!(value.value() instanceof Long number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
                throw BalanceToml.error(source, value.line(), key + " must be a whole integer");
            return number.intValue();
        }

        <E extends Enum<E>> E policy(String key, E fallback, Class<E> type) {
            BalanceToml.Value value = values.get(key);
            if (value == null) return fallback;
            if (value.value() instanceof String text) {
                try { return Enum.valueOf(type, text.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ignored) { /* Report all choices below. */ }
            }
            throw BalanceToml.error(source, value.line(), key + " must be one of "
                    + String.join(", ", java.util.Arrays.stream(type.getEnumConstants()).map(e -> e.name().toLowerCase(Locale.ROOT)).toList()));
        }
    }
}
