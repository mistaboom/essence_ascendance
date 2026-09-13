package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Rectangular, spreadsheet-readable CSVs with a lossless escape for oversized text. */
final class SpreadsheetReports {
    static final int MAX_CELL_CHARACTERS = 32_767;
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private final JsonObject longText = new JsonObject();
    private final List<Table> tables = new ArrayList<>();

    Table table(String filename, String... columns) {
        Table table = new Table(filename, columns);
        tables.add(table);
        return table;
    }

    void write(Path reports, Path diagnostics) throws IOException {
        // Publish referenced text before its CSV references. Always replace this file,
        // including with an empty object, so a new export cannot retain stale details.
        BalanceProfileStore.writeAtomically(diagnostics.resolve("report_text.json"), BalanceDocument.GSON.toJson(longText) + "\n");
        for (Table table : tables) BalanceProfileStore.writeAtomically(reports.resolve(table.filename), table.text());
    }

    static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite report number");
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    final class Table {
        private final String filename;
        private final String[] columns;
        private final StringBuilder output = new StringBuilder();
        private long rows;

        private Table(String filename, String[] columns) {
            if (columns.length == 0 || Arrays.stream(columns).distinct().count() != columns.length)
                throw new IllegalArgumentException("Missing or duplicate CSV columns: " + filename);
            this.filename = filename;
            this.columns = columns.clone();
            append(columns);
        }

        void row(String... values) {
            if (values.length != columns.length)
                throw new IllegalArgumentException(filename + " row " + (rows + 1) + " has " + values.length + " cells; expected " + columns.length);
            append(values);
            rows++;
        }

        String text() { return output.toString(); }

        private void append(String[] values) {
            for (int i = 0; i < values.length; i++) {
                String value = values[i] == null ? "" : values[i];
                if (value.length() > MAX_CELL_CHARACTERS) {
                    String key = BalanceDocument.hash(value);
                    longText.addProperty(key, value);
                    value = "../diagnostics/report_text.json#/" + key;
                }
                // CSV quoting alone does not stop a spreadsheet treating pack text
                // as a formula. Numeric negatives stay numeric; text gets an apostrophe.
                String leading = value.stripLeading();
                if (!leading.isEmpty() && "=+@-".indexOf(leading.charAt(0)) >= 0 && !NUMBER.matcher(value).matches())
                    value = "'" + value;
                if (value.length() > MAX_CELL_CHARACTERS) {
                    String original = values[i];
                    String key = BalanceDocument.hash(original);
                    longText.addProperty(key, original);
                    value = "../diagnostics/report_text.json#/" + key;
                }
                if (i > 0) output.append(',');
                output.append('"').append(value.replace("\"", "\"\"")).append('"');
            }
            output.append('\n');
        }
    }
}
