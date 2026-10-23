package com.mistaboom.essence_ascendance.verification;

import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Exact Gradle-owned native, projected-source and compiled-class inputs; never searches a cache. */
public final class TargetTestInputs {
    private TargetTestInputs() { }
    public static JarFile nativeJar(String loader) throws IOException {
        Path path = Path.of(required("essence.native." + loader + ".jar"));
        if (!Files.isRegularFile(path)) throw new IOException("Target native artifact is missing: " + path);
        return new JarFile(path.toFile());
    }
    public static JarFile minecraftClientResources() throws IOException {
        Path path = Path.of(required("essence.native.minecraft.client.jar"));
        if (!Files.isRegularFile(path)) throw new IOException("Target Minecraft client resources are missing: " + path);
        JarFile jar = new JarFile(path.toFile());
        try {
            var version = jar.getJarEntry("version.json");
            if (version == null) throw new IOException("Minecraft client resources lack version.json: " + path);
            try (var reader = new InputStreamReader(jar.getInputStream(version), StandardCharsets.UTF_8)) {
                String expected = required("essence.minecraft");
                String actual = JsonParser.parseReader(reader).getAsJsonObject().get("id").getAsString();
                if (!expected.equals(actual))
                    throw new IOException("Wrong Minecraft client resources: expected " + expected + ", got " + actual + " at " + path);
            }
            return jar;
        } catch (IOException | RuntimeException ex) {
            jar.close();
            throw ex;
        }
    }
    public static InputStream nativeClass(String loader, String name) throws IOException {
        try (var jar = nativeJar(loader)) {
            var entry = jar.getJarEntry(name + ".class");
            if (entry == null) throw new IOException(loader + " target native class missing: " + name);
            try (var input = jar.getInputStream(entry)) { return new ByteArrayInputStream(input.readAllBytes()); }
        }
    }
    public static Path javaSource(String module, String relative) throws IOException { return source(module, "java", relative); }
    public static Path resourceSource(String module, String relative) throws IOException { return source(module, "resources", relative); }
    private static Path source(String module, String kind, String relative) throws IOException {
        var found = new ArrayList<Path>();
        for (String root : required("essence.sources." + module + "." + kind).split(Pattern.quote(File.pathSeparator))) {
            Path directory = Path.of(root).toAbsolutePath().normalize(), candidate = directory.resolve(relative).normalize();
            if (!candidate.startsWith(directory)) throw new IOException("Source path escapes target root: " + relative);
            if (Files.isRegularFile(candidate)) found.add(candidate);
        }
        if (found.size() != 1) throw new IOException("Expected one " + module + " target source for " + relative + ": " + found);
        return found.getFirst();
    }
    /** Actual output from the exact module's processResources task, including native JSON projection. */
    public static Path processedResource(String module, String relative) throws IOException {
        Path directory = Path.of(required("essence.processed." + module + ".resources")).toAbsolutePath().normalize();
        Path path = directory.resolve(relative).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path))
            throw new IOException("Target processed resource missing or outside its output: " + path);
        return path;
    }
    public static Path compiledClass(String loader, String name) throws IOException {
        Path directory = Path.of(required("essence.classes." + loader)).toAbsolutePath().normalize();
        Path path = directory.resolve(name + ".class").normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) throw new IOException("Target compiled class missing: " + path);
        return path;
    }
    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Gradle must supply target test input: " + name);
        return value;
    }
}
