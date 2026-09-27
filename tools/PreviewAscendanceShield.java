import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Offline preview of the actual generated shield mesh and tier atlases, without added lighting. */
public final class PreviewAscendanceShield {
    private static final String[] TIERS = {"latent", "dormant", "awakened", "resonant", "ascendant", "transcendent"};

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Expected <asset-namespace-directory> <preview.png>");
        Path root = Path.of(args[0]);
        List<float[][]> triangles = load(root.resolve("meshes/item/ascendance_shield.eamesh"));
        int width = 1290, height = 990;
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(22, 27, 35)); g.fillRect(0, 0, width, height);
        g.setColor(new Color(238, 240, 245)); g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.drawString("Ascendance shield — six tier materials", 28, 42);
        g.setColor(new Color(170, 182, 197)); g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("Actual exported mesh and textures. Front / rear inset. Texture shading only; runtime emission is not shown.", 28, 69);
        for (int tier = 0; tier < TIERS.length; tier++) {
            int left = tier % 3 * 430 + 12, top = tier / 3 * 435 + 90;
            g.setColor(new Color(30, 37, 48)); g.fillRoundRect(left, top, 406, 417, 18, 18);
            BufferedImage texture = ImageIO.read(root.resolve("textures/item/ascendance_shield/" + TIERS[tier] + ".png").toFile());
            draw(output, triangles, texture, left + 189, top + 165, 315, .22, .12);
            draw(output, triangles, texture, left + 332, top + 317, 105, Math.PI + .48, -.20);
            g.setColor(new Color(237, 239, 244)); g.setFont(new Font("SansSerif", Font.BOLD, 20));
            String label = TIERS[tier].substring(0, 1).toUpperCase(Locale.ROOT) + TIERS[tier].substring(1);
            g.drawString(label, left + 22, top + 389);
        }
        g.dispose();
        Path destination = Path.of(args[1]);
        Files.createDirectories(destination.toAbsolutePath().getParent());
        ImageIO.write(output, "png", destination.toFile());
    }

    private static List<float[][]> load(Path path) throws IOException {
        List<float[][]> result = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != 0x45414D31) throw new IOException("Expected EAM1");
            int count = in.readInt();
            for (int triangle = 0; triangle < count; triangle++) {
                float[][] vertices = new float[3][8];
                for (float[] vertex : vertices) for (int i = 0; i < vertex.length; i++) vertex[i] = in.readFloat();
                result.add(vertices);
            }
        }
        return result;
    }

    private static void draw(BufferedImage output, List<float[][]> triangles, BufferedImage texture,
                             int centerX, int centerY, double scale, double yaw, double pitch) {
        int width = output.getWidth(), height = output.getHeight();
        double[] depth = new double[width * height]; Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        for (float[][] vertices : triangles) {
            double[][] p = new double[3][];
            for (int i = 0; i < 3; i++) {
                float[] v = vertices[i];
                double x = Math.cos(yaw) * v[0] + Math.sin(yaw) * v[2];
                double d = Math.sin(yaw) * v[0] - Math.cos(yaw) * v[2];
                double y = -Math.cos(pitch) * v[1] - Math.sin(pitch) * d;
                double z = -Math.sin(pitch) * v[1] + Math.cos(pitch) * d;
                p[i] = new double[]{centerX + scale * x, centerY - scale * y, z};
            }
            double determinant = (p[1][1] - p[2][1]) * (p[0][0] - p[2][0])
                    + (p[2][0] - p[1][0]) * (p[0][1] - p[2][1]);
            if (Math.abs(determinant) < 1e-8) continue;
            int x0 = Math.max(0, (int) Math.floor(min(p, 0))), x1 = Math.min(width - 1, (int) Math.ceil(max(p, 0)));
            int y0 = Math.max(0, (int) Math.floor(min(p, 1))), y1 = Math.min(height - 1, (int) Math.ceil(max(p, 1)));
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                double a = ((p[1][1] - p[2][1]) * (x + .5 - p[2][0]) + (p[2][0] - p[1][0]) * (y + .5 - p[2][1])) / determinant;
                double b = ((p[2][1] - p[0][1]) * (x + .5 - p[2][0]) + (p[0][0] - p[2][0]) * (y + .5 - p[2][1])) / determinant;
                double c = 1 - a - b;
                if (Math.min(a, Math.min(b, c)) < 0) continue;
                double z = a * p[0][2] + b * p[1][2] + c * p[2][2];
                if (z <= depth[y * width + x] + 1e-9) continue;
                int u = Math.clamp((int) ((a * vertices[0][3] + b * vertices[1][3] + c * vertices[2][3]) * texture.getWidth()), 0, texture.getWidth() - 1);
                int v = Math.clamp((int) ((a * vertices[0][4] + b * vertices[1][4] + c * vertices[2][4]) * texture.getHeight()), 0, texture.getHeight() - 1);
                int pixel = texture.getRGB(u, v);
                if (pixel >>> 24 < 128) continue;
                output.setRGB(x, y, pixel); depth[y * width + x] = z;
            }
        }
    }

    private static double min(double[][] p, int axis) { return Math.min(p[0][axis], Math.min(p[1][axis], p[2][axis])); }
    private static double max(double[][] p, int axis) { return Math.max(p[0][axis], Math.max(p[1][axis], p[2][axis])); }
}
