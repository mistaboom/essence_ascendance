package dev.essence.packtester;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

final class PrismDiscovery {
    record Instance(Path directory, Path root, String name, String id, String minecraft,
                    String loader, String loaderVersion, Path game, String problem) {
        Path mods() { return game.resolve("mods"); }
        @Override public String toString() { return name + " [" + id + "] — " + directory; }
    }
    // Only read relevant INI fields; never open accounts.json or copy launcher settings.
    static Map<String, String> ini(Path file) throws IOException {
        Map<String, String> fields = new HashMap<>();
        for (String line : Files.readAllLines(file)) {
            line = line.strip();
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#") && !line.startsWith(";"))
                fields.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        return fields;
    }
    // QSettings INI string/list quoting. Unsupported serialized QVariant values fail closed.
    static List<String> iniList(String raw) throws IOException {
        if (raw == null || raw.isBlank() || raw.equals("@Invalid()")) return List.of();
        if (raw.startsWith("@")) throw new IOException("Unsupported Prism INI value; browse the instance directory directly: " + raw);
        List<String> result = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') quote = !quote;
            else if (c == '\\') {
                if (++i == raw.length()) throw new IOException("Incomplete INI escape");
                c = raw.charAt(i);
                if (c == 'x') {
                    int start = i + 1, end = start;
                    while (end < raw.length() && end < start + 4 && Character.digit(raw.charAt(end), 16) >= 0) end++;
                    if (start == end) throw new IOException("Invalid INI hex escape");
                    part.append((char) Integer.parseInt(raw.substring(start, end), 16)); i = end - 1;
                } else part.append(switch (c) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> c; });
            } else if (c == ',' && !quote) { result.add(part.toString().strip()); part.setLength(0); }
            else part.append(c);
        }
        if (quote) throw new IOException("Unclosed Prism INI quote");
        result.add(part.toString().strip());
        return result.stream().filter(s -> !s.isEmpty()).toList();
    }
    static String value(Map<String, String> ini, String key, String fallback) throws IOException {
        List<String> values = iniList(ini.get(key));
        if (values.size() > 1) throw new IOException("Expected one INI value for " + key);
        return values.isEmpty() ? fallback : values.getFirst();
    }
    static List<Path> instanceDirectories(Path root) throws IOException {
        Map<String, String> cfg = ini(root.resolve("prismlauncher.cfg"));
        List<String> names = new ArrayList<>();
        names.add(value(cfg, "InstanceDir", "instances"));
        names.addAll(iniList(cfg.get("AdditionalInstanceDirs")));
        return names.stream().map(s -> root.resolve(s).toAbsolutePath().normalize()).distinct().toList();
    }
    List<Instance> discover(LocalSettings settings, Consumer<String> log) {
        Map<Path, Instance> found = new LinkedHashMap<>();
        for (Path root : settings.allRoots()) {
            try {
                for (Path instances : instanceDirectories(root)) scan(instances, root, found);
            } catch (Exception e) { log.accept("Discovery: " + root + ": " + e.getMessage()); }
        }
        for (String chosen : settings.browsed) {
            Path path = Path.of(chosen).toAbsolutePath().normalize();
            try {
                if (Files.exists(path.resolve("instance.cfg"))) found.putIfAbsent(path, inspect(path, rootFor(path, settings)));
                else if (Files.exists(path.resolve("prismlauncher.cfg"))) {
                    for (Path instances : instanceDirectories(path)) scan(instances, path, found);
                } else scan(path, null, found);
            } catch (Exception e) { log.accept("Discovery: " + path + ": " + e.getMessage()); }
        }
        return found.values().stream().sorted(Comparator.comparing(Instance::name).thenComparing(i -> i.directory().toString())).toList();
    }
    private void scan(Path instances, Path root, Map<Path, Instance> found) throws IOException {
        if (!Files.isDirectory(instances)) return;
        try (var children = Files.list(instances)) {
            for (Path path : children.filter(Files::isDirectory).toList()) {
                if (Files.exists(path.resolve("instance.cfg")) || Files.exists(path.resolve("mmc-pack.json"))) {
                    path = path.toAbsolutePath().normalize();
                    found.putIfAbsent(path, inspect(path, root));
                }
            }
        }
    }
    Path rootFor(Path instance, LocalSettings settings) {
        List<Path> roots = new ArrayList<>(settings.allRoots());
        if (instance.getParent() != null && instance.getParent().getParent() != null) roots.add(instance.getParent().getParent());
        for (Path root : roots) {
            try { if (instanceDirectories(root).contains(instance.getParent())) return root; }
            catch (IOException ignored) { /* Unassociated direct instances can deploy but cannot launch. */ }
        }
        return null;
    }
    Instance inspect(Path directory, Path root) {
        directory = directory.toAbsolutePath().normalize();
        String id = directory.getFileName().toString(), name = id, mc = "?", loader = "?", version = "?";
        Path game = directory.resolve("minecraft");
        if (!Files.exists(game) && Files.exists(directory.resolve(".minecraft"))) game = directory.resolve(".minecraft");
        String problem = "";
        try {
            Map<String, String> cfg = ini(directory.resolve("instance.cfg"));
            name = value(cfg, "name", id);
            if (!value(cfg, "InstanceType", "").equals("OneSix")) throw new IOException("Missing/unsupported InstanceType (expected OneSix)");
            JsonObject pack = JsonParser.parseString(Files.readString(directory.resolve("mmc-pack.json"))).getAsJsonObject();
            if (!pack.has("formatVersion") || pack.get("formatVersion").getAsInt() != 1) throw new IOException("Unsupported mmc-pack.json format");
            Set<String> seen = new HashSet<>();
            List<String> loaders = new ArrayList<>();
            for (JsonElement element : pack.getAsJsonArray("components")) {
                JsonObject c = element.getAsJsonObject();
                String uid = c.get("uid").getAsString();
                if (!seen.add(uid)) throw new IOException("Duplicate component " + uid);
                String v = c.has("version") ? c.get("version").getAsString() : "?";
                if (uid.equals("net.minecraft")) mc = v;
                String kind = switch (uid) {
                    case "net.neoforged" -> "neoforge";
                    case "net.minecraftforge" -> "forge";
                    case "net.fabricmc.fabric-loader" -> "fabric";
                    case "org.quiltmc.quilt-loader" -> "quilt";
                    default -> null;
                };
                if (kind != null) { loaders.add(kind); loader = kind; version = v; }
            }
            if (loaders.isEmpty()) loader = "vanilla";
            if (loaders.size() > 1) throw new IOException("Multiple loader components: " + loaders);
            if (mc.equals("?") || version.equals("?") && !loader.equals("vanilla")) throw new IOException("Missing component version");
            if (Files.isDirectory(directory.resolve("patches"))) {
                try (var patches = Files.list(directory.resolve("patches"))) {
                    if (patches.anyMatch(p -> p.toString().endsWith(".json"))) throw new IOException("Custom component patches need manual metadata review");
                }
            }
            FilesEx.plainDirectory(directory); FilesEx.plainDirectory(game);
        } catch (Exception e) { problem = e.getMessage() == null ? e.toString() : e.getMessage(); }
        return new Instance(directory, root == null ? null : root.toAbsolutePath().normalize(), name, id, mc, loader, version, game, problem);
    }
}
