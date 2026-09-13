package com.mistaboom.essence_ascendance.balance.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Reads immutable generator inputs once; it never reads or rewrites generated runtime data. */
public record BalanceInputs(BalanceSettings settings, BalanceOverrides overrides,
                            String settingsFingerprint, String overridesFingerprint) {
    public static Path settingsPath(Path configDirectory) { return configDirectory.resolve("essence_ascendance.toml"); }
    public static Path overridesPath(Path configDirectory) { return configDirectory.resolve("essence_ascendance/balance_overrides.toml"); }

    public static void scaffold(Path configDirectory) throws IOException {
        ensureDefault(settingsPath(configDirectory), "/balance/essence_ascendance.toml");
        ensureDefault(overridesPath(configDirectory), "/balance/balance_overrides.toml");
    }

    public static BalanceInputs read(Path configDirectory) throws IOException {
        scaffold(configDirectory);
        Path settingsFile = settingsPath(configDirectory);
        Path overridesFile = overridesPath(configDirectory);
        String settingsText = boundedRead(settingsFile);
        String overridesText = boundedRead(overridesFile);
        return new BalanceInputs(BalanceSettings.parse(settingsText, settingsFile.toString()),
                BalanceOverrides.parse(overridesText, overridesFile.toString()),
                fingerprint(settingsText), fingerprint(overridesText));
    }

    public static String fingerprint(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide required SHA-256", exception);
        }
    }

    public static String fingerprint(Path input) throws IOException { return fingerprint(boundedRead(input)); }

    private static String boundedRead(Path path) throws IOException {
        if (Files.size(path) > 4_000_000) throw new IOException(path + ": balance input exceeds 4 MB");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void ensureDefault(Path path, String resource) throws IOException {
        if (Files.exists(path)) return;
        Files.createDirectories(path.getParent());
        try (InputStream stream = BalanceInputs.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing bundled balance default " + resource);
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            try {
                Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ignored) {
                // Another integrated-server start already created the same human input; never overwrite it.
            }
        }
    }
}
