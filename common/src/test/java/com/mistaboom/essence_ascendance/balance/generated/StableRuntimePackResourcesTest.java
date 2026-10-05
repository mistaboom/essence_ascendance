package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonParser;
import com.google.common.hash.HashCode;
import net.minecraft.data.CachedOutput;
import net.minecraft.server.packs.resources.IoSupplier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** Inspects the installed CDP class and drives its unmodified CachedOutput implementation concurrently. */
public final class StableRuntimePackResourcesTest {
    private static int checks;
    private static final String OWNER = StableRuntimePackResources.OWNER.replace('.', '/');
    private static final String CONSTRUCTOR = "(Ljava/lang/String;Lnet/neoforged/fml/ModContainer;Lnet/minecraft/server/packs/PackType;Lnet/minecraft/server/packs/repository/Pack$Position;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V";
    private static final String WRITE = "(Ljava/nio/file/Path;[BLcom/google/common/hash/HashCode;)V";

    public static void main(String[] args) throws Exception {
        localContract();
        compiledHook();
        String directory = System.getProperty("balance.nativeGeneration.auditedModsDir");
        if (directory != null) {
            Path mods = Path.of(directory);
            Path prism = mods.getParent().getParent().getParent().getParent();
            Path cdp = mods.resolve("CreateDragonsPlus-1.11.9.jar");
            auditedCallSite(cdp, prism);
            try (var nativePack = new NativePack(cdp, prism)) {
                nativePack.unknownVersions();
                nativePack.singleWriterContract();
                for (int round = 0; round < 4; round++) nativePack.concurrentWrites(round);
            }
        }
        System.out.println("StableRuntimePackResourcesTest PASS " + checks + " (audited installed CDP=" + (directory != null) + ")");
    }

    private static void localContract() {
        check(!StableRuntimePackResources.prepare(null, "1.11.9"), "Absent optional mod is a no-op");
        check(!StableRuntimePackResources.prepare(new Object(), "1.11.9"), "Unrelated owners cannot be mutated");
        var original = new HashMap<String, String>();
        original.put("logo", null);
        original.put(null, "value");
        original.put("existing", "original");
        var protectedMap = StableRuntimePackResources.synchronizeWrites(original);
        check(protectedMap.containsKey("logo") && protectedMap.get("logo") == null, "Nullable existing logo entry retained");
        check(protectedMap.get(null).equals("value"), "Existing null-key contract retained");
        check(protectedMap.put("existing", "replacement").equals("original"), "Map.put returns original value");
        check(original.get("existing").equals("replacement"), "Original map entries remain the backing data");
        check(new ArrayList<>(protectedMap.keySet()).equals(new ArrayList<>(original.keySet())), "Existing HashMap traversal retained");
    }

    private static void auditedCallSite(Path cdp, Path prism) throws Exception {
        try (var jar = new JarFile(cdp.toFile())) {
            var type = read(jar.getInputStream(jar.getJarEntry(OWNER + ".class")));
            check(type.interfaces.contains("net/minecraft/data/CachedOutput") && (type.access & Opcodes.ACC_FINAL) != 0,
                    "Installed final runtime pack implements native CachedOutput");
            check(type.fields.stream().anyMatch(f -> f.name.equals("resources") && f.desc.equals("Ljava/util/Map;")
                    && f.access == (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)), "Resource field has the audited private final Map contract");
            var constructors = type.methods.stream().filter(m -> m.name.equals("<init>")).toList();
            check(constructors.size() == 1 && constructors.getFirst().desc.equals(CONSTRUCTOR), "Exact installed constructor injection target");
            MethodNode constructor = constructors.getFirst();
            int assignments = 0, returns = 0, mapPuts = 0;
            boolean nullableLogo = false;
            for (var instruction : constructor.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("resources")) {
                    assignments++;
                    check(previous(field) instanceof MethodInsnNode init && init.owner.equals("java/util/HashMap")
                            && init.name.equals("<init>") && init.desc.equals("()V"), "Constructor initializes an ordinary empty HashMap");
                }
                if (instruction.getOpcode() == Opcodes.RETURN) returns++;
                if (instruction instanceof MethodInsnNode call) {
                    check(!call.owner.equals("java/util/concurrent/CompletableFuture") && !call.owner.equals("net/minecraft/data/DataProvider")
                                    && !call.name.equals("addDataProvider"), "Constructor starts no asynchronous providers before RETURN repair");
                    if (call.owner.equals("net/minecraft/server/packs/PackResources") && call.name.equals("getRootResource")) nullableLogo = true;
                    if (call.owner.equals("java/util/Map") && call.name.equals("put")) mapPuts++;
                }
            }
            check(assignments == 1 && returns == 1 && mapPuts == 1 && nullableLogo, "One initialization and RETURN, with existing optional logo map entry");
            var putOwners = new ArrayList<String>();
            for (var method : type.methods) for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/Map")) {
                    check(List.of("put", "get", "forEach", "keySet").contains(call.name), "No hidden map mutation or implementation-specific map operations");
                    if (call.name.equals("put")) putOwners.add(method.name + method.desc);
                }
            check(putOwners.equals(List.of("<init>" + CONSTRUCTOR, "writeIfNeeded" + WRITE)), "Constructor logo and CachedOutput are the only map writers");
            var provider = type.methods.stream().filter(m -> m.name.equals("addDataProvider")).findFirst().orElseThrow();
            MethodInsnNode run = call(provider, "net/minecraft/data/DataProvider", "run");
            MethodInsnNode join = call(provider, "java/util/concurrent/CompletableFuture", "join");
            check(provider.instructions.indexOf(run) < provider.instructions.indexOf(join), "Provider completion is joined before runtime pack publication");
            var write = type.methods.stream().filter(m -> m.name.equals("writeIfNeeded") && m.desc.equals(WRITE)).findFirst().orElseThrow();
            check(write.instructions.indexOf(call(write, "java/util/Map", "put")) >= 0
                            && (write.access & Opcodes.ACC_SYNCHRONIZED) == 0, "Installed native write path has an unsynchronized map put");
        }
        Path minecraft = prism.resolve("libraries/net/neoforged/neoforge/21.1.251/neoforge-21.1.251-client.jar");
        try (var jar = new JarFile(minecraft.toFile())) {
            var provider = read(jar.getInputStream(jar.getJarEntry("net/minecraft/data/DataProvider.class")));
            var save = provider.methods.stream().filter(m -> m.name.equals("saveStable")
                    && m.desc.equals("(Lnet/minecraft/data/CachedOutput;Lcom/google/gson/JsonElement;Ljava/nio/file/Path;)Ljava/util/concurrent/CompletableFuture;")).findFirst().orElseThrow();
            check(call(save, "net/minecraft/Util", "backgroundExecutor") != null
                    && call(save, "java/util/concurrent/CompletableFuture", "runAsync") != null, "Installed NeoForge Minecraft saves each recipe on the background executor");
            check(provider.methods.stream().anyMatch(m -> hasCall(m, "net/minecraft/data/CachedOutput", "writeIfNeeded")),
                    "Installed asynchronous save task invokes CDP's CachedOutput writer");
            var recipes = read(jar.getInputStream(jar.getJarEntry("net/minecraft/data/recipes/RecipeProvider.class")));
            check(recipes.methods.stream().anyMatch(m -> hasCall(m, "java/util/concurrent/CompletableFuture", "allOf")),
                    "Installed recipe provider waits for all parallel output saves");
        }
    }

    private static void compiledHook() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("neoforge/src/main"))) root = root.getParent();
        if (root == null) throw new AssertionError("Repository root required");
        var type = read(Files.newInputStream(root.resolve("neoforge/build/classes/java/main/com/mistaboom/essence_ascendance/neoforge/mixin/RuntimePackResourcesMixin.class")));
        check(type.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;")), "Missing optional class retains @Pseudo handling");
        check(type.fields.isEmpty(), "Hook has no mandatory optional-mod shadow fields");
        var mixin = type.invisibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        check(values(mixin).get("targets").equals(List.of(StableRuntimePackResources.OWNER)) && values(mixin).get("remap").equals(false),
                "Hook targets only the audited optional class by unlinked name");
        var hook = type.methods.stream().filter(m -> m.name.equals("essenceAscendance$synchronizeRuntimeResources")).findFirst().orElseThrow();
        var annotation = hook.visibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        var config = values(annotation);
        check(config.get("method").equals(List.of("<init>" + CONSTRUCTOR)) && config.get("require").equals(0)
                        && config.get("remap").equals(false) && !config.containsKey("allow"), "Unsupported constructor shapes impose no required-match or count failure");
        @SuppressWarnings("unchecked") var at = (List<AnnotationNode>) config.get("at");
        check(at.size() == 1 && values(at.getFirst()).get("value").equals("RETURN"), "Repair runs after all constructor entry writes");
        check(hasCall(hook, "com/mistaboom/essence_ascendance/balance/generated/StableRuntimePackResources", "prepare"), "Native hook calls the tested production repair");
        var registration = JsonParser.parseString(Files.readString(root.resolve("neoforge/src/main/resources/essence_ascendance.vitality.mixins.json")));
        check(registration.getAsJsonObject().getAsJsonArray("mixins").asList().stream()
                .filter(e -> e.getAsString().equals("RuntimePackResourcesMixin")).count() == 1, "Common optional hook registered once");
    }

    private static final class NativePack implements AutoCloseable {
        private final URLClassLoader loader;
        private final Class<?> type;
        private final java.lang.reflect.Field resources;
        private final Object unsafe;
        private final java.lang.reflect.Method allocate;

        NativePack(Path cdp, Path prism) throws Exception {
            Path fml = prism.resolve("libraries/net/neoforged/fancymodloader/loader/4.0.44/loader-4.0.44.jar");
            loader = new URLClassLoader(new java.net.URL[]{cdp.toUri().toURL(), fml.toUri().toURL()}, StableRuntimePackResourcesTest.class.getClassLoader());
            type = Class.forName(StableRuntimePackResources.OWNER, true, loader);
            resources = type.getDeclaredField("resources");
            resources.setAccessible(true);
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            var singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
            unsafe = singleton.get(null);
            allocate = unsafeType.getMethod("allocateInstance", Class.class);
        }

        // Only native construction's backing field is supplied by the fixture. The installed class's
        // actual writeIfNeeded method, byte-array capture, and resource suppliers execute unchanged.
        private Object fixture(Map<Path, IoSupplier<InputStream>> initial) throws Exception {
            Object result = allocate.invoke(unsafe, type);
            resources.set(result, initial);
            return result;
        }

        void unknownVersions() throws Exception {
            var original = new HashMap<Path, IoSupplier<InputStream>>();
            Object pack = fixture(original);
            for (String version : new String[]{"", "1.11.8", "1.11.10", "1.11.9-custom", null}) {
                check(!StableRuntimePackResources.prepare(pack, version) && resources.get(pack) == original,
                        "Unknown/absent version leaves installed owner and map untouched: " + version);
            }
        }

        void singleWriterContract() throws Exception {
            Path logo = Path.of("pack.png"), recipe = Path.of("data/create_dragons_plus/recipe/example.json");
            var original = new HashMap<Path, IoSupplier<InputStream>>();
            original.put(logo, null);
            Object pack = fixture(original);
            check(StableRuntimePackResources.prepare(pack, "1.11.9"), "Installed final map accepts production constructor-time repair");
            var map = resources(pack);
            check(map.containsKey(logo) && map.get(logo) == null, "Native nullable constructor logo survives repair");
            var output = (CachedOutput) pack;
            output.writeIfNeeded(recipe, bytes("first"), HashCode.fromInt(1));
            output.writeIfNeeded(recipe, bytes("second"), HashCode.fromInt(2));
            check(map.size() == 2 && Arrays.equals(readResource(map, recipe), bytes("second")), "Native single-writer overwrite and last value retained");
            check(Arrays.equals(readResource(map, recipe), bytes("second")), "Native resource supplier opens a new stream each time");
            output.writeIfNeeded(null, bytes("nullable-key"), HashCode.fromInt(3));
            check(Arrays.equals(readResource(map, null), bytes("nullable-key")), "Native map's nullable-key behavior preserved");
            check(!StableRuntimePackResources.prepare(pack, "1.11.9") && resources.get(pack) == map, "Repeated repair leaves existing synchronization unchanged");
        }

        void concurrentWrites(int round) throws Exception {
            Object pack = fixture(new HashMap<>());
            check(StableRuntimePackResources.prepare(pack, "1.11.9"), "Prepare actual installed map for concurrent round " + round);
            var output = (CachedOutput) pack;
            int workers = 16, each = 1024;
            var executor = Executors.newFixedThreadPool(workers);
            var start = new CountDownLatch(1);
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            try {
                for (int worker = 0; worker < workers; worker++) {
                    int number = worker;
                    futures.add(executor.submit(() -> {
                        start.await();
                        for (int entry = 0; entry < each; entry++) {
                            int id = number * each + entry;
                            output.writeIfNeeded(path(id), bytes("recipe-" + id), HashCode.fromInt(id));
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (var future : futures) future.get(30, TimeUnit.SECONDS);
                var map = resources(pack);
                check(map.size() == workers * each, "All native concurrent writes survive map growth, round " + round);
                for (int id = 0; id < workers * each; id++)
                    if (!Arrays.equals(readResource(map, path(id)), bytes("recipe-" + id))) throw new AssertionError("Lost or corrupt native recipe " + id);
                checks++;
            } finally {
                start.countDown(); executor.shutdownNow(); executor.awaitTermination(30, TimeUnit.SECONDS);
            }
        }

        @SuppressWarnings("unchecked")
        private Map<Path, IoSupplier<InputStream>> resources(Object pack) throws Exception { return (Map<Path, IoSupplier<InputStream>>) resources.get(pack); }
        public void close() throws Exception { loader.close(); }
    }

    private static Path path(int id) { return Path.of("data/create_dragons_plus/recipe/sandpaper_polishing/fixture_" + id + ".json"); }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static byte[] readResource(Map<Path, IoSupplier<InputStream>> map, Path path) throws Exception {
        var supplier = map.get(path);
        if (supplier == null) throw new AssertionError("Missing native resource " + path);
        try (var stream = supplier.get()) { return stream.readAllBytes(); }
    }
    private static Map<String, Object> values(AnnotationNode annotation) {
        var result = new HashMap<String, Object>();
        for (int i = 0; i < annotation.values.size(); i += 2) result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        return result;
    }
    private static MethodInsnNode call(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return call;
        throw new AssertionError("Missing audited call " + owner + "." + name + " in " + method.name);
    }
    private static boolean hasCall(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0); return instruction;
    }
    private static ClassNode read(InputStream input) throws Exception {
        var result = new ClassNode(); try (input) { new ClassReader(input).accept(result, 0); } return result;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
