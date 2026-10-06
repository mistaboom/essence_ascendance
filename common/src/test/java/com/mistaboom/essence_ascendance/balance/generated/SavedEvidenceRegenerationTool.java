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
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("Expected existing config directory, isolated output directory and optional diagnostic source");
        Path config = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Path installed = BalanceProfileStore.profilePath(config.resolve("essence_ascendance"));
        Path source = args.length == 3 ? Path.of(args[2]).toAbsolutePath().normalize() : installed;
        if (output.startsWith(config) || output.equals(source.getParent()))
            throw new IllegalArgumentException("Saved-evidence audit output must be isolated from the installed profile directory");
        if (!Files.isRegularFile(source) || !Files.isRegularFile(BalanceInputs.settingsPath(config))
                || !Files.isRegularFile(BalanceInputs.overridesPath(config)))
            throw new IllegalArgumentException("Saved generated evidence and both existing human TOML inputs are required");
        String originalBytes = fileHash(source);
        String installedBytes = Files.exists(installed) ? fileHash(installed) : null;
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
        for (String section : new String[]{"settings", "overrides", "evidence", "economy", "runtime", "skills", "validation"})
            if (!first.document().sectionHash(section).equals(repeated.document().sectionHash(section)))
                throw new AssertionError("Saved evidence replay is not deterministic: " + section);
        var firstMetadata = first.document().section("metadata"); var repeatedMetadata = repeated.document().section("metadata");
        // Measure both runs honestly; elapsed diagnostic cost is the only excluded value.
        firstMetadata.getAsJsonObject("generation").getAsJsonObject("adaptiveBalance").remove("calibrationNanos");
        repeatedMetadata.getAsJsonObject("generation").getAsJsonObject("adaptiveBalance").remove("calibrationNanos");
        if (!firstMetadata.equals(repeatedMetadata)) throw new AssertionError("Saved evidence calibration decisions are not deterministic");
        if (!first.document().sectionHash("evidence").equals(original.sectionHash("evidence"))
                || !first.document().sectionHash("economy").equals(original.sectionHash("economy")))
            throw new AssertionError("Offline runtime rebuild altered authoritative saved evidence/economy");
        // Bonus prices and normalized development references are regenerated together.
        // Historical player skill receipts are world data and are never opened here.
        first.runtime().validate();
        if (!originalBytes.equals(fileHash(source)))
            throw new AssertionError("Installed profile changed during isolated replay");
        if (installedBytes == null ? Files.exists(installed)
                : !Files.exists(installed) || !installedBytes.equals(fileHash(installed)))
            throw new AssertionError("Live profile changed during isolated replay");
        if (!settingsBytes.equals(Files.readString(BalanceInputs.settingsPath(config)))
                || !overridesBytes.equals(Files.readString(BalanceInputs.overridesPath(config))))
            throw new AssertionError("Human inputs changed during replay");
        BalanceProfileStore.replace(BalanceProfileStore.profilePath(output), first.document());
        BalanceReports.export(first, null, output, 0);
        if (!original.section("metadata").has("diagnosticOnly"))
            BalanceProfileStore.writeAtomically(output.resolve("diagnostics/generation_comparison.json"),
                    BalanceDocument.GSON.toJson(comparison(original, first.document())) + "\n");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "SavedEvidenceRegenerationTool PASS: deterministic candidate written only to " + output
                + "; integrity=" + first.document().integrity() + "; saved evidence/economy and human inputs unchanged; Bonus tracks and Attunement regenerated; no world opened");
    }

    private static String fileHash(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = new java.security.DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    /** Compare validated evidence documents from the current profile format. */
    private static com.google.gson.JsonObject comparison(BalanceDocument previous, BalanceDocument current) {
        var result = new com.google.gson.JsonObject();
        result.addProperty("baselineIntegrity", previous.integrity());
        result.addProperty("currentIntegrity", current.integrity());
        result.add("baselineGeneratorRevision", previous.section("metadata").get("generatorRevision"));
        result.add("currentGeneratorRevision", current.section("metadata").get("generatorRevision"));
        result.addProperty("provenance", "Saved evidence and economy replay with matching parsed settings and overrides; no world opened or evidence recollected");
        result.addProperty("baselineCombatCases", previous.section("skills").getAsJsonObject("combinedBuilds").getAsJsonArray("cases").size());
        result.addProperty("currentCombatCases", current.section("skills").getAsJsonObject("combinedBuilds").getAsJsonArray("cases").size());
        var unchanged = new com.google.gson.JsonObject();
        for (String key : new String[]{"equipment", "balanceProfile", "infuser", "shield", "pylons", "crucible", "attunement"})
            unchanged.addProperty(key, previous.section("runtime").get(key).equals(current.section("runtime").get(key)));
        result.add("runtimeSectionEquality", unchanged);
        var beforeProfile = previous.section("runtime").getAsJsonObject("balanceProfile").deepCopy();
        var afterProfile = current.section("runtime").getAsJsonObject("balanceProfile").deepCopy();
        beforeProfile.remove("id"); afterProfile.remove("id");
        stripNativeDiagnostics(beforeProfile); stripNativeDiagnostics(afterProfile);
        result.addProperty("bonusMechanicsAndPolicyUnchanged", beforeProfile.equals(afterProfile));
        result.addProperty("nativeEvidenceBoundary", "Standalone replay omits mod-constructor block registration; step-height collision-shape evidence text may differ without any Bonus effect, cost, cap, checkpoint or availability change");
        var changes = new com.google.gson.JsonObject();
        differences("/runtime", previous.section("runtime"), current.section("runtime"), changes);
        result.add("runtimeValueChanges", changes);
        return result;
    }
    private static void stripNativeDiagnostics(com.google.gson.JsonObject profile) {
        profile.getAsJsonObject("bonusTracks").entrySet().forEach(entry -> entry.getValue().getAsJsonObject().remove("evidence"));
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
