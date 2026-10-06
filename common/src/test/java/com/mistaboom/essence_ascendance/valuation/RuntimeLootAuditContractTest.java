package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Signature fixtures and staged failure evidence; never calls a game modifier or loot callback. */
public final class RuntimeLootAuditContractTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Method getter = Handler.class.getDeclaredMethod("getLootModifierManager");
        Method all = Manager.class.getMethod("getAllLootMods");
        Field codec = ModifierApi.class.getField("DIRECT_CODEC");
        check(supported(getter, all, codec), "Known read-only API shapes remain available independent of patch version");
        check(!supported(InstanceHandler.class.getDeclaredMethod("getLootModifierManager"), all, codec), "Instance getter cannot impersonate static manager access");
        check(!supported(WrongReturnHandler.class.getDeclaredMethod("getLootModifierManager"), all, codec), "Changed getter return type is rejected");
        check(!supported(StaticHandler.class.getDeclaredMethod("getLootModifierManager"), StaticManager.class.getMethod("getAllLootMods"), codec, StaticManager.class), "Static manager enumeration is rejected");
        check(!supported(ArrayHandler.class.getDeclaredMethod("getLootModifierManager"), ArrayManager.class.getMethod("getAllLootMods"), codec, ArrayManager.class), "Changed enumeration return type is rejected");
        check(!supported(getter, all, MutableCodec.class.getField("DIRECT_CODEC")), "Mutable codec field is rejected");
        check(!supported(getter, all, WrongCodec.class.getField("DIRECT_CODEC")), "Changed codec type is rejected");
        AtomicInteger captures = new AtomicInteger();
        check(RuntimeLootAudit.captureOptional(null, () -> { captures.incrementAndGet(); return List.of(); }).isEmpty() && captures.get() == 0,
                "Absent loader never resolves its optional inspection API");
        var known = new RuntimeLootAudit.Modifier("fixture.Known", new JsonObject(), "fixture");
        check(RuntimeLootAudit.captureOptional("future-compatible-patch", () -> List.of(known)).equals(List.of(known)),
                "Compatible reader attempt is used for an unfamiliar loader version");
        var linkage = RuntimeLootAudit.captureOptional("future-patch", () -> { throw new NoSuchMethodError("fixture changed API"); });
        unknown(linkage, "Linkage failure remains explicit all-table UNKNOWN evidence");
        check(linkage.getFirst().limitation().contains("NoSuchMethodError"), "Unknown modifier retains an actionable failure reason");
        var partial = RuntimeLootAudit.captureOptional("future-patch", () -> {
            var staged = new java.util.ArrayList<RuntimeLootAudit.Modifier>(); staged.add(known);
            throw new IllegalStateException("later modifier inspection failed");
        });
        unknown(partial, "Failed capture never publishes its partially inspected modifiers");
        try {
            RuntimeLootAudit.captureOptional("future-patch", () -> { throw new OutOfMemoryError("fixture VM sentinel"); });
            throw new AssertionError("VM failure was swallowed");
        } catch (OutOfMemoryError expected) { checks++; }
        System.out.println("Runtime loot inspection contracts passed: " + checks + " checks");
    }
    private static boolean supported(Method getter, Method all, Field codec) {
        return supported(getter, all, codec, Manager.class);
    }
    private static boolean supported(Method getter, Method all, Field codec, Class<?> managerType) {
        return RuntimeLootAudit.inspectionApiSupported(getter, getter.getDeclaringClass(), managerType, all, codec.getDeclaringClass(), codec);
    }
    private static void unknown(List<RuntimeLootAudit.Modifier> modifiers, String message) {
        check(modifiers.size() == 1 && modifiers.getFirst().blockRule().mode() == BlockLootModifierAudit.Mode.UNKNOWN
                && !modifiers.getFirst().conditionsEnforced() && modifiers.getFirst().mayAffect("minecraft:blocks/stone")
                && modifiers.getFirst().mayAffect("fixture:entity") && modifiers.getFirst().definition().isEmpty(), message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    public static final class Handler { static Manager getLootModifierManager() { throw new AssertionError("No invocation"); } }
    public static final class InstanceHandler { Manager getLootModifierManager() { throw new AssertionError("No invocation"); } }
    public static final class WrongReturnHandler { static Object getLootModifierManager() { throw new AssertionError("No invocation"); } }
    public static final class StaticHandler { static StaticManager getLootModifierManager() { throw new AssertionError("No invocation"); } }
    public static final class ArrayHandler { static ArrayManager getLootModifierManager() { throw new AssertionError("No invocation"); } }
    public static final class Manager { public Collection<Object> getAllLootMods() { throw new AssertionError("No invocation"); } }
    public static final class StaticManager { public static Collection<Object> getAllLootMods() { throw new AssertionError("No invocation"); } }
    public static final class ArrayManager { public Object[] getAllLootMods() { throw new AssertionError("No invocation"); } }
    public static final class ModifierApi { public static final Codec<Object> DIRECT_CODEC = null; }
    public static final class MutableCodec { public static Codec<Object> DIRECT_CODEC; }
    public static final class WrongCodec { public static final Object DIRECT_CODEC = new Object(); }
}
