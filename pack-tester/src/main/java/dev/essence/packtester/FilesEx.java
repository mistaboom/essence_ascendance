package dev.essence.packtester;

import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

final class FilesEx {
    static final com.google.gson.Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static String hash(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) >= 0;) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    static void json(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".write-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(value));
            force(temporary);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    static void force(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }
    static Path plainDirectory(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(path) || !path.toRealPath().equals(path))
            throw new IOException("Missing or redirected directory (symlinks/junctions are not deployment targets): " + path);
        return path;
    }
    static Path child(Path parent, String name) throws IOException {
        Path p = parent.resolve(name).normalize();
        if (!p.getParent().equals(parent) || name.isBlank() || name.contains(":") || name.contains("\\") || name.contains("/"))
            throw new IOException("Unsafe journal filename: " + name);
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || !p.toRealPath().equals(p)))
            throw new IOException("Refusing redirected/non-file path: " + p);
        return p;
    }
}
