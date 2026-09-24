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
        BufferedImage composite = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int base = tint(baseMask.getRGB(x, y), baseColor);
                int accent = tint(accentMask.getRGB(x, y), accentColor);
                int pixel = over(accent, base);
                if (((pixel >>> 24) & 0xFF) != 0xFF) {
                    throw new IllegalStateException(
                            "Essentium masks leave a transparent pixel at " + x + "," + y
                    );
                }
                composite.setRGB(x, y, pixel);
            }
        }

        Files.createDirectories(path.getParent());
        if (!ImageIO.write(composite, "png", path.toFile())) {
            throw new IOException("No PNG writer is available for " + path);
        }
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

    private static int tint(int argb, int color) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = ((argb >>> 16) & 0xFF) * ((color >>> 16) & 0xFF) / 255;
        int green = ((argb >>> 8) & 0xFF) * ((color >>> 8) & 0xFF) / 255;
        int blue = (argb & 0xFF) * (color & 0xFF) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int over(int foreground, int background) {
        int foregroundAlpha = (foreground >>> 24) & 0xFF;
        int backgroundAlpha = (background >>> 24) & 0xFF;
        int outputAlpha = foregroundAlpha
                + (backgroundAlpha * (255 - foregroundAlpha) + 127) / 255;
        if (outputAlpha == 0) {
            return 0;
        }

        int foregroundRed = (foreground >>> 16) & 0xFF;
        int foregroundGreen = (foreground >>> 8) & 0xFF;
        int foregroundBlue = foreground & 0xFF;
        int backgroundRed = (background >>> 16) & 0xFF;
        int backgroundGreen = (background >>> 8) & 0xFF;
        int backgroundBlue = background & 0xFF;
        int remainingBackground = (backgroundAlpha * (255 - foregroundAlpha) + 127) / 255;

        int red = (foregroundRed * foregroundAlpha + backgroundRed * remainingBackground)
                / outputAlpha;
        int green = (foregroundGreen * foregroundAlpha + backgroundGreen * remainingBackground)
                / outputAlpha;
        int blue = (foregroundBlue * foregroundAlpha + backgroundBlue * remainingBackground)
                / outputAlpha;
        return outputAlpha << 24 | red << 16 | green << 8 | blue;
    }
}
