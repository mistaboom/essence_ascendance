import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports flat, triangulated Blockbench meshes to the runtime EAM1 format.
 *
 * <p>All coordinate conventions live in the asset manifest. {@code axes} is a
 * signed permutation such as {@code ["x", "z", "-y"]}; {@code offset} is added
 * afterward in model pixels, then coordinates are divided by
 * {@code unitsPerBlock} (16 by default). {@code sourceOrigin} and
 * {@code sourceRotation} assert the authoring frame represented by that mapping.
 * A changed frame fails the build instead of silently moving the exported mesh.
 * Mesh vertices are already expressed in that frame, so the editor transform
 * must not also be applied a second time.</p>
 *
 * <p>Winding is preserved unless {@code outwardCenter} specifies a point in
 * output block coordinates. That option is for convex meshes only. This
 * exporter intentionally rejects cubes, polygons, groups, animation, multiple
 * materials, and smooth shading until their semantics are implemented.</p>
 */
public final class BlockbenchMeshExporter {
    private static final int MAGIC = 0x45414D31;
    private static final Set<String> OPTIONS = Set.of(
            "axes", "offset", "unitsPerBlock", "sourceOrigin", "sourceRotation", "outwardCenter");

    private BlockbenchMeshExporter() {
    }

    /** Generate one complete mesh, validating every input before replacing its output. */
    public static void export(JsonObject project, JsonObject options, Path output) throws IOException {
        byte[] bytes = generate(project, options);
        if (Files.isRegularFile(output) && Arrays.equals(bytes, Files.readAllBytes(output))) {
            return;
        }
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, bytes);
    }

    /** Generate bytes without touching the filesystem, also useful for reproducibility checks. */
    public static byte[] generate(JsonObject project, JsonObject options) throws IOException {
        for (String key : options.keySet()) {
            require(OPTIONS.contains(key), "Unknown mesh option: " + key);
        }
        Mapping mapping = new Mapping(options);
        require(project.getAsJsonObject("meta").get("model_format").getAsString().equals("free"),
                "Static meshes require the Blockbench Generic Model format");
        require(!project.has("animations") || project.getAsJsonArray("animations").isEmpty(),
                "Animated models require an animation exporter");
        JsonArray textures = project.getAsJsonArray("textures");
        require(textures != null && textures.size() == 1, "EAM1 static meshes require exactly one texture");
        JsonObject resolution = project.getAsJsonObject("resolution");
        double width = finite(resolution.get("width"), "texture width");
        double height = finite(resolution.get("height"), "texture height");
        require(width > 0 && height > 0, "Texture dimensions must be positive");

        JsonArray elements = project.getAsJsonArray("elements");
        require(elements != null && !elements.isEmpty(), "Mesh has no elements");
        Set<String> elementIds = new HashSet<>();
        List<double[]> triangles = new ArrayList<>();
        for (JsonElement entry : elements) {
            JsonObject element = entry.getAsJsonObject();
            String name = element.get("name").getAsString();
            String id = element.get("uuid").getAsString();
            require(elementIds.add(id), "Duplicate mesh element UUID: " + id);
            require(element.get("type").getAsString().equals("mesh"), "Unsupported element type: " + name);
            require(!element.has("shading") || element.get("shading").getAsString().equals("flat"),
                    "Smooth mesh shading is unsupported: " + name);
            requireFrame(element, "origin", mapping.sourceOrigin, name);
            requireFrame(element, "rotation", mapping.sourceRotation, name);
            if (element.has("export") && !element.get("export").getAsBoolean()) {
                continue;
            }
            JsonObject vertices = element.getAsJsonObject("vertices");
            JsonObject faces = element.getAsJsonObject("faces");
            require(vertices != null && faces != null, "Missing vertices/faces: " + name);
            for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                String faceName = name + "/" + faceEntry.getKey();
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                JsonArray ids = face.getAsJsonArray("vertices");
                require(ids != null && ids.size() == 3,
                        "Triangulate mesh faces in Blockbench before exporting: " + faceName);
                require(face.has("texture") && !face.get("texture").isJsonNull()
                                && face.get("texture").isJsonPrimitive()
                                && face.getAsJsonPrimitive("texture").isNumber()
                                && face.get("texture").getAsDouble() == 0,
                        "Mesh face must use its sole texture: " + faceName);
                JsonObject uv = face.getAsJsonObject("uv");
                require(uv != null, "Missing UV coordinates: " + faceName);
                double[][] points = new double[3][];
                double[][] coordinates = new double[3][];
                for (int i = 0; i < 3; i++) {
                    String vertex = ids.get(i).getAsString();
                    require(vertices.has(vertex), "Unknown vertex in " + faceName + ": " + vertex);
                    require(uv.has(vertex), "Missing vertex UV in " + faceName + ": " + vertex);
                    points[i] = mapping.apply(vector(vertices.get(vertex), 3, faceName + " vertex"));
                    coordinates[i] = vector(uv.get(vertex), 2, faceName + " UV");
                }
                double[] normal = cross(subtract(points[1], points[0]), subtract(points[2], points[0]));
                double length = Math.sqrt(dot(normal, normal));
                require(length > 0 && Double.isFinite(length), "Degenerate triangle: " + faceName);
                if (mapping.outwardCenter != null) {
                    double[] midpoint = new double[3];
                    for (int i = 0; i < 3; i++) {
                        midpoint[i] = (points[0][i] + points[1][i] + points[2][i]) / 3.0;
                    }
                    double direction = dot(normal, subtract(midpoint, mapping.outwardCenter));
                    require(direction != 0, "Outward center lies on a face plane: " + faceName);
                    if (direction < 0) {
                        swap(points);
                        swap(coordinates);
                        for (int i = 0; i < 3; i++) {
                            normal[i] = -normal[i];
                        }
                    }
                }
                double[] triangle = new double[24];
                for (int i = 0; i < 3; i++) {
                    int start = i * 8;
                    System.arraycopy(points[i], 0, triangle, start, 3);
                    triangle[start + 3] = coordinates[i][0] / width;
                    triangle[start + 4] = coordinates[i][1] / height;
                    for (int axis = 0; axis < 3; axis++) {
                        triangle[start + 5 + axis] = normal[axis] / length;
                    }
                }
                triangles.add(triangle);
            }
        }
        validateOutliner(project.getAsJsonArray("outliner"), elementIds);
        require(!triangles.isEmpty() && triangles.size() <= 100_000, "Invalid EAM1 triangle count");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + triangles.size() * 96);
        try (DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeInt(MAGIC);
            data.writeInt(triangles.size());
            for (double[] triangle : triangles) {
                for (double value : triangle) {
                    float packed = (float) value;
                    require(Float.isFinite(packed), "Mesh coordinate exceeds EAM1 float range");
                    data.writeFloat(packed);
                }
            }
        }
        return bytes.toByteArray();
    }

    private static void validateOutliner(JsonArray outliner, Set<String> elements) {
        require(outliner != null, "Missing mesh outliner");
        Set<String> references = new HashSet<>();
        for (JsonElement entry : outliner) {
            require(entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString(),
                    "Mesh groups/transforms are unsupported; flatten the outliner before exporting");
            String id = entry.getAsString();
            require(elements.contains(id) && references.add(id), "Invalid mesh outliner reference: " + id);
        }
        require(references.equals(elements), "Mesh outliner must reference each element exactly once");
    }

    private static void requireFrame(JsonObject element, String property, double[] expected, String name) {
        double[] actual = element.has(property) ? vector(element.get(property), 3, property) : new double[3];
        require(Arrays.equals(actual, expected), "Changed authoring " + property + " for " + name
                + ": update the manifest coordinate mapping before exporting");
    }

    private static double[] vector(JsonElement value, int size, String name) {
        require(value != null && value.isJsonArray() && value.getAsJsonArray().size() == size,
                "Expected " + size + " components for " + name);
        double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            result[i] = finite(value.getAsJsonArray().get(i), name);
        }
        return result;
    }

    private static double finite(JsonElement value, String name) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                "Expected a number for " + name);
        double result = value.getAsDouble();
        require(Double.isFinite(result), "Non-finite " + name);
        return result;
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void swap(double[][] values) {
        double[] temporary = values[1];
        values[1] = values[2];
        values[2] = temporary;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static final class Mapping {
        final int[] axes = new int[3];
        final double[] signs = new double[3];
        final double[] offset;
        final double unitsPerBlock;
        final double[] sourceOrigin;
        final double[] sourceRotation;
        final double[] outwardCenter;

        Mapping(JsonObject options) {
            JsonArray configuredAxes = options.getAsJsonArray("axes");
            require(configuredAxes != null && configuredAxes.size() == 3, "Mesh axes must contain three axes");
            Set<Integer> unique = new HashSet<>();
            for (int i = 0; i < 3; i++) {
                String axis = configuredAxes.get(i).getAsString();
                signs[i] = axis.startsWith("-") ? -1 : 1;
                String unsigned = signs[i] < 0 ? axis.substring(1) : axis;
                axes[i] = switch (unsigned) {
                    case "x" -> 0;
                    case "y" -> 1;
                    case "z" -> 2;
                    default -> throw new IllegalArgumentException("Unknown mesh axis: " + axis);
                };
                require(unique.add(axes[i]), "Mesh axes must use each source axis exactly once");
            }
            offset = options.has("offset") ? vector(options.get("offset"), 3, "offset") : new double[3];
            unitsPerBlock = options.has("unitsPerBlock") ? finite(options.get("unitsPerBlock"), "unitsPerBlock") : 16;
            require(unitsPerBlock > 0, "unitsPerBlock must be positive");
            sourceOrigin = vector(options.get("sourceOrigin"), 3, "sourceOrigin");
            sourceRotation = vector(options.get("sourceRotation"), 3, "sourceRotation");
            outwardCenter = options.has("outwardCenter")
                    ? vector(options.get("outwardCenter"), 3, "outwardCenter") : null;
        }

        double[] apply(double[] vertex) {
            double[] result = new double[3];
            for (int i = 0; i < 3; i++) {
                result[i] = (signs[i] * vertex[axes[i]] + offset[i]) / unitsPerBlock;
            }
            return result;
        }
    }

    /** Standalone entry point; normal builds invoke the shared asset pipeline. */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: BlockbenchMeshExporter <source.bbmodel> <options.json> <output.eamesh>");
        }
        export(JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject(),
                JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject(), Path.of(args[2]));
    }
}
