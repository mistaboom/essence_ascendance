package dev.essence.packtester;

import com.google.gson.*;
import org.tomlj.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Manifest;
import java.util.zip.*;

final class ModMetadata {
    static final String OWN = "essence_ascendance";
    record Mod(String id, String version, String loader) { }
    record Requirement(String id, String range) { }
    record Metadata(List<Mod> mods, List<Requirement> required, boolean nestedOwn) {
        boolean containsOwn() { return mods.stream().anyMatch(m -> m.id().equals(OWN)); }
        boolean onlyOwn() { return containsOwn() && mods.stream().allMatch(m -> m.id().equals(OWN)) && !nestedOwn; }
    }
    static Metadata read(Path jar, String selectedLoader) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<Mod> mods = new ArrayList<>(); List<Requirement> required = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                String name = entries.nextElement().getName();
                if (!names.add(name) && (name.equals("fabric.mod.json") || name.endsWith("mods.toml") || name.endsWith(".jar")))
                    throw new IOException("Duplicate identity/bundle ZIP entry in " + jar + ": " + name);
            }
            String manifestVersion = "?";
            if (zip.getEntry("META-INF/MANIFEST.MF") != null) {
                try (var in = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                    manifestVersion = new Manifest(in).getMainAttributes().getValue("Implementation-Version");
                }
            }
            if (zip.getEntry("fabric.mod.json") != null) {
                JsonObject obj = JsonParser.parseString(entry(zip, "fabric.mod.json")).getAsJsonObject();
                mods.add(new Mod(obj.get("id").getAsString(), obj.get("version").getAsString(), "fabric"));
                if (selectedLoader.equals("fabric") && obj.get("id").getAsString().equals(OWN) && obj.has("depends")) {
                    obj.getAsJsonObject("depends").entrySet().forEach(e -> required.add(new Requirement(e.getKey(),
                            e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString())));
                }
            }
            for (String file : List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
                if (zip.getEntry(file) == null) continue;
                String loader = file.contains("neoforge") ? "neoforge" : "forge";
                var toml = parseToml(entry(zip, file));
                var list = toml.getArray("mods");
                if (list == null) throw new IOException("Missing mods table: " + jar);
                for (int i = 0; i < list.size(); i++) {
                    var m = list.getTable(i); String version = m.getString("version");
                    if ("${file.jarVersion}".equals(version)) version = manifestVersion;
                    mods.add(new Mod(Objects.requireNonNull(m.getString("modId")), version, loader));
                }
                if (loader.equals(selectedLoader) && mods.stream().anyMatch(m -> m.id().equals(OWN))) {
                    if (!"javafml".equals(toml.getString("modLoader")) || !"[4,)".equals(toml.getString("loaderVersion")))
                        throw new IOException("Unresolved NeoForge language-loader requirement; update validator for " + jar);
                    var deps = toml.getArray("dependencies." + OWN);
                    if (deps != null) for (int i = 0; i < deps.size(); i++) {
                        var dep = deps.getTable(i);
                        if ("required".equals(dep.getString("type")) || Boolean.TRUE.equals(dep.getBoolean("mandatory")))
                            required.add(new Requirement(dep.getString("modId"), dep.getString("versionRange")));
                    }
                }
            }
            // A bundled copy is ambiguous: never delete its host, and never add a second copy.
            boolean nestedOwn = false;
            for (String name : names) if (name.endsWith(".jar")) {
                try (var in = zip.getInputStream(zip.getEntry(name))) { nestedOwn |= nestedOwn(in, 0); }
            }
            return new Metadata(List.copyOf(mods), List.copyOf(required), nestedOwn);
        } catch (RuntimeException e) { throw new IOException("Unresolved mod metadata in " + jar + ": " + e.getMessage(), e); }
    }
    private static boolean nestedOwn(InputStream in, int depth) throws IOException {
        if (depth > 5) throw new IOException("Nested JAR depth exceeds safe inspection limit");
        try (var zip = new ZipInputStream(in)) {
            long total = 0;
            for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
                if (!e.getName().endsWith(".jar") && !e.getName().equals("fabric.mod.json") && !e.getName().endsWith("mods.toml")) continue;
                byte[] bytes = zip.readNBytes(32 * 1024 * 1024 + 1); total += bytes.length;
                if (total > 64 * 1024 * 1024 || bytes.length > 32 * 1024 * 1024) throw new IOException("Nested metadata scan limit exceeded");
                if (e.getName().endsWith(".jar")) { if (nestedOwn(new ByteArrayInputStream(bytes), depth + 1)) return true; }
                else if (e.getName().equals("fabric.mod.json")) {
                    var o = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    if (OWN.equals(o.get("id").getAsString())) return true;
                } else {
                    var a = parseToml(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getArray("mods");
                    if (a != null) for (int i = 0; i < a.size(); i++) if (OWN.equals(a.getTable(i).getString("modId"))) return true;
                }
            }
        }
        return false;
    }
    static String entry(ZipFile zip, String name) throws IOException {
        try (var in = zip.getInputStream(zip.getEntry(name))) {
            byte[] bytes = in.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new IOException("Oversized mod metadata");
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    static TomlParseResult parseToml(String text) throws IOException {
        var result = Toml.parse(text);
        if (result.hasErrors()) throw new IOException("Invalid TOML: " + result.errors());
        return result;
    }
    static void production(Path jar, String loader) throws IOException {
        Metadata m = read(jar, loader);
        if (!m.onlyOwn() || m.mods().stream().noneMatch(mod -> mod.loader().equals(loader)))
            throw new IOException("Archive is not an unambiguous Essence Ascendance " + loader + " mod: " + jar);
        try (var zip = new ZipFile(jar.toFile())) {
            String entry = "com/mistaboom/essence_ascendance/" + loader + "/EssenceAscendance" + (loader.equals("fabric") ? "Fabric" : "NeoForge") + ".class";
            if (zip.getEntry(entry) == null) throw new IOException("Archive has no loader entrypoint class: " + entry);
        }
    }
}
