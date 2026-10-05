package com.mistaboom.essence_ascendance.balance.generated;

import java.io.IOException;
import java.io.Writer;

/** Thread-confined canonical-output buffer. JsonWriter's tiny indentation/token writes
 * need no per-token lock; the UTF-8 encoder still owns surrogate and encoding semantics. */
final class BalanceJsonBuffer extends Writer {
    private final Writer output;
    private final char[] buffer = new char[64 * 1024];
    private int size;
    BalanceJsonBuffer(Writer output) { this.output = output; }
    @Override public void write(int value) throws IOException {
        if (size == buffer.length) drain();
        buffer[size++] = (char) value;
    }
    @Override public void write(char[] text, int offset, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset, length, text.length);
        while (length > 0) {
            if (size == buffer.length) drain();
            int count = Math.min(length, buffer.length - size);
            System.arraycopy(text, offset, buffer, size, count);
            size += count; offset += count; length -= count;
        }
    }
    @Override public void write(String text, int offset, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset, length, text.length());
        while (length > 0) {
            if (size == buffer.length) drain();
            int count = Math.min(length, buffer.length - size);
            text.getChars(offset, offset + count, buffer, size);
            size += count; offset += count; length -= count;
        }
    }
    private void drain() throws IOException { if (size > 0) { output.write(buffer, 0, size); size = 0; } }
    @Override public void flush() throws IOException { drain(); output.flush(); }
    @Override public void close() throws IOException { try { drain(); } finally { output.close(); } }
}
