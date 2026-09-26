package com.mistaboom.essence_ascendance.client.armor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.TexturePixels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Checks authored placement, animated bone attachment and all baked atlas pixels without a GL context. */
public final class AscendanceArmorTest {
    private static final String ROOT = "/assets/essence_ascendance/";
    private static final List<String> BONES = List.of("head", "hat", "body", "left_arm", "right_arm", "left_leg", "right_leg");
    private static final List<Piece> PIECES = List.of(
            new Piece("Head", "head", EquipmentSlot.HEAD, "head", "helmet", 256, 64, 0, 256, 256, 0, 0, true),
            new Piece("Chest", "chest", EquipmentSlot.CHEST, "body", "chestplate", 256, 64, 0, 512, 256, 0, 0, true),
            new Piece("Left_Arm", "left_arm", EquipmentSlot.CHEST, "left_arm", "chestplate", 128, 32, 256, 512, 256, 5, 2, true),
            new Piece("Right_Arm", "right_arm", EquipmentSlot.CHEST, "right_arm", "chestplate", 128, 32, 384, 512, 256, -5, 2, true),
            new Piece("Left_Leg", "left_leg", EquipmentSlot.LEGS, "left_leg", "leggings", 128, 32, 0, 384, 128, 1.9f, 12, true),
            new Piece("Right_Leg", "right_leg", EquipmentSlot.LEGS, "right_leg", "leggings", 128, 32, 128, 384, 128, -1.9f, 12, true),
            new Piece("Belt", "belt", EquipmentSlot.LEGS, "body", "leggings", 128, 32, 256, 384, 128, 0, 0, false),
            new Piece("Left_Foot", "left_foot", EquipmentSlot.FEET, "left_leg", "boots", 128, 32, 0, 256, 128, 1.9f, 12, true),
            new Piece("Right_Foot", "right_foot", EquipmentSlot.FEET, "right_leg", "boots", 128, 32, 128, 256, 128, -1.9f, 12, true));

    public static void main(String[] args) throws Exception {
        check(args.length == 2, "Expected the authored Armor.bbmodel path and source PNG directory");
        var source = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        Path sourceMasks = Path.of(args[1]);
        Map<String, JsonObject> elements = namedEntries(source, "elements");
        Map<String, JsonObject> textureEntries = namedEntries(source, "textures");
        Map<String, BufferedImage> textures = new LinkedHashMap<>();
        for (Piece piece : PIECES) for (String suffix : piece.hasAccent ? List.of("_Base", "_Accent") : List.of("_Base")) {
            String name = piece.sourceName + suffix;
            JsonObject texture = textureEntries.get(name);
            check(texture != null, "Missing artist texture " + name);
            // PNGs are the editable material authority; Blockbench supplies mesh/UV metadata only.
            Path png = sourceMasks.resolve(piece.resource + suffix.toLowerCase(java.util.Locale.ROOT) + ".png");
            check(Files.isRegularFile(png), "Missing editable source mask " + png);
            BufferedImage image = ImageIO.read(png.toFile());
            check(image != null && image.getWidth() == piece.textureSize && image.getHeight() == piece.textureSize,
                    "Preserve authored resolution: " + name);
            check(texture.get("uv_width").getAsInt() == piece.uvSize
                    && texture.get("uv_height").getAsInt() == piece.uvSize, "Unexpected source UV size: " + name);
            textures.put(name, image);
        }
        check(textures.size() == 17, "Armor uses 17 source masks, including the belt's base-only mask");
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            var model = new AscendanceArmorModel<LivingEntity>(slot);
            model.young = false;
            model.setAllVisible(false);
            int triangleCount = 0;
            for (Piece piece : PIECES) if (piece.slot == slot) {
                checkMeshAndAnimation(model, piece, elements.get(piece.sourceName));
                triangleCount += elements.get(piece.sourceName).getAsJsonObject("faces").size();
            }
            // A slot's other humanoid bones must remain empty even when a renderer makes them visible.
            for (String name : BONES) {
                if (PIECES.stream().anyMatch(piece -> piece.slot == slot && piece.bone.equals(name))) continue;
                bone(model, name).visible = true;
                check(render(model).vertices.isEmpty(), slot + " must not contain geometry on " + name);
                bone(model, name).visible = false;
            }
            model.setAllVisible(true);
            check(render(model).vertices.size() == triangleCount * 4, slot + " must draw its own geometry exactly once");
            checkTextures(slot, textures);
        }
        checkBeltGaitIndependence();
        System.out.println("AscendanceArmorTest: nine EAM1 meshes, independent belt/leg animation, slot isolation, UVs and 24 tier atlases from 17 source masks PASS");
    }

    private static void checkMeshAndAnimation(AscendanceArmorModel<LivingEntity> model, Piece piece,
                                             JsonObject source) throws Exception {
        check(source != null, "Missing source mesh " + piece.sourceName);
        ModelPart bone = bone(model, piece.bone);
        close(bone.x, piece.pivotX, piece.resource + " pivot X");
        close(bone.y, piece.pivotY, piece.resource + " pivot Y");
        close(bone.z, 0, piece.resource + " pivot Z");
        bone.visible = true;
        RecordingBuffer resting = render(model);
        JsonObject faces = source.getAsJsonObject("faces");
        check(resting.vertices.size() == faces.size() * 4, piece.resource + " must emit one degenerate quad per source triangle");
        try (var input = new DataInputStream(resource("meshes/armor/ascendance/" + piece.resource + ".eamesh"))) {
            check(input.readInt() == 0x45414D31, piece.resource + " must declare EAM1 mesh format");
            check(input.readInt() == faces.size(), piece.resource + " must preserve every source face");
            int faceIndex = 0;
            for (var entry : faces.entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                var ids = face.getAsJsonArray("vertices");
                check(ids.size() == 3, "Authored faces must be triangles");
                int corner = 0;
                for (var id : ids) {
                    var position = source.getAsJsonObject("vertices").getAsJsonArray(id.getAsString());
                    var uv = face.getAsJsonObject("uv").getAsJsonArray(id.getAsString());
                    float[] actual = resting.vertices.get(faceIndex * 4 + corner++);
                    // This independently restores Blockbench's authored standing pose, including its forward direction.
                    float[] expected = {position.get(0).getAsFloat() / 16,
                            (24 - position.get(2).getAsFloat()) / 16, position.get(1).getAsFloat() / 16,
                            (uv.get(0).getAsFloat() / piece.uvSize * piece.textureSize + piece.atlasX) / piece.atlasWidth,
                            uv.get(1).getAsFloat() / piece.uvSize * piece.textureSize / piece.atlasHeight};
                    for (int axis = 0; axis < 5; axis++) {
                        close(actual[axis], expected[axis], piece.resource + " source position/UV " + faceIndex + ":" + axis);
                        float local = input.readFloat();
                        float pivot = axis == 0 ? piece.pivotX / 16 : axis == 1 ? piece.pivotY / 16 : 0;
                        close(local + pivot, expected[axis], piece.resource + " imported position/UV " + faceIndex + ":" + axis);
                    }
                    for (int axis = 5; axis < 8; axis++) {
                        close(actual[axis], input.readFloat(), piece.resource + " imported normal " + faceIndex + ":" + axis);
                    }
                }
                float[] third = resting.vertices.get(faceIndex * 4 + 2);
                float[] fourth = resting.vertices.get(faceIndex * 4 + 3);
                for (int axis = 0; axis < third.length; axis++) close(third[axis], fourth[axis], "Repeated triangle corner");
                checkNormal(resting.vertices, faceIndex * 4, piece.resource);
                faceIndex++;
            }
            check(input.read() == -1, piece.resource + " has unexpected trailing geometry");
        }

        // Head yaw and limb pitch exercise the real ModelPart transform, including translated shoulder/hip pivots.
        boolean head = piece.slot == EquipmentSlot.HEAD;
        if (head) bone.yRot = (float) (Math.PI / 2); else bone.xRot = (float) (Math.PI / 2);
        bone.y += 3;
        bone.z -= 2;
        RecordingBuffer animated = render(model);
        check(animated.vertices.size() == resting.vertices.size(), "Animation must not add or drop geometry");
        for (int i = 0; i < resting.vertices.size(); i++) {
            float[] original = resting.vertices.get(i), actual = animated.vertices.get(i);
            float localX = original[0] - piece.pivotX / 16;
            float localY = original[1] - piece.pivotY / 16;
            float localZ = original[2];
            close(actual[0], piece.pivotX / 16 + (head ? localZ : localX), piece.resource + " animated X");
            close(actual[1], (piece.pivotY + 3) / 16 + (head ? localY : -localZ), piece.resource + " animated Y");
            close(actual[2], -2f / 16 + (head ? -localX : localY), piece.resource + " animated Z");
            close(actual[3], original[3], "Animation must preserve atlas U");
            close(actual[4], original[4], "Animation must preserve atlas V");
            close(actual[5], head ? original[7] : original[5], piece.resource + " animated normal X");
            close(actual[6], head ? original[6] : -original[7], piece.resource + " animated normal Y");
            close(actual[7], head ? -original[5] : original[6], piece.resource + " animated normal Z");
        }
        bone.xRot = bone.yRot = 0;
        bone.y = piece.pivotY;
        bone.z = 0;
        bone.visible = false;
        check(render(model).vertices.isEmpty(), piece.resource + " must obey bone visibility");
    }

    private static void checkTextures(EquipmentSlot slot, Map<String, BufferedImage> textures) throws Exception {
        List<Piece> pieces = PIECES.stream().filter(piece -> piece.slot == slot).toList();
        Piece atlas = pieces.getFirst();
        for (EquipmentTier tier : EquipmentTier.values()) {
            String name = atlas.atlas + "_" + tier.serializedName();
            BufferedImage image;
            try (InputStream input = resource("textures/armor/ascendance/generated/" + name + ".png")) {
                image = ImageIO.read(input);
            }
            check(image != null && image.getWidth() == atlas.atlasWidth && image.getHeight() == atlas.atlasHeight,
                    name + " has wrong atlas dimensions");
            int[] expected = new int[atlas.atlasWidth * atlas.atlasHeight];
            for (Piece piece : pieces) {
                BufferedImage base = textures.get(piece.sourceName + "_Base");
                BufferedImage accent = textures.get(piece.sourceName + "_Accent");
                for (int y = 0; y < piece.textureSize; y++) for (int x = 0; x < piece.textureSize; x++) {
                    int tintedBase = TexturePixels.tintArgb(base.getRGB(x, y), AscendancePalette.tierPrimaryRgb(tier));
                    expected[y * atlas.atlasWidth + piece.atlasX + x] = piece.hasAccent
                            ? TexturePixels.overArgb(TexturePixels.tintArgb(accent.getRGB(x, y), AscendancePalette.tierMetalRgb(tier)), tintedBase)
                            : tintedBase;
                }
            }
            for (int y = 0; y < atlas.atlasHeight; y++) for (int x = 0; x < atlas.atlasWidth; x++) {
                check(image.getRGB(x, y) == expected[y * atlas.atlasWidth + x],
                        name + " changed source coverage, shading or canonical tint at " + x + "," + y);
            }
        }
    }

    private static void checkBeltGaitIndependence() {
        var model = new AscendanceArmorModel<LivingEntity>(EquipmentSlot.LEGS);
        model.young = false;
        RecordingBuffer restingBelt = renderOnly(model, model.body);
        RecordingBuffer restingLeftLeg = renderOnly(model, model.leftLeg);
        RecordingBuffer restingRightLeg = renderOnly(model, model.rightLeg);

        // A crouched torso moves the belt while each leg keeps its own hip transform.
        model.body.xRot = .6f;
        model.body.y = 2;
        model.body.z = 1;
        RecordingBuffer crouchedBelt = renderOnly(model, model.body);
        checkMoved(restingBelt, crouchedBelt, "Belt must follow torso pitch and translation");
        checkSameVertices(restingLeftLeg, renderOnly(model, model.leftLeg), "Torso must not drive left-leg gait");
        checkSameVertices(restingRightLeg, renderOnly(model, model.rightLeg), "Torso must not drive right-leg gait");

        // Walking poses can change both legs without pulling the belt away from the torso.
        model.leftLeg.xRot = .8f;
        model.rightLeg.xRot = -.8f;
        model.leftLeg.yRot = .2f;
        model.rightLeg.yRot = -.2f;
        checkSameVertices(crouchedBelt, renderOnly(model, model.body), "Leg rotations must not move or relight the belt");
        checkMoved(restingLeftLeg, renderOnly(model, model.leftLeg), "Left-leg geometry must follow its gait pose");
        checkMoved(restingRightLeg, renderOnly(model, model.rightLeg), "Right-leg geometry must follow its gait pose");
    }

    private static RecordingBuffer renderOnly(AscendanceArmorModel<LivingEntity> model, ModelPart part) {
        model.setAllVisible(false);
        part.visible = true;
        return render(model);
    }

    private static void checkSameVertices(RecordingBuffer expected, RecordingBuffer actual, String message) {
        check(!expected.vertices.isEmpty() && actual.vertices.size() == expected.vertices.size(), message + " (vertex count)");
        for (int i = 0; i < expected.vertices.size(); i++) for (int axis = 0; axis < 8; axis++)
            close(actual.vertices.get(i)[axis], expected.vertices.get(i)[axis], message);
    }

    private static void checkMoved(RecordingBuffer before, RecordingBuffer after, String message) {
        check(!before.vertices.isEmpty() && after.vertices.size() == before.vertices.size(), message + " (vertex count)");
        for (int i = 0; i < before.vertices.size(); i++) for (int axis = 0; axis < 3; axis++)
            if (Math.abs(before.vertices.get(i)[axis] - after.vertices.get(i)[axis]) > 2e-6f) return;
        throw new AssertionError(message);
    }

    private static void checkNormal(List<float[]> vertices, int index, String part) {
        float[] a = vertices.get(index), b = vertices.get(index + 1), c = vertices.get(index + 2);
        float x = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
        float y = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
        float z = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        check(length > 1e-9f, part + " contains a degenerate source triangle");
        close(a[5], x / length, part + " surface normal X");
        close(a[6], y / length, part + " surface normal Y");
        close(a[7], z / length, part + " surface normal Z");
    }

    private static RecordingBuffer render(AscendanceArmorModel<LivingEntity> model) {
        var buffer = new RecordingBuffer();
        model.renderToBuffer(new PoseStack(), buffer, 15728880, 0, -1);
        return buffer;
    }

    private static ModelPart bone(AscendanceArmorModel<LivingEntity> model, String name) {
        return switch (name) {
            case "head" -> model.head;
            case "hat" -> model.hat;
            case "body" -> model.body;
            case "left_arm" -> model.leftArm;
            case "right_arm" -> model.rightArm;
            case "left_leg" -> model.leftLeg;
            case "right_leg" -> model.rightLeg;
            default -> throw new AssertionError("Unknown bone " + name);
        };
    }

    private static Map<String, JsonObject> namedEntries(JsonObject source, String name) {
        Map<String, JsonObject> entries = new LinkedHashMap<>();
        for (var entry : source.getAsJsonArray(name)) {
            JsonObject value = entry.getAsJsonObject();
            entries.put(value.get("name").getAsString(), value);
        }
        return entries;
    }

    private static InputStream resource(String path) {
        InputStream input = AscendanceArmorTest.class.getResourceAsStream(ROOT + path);
        check(input != null, "Missing generated resource " + path);
        return input;
    }

    private static void close(float actual, float expected, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < 2e-6f,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Piece(String sourceName, String resource, EquipmentSlot slot, String bone, String atlas,
                         int textureSize, int uvSize, int atlasX, int atlasWidth, int atlasHeight,
                         float pivotX, float pivotY, boolean hasAccent) { }

    private static final class RecordingBuffer implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new float[]{x, y, z, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN}); return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) {
            check(Float.isFinite(u) && Float.isFinite(v) && u >= 0 && u <= 1 && v >= 0 && v <= 1, "Atlas UV outside [0,1]");
            vertices.getLast()[3] = u; vertices.getLast()[4] = v; return this;
        }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) {
            check(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                    && Math.abs(x * x + y * y + z * z - 1) < 1e-5, "Finite unit surface normal");
            vertices.getLast()[5] = x; vertices.getLast()[6] = y; vertices.getLast()[7] = z; return this;
        }
    }
}
