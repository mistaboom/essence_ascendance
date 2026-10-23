package com.mistaboom.essence_ascendance.client.ore;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import com.mistaboom.essence_ascendance.ore.LatentOreItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 26.1 render-state model adapter; host shape, shade, layer and native UVs remain intact. */
public final class LatentOreModels {
    private static final Map<BlockState, BlockStateModel> MODELS = new ConcurrentHashMap<>();
    private static final Map<BlockState, List<BakedQuad>> ITEM_QUADS = new ConcurrentHashMap<>();
    private static final Set<Identifier> WARNED = ConcurrentHashMap.newKeySet();
    private LatentOreModels() {}

    public static void invalidate() { MODELS.clear(); ITEM_QUADS.clear(); WARNED.clear(); }
    public static boolean isOre(BlockState state) { return state.is(EssenceInfuserContent.LATENT_ORE.get()); }
    public static boolean isOre(ItemStack stack) { return LatentOreItems.isOre(stack); }

    public static BlockStateModel forWorld(BlockGetter world, BlockPos pos, BlockStateModel fallback) {
        return LatentOreHost.host(world, pos).map(host -> forHost(host, fallback)).orElse(fallback);
    }

    public static BlockStateModel forItem(ItemStack stack, BlockStateModel fallback) {
        if (!isOre(stack)) return fallback;
        return LatentOreHost.read(stack).map(host -> forHost(host, fallback)).orElse(fallback);
    }

    /** Host quads for the client item's existing model layer; its transforms and foil stay native. */
    public static List<BakedQuad> itemQuads(ItemStack stack, ItemStackRenderState renderState) {
        if (!isOre(stack)) return null;
        BlockState host = LatentOreHost.read(stack).orElse(null);
        if (host == null) return null;
        return itemQuadsForHost(host, renderState);
    }

    /** GUI atlas identity must include the host, since all variants share one authored item model. */
    public static List<BakedQuad> itemQuadsForHost(BlockState host, ItemStackRenderState renderState) {
        List<BakedQuad> quads = ITEM_QUADS.computeIfAbsent(host, LatentOreModels::buildItemQuadsForHost);
        if (quads != null) renderState.appendModelIdentityElement(host);
        return quads;
    }

    private static List<BakedQuad> buildItemQuadsForHost(BlockState host) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(host.getBlock());
        if (!LatentOreSprites.supports(id)) return null;
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(host);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(0), parts);
        List<BakedQuad> result = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            part.getQuads(null).forEach(quad -> result.add(remap(quad, true)));
            for (Direction side : Direction.values())
                part.getQuads(side).forEach(quad -> result.add(remap(quad, true)));
        }
        return List.copyOf(result);
    }

    public static BlockStateModel forHost(BlockState host, BlockStateModel fallback) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(host.getBlock());
        if (!LatentOreSprites.supports(id)) {
            if (WARNED.add(id)) EssenceAscendance.LOGGER.warn(
                    "Latent Ore host {} has no supported prepared model; displaying diagnostic ore", id);
            return fallback;
        }
        return MODELS.computeIfAbsent(host, state -> new HostModel(
                Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)));
    }

    private static TextureAtlasSprite sprite(TextureAtlasSprite original) {
        Identifier nativeId = original.contents().name();
        Identifier generated = LatentOreSprites.prepared(nativeId)
                ? LatentOreSprites.compositeId(nativeId) : LatentOreSprites.UNRESOLVED;
        return Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, generated));
    }

    private static Material.Baked material(Material.Baked original) {
        return new Material.Baked(sprite(original.sprite()), original.forceTranslucent());
    }

    private static BakedQuad remap(BakedQuad source) {
        return remap(source, false);
    }

    private static BakedQuad remap(BakedQuad source, boolean item) {
        TextureAtlasSprite before = source.materialInfo().sprite();
        int quarterTurn = overlayTurn(source);
        Identifier nativeId = before.contents().name();
        Identifier generated = !LatentOreSprites.prepared(nativeId) ? LatentOreSprites.UNRESOLVED
                : quarterTurn == 0
                ? (item ? LatentOreSprites.itemCompositeId(nativeId) : LatentOreSprites.compositeId(nativeId))
                : (item ? LatentOreSprites.orientedItemCompositeId(nativeId, quarterTurn)
                        : LatentOreSprites.orientedCompositeId(nativeId, quarterTurn));
        TextureAtlasSprite after = Minecraft.getInstance().getAtlasManager()
                .get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, generated));
        return remap(source, before, after);
    }

    /** A sideways native face needs only its ore mask rotated; host pixels and UVs stay native. */
    private static int overlayTurn(BakedQuad quad) {
        if (!quad.direction().getAxis().isHorizontal()) return 0;
        for (int a = 0; a < 4; a++) for (int b = a + 1; b < 4; b++) {
            var pa = quad.position(a); var pb = quad.position(b);
            if (Math.abs(pa.y() - pb.y()) < 0.5f || Math.abs(pa.x() - pb.x()) > 0.001f
                    || Math.abs(pa.z() - pb.z()) > 0.001f) continue;
            int top = pa.y() > pb.y() ? a : b, bottom = top == a ? b : a;
            var sprite = quad.materialInfo().sprite();
            float du = (UVPair.unpackU(quad.packedUV(bottom)) - UVPair.unpackU(quad.packedUV(top)))
                    / (sprite.getU1() - sprite.getU0());
            float dv = (UVPair.unpackV(quad.packedUV(bottom)) - UVPair.unpackV(quad.packedUV(top)))
                    / (sprite.getV1() - sprite.getV0());
            if (Math.abs(du) > Math.abs(dv)) return du > 0 ? 1 : -1;
            return dv > 0 ? 0 : 2;
        }
        return 0;
    }

    private static BakedQuad remap(BakedQuad source, TextureAtlasSprite before, TextureAtlasSprite after) {
        long[] uv = new long[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            long packed = source.packedUV(vertex);
            float u = UVPair.unpackU(packed), v = UVPair.unpackV(packed);
            float newU = after.getU0() + (u - before.getU0()) /
                    (before.getU1() - before.getU0()) * (after.getU1() - after.getU0());
            float newV = after.getV0() + (v - before.getV0()) /
                    (before.getV1() - before.getV0()) * (after.getV1() - after.getV0());
            uv[vertex] = UVPair.pack(newU, newV);
        }
        BakedQuad.MaterialInfo sourceInfo = source.materialInfo();
        BakedQuad.MaterialInfo info = new BakedQuad.MaterialInfo(after, sourceInfo.layer(),
                sourceInfo.itemRenderType(), -1, sourceInfo.shade(), sourceInfo.lightEmission());
        return new BakedQuad(source.position0(), source.position1(), source.position2(), source.position3(),
                uv[0], uv[1], uv[2], uv[3], source.direction(), info);
    }

    private static final class HostModel implements BlockStateModel {
        private final BlockStateModel nativeModel;
        private final Map<BlockStateModelPart, BlockStateModelPart> parts = new ConcurrentHashMap<>();
        private HostModel(BlockStateModel nativeModel) { this.nativeModel = nativeModel; }

        @Override public void collectParts(RandomSource random, List<BlockStateModelPart> result) {
            List<BlockStateModelPart> selected = new ArrayList<>();
            nativeModel.collectParts(random, selected);
            selected.forEach(part -> result.add(parts.computeIfAbsent(part, HostPart::new)));
        }

        @Override public Material.Baked particleMaterial() { return material(nativeModel.particleMaterial()); }
        @Override public int materialFlags() { return nativeModel.materialFlags(); }
    }

    private static final class HostPart implements BlockStateModelPart {
        private final BlockStateModelPart nativePart;
        private final Map<BakedQuad, BakedQuad> quads = new ConcurrentHashMap<>();
        private HostPart(BlockStateModelPart nativePart) { this.nativePart = nativePart; }

        @Override public List<BakedQuad> getQuads(Direction side) {
            return nativePart.getQuads(side).stream().map(quad -> quads.computeIfAbsent(quad, LatentOreModels::remap)).toList();
        }
        @Override public boolean useAmbientOcclusion() { return nativePart.useAmbientOcclusion(); }
        @Override public Material.Baked particleMaterial() { return material(nativePart.particleMaterial()); }
        @Override public int materialFlags() { return nativePart.materialFlags(); }
    }
}
