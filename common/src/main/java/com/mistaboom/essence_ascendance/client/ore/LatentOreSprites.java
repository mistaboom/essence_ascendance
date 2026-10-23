package com.mistaboom.essence_ascendance.client.ore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.ore.LatentOreItems;
import com.mistaboom.essence_ascendance.visual.TexturePixels;
import com.mojang.blaze3d.platform.NativeImage;
//? if <26.1.2 {
import net.minecraft.client.renderer.block.model.BlockModel;
//? }
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
//? if <26.1.2 {
import net.minecraft.server.packs.resources.ResourceMetadata;
//? }

import java.io.IOException;
import java.util.*;

/** One CPU-only atlas preparation path; only native pixel and model APIs vary by target. */
public final class LatentOreSprites {
    public static final ResourceLocation UNRESOLVED = id("block/latent_ore/unresolved");
    private static final ResourceLocation BASE = id("block/latent_ore/latent_ore_base");
    private static final ResourceLocation TRANSITION = id("block/latent_ore/latent_ore_transition");
    public static final Set<ResourceLocation> BASELINE = Set.of(
            ResourceLocation.withDefaultNamespace("stone"), ResourceLocation.withDefaultNamespace("deepslate"),
            ResourceLocation.withDefaultNamespace("netherrack"), ResourceLocation.withDefaultNamespace("end_stone"));
    private static volatile Set<ResourceLocation> catalogHosts = Set.of();
    private static volatile Set<ResourceLocation> preparedHosts = Set.of();
    private static volatile Set<ResourceLocation> supportedHosts = Set.of();
    private static volatile Set<ResourceLocation> preparedTextures = Set.of();

    private LatentOreSprites() {}

    public static boolean setHosts(Set<ResourceLocation> hosts) {
        catalogHosts = Set.copyOf(hosts);
        return needsPreparation();
    }

    /** Every registered /give alias needs atlas faces, even outside this world's ore catalog. */
    static Set<ResourceLocation> selectedHosts(Set<ResourceLocation> catalog, Set<ResourceLocation> registered) {
        Set<ResourceLocation> result = new HashSet<>(BASELINE);
        result.addAll(catalog);
        result.addAll(registered);
        return Set.copyOf(result);
    }

    private static Set<ResourceLocation> selectedHosts() {
        return selectedHosts(catalogHosts, LatentOreItems.registeredHostIds());
    }

    static boolean needsPreparation() { return !selectedHosts().equals(preparedHosts); }

    public static boolean supports(ResourceLocation host) { return supportedHosts.contains(host); }
    public static boolean prepared(ResourceLocation texture) { return preparedTextures.contains(texture); }
    public static ResourceLocation compositeId(ResourceLocation nativeTexture) {
        return id("block/latent_ore/generated/" + nativeTexture.getNamespace() + "/" + nativeTexture.getPath());
    }
    public static ResourceLocation itemCompositeId(ResourceLocation nativeTexture) {
        return id("block/latent_ore/item/" + nativeTexture.getNamespace() + "/" + nativeTexture.getPath());
    }

    // World/item face sprite with unchanged host pixels and an upright ore mask.
    //? if >=26.1.2 {
    /*public static ResourceLocation orientedCompositeId(ResourceLocation nativeTexture, int quarterTurn) {
        String orientation = switch (quarterTurn) {
            case 1 -> "u_down";
            case -1 -> "u_up";
            case 2 -> "v_up";
            default -> throw new IllegalArgumentException("Overlay turn must be -1, 1 or 2");
        };
        return id("block/latent_ore/generated/face_" + orientation
                + "/" + nativeTexture.getNamespace() + "/" + nativeTexture.getPath());
    }*/
    /*public static ResourceLocation orientedItemCompositeId(ResourceLocation nativeTexture, int quarterTurn) {
        String orientation = switch (quarterTurn) {
            case 1 -> "u_down";
            case -1 -> "u_up";
            case 2 -> "v_up";
            default -> throw new IllegalArgumentException("Overlay turn must be -1, 1 or 2");
        };
        return id("block/latent_ore/item/face_" + orientation
                + "/" + nativeTexture.getNamespace() + "/" + nativeTexture.getPath());
    }*/
    //? }

    /** Every image returned here is subsequently owned and closed by the vanilla atlas. */
    public static List<SpriteContents> prepare(ResourceManager resources, List<SpriteContents> original) {
        List<SpriteContents> result = new ArrayList<>(original);
        Set<ResourceLocation> supported = new HashSet<>();
        Set<ResourceLocation> nativeTextures = new HashSet<>();
        Set<ResourceLocation> requested = selectedHosts();
        Map<ResourceLocation, SpriteContents> generated = new LinkedHashMap<>();
        try (NativeImage ore = image(resources, BASE); NativeImage transition = image(resources, TRANSITION)) {
            int[] orePixels = pixels(ore), transitionPixels = pixels(transition);
            try (NativeImage missing = new NativeImage(16, 16, false)) {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                    //? if >=26.1.2
                    /*missing.setPixel(x, y, ((x < 8) == (y < 8)) ? 0xFFFF00FF : 0xFF000000);*/
                    //? if <26.1.2
                    missing.setPixelRGBA(x, y, ((x < 8) == (y < 8)) ? 0xFFFF00FF : 0xFF000000);
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
                            generated.put(itemCompositeId(texture), compose(itemCompositeId(texture),
                                    nativeImage, transition, transitionPixels, ore, orePixels, 0));
                            //? if >=26.1.2 {
                            /*for (int turn : new int[]{-1, 1, 2})
                                generated.put(orientedCompositeId(texture, turn), compose(orientedCompositeId(texture, turn),
                                        nativeImage, transition, transitionPixels, ore, orePixels, turn));*/
                            /*for (int turn : new int[]{-1, 1, 2})
                                generated.put(orientedItemCompositeId(texture, turn), compose(orientedItemCompositeId(texture, turn),
                                        nativeImage, transition, transitionPixels, ore, orePixels, turn));*/
                            //? }
                            nativeTextures.add(texture);
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
        //? if >=26.1.2
        /*preparedTextures = Set.copyOf(nativeTextures);*/
        //? if <26.1.2
        preparedTextures = Set.copyOf(generated.keySet());
        preparedHosts = requested;
        //? if >=26.1.2 {
        /*EssenceAscendance.LOGGER.info("Prepared Latent Ore sprites: {}/{} hosts, {} distinct face/particle textures with upright overlays",
                supported.size(), requested.size(), nativeTextures.size());*/
        //? }
        //? if <26.1.2 {
        EssenceAscendance.LOGGER.info("Prepared Latent Ore sprites: {}/{} hosts, {} distinct face/particle textures",
                supported.size(), requested.size(), nativeTextures.size());
        //? }
        return result;
    }

    private static SpriteContents compose(ResourceLocation name, NativeImage host, NativeImage transition,
                                          int[] transitionPixels, NativeImage ore, int[] orePixels) {
        return compose(name, host, transition, transitionPixels, ore, orePixels, 0);
    }

    private static SpriteContents compose(ResourceLocation name, NativeImage host, NativeImage transition,
                                          int[] transitionPixels, NativeImage ore, int[] orePixels, int quarterTurn) {
        int[] composed = TexturePixels.composeLatentOre(pixels(host), host.getWidth(), host.getHeight(),
                transitionPixels, transition.getWidth(), transition.getHeight(), orePixels, ore.getWidth(), ore.getHeight(),
                quarterTurn);
        return sprite(name, host, composed);
    }

    private static SpriteContents sprite(ResourceLocation name, NativeImage host, int[] composed) {
        NativeImage output = new NativeImage(host.getWidth(), host.getHeight(), false);
        try {
            for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                //? if >=26.1.2
                /*output.setPixel(x, y, composed[y * output.getWidth() + x]);*/
                //? if <26.1.2
                output.setPixelRGBA(x, y, TexturePixels.argbToAbgr(composed[y * output.getWidth() + x]));
            //? if >=26.1.2
            /*return new SpriteContents(name, new FrameSize(output.getWidth(), output.getHeight()), output);*/
            //? if <26.1.2
            return new SpriteContents(name, new FrameSize(output.getWidth(), output.getHeight()), output, ResourceMetadata.EMPTY);
        } catch (RuntimeException | Error ex) {
            output.close();
            throw ex;
        }
    }

    private static int[] pixels(NativeImage image) {
        int[] result = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            //? if >=26.1.2
            /*result[y * image.getWidth() + x] = image.getPixel(x, y);*/
            //? if <26.1.2
            result[y * image.getWidth() + x] = TexturePixels.abgrToArgb(image.getPixelRGBA(x, y));
        return result;
    }

    static NativeImage image(ResourceManager resources, ResourceLocation texture) throws IOException {
        var resource = resources.getResourceOrThrow(texture.withPath("textures/" + texture.getPath() + ".png"));
        //? if >=26.1.2
        /*if (resource.metadata().getSection(AnimationMetadataSection.TYPE).isPresent())*/
        //? if <26.1.2
        if (resource.metadata().getSection(AnimationMetadataSection.SERIALIZER).isPresent())
            throw new IllegalArgumentException("animated texture " + texture + " is outside the static opaque-host renderer");
        try (var stream = resource.open()) { return NativeImage.read(stream); }
    }

    /** Resolve ordinary cube JSON and texture variables; block IDs are never texture paths. */
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
        Set<ResourceLocation> textures = new HashSet<>();
        //? if <26.1.2 {
        Map<ResourceLocation, BlockModel> loaded = new HashMap<>();
        Set<ResourceLocation> loading = new HashSet<>();
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
        //? }
        //? if >=26.1.2 {
        /*Map<ResourceLocation, JsonObject> loaded = new HashMap<>();
        Set<ResourceLocation> loading = new HashSet<>();
        for (ResourceLocation modelId : models) {
            JsonObject model = model(resources, modelId, loaded, loading);
            if (!model.has("elements") || model.getAsJsonArray("elements").size() != 1)
                throw new IllegalArgumentException("model is not one opaque cube: " + modelId);
            JsonObject element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
            if (!corner(element, "from", 0) || !corner(element, "to", 16)
                    || element.has("rotation") || !element.has("faces")
                    || element.getAsJsonObject("faces").size() != 6)
                throw new IllegalArgumentException("model is not a complete unrotated cube: " + modelId);
            JsonObject slots = model.getAsJsonObject("textures");
            if (slots == null) throw new IllegalArgumentException("model has no texture slots: " + modelId);
            for (var value : element.getAsJsonObject("faces").entrySet()) {
                JsonObject face = value.getValue().getAsJsonObject();
                if (face.has("tintindex") && face.get("tintindex").getAsInt() != -1)
                    throw new IllegalArgumentException("tinted host faces require a future color adapter: " + modelId);
                if (face.has("uv") && !fullUv(face.getAsJsonArray("uv")))
                    throw new IllegalArgumentException("cropped or tiled face UV requires a future model adapter: " + modelId);
                textures.add(texture(face.get("texture").getAsString(), slots, new HashSet<>()));
            }
            textures.add(texture("#particle", slots, new HashSet<>()));
        }*/
        //? }
        return textures;
    }

    //? if >=26.1.2 {
    /*private static boolean corner(JsonObject element, String name, int expected) {
        if (!element.has(name) || element.getAsJsonArray(name).size() != 3) return false;
        for (var component : element.getAsJsonArray(name))
            if (component.getAsFloat() != expected) return false;
        return true;
    }

    private static boolean fullUv(com.google.gson.JsonArray uv) {
        if (uv.size() != 4) return false;
        float u0 = uv.get(0).getAsFloat(), v0 = uv.get(1).getAsFloat();
        float u1 = uv.get(2).getAsFloat(), v1 = uv.get(3).getAsFloat();
        return Math.abs(u1 - u0) == 16 && Math.abs(v1 - v0) == 16
                && Math.min(u0, u1) == 0 && Math.min(v0, v1) == 0;
    }

    private static ResourceLocation texture(String value, JsonObject slots, Set<String> seen) {
        if (!value.startsWith("#")) return ResourceLocation.parse(value);
        String slot = value.substring(1);
        if (!seen.add(slot) || !slots.has(slot))
            throw new IllegalArgumentException("missing or cyclic model texture slot: " + slot);
        return texture(slots.get(slot).getAsString(), slots, seen);
    }*/
    //? }

    //? if <26.1.2 {
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
        //? }
        //? if >=26.1.2 {
    /*private static JsonObject model(ResourceManager resources, ResourceLocation id, Map<ResourceLocation, JsonObject> cache,
                                     Set<ResourceLocation> loading) {
        JsonObject found = cache.get(id);
        if (found != null) return found;
        if (loading.size() >= 32 || !loading.add(id)) throw new IllegalArgumentException("cyclic or excessively deep model parents: " + id);
        try (var reader = resources.getResourceOrThrow(id.withPath("models/" + id.getPath() + ".json")).openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("loader")) throw new IllegalArgumentException("custom model loader: " + id);
            JsonObject value = json.has("parent")
                    ? model(resources, ResourceLocation.parse(json.get("parent").getAsString()), cache, loading).deepCopy()
                    : new JsonObject();
            for (var field : json.entrySet()) {
                if (field.getKey().equals("parent")) continue;
                if (field.getKey().equals("textures") && value.has("textures")) {
                    JsonObject merged = value.getAsJsonObject("textures").deepCopy();
                    field.getValue().getAsJsonObject().entrySet().forEach(slot ->
                            merged.add(slot.getKey(), slot.getValue().deepCopy()));
                    value.add("textures", merged);
                } else value.add(field.getKey(), field.getValue().deepCopy());
            }
            cache.put(id, value);
            return value;
        } catch (IOException ex) {
            throw new IllegalArgumentException("unavailable model " + id, ex);
        } finally { loading.remove(id); }
    }*/
    //? }

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
