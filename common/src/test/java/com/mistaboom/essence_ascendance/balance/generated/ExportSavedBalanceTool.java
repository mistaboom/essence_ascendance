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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit export of an installed profile; no generation, installation, or world access. */
public final class ExportSavedBalanceTool {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> error.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        if (args.length != 1) throw new IllegalArgumentException("Expected existing config directory");
        Path config = Path.of(args[0]).toAbsolutePath().normalize();
        Path folder = config.resolve("essence_ascendance");
        Path profile = folder.resolve("generated_balance.json");
        Map<Path, byte[]> protectedContents = new LinkedHashMap<>();
        for (Path path : List.of(profile, BalanceInputs.settingsPath(config), BalanceInputs.overridesPath(config))) {
            if (!Files.isRegularFile(path))
                throw new IllegalArgumentException("Saved generated profile and both existing human TOML inputs are required: " + path);
            protectedContents.put(path, Files.readAllBytes(path));
        }

        GeneratedBalanceService.Active current;
        try {
            SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
            EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
            MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
            com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
            current = GeneratedBalanceService.decode(BalanceProfileStore.read(profile));
            BalanceReports.export(current, null, folder, 0);
        } finally {
            for (var entry : protectedContents.entrySet()) {
                if (!Arrays.equals(entry.getValue(), Files.readAllBytes(entry.getKey())))
                    throw new AssertionError("Report export changed a protected profile or human input: " + entry.getKey());
            }
        }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "ExportSavedBalanceTool PASS: reports exported from installed profile in " + folder
                        + "; integrity=" + current.document().integrity()
                        + "; profile and both human TOML inputs byte-identical; no generation or world opened");
    }
}
