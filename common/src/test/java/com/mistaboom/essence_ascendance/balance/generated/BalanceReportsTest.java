package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Spreadsheet boundary checks; full installed-profile export is covered by the integration suite. */
public final class BalanceReportsTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("balance-spreadsheet-report-");
        try {
            SpreadsheetReports reports = new SpreadsheetReports();
            var table = reports.table("fixture.csv", "id", "value", "detail");
            String longText = "quoted, \"source\"\n日本語\r\n".repeat(4000);
            String boundaryFormula = "=" + "x".repeat(SpreadsheetReports.MAX_CELL_CHARACTERS - 1);
            String ordinaryText = "commas, quotes \"and\" line\nbreaks\r\n日本語";
            table.row("ordinary", "42", ordinaryText);
            table.row("long", "0", longText);
            table.row("formula", "-1.25", "=SUM(A1:A2)");
            table.row("boundary", "0.0000001", boundaryFormula);
            table.row("whitespace_formula", "0", " \t+SUM(A1:A2)");
            table.row("at_boundary", "0", "x".repeat(SpreadsheetReports.MAX_CELL_CHARACTERS));
            reject(() -> table.row("short", "1"), "Ragged row was accepted");
            reject(() -> reports.table("duplicates.csv", "same", "same"), "Duplicate headings were accepted");
            reject(() -> SpreadsheetReports.number(Double.NaN), "NaN exported as a numeric cell");
            reject(() -> SpreadsheetReports.number(Double.POSITIVE_INFINITY), "Infinity exported as a numeric cell");
            check(SpreadsheetReports.number(42).equals("42"), "Whole numeric value retained decimal clutter");
            check(SpreadsheetReports.number(0.0000001).equals("0.0000001"), "Small nonzero evidence was rounded to zero");
            check(Double.parseDouble(SpreadsheetReports.number(1.234567891234567)) == 1.234567891234567,
                    "Numeric export changed a saved double");
            reports.write(root.resolve("reports"), root.resolve("diagnostics"));
            String exported = Files.readString(root.resolve("reports/fixture.csv"), StandardCharsets.UTF_8);
            List<List<String>> rows = GeneratedBalanceIntegrationTest.csv(exported);
            check(rows.size() == 7, "CSV line breaks became extra rows");
            for (List<String> row : rows) {
                check(row.size() == 3, "CSV quoting broke the table width");
                for (String cell : row) check(cell.length() <= SpreadsheetReports.MAX_CELL_CHARACTERS, "Spreadsheet cell exceeded its limit");
            }
            check(rows.get(1).get(2).equals(ordinaryText), "CSV escaping lost commas, quotes, Unicode or newlines");
            JsonObject details = JsonParser.parseString(Files.readString(root.resolve("diagnostics/report_text.json"))).getAsJsonObject();
            check(details.size() == 2, "Lossless text fallback did not retain exactly the oversized values");
            for (int index : List.of(2, 4)) {
                String reference = rows.get(index).get(2), key = reference.substring(reference.indexOf("#/") + 2);
                check(reference.startsWith("../diagnostics/report_text.json#/"), "Long cell has no usable detail reference");
                check(details.get(key).getAsString().equals(index == 2 ? longText : boundaryFormula), "Oversized source text was truncated or transformed");
            }
            check(rows.get(3).get(1).equals("-1.25"), "Negative numeric data became text");
            check(rows.get(3).get(2).equals("'=SUM(A1:A2)"), "Formula-like source text was exported executable");
            check(rows.get(5).get(2).startsWith("'"), "Whitespace concealed formula-like source text");
            reports.write(root.resolve("reports"), root.resolve("diagnostics"));
            check(Files.readString(root.resolve("reports/fixture.csv")).equals(exported), "Identical tables exported nondeterministically");
            SpreadsheetReports replacement = new SpreadsheetReports();
            replacement.table("fixture.csv", "id").row("replacement");
            replacement.write(root.resolve("reports"), root.resolve("diagnostics"));
            check(JsonParser.parseString(Files.readString(root.resolve("diagnostics/report_text.json"))).getAsJsonObject().isEmpty(),
                    "A later export retained stale long-text details");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("BalanceReportsTest: " + checks + " spreadsheet quoting, numeric fidelity, cell limits and lossless fallback checks PASS");
    }

    private static void reject(Runnable action, String failure) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(failure);
    }
    private static void check(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
        checks++;
    }
}
