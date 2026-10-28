package com.mistaboom.essence_ascendance.client.ore;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Quadrant;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native CPU atlas/quad sampling; deliberately does not claim GPU or GUI visual acceptance. */
public final class LatentOreNativePixelsTest {
    private static int checks;
    private static final ModelBaker.Interner INTERNER = new ModelBaker.Interner() {
        @Override public Vector3fc vector(Vector3fc value) { return value; }
        @Override public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo value) { return value; }
    };

    private LatentOreNativePixelsTest() {}

    public static void verify(ResourceManager resources, int[] ore, int[] transition) throws Exception {
        List<SpriteContents> nativeFaces = new ArrayList<>();
        List<Identifier> textures = List.of(id("stone"), id("deepslate"), id("deepslate_top"),
                id("netherrack"), id("end_stone"));
        for (Identifier texture : textures) {
            NativeImage image = LatentOreSprites.image(resources, texture);
            nativeFaces.add(new SpriteContents(texture, new FrameSize(image.getWidth(), image.getHeight()), image));
        }
        List<SpriteContents> prepared = LatentOreSprites.prepare(resources, nativeFaces);
        try {
            require(prepared.size() == nativeFaces.size() + 1 + 4 * textures.size(),
                    "Five native faces need four distinct overlay orientations each, with no inventory duplicates");
            require(prepared.stream().map(SpriteContents::name).distinct().count() == prepared.size(),
                    "Prepared sprites must have unique atlas identities");
            // Use the same native stitcher and atlas-sprite factory as SpriteLoader, without
            // constructing Minecraft, a window or a GPU device just to read Options.
            Stitcher<SpriteContents> stitcher = new Stitcher<>(4096, 4096, 1, 0);
            prepared.forEach(stitcher::registerSprite);
            stitcher.stitch();
            Method factory = SpriteLoader.class.getDeclaredMethod("getStitchedSprites", Stitcher.class, int.class, int.class);
            factory.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Identifier, TextureAtlasSprite> sprites = (Map<Identifier, TextureAtlasSprite>) factory.invoke(
                    new SpriteLoader(TextureAtlas.LOCATION_BLOCKS, 4096), stitcher, stitcher.getWidth(), stitcher.getHeight());
            prepared.forEach(sprite -> sprite.increaseMipLevel(1));
            Field mipImages = SpriteContents.class.getDeclaredField("byMipLevel");
            mipImages.setAccessible(true);
            Method remap = LatentOreModels.class.getDeclaredMethod("remap", BakedQuad.class,
                    TextureAtlasSprite.class, TextureAtlasSprite.class);
            remap.setAccessible(true);

            // Assemble CPU atlas pixels at native stitch positions, including native mip data.
            // This catches a correct SpriteContents paired with UVs into a different atlas tile.
            int[][] atlas = {new int[stitcher.getWidth() * stitcher.getHeight()],
                    new int[(stitcher.getWidth() / 2) * (stitcher.getHeight() / 2)]};
            Map<Identifier, NativeImage[]> images = new HashMap<>();
            for (TextureAtlasSprite sprite : sprites.values()) {
                NativeImage[] levels = (NativeImage[]) mipImages.get(sprite.contents());
                images.put(sprite.contents().name(), levels);
                for (int mip = 0; mip < 2; mip++) {
                    NativeImage image = levels[mip];
                    int width = stitcher.getWidth() >> mip;
                    int ox = Math.round(sprite.getU0() * stitcher.getWidth()) >> mip;
                    int oy = Math.round(sprite.getV0() * stitcher.getHeight()) >> mip;
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                        atlas[mip][(oy + y) * width + ox + x] = image.getPixel(x, y);
                }
            }
            for (Identifier texture : textures) {
                TextureAtlasSprite before = sprites.get(texture);
                TextureAtlasSprite after = sprites.get(LatentOreSprites.itemCompositeId(texture));
                require(after != null, texture + " missing prepared inventory sprite");
                NativeImage[] base = images.get(texture), composed = images.get(after.contents().name());
                for (Direction face : Direction.values()) {
                    BakedQuad original = FaceBakery.bakeQuad(INTERNER, new Vector3f(), new Vector3f(16),
                            new CuboidFace.UVs(0, 0, 16, 16), Quadrant.R0,
                            new BakedQuad.MaterialInfo(before, ChunkSectionLayer.SOLID, null, -1, true, 0),
                            face, BlockModelRotation.IDENTITY, null);
                    BakedQuad actual = (BakedQuad) remap.invoke(null, original, before, after);
                    require(actual.materialInfo().sprite() == after && actual.materialInfo().tintIndex() == -1,
                            texture + " face must use its composited, untinted material");
                    for (int size : new int[]{16, 8}) {
                        int core = 0, ring = 0, changed = 0, mipChanged = 0;
                        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                            float s = (x + .5f) / size, t = (y + .5f) / size;
                            float[] sourceUv = uv(original, s, t), actualUv = uv(actual, s, t);
                            int px = (int) (sourceUv[0] * stitcher.getWidth()) - Math.round(before.getU0() * stitcher.getWidth());
                            int py = (int) (sourceUv[1] * stitcher.getHeight()) - Math.round(before.getV0() * stitcher.getHeight());
                            require(px >= 0 && px < 16 && py >= 0 && py < 16, "Native cube UV must stay on its full face");
                            int sampled = sample(atlas[0], stitcher.getWidth(), stitcher.getHeight(), actualUv);
                            require(sampled == composed[0].getPixel(px, py), texture + " quad samples the wrong atlas tile/pixel");
                            int index = py * 16 + px;
                            if ((ore[index] >>> 24) == 255 && sampled == ore[index]) core++;
                            if ((ore[index] >>> 24) == 0 && (transition[index] >>> 24) == 255
                                    && sampled != base[0].getPixel(px, py)) ring++;
                            if (sampled != base[0].getPixel(px, py)) changed++;
                            int mipPixel = sample(atlas[1], stitcher.getWidth() / 2, stitcher.getHeight() / 2, actualUv);
                            require(mipPixel == composed[1].getPixel(px / 2, py / 2), texture + " native mip samples the wrong tile");
                            if (mipPixel != base[1].getPixel(px / 2, py / 2)) mipChanged++;
                        }
                        require(core >= 8 && ring >= 8 && changed >= 24 && mipChanged >= 24,
                                texture + " " + face + " " + size + "px face lost the ore or transition"
                                        + " (core=" + core + ", ring=" + ring + ", changed=" + changed + ", mip=" + mipChanged + ")");
                    }
                }
            }
            System.out.println("LatentOreNativePixelsTest: " + checks
                    + " native preparation/stitch/mip/baked-quad pixel checks PASS; no GPU/GUI visual claim");
        } finally {
            prepared.forEach(SpriteContents::close);
        }
    }

    private static float[] uv(BakedQuad quad, float s, float t) {
        float[] weights = {(1 - s) * (1 - t), (1 - s) * t, s * t, s * (1 - t)};
        float u = 0, v = 0;
        for (int i = 0; i < 4; i++) {
            u += UVPair.unpackU(quad.packedUV(i)) * weights[i];
            v += UVPair.unpackV(quad.packedUV(i)) * weights[i];
        }
        return new float[]{u, v};
    }

    private static int sample(int[] atlas, int width, int height, float[] uv) {
        int x = (int) (uv[0] * width), y = (int) (uv[1] * height);
        require(x >= 0 && x < width && y >= 0 && y < height, "Quad UV escaped the native atlas");
        return atlas[y * width + x];
    }

    private static Identifier id(String texture) { return Identifier.withDefaultNamespace("block/" + texture); }
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
