package com.mistaboom.essence_ascendance.balance.generated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Export migration must preserve old reports and never touch authoritative inputs. */
public final class BalanceReportLayoutTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("balance-report-layout-");
        try {
            List<String> protectedNames = List.of("generated_balance.json", "balance_overrides.toml", "my_notes.csv");
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

            BalanceReportLayout.finishExport(root);
            require(Files.readString(archives.resolve("export-1/valuation.csv")).equals("older archive"), "Existing archive overwritten");
            require(Files.readString(archives.resolve("export-2/valuation.csv")).equals("old valuation"), "Old report contents lost");
            require(Files.readString(archives.resolve("export-2/pack_metadata.json")).equals("old metadata"), "Old metadata lost");
            require(!Files.exists(root.resolve("valuation.csv")), "Stale report still competes with current output");
            require(Files.readString(BalanceReportLayout.reports(root).resolve("valuation.csv")).equals("current valuation"), "Current report changed");
            for (String name : protectedNames)
                require(Files.readString(root.resolve(name)).equals("preserve " + name), "Unrelated or authoritative file changed: " + name);
            require(Files.readString(root.resolve("equipment.csv/notes.txt")).equals("directory must not move"), "Unexpected directory traversed");
            require(Files.readString(root.resolve("README_REPORTS.txt")).contains("/essence debug balance export"), "File guide missing");

            BalanceReportLayout.finishExport(root);
            require(!Files.exists(archives.resolve("export-3")), "Repeat export created an empty archive");
            Files.writeString(root.resolve("valuation.csv"), "another old report");
            BalanceReportLayout.finishExport(root);
            require(Files.readString(archives.resolve("export-3/valuation.csv")).equals("another old report"), "Subsequent legacy export not preserved");
            System.out.println("BalanceReportLayoutTest: archive collisions, repeated export, current output and input preservation PASS");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
