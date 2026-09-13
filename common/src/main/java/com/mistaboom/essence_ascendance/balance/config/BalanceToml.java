package com.mistaboom.essence_ascendance.balance.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deliberately bounded TOML reader for the two documented balance input schemas.
 * Supports named tables, array tables, quoted/bare keys, basic/literal strings,
 * decimal numbers, booleans and primitive arrays (including multiline arrays).
 * Unsupported TOML constructs fail with their source line instead of being ignored.
 * No loader-specific parser is required on Fabric or a dedicated server.
 */
final class BalanceToml {
    record Value(Object value, int line) { }
    record Table(String name, boolean array, int line, Map<String, Value> values) {
        Table {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private BalanceToml() { }

    static List<Table> parse(String input, String source) {
        if (input.length() > 4_000_000) {
            throw error(source, 0, "TOML exceeds the 4 MB input limit; use general facts instead of generated rows");
        }
        List<Table> tables = new ArrayList<>();
        Map<String, Boolean> declared = new LinkedHashMap<>();
        Map<String, Value> entries = new LinkedHashMap<>();
        String tableName = "";
        boolean array = false;
        int tableLine = 1;
        String[] lines = input.replace("\r\n", "\n").split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            int line = index + 1;
            String text = stripComment(lines[index], source, line).trim();
            if (text.isEmpty()) continue;
            if (text.charAt(0) == '[') {
                tables.add(new Table(tableName, array, tableLine, entries));
                array = text.startsWith("[[");
                String close = array ? "]]" : "]";
                if (!text.endsWith(close)) throw error(source, line, "Unclosed table header");
                tableName = text.substring(array ? 2 : 1, text.length() - close.length()).trim();
                if (!tableName.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")) {
                    throw error(source, line, "Table names must be bare names separated by dots");
                }
                Boolean previous = declared.putIfAbsent(tableName, array);
                if (previous != null && (!array || !previous)) {
                    throw error(source, line, "Duplicate or conflicting table '" + tableName + "'");
                }
                entries = new LinkedHashMap<>();
                tableLine = line;
                continue;
            }
            int equals = separator(text, '=', source, line);
            if (equals < 1) throw error(source, line, "Expected key = value");
            String keyText = text.substring(0, equals).trim();
            String key;
            if (keyText.startsWith("\"") || keyText.startsWith("'")) {
                Object parsed = new LiteralReader(keyText, source, line).read();
                if (!(parsed instanceof String)) throw error(source, line, "Expected a string key");
                key = (String) parsed;
            } else {
                if (!keyText.matches("[A-Za-z0-9_-]+")) {
                    throw error(source, line, "Quote keys containing dots, slashes or colons");
                }
                key = keyText;
            }
            String raw = text.substring(equals + 1).trim();
            while (unclosedArray(raw, source, line)) {
                if (++index >= lines.length) throw error(source, line, "Unclosed array");
                raw += "\n" + stripComment(lines[index], source, index + 1).trim();
            }
            Value value = new Value(new LiteralReader(raw, source, line).read(), line);
            if (entries.putIfAbsent(key, value) != null) {
                throw error(source, line, "Duplicate key '" + key + "' in '" + tableName + "'");
            }
        }
        tables.add(new Table(tableName, array, tableLine, entries));
        return List.copyOf(tables);
    }

    static void requireKeys(Table table, Set<String> keys, String source) {
        table.values.forEach((key, value) -> {
            if (!keys.contains(key)) throw error(source, value.line,
                    "Unknown key '" + key + "' in [" + table.name + "]; allowed: " + String.join(", ", keys.stream().sorted().toList()));
        });
    }

    static BalanceConfigException error(String source, int line, String message) {
        return new BalanceConfigException(source, line, message);
    }

    private static String stripComment(String text, String source, int line) {
        int position = separator(text, '#', source, line);
        return position < 0 ? text : text.substring(0, position);
    }

    private static int separator(String text, char separator, String source, int line) {
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (quote == '"' && c == '\\') { escaped = true; continue; }
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == separator) return i;
        }
        if (quote != 0) throw error(source, line, "Unclosed string; multiline strings are not supported");
        return -1;
    }

    private static boolean unclosedArray(String text, String source, int line) {
        char quote = 0;
        boolean escaped = false;
        int brackets = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (quote == '"' && c == '\\') { escaped = true; continue; }
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '[') brackets++;
            else if (c == ']') brackets--;
            if (brackets < 0) throw error(source, line, "Unexpected closing array bracket");
        }
        return brackets > 0;
    }

    private static final class LiteralReader {
        private final String text;
        private final String source;
        private final int line;
        private int position;

        LiteralReader(String text, String source, int line) {
            this.text = text;
            this.source = source;
            this.line = line;
        }

        Object read() {
            Object value = literal(false);
            whitespace();
            if (position != text.length()) throw fail("Unexpected text after value");
            return value;
        }

        private Object literal(boolean inArray) {
            whitespace();
            if (position == text.length()) throw fail("Missing value");
            char first = text.charAt(position);
            if (first == '\'' || first == '"') return string();
            if (first == '[') {
                if (inArray) throw fail("Nested arrays are unsupported; use a separate fact");
                position++;
                List<Object> values = new ArrayList<>();
                whitespace();
                if (take(']')) return List.of();
                while (true) {
                    values.add(literal(true));
                    if (values.size() > 4096) throw fail("Array exceeds 4096 values");
                    whitespace();
                    if (take(']')) return List.copyOf(values);
                    if (!take(',')) throw fail("Expected comma between array values");
                    whitespace();
                    if (take(']')) return List.copyOf(values);
                }
            }
            int start = position;
            while (position < text.length() && text.charAt(position) != ',' && text.charAt(position) != ']') position++;
            String token = text.substring(start, position).trim();
            if (token.equals("true")) return Boolean.TRUE;
            if (token.equals("false")) return Boolean.FALSE;
            if (token.matches("[+-]?(?:0|[1-9](?:_?[0-9])*)")) {
                try { return Long.parseLong(token.replace("_", "")); }
                catch (NumberFormatException exception) { throw fail("Integer is outside the 64-bit range"); }
            }
            if (token.matches("[+-]?(?:0|[1-9](?:_?[0-9])*)(?:\\.[0-9](?:_?[0-9])*)?(?:[eE][+-]?[0-9](?:_?[0-9])*)?")
                    && (token.contains(".") || token.contains("e") || token.contains("E"))) {
                double value = Double.parseDouble(token.replace("_", ""));
                if (!Double.isFinite(value)) throw fail("Numbers must be finite");
                return value;
            }
            throw fail("Unsupported value '" + token + "'; use quoted strings, finite decimal numbers, booleans or arrays");
        }

        private String string() {
            char quote = text.charAt(position++);
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == quote) return result.toString();
                if (c < 0x20 && c != '\t') throw fail("Control characters are not allowed in strings");
                if (c != '\\' || quote == '\'') { result.append(c); continue; }
                if (position == text.length()) throw fail("Unclosed string escape");
                char escape = text.charAt(position++);
                switch (escape) {
                    case 'b' -> result.append('\b');
                    case 't' -> result.append('\t');
                    case 'n' -> result.append('\n');
                    case 'f' -> result.append('\f');
                    case 'r' -> result.append('\r');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case 'u', 'U' -> {
                        int digits = escape == 'u' ? 4 : 8;
                        if (position + digits > text.length()) throw fail("Incomplete Unicode escape");
                        String hex = text.substring(position, position + digits);
                        if (!hex.matches("[0-9a-fA-F]+")) throw fail("Invalid Unicode escape");
                        long code = Long.parseLong(hex, 16);
                        if (code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) throw fail("Unicode escape is not a scalar value");
                        result.appendCodePoint((int) code);
                        position += digits;
                    }
                    default -> throw fail("Unsupported string escape \\" + escape);
                }
            }
            throw fail("Unclosed string");
        }

        private boolean take(char c) {
            if (position < text.length() && text.charAt(position) == c) { position++; return true; }
            return false;
        }

        private void whitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
        }

        private BalanceConfigException fail(String message) { return error(source, line, message); }
    }
}
