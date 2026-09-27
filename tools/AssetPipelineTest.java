import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.visual.CanonicalPaletteValues;
import com.mistaboom.essence_ascendance.visual.TexturePixels;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exercises the build tool against isolated authoring projects, without loading Minecraft. */
public final class AssetPipelineTest {
    private static final Gson JSON = new Gson();
    private static final String ROOT = "assets/essence_ascendance/";
    private static final String[] TIERS = {"latent", "dormant", "awakened", "resonant", "ascendant", "transcendent"};
    private static int assertions;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected <test-work-directory-under-build> <art-directory> <generated-resources>");
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        sourceEditsAndDeterminism(directory);
        invalidSourcesPreservePublishedResources(directory);
        ambiguousOwnershipFails(directory);
        containment(directory);
        sourceCoverage(directory);
        resourceReferences(directory);
        tierAndDrawModels(directory);
        blockInventoryModels(directory);
        stitchedMeshMaterials(directory);
        invalidStitchedMaterials(directory);
        shieldSourceContract(Path.of(args[1]), Path.of(args[2]));
        System.out.println("AssetPipelineTest: " + assertions + " build-pipeline assertions PASS");
    }

    private static void sourceEditsAndDeterminism(Path directory) throws Exception {
        try (Fixture f = new Fixture(directory)) {
            f.generate();
            Path texture = f.resource("textures/item/pipeline_tool/base.png");
            byte[] first = Files.readAllBytes(texture);
            check(Arrays.equals(first, png(0xFF406080)), "Embedded texture bytes must be exported losslessly");
            Map<String, byte[]> before = snapshot(f.output);
            FileTime marker = FileTime.fromMillis(1_600_000_000_000L);
            Files.setLastModifiedTime(texture, marker);
            f.generate();
            check(same(before, snapshot(f.output)), "Unchanged input must generate deterministic resources");
            check(Files.getLastModifiedTime(texture).equals(marker), "Unchanged resources must not be rewritten");

            byte[] changed = png(0xFFC0D0E0);
            f.texture(0).addProperty("source", embedded(changed));
            f.save();
            byte[] authoring = Files.readAllBytes(f.source);
            f.run();
            check(Arrays.equals(changed, Files.readAllBytes(texture)), "Editing embedded art must update the next build");
            check(!Arrays.equals(first, Files.readAllBytes(texture)), "Old runtime texture must not survive a source edit");
            check(Arrays.equals(authoring, Files.readAllBytes(f.source)), "Generating must leave the BBModel untouched");

            Path stale = f.resource("textures/item/retired.png");
            Files.write(stale, first);
            f.export(0).addProperty("path", "item/pipeline_tool/repainted");
            f.state(0).getAsJsonArray("layers").set(0, new com.google.gson.JsonPrimitive("item/pipeline_tool/repainted"));
            f.generate();
            check(!Files.exists(stale), "Stale resources must not leak into subsequent builds");
            check(!Files.exists(texture), "Renaming an exported resource must remove its previous output");
            check(Arrays.equals(changed, Files.readAllBytes(f.resource("textures/item/pipeline_tool/repainted.png"))),
                    "Renamed resource must contain current source pixels");
        }
    }

    private static void invalidSourcesPreservePublishedResources(Path directory) throws Exception {
        List<InvalidCase> cases = List.of(
                new InvalidCase("Missing Blockbench entry", f -> f.export(1).addProperty("name", "missing_accent")),
                new InvalidCase("Duplicate Blockbench name", f -> f.project.getAsJsonArray("textures").add(f.texture(1).deepCopy())),
                new InvalidCase("Expected embedded PNG", f -> f.texture(1).addProperty("source", "C:/external/texture.png")),
                new InvalidCase("Invalid PNG signature", f -> f.texture(1).addProperty("source", embedded(new byte[]{1, 2, 3}))),
                new InvalidCase("Illegal base64", f -> f.texture(1).addProperty("source", "data:image/png;base64,!!!!")),
                new InvalidCase("dimensions", f -> f.texture(1).addProperty("width", 16)));
        for (InvalidCase invalid : cases) {
            try (Fixture f = new Fixture(directory)) {
                f.generate();
                Map<String, byte[]> published = snapshot(f.output);
                // The first texture is valid and changed; a later failure must prevent partial publication.
                f.texture(0).addProperty("source", embedded(png(0xFFFF0000)));
                invalid.mutation.accept(f);
                f.save();
                byte[] authoring = Files.readAllBytes(f.source);
                expectFailure(f::run, invalid.message);
                check(same(published, snapshot(f.output)), "Failed import must preserve the last complete resource set");
                check(Arrays.equals(authoring, Files.readAllBytes(f.source)), "Failed import must preserve source art");
            }
        }
    }

    private static void ambiguousOwnershipFails(Path directory) throws Exception {
        try (Fixture f = new Fixture(directory)) {
            f.assets().add(f.asset.deepCopy());
            f.save();
            expectFailure(f::run, "duplicate asset source");
            check(!Files.exists(f.output), "Duplicate source must fail before publishing");
        }
        try (Fixture f = new Fixture(directory)) {
            f.export(1).addProperty("path", f.export(0).get("path").getAsString());
            f.save();
            expectFailure(f::run, "same resource");
            check(!Files.exists(f.output), "Duplicate output must fail before publishing");
        }
    }

    private static void containment(Path directory) throws Exception {
        try (Fixture f = new Fixture(directory)) {
            f.asset.addProperty("source", "../outside.bbmodel");
            f.save();
            expectFailure(f::run, "Path escapes");
        }
        try (Fixture f = new Fixture(directory)) {
            f.asset.addProperty("source", f.source.toAbsolutePath().toString());
            f.save();
            expectFailure(f::run, "Path escapes");
        }
        try (Fixture f = new Fixture(directory)) {
            f.export(0).addProperty("path", "../../../outside");
            f.save();
            expectFailure(f::run, "Invalid resource path");
            check(!Files.exists(f.root.resolve("outside.png")), "Resource traversal must never create external files");
        }
        try (Fixture f = new Fixture(directory)) {
            f.save();
            Path outside = f.root.resolve("outside-resources");
            expectFailure(() -> f.run(outside, f.work), "Derived output must be inside");
            check(!Files.exists(outside), "Out-of-build output must fail before creating directories");
            expectFailure(() -> f.run(f.build, f.work), "Derived output must be inside");
            expectFailure(() -> f.run(f.output, f.output.resolve("work")), "must be separate");
            expectFailure(() -> f.run(f.work.resolve("resources"), f.work), "must be separate");
        }
    }

    private static void sourceCoverage(Path directory) throws Exception {
        try (Fixture f = new Fixture(directory)) {
            f.save();
            Files.writeString(f.source.resolveSibling("forgotten.bbmodel"), JSON.toJson(f.project));
            expectFailure(f::run, "missing from asset-manifest");
            check(!Files.exists(f.output), "Unregistered BBModel must fail before publishing");
        }
    }

    private static void resourceReferences(Path directory) throws Exception {
        try (Fixture f = new Fixture(directory)) {
            f.state(0).getAsJsonArray("layers").set(0, new com.google.gson.JsonPrimitive("item/missing"));
            f.save();
            expectFailure(f::run, "unexported texture");
        }
        try (Fixture f = new Fixture(directory)) {
            f.block("cube", "block/missing");
            f.save();
            expectFailure(f::run, "unexported texture");
        }
    }

    private static void tierAndDrawModels(Path directory) throws Exception {
        try (Fixture f = new Fixture(directory)) {
            JsonObject item = f.asset.getAsJsonObject("item");
            item.addProperty("tiered", true);
            f.state(0).addProperty("parent", "minecraft:item/bow");
            for (int state = 0; state < 3; state++) {
                JsonObject pulling = f.state(0).deepCopy();
                pulling.addProperty("suffix", "_pulling_" + state);
                pulling.addProperty("parent", "minecraft:item/bow_pulling_" + state);
                JsonObject predicate = new JsonObject();
                predicate.addProperty("minecraft:pulling", 1);
                if (state > 0) predicate.addProperty("minecraft:pull", state == 1 ? 0.65 : 0.9);
                pulling.add("predicate", predicate);
                item.getAsJsonArray("states").add(pulling);
            }
            f.generate();
            JsonObject root = f.model("item/pipeline_tool");
            for (int tier = 0; tier < TIERS.length; tier++) {
                for (int state = 0; state < 4; state++) {
                    String suffix = state == 0 ? "" : "_pulling_" + (state - 1);
                    String expected = "essence_ascendance:item/tier/" + TIERS[tier] + "/pipeline_tool" + suffix;
                    double pull = state < 2 ? 0 : state == 2 ? 0.65 : 0.9;
                    check(select(root, tier / 5.0, state == 0 ? 0 : 1, pull).equals(expected),
                            "Ordered predicates must choose the correct tier and bow state");
                    JsonObject model = f.model("item/tier/" + TIERS[tier] + "/pipeline_tool" + suffix);
                    check(model.get("parent").getAsString().equals(state == 0
                                    ? "minecraft:item/bow" : "minecraft:item/bow_pulling_" + (state - 1)),
                            "Generated models must inherit the appropriate vanilla display transform");
                    JsonObject layers = model.getAsJsonObject("textures");
                    check(layers.get("layer0").getAsString().endsWith("/base")
                                    && layers.get("layer1").getAsString().endsWith("/accent")
                                    && layers.get("layer2").getAsString().endsWith("/nochange"),
                            "Base, accent and untinted layers must retain their runtime tint indices");
                }
                check(select(root, tier / 5.0, 1, 0.649999).endsWith("_pulling_0"),
                        "Bow state must not advance before its threshold");
                check(select(root, tier / 5.0, 1, 0.899999).endsWith("_pulling_1"),
                        "Final bow state must not advance before its threshold");
            }
        }
    }

    private static void blockInventoryModels(Path directory) throws Exception {
        for (String shape : List.of("cube", "column")) {
            try (Fixture f = new Fixture(directory)) {
                f.block(shape, "item/pipeline_tool/base");
                f.generate();
                JsonObject item = f.model("item/pipeline_block");
                check(item.get("parent").getAsString().equals("essence_ascendance:block/pipeline_block"),
                        "Block inventories must inherit their 3D block model");
                JsonObject block = f.model("block/pipeline_block");
                check(block.get("parent").getAsString().equals("minecraft:block/"
                                + (shape.equals("cube") ? "cube_all" : "cube_column")),
                        "Block geometry must retain its standard Minecraft parent");
                if (shape.equals("column")) {
                    check(f.model("block/pipeline_block_horizontal").get("parent").getAsString()
                                    .equals("minecraft:block/cube_column_horizontal"),
                            "Column blocks must provide the horizontal model used by blockstates");
                }
            }
        }
    }

    private static void stitchedMeshMaterials(Path directory) throws Exception {
        try (Fixture f = new Fixture(directory)) {
            f.stitchedMesh();
            f.generate();
            byte[] authoring = Files.readAllBytes(f.source);
            assertStitchedPixels(f.project, "base", "accent", f.resource("textures/item/pipeline_tool"));
            Path mesh = f.resource("meshes/item/pipeline_tool.eamesh");
            try (DataInputStream input = new DataInputStream(Files.newInputStream(mesh))) {
                check(input.readInt() == 0x45414D31 && input.readInt() == 2,
                        "Both mask material assignments must share one EAM1 mesh");
                float[][] expected = {{0, 0, 0, 0, 0, 0, 0, -1}, {1, 0, 0, 1, 0, 0, 0, -1},
                        {0, -1, 0, 0, 1, 0, 0, -1}};
                for (int triangle = 0; triangle < 2; triangle++) for (float[] vertex : expected)
                    for (float value : vertex) check(input.readFloat() == value,
                            "Stitched mesh must use mask UV dimensions and declared axes without altering winding");
                check(input.read() == -1, "EAM1 must contain only its declared triangles");
            }
            Map<String, byte[]> generated = snapshot(f.output);
            f.run();
            check(same(generated, snapshot(f.output)), "Stitched resource generation must be deterministic");
            check(Arrays.equals(authoring, Files.readAllBytes(f.source)), "Stitching must not merge authoring materials in place");
            check(generated.size() == 8, "Stitched meshes export only one mesh, six composites and one emission mask");
            check(!Files.exists(f.resource("textures/item/pipeline_tool/base.png")),
                    "Intermediate base masks must not be runtime resources");
        }
    }

    private static void invalidStitchedMaterials(Path directory) throws Exception {
        List<InvalidCase> cases = List.of(
                new InvalidCase("UV dimensions must match", f -> f.texture(1).addProperty("uv_width", 4)),
                new InvalidCase("unrelated material", f -> f.firstFace().addProperty("texture", 2)),
                new InvalidCase("Invalid stitched face material", f -> f.firstFace().addProperty("texture", 0.5)),
                new InvalidCase("UV outside material frame", f -> f.firstFace().getAsJsonObject("uv")
                        .add("b", JsonParser.parseString("[3,0]"))),
                new InvalidCase("Changed authoring rotation", f -> f.project.getAsJsonArray("elements").get(0)
                        .getAsJsonObject().add("rotation", JsonParser.parseString("[90,0,0]"))),
                new InvalidCase("distinct", f -> f.asset.getAsJsonObject("bake").addProperty("accent", "base")));
        for (InvalidCase invalid : cases) {
            try (Fixture f = new Fixture(directory)) {
                f.stitchedMesh();
                f.generate();
                Map<String, byte[]> published = snapshot(f.output);
                invalid.mutation.accept(f);
                f.save();
                expectFailure(f::run, invalid.message);
                check(same(published, snapshot(f.output)), "Invalid stitched geometry/materials must preserve published assets");
            }
        }
    }

    /** Validate the actual shipped shield, not just the miniature importer fixtures. */
    private static void shieldSourceContract(Path art, Path output) throws Exception {
        JsonObject shield = BlockbenchSource.read(art.resolve("source/models/item/ascendance_shield.bbmodel"));
        assertStitchedPixels(shield, "ascendance_shield_base", "ascendance_shield_accent",
                output.resolve(ROOT + "textures/item/ascendance_shield"));
        JsonObject element = BlockbenchSource.named(shield.getAsJsonArray("elements"), "Shield");
        JsonObject faces = element.getAsJsonObject("faces"), vertices = element.getAsJsonObject("vertices");
        JsonObject mask = BlockbenchSource.named(shield.getAsJsonArray("textures"), "ascendance_shield_base");
        double uvWidth = mask.get("uv_width").getAsDouble(), uvHeight = mask.get("uv_height").getAsDouble();
        Path path = output.resolve(ROOT + "meshes/item/ascendance_shield.eamesh");
        try (DataInputStream mesh = new DataInputStream(Files.newInputStream(path))) {
            check(mesh.readInt() == 0x45414D31 && mesh.readInt() == faces.size(), "Shield must export every authored triangle");
            for (var entry : faces.entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                for (var id : face.getAsJsonArray("vertices")) {
                    JsonArray point = vertices.getAsJsonArray(id.getAsString());
                    JsonArray uv = face.getAsJsonObject("uv").getAsJsonArray(id.getAsString());
                    float[] expected = {(float) (point.get(0).getAsDouble() / 16),
                            (float) (-point.get(2).getAsDouble() / 16), (float) (point.get(1).getAsDouble() / 16),
                            (float) (uv.get(0).getAsDouble() / uvWidth), (float) (uv.get(1).getAsDouble() / uvHeight)};
                    for (float value : expected) check(mesh.readFloat() == value,
                            "Shield pose must retain authored geometry/UVs in vanilla shield coordinates");
                    double nx = mesh.readFloat(), ny = mesh.readFloat(), nz = mesh.readFloat();
                    check(Math.abs(nx * nx + ny * ny + nz * nz - 1) < 1e-6, "Shield normals must be normalized");
                }
            }
            check(mesh.read() == -1, "Shield mesh must not contain extra geometry");
        }
    }

    private static void assertStitchedPixels(JsonObject project, String baseName, String accentName, Path directory) throws Exception {
        byte[] basePng = BlockbenchSource.png(BlockbenchSource.named(project.getAsJsonArray("textures"), baseName));
        byte[] accentPng = BlockbenchSource.png(BlockbenchSource.named(project.getAsJsonArray("textures"), accentName));
        BufferedImage base = ImageIO.read(new java.io.ByteArrayInputStream(basePng));
        BufferedImage accent = ImageIO.read(new java.io.ByteArrayInputStream(accentPng));
        check(Arrays.equals(accentPng, Files.readAllBytes(directory.resolve("emission.png"))),
                "Emission must preserve every authored accent byte, including alpha and hidden RGB");
        int[] bases = {CanonicalPaletteValues.LATENT_PRIMARY, CanonicalPaletteValues.DORMANT_PRIMARY,
                CanonicalPaletteValues.AWAKENED_PRIMARY, CanonicalPaletteValues.RESONANT_PRIMARY,
                CanonicalPaletteValues.ASCENDANT_PRIMARY, CanonicalPaletteValues.TRANSCENDENT_PRIMARY};
        int[] accents = {CanonicalPaletteValues.LATENT_METAL, CanonicalPaletteValues.DORMANT_METAL,
                CanonicalPaletteValues.AWAKENED_METAL, CanonicalPaletteValues.RESONANT_METAL,
                CanonicalPaletteValues.ASCENDANT_METAL, CanonicalPaletteValues.TRANSCENDENT_METAL};
        for (int tier = 0; tier < TIERS.length; tier++) {
            BufferedImage image = ImageIO.read(directory.resolve(TIERS[tier] + ".png").toFile());
            check(image.getWidth() == base.getWidth() && image.getHeight() == base.getHeight(),
                    "Stitched images must keep source resolution");
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int expected = TexturePixels.overArgb(TexturePixels.tintArgb(accent.getRGB(x, y), accents[tier]),
                        TexturePixels.tintArgb(base.getRGB(x, y), bases[tier]));
                check(image.getRGB(x, y) == expected, "Tier composite must preserve palette tint, source shading and mask coverage");
            }
        }
    }

    /** Minecraft chooses the final override whose property thresholds are all satisfied. */
    private static String select(JsonObject root, double tier, double pulling, double pull) {
        String selected = root.get("parent").getAsString();
        Map<String, Double> properties = Map.of("essence_ascendance:tier", tier,
                "minecraft:pulling", pulling, "minecraft:pull", pull);
        for (var value : root.getAsJsonArray("overrides")) {
            JsonObject override = value.getAsJsonObject();
            boolean matches = override.getAsJsonObject("predicate").entrySet().stream()
                    .allMatch(e -> properties.getOrDefault(e.getKey(), 0.0) >= e.getValue().getAsDouble());
            if (matches) selected = override.get("model").getAsString();
        }
        return selected;
    }

    private static byte[] png(int pixel) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, pixel);
        image.setRGB(1, 1, pixel);
        var out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", out)) throw new IllegalStateException("Missing PNG encoder");
        return out.toByteArray();
    }

    private static byte[] pixels(int... pixels) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 2, 2, pixels, 0, 2);
        var out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", out)) throw new IllegalStateException("Missing PNG encoder");
        return out.toByteArray();
    }

    private static String embedded(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    private static Map<String, byte[]> snapshot(Path directory) throws Exception {
        Map<String, byte[]> result = new TreeMap<>();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                result.put(directory.relativize(file).toString(), Files.readAllBytes(file));
            }
        }
        return result;
    }

    private static boolean same(Map<String, byte[]> left, Map<String, byte[]> right) {
        return left.keySet().equals(right.keySet())
                && left.keySet().stream().allMatch(path -> Arrays.equals(left.get(path), right.get(path)));
    }

    private static void expectFailure(Action action, String diagnostic) throws Exception {
        try {
            action.run();
        } catch (Exception failure) {
            StringBuilder messages = new StringBuilder();
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                messages.append(cause.getMessage()).append('\n');
            }
            check(messages.toString().contains(diagnostic), "Failure must explain " + diagnostic + ": " + messages);
            return;
        }
        throw new AssertionError("Expected generation failure: " + diagnostic);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private record InvalidCase(String message, Mutation mutation) { }
    @FunctionalInterface private interface Mutation { void accept(Fixture fixture) throws Exception; }
    @FunctionalInterface private interface Action { void run() throws Exception; }

    private static final class Fixture implements AutoCloseable {
        final Path root;
        final Path art;
        final Path source;
        final Path build;
        final Path output;
        final Path work;
        final JsonObject project = new JsonObject();
        final JsonObject manifest = new JsonObject();
        final JsonObject asset = new JsonObject();

        Fixture(Path directory) throws Exception {
            root = Files.createTempDirectory(directory, "asset-pipeline-");
            art = root.resolve("art");
            source = art.resolve("source/models/item/test.bbmodel");
            build = root.resolve("common/build");
            output = build.resolve("resources");
            work = build.resolve("work");
            Files.createDirectories(source.getParent());
            Files.createDirectories(build);
            JsonArray textures = new JsonArray();
            JsonArray exports = new JsonArray();
            JsonArray layers = new JsonArray();
            for (String name : List.of("base", "accent", "nochange")) {
                JsonObject texture = new JsonObject();
                texture.addProperty("name", name);
                texture.addProperty("width", 2);
                texture.addProperty("height", 2);
                texture.addProperty("source", embedded(png(0xFF406080)));
                textures.add(texture);
                JsonObject export = new JsonObject();
                export.addProperty("name", name);
                export.addProperty("path", "item/pipeline_tool/" + name);
                exports.add(export);
                layers.add("item/pipeline_tool/" + name);
            }
            project.add("textures", textures);
            asset.addProperty("source", "source/models/item/test.bbmodel");
            asset.addProperty("category", "layered_item");
            asset.add("textures", exports);
            JsonObject state = new JsonObject();
            state.addProperty("suffix", "");
            state.addProperty("parent", "minecraft:item/handheld");
            state.add("layers", layers);
            JsonArray states = new JsonArray();
            states.add(state);
            JsonObject item = new JsonObject();
            item.addProperty("id", "pipeline_tool");
            item.addProperty("tiered", false);
            item.add("states", states);
            asset.add("item", item);
            manifest.addProperty("schemaVersion", 1);
            manifest.addProperty("namespace", "essence_ascendance");
            JsonArray assets = new JsonArray();
            assets.add(asset);
            manifest.add("assets", assets);
        }

        JsonObject texture(int index) { return project.getAsJsonArray("textures").get(index).getAsJsonObject(); }
        JsonObject export(int index) { return asset.getAsJsonArray("textures").get(index).getAsJsonObject(); }
        JsonObject state(int index) { return asset.getAsJsonObject("item").getAsJsonArray("states").get(index).getAsJsonObject(); }
        JsonArray assets() { return manifest.getAsJsonArray("assets"); }
        Path resource(String relative) { return output.resolve(ROOT + relative); }
        JsonObject model(String name) throws Exception {
            return JsonParser.parseString(Files.readString(resource("models/" + name + ".json"))).getAsJsonObject();
        }

        void block(String shape, String texture) {
            asset.addProperty("category", "block");
            asset.remove("item");
            JsonObject block = new JsonObject();
            block.addProperty("id", "pipeline_block");
            block.addProperty("shape", shape);
            block.addProperty("texture", texture);
            asset.add("block", block);
        }

        JsonObject firstFace() {
            return project.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("front");
        }

        void stitchedMesh() throws Exception {
            asset.addProperty("category", "stitched_mesh");
            asset.remove("item");
            asset.remove("textures");
            asset.add("bake", JsonParser.parseString("""
                    {"base":"base","accent":"accent","output":"textures/item/pipeline_tool"}
                    """));
            asset.add("mesh", JsonParser.parseString("""
                    {"output":"meshes/item/pipeline_tool.eamesh","axes":["x","-y","z"],
                     "sourceOrigin":[0,0,0],"sourceRotation":[0,0,0]}
                    """));
            project.add("meta", JsonParser.parseString("{\"model_format\":\"free\"}"));
            project.add("resolution", JsonParser.parseString("{\"width\":64,\"height\":64}"));
            project.add("outliner", JsonParser.parseString("[\"fixture\"]"));
            project.add("elements", JsonParser.parseString("""
                    [{"name":"Shield","uuid":"fixture","type":"mesh","origin":[0,0,0],"rotation":[0,0,0],
                      "vertices":{"a":[0,0,0],"b":[16,0,0],"c":[0,16,0]},
                      "faces":{"front":{"vertices":["a","b","c"],"uv":{"a":[0,0],"b":[2,0],"c":[0,2]},"texture":0},
                               "second":{"vertices":["a","b","c"],"uv":{"a":[0,0],"b":[2,0],"c":[0,2]},"texture":1}}}]
                    """));
            for (int i = 0; i < 2; i++) {
                texture(i).addProperty("uv_width", 2);
                texture(i).addProperty("uv_height", 2);
            }
            texture(0).addProperty("source", embedded(pixels(0xFFD0D0D0, 0, 0x80606060, 0x00505050)));
            texture(1).addProperty("source", embedded(pixels(0, 0xFFFFFFFF, 0x80C0C0C0, 0x00202020)));
        }

        void save() throws Exception {
            Files.writeString(source, JSON.toJson(project));
            Files.writeString(art.resolve("asset-manifest.json"), JSON.toJson(manifest));
        }

        void generate() throws Exception { save(); run(); }
        void run() throws Exception { run(output, work); }
        void run(Path resources, Path intermediate) throws Exception {
            AssetPipeline.main(new String[]{art.toString(), resources.toString(), intermediate.toString()});
        }

        @Override
        public void close() throws Exception {
            // Every fixture owns its unique temporary root, including all deliberately invalid inputs.
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
