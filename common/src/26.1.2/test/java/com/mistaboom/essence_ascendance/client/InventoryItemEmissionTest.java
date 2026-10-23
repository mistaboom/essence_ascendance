package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.*;
import com.mistaboom.essence_ascendance.verification.TargetTestInputs;
import com.mistaboom.essence_ascendance.verification.NativeTestBootstrap2612;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The 26.1 native item submission path, with deferred geometry executed by a recording consumer. */
public final class InventoryItemEmissionTest {
    private static int checks;
    private static final List<BakedQuad> QUADS = List.of(quad(0), quad(1), quad(2), quad(3), quad(-1));

    public static void main(String[] args) throws Exception {
        NativeTestBootstrap2612.bindBuiltInComponents();
        checkItemContract();
        permitTestItemRegistration();
        Identifier id = Identifier.fromNamespaceAndPath("essence_ascendance", "emission_test_item_2612");
        Item item = Registry.register(BuiltInRegistries.ITEM, id, new TestEquipmentItem(id));
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1).set(DataComponents.MAX_DAMAGE, 100).build());
        int[] expectedAlpha = {0, 36, 72, 108, 144, 180};
        for (EquipmentTier tier : EquipmentTier.values()) {
            ItemStack stack = new ItemStack(item);
            if (tier != EquipmentTier.LATENT) stack.set(DataComponents.CUSTOM_DATA, EquipmentTierData.tierData(tier));
            var state = new ItemStackRenderState();
            var pose = new PoseStack();
            pose.translate(.25F, -.5F, 1F);
            var capture = new Submission();
            InventoryItemEmissionRenderer.capture(state, stack);
            InventoryItemEmissionRenderer.submit(state, QUADS, pose, capture.collector(), OverlayTexture.NO_OVERLAY);
            if (tier == EquipmentTier.LATENT) {
                check(capture.passes.isEmpty(), "Latent item has no emission submission");
                continue;
            }
            check(capture.passes.size() == 1, "Each higher tier submits one deferred emission pass");
            var pass = capture.passes.getFirst();
            check(texture(pass.type()).equals(TextureAtlas.LOCATION_ITEMS), "Emission samples the 26.1 item atlas");
            check(pass.pose() == pose, "Emission reuses the native display transform stack");
            check(capture.vertices.isEmpty(), "Geometry waits for native collector execution after item submission");
            pass.geometry().render(pose.last(), capture);
            check(capture.vertices.size() == 4, "Only tint-index-one accent quad emits");
            int rgb = ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier));
            for (float[] vertex : capture.vertices) {
                check(channel(vertex[3], rgb >> 16 & 255) && channel(vertex[4], rgb >> 8 & 255)
                                && channel(vertex[5], rgb & 255) && channel(vertex[6], expectedAlpha[tier.ordinal()]),
                        "Native quad keeps luminous tint and progressive alpha");
                check((int) vertex[7] == ClientLightCompat.FULL_BRIGHT, "Accent uses full brightness");
                check((int) vertex[8] == OverlayTexture.NO_OVERLAY, "Accent keeps the native overlay");
            }
            check(capture.vertices.getFirst()[0] == .25F && capture.vertices.getFirst()[1] == -.5F
                            && capture.vertices.getFirst()[2] == 1F,
                    "Native quad uses current item pose");
            InventoryItemEmissionRenderer.clear(state);
            var stale = new Submission();
            InventoryItemEmissionRenderer.submit(state, QUADS, new PoseStack(), stale.collector(), 0);
            check(stale.passes.isEmpty(), "Reused native state cannot leak the previous item accent");
        }
        Identifier plainId = Identifier.fromNamespaceAndPath("essence_ascendance", "plain_emission_test_item_2612");
        Item plain = Registry.register(BuiltInRegistries.ITEM, plainId,
                new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, plainId))));
        plain.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        var unrelated = new Submission();
        var state = new ItemStackRenderState();
        InventoryItemEmissionRenderer.capture(state, new ItemStack(plain));
        InventoryItemEmissionRenderer.submit(state, QUADS, new PoseStack(), unrelated.collector(), 0);
        check(unrelated.passes.isEmpty(), "Vanilla items keep vanilla rendering only");
        var empty = new Submission();
        InventoryItemEmissionRenderer.capture(state, new ItemStack(item));
        InventoryItemEmissionRenderer.submit(state, List.of(), new PoseStack(), empty.collector(), 0);
        check(empty.passes.isEmpty(), "An empty resolved model cannot schedule geometry");
        checkLoaderHooks();
        System.out.println("InventoryItemEmissionTest: " + checks + " native item submission assertions PASS");
    }

    private static BakedQuad quad(int tint) {
        long a = UVPair.pack(0, 0), b = UVPair.pack(1, 0), c = UVPair.pack(1, 1), d = UVPair.pack(0, 1);
        var material = new BakedQuad.MaterialInfo(null, null, null, tint, false, 0);
        return new BakedQuad(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0), new Vector3f(0, 1, 0), a, b, c, d, Direction.NORTH, material);
    }

    private static Identifier texture(RenderType type) throws Exception {
        var stateField = type.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(type);
        var texturesField = state.getClass().getDeclaredField("textures");
        texturesField.setAccessible(true);
        var bindings = (Map<?, ?>) texturesField.get(state);
        check(bindings.size() == 1, "Item emission has exactly one atlas binding");
        Object binding = bindings.values().iterator().next();
        var location = binding.getClass().getDeclaredMethod("location");
        location.setAccessible(true);
        return (Identifier) location.invoke(binding);
    }

    private static void checkLoaderHooks() throws Exception {
        String resolver = Files.readString(TargetTestInputs.javaSource("common",
                "com/mistaboom/essence_ascendance/mixin/ItemRendererEmissionMixin.java"));
        check(resolver.contains("InventoryItemEmissionRenderer.clear(state)")
                        && resolver.contains("InventoryItemEmissionRenderer.capture(state, stack)"),
                "Shared hook associates only the current stack with native render state");
        String layer = Files.readString(TargetTestInputs.javaSource("common",
                "com/mistaboom/essence_ascendance/mixin/ItemStackEmissionLayerMixin.java"));
        check(layer.contains("InventoryItemEmissionRenderer.submit(this$0, quads, pose, collector, overlay)")
                        && layer.contains("PoseStack;popPose()V"),
                "Shared hook submits accents after the normal layer with its active transform");
        var config = com.google.gson.JsonParser.parseString(Files.readString(
                TargetTestInputs.processedResource("common", "essence_ascendance.mixins.json"))).getAsJsonObject();
        var clients = new java.util.ArrayList<String>();
        config.getAsJsonArray("client").forEach(entry -> clients.add(entry.getAsString()));
        check(config.get("required").getAsBoolean(), "Native item hooks remain required");
        check(clients.stream().filter("ItemRendererEmissionMixin"::equals).count() == 1
                        && clients.stream().filter("ItemStackEmissionLayerMixin"::equals).count() == 1,
                "Processed common metadata assembles both native emission hooks exactly once");
        for (String loader : List.of("fabric", "neoforge")) {
            String configName = loader.equals("fabric")
                    ? "essence_ascendance.damage.mixins.json" : "essence_ascendance.vitality.mixins.json";
            String loaderConfig = Files.readString(TargetTestInputs.processedResource(loader, configName));
            check(!loaderConfig.contains("\"ItemRendererEmissionMixin\"")
                            && !loaderConfig.contains("\"ItemStackEmissionLayerMixin\""),
                    loader + " cannot double-apply the shared emission hooks");
        }
    }

    private static void checkItemContract() {
        for (Class<?> type : List.of(AscendanceSwordItem.class, AscendanceRangedWeaponItem.class,
                AscendanceCasterItem.class, AscendancePickaxeItem.class, AscendanceAxeItem.class,
                AscendanceShovelItem.class, AscendanceHoeItem.class))
            check(EmissiveAccentItem.class.isAssignableFrom(type), type.getSimpleName() + " opts into accent emission");
        for (Class<?> type : List.of(AscendanceArmorItem.class, AscendanceShieldItem.class, AscendanceArchiveItem.class))
            check(!EmissiveAccentItem.class.isAssignableFrom(type), type.getSimpleName() + " retains its native renderer");
    }

    private static void permitTestItemRegistration() throws Exception {
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(BuiltInRegistries.ITEM, false);
        var intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        intrusive.set(BuiltInRegistries.ITEM, new IdentityHashMap<>());
    }

    private static boolean channel(float actual, int expected) { return Math.abs(actual - expected) <= 1; }
    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }

    private static final class TestEquipmentItem extends Item implements EquipmentProfileItem, EmissiveAccentItem {
        private TestEquipmentItem(Identifier id) {
            super(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).durability(100));
        }
        @Override public Identifier equipmentProfileId() { return EquipmentProfiles.SWORD.id(); }
    }

    private record Pass(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {}
    private static final class Submission implements VertexConsumer {
        private final List<Pass> passes = new ArrayList<>();
        private final List<float[]> vertices = new ArrayList<>();

        SubmitNodeCollector collector() {
            return (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                    new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                        if (method.getName().equals("submitCustomGeometry")) {
                            passes.add(new Pass((PoseStack) args[0], (RenderType) args[1],
                                    (SubmitNodeCollector.CustomGeometryRenderer) args[2]));
                            return null;
                        }
                        if (method.getName().equals("order")) return proxy;
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("toString")) return "Recording item submit collector";
                        throw new AssertionError("Unexpected item submission: " + method.getName());
                    });
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new float[]{x, y, z, 0, 0, 0, 0, 0, 0});
            return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            float[] v = vertices.getLast(); v[3] = red; v[4] = green; v[5] = blue; v[6] = alpha;
            return this;
        }
        @Override public VertexConsumer setColor(int argb) {
            return setColor(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, argb >>> 24 & 255);
        }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { vertices.getLast()[8] = u | v << 16; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { vertices.getLast()[7] = u | v << 16; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
