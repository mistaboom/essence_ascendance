package com.mistaboom.essence_ascendance.balance.generated;

import com.mistaboom.essence_ascendance.balance.config.BalanceInputs;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit export of an installed profile; no generation, installation, or world access. */
public final class ExportSavedBalanceTool {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> error.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        if (args.length < 1 || args.length > 2 || args.length == 2
                && !List.of("--measure-heap", "--measure-phases").contains(args[1]))
            throw new IllegalArgumentException("Expected existing config directory and optional --measure-heap or --measure-phases");
        boolean measureHeap = args.length == 2 && args[1].equals("--measure-heap");
        boolean measurePhases = args.length == 2 && args[1].equals("--measure-phases");
        Path config = Path.of(args[0]).toAbsolutePath().normalize();
        Path folder = config.resolve("essence_ascendance");
        Path profile = BalanceProfileStore.profilePath(folder);
        Map<Path, String> protectedContents = new LinkedHashMap<>();
        for (Path path : List.of(profile, BalanceInputs.settingsPath(config), BalanceInputs.overridesPath(config))) {
            if (!Files.isRegularFile(path))
                throw new IllegalArgumentException("Saved generated profile and both existing human TOML inputs are required: " + path);
            protectedContents.put(path, fileHash(path));
        }

        GeneratedBalanceService.Active current;
        try {
            SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
            EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
            MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
            com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
            if (measureHeap) {
                measureRetainedHeap(profile);
                return;
            }
            try (var operation = BalancePerformance.begin("saved_profile_offline_decode",
                    measurePhases ? "readonly_phase_probe" : "readonly_export")) {
                try {
                    BalancePerformance.flag("saved_data_reused", true);
                    BalancePerformance.flag("environment_analysis_rescanned", false);
                    BalancePerformance.flag("identity_rescanned", false);
                    BalancePerformance.flag("reports_regenerated", false);
                    BalancePerformance.detail("environment_validation", "Offline decode only; no live pack fingerprint available");
                    current = GeneratedBalanceService.decode(BalanceProfileStore.read(profile));
                    if (!measurePhases) {
                        try (var phase = BalancePerformance.phase("offline_report_export")) {
                            BalanceReports.export(current, null, folder, 0);
                            BalancePerformance.flag("reports_regenerated", true);
                        }
                    }
                    operation.complete("validated_offline");
                } catch (Exception | Error error) {
                    operation.fail(error);
                    throw error;
                }
            }
            new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                    "BALANCE_PERFORMANCE_OFFLINE " + BalanceDocument.GSON.toJson(BalancePerformance.lastSnapshot()));
        } finally {
            for (var entry : protectedContents.entrySet()) {
                if (!entry.getValue().equals(fileHash(entry.getKey())))
                    throw new AssertionError("Report export changed a protected profile or human input: " + entry.getKey());
            }
        }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "ExportSavedBalanceTool PASS: " + (measurePhases ? "phases measured without report writes in " : "reports exported from installed profile in ") + folder
                        + "; integrity=" + current.document().integrity()
                        + "; profile and both human TOML inputs byte-identical; no generation or world opened");
    }

    /** Explicit isolated-tool diagnostic only; never invoked by Minecraft or ordinary export. */
    private static void measureRetainedHeap(Path path) throws Exception {
        long baseline = retainedHeap(null);
        BalanceDocument document = BalanceProfileStore.read(path);
        long documentHeap = retainedHeap(document);
        GeneratedBalanceService.Active current = GeneratedBalanceService.decode(document);
        // The active snapshot may own a compact document rather than the parsed tree.
        document = null;
        long activeHeap = retainedHeap(current);
        String integrity = current.document().integrity();
        Object[] typed = {current.evidence(), current.economy(), current.runtime()};
        var documentReference = new java.lang.ref.WeakReference<>(current.document());
        current = null;
        long typedHeap = retainedHeap(typed);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "Saved-profile isolated retained-heap measurement: baselineBytes=" + baseline
                        + "; documentOnlyBytes=" + documentHeap + "; activeBytes=" + activeHeap
                        + "; typedOnlyBytes=" + typedHeap + "; releasedDocumentBytes=" + (activeHeap - typedHeap)
                        + "; documentCollected=" + (documentReference.get() == null)
                        + "; integrity=" + integrity
                        + "; no reports written, no generation or world opened; protected inputs checked on exit");
        java.lang.ref.Reference.reachabilityFence(typed);
    }

    private static long retainedHeap(Object keepAlive) {
        // Deliberately requested only in this short-lived diagnostic JVM, never in the game.
        System.gc();
        long used = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        java.lang.ref.Reference.reachabilityFence(keepAlive);
        return used;
    }

    private static String fileHash(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = new java.security.DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
