package com.mistaboom.essence_ascendance.client;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceShieldItem;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;

/** Exercises real shield passes and generated geometry without an OpenGL window. */
public final class AscendanceShieldTest {
    private static final String PREFIX = "assets/essence_ascendance/";
    private static final String TARGET = "net/minecraft/client/renderer/BlockEntityWithoutLevelRenderer";
    private static final String DESCRIPTOR = "(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        permitTestItemRegistration();
        Item shield = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "render_test_shield"),
                new AscendanceShieldItem(new Item.Properties().durability(1024)));
        check(EquipmentShieldService.isShield(new ItemStack(shield)), "Authored renderer accepts Ascendance shield");
        check(!EquipmentShieldService.isShield(new ItemStack(Items.SHIELD)), "Vanilla shield stays on vanilla renderer");
        check(!EquipmentShieldService.isShield(ItemStack.EMPTY)
                && !EquipmentShieldService.isShield(new ItemStack(Items.IRON_CHESTPLATE)), "Other items remain untouched");

        var meshField = AscendanceShieldRenderer.class.getDeclaredField("MESH");
        meshField.setAccessible(true);
        BlockbenchStaticMesh mesh = (BlockbenchStaticMesh) meshField.get(null);
        var dataField = BlockbenchStaticMesh.class.getDeclaredField("vertexData");
        dataField.setAccessible(true);
        float[] data = readMesh();
        // Supply real generated resource data without needing Minecraft's live resource manager.
        dataField.set(mesh, data);
        var bodyField = AscendanceShieldRenderer.class.getDeclaredField("BODY");
        bodyField.setAccessible(true);
        RenderType[] bodies = (RenderType[]) bodyField.get(null);
        var emissionField = AscendanceShieldRenderer.class.getDeclaredField("EMISSION");
        emissionField.setAccessible(true);
        RenderType emission = (RenderType) emissionField.get(null);
        check(texture(emission).getPath().equals("textures/item/ascendance_shield/emission.png"), "Shared white emission mask");
        int[] alphas = {0, 36, 72, 108, 144, 180};
        for (EquipmentTier tier : EquipmentTier.values()) {
            check(texture(bodies[tier.ordinal()]).getPath().equals(
                    "textures/item/ascendance_shield/" + tier.serializedName() + ".png"), "Correct stitched tier atlas");
            check(resource(texture(bodies[tier.ordinal()])) != null, "Tier texture is packaged");
            ItemStack stack = new ItemStack(shield);
            stack.set(DataComponents.CUSTOM_DATA, EquipmentTierData.tierData(tier));
            exercisePasses(stack, tier, bodies[tier.ordinal()], emission, data, alphas[tier.ordinal()], false);
            if (tier == EquipmentTier.TRANSCENDENT) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                exercisePasses(stack, tier, bodies[tier.ordinal()], emission, data, alphas[tier.ordinal()], true);
            }
        }
        AscendanceShieldRenderer.invalidateResources();
        check(dataField.get(mesh) == null, "Resource reload discards stale shield geometry");
        checkModelsAndNativeHooks();
        System.out.println("AscendanceShieldTest: six real tier draws, exact accent/normal geometry, tuned emission,"
                + " foil, vanilla isolation, reload and both loader hook descriptors PASS");
    }

    private static void exercisePasses(ItemStack stack, EquipmentTier tier, RenderType body, RenderType emission,
                                       float[] data, int alpha, boolean foil) {
        var buffers = new RecordingBuffers();
        PoseStack pose = new PoseStack();
        pose.translate(0.25F, -0.125F, 1.5F);
        var before = pose.last();
        int light = 0x600090;
        AscendanceShieldRenderer.render(stack, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        check(pose.last() == before, "Shield restores caller pose stack");
        var normal = buffers.recordings.get(body);
        check(normal != null && normal.vertices.size() == data.length / 24 * 4, "Draw authored triangles exactly once");
        checkColorAndLight(normal, 0xFFFFFF, 255, light);
        float[] first = normal.vertices.getFirst();
        check(first[0] == data[0] + 0.25F && first[1] == -data[1] - 0.125F
                && first[2] == -data[2] + 1.5F, "Preserve centered vanilla grip and scale(1,-1,-1)");
        if (alpha == 0) {
            check(!buffers.recordings.containsKey(emission), "Latent must not request an emissive pass");
        } else {
            var glow = buffers.recordings.get(emission);
            check(glow != null, "Higher tier requests emission");
            checkGeometry(normal, glow, "Emission uses exact normal-pass positions, UVs and normals");
            checkColorAndLight(glow, ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier)),
                    alpha, LightTexture.FULL_BRIGHT);
        }
        if (foil) {
            checkGeometry(normal, buffers.recordings.get(RenderType.glint()), "Vanilla foil covers normal geometry once");
        } else {
            check(!buffers.recordings.containsKey(RenderType.glint()), "Unenchanted shield has no foil pass");
        }
        check(buffers.recordings.size() == 1 + (alpha == 0 ? 0 : 1) + (foil ? 1 : 0), "No duplicate geometry passes");
    }

    private static void checkColorAndLight(RecordingBuffer buffer, int rgb, int alpha, int light) {
        for (float[] vertex : buffer.vertices) {
            check(vertex[8] == (rgb >> 16 & 255) && vertex[9] == (rgb >> 8 & 255)
                            && vertex[10] == (rgb & 255) && vertex[11] == alpha,
                    "Vertex uses canonical tint and current tuned alpha");
            check(vertex[12] == (light & 0xFFFF) && vertex[13] == (light >>> 16), "Pass lighting is preserved");
        }
    }

    private static void checkGeometry(RecordingBuffer first, RecordingBuffer second, String message) {
        check(second != null && first.vertices.size() == second.vertices.size(), message);
        for (int i = 0; i < first.vertices.size(); i++) {
            check(Arrays.equals(first.vertices.get(i), 0, 8, second.vertices.get(i), 0, 8), message);
        }
    }

    private static float[] readMesh() throws Exception {
        try (var input = new DataInputStream(AscendanceShieldTest.class.getResourceAsStream(
                "/" + PREFIX + "meshes/item/ascendance_shield.eamesh"))) {
            check(input.readInt() == 0x45414D31, "EAM1 mesh is packaged");
            int triangles = input.readInt();
            check(triangles > 0 && triangles <= 100_000, "Bounded authored triangle count");
            float[] data = new float[triangles * 24];
            for (int i = 0; i < data.length; i++) data[i] = input.readFloat();
            check(input.read() == -1, "No trailing mesh bytes");
            return data;
        }
    }

    private static ResourceLocation texture(RenderType type) throws Exception {
        var stateField = type.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(type);
        var textureStateField = state.getClass().getDeclaredField("textureState");
        textureStateField.setAccessible(true);
        Object textureState = textureStateField.get(state);
        var textureField = textureState.getClass().getDeclaredField("texture");
        textureField.setAccessible(true);
        return (ResourceLocation) ((Optional<?>) textureField.get(textureState)).orElseThrow();
    }

    private static java.net.URL resource(ResourceLocation texture) {
        return AscendanceShieldTest.class.getResource("/assets/" + texture.getNamespace() + "/" + texture.getPath());
    }

    private static void checkModelsAndNativeHooks() throws Exception {
        for (String name : List.of("ascendance_shield", "ascendance_shield_blocking")) {
            try (var input = AscendanceShieldTest.class.getResourceAsStream("/" + PREFIX + "models/item/" + name + ".json")) {
                var model = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                check(model.get("parent").getAsString().equals("minecraft:item/"
                        + (name.endsWith("_blocking") ? "shield_blocking" : "shield")), "Retain vanilla hand/display transforms");
            }
        }
        try (InputStream input = AscendanceShieldTest.class.getClassLoader().getResourceAsStream(TARGET + ".class")) {
            checkNativeHook(input);
        }
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("art/asset-manifest.json"))) root = root.getParent();
        check(root != null, "Project root available for both loader contract checks");
        List<Path> jars;
        try (var paths = Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            jars = paths.filter(path -> path.getFileName().toString().startsWith("neoforge-")
                    && path.toString().endsWith(".jar") && !path.toString().endsWith("-sources.jar")).toList();
        }
        check(!jars.isEmpty(), "Mapped NeoForge renderer is available");
        for (Path path : jars) try (JarFile jar = new JarFile(path.toFile());
                                   InputStream input = jar.getInputStream(jar.getJarEntry(TARGET + ".class"))) {
            checkNativeHook(input);
        }
        String commonHook = null;
        for (String loader : List.of("fabric", "neoforge")) {
            String hook = Files.readString(root.resolve(loader + "/src/main/java/com/mistaboom/essence_ascendance/"
                    + loader + "/mixin/ShieldItemRendererMixin.java"));
            check(hook.contains("@Inject(method = \"renderByItem\", at = @At(\"HEAD\"), cancellable = true)"),
                    loader + " uses stable method-head dispatch");
            check(hook.contains("if (EquipmentShieldService.isShield(stack))")
                            && hook.contains("AscendanceShieldRenderer.render(stack, pose, buffers, packedLight, packedOverlay)")
                            && hook.contains("ci.cancel()"), loader + " owns only the custom shield draw");
            String body = hook.substring(hook.indexOf("import "));
            if (commonHook != null) check(commonHook.equals(body), "Both loaders dispatch identically");
            commonHook = body;
        }
    }

    private static void checkNativeHook(InputStream input) throws Exception {
        check(input != null, "Native item renderer exists");
        var type = new ClassNode();
        new ClassReader(input).accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        check(type.methods.stream().anyMatch(method -> method.name.equals("renderByItem")
                && method.desc.equals(DESCRIPTOR) && (method.access & Opcodes.ACC_PUBLIC) != 0),
                "Exact public renderByItem injection signature exists in mapped loader");
    }

    private static void permitTestItemRegistration() throws Exception {
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(BuiltInRegistries.ITEM, false);
        var intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        intrusive.set(BuiltInRegistries.ITEM, new IdentityHashMap<>());
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }

    private static final class RecordingBuffers implements MultiBufferSource {
        private final Map<RenderType, RecordingBuffer> recordings = new IdentityHashMap<>();
        public VertexConsumer getBuffer(RenderType type) {
            return recordings.computeIfAbsent(type, ignored -> new RecordingBuffer());
        }
    }

    private static final class RecordingBuffer implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();
        public VertexConsumer addVertex(float x, float y, float z) {
            float[] vertex = new float[14];
            vertex[0] = x; vertex[1] = y; vertex[2] = z;
            vertices.add(vertex);
            return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            float[] vertex = vertices.getLast();
            vertex[8] = r; vertex[9] = g; vertex[10] = b; vertex[11] = a;
            return this;
        }
        public VertexConsumer setUv(float u, float v) {
            vertices.getLast()[3] = u; vertices.getLast()[4] = v; return this;
        }
        public VertexConsumer setNormal(float x, float y, float z) {
            vertices.getLast()[5] = x; vertices.getLast()[6] = y; vertices.getLast()[7] = z; return this;
        }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) {
            vertices.getLast()[12] = u; vertices.getLast()[13] = v; return this;
        }
    }
}
