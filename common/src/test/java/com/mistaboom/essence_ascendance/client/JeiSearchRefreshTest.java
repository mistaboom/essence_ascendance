package com.mistaboom.essence_ascendance.client;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Executes both compiled production mixins against a small JEI indexing lifecycle fixture.
 * The fixture supplies only the target rebuild method, not a copy of our refresh logic.
 * This is not a live JEI/Mixin transformation or a timing benchmark.
 */
public final class JeiSearchRefreshTest {
    private static final String PREFIX = "essenceAscendance$";
    private static final Map<Object, Rebuild> REBUILDS = new IdentityHashMap<>();
    private static AtomicLong revision;
    private static int checks;

    public static void main(String[] args) throws Exception {
        var field = JeiTooltipSearchRefreshBridge.class.getDeclaredField("REVISION");
        field.setAccessible(true);
        revision = (AtomicLong) field.get(null);
        long previous = revision.get();
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("neoforge/src/main/java"))) root = root.getParent();
        if (root == null) throw new AssertionError("Project root required");
        try {
            for (String loader : List.of("fabric", "neoforge")) {
                Path compiled = root.resolve(loader + "/build/classes/java/main/com/mistaboom/essence_ascendance/"
                        + loader + "/mixin/JeiIngredientFilterMixin.class");
                check(Files.isRegularFile(compiled), loader + " compiled mixin is required");
                byte[] bytes = Files.readAllBytes(compiled);
                inspectHooks(bytes);
                exercise(bytes, loader);
            }
        } finally {
            revision.set(previous);
            REBUILDS.clear();
        }
        System.out.println("JeiSearchRefreshTest: " + checks + " checks PASS; both production loader hooks,"
                + " initial/reload reuse, new revisions, reentrancy and failure throttling; no live JEI timing claim");
    }

    private static void exercise(byte[] bytes, String loader) throws Exception {
        revision.set(2);
        Object filter = fixture(bytes, true);
        Rebuild rebuild = REBUILDS.get(filter);
        invoke(filter, "initialIndexComplete");
        getElements(filter);
        check(rebuild.calls == 0, loader + " first open reuses JEI's initial index");

        revision.set(3);
        getElements(filter);
        getElements(filter);
        check(rebuild.calls == 1 && indexed(filter) == 3, loader + " newer tooltip revision refreshes exactly once");

        revision.set(6);
        rebuild(filter); // JEI/resource reload performs its own indexing before inventory opens.
        getElements(filter);
        check(rebuild.calls == 2 && indexed(filter) == 6, loader + " resource reload satisfies the latest revision");

        revision.set(7);
        rebuild.during = revision::incrementAndGet;
        rebuild(filter);
        check(indexed(filter) == 7 && revision.get() == 8, loader + " build cannot acknowledge a later revision");
        rebuild.during = null;
        getElements(filter);
        check(rebuild.calls == 4 && indexed(filter) == 8, loader + " later revision remains pending after native rebuild");

        revision.set(9);
        rebuild.during = () -> getElements(filter);
        getElements(filter);
        check(rebuild.calls == 5 && indexed(filter) == 9, loader + " own refresh prevents recursive search rebuilding");
        rebuild.during = null;

        revision.set(10);
        rebuild.fail = true;
        try { rebuild(filter); throw new AssertionError("Expected fixture failure"); }
        catch (IllegalStateException expected) { check(indexed(filter) == 9, loader + " failed native rebuild is not acknowledged"); }
        getElements(filter); // Existing reflective failure handling records this attempted revision.
        int failedCalls = rebuild.calls;
        getElements(filter);
        check(rebuild.calls == failedCalls && indexed(filter) == 10, loader + " failed own refresh does not retry every frame");
        rebuild.fail = false;
        revision.set(11);
        getElements(filter);
        check(rebuild.calls == failedCalls + 1 && indexed(filter) == 11, loader + " newer revision retries after failure");

        Object missingMethod = fixture(bytes, false);
        getElements(missingMethod);
        check(indexed(missingMethod) == 11, loader + " incompatible JEI method keeps existing failure throttling");
    }

    /** Called by the fixture method added to the compiled mixin; models JEI's HEAD/successful RETURN. */
    public static void rebuild(Object filter) throws Exception {
        Rebuild state = REBUILDS.get(filter);
        state.calls++;
        invoke(filter, "indexRebuildStarted");
        if (state.during != null) state.during.run();
        if (state.fail) throw new IllegalStateException("Expected fixture rebuild failure");
        invoke(filter, "indexRebuildComplete");
    }

    private static Object fixture(byte[] bytes, boolean hasRebuild) throws Exception {
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        type.access &= ~Opcodes.ACC_ABSTRACT; // No abstract methods; production mixins are abstract by convention.
        ClassWriter writer = new ClassWriter(0);
        type.accept(writer);
        if (hasRebuild) {
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "rebuildItemFilter", "()V", null, null);
            method.visitCode();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/mistaboom/essence_ascendance/client/JeiSearchRefreshTest", "rebuild", "(Ljava/lang/Object;)V", false);
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
        }
        byte[] fixtureBytes = writer.toByteArray();
        Class<?> fixtureClass = new ClassLoader(JeiSearchRefreshTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, fixtureBytes, 0, fixtureBytes.length); }
        }.define();
        Object value = fixtureClass.getConstructor().newInstance();
        REBUILDS.put(value, new Rebuild());
        return value;
    }

    private static void getElements(Object filter) throws Exception { invoke(filter, "refreshSearchIndexIfNeeded"); }

    private static void invoke(Object target, String name) throws Exception {
        var method = java.util.Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(PREFIX + name)).findFirst().orElseThrow();
        method.setAccessible(true);
        try { method.invoke(target, new Object[]{null}); }
        catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw exception;
        }
    }

    private static long indexed(Object filter) throws Exception {
        var field = filter.getClass().getDeclaredField(PREFIX + "indexedRevision");
        field.setAccessible(true);
        return field.getLong(filter);
    }

    private static void inspectHooks(byte[] bytes) {
        ClassNode type = new ClassNode(); new ClassReader(bytes).accept(type, 0);
        hook(type, "initialIndexComplete", "<init>", "RETURN");
        hook(type, "indexRebuildStarted", "rebuildItemFilter", "HEAD");
        hook(type, "indexRebuildComplete", "rebuildItemFilter", "RETURN");
        hook(type, "refreshSearchIndexIfNeeded", "getElements", "HEAD");
    }

    private static void hook(ClassNode type, String name, String target, String site) {
        MethodNode method = type.methods.stream().filter(candidate -> candidate.name.equals(PREFIX + name)).findFirst().orElseThrow();
        AnnotationNode injection = method.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        check(value(injection, "method").equals(List.of(target)), name + " targets actual JEI lifecycle method");
        var at = (AnnotationNode) ((List<?>) value(injection, "at")).getFirst();
        check(value(at, "value").equals(site), name + " runs at the required successful lifecycle boundary");
        check(value(injection, "require").equals(0) && value(injection, "remap").equals(false), name + " preserves optional JEI linkage");
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        throw new AssertionError("Missing annotation key " + key);
    }

    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private interface During { void run() throws Exception; }
    private static final class Rebuild { int calls; boolean fail; During during; }
}
