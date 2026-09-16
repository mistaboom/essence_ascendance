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
        if (output.equals(source.getParent()))
            throw new IllegalArgumentException("Saved-evidence audit output must be isolated from the installed profile directory");
        if (!Files.isRegularFile(source) || !Files.isRegularFile(BalanceInputs.settingsPath(config))
                || !Files.isRegularFile(BalanceInputs.overridesPath(config)))
            throw new IllegalArgumentException("Saved generated evidence and both existing human TOML inputs are required");
        byte[] originalBytes = Files.readAllBytes(source);
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
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(source)))
            throw new AssertionError("Installed profile changed during isolated replay");
        if (!settingsBytes.equals(Files.readString(BalanceInputs.settingsPath(config)))
                || !overridesBytes.equals(Files.readString(BalanceInputs.overridesPath(config))))
            throw new AssertionError("Human inputs changed during replay");
        BalanceProfileStore.replace(output.resolve("generated_balance.json"), first.document());
        BalanceReports.export(first, null, output, 0);
        BalanceProfileStore.writeAtomically(output.resolve("diagnostics/generation_comparison.json"),
                BalanceDocument.GSON.toJson(comparison(original, first.document())) + "\n");
        if (original.section("metadata").get("generatorRevision").getAsString().equals("smooth-bonus-tracks-19"))
            requireVitalityPreservation(original, first.document());
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "SavedEvidenceRegenerationTool PASS: deterministic current runtime installed in " + output
                + "; integrity=" + first.document().integrity() + "; saved evidence/economy and human inputs unchanged; Bonus tracks and Attunement regenerated; no world opened");
    }

    private static void requireVitalityPreservation(BalanceDocument previous, BalanceDocument current) {
        var before = previous.section("runtime"); var after = current.section("runtime");
        for (String key : new String[]{"equipment", "statMaxBonuses", "infuser", "shield", "pylons", "crucible", "attunement", "milestones", "advancements", "worldgen"})
            if (!before.get(key).equals(after.get(key))) throw new AssertionError("Vitality changed unrelated runtime section " + key);
        var oldProfile = before.getAsJsonObject("balanceProfile").deepCopy();
        var newProfile = after.getAsJsonObject("balanceProfile").deepCopy();
        oldProfile.remove("id"); newProfile.remove("id");
        stripNativeDiagnostics(oldProfile); stripNativeDiagnostics(newProfile);
        if (!oldProfile.equals(newProfile)) throw new AssertionError("Vitality changed Bonus tracks, costs or tier policy");
        for (var entry : before.getAsJsonObject("effects").entrySet())
            if (!entry.getValue().equals(after.getAsJsonObject("effects").get(entry.getKey())))
                throw new AssertionError("Vitality changed prior skill settings " + entry.getKey());
        var vitalityIds = java.util.Set.of("essence_ascendance:rising_recovery", "essence_ascendance:life_steal", "essence_ascendance:feast_reflex", "essence_ascendance:inner_sustenance");
        var projectedPostures = java.util.Set.of("essence_ascendance:evasive_current", "essence_ascendance:bulwark_stance", "essence_ascendance:adaptive_guard");
        for (var entry : before.getAsJsonObject("skillCurves").entrySet()) {
            var changed = after.getAsJsonObject("skillCurves").getAsJsonObject(entry.getKey());
            if (!vitalityIds.contains(entry.getKey()) && !projectedPostures.contains(entry.getKey()) && !entry.getValue().equals(changed))
                throw new AssertionError("Vitality changed prior skill curve " + entry.getKey());
            var oldRanks = entry.getValue().getAsJsonObject().getAsJsonArray("ranks");
            for (int i = 0; i < oldRanks.size(); i++)
                if (!oldRanks.get(i).getAsJsonObject().get("cost").equals(changed.getAsJsonArray("ranks").get(i).getAsJsonObject().get("cost")))
                    throw new AssertionError("Vitality changed skill price " + entry.getKey());
            if (changed.get("maximumRank").getAsInt() != 1) throw new AssertionError("Vitality enabled purchasable ranks");
            if (!oldRanks.get(0).equals(changed.getAsJsonArray("ranks").get(0)))
                throw new AssertionError("Vitality changed current purchasable rank " + entry.getKey());
        }
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
