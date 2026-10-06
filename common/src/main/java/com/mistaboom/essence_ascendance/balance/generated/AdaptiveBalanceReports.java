package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.Path;

/** Export saved calibration decisions without executing the calibration or inspecting the environment. */
final class AdaptiveBalanceReports {
    private AdaptiveBalanceReports() { }
    private static final String[] COLUMNS = {"feature", "capability", "band", "path", "baseline", "externalFrontier", "unit",
            "relationship", "share", "adaptiveTarget", "finalGeneratedValue", "limit", "robustCap", "measuredMaximum", "sources", "confidence", "scope", "operation", "applicability"};
    private static final String[] SKILL_COLUMNS = {"skill", "catalogTier", "candidateTier", "generatedTier", "decisionReason",
            "decision", "evidenceStatus", "competitionCounts", "evidenceGaps", "candidateReasonCountsByAxis", "prerequisiteClamps",
            "relevantCandidates", "relevantProviders", "unscopedCandidateReasonCountsGlobal", "unscopedCandidateSamplesGlobal",
            "scopedCandidateCoverageAvailable", "consideredAxes", "prerequisiteRanks", "requirementIds", "witness", "coveragePolicy", "policy",
            "model", "intrinsicDecision", "mechanics", "semantics", "operatingAccess", "rankDecisions"};
    private static JsonObject report(JsonObject metadata) {
        return metadata.has("generation") && metadata.getAsJsonObject("generation").has("adaptiveBalance")
                ? metadata.getAsJsonObject("generation").getAsJsonObject("adaptiveBalance") : null;
    }
    static void saved(SpreadsheetReports tables, JsonObject metadata) {
        JsonObject report = report(metadata); if (report == null) return;
        var table = tables.table("adaptive_balance.csv", COLUMNS);
        for (var element : report.getAsJsonArray("rows")) {
            var row = element.getAsJsonObject(); String[] cells = new String[COLUMNS.length];
            for (int i = 0; i < cells.length; i++) cells[i] = cell(row, COLUMNS[i]);
            table.row(cells);
        }
        if (report.has("skillAvailability")) {
            var availability = tables.table("skill_availability.csv", SKILL_COLUMNS);
            var ranks = tables.table("skill_ranks.csv", "skill", "implementation", "rank", "requiredTier", "price", "measuredOutcomes",
                    "nativeMechanics", "prerequisites", "requirements", "priceBasis");
            for (var element : report.getAsJsonArray("skillAvailability")) {
                var row = element.getAsJsonObject(); String[] cells = new String[SKILL_COLUMNS.length];
                for (int i = 0; i < cells.length; i++) cells[i] = cell(row, SKILL_COLUMNS[i]);
                availability.row(cells);
                if (row.has("rankDecisions")) for (var elementRank : row.getAsJsonArray("rankDecisions")) {
                    var rank = elementRank.getAsJsonObject();
                    ranks.row(cell(row,"skill"), cell(row,"implementation"), cell(rank,"rank"), cell(rank,"requiredTier"),
                            cell(rank,"publishedPrice"), cell(rank,"measuredOutcomes"), cell(rank,"nativeMechanics"),
                            cell(rank,"prerequisites"), cell(rank,"requirements"), rank.toString());
                }
            }
        }
    }
    static void writeDetails(Path folder, JsonObject metadata) throws IOException {
        JsonObject report = report(metadata); if (report == null) return;
        BalanceProfileStore.writeAtomically(folder.resolve("diagnostics/adaptive_balance.json"), BalanceDocument.GSON.toJson(report));
        BalanceProfileStore.writeReport(folder.resolve("reports/adaptive_balance.md"), out -> {
            out.write("# Adaptive skill, bonus and equipment calibration\n\n" + report.get("policy").getAsString()
                    + "\n\nCalibration cost: " + report.get("calibrationNanos").getAsLong() / 1_000_000.0 + " ms. This excludes the capability census and existing runtime scenario validation.\n\n");
            out.write("| Feature / native path | Capability / band | Baseline | External frontier | Relationship / share | Target | Final | Limit | Sources / confidence |\n|---|---|---|---|---|---|---|---|---|\n");
            for (var element : report.getAsJsonArray("rows")) {
                var r = element.getAsJsonObject(); out.write("| " + cell(r,"feature") + " / " + cell(r,"path") + " | " + cell(r,"capability") + " / " + cell(r,"band")
                        + " | " + cell(r,"baseline") + " | " + cell(r,"externalFrontier") + " " + cell(r,"unit")
                        + " | " + cell(r,"relationship") + " / " + cell(r,"share") + " | " + cell(r,"adaptiveTarget")
                        + " | " + cell(r,"finalGeneratedValue") + " | " + cell(r,"limit") + " | " + escape(cell(r,"sources")) + " / " + cell(r,"confidence") + " |\n");
            }
            if (report.has("skillAvailability")) {
                out.write("\n## Generated skill availability\n\nEvery registered skill is considered. Each saved row identifies its decision model when available. Legacy rows retain their original decision reasons; report export does not regenerate or reinterpret saved decisions. Prerequisite ranks, milestones, rank gates and live conditions remain required. PARTIAL/UNKNOWN evidence does not prove that all earlier alternatives were checked. Earlier/same/later counts refer to distinct admitted measurement witnesses per axis/comparison key, excluding repeated cumulative frontiers; one source may supply several measurements. Detailed candidate/provider reasons, uncapped per-axis gap counts and bounded samples are saved in skill_availability.csv and diagnostics/adaptive_balance.json.\n\n| Skill | Catalog | Candidate | Generated | Decision | Evidence | Admitted measurement witnesses | Gaps / prerequisite clamps | Retained requirements |\n|---|---|---|---|---|---|---|---|---|\n");
                for (var element : report.getAsJsonArray("skillAvailability")) {
                    var row = element.getAsJsonObject();
                    out.write("| " + cell(row,"skill") + " | " + cell(row,"catalogTier") + " | " + cell(row,"candidateTier")
                            + " | " + cell(row,"generatedTier") + " | " + escape(cell(row,"decision")) + " | " + cell(row,"evidenceStatus")
                            + " | " + competitionSummary(row) + " | " + escape(cell(row,"evidenceGaps") + " / " + cell(row,"prerequisiteClamps"))
                            + " | " + escape(cell(row,"requirementIds")) + " |\n");
                }
                if (report.has("skillAvailabilityCoverage")) out.write("\nGlobal coverage context: "
                        + escape(report.get("skillAvailabilityCoverage").toString()) + "\n");
            }
            out.write("\n## Unsupported behavior\n\n");
            for (var warning : report.getAsJsonArray("warnings")) out.write("- " + escape(warning.getAsString()) + "\n");
        });
    }
    private static String competitionSummary(JsonObject row) {
        if (!row.has("competitionCounts")) return "legacy report: no scoped counts";
        var counts = row.getAsJsonObject("competitionCounts");
        return "earlier=" + cell(counts,"admittedEarlierTier") + "; same=" + cell(counts,"admittedSameTier")
                + "; later=" + cell(counts,"admittedLaterTier");
    }
    private static String cell(JsonObject row, String column) {
        JsonElement v = row.get(column); return v == null || v.isJsonNull() ? "" : v.isJsonPrimitive() ? v.getAsString() : v.toString();
    }
    private static String escape(String text) { return text.replace("|", "\\|").replace("\n", " ").replace("\r", " "); }
}
