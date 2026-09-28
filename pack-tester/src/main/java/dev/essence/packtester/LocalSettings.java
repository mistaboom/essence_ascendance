package dev.essence.packtester;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class LocalSettings {
    List<String> roots = new ArrayList<>();
    List<String> browsed = new ArrayList<>();
    String selected;
    String executable;
    static LocalSettings load(Path project) throws IOException {
        Path path = project.resolve(".pack-tester/settings.json");
        if (!Files.exists(path)) return new LocalSettings();
        try {
            LocalSettings result = FilesEx.JSON.fromJson(Files.readString(path), LocalSettings.class);
            if (result == null || result.roots == null || result.browsed == null) throw new IOException("Invalid local settings: " + path);
            return result;
        } catch (RuntimeException e) { throw new IOException("Cannot read local settings: " + path, e); }
    }
    void save(Path project) throws IOException { FilesEx.json(project.resolve(".pack-tester/settings.json"), this); }
    static Optional<Path> standardRoot() {
        String appData = System.getenv("APPDATA");
        return appData == null ? Optional.empty() : Optional.of(Path.of(appData, "PrismLauncher"));
    }
    List<Path> allRoots() {
        Set<Path> paths = new LinkedHashSet<>();
        standardRoot().filter(Files::isDirectory).ifPresent(paths::add);
        roots.stream().map(Path::of).forEach(paths::add);
        return List.copyOf(paths);
    }
}
