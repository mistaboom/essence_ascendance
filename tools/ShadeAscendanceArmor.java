import com.google.gson.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline art operation. Repaints RGB only; never run implicitly by the resource build. */
public final class ShadeAscendanceArmor {
    // Source coordinates: X right, Z up, negative Y forward. One upper front-left light.
    private static final Vec LIGHT = new Vec(-.35, -.56, .75).unit();
    private static final int GRID = 24;
    private static final double[][] MATERIAL = new double[GRID][GRID];
    private static double materialMin = 255, materialMax;
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected <input.bbmodel> <material-reference.png> <output-directory>");
        JsonObject project = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        JsonObject originalProject = project.deepCopy();
        readMaterial(ImageIO.read(Path.of(args[1]).toFile()));
        Path output = Path.of(args[2]);
        Files.createDirectories(output);
        Map<String, JsonObject> textures = new LinkedHashMap<>();
        for (var value : project.getAsJsonArray("textures")) {
            JsonObject texture = value.getAsJsonObject();
            textures.put(texture.get("name").getAsString(), texture);
        }
        JsonArray report = new JsonArray();
        for (var value : project.getAsJsonArray("elements")) {
            JsonObject mesh = value.getAsJsonObject();
            String name = mesh.get("name").getAsString();
            JsonObject baseMeta = Objects.requireNonNull(textures.get(name + "_Base"));
            BufferedImage base = decode(baseMeta), accent = textures.containsKey(name + "_Accent") ? decode(textures.get(name + "_Accent")) : null;
            int width = base.getWidth(), height = base.getHeight();
            List<Triangle> triangles = triangles(mesh, width / baseMeta.get("uv_width").getAsDouble(), height / baseMeta.get("uv_height").getAsDouble());
            List<Patch> patches = patches(triangles);
            double[] baseField = new double[width * height], accentField = new double[width * height];
            int[] samples = new int[width * height];
            for (Patch patch : patches) for (Triangle triangle : patch.triangles) {
                double[] u = triangle.u, v = triangle.v;
                double denominator = (v[1] - v[2]) * (u[0] - u[2]) + (u[2] - u[1]) * (v[0] - v[2]);
                if (Math.abs(denominator) < 1e-9) continue;
                int x0 = Math.max(0, (int) Math.floor(min(u))), x1 = Math.min(width, (int) Math.ceil(max(u)));
                int y0 = Math.max(0, (int) Math.floor(min(v))), y1 = Math.min(height, (int) Math.ceil(max(v)));
                double diffuse = .5 + .5 * triangle.normal.dot(LIGHT);
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    if (base.getRGB(x, y) >>> 24 == 0 && (accent == null || accent.getRGB(x, y) >>> 24 == 0)) continue;
                    double a = ((v[1] - v[2]) * (x + .5 - u[2]) + (u[2] - u[1]) * (y + .5 - v[2])) / denominator;
                    double b = ((v[2] - v[0]) * (x + .5 - u[2]) + (u[0] - u[2]) * (y + .5 - v[2])) / denominator;
                    double c = 1 - a - b;
                    if (Math.min(a, Math.min(b, c)) < -1e-7) continue;
                    Vec point = triangle.p[0].times(a).plus(triangle.p[1].times(b)).plus(triangle.p[2].times(c));
                    double progress = Math.clamp((point.dot(LIGHT) - patch.minimum) / Math.max(.001, patch.maximum - patch.minimum), 0, 1);
                    double material = sampleMaterial(1 - progress);
                    double edge = edgeLight(point, patch.edges);
                    double shadow = diffuse > .5 ? shadow(point, triangle.normal, triangles) : 0;
                    int index = y * width + x;
                    // A broad tonal range and real panel-edge relief remain visible under flat/unlit rendering.
                    baseField[index] += 180 + 65 * diffuse + 56 * (material - .5) + 24 * edge - 28 * shadow;
                    accentField[index] += 196 + 51 * diffuse + 50 * (material - .5) + 22 * edge - 24 * shadow;
                    samples[index]++;
                }
            }
            for (int i = 0; i < samples.length; i++) if (samples[i] > 0) {
                baseField[i] /= samples[i]; accentField[i] /= samples[i];
            }
            for (String suffix : accent == null ? List.of("_Base") : List.of("_Base", "_Accent")) {
                String textureName = name + suffix;
                BufferedImage input = suffix.equals("_Base") ? base : accent;
                double[] field = suffix.equals("_Base") ? baseField : accentField;
                BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                byte[] alpha = new byte[width * height];
                int count = 0, changed = 0, darkest = 255, brightest = 0;
                double sum = 0, squares = 0;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int index = y * width + x, old = input.getRGB(x, y);
                    alpha[index] = (byte) (old >>> 24);
                    if (old >>> 24 == 0) { painted.setRGB(x, y, old); continue; }
                    int nearest = index;
                    if (samples[index] == 0) {
                        double distance = Double.POSITIVE_INFINITY;
                        for (int yy = Math.max(0, y - 8); yy < Math.min(height, y + 9); yy++)
                            for (int xx = Math.max(0, x - 8); xx < Math.min(width, x + 9); xx++) {
                                double d = (xx - x) * (xx - x) + (yy - y) * (yy - y);
                                if (samples[yy * width + xx] > 0 && d < distance) { distance = d; nearest = yy * width + xx; }
                            }
                    }
                    int gray = (int) Math.round(Math.clamp(samples[nearest] == 0 ? 225 : field[nearest], 135, 255));
                    int pixel = (old & 0xff000000) | gray << 16 | gray << 8 | gray;
                    painted.setRGB(x, y, pixel);
                    if (pixel != old) changed++;
                    count++; sum += gray; squares += gray * gray;
                    darkest = Math.min(darkest, gray); brightest = Math.max(brightest, gray);
                }
                // Check every pixel, including RGB hidden under transparency, before writing.
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int old = input.getRGB(x, y), next = painted.getRGB(x, y);
                    if ((old >>> 24) != (next >>> 24) || (old >>> 24 == 0 && old != next)) throw new IllegalStateException("Changed mask coverage: " + textureName);
                }
                ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(painted, "png", png);
                Files.write(output.resolve(textureName.toLowerCase(Locale.ROOT) + ".png"), png.toByteArray());
                textures.get(textureName).addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
                JsonObject stats = new JsonObject();
                stats.addProperty("texture", textureName); stats.addProperty("painted_pixels", count); stats.addProperty("recolored_pixels", changed);
                stats.addProperty("gray_min", darkest); stats.addProperty("gray_max", brightest); stats.addProperty("gray_mean", sum / count);
                stats.addProperty("gray_standard_deviation", Math.sqrt(squares / count - Math.pow(sum / count, 2)));
                stats.addProperty("alpha_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(alpha)));
                stats.addProperty("alpha_and_hidden_rgb_unchanged", true); report.add(stats);
                System.out.printf(Locale.ROOT, "%s: %d painted pixels, gray %d..%d, mean %.1f, deviation %.1f; alpha unchanged%n", textureName, count, darkest, brightest, sum / count, stats.get("gray_standard_deviation").getAsDouble());
            }
        }
        JsonObject comparison = project.deepCopy();
        for (int i = 0; i < comparison.getAsJsonArray("textures").size(); i++) comparison.getAsJsonArray("textures").get(i).getAsJsonObject()
                .add("source", originalProject.getAsJsonArray("textures").get(i).getAsJsonObject().get("source"));
        if (!comparison.equals(originalProject)) throw new IllegalStateException("Non-texture Blockbench data changed");
        Files.writeString(output.resolve("ascendance_armor.bbmodel"), JSON.toJson(project));
        Files.writeString(output.resolve("shading-verification.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private static List<Triangle> triangles(JsonObject mesh, double sx, double sy) {
        List<Triangle> result = new ArrayList<>();
        for (var entry : mesh.getAsJsonObject("faces").entrySet()) {
            JsonObject face = entry.getValue().getAsJsonObject();
            JsonArray ids = face.getAsJsonArray("vertices");
            if (ids.size() != 3) throw new IllegalArgumentException("Expected authored triangles");
            Vec[] p = new Vec[3]; double[] u = new double[3], v = new double[3];
            for (int i = 0; i < 3; i++) {
                String id = ids.get(i).getAsString(); JsonArray vertex = mesh.getAsJsonObject("vertices").getAsJsonArray(id), uv = face.getAsJsonObject("uv").getAsJsonArray(id);
                p[i] = new Vec(vertex.get(0).getAsDouble(), vertex.get(1).getAsDouble(), vertex.get(2).getAsDouble());
                u[i] = uv.get(0).getAsDouble() * sx; v[i] = uv.get(1).getAsDouble() * sy;
            }
            result.add(new Triangle(p, u, v, p[1].minus(p[0]).cross(p[2].minus(p[0])).unit()));
        }
        return result;
    }

    /** Merge adjacent coplanar triangles so triangulation diagonals never become painted seams. */
    private static List<Patch> patches(List<Triangle> triangles) {
        int[] parent = new int[triangles.size()]; for (int i = 0; i < parent.length; i++) parent[i] = i;
        Map<String, List<Integer>> adjacent = new LinkedHashMap<>();
        for (int i = 0; i < triangles.size(); i++) for (int j = 0; j < 3; j++) adjacent.computeIfAbsent(edgeKey(triangles.get(i).p[j], triangles.get(i).p[(j + 1) % 3]), k -> new ArrayList<>()).add(i);
        for (List<Integer> indices : adjacent.values()) for (int a : indices) for (int b : indices)
            if (triangles.get(a).normal.dot(triangles.get(b).normal) > .99999) parent[find(parent, a)] = find(parent, b);
        Map<Integer, List<Triangle>> groups = new LinkedHashMap<>();
        for (int i = 0; i < triangles.size(); i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(triangles.get(i));
        List<Patch> result = new ArrayList<>();
        for (List<Triangle> group : groups.values()) {
            Map<String, List<Edge>> edges = new LinkedHashMap<>(); double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
            for (Triangle t : group) for (int i = 0; i < 3; i++) {
                Vec a = t.p[i], b = t.p[(i + 1) % 3], opposite = t.p[(i + 2) % 3];
                Vec axis = b.minus(a).unit(), toward = a.plus(b).times(.5).minus(opposite);
                Vec outward = toward.minus(axis.times(toward.dot(axis))).unit();
                edges.computeIfAbsent(edgeKey(a, b), k -> new ArrayList<>()).add(new Edge(a, b, outward));
                minimum = Math.min(minimum, a.dot(LIGHT)); maximum = Math.max(maximum, a.dot(LIGHT));
            }
            result.add(new Patch(group, edges.values().stream().filter(e -> e.size() == 1).map(e -> e.get(0)).toList(), minimum, maximum));
        }
        return result;
    }

    private static double edgeLight(Vec p, List<Edge> edges) {
        double closest = Double.POSITIVE_INFINITY, lighting = 0;
        for (Edge edge : edges) {
            Vec ab = edge.b.minus(edge.a);
            double t = Math.clamp(p.minus(edge.a).dot(ab) / ab.dot(ab), 0, 1);
            double distance = p.minus(edge.a.plus(ab.times(t))).length();
            if (distance < closest) { closest = distance; lighting = edge.outward.dot(LIGHT); }
        }
        // Painted bevel is confined to a fraction of one model pixel, not every UV/triangle edge.
        return lighting * Math.pow(Math.max(0, 1 - closest / .48), 1.5);
    }

    private static double shadow(Vec p, Vec normal, List<Triangle> triangles) {
        double total = 0;
        for (Vec offset : List.of(new Vec(0, 0, 0), new Vec(.12, 0, .06), new Vec(-.12, 0, -.06), new Vec(0, .12, .06), new Vec(0, -.12, -.06))) {
            Vec direction = LIGHT.plus(offset).unit(), origin = p.plus(normal.times(.012)).plus(direction.times(.012));
            for (Triangle t : triangles) {
                Vec e1 = t.p[1].minus(t.p[0]), e2 = t.p[2].minus(t.p[0]), h = direction.cross(e2);
                double determinant = e1.dot(h); if (Math.abs(determinant) < 1e-9) continue;
                Vec s = origin.minus(t.p[0]); double u = s.dot(h) / determinant; if (u < 0 || u > 1) continue;
                Vec q = s.cross(e1); double v = direction.dot(q) / determinant; if (v < 0 || u + v > 1) continue;
                double distance = e2.dot(q) / determinant;
                if (distance > .015 && distance < 3) { total++; break; }
            }
        }
        return total / 5;
    }

    private static void readMaterial(BufferedImage image) {
        for (int gy = 0; gy < GRID; gy++) for (int gx = 0; gx < GRID; gx++) {
            long sum = 0, count = 0;
            for (int y = gy * image.getHeight() / GRID; y < (gy + 1) * image.getHeight() / GRID; y++)
                for (int x = gx * image.getWidth() / GRID; x < (gx + 1) * image.getWidth() / GRID; x++) {
                    int color = image.getRGB(x, y); sum += ((color >>> 16) & 255) * 54 + ((color >>> 8) & 255) * 183 + (color & 255) * 19; count++;
                }
            MATERIAL[gy][gx] = (double) sum / count / 256;
            materialMin = Math.min(materialMin, MATERIAL[gy][gx]); materialMax = Math.max(materialMax, MATERIAL[gy][gx]);
        }
    }

    private static double sampleMaterial(double progress) {
        double coordinate = Math.clamp(progress, 0, 1) * (GRID - 1); int a = (int) coordinate, b = Math.min(GRID - 1, a + 1); double f = coordinate - a;
        double value = MATERIAL[a][a] * (1 - f) * (1 - f) + (MATERIAL[a][b] + MATERIAL[b][a]) * f * (1 - f) + MATERIAL[b][b] * f * f;
        return (value - materialMin) / Math.max(1, materialMax - materialMin);
    }

    private static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }
    private static String edgeKey(Vec a, Vec b) { String x = a.key(), y = b.key(); return x.compareTo(y) < 0 ? x + "/" + y : y + "/" + x; }
    private static double min(double[] a) { return Math.min(a[0], Math.min(a[1], a[2])); }
    private static double max(double[] a) { return Math.max(a[0], Math.max(a[1], a[2])); }
    private static BufferedImage decode(JsonObject texture) throws IOException { String source = texture.get("source").getAsString(); return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1)))); }
    private record Triangle(Vec[] p, double[] u, double[] v, Vec normal) { }
    private record Edge(Vec a, Vec b, Vec outward) { }
    private record Patch(List<Triangle> triangles, List<Edge> edges, double minimum, double maximum) { }
    private record Vec(double x, double y, double z) {
        Vec plus(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
        Vec minus(Vec v) { return new Vec(x - v.x, y - v.y, z - v.z); }
        Vec times(double f) { return new Vec(x * f, y * f, z * f); }
        double dot(Vec v) { return x * v.x + y * v.y + z * v.z; }
        Vec cross(Vec v) { return new Vec(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x); }
        double length() { return Math.sqrt(dot(this)); }
        Vec unit() { double l = length(); if (l < 1e-9) throw new IllegalArgumentException("Degenerate geometry"); return times(1 / l); }
        String key() { return Math.round(x * 1e6) + "," + Math.round(y * 1e6) + "," + Math.round(z * 1e6); }
    }
}
