package com.mistaboom.essence_ascendance.client.ore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.visual.TexturePixels;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;

import java.io.IOException;
import java.util.*;

/** CPU-only preparation at the block atlas's stitch boundary, before model baking. */
public final class LatentOreSprites {
    public static final ResourceLocation UNRESOLVED = id("block/latent_ore/unresolved");
    private static final ResourceLocation BASE = id("block/latent_ore/latent_ore_base");
    private static final ResourceLocation TRANSITION = id("block/latent_ore/latent_ore_transition");
    public static final Set<ResourceLocation> BASELINE = Set.of(
            ResourceLocation.withDefaultNamespace("stone"), ResourceLocation.withDefaultNamespace("deepslate"),
            ResourceLocation.withDefaultNamespace("netherrack"), ResourceLocation.withDefaultNamespace("end_stone"));
    private static volatile Set<ResourceLocation> requestedHosts = BASELINE;
    private static volatile Set<ResourceLocation> preparedHosts = Set.of();
    private static volatile Set<ResourceLocation> supportedHosts = Set.of();
    private static volatile Set<ResourceLocation> preparedTextures = Set.of();

    private LatentOreSprites() {}

    public static boolean setHosts(Set<ResourceLocation> hosts) {
        Set<ResourceLocation> next = new HashSet<>(BASELINE);
        next.addAll(hosts);
        next = Set.copyOf(next);
        requestedHosts = next;
        return needsPreparation();
    }

    static boolean needsPreparation() { return !requestedHosts.equals(preparedHosts); }

    public static boolean supports(ResourceLocation host) { return supportedHosts.contains(host); }
    public static boolean prepared(ResourceLocation texture) { return preparedTextures.contains(texture); }
    public static ResourceLocation compositeId(ResourceLocation nativeTexture) {
        return id("block/latent_ore/generated/" + nativeTexture.getNamespace() + "/" + nativeTexture.getPath());
    }

    /** Every image returned here is subsequently owned and closed by the vanilla atlas. */
    public static List<SpriteContents> prepare(ResourceManager resources, List<SpriteContents> original) {
        List<SpriteContents> result = new ArrayList<>(original);
        Set<ResourceLocation> supported = new HashSet<>();
        Set<ResourceLocation> requested = requestedHosts;
        Map<ResourceLocation, SpriteContents> generated = new LinkedHashMap<>();
        try (NativeImage ore = image(resources, BASE); NativeImage transition = image(resources, TRANSITION)) {
            int[] orePixels = pixels(ore), transitionPixels = pixels(transition);
            try (NativeImage missing = new NativeImage(16, 16, false)) {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                    missing.setPixelRGBA(x, y, TexturePixels.argbToAbgr(((x < 8) == (y < 8)) ? 0xFFFF00FF : 0xFF000000));
                generated.put(UNRESOLVED, compose(UNRESOLVED, missing, transition, transitionPixels, ore, orePixels));
            }
            for (ResourceLocation host : requested.stream().sorted().toList()) {
                try {
                    Set<ResourceLocation> textures = textures(resources, host);
                    for (ResourceLocation texture : textures) {
                        if (generated.containsKey(texture)) continue;
                        try (NativeImage nativeImage = image(resources, texture)) {
                            int[] nativePixels = pixels(nativeImage);
                            for (int pixel : nativePixels) if ((pixel >>> 24) != 255)
                                throw new IllegalArgumentException("non-opaque texture " + texture);
                            generated.put(texture, compose(compositeId(texture), nativeImage, transition, transitionPixels, ore, orePixels));
                        }
                    }
                    supported.add(host);
                } catch (RuntimeException | IOException ex) {
                    EssenceAscendance.LOGGER.warn("Latent Ore visual host {} unresolved: {}; using the generated diagnostic ore", host, ex.getMessage());
                }
            }
        } catch (RuntimeException | IOException ex) {
            generated.values().forEach(SpriteContents::close);
            supportedHosts = Set.of();
            preparedTextures = Set.of();
            EssenceAscendance.LOGGER.error("Latent Ore source masks could not be loaded; missing-texture diagnostic will be visible", ex);
            return result;
        }
        // A resource pack cannot accidentally introduce two sprites with the generated identity.
        Set<ResourceLocation> generatedNames = new HashSet<>();
        generated.values().forEach(sprite -> generatedNames.add(sprite.name()));
        result.removeIf(sprite -> {
            if (!generatedNames.contains(sprite.name())) return false;
            sprite.close();
            return true;
        });
        result.addAll(generated.values());
        supportedHosts = Set.copyOf(supported);
        preparedTextures = Set.copyOf(generated.keySet());
        preparedHosts = requested;
        EssenceAscendance.LOGGER.info("Prepared Latent Ore sprites: {}/{} hosts, {} distinct face/particle textures",
                supported.size(), requested.size(), generated.size() - 1);
        return result;
    }

    private static SpriteContents compose(ResourceLocation name, NativeImage host, NativeImage transition,
                                          int[] transitionPixels, NativeImage ore, int[] orePixels) {
        int[] composed = TexturePixels.composeLatentOre(pixels(host), host.getWidth(), host.getHeight(),
                transitionPixels, transition.getWidth(), transition.getHeight(), orePixels, ore.getWidth(), ore.getHeight());
        NativeImage output = new NativeImage(host.getWidth(), host.getHeight(), false);
        try {
            for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                output.setPixelRGBA(x, y, TexturePixels.argbToAbgr(composed[y * output.getWidth() + x]));
            return new SpriteContents(name, new FrameSize(output.getWidth(), output.getHeight()), output, ResourceMetadata.EMPTY);
        } catch (RuntimeException | Error ex) {
            output.close();
            throw ex;
        }
    }

    private static int[] pixels(NativeImage image) {
        int[] result = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            result[y * image.getWidth() + x] = TexturePixels.abgrToArgb(image.getPixelRGBA(x, y));
        return result;
    }

    static NativeImage image(ResourceManager resources, ResourceLocation texture) throws IOException {
        var resource = resources.getResourceOrThrow(texture.withPath("textures/" + texture.getPath() + ".png"));
        if (resource.metadata().getSection(AnimationMetadataSection.SERIALIZER).isPresent())
            throw new IllegalArgumentException("animated texture " + texture + " is outside the static opaque-host renderer");
        try (var stream = resource.open()) { return NativeImage.read(stream); }
    }

    /** Uses Minecraft's own parent/texture-variable resolver; block IDs are never treated as texture paths. */
    static Set<ResourceLocation> textures(ResourceManager resources, ResourceLocation host) throws IOException {
        JsonObject states;
        try (var reader = resources.getResourceOrThrow(host.withPath("blockstates/" + host.getPath() + ".json")).openAsReader()) {
            states = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (states.has("multipart") || !states.has("variants"))
            throw new IllegalArgumentException("requires ordinary blockstate variants, not multipart/custom geometry");
        Set<ResourceLocation> models = new HashSet<>();
        collectModels(states.get("variants"), models);
        if (models.isEmpty()) throw new IllegalArgumentException("blockstate has no resource-backed models");
        Map<ResourceLocation, BlockModel> loaded = new HashMap<>();
        Set<ResourceLocation> loading = new HashSet<>();
        Set<ResourceLocation> textures = new HashSet<>();
        for (ResourceLocation modelId : models) {
            BlockModel model = model(resources, modelId, loaded, loading);
            var elements = model.getElements();
            if (elements.size() != 1) throw new IllegalArgumentException("model is not one opaque cube: " + modelId);
            var element = elements.getFirst();
            if (!element.from.equals(0, 0, 0) || !element.to.equals(16, 16, 16)
                    || element.rotation != null || element.faces.size() != 6)
                throw new IllegalArgumentException("model is not a complete unrotated cube: " + modelId);
            for (var face : element.faces.values()) {
                if (face.tintIndex() != -1) throw new IllegalArgumentException("tinted host faces require a future color adapter: " + modelId);
                float[] uv = face.uv().uvs;
                if (uv == null || Math.abs(uv[2] - uv[0]) != 16 || Math.abs(uv[3] - uv[1]) != 16
                        || Math.min(uv[0], uv[2]) != 0 || Math.min(uv[1], uv[3]) != 0)
                    throw new IllegalArgumentException("cropped or tiled face UV requires a future model adapter: " + modelId);
                textures.add(model.getMaterial(face.texture()).texture());
            }
            textures.add(model.getMaterial("particle").texture());
        }
        return textures;
    }

    private static BlockModel model(ResourceManager resources, ResourceLocation id, Map<ResourceLocation, BlockModel> cache,
                                    Set<ResourceLocation> loading) {
        BlockModel found = cache.get(id);
        if (found != null) return found;
        if (loading.size() >= 32 || !loading.add(id)) throw new IllegalArgumentException("cyclic or excessively deep model parents: " + id);
        try (var reader = resources.getResourceOrThrow(id.withPath("models/" + id.getPath() + ".json")).openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("loader")) throw new IllegalArgumentException("custom model loader: " + id);
            BlockModel value = BlockModel.fromString(json.toString());
            value.resolveParents(parent -> model(resources, parent, cache, loading));
            cache.put(id, value);
            return value;
        } catch (IOException ex) {
            throw new IllegalArgumentException("unavailable model " + id, ex);
        } finally { loading.remove(id); }
    }

    private static void collectModels(JsonElement value, Set<ResourceLocation> models) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> collectModels(child, models));
        else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("model")) models.add(ResourceLocation.parse(object.get("model").getAsString()));
            else object.entrySet().forEach(entry -> collectModels(entry.getValue(), models));
        }
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path); }
}
