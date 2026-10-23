package dev.essence.packtester;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class Compatibility {
    record Verdict(String status, String detail) {
        void requireSupported() throws IOException { if (!status.equals("SUPPORTED")) throw new IOException(status + ": " + detail); }
        @Override public String toString() { return status + ": " + detail; }
    }
    private final Path project;
    Compatibility(Path project) { this.project = project; }
    Verdict instance(PrismDiscovery.Instance instance) {
        if (!instance.problem().isEmpty()) return new Verdict("UNRESOLVED", instance.problem());
        if (!Set.of("fabric", "neoforge").contains(instance.loader())) return new Verdict("INCOMPATIBLE", "Unsupported loader: " + instance.loader() + "; Forge is not NeoForge.");
        try {
            var target = TargetCatalog.require(project, instance.minecraft(), instance.loader());
            return requirements(instance, sourceRequirements(instance.loader(), target));
        } catch (TargetCatalog.UnsupportedTarget e) { return new Verdict("INCOMPATIBLE", e.getMessage()); }
        catch (Exception e) { return new Verdict("UNRESOLVED", e.getMessage()); }
    }
    List<ModMetadata.Requirement> sourceRequirements(String loader, TargetCatalog.Target target) throws IOException {
        List<ModMetadata.Requirement> list = new ArrayList<>();
        if (loader.equals("fabric")) {
            String source = Files.readString(project.resolve("fabric/src/main/resources/fabric.mod.json"))
                    .replace("${fabric_loader_requirement}", ">=" + target.fabricLoader())
                    .replace("${minecraft_requirement}", target.minecraft())
                    .replace("${java_requirement}", ">=" + target.javaVersion())
                    .replace("${architectury_requirement}", ">=" + target.architecturyApi());
            var obj = JsonParser.parseString(source).getAsJsonObject();
            obj.getAsJsonObject("depends").entrySet().forEach(e -> list.add(new ModMetadata.Requirement(e.getKey(),
                    e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString())));
        } else {
            String source = Files.readString(project.resolve("neoforge/src/main/resources/META-INF/neoforge.mods.toml"))
                    .replace("${neoforge_requirement}", "[" + target.neoforgeMinimum() + ",)")
                    .replace("${minecraft_requirement}", "[" + target.minecraft() + "]")
                    .replace("${architectury_requirement}", "[" + target.architecturyApi() + ",)");
            var toml = ModMetadata.parseToml(source);
            if (!"javafml".equals(toml.getString("modLoader")) || !"[4,)".equals(toml.getString("loaderVersion"))) throw new IOException("Unresolved NeoForge language-loader requirement");
            var deps = toml.getArray("dependencies." + ModMetadata.OWN);
            if (deps != null) for (int i = 0; i < deps.size(); i++) {
                var d = deps.getTable(i);
                if ("required".equals(d.getString("type"))) list.add(new ModMetadata.Requirement(d.getString("modId"), d.getString("versionRange")));
            }
        }
        return list;
    }
    Verdict requirements(PrismDiscovery.Instance instance, List<ModMetadata.Requirement> requirements) {
        try {
            int javaVersion = TargetCatalog.require(project, instance.minecraft(), instance.loader()).javaVersion();
            Map<String, List<String>> installed = new HashMap<>();
            installed.put("minecraft", List.of(instance.minecraft()));
            installed.put(instance.loader().equals("fabric") ? "fabricloader" : "neoforge", List.of(instance.loaderVersion()));
            if (Files.exists(instance.mods())) {
                FilesEx.plainDirectory(instance.mods());
                try (var stream = Files.list(instance.mods())) {
                    for (Path jar : stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                        FilesEx.child(instance.mods(), jar.getFileName().toString());
                        var metadata = ModMetadata.read(jar, instance.loader());
                        if (metadata.nestedOwn() || metadata.containsOwn() && !metadata.onlyOwn())
                            return new Verdict("UNRESOLVED", "Essence Ascendance is in an ambiguous bundle: " + jar);
                        for (var mod : metadata.mods()) if (mod.loader().equals(instance.loader()) && !mod.id().equals(ModMetadata.OWN))
                            installed.computeIfAbsent(mod.id(), k -> new ArrayList<>()).add(mod.version());
                    }
                }
            }
            for (var req : requirements) {
                // Prism may auto-select Java at launch; this tool never changes that setting.
                if (req.id().equals("java")) {
                    if (!req.range().equals(">=" + javaVersion)) return new Verdict("UNRESOLVED", "Game Java requirement differs from the selected target: " + req.range());
                    continue;
                }
                var versions = installed.get(req.id());
                if (versions == null) return new Verdict("INCOMPATIBLE", "Missing required " + req.id() + " " + req.range() + " (not found in top-level loader metadata)");
                if (versions.size() != 1) return new Verdict("UNRESOLVED", "Duplicate dependency " + req.id() + ": " + versions);
                if (!satisfies(versions.getFirst(), req.range())) return new Verdict("INCOMPATIBLE", req.id() + " " + versions.getFirst() + " does not satisfy " + req.range());
            }
            return new Verdict("SUPPORTED", "Minecraft/loader and declared required mods match. Prism game Java must be " + javaVersion + " or newer.");
        } catch (Exception e) { return new Verdict("UNRESOLVED", e.getMessage()); }
    }
    // The source declares these forms. Unknown expressions/qualifiers must never imply compatibility.
    static boolean satisfies(String version, String range) throws IOException {
        if (range == null || version == null || version.contains("${")) throw new IOException("Unresolved dependency version");
        if (range.equals("*")) return true;
        if (range.startsWith(">=")) return compare(version, range.substring(2)) >= 0;
        if (range.startsWith("~")) {
            String base = range.substring(1); String[] parts = base.split("\\.");
            if (parts.length != 3) throw new IOException("Unsupported range: " + range);
            return compare(version, base) >= 0 && compare(version, parts[0] + "." + (Integer.parseInt(parts[1]) + 1) + ".0") < 0;
        }
        if (range.matches("[\\[(][0-9.]*,[0-9.]*[\\])]")) {
            String[] bounds = range.substring(1, range.length() - 1).split(",", -1);
            return (bounds[0].isEmpty() || compare(version, bounds[0]) >= (range.charAt(0) == '[' ? 0 : 1))
                    && (bounds[1].isEmpty() || compare(version, bounds[1]) <= (range.endsWith("]") ? 0 : -1));
        }
        if (range.matches("\\[[0-9.]+\\]")) return compare(version, range.substring(1, range.length() - 1)) == 0;
        if (range.matches("[0-9.]+")) return compare(version, range) == 0;
        throw new IOException("Unsupported dependency range: " + range);
    }
    static int compare(String a, String b) throws IOException {
        a = a.split("\\+", 2)[0]; b = b.split("\\+", 2)[0];
        if (!a.matches("[0-9]+(\\.[0-9]+)*") || !b.matches("[0-9]+(\\.[0-9]+)*")) throw new IOException("Unresolved version comparison: " + a + " / " + b);
        var aa = a.split("\\."); var bb = b.split("\\.");
        for (int i = 0; i < Math.max(aa.length, bb.length); i++) {
            int c = new java.math.BigInteger(i < aa.length ? aa[i] : "0").compareTo(new java.math.BigInteger(i < bb.length ? bb[i] : "0"));
            if (c != 0) return c;
        }
        return 0;
    }
}
