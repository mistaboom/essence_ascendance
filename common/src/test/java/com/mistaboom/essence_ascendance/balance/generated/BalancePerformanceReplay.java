package com.mistaboom.essence_ascendance.balance.generated;

import com.mistaboom.essence_ascendance.balance.config.BalanceInputs;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.nio.file.*;
import java.util.*;

/** Opt-in read-only ATM profile replay. Writes only isolated diagnostics; never installs or opens a world. */
public final class BalancePerformanceReplay {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> error.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        Path config = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (output.startsWith(config)) throw new IllegalArgumentException("Replay output must be outside live config");
        Files.createDirectories(output);
        String mode = args[2];
        if (!Set.of("load", "runtime", "capabilities").contains(mode)) throw new IllegalArgumentException("Replay mode must be load, runtime or capabilities");
        String expectedSource = System.getProperty("balance.replay.expectedCodeSource");
        if (expectedSource != null) {
            Path expected = Path.of(java.net.URI.create(expectedSource));
            for (Class<?> type : List.of(BalanceDocument.class, GeneratedBalanceService.class,
                    com.mistaboom.essence_ascendance.balance.runtime.RuntimeBuildScenarios.class))
                if (!Files.isSameFile(expected, Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())))
                    throw new AssertionError("Replay baseline class was not loaded from the requested artifact: " + type);
        }
        boolean profile = Boolean.parseBoolean(args[3]);
        var paths = List.of(BalanceProfileStore.profilePath(config.resolve("essence_ascendance")),
                BalanceInputs.settingsPath(config), BalanceInputs.overridesPath(config));
        Map<Path, String> protectedHashes = new LinkedHashMap<>();
        for (var path : paths) protectedHashes.put(path, hash(path));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        try (var recording = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))) {
            recording.setMaxSize(128L * 1024 * 1024);
            recording.setDuration(java.time.Duration.ofMinutes(10));
            if (profile && mode.equals("load")) recording.start();
            GeneratedBalanceService.Active active;
            try (var operation = BalancePerformance.begin("offline_profile_load", "chat_02b_replay")) {
                active = GeneratedBalanceService.decode(BalanceProfileStore.read(paths.getFirst()));
                operation.complete("validated_offline");
            }
            write(output.resolve("load.json"), BalancePerformance.lastSnapshot());
            var counts = BalancePerformance.lastSnapshot().counts();
            if (counts.getOrDefault("profile_reads", 0L) != 1 || counts.getOrDefault("profile_json_parses", 0L) != 1
                    || counts.getOrDefault("profile_decodes", 0L) != 1) throw new AssertionError("Saved load duplicated document work");
            for (String forbidden : List.of("full_generation_runs", "evidence_collection_runs", "conservation_solve_runs", "runtime_generation_runs",
                    "report_export_runs", "valuation_snapshot_collections", "production_graph_collections", "valuation_index_builds",
                    "quest_definition_normalizations", "quest_progression_rule_evaluations", "competitive_capability_runs", "adaptive_calibration_runs"))
                if (counts.getOrDefault(forbidden, 0L) != 0) throw new AssertionError("Saved load invoked " + forbidden);
            Map<String, String> semantic = new TreeMap<>();
            for (String section : List.of("metadata", "settings", "overrides", "evidence", "economy", "runtime", "skills", "validation"))
                semantic.put(section, active.document().sectionHash(section));
            write(output.resolve("semantic-profile.json"), semantic);
            if (mode.equals("capabilities")) {
                var report = com.mistaboom.essence_ascendance.balance.engine.CompetitiveCapabilities.replay(active.evidence(),
                        new com.mistaboom.essence_ascendance.balance.economy.ProductionGraph(active.economy().processes(), active.economy().warnings()));
                CompetitiveCapabilityReports.write(output, report);
            }
            if (mode.equals("runtime")) {
                var inputs = BalanceInputs.read(config);
                if (profile) recording.start();
                RuntimeBalanceDefinition runtime;
                try (var operation = BalancePerformance.begin("offline_runtime_generation", "saved_evidence_no_recollection")) {
                    var generation = active.document().section("metadata").getAsJsonObject("generation");
                    var calibration = new com.mistaboom.essence_ascendance.balance.runtime.AdaptiveCompetitionCalibration(active.evidence(),
                            generation != null && generation.has("competitiveCapabilities") ? generation.getAsJsonObject("competitiveCapabilities") : null);
                    try (var phase = BalancePerformance.phase("runtime_generation")) {
                        runtime = com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceGenerator.generate(
                                active.evidence(), active.economy(), inputs.settings(), inputs.overrides(), calibration);
                    }
                    var metadata = new com.google.gson.JsonObject(); var diagnostics = new com.google.gson.JsonObject();
                    diagnostics.add("adaptiveBalance", calibration.complete(runtime)); metadata.add("generation", diagnostics);
                    AdaptiveBalanceReports.writeDetails(output, metadata);
                    try (var tables = new SpreadsheetReports()) {
                        AdaptiveBalanceReports.saved(tables, metadata); tables.write(output.resolve("reports"), output.resolve("diagnostics"));
                    }
                    operation.complete("generated_offline");
                }
                write(output.resolve("runtime-timing.json"), BalancePerformance.lastSnapshot());
                // Complete canonical runtime AND generation analysis, no field exclusions.
                write(output.resolve("runtime.json"), runtime.toJson());
                write(output.resolve("analysis.json"), runtime.generationAnalysis());
            }
            if (profile) { recording.stop(); recording.dump(output.resolve(mode + ".jfr")); }
            write(output.resolve("identity.json"), Map.of("java", System.getProperty("java.runtime.version"),
                    "profileIntegrity", active.document().integrity(), "profiled", profile,
                    "maxHeapBytes", Runtime.getRuntime().maxMemory(),
                    "documentCodeSource", BalanceDocument.class.getProtectionDomain().getCodeSource().getLocation().toString(),
                    "runtimeCodeSource", com.mistaboom.essence_ascendance.balance.runtime.RuntimeBuildScenarios.class.getProtectionDomain().getCodeSource().getLocation().toString(),
                    "mode", mode, "boundary", "Isolated replay; no Minecraft environment collection or startup",
                    "protectedFiles", protectedHashes.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey().toString(), Map.Entry::getValue))));
        } finally {
            for (var entry : protectedHashes.entrySet())
                if (!entry.getValue().equals(hash(entry.getKey()))) throw new AssertionError("Changed protected input " + entry.getKey());
        }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("BalancePerformanceReplay PASS " + output);
    }
    private static void write(Path path, Object value) throws Exception {
        try (var writer = Files.newBufferedWriter(path)) { BalanceDocument.GSON.toJson(value, writer); }
    }
    private static String hash(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = new java.security.DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
