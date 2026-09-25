import com.mistaboom.essence_ascendance.visual.TexturePixels;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared block/equipment mask baking. Never renders complementary masks as separate surfaces. */
public final class MaskedTextureWriter {
    private MaskedTextureWriter() { }

    public static void write(Path path, BufferedImage base, BufferedImage accent,
                             int baseColor, int accentColor, boolean requireOpaque) throws IOException {
        writeImage(path, compose(base, accent, baseColor, accentColor, requireOpaque));
    }

    public static BufferedImage compose(BufferedImage base, BufferedImage accent,
                                        int baseColor, int accentColor, boolean requireOpaque) {
        int width = base.getWidth();
        int height = base.getHeight();
        if (accent.getWidth() != width || accent.getHeight() != height)
            throw new IllegalArgumentException("Base and accent mask dimensions must match");
        BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = TexturePixels.overArgb(
                        TexturePixels.tintArgb(accent.getRGB(x, y), accentColor),
                        TexturePixels.tintArgb(base.getRGB(x, y), baseColor));
                if (requireOpaque && pixel >>> 24 != 255)
                    throw new IllegalStateException("Masks leave a transparent pixel at " + x + "," + y);
                composite.setRGB(x, y, pixel);
            }
        }
        return composite;
    }

    public static void writeImage(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile()))
            throw new IOException("No PNG writer is available for " + path);
    }
}
