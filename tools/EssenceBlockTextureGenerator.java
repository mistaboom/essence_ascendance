import com.mistaboom.essence_ascendance.visual.CanonicalPaletteValues;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Build-time generator for the single-texture Essentium block models.
 *
 * The source base/accent images are complementary masks. This generator
 * colorizes each mask, stitches them into one opaque sprite, and emits the
 * model that uses that sprite. Keeping this outside the game renderer avoids
 * coplanar block quads and makes the generated block safe for every face.
 */
public final class EssenceBlockTextureGenerator {

    private static final String NAMESPACE = "essence_ascendance";
    private static final String MODEL_ROOT =
            "assets/essence_ascendance/models/block/generated/essentium";
    private static final String TEXTURE_ROOT =
            "assets/essence_ascendance/textures/block/generated/essentium";

    private static final Map<String, Integer> ESSENCE_COLORS = new LinkedHashMap<>();
    private static final Map<String, Integer> TIER_COLORS = new LinkedHashMap<>();

    static {
        ESSENCE_COLORS.put("offense", CanonicalPaletteValues.OFFENSE);
        ESSENCE_COLORS.put("defense", CanonicalPaletteValues.DEFENSE);
        ESSENCE_COLORS.put("vitality", CanonicalPaletteValues.VITALITY);
        ESSENCE_COLORS.put("mobility", CanonicalPaletteValues.MOBILITY);
        ESSENCE_COLORS.put("gathering", CanonicalPaletteValues.GATHERING);
        ESSENCE_COLORS.put("utility", CanonicalPaletteValues.UTILITY);

        TIER_COLORS.put("dormant", CanonicalPaletteValues.DORMANT_PRIMARY);
        TIER_COLORS.put("awakened", CanonicalPaletteValues.AWAKENED_PRIMARY);
        TIER_COLORS.put("resonant", CanonicalPaletteValues.RESONANT_PRIMARY);
        TIER_COLORS.put("ascendant", CanonicalPaletteValues.ASCENDANT_PRIMARY);
        TIER_COLORS.put("transcendent", CanonicalPaletteValues.TRANSCENDENT_PRIMARY);
    }

    private EssenceBlockTextureGenerator() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected <base-mask.png> <accent-mask.png> <generated-resource-directory>"
            );
        }

        BufferedImage baseMask = readMask(Path.of(args[0]));
        BufferedImage accentMask = readMask(Path.of(args[1]));
        Path outputRoot = Path.of(args[2]);

        writeComposite(
                outputRoot.resolve("assets/essence_ascendance/textures/block/generated/latent.png"),
                baseMask,
                accentMask,
                0xFFFFFF,
                0xFFFFFF
        );

        for (Map.Entry<String, Integer> essence : ESSENCE_COLORS.entrySet()) {
            for (Map.Entry<String, Integer> tier : TIER_COLORS.entrySet()) {
                String modelName = essence.getKey() + "_" + tier.getKey();
                Path texture = outputRoot.resolve(TEXTURE_ROOT + "/" + modelName + ".png");
                writeComposite(texture, baseMask, accentMask, tier.getValue(), essence.getValue());
                writeModel(outputRoot.resolve(MODEL_ROOT + "/" + modelName + ".json"), modelName);
            }
        }
    }

    private static BufferedImage readMask(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
            throw new IllegalArgumentException("Essentium mask must be a readable 16x16 PNG: " + path);
        }
        return image;
    }

    private static void writeComposite(
            Path path,
            BufferedImage baseMask,
            BufferedImage accentMask,
            int baseColor,
            int accentColor
    ) throws IOException {
        MaskedTextureWriter.write(path, baseMask, accentMask, baseColor, accentColor, true);
    }

    private static void writeModel(Path path, String modelName) throws IOException {
        String texture = NAMESPACE + ":block/generated/essentium/" + modelName;
        String json = "{\n"
                + "  \"parent\": \"minecraft:block/cube_all\",\n"
                + "  \"textures\": {\"all\": \"" + texture + "\"}\n"
                + "}\n";
        Files.createDirectories(path.getParent());
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

}
