import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Rasterizes the actual exported meshes and atlases with no extra lighting: texture shading is visible on its own. */
public final class PreviewAscendanceArmor {
    private record Part(String mesh, String atlas, float px, float py) { }
    private record Triangle(float[][] vertices, String atlas) { }
    private static final Part[] PARTS = {
            new Part("head", "helmet", 0, 0), new Part("chest", "chestplate", 0, 0),
            new Part("left_arm", "chestplate", 5, 2), new Part("right_arm", "chestplate", -5, 2),
            new Part("left_leg", "leggings", 1.9f, 12), new Part("right_leg", "leggings", -1.9f, 12),
            new Part("belt", "leggings", 0, 0),
            new Part("left_foot", "boots", 1.9f, 12), new Part("right_foot", "boots", -1.9f, 12)};

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("Expected <asset-namespace-directory> <preview.png> [before-asset-namespace-directory]");
        boolean comparison = args.length == 3;
        int columns = comparison ? 2 : 3, cellWidth = comparison ? 480 : 400;
        int width = columns * cellWidth, height = 1330;
        var output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(22, 27, 35)); graphics.fillRect(0, 0, width, height);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 25)); graphics.setColor(new Color(238, 240, 245));
        graphics.drawString(comparison ? "Armor textures - before / after" : "Ascendance armor - shaded textures and belt", 28, 40);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 15)); graphics.setColor(new Color(170, 182, 197));
        graphics.drawString("Texture-only preview: no added render lighting. Actual exported geometry; not an in-game screenshot.", 28, 66);
        String[] tiers = comparison ? new String[]{"latent", "latent", "ascendant", "ascendant"}
                : new String[]{"latent", "dormant", "awakened", "resonant", "ascendant", "transcendent"};
        double[] depth = new double[width * height]; Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        for (int index = 0; index < tiers.length; index++) {
            int cellX = index % columns * cellWidth, cellY = index / columns * 610 + 90;
            Path root = Path.of(comparison && index % 2 == 0 ? args[2] : args[0]);
            graphics.setColor(new Color(30, 37, 48)); graphics.fillRoundRect(cellX + 12, cellY, cellWidth - 24, 594, 18, 18);
            Map<String, BufferedImage> textures = new HashMap<>();
            for (String atlas : List.of("helmet", "chestplate", "leggings", "boots"))
                textures.put(atlas, ImageIO.read(root.resolve("textures/armor/ascendance/generated/" + atlas + "_" + tiers[index] + ".png").toFile()));
            for (Triangle triangle : load(root)) {
                float[][] vertices = triangle.vertices; BufferedImage texture = textures.get(triangle.atlas);
                double[][] p = new double[3][];
                for (int i = 0; i < 3; i++) p[i] = project(vertices[i], cellX + cellWidth / 2, cellY + 285);
                double det = (p[1][1] - p[2][1]) * (p[0][0] - p[2][0]) + (p[2][0] - p[1][0]) * (p[0][1] - p[2][1]);
                if (Math.abs(det) < 1e-7) continue;
                int x0 = Math.max(0, (int) Math.floor(min(p, 0))), x1 = Math.min(width - 1, (int) Math.ceil(max(p, 0)));
                int y0 = Math.max(0, (int) Math.floor(min(p, 1))), y1 = Math.min(height - 1, (int) Math.ceil(max(p, 1)));
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                    double a = ((p[1][1] - p[2][1]) * (x + .5 - p[2][0]) + (p[2][0] - p[1][0]) * (y + .5 - p[2][1])) / det;
                    double b = ((p[2][1] - p[0][1]) * (x + .5 - p[2][0]) + (p[0][0] - p[2][0]) * (y + .5 - p[2][1])) / det, c = 1 - a - b;
                    if (Math.min(a, Math.min(b, c)) < 0) continue;
                    double z = a * p[0][2] + b * p[1][2] + c * p[2][2]; if (z <= depth[y * width + x] + 1e-9) continue;
                    int u = Math.clamp((int) ((a * vertices[0][3] + b * vertices[1][3] + c * vertices[2][3]) * texture.getWidth()), 0, texture.getWidth() - 1);
                    int v = Math.clamp((int) ((a * vertices[0][4] + b * vertices[1][4] + c * vertices[2][4]) * texture.getHeight()), 0, texture.getHeight() - 1);
                    int pixel = texture.getRGB(u, v); if (pixel >>> 24 < 128) continue;
                    output.setRGB(x, y, pixel); depth[y * width + x] = z;
                }
            }
            graphics.setColor(new Color(237, 239, 244)); graphics.setFont(new Font("SansSerif", Font.BOLD, 20));
            String label = tiers[index].substring(0, 1).toUpperCase() + tiers[index].substring(1);
            if (comparison) label += index % 2 == 0 ? " - before" : " - after";
            graphics.drawString(label, cellX + 28, cellY + 567);
        }
        graphics.dispose(); Path outputPath = Path.of(args[1]); Files.createDirectories(outputPath.toAbsolutePath().getParent());
        ImageIO.write(output, "png", outputPath.toFile());
    }

    private static List<Triangle> load(Path root) throws IOException {
        List<Triangle> triangles = new ArrayList<>();
        for (Part part : PARTS) try (var in = new DataInputStream(Files.newInputStream(root.resolve("meshes/armor/ascendance/" + part.mesh + ".eamesh")))) {
            if (in.readInt() != 0x45414D31) throw new IOException("Expected EAM1");
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                float[][] vertices = new float[3][5];
                for (float[] vertex : vertices) {
                    for (int j = 0; j < 5; j++) vertex[j] = in.readFloat();
                    for (int j = 0; j < 3; j++) in.readFloat();
                    vertex[0] += part.px / 16; vertex[1] += part.py / 16;
                }
                triangles.add(new Triangle(vertices, part.atlas));
            }
        }
        return triangles;
    }

    private static double[] project(float[] p, int xOffset, int yOffset) {
        double x = p[0], y = .48 - p[1], z = p[2], yaw = .30, pitch = .10;
        double c = Math.cos(yaw), s = Math.sin(yaw), d = s * x - c * z;
        return new double[]{xOffset + 245 * (c * x + s * z), yOffset - 245 * (Math.cos(pitch) * y - Math.sin(pitch) * d), Math.sin(pitch) * y + Math.cos(pitch) * d};
    }
    private static double min(double[][] p, int axis) { return Math.min(p[0][axis], Math.min(p[1][axis], p[2][axis])); }
    private static double max(double[][] p, int axis) { return Math.max(p[0][axis], Math.max(p[1][axis], p[2][axis])); }
}
