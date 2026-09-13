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

/** Explicit maintenance command; consumes saved evidence and touches only generated profile/exports. */
public final class SavedEvidenceRegenerationTool {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread,error) -> error.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        if (args.length != 2) throw new IllegalArgumentException("Expected existing config directory and output generated-profile directory");
        Path config = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Path source = config.resolve("essence_ascendance/generated_balance.json");
        if (!Files.isRegularFile(source) || !Files.isRegularFile(BalanceInputs.settingsPath(config))
                || !Files.isRegularFile(BalanceInputs.overridesPath(config)))
            throw new IllegalArgumentException("Saved generated evidence and both existing human TOML inputs are required");
        var original = BalanceProfileStore.read(source);
        String settingsBytes = Files.readString(BalanceInputs.settingsPath(config));
        String overridesBytes = Files.readString(BalanceInputs.overridesPath(config));
        var inputs = BalanceInputs.read(config);
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var first = SavedEvidenceRegenerator.regenerate(original, inputs);
        var repeated = SavedEvidenceRegenerator.regenerate(original, inputs);
        if (!first.document().text().equals(repeated.document().text())) throw new AssertionError("Saved evidence replay is not deterministic");
        if (!first.document().section("evidence").equals(original.section("evidence"))
                || !first.document().section("economy").equals(original.section("economy")))
            throw new AssertionError("Offline runtime rebuild altered authoritative saved evidence/economy");
        if (!first.document().section("runtime").get("attunement").equals(original.section("runtime").get("attunement")))
            throw new AssertionError("Projectile batch unexpectedly retuned Category Attunement");
        if (!settingsBytes.equals(Files.readString(BalanceInputs.settingsPath(config)))
                || !overridesBytes.equals(Files.readString(BalanceInputs.overridesPath(config))))
            throw new AssertionError("Human inputs changed during replay");
        BalanceProfileStore.replace(output.resolve("generated_balance.json"), first.document());
        BalanceReports.export(first, null, output, 0);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "SavedEvidenceRegenerationTool PASS: deterministic current runtime installed in " + output
                + "; integrity=" + first.document().integrity() + "; saved evidence/economy/Attunement and human inputs unchanged; no world opened");
    }
}
