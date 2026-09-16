package com.mistaboom.essence_ascendance.skill.effect;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Manual-use, container and owner-only sleep/phantom hook parity across loaders. */
public final class SustenanceNativeHookShapeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        inspect(name -> SustenanceNativeHookShapeTest.class.getClassLoader().getResourceAsStream(name + ".class"), false);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) root = root.getParent();
        if (root == null) throw new AssertionError("Mapped NeoForge cache required");
        List<Path> jars;
        try (var files = Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            jars = files.filter(p -> p.getFileName().toString().startsWith("neoforge-") && p.toString().endsWith(".jar")
                    && !p.toString().endsWith("-sources.jar")).toList();
        }
        check(!jars.isEmpty(), "both loader artifacts available");
        for (var path : jars) try (var jar = new JarFile(path.toFile())) {
            inspect(name -> jar.getInputStream(jar.getJarEntry(name + ".class")), true);
        }
        System.out.println("Sustenance native shapes passed: " + checks + " (both mapped loaders; no world)");
    }
    private interface Source { InputStream read(String name) throws Exception; }
    private static ClassNode read(Source source, String name) throws Exception {
        try (var input = source.read("net/minecraft/" + name)) { var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node; }
    }
    private static void inspect(Source source, boolean neo) throws Exception {
        var living = read(source,"world/entity/LivingEntity");
        var start = method(living,"startUsingItem",null);
        check(count(start,"getUseDuration") == 1, "one native duration read at start");
        if (neo) check(count(start,"onItemUseStart") == 1, "NeoForge native start event can cancel shortened duration");
        var update = method(living,"updateUsingItem",null);
        check(count(update,"onUseTick") == 1 && count(update,"completeUsingItem") == 1,
                "native countdown retains one per-tick callback and one completion");
        if (neo) check(count(update,"onItemUseTick") == 1, "NeoForge use tick adjustment retained");
        var complete = method(living,"completeUsingItem",null);
        check(count(complete,"finishUsingItem") == 1 && count(complete,"setItemInHand") == 1
                && count(complete,"stopUsingItem") == 1 && count(complete,"triggerItemUseEffects") == 1,
                "single native completion retains hand replacement, stop and sound/particles");
        if (neo) check(count(complete,"onItemUseFinish") == 1, "NeoForge finish event retained");
        if (neo) check(count(method(read(source,"server/level/ServerPlayerGameMode"),"useItem",null),"onItemRightClick") == 1,
                "NeoForge native game-mode use retains right-click cancellation before item use");
        check(count(method(living,"releaseUsingItem",null),"releaseUsing") == 1, "ordinary release cancellation retained");
        var player = read(source,"world/entity/player/Player");
        var eat = method(player,"eat",null);
        check(count(eat,"usingConvertsTo") == 1 && count(eat,"add") == 1 && count(eat,"trigger") == 1,
                "one component-container insertion and native consume criterion");
        check(count(eat,"drop") == (neo ? 1 : 0),
                "NeoForge already drops rejected component containers; only Fabric needs the fallback");
        check(count(method(player,"jumpFromGround",null),"causeFoodExhaustion") == 2
                && count(method(player,"attack",null),"causeFoodExhaustion") == 1, "jump and attack exertion only");
        check(count(method(player,"actuallyHurt",null),"causeFoodExhaustion") == 1,
                "accepted damage exhaustion remains explicit and outside suppression");
        check(count(method(read(source,"server/level/ServerPlayer"),"checkMovementStatistics",null),"causeFoodExhaustion") == 6,
                "native movement exhaustion routes are scoped");
        check(count(method(read(source,"world/level/block/Block"),"playerDestroy",null),"causeFoodExhaustion") == 1,
                "native mining exertion scoped");
        check(count(method(read(source,"server/players/SleepStatus"),"update",null),"isSpectator") == 1,
                "sleep quorum uses per-owner exclusion");
        check(count(method(read(source,"world/level/levelgen/PhantomSpawner"),"tick",null),"isSpectator") == 1,
                "phantom spawn loop filters each player");
        var phantomGoal = read(source,"world/entity/monster/Phantom$PhantomAttackPlayerTargetGoal");
        check(count(method(phantomGoal,"canUse",null),"canAttack") == 1
                && count(method(phantomGoal,"canContinueToUse",null),"canAttack") == 1,
                "phantom initial and retained target share native targeting conditions");
        check(method(read(source,"world/entity/ai/targeting/TargetingConditions"),"test",null) != null,
                "targeting conditions accept attacker and target for owner-local protection");
        check(method(read(source,"world/entity/monster/Phantom"),"aiStep",null) != null,
                "phantom pre-AI hook clears already-swooping targets before inherited AI executes");
    }
    private static MethodNode method(ClassNode type, String name, String desc) {
        var found = type.methods.stream().filter(m -> m.name.equals(name) && (desc == null || m.desc.equals(desc))).toList();
        if (found.size() != 1) throw new AssertionError("Expected one exact method " + type.name + "." + name);
        return found.getFirst();
    }
    private static long count(MethodNode method, String name) {
        long count = 0; for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals(name)) count++;
        return count;
    }
    private static void check(boolean okay, String message) { checks++; if (!okay) throw new AssertionError(message); }
}
