import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.visual.CanonicalPaletteValues;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Manifest-driven build pipeline. Source art is read-only; all derived files live under build/. */
public final class AssetPipeline {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String NAMESPACE = "essence_ascendance";
    private static final String ROOT = "assets/" + NAMESPACE + "/";
    private static final List<String> TIERS = List.of("latent", "dormant", "awakened", "resonant", "ascendant", "transcendent");
    private static final int[] BASE_COLORS = {CanonicalPaletteValues.LATENT_PRIMARY, CanonicalPaletteValues.DORMANT_PRIMARY,
            CanonicalPaletteValues.AWAKENED_PRIMARY, CanonicalPaletteValues.RESONANT_PRIMARY,
            CanonicalPaletteValues.ASCENDANT_PRIMARY, CanonicalPaletteValues.TRANSCENDENT_PRIMARY};
    private static final int[] ACCENT_COLORS = {CanonicalPaletteValues.LATENT_METAL, CanonicalPaletteValues.DORMANT_METAL,
            CanonicalPaletteValues.AWAKENED_METAL, CanonicalPaletteValues.RESONANT_METAL,
            CanonicalPaletteValues.ASCENDANT_METAL, CanonicalPaletteValues.TRANSCENDENT_METAL};
    private final Map<String, byte[]> resources = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected <art-directory> <generated-resource-root> <work-directory>");
        Path art = Path.of(args[0]).toAbsolutePath().normalize();
        Path build = art.getParent().resolve("common/build").normalize();
        Path output = buildPath(build, Path.of(args[1]));
        Path work = buildPath(build, Path.of(args[2]));
        if (output.startsWith(work) || work.startsWith(output))
            throw new IllegalArgumentException("Resource and intermediate directories must be separate");
        new AssetPipeline().generate(art, output, work);
    }

    private void generate(Path art, Path output, Path work) throws Exception {
        JsonObject manifest = BlockbenchSource.read(art.resolve("asset-manifest.json"));
        if (manifest.get("schemaVersion").getAsInt() != 1 || !NAMESPACE.equals(manifest.get("namespace").getAsString()))
            throw new IllegalArgumentException("Unsupported asset manifest version or namespace");
        Files.createDirectories(work);
        Set<Path> sources = new LinkedHashSet<>();
        for (var value : manifest.getAsJsonArray("assets")) {
            JsonObject asset = value.getAsJsonObject();
            Path source = contained(art, asset.get("source").getAsString());
            requirePhysicalPath(source);
            if (!source.startsWith(art.resolve("source/models")) || !source.toString().endsWith(".bbmodel")
                    || !sources.add(source)) throw new IllegalArgumentException("Invalid or duplicate asset source: " + source);
            JsonObject project = BlockbenchSource.read(source);
            try {
                if (asset.has("textures")) exportTextures(project, asset.getAsJsonArray("textures"));
                switch (asset.get("category").getAsString()) {
                    case "layered_item" -> itemModels(asset.getAsJsonObject("item"));
                    case "block" -> blockModels(asset.getAsJsonObject("block"));
                    case "runtime_composite" -> { /* Host-dependent ore composition stays in the renderer. */ }
                    case "static_mesh" -> {
                        JsonObject options = asset.getAsJsonObject("mesh").deepCopy();
                        String meshOutput = options.remove("output").getAsString();
                        add(meshOutput, BlockbenchMeshExporter.generate(project, options));
                    }
                    case "stitched_block" -> bakeBlock(asset.getAsJsonObject("bake"), work);
                    case "stitched_mesh" -> bakeStitchedMesh(project, asset);
                    case "armor_atlas" -> bakeArmor(source, work);
                    default -> throw new IllegalArgumentException("Unknown category: " + asset.get("category"));
                }
            } catch (Exception failure) {
                throw new IOException("Asset generation failed for " + source + ": " + failure.getMessage(), failure);
            }
        }
        try (var files = Files.walk(art.resolve("source/models"))) {
            Set<Path> actual = new LinkedHashSet<>(files.filter(p -> p.toString().endsWith(".bbmodel")).toList());
            if (!actual.equals(sources)) {
                actual.removeAll(sources);
                throw new IllegalArgumentException("BBModels missing from asset-manifest.json: " + actual);
            }
        }
        // Publish only after every source, texture, model and bake has validated successfully.
        sync(output);
        Files.writeString(work.resolve("resource-index.json"), JSON.toJson(resources.keySet()) + "\n");
        System.out.println("AssetPipeline: " + sources.size() + " BBModels -> " + resources.size() + " runtime resources");
    }

    private void exportTextures(JsonObject project, JsonArray textures) throws IOException {
        for (var value : textures) {
            JsonObject export = value.getAsJsonObject();
            JsonObject source = BlockbenchSource.named(project.getAsJsonArray("textures"), export.get("name").getAsString());
            byte[] png = BlockbenchSource.png(source);
            if (export.has("transform")) {
                if (!export.get("transform").getAsString().equals("opaque_white"))
                    throw new IllegalArgumentException("Unsupported texture transform: " + export.get("transform"));
                // The Focus samples a sparse white row; preserve its established filtering-safe texture.
                BufferedImage original = ImageIO.read(new ByteArrayInputStream(png));
                BufferedImage opaque = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
                int[] pixels = new int[opaque.getWidth() * opaque.getHeight()];
                Arrays.fill(pixels, 0xFFFFFFFF);
                opaque.setRGB(0, 0, opaque.getWidth(), opaque.getHeight(), pixels, 0, opaque.getWidth());
                var encoded = new ByteArrayOutputStream();
                if (!ImageIO.write(opaque, "PNG", encoded)) throw new IOException("No PNG encoder");
                png = encoded.toByteArray();
            }
            add("textures/" + export.get("path").getAsString() + ".png", png);
        }
    }

    private void itemModels(JsonObject item) {
        String id = item.get("id").getAsString();
        boolean tiered = item.get("tiered").getAsBoolean();
        JsonArray states = item.getAsJsonArray("states");
        if (states.isEmpty() || !states.get(0).getAsJsonObject().get("suffix").getAsString().isEmpty())
            throw new IllegalArgumentException("First item state must be the idle state: " + id);
        if (!tiered && states.size() != 1)
            throw new IllegalArgumentException("Multiple states require a tiered item: " + id);
        JsonArray overrides = new JsonArray();
        List<String> tiers = tiered ? TIERS : List.of("");
        for (int tier = 0; tier < tiers.size(); tier++) {
            String prefix = tiered ? "tier/" + tiers.get(tier) + "/" : "";
            for (int stateIndex = 0; stateIndex < states.size(); stateIndex++) {
                JsonObject state = states.get(stateIndex).getAsJsonObject();
                String modelId = prefix + id + state.get("suffix").getAsString();
                JsonObject model = parent(state.get("parent").getAsString());
                JsonObject textures = new JsonObject();
                JsonArray layers = state.getAsJsonArray("layers");
                if (layers.isEmpty() || layers.size() > 5) throw new IllegalArgumentException("Invalid layers: " + modelId);
                for (int layer = 0; layer < layers.size(); layer++) {
                    String texture = layers.get(layer).getAsString();
                    if (!resources.containsKey(ROOT + "textures/" + texture + ".png"))
                        throw new IllegalArgumentException("Item references unexported texture: " + texture);
                    textures.addProperty("layer" + layer, NAMESPACE + ":" + texture);
                }
                model.add("textures", textures);
                model("item/" + modelId, model);
                if (tiered && (tier > 0 || stateIndex > 0)) {
                    JsonObject predicate = new JsonObject();
                    if (tier > 0) predicate.addProperty(NAMESPACE + ":tier", tier / 5.0);
                    if (stateIndex > 0) {
                        if (!state.has("predicate")) throw new IllegalArgumentException("State missing predicate: " + modelId);
                        state.getAsJsonObject("predicate").entrySet().forEach(e -> predicate.add(e.getKey(), e.getValue()));
                    }
                    JsonObject override = new JsonObject();
                    override.add("predicate", predicate);
                    override.addProperty("model", NAMESPACE + ":item/" + modelId);
                    overrides.add(override);
                }
            }
        }
        if (tiered) {
            JsonObject root = parent(NAMESPACE + ":item/tier/latent/" + id);
            root.add("overrides", overrides);
            model("item/" + id, root);
        }
    }

    private void blockModels(JsonObject block) {
        String id = block.get("id").getAsString();
        requireTexture(block.get("texture").getAsString());
        String texture = NAMESPACE + ":" + block.get("texture").getAsString();
        switch (block.get("shape").getAsString()) {
            case "cube" -> {
                JsonObject model = parent("minecraft:block/cube_all");
                JsonObject textures = new JsonObject();
                textures.addProperty("all", texture);
                model.add("textures", textures);
                model("block/" + id, model);
            }
            case "column" -> {
                for (String suffix : List.of("", "_horizontal")) {
                    JsonObject model = parent("minecraft:block/cube_column" + suffix);
                    JsonObject textures = new JsonObject();
                    textures.addProperty("end", texture);
                    textures.addProperty("side", texture);
                    model.add("textures", textures);
                    model("block/" + id + suffix, model);
                }
            }
            default -> throw new IllegalArgumentException("Unknown block shape: " + block.get("shape"));
        }
        model("item/" + id, parent(NAMESPACE + ":block/" + id));
    }

    private void bakeBlock(JsonObject bake, Path work) throws Exception {
        Path scratch = Files.createTempDirectory(work, "block-bake-");
        try {
            Path base = scratch.resolve("base.png"), accent = scratch.resolve("accent.png");
            Files.write(base, requireTexture(bake.get("base").getAsString()));
            Files.write(accent, requireTexture(bake.get("accent").getAsString()));
            Path baked = scratch.resolve("resources");
            EssenceBlockTextureGenerator.main(new String[]{base.toString(), accent.toString(), baked.toString()});
            collect(baked);
        } finally {
            deleteScratch(scratch);
        }
    }

    private void bakeArmor(Path source, Path work) throws Exception {
        Path masks = work.resolve("armor-masks");
        AscendanceArmorGenerator.main(new String[]{"--export-masks", source.toString(), masks.toString()});
        Path scratch = Files.createTempDirectory(work, "armor-bake-");
        try {
            AscendanceArmorGenerator.main(new String[]{source.toString(), scratch.toString(), masks.toString()});
            collect(scratch);
        } finally {
            deleteScratch(scratch);
        }
    }

    /** Complementary material masks share one mesh/UV frame and one normally lit surface. */
    private void bakeStitchedMesh(JsonObject project, JsonObject asset) throws IOException {
        JsonObject bake = asset.getAsJsonObject("bake");
        String baseName = bake.get("base").getAsString(), accentName = bake.get("accent").getAsString();
        if (baseName.equals(accentName)) throw new IllegalArgumentException("Stitched base and accent must be distinct");
        JsonArray textures = project.getAsJsonArray("textures");
        JsonObject baseTexture = BlockbenchSource.named(textures, baseName);
        JsonObject accentTexture = BlockbenchSource.named(textures, accentName);
        BufferedImage base = ImageIO.read(new ByteArrayInputStream(BlockbenchSource.png(baseTexture)));
        byte[] accentPng = BlockbenchSource.png(accentTexture);
        BufferedImage accent = ImageIO.read(new ByteArrayInputStream(accentPng));
        if (base.getWidth() != accent.getWidth() || base.getHeight() != accent.getHeight())
            throw new IllegalArgumentException("Stitched base and accent dimensions must match");
        double uvWidth = maskUvSize(baseTexture, "uv_width"), uvHeight = maskUvSize(baseTexture, "uv_height");
        if (uvWidth != maskUvSize(accentTexture, "uv_width") || uvHeight != maskUvSize(accentTexture, "uv_height"))
            throw new IllegalArgumentException("Stitched base and accent UV dimensions must match");

        // EAM1 has one texture binding. Normalize only a private export view; the editable
        // BBModel keeps both masks, its original material assignments, and every face/UV.
        JsonObject merged = project.deepCopy();
        JsonArray mergedTextures = new JsonArray();
        mergedTextures.add(baseTexture.deepCopy());
        merged.add("textures", mergedTextures);
        JsonObject resolution = new JsonObject();
        resolution.addProperty("width", uvWidth);
        resolution.addProperty("height", uvHeight);
        merged.add("resolution", resolution);
        for (var element : merged.getAsJsonArray("elements")) {
            JsonObject mesh = element.getAsJsonObject();
            if (!mesh.has("faces")) throw new IllegalArgumentException("Stitched mesh is missing faces");
            for (var entry : mesh.getAsJsonObject("faces").entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                var material = face.get("texture");
                if (material == null || !material.isJsonPrimitive() || !material.getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("Stitched face must reference a base or accent material: " + entry.getKey());
                double index = material.getAsDouble();
                if (!Double.isFinite(index) || index != Math.rint(index) || index < 0 || index >= textures.size())
                    throw new IllegalArgumentException("Invalid stitched face material: " + entry.getKey());
                String name = textures.get((int) index).getAsJsonObject().get("name").getAsString();
                if (!name.equals(baseName) && !name.equals(accentName))
                    throw new IllegalArgumentException("Stitched face uses an unrelated material: " + name);
                face.addProperty("texture", 0);
                JsonObject coordinates = face.getAsJsonObject("uv");
                if (coordinates == null) throw new IllegalArgumentException("Stitched face is missing UV coordinates");
                for (var value : coordinates.entrySet()) {
                    var coordinate = value.getValue();
                    if (!coordinate.isJsonArray() || coordinate.getAsJsonArray().size() != 2)
                        throw new IllegalArgumentException("Stitched face requires two UV coordinates");
                    double u = coordinate.getAsJsonArray().get(0).getAsDouble();
                    double v = coordinate.getAsJsonArray().get(1).getAsDouble();
                    if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0 || v < 0 || u > uvWidth || v > uvHeight)
                        throw new IllegalArgumentException("Stitched face UV outside material frame: " + entry.getKey());
                }
            }
        }
        JsonObject mesh = asset.getAsJsonObject("mesh").deepCopy();
        String meshOutput = mesh.remove("output").getAsString();
        add(meshOutput, BlockbenchMeshExporter.generate(merged, mesh));
        String textureDirectory = bake.get("output").getAsString();
        for (int tier = 0; tier < TIERS.size(); tier++) {
            BufferedImage stitched = MaskedTextureWriter.compose(base, accent,
                    BASE_COLORS[tier], ACCENT_COLORS[tier], false);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            if (!ImageIO.write(stitched, "PNG", encoded)) throw new IOException("No PNG encoder");
            add(textureDirectory + "/" + TIERS.get(tier) + ".png", encoded.toByteArray());
        }
        // Preserve the exact grayscale ARGB mask, including hidden RGB; runtime applies
        // the same luminous tint and current opacity progression as equipped armor.
        add(textureDirectory + "/emission.png", accentPng);
    }

    private static double maskUvSize(JsonObject texture, String key) {
        if (!texture.has(key)) throw new IllegalArgumentException("Stitched mask is missing " + key);
        double size = texture.get(key).getAsDouble();
        if (!Double.isFinite(size) || size <= 0) throw new IllegalArgumentException("Invalid stitched mask " + key);
        return size;
    }

    private byte[] requireTexture(String texture) {
        byte[] bytes = resources.get(ROOT + "textures/" + texture + ".png");
        if (bytes == null) throw new IllegalArgumentException("Bake references unexported texture: " + texture);
        return bytes;
    }

    private void collect(Path directory) throws IOException {
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String relative = directory.relativize(file).toString().replace('\\', '/');
                if (!relative.startsWith(ROOT)) throw new IOException("Unexpected baker output: " + relative);
                add(relative.substring(ROOT.length()), Files.readAllBytes(file));
            }
        }
    }

    private void model(String id, JsonObject model) {
        add("models/" + id + ".json", (JSON.toJson(model) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject parent(String value) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", value);
        return model;
    }

    private void add(String relative, byte[] bytes) {
        if (!relative.matches("[a-z0-9_/.-]+") || relative.startsWith("/") || relative.contains(".."))
            throw new IllegalArgumentException("Invalid resource path: " + relative);
        if (resources.putIfAbsent(ROOT + relative, bytes) != null)
            throw new IllegalArgumentException("Multiple exporters own the same resource: " + relative);
    }

    private void sync(Path output) throws IOException {
        requirePhysicalPath(output);
        Files.createDirectories(output);
        try (var files = Files.walk(output)) {
            for (Path file : files.toList()) requirePhysicalPath(file);
        }
        for (var resource : resources.entrySet()) {
            Path file = contained(output, resource.getKey());
            Files.createDirectories(file.getParent());
            if (!Files.exists(file) || !Arrays.equals(Files.readAllBytes(file), resource.getValue()))
                Files.write(file, resource.getValue());
        }
        try (var files = Files.walk(output)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = output.relativize(file).toString().replace('\\', '/');
                if (!resources.containsKey(relative)) Files.delete(file);
            }
        }
    }

    private static Path buildPath(Path build, Path path) throws IOException {
        Path resolved = path.toAbsolutePath().normalize();
        if (!resolved.startsWith(build) || resolved.equals(build))
            throw new IllegalArgumentException("Derived output must be inside " + build + ": " + resolved);
        requirePhysicalPath(resolved);
        return resolved;
    }

    /** Reject redirected paths before reading sources or replacing generated output. */
    private static void requirePhysicalPath(Path path) throws IOException {
        Path existing = path.toAbsolutePath().normalize();
        while (!Files.exists(existing, java.nio.file.LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (!existing.toRealPath().equals(existing))
            throw new IOException("Asset paths must not traverse symbolic links or junctions: " + path);
    }

    private static Path contained(Path root, String relative) {
        Path result = root.resolve(relative).normalize();
        if (Path.of(relative).isAbsolute() || !result.startsWith(root) || result.equals(root))
            throw new IllegalArgumentException("Path escapes asset root: " + relative);
        return result;
    }

    private static void deleteScratch(Path scratch) throws IOException {
        try (var files = Files.walk(scratch)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
