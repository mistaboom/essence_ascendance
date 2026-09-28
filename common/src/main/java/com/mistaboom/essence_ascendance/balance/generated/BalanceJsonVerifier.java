package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Checks Gson's typed output against an existing tree without constructing another tree/string. */
final class BalanceJsonVerifier extends JsonWriter {
    private final JsonElement root;
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private boolean started;

    private static final class Frame {
        final JsonElement value;
        final Set<String> names = new HashSet<>();
        String pending;
        int index;
        Frame(JsonElement value) { this.value = value; }
    }

    BalanceJsonVerifier(JsonElement root) { super(Writer.nullWriter()); this.root = root; }

    private JsonElement take() {
        if (frames.isEmpty()) {
            if (started) throw mismatch("multiple roots");
            started = true;
            return root;
        }
        Frame frame = frames.peek();
        if (frame.value.isJsonArray()) {
            if (frame.index >= frame.value.getAsJsonArray().size()) throw mismatch("additional array element");
            return frame.value.getAsJsonArray().get(frame.index++);
        }
        String name = frame.pending;
        frame.pending = null;
        if (name == null || !frame.names.add(name) || !frame.value.getAsJsonObject().has(name))
            throw mismatch("missing or duplicate object member");
        return frame.value.getAsJsonObject().get(name);
    }

    @Override public JsonWriter beginObject() {
        JsonElement expected = take();
        if (!expected.isJsonObject()) throw mismatch("object type");
        frames.push(new Frame(expected));
        return this;
    }
    @Override public JsonWriter endObject() {
        Frame frame = frames.pop();
        if (!frame.value.isJsonObject() || frame.pending != null) throw mismatch("object boundary");
        long expected = frame.value.getAsJsonObject().entrySet().stream()
                .filter(entry -> getSerializeNulls() || !entry.getValue().isJsonNull()).count();
        if (frame.names.size() != expected) throw mismatch("unconsumed object member");
        return this;
    }
    @Override public JsonWriter beginArray() {
        JsonElement expected = take();
        if (!expected.isJsonArray()) throw mismatch("array type");
        frames.push(new Frame(expected));
        return this;
    }
    @Override public JsonWriter endArray() {
        Frame frame = frames.pop();
        if (!frame.value.isJsonArray() || frame.index != frame.value.getAsJsonArray().size())
            throw mismatch("array length");
        return this;
    }
    @Override public JsonWriter name(String name) {
        Frame frame = frames.peek();
        if (frame == null || !frame.value.isJsonObject() || frame.pending != null) throw mismatch("member name");
        frame.pending = name;
        return this;
    }
    @Override public JsonWriter nullValue() {
        Frame frame = frames.peek();
        if (frame != null && frame.pending != null && !getSerializeNulls()) {
            frame.pending = null;
            return this;
        }
        if (!take().isJsonNull()) throw mismatch("null value");
        return this;
    }
    @Override public JsonWriter value(String value) {
        if (value == null) return nullValue();
        JsonElement expected = take();
        if (!expected.isJsonPrimitive() || !expected.getAsJsonPrimitive().isString()
                || !expected.getAsString().equals(value)) throw mismatch("string value");
        return this;
    }
    @Override public JsonWriter value(boolean value) {
        JsonElement expected = take();
        if (!expected.isJsonPrimitive() || !expected.getAsJsonPrimitive().isBoolean()
                || expected.getAsBoolean() != value) throw mismatch("boolean value");
        return this;
    }
    @Override public JsonWriter value(Boolean value) { return value == null ? nullValue() : value(value.booleanValue()); }
    @Override public JsonWriter value(double value) { return value(Double.valueOf(value)); }
    @Override public JsonWriter value(long value) { return value(Long.valueOf(value)); }
    @Override public JsonWriter value(Number value) {
        if (value == null) return nullValue();
        JsonElement expected = take();
        if (!Double.isFinite(value.doubleValue()) || !expected.isJsonPrimitive()
                || !expected.getAsJsonPrimitive().isNumber()
                || !expected.getAsNumber().toString().equals(value.toString())) throw mismatch("numeric value");
        return this;
    }
    @Override public JsonWriter jsonValue(String value) { throw mismatch("raw JSON adapter output is unsupported"); }
    @Override public void flush() { }
    @Override public void close() { finish(); }
    void finish() { if (!started || !frames.isEmpty()) throw mismatch("incomplete typed value"); }
    private static IllegalArgumentException mismatch(String reason) {
        return new IllegalArgumentException("Generated section differs from typed value: " + reason);
    }
}
