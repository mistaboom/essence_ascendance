package com.mistaboom.essence_ascendance.client.ore;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared baked geometry for both loaders. Work is cached by saved host state and native quad. */
public final class LatentOreModels {
    private static final Map<BlockState, BakedModel> MODELS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
    private LatentOreModels() {}

    public static void invalidate() { MODELS.clear(); WARNED.clear(); }
    public static boolean isOre(BlockState state) { return state.is(EssenceInfuserContent.LATENT_ORE.get()); }
    public static boolean isOre(ItemStack stack) { return stack.is(EssenceInfuserContent.LATENT_ORE.get().asItem()); }

    public static BakedModel forWorld(BlockGetter world, BlockPos pos, BakedModel fallback) {
        return LatentOreHost.host(world, pos).map(host -> forHost(host, fallback)).orElse(fallback);
    }

    public static BakedModel forItem(ItemStack stack, BakedModel fallback) {
        if (!isOre(stack)) return fallback;
        return LatentOreHost.read(stack).map(host -> forHost(host, fallback)).orElse(fallback);
    }

    public static BakedModel forHost(BlockState host, BakedModel fallback) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(host.getBlock());
        if (!LatentOreSprites.supports(id)) {
            if (WARNED.add(id)) EssenceAscendance.LOGGER.warn("Latent Ore host {} has no supported prepared model; displaying diagnostic ore", id);
            return fallback;
        }
        return MODELS.computeIfAbsent(host, state -> new HostModel(state,
                Minecraft.getInstance().getBlockRenderer().getBlockModel(state), fallback));
    }

    private static final class HostModel implements BakedModel {
        private final BlockState host;
        private final BakedModel nativeModel;
        private final BakedModel fallback;
        private final Map<BakedQuad, BakedQuad> quads = new ConcurrentHashMap<>();
        private HostModel(BlockState host, BakedModel nativeModel, BakedModel fallback) {
            this.host = host;
            this.nativeModel = nativeModel;
            this.fallback = fallback;
        }

        @Override public List<BakedQuad> getQuads(BlockState ignored, Direction side, RandomSource random) {
            return nativeModel.getQuads(host, side, random).stream().map(quad -> quads.computeIfAbsent(quad, this::remap)).toList();
        }

        private BakedQuad remap(BakedQuad source) {
            TextureAtlasSprite before = source.getSprite();
            TextureAtlasSprite after = sprite(before);
            int[] data = source.getVertices().clone();
            int stride = data.length / 4;
            // BLOCK vertex format: xyz, color, uv, light, normal. Preserve all geometry/shading fields.
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                float u = Float.intBitsToFloat(data[offset + 4]);
                float v = Float.intBitsToFloat(data[offset + 5]);
                data[offset + 4] = Float.floatToRawIntBits(after.getU0()
                        + (u - before.getU0()) / (before.getU1() - before.getU0()) * (after.getU1() - after.getU0()));
                data[offset + 5] = Float.floatToRawIntBits(after.getV0()
                        + (v - before.getV0()) / (before.getV1() - before.getV0()) * (after.getV1() - after.getV0()));
            }
            return new BakedQuad(data, -1, source.getDirection(), after, source.isShade());
        }

        private TextureAtlasSprite sprite(TextureAtlasSprite original) {
            ResourceLocation nativeId = original.contents().name();
            ResourceLocation generated = LatentOreSprites.prepared(nativeId)
                    ? LatentOreSprites.compositeId(nativeId) : LatentOreSprites.UNRESOLVED;
            return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(generated);
        }

        @Override public boolean useAmbientOcclusion() { return nativeModel.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return sprite(nativeModel.getParticleIcon()); }
        @Override public ItemTransforms getTransforms() { return fallback.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
