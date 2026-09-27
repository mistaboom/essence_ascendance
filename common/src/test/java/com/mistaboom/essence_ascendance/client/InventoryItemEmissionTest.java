package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import com.mistaboom.essence_ascendance.item.AscendanceArchiveItem;
import com.mistaboom.essence_ascendance.item.AscendanceAxeItem;
import com.mistaboom.essence_ascendance.item.AscendanceCasterItem;
import com.mistaboom.essence_ascendance.item.AscendanceHoeItem;
import com.mistaboom.essence_ascendance.item.AscendanceMeleeWeaponItem;
import com.mistaboom.essence_ascendance.item.AscendancePickaxeItem;
import com.mistaboom.essence_ascendance.item.AscendanceRangedWeaponItem;
import com.mistaboom.essence_ascendance.item.AscendanceShieldItem;
import com.mistaboom.essence_ascendance.item.AscendanceShovelItem;
import com.mistaboom.essence_ascendance.item.EmissiveAccentItem;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Exercises the real generated-item accent pass without opening a rendering window. */
public final class InventoryItemEmissionTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        checkItemContract();
        permitTestItemRegistration();
        Item emissive = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "emission_test_item"),
                new TestEquipmentItem());
        BakedModel model = new TestModel();

        int[] expectedAlpha = {0, 36, 72, 108, 144, 180};
        for (EquipmentTier tier : EquipmentTier.values()) {
            ItemStack stack = new ItemStack(emissive);
            if (tier != EquipmentTier.LATENT) {
                stack.set(DataComponents.CUSTOM_DATA, EquipmentTierData.tierData(tier));
            }
            PoseStack pose = new PoseStack();
            pose.translate(0.25F, -0.5F, 1.0F);
            RecordingBuffers buffers = new RecordingBuffers();
            InventoryItemEmissionRenderer.render(stack, model, pose, buffers);

            if (tier == EquipmentTier.LATENT) {
                check(buffers.recordings.isEmpty(), "Latent item does not request an emission pass");
                continue;
            }
            check(buffers.recordings.size() == 1, "Higher tier requests exactly one shared emission buffer");
            Map.Entry<RenderType, RecordingBuffer> pass = buffers.recordings.entrySet().iterator().next();
            check(texture(pass.getKey()).equals(TextureAtlas.LOCATION_BLOCKS),
                    "Generated-item emission samples the shared item/block atlas");
            check(pass.getValue().vertices.size() == 4,
                    "Only the tint-index-one accent quad emits; base, nochange and untinted quads do not");
            int rgb = ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier));
            for (float[] vertex : pass.getValue().vertices) {
                check(channel(vertex[3], rgb >> 16 & 255)
                                && channel(vertex[4], rgb >> 8 & 255)
                                && channel(vertex[5], rgb & 255)
                                && channel(vertex[6], expectedAlpha[tier.ordinal()]),
                        "Item emission uses the canonical luminous tint and progressive armor alpha");
                check((int) vertex[7] == LightTexture.FULL_BRIGHT,
                        "Item accent is independent of environmental light");
                check((int) vertex[8] == OverlayTexture.NO_OVERLAY,
                        "Item accent uses the armor/shield overlay contract");
            }
            float[] first = pass.getValue().vertices.getFirst();
            check(first[0] == 0.25F && first[1] == -0.5F && first[2] == 1.0F,
                    "Emission reuses the already-selected model's current display transform");
        }

        RecordingBuffers unrelated = new RecordingBuffers();
        InventoryItemEmissionRenderer.render(new ItemStack(Item.byBlock(net.minecraft.world.level.block.Blocks.STONE)),
                model, new PoseStack(), unrelated);
        check(unrelated.recordings.isEmpty(), "Unmarked vanilla items retain vanilla rendering only");
        checkDeferredDrawOrder(emissive, model);
        checkLoaderHooks();
        System.out.println("InventoryItemEmissionTest: " + checks
                + " accent filtering, six-tier brightness, transform, atlas and loader-hook checks PASS");
    }

    private static void checkDeferredDrawOrder(Item item, BakedModel model) {
        for (EquipmentTier tier : EquipmentTier.values()) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_DATA, EquipmentTierData.tierData(tier));
            try (var shared = new ByteBufferBuilder(4096); var atlas = new ByteBufferBuilder(4096)) {
                var buffers = new ScheduledBuffers(shared, atlas);
                var pose = new PoseStack();
                // RenderBuffers reserves a fixed buffer for this sheet. getBuffer does not flush it
                // when the custom emission pass requests a different, shared-buffer RenderType.
                buffers.getBuffer(Sheets.translucentCullBlockSheet()).putBulkData(pose.last(), quad(1),
                        1, 1, 1, 1, 0, OverlayTexture.NO_OVERLAY);
                InventoryItemEmissionRenderer.render(stack, model, pose, buffers);
                if (tier == EquipmentTier.LATENT) {
                    check(buffers.draws.isEmpty(), "Latent leaves normal item batching untouched");
                }
                buffers.endBatch();
                check(buffers.draws.getFirst() == Sheets.translucentCullBlockSheet(),
                        "Deferred normal item sheet must draw BEFORE emission, or it overwrites the glow: " + tier);
                check(buffers.draws.size() == (tier == EquipmentTier.LATENT ? 1 : 2),
                        "Draw exactly the normal sheet plus the non-Latent emission pass");
            }
        }
    }

    /** Real BufferSource scheduling and vertex buffers; intercept only the terminal GPU upload. */
    private static final class ScheduledBuffers extends MultiBufferSource.BufferSource {
        private final List<RenderType> draws = new ArrayList<>();

        private ScheduledBuffers(ByteBufferBuilder shared, ByteBufferBuilder atlas) {
            super(shared, new LinkedHashMap<>(Map.of(Sheets.translucentCullBlockSheet(), atlas)));
        }

        @Override
        public void endBatch(RenderType type) {
            var builder = startedBuilders.remove(type);
            if (builder != null) {
                try (var mesh = builder.build()) {
                    if (mesh != null) draws.add(type);
                }
            }
            if (lastSharedType == type) lastSharedType = null;
        }
    }

    private static void checkItemContract() {
        for (Class<?> type : List.of(AscendanceMeleeWeaponItem.class, AscendanceRangedWeaponItem.class,
                AscendanceCasterItem.class, AscendancePickaxeItem.class, AscendanceAxeItem.class,
                AscendanceShovelItem.class, AscendanceHoeItem.class)) {
            check(EmissiveAccentItem.class.isAssignableFrom(type), type.getSimpleName() + " opts into accent emission");
        }
        for (Class<?> type : List.of(AscendanceArmorItem.class, AscendanceShieldItem.class, AscendanceArchiveItem.class)) {
            check(!EmissiveAccentItem.class.isAssignableFrom(type),
                    type.getSimpleName() + " stays on its established non-item-pass renderer");
        }
    }

    private static void checkLoaderHooks() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("art/asset-manifest.json"))) root = root.getParent();
        check(root != null, "Project root is available for loader hook checks");
        for (String loader : List.of("fabric", "neoforge")) {
            String mixin = Files.readString(root.resolve(loader + "/src/main/java/com/mistaboom/essence_ascendance/"
                    + loader + "/mixin/ItemRendererEmissionMixin.java"));
            check(mixin.contains("shift = At.Shift.AFTER")
                            && mixin.contains("renderModelLists(")
                            && mixin.contains("InventoryItemEmissionRenderer.render(stack, model, pose, buffers)"),
                    loader + " draws once after vanilla using the selected model and active pose");
            String configName = loader.equals("fabric")
                    ? "essence_ascendance.damage.mixins.json" : "essence_ascendance.vitality.mixins.json";
            String config = Files.readString(root.resolve(loader + "/src/main/resources/" + configName));
            check(config.contains("\"ItemRendererEmissionMixin\""), loader + " registers its client-only emission hook");
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

    private static boolean channel(float actual, int expected) {
        return Math.abs(actual - expected) <= 1;
    }

    private static BakedQuad quad(int tintIndex) {
        int[] vertices = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            vertices[offset] = Float.floatToRawIntBits(vertex == 1 || vertex == 2 ? 1.0F : 0.0F);
            vertices[offset + 1] = Float.floatToRawIntBits(vertex >= 2 ? 1.0F : 0.0F);
            vertices[offset + 2] = Float.floatToRawIntBits(0.0F);
            vertices[offset + 3] = -1;
            vertices[offset + 4] = Float.floatToRawIntBits(vertex == 1 || vertex == 2 ? 1.0F : 0.0F);
            vertices[offset + 5] = Float.floatToRawIntBits(vertex >= 2 ? 1.0F : 0.0F);
        }
        return new BakedQuad(vertices, tintIndex, Direction.NORTH, null, false);
    }

    private static void permitTestItemRegistration() throws Exception {
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(BuiltInRegistries.ITEM, false);
        var intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        intrusive.set(BuiltInRegistries.ITEM, new IdentityHashMap<>());
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }

    private static final class TestEquipmentItem extends Item
            implements EquipmentProfileItem, EmissiveAccentItem {
        private TestEquipmentItem() {
            super(new Item.Properties().durability(100));
        }

        @Override
        public ResourceLocation equipmentProfileId() {
            return EquipmentProfiles.MELEE_WEAPON.id();
        }
    }

    private static final class TestModel implements BakedModel {
        private static final List<BakedQuad> QUADS = List.of(quad(0), quad(1), quad(2), quad(-1));

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return side == null ? QUADS : List.of();
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean usesBlockLight() { return false; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static final class RecordingBuffers implements MultiBufferSource {
        private final Map<RenderType, RecordingBuffer> recordings = new IdentityHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return recordings.computeIfAbsent(type, ignored -> new RecordingBuffer());
        }
    }

    private static final class RecordingBuffer implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new float[]{x, y, z, 0, 0, 0, 0, 0, 0});
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            float[] vertex = vertices.getLast();
            vertex[3] = red;
            vertex[4] = green;
            vertex[5] = blue;
            vertex[6] = alpha;
            return this;
        }

        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) {
            vertices.getLast()[8] = u | v << 16;
            return this;
        }
        @Override public VertexConsumer setUv2(int u, int v) {
            vertices.getLast()[7] = u | v << 16;
            return this;
        }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
