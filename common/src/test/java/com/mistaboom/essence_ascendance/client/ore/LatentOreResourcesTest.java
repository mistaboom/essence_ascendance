package com.mistaboom.essence_ascendance.client.ore;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.verification.TargetTestInputs;
import com.mistaboom.essence_ascendance.visual.TexturePixels;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
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
        Set<ResourceLocation> selected = LatentOreSprites.selectedHosts(
                Set.of(id("world_ore_host")), Set.of(id("registered_give_host")));
        check(selected.containsAll(LatentOreSprites.BASELINE)
                        && selected.contains(id("world_ore_host"))
                        && selected.contains(id("registered_give_host"))
                        && selected.size() == LatentOreSprites.BASELINE.size() + 2,
                "Registered /give hosts need prepared item faces even outside the selected world ore catalog");
        nativeTargetResources();
        nativeAliasModelContract();
        System.out.println("LatentOreResourcesTest: " + assertions + " checks PASS");
    }

    /** Real version-scoped Minecraft assets guard the base item faces, especially End Stone. */
    private static void nativeTargetResources() throws Exception {
        Map<ResourceLocation, Set<ResourceLocation>> expected = Map.of(
                vanilla("stone"), Set.of(vanilla("block/stone")),
                vanilla("deepslate"), Set.of(vanilla("block/deepslate"), vanilla("block/deepslate_top")),
                vanilla("netherrack"), Set.of(vanilla("block/netherrack")),
                vanilla("end_stone"), Set.of(vanilla("block/end_stone")));
        BufferedImage ore = authoredMask("latent_ore_base");
        BufferedImage transition = authoredMask("latent_ore_transition");
        int[] orePixels = pixels(ore), transitionPixels = pixels(transition);
        try (JarFile jar = TargetTestInputs.minecraftClientResources()) {
            NativeResources resources = new NativeResources(jar);
            for (var host : expected.entrySet()) {
                Set<ResourceLocation> resolved = LatentOreSprites.textures(resources, host.getKey());
                check(resolved.equals(host.getValue()), host.getKey() + " must resolve its exact native block faces, not suspicious sand");
                for (ResourceLocation texture : resolved) {
                    BufferedImage nativeFace = resources.image(texture);
                    check(nativeFace.getWidth() == ore.getWidth() && nativeFace.getHeight() == ore.getHeight(),
                            texture + " face and authored masks must use compatible dimensions");
                    int[] hostPixels = pixels(nativeFace);
                    int[] output = TexturePixels.composeLatentOre(hostPixels, nativeFace.getWidth(), nativeFace.getHeight(),
                            transitionPixels, transition.getWidth(), transition.getHeight(),
                            orePixels, ore.getWidth(), ore.getHeight());
                    int oreCore = 0, transitionRing = 0, uncovered = 0;
                    for (int i = 0; i < output.length; i++) {
                        int oreAlpha = orePixels[i] >>> 24, transitionAlpha = transitionPixels[i] >>> 24;
                        if (oreAlpha == 255 && output[i] == orePixels[i]) oreCore++;
                        if (oreAlpha == 0 && transitionAlpha == 255 && output[i] != hostPixels[i]) transitionRing++;
                        if (oreAlpha == 0 && transitionAlpha == 0 && output[i] == hostPixels[i]) uncovered++;
                    }
                    check(oreCore >= 8 && transitionRing >= 8 && uncovered >= 8,
                            texture + " item face needs visible opaque ore and transition while uncovered host pixels remain native"
                                    + " (ore=" + oreCore + ", transition=" + transitionRing + ", host=" + uncovered + ")");
                }
            }
            //? if >=26.1.2
            /*LatentOreNativePixelsTest.verify(resources, orePixels, transitionPixels);*/
        }
    }

    /** 1.21's native item loop must load the authored alias model; 26 uses ITEM_MODEL instead. */
    private static void nativeAliasModelContract() throws Exception {
        String version = System.getProperty("essence.minecraft");
        check(version != null, "Gradle must supply the exact target Minecraft version");
        try (InputStream input = LatentOreResourcesTest.class.getResourceAsStream("/essence_ascendance.mixins.json")) {
            check(input != null, "Processed target mixin configuration must be present");
            var config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean registered = config.getAsJsonArray("client").asList().stream()
                    .anyMatch(entry -> entry.getAsString().equals("LatentOreModelBakeryMixin"));
            check(registered == version.equals("1.21.1"), "ModelBakery hook must be required only for 1.21.1");
        }
        String bakery = "net/minecraft/client/resources/model/ModelBakery";
        String aliasMixin = "com/mistaboom/essence_ascendance/mixin/LatentOreModelBakeryMixin.class";
        try (InputStream input = LatentOreResourcesTest.class.getClassLoader().getResourceAsStream(aliasMixin)) {
            check((input != null) == version.equals("1.21.1"), "Only 1.21.1 may compile the alias ModelBakery hook");
            if (input != null) {
                ClassNode type = new ClassNode();
                new ClassReader(input).accept(type, 0);
                var handler = type.methods.stream().filter(method -> method.name.equals("essence$aliasModel")).findFirst()
                        .orElseThrow(() -> new AssertionError("Alias model redirect handler missing"));
                boolean gated = false, adaptive = false;
                for (var instruction : handler.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("com/mistaboom/essence_ascendance/ore/LatentOreItems")
                            && call.name.equals("isRegisteredAlias")) gated = true;
                    if (instruction instanceof LdcInsnNode constant && constant.cst.equals("item/adaptive_latent_ore")) adaptive = true;
                }
                check(gated && adaptive, "Alias model redirect must use registered identities and authored adaptive geometry");
                for (String loader : List.of("fabric", "neoforge")) {
                    try (InputStream nativeClass = TargetTestInputs.nativeClass(loader, bakery)) {
                        ClassNode nativeType = new ClassNode();
                        new ClassReader(nativeClass).accept(nativeType, 0);
                        long calls = nativeType.methods.stream().filter(method -> method.name.equals("loadItemModelAndDependencies"))
                                .flatMap(method -> method.instructions == null ? Stream.empty() :
                                        java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                                .filter(instruction -> instruction instanceof MethodInsnNode call
                                        && call.owner.equals(bakery) && call.name.equals("getModel")
                                        && call.desc.equals("(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/UnbakedModel;"))
                                .count();
                        check(calls == 1, loader + " 1.21.1 native ModelBakery must call the exact required getModel target once");
                    }
                }
            }
        }
    }

    private static BufferedImage authoredMask(String name) throws IOException {
        String path = "/assets/essence_ascendance/textures/block/latent_ore/" + name + ".png";
        try (InputStream input = LatentOreResourcesTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Authored ore mask missing from target output: " + path);
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("Invalid authored ore mask: " + path);
            return image;
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static ResourceLocation vanilla(String path) { return ResourceLocation.withDefaultNamespace(path); }

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

    private static final class NativeResources implements ResourceManager {
        private final JarFile jar;
        private NativeResources(JarFile jar) { this.jar = jar; }

        private BufferedImage image(ResourceLocation texture) throws IOException {
            ResourceLocation path = texture.withPath("textures/" + texture.getPath() + ".png");
            try (InputStream input = getResourceOrThrow(path).open()) {
                BufferedImage image = ImageIO.read(input);
                if (image == null) throw new IOException("Invalid native PNG: " + path);
                return image;
            }
        }

        @Override public Set<String> getNamespaces() { return Set.of("minecraft"); }
        @Override public Optional<Resource> getResource(ResourceLocation id) {
            if (id.getNamespace().equals("essence_ascendance")) {
                String path = "/assets/essence_ascendance/" + id.getPath();
                if (LatentOreResourcesTest.class.getResource(path) == null) return Optional.empty();
                return Optional.of(new Resource(null,
                        () -> LatentOreResourcesTest.class.getResourceAsStream(path)));
            }
            if (!id.getNamespace().equals("minecraft")) return Optional.empty();
            var entry = jar.getJarEntry("assets/minecraft/" + id.getPath());
            if (entry == null) return Optional.empty();
            return Optional.of(new Resource(null, () -> {
                try { return jar.getInputStream(entry); }
                catch (IOException ex) { throw new UncheckedIOException(ex); }
            }));
        }
        @Override public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).stream().toList(); }
        @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> predicate) {
            return jar.stream().filter(entry -> entry.getName().startsWith("assets/minecraft/" + path))
                    .map(entry -> vanilla(entry.getName().substring("assets/minecraft/".length())))
                    .filter(predicate).collect(Collectors.toMap(key -> key, key -> getResource(key).orElseThrow()));
        }
        @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> predicate) {
            return listResources(path, predicate).entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> List.of(entry.getValue())));
        }
        @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
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
