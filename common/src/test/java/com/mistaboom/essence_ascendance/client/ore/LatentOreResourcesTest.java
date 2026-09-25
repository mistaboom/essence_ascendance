package com.mistaboom.essence_ascendance.client.ore;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Small resource-pack fixtures exercise actual Minecraft model-parent resolution. */
public final class LatentOreResourcesTest {
    private static int assertions;
    private static final ResourceLocation HOST = id("native_rock");
    private static final String CUBE = """
            {"textures":{"particle":"#side"},"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
              "down":{"texture":"#end"},"up":{"texture":"#end"},
              "north":{"texture":"#side"},"south":{"texture":"#side"},
              "west":{"texture":"#side"},"east":{"texture":"#side"}
            }}]}
            """;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Fixture fixture = ordinaryHost();
        Set<ResourceLocation> expected = Set.of(id("geology/layer_side"), id("geology/layer_end"));
        check(LatentOreSprites.textures(fixture, HOST).equals(expected),
                "Parent indirection and face-specific variables must resolve actual resources");
        fixture.json("models/block/native_rock.json", """
                {"parent":"test:block/layer","textures":{"side":"test:pack/override_side","end":"test:pack/override_end"}}
                """);
        check(LatentOreSprites.textures(fixture, HOST).equals(Set.of(id("pack/override_side"), id("pack/override_end"))),
                "Reload must resolve active resource replacements without stale model cache");
        fixture.json("blockstates/native_rock.json", """
                {"variants":{"axis=y":{"model":"test:block/native_rock"},
                "axis=x":{"model":"test:block/native_rock","x":90,"y":90},
                "axis=z":[{"model":"test:block/native_rock","x":90,"weight":2},{"model":"test:block/layer","weight":1}]}}
                """);
        check(LatentOreSprites.textures(fixture, HOST).size() == 4,
                "All ordinary orientation/weighted variants must prepare their face resources");

        rejected("multipart", f -> f.json("blockstates/native_rock.json", "{\"multipart\":[]}"), "ordinary blockstate");
        rejected("custom loader", f -> f.json("models/block/native_rock.json", "{\"loader\":\"test:custom\"}"), "custom model loader");
        rejected("non-cube", f -> f.json("models/block/cube.json", CUBE.replace("[16,16,16]", "[16,8,16]")), "complete unrotated cube");
        rejected("biome tint", f -> f.json("models/block/cube.json", CUBE.replace("\"texture\":\"#side\"", "\"texture\":\"#side\",\"tintindex\":0")), "tinted host");
        rejected("cropped UV", f -> f.json("models/block/cube.json", CUBE.replace("\"texture\":\"#side\"", "\"texture\":\"#side\",\"uv\":[0,0,8,16]")), "cropped or tiled");
        rejected("parent cycle", f -> f.json("models/block/cube.json", "{\"parent\":\"test:block/native_rock\"}"), "cyclic");
        rejected("missing model", f -> f.resources.remove(id("models/block/cube.json")), "unavailable model");

        Fixture animated = new Fixture();
        animated.resources.put(id("textures/animated.png"), new Resource(null,
                () -> { throw new AssertionError("Animated texture must be rejected before decoding its strip as one face"); },
                () -> ResourceMetadata.fromJsonStream(bytes("{\"animation\":{\"frametime\":2}}"))));
        boolean rejectedAnimation = false;
        try { LatentOreSprites.image(animated, id("animated")); }
        catch (IllegalArgumentException ex) { rejectedAnimation = ex.getMessage().contains("animated texture"); }
        check(rejectedAnimation, "Animated sources need explicit diagnostic before native decoding");
        check(LatentOreSprites.compositeId(id("geology/layer_side"))
                        .equals(ResourceLocation.parse("essence_ascendance:block/latent_ore/generated/test/geology/layer_side")),
                "Generated sprite identity must depend on native resource, not dimension or position");
        System.out.println("LatentOreResourcesTest: " + assertions + " checks PASS");
    }

    private static Fixture ordinaryHost() {
        Fixture fixture = new Fixture();
        fixture.json("blockstates/native_rock.json", "{\"variants\":{\"\":{\"model\":\"test:block/native_rock\"}}}");
        fixture.json("models/block/native_rock.json", "{\"parent\":\"test:block/layer\"}");
        fixture.json("models/block/layer.json", """
                {"parent":"test:block/cube","textures":{"side":"test:geology/layer_side","end":"test:geology/layer_end"}}
                """);
        fixture.json("models/block/cube.json", CUBE);
        return fixture;
    }

    private static void rejected(String caseName, java.util.function.Consumer<Fixture> mutation, String reason) throws Exception {
        Fixture fixture = ordinaryHost();
        mutation.accept(fixture);
        boolean rejected = false;
        try { LatentOreSprites.textures(fixture, HOST); }
        catch (IllegalArgumentException ex) { rejected = ex.getMessage().contains(reason); }
        check(rejected, "Useful diagnostic for " + caseName);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    private static ByteArrayInputStream bytes(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture implements ResourceManager {
        private final Map<ResourceLocation, Resource> resources = new HashMap<>();
        private void json(String path, String content) {
            resources.put(id(path), new Resource(null, () -> bytes(content)));
        }
        @Override public Set<String> getNamespaces() { return Set.of("test"); }
        @Override public Optional<Resource> getResource(ResourceLocation id) { return Optional.ofNullable(resources.get(id)); }
        @Override public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).stream().toList(); }
        @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> predicate) {
            return resources.entrySet().stream().filter(entry -> entry.getKey().getPath().startsWith(path)
                    && predicate.test(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> predicate) {
            return listResources(path, predicate).entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> List.of(entry.getValue())));
        }
        @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
    }
}
