import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import com.mistaboom.essence_ascendance.visual.CanonicalPaletteValues;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic branding artwork; no Minecraft, image libraries, fonts, or random inputs. */
public final class LogoGenerator {
    private static final int CANVAS = 1024;
    private static final int[] SIZES = {32, 64, 128, 256, 400, 512, 1024};
    // Preserve NexusVisuals.CATEGORY_COLORS order, clockwise from the top.
    private static final int[] CATEGORIES = {
            CanonicalPaletteValues.OFFENSE, CanonicalPaletteValues.DEFENSE,
            CanonicalPaletteValues.MOBILITY, CanonicalPaletteValues.UTILITY,
            CanonicalPaletteValues.VITALITY, CanonicalPaletteValues.GATHERING};
    private static final int BACKGROUND = AscendanceUiPalette.SURFACE;
    private static final int HALO = mix(BACKGROUND, CanonicalPaletteValues.TRANSCENDENT_PRIMARY, 0.45);
    private static final int GOLD = CanonicalPaletteValues.TRANSCENDENT_METAL;
    private static final String BASENAME = "essence_ascendance_logo";

    private record Facet(int rgb, double[] points) { }

    private LogoGenerator() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <branding-output-directory> <preview-directory>");
        }
        Path output = Path.of(args[0]);
        Path preview = Path.of(args[1]);
        Files.createDirectories(output);
        Files.createDirectories(preview);
        List<Facet> facets = emblem();
        Files.writeString(output.resolve(BASENAME + ".svg"), svg(facets), StandardCharsets.UTF_8);
        for (int size : SIZES) {
            Path file = output.resolve(BASENAME + "_" + size + ".png");
            BufferedImage image = render(facets, size);
            writePng(file, image);
            BufferedImage decoded = ImageIO.read(file.toFile());
            if (decoded == null || decoded.getWidth() != size || decoded.getHeight() != size) {
                throw new IOException("Invalid PNG dimensions: " + file);
            }
            if (size == 512 && Files.size(file) > 256 * 1024) {
                throw new IOException("512px icon exceeds Modrinth's 256 KiB icon API limit");
            }
            System.out.printf(Locale.ROOT, "%s: %dx%d, %,d bytes%n",
                    file.getFileName(), size, size, Files.size(file));
        }
        writePng(preview.resolve("logo-preview.png"), preview(facets));
    }

    private static List<Facet> emblem() {
        List<Facet> facets = new ArrayList<>();
        // Six broad, interrupted orbital bands. The little bevel is secondary at small sizes.
        for (int i = 0; i < 6; i++) {
            double start = -90 + i * 60 + 12;
            double end = -90 + (i + 1) * 60 - 12;
            band(facets, 306, 338, start, end, CanonicalPaletteValues.RESONANT_PRIMARY);
            band(facets, 326, 338, start, end, CanonicalPaletteValues.ASCENDANT_PRIMARY);
            band(facets, 306, 311, start, end, CanonicalPaletteValues.AWAKENED_METAL);
        }

        // A long kite, rather than a regular diamond: its higher shoulder points upward.
        // The dark outer silhouette gives the core separation from the orbital band.
        polygon(facets, BACKGROUND, 512, 226, 660, 526, 512, 804, 364, 526);
        polygon(facets, CanonicalPaletteValues.ASCENDANT_METAL,
                512, 246, 642, 526, 512, 780, 382, 526);
        polygon(facets, GOLD, 512, 246, 512, 502, 382, 526);
        polygon(facets, CanonicalPaletteValues.RESONANT_METAL,
                512, 246, 642, 526, 512, 502);
        polygon(facets, CanonicalPaletteValues.AWAKENED_METAL,
                382, 526, 512, 502, 512, 780);
        polygon(facets, CanonicalPaletteValues.ASCENDANT_METAL,
                512, 502, 642, 526, 512, 780);

        // An inset ascending facet adds an identifiable arrow/peak in the crystal.
        polygon(facets, CanonicalPaletteValues.DORMANT_METAL,
                512, 400, 588, 555, 512, 528, 436, 555);
        polygon(facets, GOLD, 512, 414, 576, 544, 512, 516, 448, 544);
        polygon(facets, CanonicalPaletteValues.ASCENDANT_METAL,
                512, 414, 576, 544, 512, 516);

        // Category gems use exact canonical midtones and palette-derived shaded facets.
        for (int i = 0; i < CATEGORIES.length; i++) {
            double angle = Math.toRadians(-90 + i * 60);
            double x = 512 + Math.cos(angle) * 322;
            double y = 512 + Math.sin(angle) * 322;
            int color = CATEGORIES[i];
            diamond(facets, x, y, 54, 67, BACKGROUND);
            diamond(facets, x, y, 43, 55, color);
            polygon(facets, mix(color, GOLD, 0.28), x, y - 55, x, y, x - 43, y);
            polygon(facets, mix(color, BACKGROUND, 0.27), x + 43, y, x, y + 55, x, y);
        }
        return List.copyOf(facets);
    }

    private static void diamond(List<Facet> facets, double x, double y,
                                double width, double height, int color) {
        polygon(facets, color, x, y - height, x + width, y, x, y + height, x - width, y);
    }

    private static void band(List<Facet> facets, double inner, double outer,
                             double start, double end, int color) {
        // Polygonal contour echoes the faceted world effects, shared by SVG and PNG.
        int segments = 6;
        double[] points = new double[(segments + 1) * 4];
        int cursor = 0;
        for (int side = 0; side < 2; side++) {
            double radius = side == 0 ? outer : inner;
            for (int i = 0; i <= segments; i++) {
                double t = (side == 0 ? i : segments - i) / (double) segments;
                double angle = Math.toRadians(start + (end - start) * t);
                points[cursor++] = 512 + Math.cos(angle) * radius;
                points[cursor++] = 512 + Math.sin(angle) * radius;
            }
        }
        polygon(facets, color, points);
    }

    private static void polygon(List<Facet> facets, int color, double... points) {
        facets.add(new Facet(color, points));
    }

    private static BufferedImage render(List<Facet> facets, int size) {
        // Supersampling preserves thin beveled edges without changing the emblem geometry.
        int workingSize = size * 4;
        BufferedImage working = new BufferedImage(workingSize, workingSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = working.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(workingSize / (double) CANVAS, workingSize / (double) CANVAS);
        g.setPaint(new RadialGradientPaint(512, 480, 620, new float[]{0, 1},
                new Color[]{new Color(HALO), new Color(BACKGROUND)}));
        g.fillRect(0, 0, CANVAS, CANVAS);
        for (Facet facet : facets) {
            Path2D path = new Path2D.Double();
            path.moveTo(facet.points()[0], facet.points()[1]);
            for (int i = 2; i < facet.points().length; i += 2) {
                path.lineTo(facet.points()[i], facet.points()[i + 1]);
            }
            path.closePath();
            g.setColor(new Color(facet.rgb()));
            g.fill(path);
        }
        g.dispose();
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        // Fixed box reduction is deterministic and avoids platform-dependent image filters.
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int red = 0, green = 0, blue = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        int rgb = working.getRGB(x * 4 + sx, y * 4 + sy);
                        red += (rgb >> 16) & 255;
                        green += (rgb >> 8) & 255;
                        blue += rgb & 255;
                    }
                }
                result.setRGB(x, y, ((red + 8) / 16 << 16)
                        | ((green + 8) / 16 << 8) | ((blue + 8) / 16));
            }
        }
        return result;
    }

    private static String svg(List<Facet> facets) {
        StringBuilder result = new StringBuilder();
        result.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1024\" height=\"1024\" viewBox=\"0 0 1024 1024\" role=\"img\" aria-labelledby=\"title desc\">\n")
                .append("  <title id=\"title\">Essence Ascendance</title>\n")
                .append("  <desc id=\"desc\">An ascending faceted gold core within a broken orbit of six Essence-colored gems.</desc>\n")
                .append("  <defs><radialGradient id=\"halo\" gradientUnits=\"userSpaceOnUse\" cx=\"512\" cy=\"480\" r=\"620\">")
                .append("<stop stop-color=\"").append(hex(HALO)).append("\"/>")
                .append("<stop offset=\"1\" stop-color=\"").append(hex(BACKGROUND))
                .append("\"/></radialGradient></defs>\n")
                .append("  <rect width=\"1024\" height=\"1024\" fill=\"url(#halo)\"/>\n");
        for (Facet facet : facets) {
            result.append("  <polygon fill=\"").append(hex(facet.rgb())).append("\" points=\"");
            for (int i = 0; i < facet.points().length; i += 2) {
                if (i != 0) result.append(' ');
                result.append(String.format(Locale.ROOT, "%.3f,%.3f", facet.points()[i], facet.points()[i + 1]));
            }
            result.append("\"/>\n");
        }
        return result.append("</svg>\n").toString();
    }

    private static BufferedImage preview(List<Facet> facets) {
        BufferedImage sheet = new BufferedImage(800, 552, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(AscendanceUiPalette.RAISED_SURFACE));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.drawImage(render(facets, 512), 20, 20, null);
        g.drawImage(render(facets, 128), 572, 24, null);
        g.drawImage(render(facets, 64), 572, 192, null);
        g.drawImage(render(facets, 32), 572, 296, null);
        // Preview labels only; exported logo artwork never depends on a font.
        g.setColor(new Color(AscendanceUiPalette.PRIMARY_TEXT));
        g.drawString("512 px", 20, 547);
        g.drawString("128 px", 712, 94);
        g.drawString("64 px", 648, 230);
        g.drawString("32 px", 616, 318);
        g.dispose();
        return sheet;
    }

    private static void writePng(Path path, BufferedImage image) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG writer unavailable");
    }

    private static int mix(int a, int b, double weight) {
        int result = 0;
        for (int shift : new int[]{16, 8, 0}) {
            int channel = (int) Math.round(((a >> shift) & 255) * (1 - weight)
                    + ((b >> shift) & 255) * weight);
            result |= channel << shift;
        }
        return result;
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }
}
