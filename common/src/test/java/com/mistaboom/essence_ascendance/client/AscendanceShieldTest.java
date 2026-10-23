package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.verification.TargetTestInputs;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceShieldItem;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if <26.1.2
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderType;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderTypes;*/
//? if <26.1.2
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

    private static RenderType foilType() {
        //? if >=26.1.2
        /*return RenderTypes.entityGlint();*/
        //? if <26.1.2
        return RenderType.glint();
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        permitTestItemRegistration();
        var shieldId = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "render_test_shield");
        Item shield = Registry.register(BuiltInRegistries.ITEM,
                shieldId,
                //? if >=26.1.2
                /*new AscendanceShieldItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, shieldId)).durability(1024)));*/
                //? if <26.1.2
                new AscendanceShieldItem(new Item.Properties().durability(1024)));
        //? if >=26.1.2
        /*com.mistaboom.essence_ascendance.verification.NativeTestBootstrap2612.bindRegisteredComponents();*/
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
                    alpha,
                    //? if >=26.1.2
                    /*ClientLightCompat.FULL_BRIGHT);*/
                    //? if <26.1.2
                    LightTexture.FULL_BRIGHT);
        }
        if (foil) {
            checkGeometry(normal, buffers.recordings.get(foilType()), "Vanilla foil covers normal geometry once");
        } else {
            check(!buffers.recordings.containsKey(foilType()), "Unenchanted shield has no foil pass");
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
        //? if >=26.1.2 {
        /*var texturesField = state.getClass().getDeclaredField("textures");
        texturesField.setAccessible(true);
        var bindings = (Map<?, ?>) texturesField.get(state);
        check(bindings.size() == 1, "Shield pass has one authored texture binding");
        Object binding = bindings.values().iterator().next();
        var location = binding.getClass().getDeclaredMethod("location");
        location.setAccessible(true);
        return (ResourceLocation) location.invoke(binding);
        *///?} else {
        var textureStateField = state.getClass().getDeclaredField("textureState");
        textureStateField.setAccessible(true);
        Object textureState = textureStateField.get(state);
        var textureField = textureState.getClass().getDeclaredField("texture");
        textureField.setAccessible(true);
        return (ResourceLocation) ((Optional<?>) textureField.get(textureState)).orElseThrow();
        //?}
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
        if ("26.1.2".equals(System.getProperty("essence.minecraft"))) {
            checkSpecialModelNativeHooks();
            return;
        }
        for (String loader : List.of("fabric", "neoforge")) {
            try (InputStream input = TargetTestInputs.nativeClass(loader, TARGET)) { checkNativeHook(input); }
        }
        String hook = Files.readString(TargetTestInputs.javaSource("common",
                "com/mistaboom/essence_ascendance/mixin/ShieldItemRendererMixin.java"));
        check(hook.contains("@Inject(method = \"renderByItem\", at = @At(\"HEAD\"), cancellable = true)"),
                "Shared shield hook uses stable method-head dispatch");
        check(hook.contains("if (EquipmentShieldService.isShield(stack))")
                        && hook.contains("AscendanceShieldRenderer.render(stack, pose, buffers, packedLight, packedOverlay)")
                        && hook.contains("ci.cancel()"), "Shared hook owns only the custom shield draw");
        String commonConfig = Files.readString(TargetTestInputs.processedResource("common",
                "essence_ascendance.mixins.json"));
        check(commonConfig.contains("\"ShieldItemRendererMixin\""), "Common registers the client-only shield hook");
        for (String loader : List.of("fabric", "neoforge")) {
            String configName = loader.equals("fabric")
                    ? "essence_ascendance.damage.mixins.json" : "essence_ascendance.vitality.mixins.json";
            String config = Files.readString(TargetTestInputs.processedResource(loader, configName));
            check(!config.contains("\"ShieldItemRendererMixin\""), loader + " cannot double-apply the shared shield hook");
        }
    }

    /** 26.1 routes authored shield geometry through the special-model codec and collector. */
    private static void checkSpecialModelNativeHooks() throws Exception {
        try (var input = AscendanceShieldTest.class.getResourceAsStream("/" + PREFIX + "items/ascendance_shield.json")) {
            check(input != null, "Target item model definition is packaged");
            var model = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("model");
            check(model.get("type").getAsString().equals("minecraft:range_dispatch")
                            && model.getAsJsonObject("fallback").getAsJsonObject("model")
                            .get("type").getAsString().equals("essence_ascendance:ascendance_shield")
                            && model.getAsJsonArray("entries").get(0).getAsJsonObject()
                            .getAsJsonObject("model").getAsJsonObject("model")
                            .get("type").getAsString().equals("essence_ascendance:ascendance_shield"),
                    "Normal and blocking poses both retain the authored special shield model");
        }
        for (String loader : List.of("fabric", "neoforge")) {
            try (InputStream input = TargetTestInputs.nativeClass(loader,
                    "net/minecraft/client/renderer/special/SpecialModelRenderer")) {
                var nativeType = new ClassNode();
                new ClassReader(input).accept(nativeType, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                check(nativeType.methods.stream().anyMatch(method -> method.name.equals("submit")
                                && method.desc.contains("SubmitNodeCollector")
                                && method.desc.endsWith("IIZI)V")),
                        loader + " mapped native special-model renderer accepts deferred item submission");
            }
            String source = loader.equals("fabric")
                    ? Files.readString(TargetTestInputs.javaSource(loader,
                    "com/mistaboom/essence_ascendance/fabric/client/FabricItemModelCodecs2612.java"))
                    : Files.readString(TargetTestInputs.javaSource(loader,
                    "com/mistaboom/essence_ascendance/neoforge/EssenceAscendanceNeoForgeClient.java"));
            check(source.contains("AscendanceShieldSpecialRenderer2612.Unbaked.MAP_CODEC")
                            && source.contains("ascendance_shield"),
                    loader + " registers the authored shield special-model codec");
        }
        var renderer = Class.forName("com.mistaboom.essence_ascendance.client.AscendanceShieldSpecialRenderer2612");
        check(java.util.Arrays.stream(renderer.getDeclaredMethods()).anyMatch(method -> method.getName().equals("submit"))
                        && java.util.Arrays.stream(renderer.getDeclaredMethods()).anyMatch(method -> method.getName().equals("getExtents")),
                "The target shield retains geometry submission and model bounds");
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
        //? if >=26.1.2
        /*@Override public VertexConsumer setColor(int argb) { return setColor(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, argb >>> 24 & 255); }*/
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
        //? if >=26.1.2
        /*@Override public VertexConsumer setLineWidth(float width) { if (!Float.isFinite(width) || width <= 0) throw new AssertionError("Invalid native line width"); return this; }*/
    }
}
