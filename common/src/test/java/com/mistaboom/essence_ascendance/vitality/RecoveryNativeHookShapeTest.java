package com.mistaboom.essence_ascendance.vitality;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Verify both mapped loaders retain the exact two regeneration-only timer writes. */
public final class RecoveryNativeHookShapeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        inspect(RecoveryNativeHookShapeTest.class.getResourceAsStream("/net/minecraft/world/food/FoodData.class"));
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) root = root.getParent();
        if (root == null) throw new AssertionError("Mapped NeoForge build cache required");
        int found = 0;
        try (var paths = Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().startsWith("neoforge-")
                    && p.toString().endsWith(".jar") && !p.toString().endsWith("-sources.jar")).toList()) {
                try (var jar = new JarFile(path.toFile())) { inspect(jar.getInputStream(jar.getJarEntry("net/minecraft/world/food/FoodData.class"))); found++; }
            }
        }
        check(found > 0, "NeoForge parity was actually inspected");
        System.out.println("Recovery native hook shapes passed: " + checks + " (Fabric and NeoForge)");
    }
    private static void inspect(InputStream input) throws Exception {
        var type = new ClassNode(); try (input) { new ClassReader(input).accept(type, 0); }
        var tick = type.methods.stream().filter(m -> m.name.equals("tick")).findFirst().orElseThrow();
        int writes = 0, heals = 0, starvation = 0, costs = 0;
        for (AbstractInsnNode instruction : tick.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("tickTimer") && field.getOpcode() == Opcodes.PUTFIELD) {
                var previous = previous(instruction);
                if (writes == 0 || writes == 2) check(previous.getOpcode() == Opcodes.IADD, "Hooked timer write is a native increment");
                if (writes == 1 || writes == 3 || writes == 5 || writes == 6)
                    check(previous.getOpcode() == Opcodes.ICONST_0, "Native healing/starvation resets remain untouched");
                writes++;
            }
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("heal")) heals++;
                if (call.name.equals("starve")) starvation++;
                if (call.name.equals("addExhaustion")) costs++;
            }
        }
        check(writes == 7 && heals == 2 && starvation == 1 && costs == 2, "Exact native regeneration/cost/starvation topology");
        var save = type.methods.stream().filter(m -> m.name.equals("addAdditionalSaveData")).findFirst().orElseThrow();
        int timerReads = 0;
        for (AbstractInsnNode instruction : save.instructions)
            if (instruction instanceof FieldInsnNode field && field.name.equals("tickTimer") && field.getOpcode() == Opcodes.GETFIELD) timerReads++;
        check(timerReads == 1, "Exact one native serialized timer read excludes transient acceleration");
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node.getOpcode() < 0); return node;
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
