package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence;
import com.mistaboom.essence_ascendance.balance.engine.RobustFrontiers;
import java.nio.file.Path;
import java.io.IOException;

/** Derived saved evidence only. No environment access or provider dispatch. */
public final class CompetitiveCapabilityReports {
    private CompetitiveCapabilityReports() { }
    static void tables(SpreadsheetReports tables, JsonObject report) {
        var evidence = tables.table("competitive_capabilities.csv", "source", "configuration", "stage", "reachable", "attainable",
                "family", "axis", "magnitude", "unit", "scope", "operation", "applicability", "confidence", "provider", "provenance", "acquisition", "unsupported");
        for (var element : report.getAsJsonArray("evidence")) {
            var fact = BalanceDocument.GSON.fromJson(element, CapabilityEvidence.Functional.class);
            for (var m : fact.measurements()) evidence.row(fact.source().subjectId(), fact.configuration(), fact.source().stage().name(),
                    Boolean.toString(fact.source().reachable()), Boolean.toString(fact.attainable()), m.family().name(), m.axis().name(),
                    m.magnitude() == null ? "" : SpreadsheetReports.number(m.magnitude()), m.unit(), BalanceDocument.GSON.toJson(m.scope()),
                    BalanceDocument.GSON.toJson(m.operation()), m.applicability(), SpreadsheetReports.number(fact.source().confidence()), m.provider(),
                    m.origin() + ": " + fact.source().reason(), BalanceDocument.GSON.toJson(fact.acquisition()), m.unsupported().toString());
        }
        var frontiers = tables.table("competitive_frontiers.csv", "band", "comparison_key", "magnitude", "measured_maximum", "capped",
                "representative", "configuration", "scope", "operation", "acquisition", "confidence", "provider", "unsupported");
        for (var element : report.getAsJsonArray("frontiers")) {
            var frontier = BalanceDocument.GSON.fromJson(element, RobustFrontiers.CompetitiveFrontier.class);
            for (var fact : frontier.representatives()) {
                var m = fact.measurements().stream().filter(axis -> axis.comparisonKey().equals(frontier.comparisonKey())).findFirst().orElseThrow();
                frontiers.row(frontier.band().name(), frontier.comparisonKey(), SpreadsheetReports.number(frontier.magnitude()),
                        SpreadsheetReports.number(frontier.measuredMaximum()), Boolean.toString(frontier.capped()), fact.source().subjectId(), fact.configuration(),
                        BalanceDocument.GSON.toJson(m.scope()), BalanceDocument.GSON.toJson(m.operation()), BalanceDocument.GSON.toJson(fact.acquisition()),
                        SpreadsheetReports.number(fact.source().confidence()), m.provider() + ": " + fact.source().reason(), m.unsupported().toString());
            }
        }
        var candidates = tables.table("competitive_candidates.csv", "source", "provider", "detail");
        for (var element : report.getAsJsonArray("candidates")) {
            var row = element.getAsJsonObject(); candidates.row(row.get("subject").getAsString(), row.get("provider").getAsString(), row.get("detail").getAsString());
        }
        var unknown = tables.table("competitive_unsupported_counts.csv", "provider_and_limitation", "count");
        report.getAsJsonObject("unsupportedCounts").entrySet().forEach(e -> unknown.row(e.getKey(), e.getValue().getAsString()));
    }
    public static void write(Path folder, JsonObject report) throws IOException {
        try (var tables = new SpreadsheetReports()) { tables(tables, report); tables.write(folder.resolve("reports"), folder.resolve("diagnostics")); }
        writeDetails(folder, report);
    }
    public static void writeDetails(Path folder, JsonObject report) throws IOException {
        BalanceProfileStore.writeReport(folder.resolve("reports/competitive_capabilities.md"), out -> {
            out.write("# Competitive capability evidence\n\n" + report.get("purpose").getAsString() + "\n\n");
            if (report.has("basis")) out.write(report.get("basis").getAsString() + "\n\n");
            out.write("Measurements retain physical units, applicability and operating conditions. Potential configurations and unknown rates do not define attainable numeric power. Earlier usable sources remain in later bands. Alternatives are not summed. A capped frontier retains the raw maximum and actual source scope; it is not a fabricated configuration.\n\n");
            for (var band : com.mistaboom.essence_ascendance.balance.engine.ProgressionBand.values()) {
                out.write("## " + band + "\n\n| Family / axis | Magnitude / unit | Scope | Representative | Access / confidence / provider |\n|---|---|---|---|---|\n");
                for (var element : report.getAsJsonArray("frontiers")) {
                    var frontier = BalanceDocument.GSON.fromJson(element, RobustFrontiers.CompetitiveFrontier.class);
                    if (frontier.band() != band || frontier.representatives().isEmpty()) continue;
                    var fact = frontier.representatives().getFirst();
                    var m = fact.measurements().stream().filter(axis -> axis.comparisonKey().equals(frontier.comparisonKey())).findFirst().orElseThrow();
                    out.write("| " + m.family() + " / " + m.axis() + " | " + SpreadsheetReports.number(frontier.magnitude()) + " " + escape(m.unit())
                            + (frontier.capped() ? " (robust cap; raw=" + frontier.measuredMaximum() + ")" : "") + " | " + escape(m.scope().toString()) + " | "
                            + escape(fact.source().subjectId() + " / " + fact.configuration()) + " | "
                            + escape(fact.acquisition().stream().map(s -> s.id() + ": " + s.reason()).toList().toString()) + " / "
                            + fact.source().confidence() + " / " + escape(m.provider()) + " |\n");
                }
            }
            out.write("\n## Unsupported competitors\n\nCounts include all observations; representatives are bounded to 512. Full measurements, conditions, costs, provider provenance and uncertainties are in the CSVs and diagnostic JSON.\n\n");
            for (var element : report.getAsJsonArray("candidates")) {
                var candidate = element.getAsJsonObject(); out.write("- " + escape(candidate.get("subject").getAsString()) + " ("
                        + escape(candidate.get("provider").getAsString()) + "): " + escape(candidate.get("detail").getAsString()) + "\n");
            }
        });
        BalanceProfileStore.writeAtomically(folder.resolve("diagnostics/competitive_capabilities.json"), BalanceDocument.GSON.toJson(report));
    }
    static void saved(SpreadsheetReports tables, JsonObject metadata) {
        if (metadata.has("generation") && metadata.getAsJsonObject("generation").has("competitiveCapabilities"))
            tables(tables, metadata.getAsJsonObject("generation").getAsJsonObject("competitiveCapabilities"));
    }
    private static String escape(String value) { return value.replace("|", "\\|").replace("\r", " ").replace("\n", " "); }
}
