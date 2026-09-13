package com.mistaboom.essence_ascendance.balance.generated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** The sole writer for generated_balance.json. A non-atomic filesystem fails closed. */
public final class BalanceProfileStore {
    private static final long MAX_PROFILE_BYTES = 256L * 1024L * 1024L;
    private BalanceProfileStore() { }

    public static BalanceDocument read(Path path) throws IOException {
        if (Files.size(path) > MAX_PROFILE_BYTES) throw new IOException("Generated balance exceeds 256 MiB safety limit: " + path);
        return BalanceDocument.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static void replace(Path path, BalanceDocument candidate) throws IOException {
        writeAtomically(path, candidate.text());
    }

    public static void writeAtomically(Path path, String contents) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".pending");
        try {
            byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_PROFILE_BYTES) throw new IOException("Generated output exceeds 256 MiB safety limit");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
