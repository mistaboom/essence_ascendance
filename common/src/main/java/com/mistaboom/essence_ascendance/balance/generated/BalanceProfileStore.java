package com.mistaboom.essence_ascendance.balance.generated;

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FilterOutputStream;
import java.io.Writer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FilterInputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Shared bounded storage for profiles and diagnostics. A non-atomic filesystem fails closed. */
public final class BalanceProfileStore {
    public static final String PROFILE_FILE = "generated_balance.json.gz";
    static final long MAX_PROFILE_BYTES = 256L * 1024L * 1024L;
    static final long MAX_JSON_BYTES = 4L * 1024L * 1024L * 1024L;
    private BalanceProfileStore() { }

    public static Path profilePath(Path folder) { return folder.resolve(PROFILE_FILE); }

    static void requireCurrentStorage(Path folder, boolean rebuild) throws IOException {
        if (!rebuild && !Files.exists(profilePath(folder)) && Files.exists(folder.resolve("generated_balance.json")))
            throw new IOException("Uncompressed development profile retained at " + folder.resolve("generated_balance.json")
                    + "; explicit balance rebuild is required for compressed storage. No profile was deleted or migrated");
    }

    public static BalanceDocument read(Path path) throws IOException {
        BalancePerformance.increment("profile_reads");
        try (var phase = BalancePerformance.phase("profile_locate")) {
            long storedBytes = Files.size(path);
            BalancePerformance.count("profile_stored_bytes", storedBytes);
            if (storedBytes > MAX_PROFILE_BYTES) throw new IOException("Generated balance exceeds 256 MiB safety limit: " + path);
        }
        InputStream stored;
        try (var phase = BalancePerformance.phase("profile_open")) { stored = Files.newInputStream(path); }
        try (stored) {
            InputStreamReader reader;
            try (var phase = BalancePerformance.phase("profile_reader_open")) {
                boolean compressed = path.getFileName().toString().endsWith(".gz");
                reader = new InputStreamReader(limitedInput(compressed ? new GZIPInputStream(stored) : stored,
                        compressed ? MAX_JSON_BYTES : MAX_PROFILE_BYTES), StandardCharsets.UTF_8.newDecoder());
            }
            try (reader) { return BalanceDocument.parse(reader); }
        }
    }

    public static void replace(Path path, BalanceDocument candidate) throws IOException {
        // The authoritative .json.gz keeps every field with the same canonical integrity.
        // Plain JSON remains supported for explicit small offline fixtures/exports only.
        writeAtomically(path, candidate::writeCompactTo, path.getFileName().toString().endsWith(".gz"));
    }

    /** Lossless failure evidence for the existing offline reader, never the live profile path. */
    public static void writeDiagnostic(Path path, BalanceDocument candidate) throws IOException {
        if (!path.getFileName().toString().endsWith(".json.gz"))
            throw new IllegalArgumentException("Compressed diagnostics require a .json.gz path");
        writeAtomically(path, candidate::writeCompactTo, true);
    }

    public static void writeAtomically(Path path, String contents) throws IOException {
        writeAtomically(path, output -> output.write(contents));
    }
    @FunctionalInterface
    interface WriteAction { void write(Writer output) throws IOException; }
    static void writeReport(Path path, WriteAction action) throws IOException {
        writeAtomically(path, action, false, MAX_JSON_BYTES);
    }
    private static void writeAtomically(Path path, WriteAction action) throws IOException {
        writeAtomically(path, action, false);
    }
    private static void writeAtomically(Path path, WriteAction action, boolean compressed) throws IOException {
        writeAtomically(path, action, compressed, MAX_PROFILE_BYTES);
    }
    private static void writeAtomically(Path path, WriteAction action, boolean compressed, long storedLimit) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var stored = limitedOutput(Channels.newOutputStream(channel), storedLimit, false);
                var bounded = compressed ? limitedOutput(new GZIPOutputStream(stored), MAX_JSON_BYTES, true) : stored;
                try (var output = new BufferedWriter(new OutputStreamWriter(bounded, StandardCharsets.UTF_8))) {
                    action.write(output);
                }
                channel.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    static OutputStream limitedOutput(OutputStream stream, long maximum, boolean closeDelegate) {
        return new FilterOutputStream(stream) {
            long bytes;
            public void write(int value) throws IOException { check(1); out.write(value); }
            public void write(byte[] values, int offset, int length) throws IOException { check(length); out.write(values, offset, length); }
            // Finish both UTF-8 and compression before fsync; the outer scope owns the channel.
            public void close() throws IOException { if (closeDelegate) out.close(); else flush(); }
            private void check(int length) throws IOException {
                if ((bytes += length) > maximum) throw new IOException("Generated output exceeds " + maximum / 1024 / 1024 + " MiB safety limit");
            }
        };
    }
    static InputStream limitedInput(InputStream stream, long maximum) {
        return new FilterInputStream(stream) {
            long bytes;
            public int read() throws IOException { int value = in.read(); if (value >= 0) check(1); return value; }
            public int read(byte[] values, int offset, int length) throws IOException {
                int count = in.read(values, offset, length); if (count > 0) check(count); return count;
            }
            private void check(int count) throws IOException {
                if ((bytes += count) > maximum) throw new IOException("Inflated diagnostic exceeds safety limit");
            }
        };
    }
}
