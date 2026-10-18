package com.mistaboom.essence_ascendance.balance.generated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Current report guidance and manifest identity do not relocate or reinterpret other files. */
public final class BalanceReportLayoutTest {
    private static final String CURRENT_INTEGRITY = "installed-profile-integrity";
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("balance-report-layout-");
        try {
            List<String> protectedNames = List.of(BalanceProfileStore.PROFILE_FILE, "balance_overrides.toml",
                    "skill_settings.toml", "bonus_settings.toml", "my_notes.csv");
            for (String name : protectedNames) Files.writeString(root.resolve(name), "preserve " + name);
            Files.createDirectories(BalanceReportLayout.reports(root));
            Files.createDirectories(BalanceReportLayout.diagnostics(root));
            Path valuation = BalanceReportLayout.reports(root).resolve("valuation.csv");
            Files.writeString(valuation, "current valuation");
            BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
            String guide = Files.readString(root.resolve("README_REPORTS.txt"));
            require(guide.contains("/essence admin balance export"), "Current export command missing");
            require(!guide.contains("/essence debug balance export"), "Retired alias advertised");
            require(guide.contains("do not prove survival access"), "Estimate/access distinction missing");
            require(guide.contains("skill_settings.toml") && guide.contains("bonus_settings.toml")
                    && guide.contains("native stat endpoints"), "Current editable progression settings missing");
            require(!guide.contains("skill_layout.toml") && !guide.contains("bonus_progression.toml"),
                    "Retired configuration filenames advertised");
            require(BalanceReports.state(root, CURRENT_INTEGRITY).equals("unverified_existing_or_missing"), "Unverified reports certified");
            Path manifest = BalanceReportLayout.diagnostics(root).resolve("report_manifest.json");
            Files.writeString(manifest, "{\"profileIntegrity\":\"installed-profile-integrity\",\"state\":\"complete\"}");
            require(BalanceReports.state(root, CURRENT_INTEGRITY).equals("current"), "Matching export not reusable");
            require(BalanceReports.state(root, "different").equals("different_profile"), "Other profile reports presented as current");
            Files.writeString(manifest, "{\"profileIntegrity\":\"installed-profile-integrity\",\"state\":\"incomplete\"}");
            require(BalanceReports.state(root, CURRENT_INTEGRITY).equals("incomplete"), "Partial export presented as complete");
            BalanceReportLayout.finishExport(root, CURRENT_INTEGRITY);
            require(Files.readString(root.resolve("README_REPORTS.txt")).equals(guide), "Repeated guide export changed");
            require(Files.readString(valuation).equals("current valuation"), "Guide export changed current table");
            for (String name : protectedNames)
                require(Files.readString(root.resolve(name)).equals("preserve " + name), "Unrelated or authoritative file changed");
            try (var files = Files.list(root)) {
                require(files.count() == protectedNames.size() + 3, "Guide export created an unexpected directory");
            }
            System.out.println("BalanceReportLayoutTest: current guide, manifest identity and input preservation PASS");
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
