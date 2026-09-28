package com.mistaboom.essence_ascendance.balance.generated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Export migration must preserve old reports and never touch authoritative inputs. */
public final class BalanceReportLayoutTest {
    private static final String CURRENT_INTEGRITY = "installed-profile-integrity";

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("balance-report-layout-");
        try {
            List<String> protectedNames = List.of("generated_balance.json", BalanceProfileStore.PROFILE_FILE, "balance_overrides.toml", "my_notes.csv");
            for (String name : protectedNames) Files.writeString(root.resolve(name), "preserve " + name);
            Files.createDirectory(root.resolve("equipment.csv"));
            Files.writeString(root.resolve("equipment.csv/notes.txt"), "directory must not move");
            Files.writeString(root.resolve("valuation.csv"), "old valuation");
            Files.writeString(root.resolve("pack_metadata.json"), "old metadata");
            Files.createDirectories(BalanceReportLayout.reports(root));
            Files.writeString(BalanceReportLayout.reports(root).resolve("valuation.csv"), "current valuation");
            Path archives = BalanceReportLayout.diagnostics(root).resolve("legacy_reports");
            Files.createDirectories(archives.resolve("export-1"));
            Files.writeString(archives.resolve("export-1/valuation.csv"), "older archive");

            BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
            require(Files.readString(archives.resolve("export-1/valuation.csv")).equals("older archive"), "Existing archive overwritten");
            require(Files.readString(archives.resolve("export-2/valuation.csv")).equals("old valuation"), "Old report contents lost");
            require(Files.readString(archives.resolve("export-2/pack_metadata.json")).equals("old metadata"), "Old metadata lost");
            require(!Files.exists(root.resolve("valuation.csv")), "Stale report still competes with current output");
            require(Files.readString(BalanceReportLayout.reports(root).resolve("valuation.csv")).equals("current valuation"), "Current report changed");
            for (String name : protectedNames)
                require(Files.readString(root.resolve(name)).equals("preserve " + name), "Unrelated or authoritative file changed: " + name);
            require(Files.readString(root.resolve("equipment.csv/notes.txt")).equals("directory must not move"), "Unexpected directory traversed");
            String guide = Files.readString(root.resolve("README_REPORTS.txt"));
            require(guide.contains("/essence admin balance export"), "Current report-export command missing from file guide");
            require(!guide.contains("/essence debug balance export"), "Retired report-export alias advertised in file guide");

            BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
            require(!Files.exists(archives.resolve("export-3")), "Repeat export created an empty archive");
            Files.writeString(root.resolve("valuation.csv"), "another old report");
            BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
            require(Files.readString(archives.resolve("export-3/valuation.csv")).equals("another old report"), "Subsequent legacy export not preserved");
            comparisonArchives(root.resolve("comparison-tests"));
            System.out.println("BalanceReportLayoutTest: archive collisions, repeated export, comparison identity and malformed comparisons, current output and input preservation PASS");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void comparisonArchives(Path root) throws Exception {
        Path diagnostics = BalanceReportLayout.diagnostics(root);
        Files.createDirectories(diagnostics);
        Path comparison = diagnostics.resolve("generation_comparison.json");
        Path archives = diagnostics.resolve("legacy_reports");
        String current = "{\n  \"currentIntegrity\": \"" + CURRENT_INTEGRITY
                + "\", \"runtimeValueChanges\": {\"path\": {\"before\": 1, \"after\": 2}}\n}\n";
        Files.writeString(comparison, current);
        BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
        BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
        require(Files.readString(comparison).equals(current), "Current comparison was rewritten or archived");
        require(!Files.exists(archives), "Matching comparison created an archive");

        Files.createDirectories(archives.resolve("export-1"));
        Files.writeString(archives.resolve("export-1/generation_comparison.json"), "older comparison archive");
        Files.writeString(archives.resolve("export-2"), "occupied archive name");
        String stale = "{\"currentIntegrity\":\"previous-profile\",\"provenance\":\"preserve historical evidence\"}\n";
        Files.writeString(comparison, stale);
        BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
        require(!Files.exists(comparison), "Stale comparison still appears current");
        require(Files.readString(archives.resolve("export-3/generation_comparison.json")).equals(stale), "Stale comparison contents lost");
        require(Files.readString(archives.resolve("export-1/generation_comparison.json")).equals("older comparison archive"), "Comparison archive overwritten");
        require(Files.readString(archives.resolve("export-2")).equals("occupied archive name"), "Occupied archive name overwritten");
        BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
        require(!Files.exists(archives.resolve("export-4")), "Repeat comparison export created an empty archive");

        List<String> untrustedComparisons = List.of("", "{", "[]", "{}", "{\"currentIntegrity\":null}",
                "{\"currentIntegrity\":123}", "{\"currentIntegrity\":[]}", current + "trailing text",
                "{currentIntegrity:\"" + CURRENT_INTEGRITY + "\"}",
                "{\"currentIntegrity\":\"old\",\"currentIntegrity\":\"" + CURRENT_INTEGRITY + "\"}");
        int sequence = 4;
        for (String untrusted : untrustedComparisons) {
            Files.writeString(comparison, untrusted);
            BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
            require(!Files.exists(comparison), "Untrusted comparison still appears current: " + untrusted);
            require(Files.readString(archives.resolve("export-" + sequence + "/generation_comparison.json")).equals(untrusted),
                    "Untrusted comparison contents lost");
            sequence++;
            BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
            require(!Files.exists(archives.resolve("export-" + sequence)), "Repeat malformed-comparison export created an empty archive");
        }

        Files.createDirectory(comparison);
        Files.writeString(comparison.resolve("notes.txt"), "unrelated directory contents");
        boolean rejected = false;
        try { BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY); }
        catch (java.io.IOException expected) { rejected = true; }
        require(rejected, "Nonregular comparison did not fail export safely");
        require(Files.readString(comparison.resolve("notes.txt")).equals("unrelated directory contents"), "Nonregular comparison was traversed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
