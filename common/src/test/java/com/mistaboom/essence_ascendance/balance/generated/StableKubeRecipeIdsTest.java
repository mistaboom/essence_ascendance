package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** Real optional KubeJS call-site/native hash audit plus independent JVM replay. */
public final class StableKubeRecipeIdsTest {
    private static int checks;
    private static final String KUBE = "2101.7.2-build.374", MI = "2.5.8";
    private static final String OWNER = "dev/latvian/mods/kubejs/plugin/builtin/wrapper/StringUtilsWrapper";
    private static final String HASH_DESCRIPTOR = "(Lcom/google/gson/JsonElement;)Ljava/lang/String;";
    private static final String FIRST = "{\"type\":\"modern_industrialization:mixer\",\"eu\":2,\"item_inputs\":[{\"amount\":1,\"item\":\"minecraft:sand\"},{\"item\":\"minecraft:gravel\",\"amount\":2}],\"duration\":100}";
    private static final String REORDERED = "{\"duration\":100,\"item_inputs\":[{\"item\":\"minecraft:sand\",\"amount\":1},{\"amount\":2,\"item\":\"minecraft:gravel\"}],\"eu\":2,\"type\":\"modern_industrialization:mixer\"}";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--child")) {
            try (var nativeHash = new NativeHash(Path.of(args[1]))) {
                String json = new String(java.util.Base64.getDecoder().decode(args[2]), java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("ID_HASH=" + nativeHash.hash(canonical(JsonParser.parseString(json))));
            }
            return;
        }
        semanticPreservation();
        compiledHook();
        String directory = System.getProperty("balance.nativeGeneration.auditedModsDir");
        if (directory != null) {
            Path mods = Path.of(directory);
            auditedCallSite(mods);
            try (var nativeHash = new NativeHash(mods)) {
                check(!nativeHash.hash(JsonParser.parseString(FIRST)).equals(nativeHash.hash(JsonParser.parseString(REORDERED))),
                        "Installed native KubeJS hash reproduces object-insertion-order drift");
                String expected = nativeHash.hash(canonical(JsonParser.parseString(FIRST)));
                check(expected.equals(nativeHash.hash(canonical(JsonParser.parseString(REORDERED)))), "Canonical input reaches the same installed native hash");
                check(expected.equals(childHash(mods, FIRST)), "Fresh JVM one retains the same native auto ID");
                check(expected.equals(childHash(mods, REORDERED)), "Fresh JVM two with opposite JSON insertion order retains the same native auto ID");
                JsonElement arrayChange = JsonParser.parseString(FIRST);
                var inputs = arrayChange.getAsJsonObject().getAsJsonArray("item_inputs");
                var first = inputs.get(0); inputs.set(0, inputs.get(1)); inputs.set(1, first);
                check(!expected.equals(nativeHash.hash(canonical(arrayChange))), "Native ID still changes for an ordered input-list change");
            }
        }
        System.out.println("StableKubeRecipeIdsTest PASS " + checks + " (audited installed KubeJS=" + (directory != null) + ")");
    }

    private static JsonElement canonical(JsonElement json) {
        return StableKubeRecipeIds.canonicalFallbackInput(json, "modern_industrialization", KUBE, MI);
    }

    private static void semanticPreservation() {
        JsonElement first = JsonParser.parseString(FIRST), other = JsonParser.parseString(REORDERED);
        String original = first.toString();
        check(canonical(first).toString().equals(canonical(other).toString()), "Object keys are sorted at every depth");
        check(first.toString().equals(original), "Source recipe JSON is never mutated");
        check(canonical(first).equals(first), "Canonicalization preserves every JSON value");
        var numbers = JsonParser.parseString("{\"z\":9223372036854775807,\"a\":1e-100,\"minus\":-0.0,\"null\":null,\"bool\":true,\"text\":\"literal\"}");
        check(canonical(numbers).getAsJsonObject().get("z") == numbers.getAsJsonObject().get("z"), "Full-width number object retained without conversion");
        check(canonical(numbers).getAsJsonObject().get("a").toString().equals("1e-100"), "Number representation remains exact");
        check(canonical(numbers).getAsJsonObject().get("minus").toString().equals("-0.0"), "Signed zero remains exact");
        check(canonical(numbers).equals(numbers), "Nulls, booleans, strings, and numbers remain significant");
        check(StableKubeRecipeIds.canonicalFallbackInput(first, "other_mod", KUBE, MI) == first, "Other serializer namespaces remain untouched");
        check(StableKubeRecipeIds.canonicalFallbackInput(first, "modern_industrialization", "changed", MI) == first, "Other KubeJS versions remain untouched");
        check(StableKubeRecipeIds.canonicalFallbackInput(first, "modern_industrialization", KUBE, "changed") == first, "Other MI versions remain untouched");
        check(StableKubeRecipeIds.fallbackInput(new Object(), first, KUBE, MI) == first, "No reflective provenance means no rewrite");
        check(StableKubeRecipeIds.fallbackInput(new WrongProvenance(), first, KUBE, MI) == first, "A similarly named method cannot forge KubeJS ownership");
    }

    public static final class WrongProvenance { public Object getSerializationTypeFunction() { return new Object(); } }

    private static void auditedCallSite(Path mods) throws Exception {
        try (var jar = new JarFile(mods.resolve("kubejs-neoforge-2101.7.2-build.374.jar").toFile())) {
            var type = read(jar.getInputStream(jar.getJarEntry("dev/latvian/mods/kubejs/recipe/KubeRecipe.class")));
            MethodNode method = type.methods.stream().filter(m -> m.name.equals("getOrCreateId")
                    && m.desc.equals("()Lnet/minecraft/resources/ResourceLocation;")).findFirst().orElseThrow();
            List<MethodInsnNode> calls = new ArrayList<>();
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals(OWNER) && call.name.equals("getUniqueId")) calls.add(call);
            check(calls.size() == 1 && calls.getFirst().desc.equals(HASH_DESCRIPTOR), "Exactly one audited native fallback hash call and descriptor");
            var call = calls.getFirst();
            check(call.getOpcode() == Opcodes.INVOKESTATIC && call.itf, "Fallback remains the same native interface static call");
            int hashIndex = method.instructions.indexOf(call);
            boolean explicitBypass = false, uniqueBypass = false;
            for (var instruction : method.instructions) {
                if (instruction instanceof JumpInsnNode jump && method.instructions.indexOf(jump) < hashIndex
                        && method.instructions.indexOf(jump.label) > hashIndex) {
                    if (jump.getOpcode() == Opcodes.IFNONNULL && previous(jump) instanceof FieldInsnNode field
                            && field.name.equals("id") && field.desc.equals("Lnet/minecraft/resources/ResourceLocation;")) explicitBypass = true;
                    if (jump.getOpcode() == Opcodes.IFEQ && previous(jump) instanceof MethodInsnNode previous
                            && previous.owner.equals("java/lang/String") && previous.name.equals("isEmpty")) uniqueBypass = true;
                }
            }
            check(explicitBypass, "Existing and explicit IDs branch past the entire fallback hash");
            check(uniqueBypass, "Nonempty schema-generated unique IDs branch past the fallback hash");
            check(previous(call) instanceof FieldInsnNode jsonField && jsonField.name.equals("json")
                    && jsonField.desc.equals("Lcom/google/gson/JsonObject;"), "Hash argument is the recipe's original JSON object");
            MethodNode getter = type.methods.stream().filter(m -> m.name.equals("getSerializationTypeFunction")).findFirst().orElseThrow();
            check(getter.desc.equals("()Ldev/latvian/mods/kubejs/recipe/RecipeTypeFunction;") && getter.instructions.iterator().hasNext(), "Audited serialization type getter descriptor");
            var typeFunction = read(jar.getInputStream(jar.getJarEntry("dev/latvian/mods/kubejs/recipe/RecipeTypeFunction.class")));
            check(typeFunction.fields.stream().anyMatch(f -> f.name.equals("id") && f.desc.equals("Lnet/minecraft/resources/ResourceLocation;")
                    && (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)), "Serializer provenance ID is public and final");
        }
        try (var jar = new JarFile(mods.resolve("Modern-Industrialization-2.5.8.jar").toFile())) {
            var type = read(jar.getInputStream(jar.getJarEntry("aztech/modern_industrialization/compat/kubejs/recipe/MachineKubeRecipe.class")));
            check(type.superName.equals("dev/latvian/mods/kubejs/recipe/KubeRecipe") && type.methods.stream().noneMatch(m -> m.name.equals("getSerializationTypeFunction")),
                    "Installed MI recipe inherits the audited KubeJS serialization-type method");
        }
    }

    private static void compiledHook() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("neoforge/src/main"))) root = root.getParent();
        if (root == null) throw new AssertionError("Repository root required");
        var type = read(Files.newInputStream(root.resolve("neoforge/build/classes/java/main/com/mistaboom/essence_ascendance/neoforge/mixin/KubeRecipeGeneratedIdMixin.class")));
        check(type.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;")), "Optional target retains @Pseudo");
        MethodNode hook = type.methods.stream().filter(m -> m.name.equals("essenceAscendance$stableFallbackInput")).findFirst().orElseThrow();
        AnnotationNode annotation = hook.visibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")).findFirst().orElseThrow();
        var values = annotationValues(annotation);
        check(values.get("method").equals(List.of("getOrCreateId()Lnet/minecraft/resources/ResourceLocation;")), "Mixin targets only exact auto-ID method");
        check(values.get("index").equals(0) && values.get("require").equals(0) && !values.containsKey("allow")
                && values.get("remap").equals(false), "Optional unremapped hook cannot impose a match-count failure on unsupported versions");
        var at = annotationValues((AnnotationNode) values.get("at"));
        check(at.get("value").equals("INVOKE") && at.get("target").equals("L" + OWNER + ";getUniqueId" + HASH_DESCRIPTOR), "Mixin call target matches installed native fallback hash");
        JsonElement config = JsonParser.parseString(Files.readString(root.resolve("neoforge/src/main/resources/essence_ascendance.vitality.mixins.json")));
        check(config.getAsJsonObject().getAsJsonArray("mixins").asList().stream().filter(e -> e.getAsString().equals("KubeRecipeGeneratedIdMixin")).count() == 1,
                "Server optional mixin is registered exactly once");
    }

    private static Map<String, Object> annotationValues(AnnotationNode annotation) {
        var values = new java.util.HashMap<String, Object>();
        for (int i = 0; i < annotation.values.size(); i += 2) values.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        return values;
    }

    private static ClassNode read(InputStream input) throws Exception {
        var type = new ClassNode(); try (input) { new ClassReader(input).accept(type, 0); } return type;
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0); return node;
    }

    private static String childHash(Path mods, String json) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), "-Xmx128m", "-cp",
                System.getProperty("java.class.path"), StableKubeRecipeIdsTest.class.getName(), "--child", mods.toString(),
                java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("Isolated native hash fixture timed out"); }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new AssertionError("Isolated native hash fixture failed: " + output);
        return output.lines().filter(line -> line.startsWith("ID_HASH=")).map(line -> line.substring(8)).findFirst().orElseThrow();
    }

    private static final class NativeHash implements AutoCloseable {
        private final URLClassLoader loader;
        private final java.lang.reflect.Method method;
        NativeHash(Path mods) throws Exception {
            Path rhino;
            try (var files = Files.list(mods)) { rhino = files.filter(p -> p.getFileName().toString().startsWith("rhino-") && p.toString().endsWith(".jar")).findFirst().orElseThrow(); }
            loader = new URLClassLoader(new java.net.URL[]{mods.resolve("kubejs-neoforge-2101.7.2-build.374.jar").toUri().toURL(), rhino.toUri().toURL()},
                    StableKubeRecipeIdsTest.class.getClassLoader());
            method = Class.forName("dev.latvian.mods.kubejs.util.JsonIO", true, loader).getMethod("getJsonHashString", JsonElement.class);
        }
        String hash(JsonElement json) throws Exception { return (String) method.invoke(null, json); }
        public void close() throws Exception { loader.close(); }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
