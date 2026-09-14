package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.BalanceInputs;
import com.mistaboom.essence_ascendance.balance.economy.EconomyGenerator;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;

/** Explicit offline runtime rebuild from intact saved pack evidence, without loading a world. */
public final class SavedEvidenceRegenerator {
    private SavedEvidenceRegenerator() { }

    public static GeneratedBalanceService.Active regenerate(BalanceDocument source, BalanceInputs inputs) {
        var gson = BalanceDocument.GSON;
        // Evidence and economy were collected with these exact inputs. A changed
        // input requires native recollection, not a misleading offline freshness claim.
        if (!source.section("settings").equals(gson.toJsonTree(inputs.settings()))
                || !source.section("overrides").equals(gson.toJsonTree(inputs.overrides())))
            throw new IllegalArgumentException("Saved evidence inputs differ from current TOML; rebuild in game to recollect the pack");
        PackEvidence evidence = gson.fromJson(source.section("evidence"), PackEvidence.class);
        EconomyProfile economy = gson.fromJson(source.section("economy"), EconomyProfile.class);
        if (!source.section("metadata").get("evidenceDigest").getAsString().equals(BalanceDocument.hash(gson.toJsonTree(evidence))))
            throw new IllegalArgumentException("Saved evidence digest mismatch");
        EconomyGenerator.validate(economy);
        EconomyGenerator.validateWhole(economy);
        RuntimeBalanceDefinition runtime = RuntimeBalanceDefinition.generate(evidence, economy, inputs.settings(), inputs.overrides());
        JsonObject metadata = source.section("metadata");
        metadata.addProperty("generatorRevision", GeneratedBalanceService.GENERATION_REVISION);
        metadata.addProperty("settingsFingerprint", inputs.settingsFingerprint());
        metadata.addProperty("overridesFingerprint", inputs.overridesFingerprint());
        metadata.addProperty("runtimeRebuild", "Saved pack evidence and economy replay; no world opened, no evidence recollected, no live gameplay observed");
        JsonObject validation = source.section("validation");
        validation.addProperty("runtime", "passed");
        validation.addProperty("economy", "passed; saved production evidence retained");
        validation.addProperty("serialization", "passed");
        validation.addProperty("projectiles", "validated independent path/payload and bounded control policy; native gameplay remains manual");
        validation.addProperty("guard", "validated guard mobility, reflection and counterattack bounds; live gameplay remains manual");
        validation.addProperty("postureStatus", "validated generated posture mitigation and binary harmful-status capability; conditional native gameplay remains manual");
        validation.addProperty("liveGameplay", "not performed by saved-evidence replay");
        JsonObject content = new JsonObject();
        content.add("metadata", metadata);
        for (String section : new String[]{"settings", "overrides", "evidence", "economy"}) content.add(section, source.section(section));
        content.add("runtime", runtime.toJson());
        content.add("skills", GeneratedBalanceService.skillDiagnostics(runtime));
        content.add("validation", validation);
        return GeneratedBalanceService.decode(BalanceDocument.seal(content));
    }
}
