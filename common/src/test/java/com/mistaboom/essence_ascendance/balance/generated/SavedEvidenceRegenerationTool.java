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
        // Bonus prices and normalized development references are regenerated together.
        // Historical player skill receipts are world data and are never opened here.
        first.runtime().validate();
        if (!settingsBytes.equals(Files.readString(BalanceInputs.settingsPath(config)))
                || !overridesBytes.equals(Files.readString(BalanceInputs.overridesPath(config))))
            throw new AssertionError("Human inputs changed during replay");
        BalanceProfileStore.replace(output.resolve("generated_balance.json"), first.document());
        BalanceReports.export(first, null, output, 0);
        BalanceProfileStore.writeAtomically(output.resolve("diagnostics/generation_comparison.json"),
                BalanceDocument.GSON.toJson(comparison(original, first.document())) + "\n");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "SavedEvidenceRegenerationTool PASS: deterministic current runtime installed in " + output
                + "; integrity=" + first.document().integrity() + "; saved evidence/economy and human inputs unchanged; Bonus tracks and Attunement regenerated; no world opened");
    }

    /** Compare raw validated evidence documents; no discarded runtime-schema adapter is needed. */
    private static com.google.gson.JsonObject comparison(BalanceDocument previous, BalanceDocument current) {
        var result = new com.google.gson.JsonObject();
        result.addProperty("baselineIntegrity", previous.integrity());
        result.addProperty("currentIntegrity", current.integrity());
        result.add("baselineGeneratorRevision", previous.section("metadata").get("generatorRevision"));
        result.add("currentGeneratorRevision", current.section("metadata").get("generatorRevision"));
        result.addProperty("provenance", "Saved evidence and economy replay with exact original human-input fingerprints; no world opened or evidence recollected");
        result.addProperty("baselineCombatCases", previous.section("skills").getAsJsonObject("combinedBuilds").getAsJsonArray("cases").size());
        result.addProperty("currentCombatCases", current.section("skills").getAsJsonObject("combinedBuilds").getAsJsonArray("cases").size());
        var unchanged = new com.google.gson.JsonObject();
        for (String key : new String[]{"equipment", "balanceProfile", "infuser", "shield", "pylons", "crucible", "attunement"})
            unchanged.addProperty(key, previous.section("runtime").get(key).equals(current.section("runtime").get(key)));
        result.add("runtimeSectionEquality", unchanged);
        var changes = new com.google.gson.JsonObject();
        differences("/runtime", previous.section("runtime"), current.section("runtime"), changes);
        result.add("runtimeValueChanges", changes);
        return result;
    }
    private static void differences(String path, com.google.gson.JsonElement before, com.google.gson.JsonElement after,
                                    com.google.gson.JsonObject changes) {
        if (java.util.Objects.equals(before, after)) return;
        if (before != null && after != null && before.isJsonObject() && after.isJsonObject()) {
            var keys = new java.util.TreeSet<>(before.getAsJsonObject().keySet()); keys.addAll(after.getAsJsonObject().keySet());
            for (String key : keys) differences(path + "/" + key.replace("~", "~0").replace("/", "~1"),
                    before.getAsJsonObject().get(key), after.getAsJsonObject().get(key), changes);
        } else if (before != null && after != null && before.isJsonArray() && after.isJsonArray()
                && before.getAsJsonArray().size() == after.getAsJsonArray().size()) {
            for (int i = 0; i < before.getAsJsonArray().size(); i++) differences(path + "/" + i,
                    before.getAsJsonArray().get(i), after.getAsJsonArray().get(i), changes);
        } else {
            var pair = new com.google.gson.JsonObject(); pair.add("before", before); pair.add("after", after); changes.add(path, pair);
        }
    }
}
