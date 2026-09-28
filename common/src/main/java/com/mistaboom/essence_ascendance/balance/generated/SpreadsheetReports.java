package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Rectangular, spreadsheet-readable CSVs with a lossless escape for oversized text. */
final class SpreadsheetReports implements AutoCloseable {
    static final int MAX_CELL_CHARACTERS = 32_767;
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private final JsonObject longText = new JsonObject();
    private final List<Table> tables = new ArrayList<>();
    private boolean closed;

    Table table(String filename, String... columns) {
        if (closed) throw new IllegalStateException("Report export is closed");
        Table table = new Table(filename, columns);
        tables.add(table);
        return table;
    }

    void write(Path reports, Path diagnostics) throws IOException {
        // Publish referenced text before its CSV references. Always replace this file,
        // including with an empty object, so a new export cannot retain stale details.
        try (this) {
            BalanceProfileStore.writeReport(diagnostics.resolve("report_text.json"), output -> {
                BalanceDocument.GSON.toJson(longText, output); output.write('\n');
            });
            for (Table table : tables) BalanceProfileStore.writeReport(reports.resolve(table.filename), table::writeTo);
        }
    }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        for (Table table : tables) {
            try { table.close(); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }

    static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite report number");
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    final class Table {
        private final String filename;
        private final String[] columns;
        private StringBuilder output = new StringBuilder();
        private Path spool;
        private Writer spoolWriter;
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

        private void writeTo(Writer destination) throws IOException {
            if (spool == null) destination.write(output.toString());
            else {
                spoolWriter.flush();
                try (var input = Files.newBufferedReader(spool, StandardCharsets.UTF_8)) { input.transferTo(destination); }
            }
        }

        private void close() throws IOException {
            try { if (spoolWriter != null) spoolWriter.close(); }
            finally { if (spool != null) Files.deleteIfExists(spool); }
        }

        private void append(String[] values) {
            if (closed) throw new IllegalStateException("Report export is closed");
            StringBuilder row = new StringBuilder();
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
                if (i > 0) row.append(',');
                row.append('"').append(value.replace("\"", "\"\"")).append('"');
            }
            row.append('\n');
            try {
                if (spool == null && output.length() + row.length() <= 65_536) output.append(row);
                else {
                    if (spool == null) {
                        spool = Files.createTempFile("essence-report-", ".csv.pending");
                        spoolWriter = new BufferedWriter(new OutputStreamWriter(BalanceProfileStore.limitedOutput(
                                Files.newOutputStream(spool), BalanceProfileStore.MAX_JSON_BYTES, true), StandardCharsets.UTF_8));
                        spoolWriter.write(output.toString()); output = null;
                    }
                    spoolWriter.write(row.toString());
                }
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }
    }
}
