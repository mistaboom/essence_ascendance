package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings.DimensionSettings;

/** Configured placement opportunities, independent of unmeasured acquisition or resource supply. */
public final class LatentOreBalanceGenerator {
    private static final int MINIMUM_ATTEMPTS = 12;
    private static final int MINIMUM_VEIN_SIZE = 9;
    private LatentOreBalanceGenerator() { }

    public static LatentOreWorldgenSettings generate(PackEvidence evidence, EconomyProfile economy,
                                                     LatentOreWorldgenSettings inputs) {
        return new LatentOreWorldgenSettings(baseline(inputs.overworld()), baseline(inputs.nether()),
                baseline(inputs.end()), inputs.automaticDimensions(), inputs.dimensions());
    }

    private static DimensionSettings baseline(DimensionSettings input) {
        if (!input.enabled() || input.veinsPerChunk() == 0) return input;
        return new DimensionSettings(true, Math.max(MINIMUM_VEIN_SIZE, input.veinSize()),
                Math.max(MINIMUM_ATTEMPTS, input.veinsPerChunk()),
                input.minY(), input.maxY(), input.discardChanceOnAirExposure());
    }

    /** Saved diagnostics describe policy, never acquisition availability or measured mining rates. */
    public static JsonObject diagnostics(PackEvidence evidence, EconomyProfile economy,
                                         LatentOreWorldgenSettings inputs, LatentOreWorldgenSettings resolved) {
        JsonObject result = new JsonObject();
        result.addProperty("policy", "configured_baseline");
        result.addProperty("acquisitionEvaluated", false);
        result.addProperty("minimumAttempts", MINIMUM_ATTEMPTS);
        result.addProperty("minimumVeinSize", MINIMUM_VEIN_SIZE);
        JsonArray assumptions = new JsonArray();
        assumptions.add("Configured distributions control placement. Resource values and catalog strength do not establish acquisition, supply, or conversion fuel.");
        assumptions.add("Enabled nonzero baseline distributions retain at least 12 attempts of size 9; disabled and zero-attempt settings remain disabled.");
        assumptions.add("Attempts and vein size are placement opportunities, never guaranteed ore counts or measured mining time.");
        assumptions.add("Exact dimension distributions and exact runtime overrides remain authoritative. Only newly generated chunks change.");
        result.add("assumptions", assumptions);
        result.add("inputs", RuntimeBalanceDefinition.worldgenJson(inputs));
        result.add("resolved", RuntimeBalanceDefinition.worldgenJson(resolved));
        return result;
    }
}
