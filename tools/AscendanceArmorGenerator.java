import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.visual.CanonicalPaletteValues;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Imports the authored armor meshes and bakes complementary masks into one atlas per equipment slot. */
public final class AscendanceArmorGenerator {
    private static final int MESH_MAGIC = 0x45414D31; // "EAM1", shared with other authored mod meshes.
    private static final String ROOT = "assets/essence_ascendance/";
    private static final String[] TIERS = {"latent", "dormant", "awakened", "resonant", "ascendant", "transcendent"};
    private static final int[] BASE = {CanonicalPaletteValues.LATENT_PRIMARY, CanonicalPaletteValues.DORMANT_PRIMARY,
            CanonicalPaletteValues.AWAKENED_PRIMARY, CanonicalPaletteValues.RESONANT_PRIMARY,
            CanonicalPaletteValues.ASCENDANT_PRIMARY, CanonicalPaletteValues.TRANSCENDENT_PRIMARY};
    private static final int[] ACCENT = {CanonicalPaletteValues.LATENT_METAL, CanonicalPaletteValues.DORMANT_METAL,
            CanonicalPaletteValues.AWAKENED_METAL, CanonicalPaletteValues.RESONANT_METAL,
            CanonicalPaletteValues.ASCENDANT_METAL, CanonicalPaletteValues.TRANSCENDENT_METAL};
    private static final List<PartSpec> PARTS = List.of(
            new PartSpec("Head", 256, 256, 0, 0, 0),
            new PartSpec("Chest", 256, 256, 0, 0, 0),
            new PartSpec("Left_Arm", 128, 128, 5, 2, 0),
            new PartSpec("Right_Arm", 128, 128, -5, 2, 0),
            new PartSpec("Left_Leg", 128, 128, 1.9f, 12, 0),
            new PartSpec("Right_Leg", 128, 128, -1.9f, 12, 0),
            new PartSpec("Left_Foot", 128, 128, 1.9f, 12, 0),
            new PartSpec("Right_Foot", 128, 128, -1.9f, 12, 0));

    public static void main(String[] args) throws IOException {
        if (args.length == 3 && args[0].equals("--export-masks")) {
            exportMasks(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length != 3) throw new IllegalArgumentException(
                "Expected <Armor.bbmodel> <resource-root> <mask-directory> or --export-masks <Armor.bbmodel> <mask-directory>");
        JsonObject project = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        Path output = Path.of(args[1]);
        Path masks = Path.of(args[2]);
        Map<String, Part> parts = new LinkedHashMap<>();
        for (PartSpec spec : PARTS) parts.put(spec.name, loadPart(project, spec, masks));
        List<Atlas> atlases = List.of(
                atlas("helmet", parts, "Head"),
                atlas("chestplate", parts, "Chest", "Left_Arm", "Right_Arm"),
                atlas("leggings", parts, "Left_Leg", "Right_Leg"),
                atlas("boots", parts, "Left_Foot", "Right_Foot"));
        int triangles = 0;
        for (Atlas atlas : atlases) {
            for (int tier = 0; tier < TIERS.length; tier++) bakeAtlas(output, atlas, tier);
            for (Placement placement : atlas.parts) {
                writeMesh(output, atlas, placement);
                triangles += placement.part.mesh.getAsJsonObject("faces").size();
            }
        }
        System.out.println("Imported " + triangles + " triangles across " + PARTS.size()
                + " armor parts and baked " + (atlases.size() * TIERS.length) + " single-texture variants");
    }

    private static Part loadPart(JsonObject project, PartSpec spec, Path masks) throws IOException {
        JsonObject baseTexture = named(project.getAsJsonArray("textures"), spec.name + "_Base");
        JsonObject accentTexture = named(project.getAsJsonArray("textures"), spec.name + "_Accent");
        // Normal builds read the editable PNGs; embedded Blockbench pixels are import-only.
        BufferedImage base = sourceImage(masks.resolve(maskName(spec, "base")));
        BufferedImage accent = sourceImage(masks.resolve(maskName(spec, "accent")));
        if (base.getWidth() != spec.width || base.getHeight() != spec.height
                || accent.getWidth() != spec.width || accent.getHeight() != spec.height)
            throw new IllegalArgumentException(spec.name + " masks must both be " + spec.width + "x" + spec.height);
        JsonObject mesh = named(project.getAsJsonArray("elements"), spec.name);
        // Fail instead of silently changing placement after an incompatible art edit.
        if (!mesh.get("type").getAsString().equals("mesh")
                || !mesh.getAsJsonArray("origin").equals(JsonParser.parseString("[0,0,0]"))
                || !mesh.getAsJsonArray("rotation").equals(JsonParser.parseString("[-90,0,0]")))
            throw new IllegalArgumentException("Expected " + spec.name + " mesh at origin 0,0,0 with rotation -90,0,0");
        float uvWidth = baseTexture.get("uv_width").getAsFloat();
        float uvHeight = baseTexture.get("uv_height").getAsFloat();
        if (!Float.isFinite(uvWidth) || !Float.isFinite(uvHeight) || uvWidth <= 0 || uvHeight <= 0
                || uvWidth != accentTexture.get("uv_width").getAsFloat()
                || uvHeight != accentTexture.get("uv_height").getAsFloat())
            throw new IllegalArgumentException(spec.name + " masks must use the same positive UV resolution");
        return new Part(spec, mesh, base, accent, uvWidth, uvHeight);
    }

    /** Place each source image unchanged along the top row; unused atlas space stays transparent. */
    private static Atlas atlas(String name, Map<String, Part> parts, String... names) {
        var placements = new ArrayList<Placement>();
        int width = 0;
        int height = 0;
        for (String partName : names) {
            Part part = parts.get(partName);
            placements.add(new Placement(part, width, 0));
            width += part.base.getWidth();
            height = Math.max(height, part.base.getHeight());
        }
        return new Atlas(name, width, height, List.copyOf(placements));
    }

    private static void bakeAtlas(Path output, Atlas atlas, int tier) throws IOException {
        BufferedImage image = new BufferedImage(atlas.width, atlas.height, BufferedImage.TYPE_INT_ARGB);
        for (Placement placement : atlas.parts) {
            Part part = placement.part;
            BufferedImage composite = MaskedTextureWriter.compose(part.base, part.accent, BASE[tier], ACCENT[tier], false);
            // Copy exact ARGB pixels, avoiding interpolation, alpha rounding, padding or hole filling.
            int width = composite.getWidth();
            int height = composite.getHeight();
            image.setRGB(placement.x, placement.y, width, height,
                    composite.getRGB(0, 0, width, height, null, 0, width), 0, width);
        }
        MaskedTextureWriter.writeImage(output.resolve(ROOT + "textures/armor/ascendance/generated/"
                + atlas.name + "_" + TIERS[tier] + ".png"), image);
    }

    private static void writeMesh(Path output, Atlas atlas, Placement placement) throws IOException {
        Part part = placement.part;
        JsonObject faces = part.mesh.getAsJsonObject("faces");
        JsonObject vertices = part.mesh.getAsJsonObject("vertices");
        Path meshPath = output.resolve(ROOT + "meshes/armor/ascendance/" + part.spec.name.toLowerCase(Locale.ROOT) + ".eamesh");
        Files.createDirectories(meshPath.getParent());
        try (DataOutputStream data = new DataOutputStream(Files.newOutputStream(meshPath))) {
            data.writeInt(MESH_MAGIC);
            data.writeInt(faces.size());
            for (var entry : faces.entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                JsonArray ids = face.getAsJsonArray("vertices");
                if (ids.size() != 3) throw new IllegalArgumentException(part.spec.name + " faces must be triangles: " + entry.getKey());
                float[][] triangle = new float[3][5];
                int corner = 0;
                for (var id : ids) {
                    JsonArray position = vertices.getAsJsonArray(id.getAsString());
                    JsonArray uv = face.getAsJsonObject("uv").getAsJsonArray(id.getAsString());
                    if (position == null || position.size() != 3 || uv == null || uv.size() != 2)
                        throw new IllegalArgumentException("Missing position/UV for " + part.spec.name + ": " + id);
                    float x = finite(position, 0);
                    float y = finite(position, 1);
                    float z = finite(position, 2);
                    float u = finite(uv, 0);
                    float v = finite(uv, 1);
                    if (u < 0 || u > part.uvWidth || v < 0 || v > part.uvHeight)
                        throw new IllegalArgumentException("UV outside mask for " + part.spec.name + ": " + id);
                    // Source -90 X rotation, Java conversion and the established forward-facing turn
                    // yield (x, 24-z, y). Subtract the corresponding animated humanoid part pivot.
                    triangle[corner++] = new float[]{(x - part.spec.pivotX) / 16,
                            (24 - z - part.spec.pivotY) / 16, (y - part.spec.pivotZ) / 16,
                            (placement.x + u / part.uvWidth * part.base.getWidth()) / atlas.width,
                            (placement.y + v / part.uvHeight * part.base.getHeight()) / atlas.height};
                }
                float[] normal = normal(triangle);
                for (float[] vertex : triangle) {
                    for (float value : vertex) data.writeFloat(value);
                    for (float value : normal) data.writeFloat(value);
                }
            }
        }
    }

    private static float[] normal(float[][] triangle) {
        float[] a = triangle[0], b = triangle[1], c = triangle[2];
        float x = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
        float y = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
        float z = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (!Float.isFinite(length) || length <= 1e-9f)
            throw new IllegalArgumentException("Cannot export degenerate armor triangle");
        return new float[]{x / length, y / length, z / length};
    }

    private static float finite(JsonArray values, int index) {
        float value = values.get(index).getAsFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite mesh position or UV");
        return value;
    }

    private static JsonObject named(JsonArray entries, String name) {
        for (var entry : entries) {
            JsonObject object = entry.getAsJsonObject();
            if (object.get("name").getAsString().equals(name)) return object;
        }
        throw new IllegalArgumentException("Missing Blockbench entry: " + name);
    }

    private static BufferedImage sourceImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Unreadable source PNG: " + path);
        return image;
    }

    private static String maskName(PartSpec spec, String suffix) {
        return spec.name.toLowerCase(Locale.ROOT) + "_" + suffix + ".png";
    }

    /** Explicit authoring action only; never called by the normal generation path. */
    private static void exportMasks(Path source, Path masks) throws IOException {
        JsonObject project = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        Map<String, byte[]> images = new LinkedHashMap<>();
        for (PartSpec spec : PARTS) for (String suffix : List.of("Base", "Accent")) {
            JsonObject texture = named(project.getAsJsonArray("textures"), spec.name + "_" + suffix);
            byte[] png = embeddedPng(texture);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null || image.getWidth() != spec.width || image.getHeight() != spec.height)
                throw new IOException("Invalid embedded mask dimensions: " + texture.get("name"));
            images.put(maskName(spec, suffix.toLowerCase(Locale.ROOT)), png);
        }
        // Validate all inputs first and preserve the embedded PNG bytes, including hidden RGB values.
        Files.createDirectories(masks);
        for (var image : images.entrySet()) Files.write(masks.resolve(image.getKey()), image.getValue());
        System.out.println("Explicitly imported " + images.size() + " editable armor masks from " + source);
    }

    private static byte[] embeddedPng(JsonObject texture) throws IOException {
        String source = texture.get("source").getAsString();
        if (!source.startsWith("data:image/png;base64,"))
            throw new IOException("Expected embedded PNG texture: " + texture.get("name"));
        return Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
    }

    private record PartSpec(String name, int width, int height, float pivotX, float pivotY, float pivotZ) { }
    private record Part(PartSpec spec, JsonObject mesh, BufferedImage base, BufferedImage accent,
                        float uvWidth, float uvHeight) { }
    private record Placement(Part part, int x, int y) { }
    private record Atlas(String name, int width, int height, List<Placement> parts) { }
}
