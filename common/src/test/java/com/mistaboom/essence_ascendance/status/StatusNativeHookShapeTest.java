package com.mistaboom.essence_ascendance.status;

import com.mistaboom.essence_ascendance.verification.TargetTestInputs;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;

/** Exact protected native status entry/merge/instant shapes in both mapped loaders. */
public final class StatusNativeHookShapeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        inspect(name -> TargetTestInputs.nativeClass("fabric", name), false);
        inspect(name -> TargetTestInputs.nativeClass("neoforge", name), true);
        System.out.println("Status native hook shapes passed: " + checks + " (exact target Fabric and NeoForge artifacts; no world)");
    }
    private interface Source { InputStream read(String name) throws Exception; }
    private static ClassNode read(Source source, String name) throws Exception {
        try (var input = source.read("net/minecraft/" + name)) { var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node; }
    }
    private static void inspect(Source source, boolean neo) throws Exception {
        boolean modern = "26.1.2".equals(System.getProperty("essence.minecraft"));
        String livingOwner = "net/minecraft/world/entity/LivingEntity";
        String instanceOwner = "net/minecraft/world/effect/MobEffectInstance";
        String effectOwner = "net/minecraft/world/effect/MobEffect";
        String addDescriptor = "(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z";
        var living = read(source, "world/entity/LivingEntity");
        var add = method(living, "addEffect", "(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z");
        check(count(add, "canBeAffected") == (neo ? 0 : 1)
                && count(add, "canMobEffectBeApplied") == (neo ? 1 : 0), "single loader-native acceptance precheck");
        if (neo) check(count(add, "post") == 1, "NeoForge posts exactly one native Added event after acceptance");
        check(count(add, "update") == 1, "single merge handles stronger/weaker/hidden refresh");
        check(count(add, "onEffectAdded") == 2 && count(add, "onEffectStarted") == 1, "native callbacks remain in one application");
        var force = method(living, "forceAddEffect", "(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V");
        check(count(force, "canBeAffected") == (neo ? 0 : 1)
                && count(force, "canMobEffectBeApplied") == (neo ? 1 : 0)
                && count(force, "put") == 1, "forced effect retains loader-native acceptance and replacement");
        var remove = method(living, "removeEffect", "(Lnet/minecraft/core/Holder;)Z");
        if (modern) {
            String removal = "(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;";
            check(count(remove, livingOwner, "removeEffectNoUpdate", removal) == 1
                    && count(remove, livingOwner, "onEffectsRemoved", "(Ljava/util/Collection;)V") == 1
                    && count(method(living, "removeEffectNoUpdate", removal), "java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;") == 1,
                    "26 native removal owns one map mutation and one collection completion callback");
            if (neo) {
                String eventOwner = "net/neoforged/neoforge/event/EventHooks";
                String eventDescriptor = "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/Holder;)Z";
                check(count(remove, eventOwner, "onEffectRemoved", eventDescriptor) == 1
                        && index(remove, eventOwner, "onEffectRemoved", eventDescriptor) < index(remove, livingOwner, "removeEffectNoUpdate", removal),
                        "26 NeoForge retains its single cancellation event before map mutation");
            } else check(count(remove, "onEffectRemoved") == 0, "26 Fabric has no NeoForge removal event");
        } else check(count(remove, "onEffectRemoved") == (neo ? 2 : 1) && count(remove, "removeEffectNoUpdate") == 1,
                "1.21.1 native removal keeps NeoForge cancellation before the sole map removal and completion callback");
        var instance = read(source, "world/effect/MobEffectInstance");
        if (modern) {
            var tick = method(instance, "tickServer", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z");
            check(count(tick, effectOwner, "applyEffectTick", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;I)Z") == 1
                    && count(tick, instanceOwner, "downgradeToHiddenEffect", "()Z") == 1
                    && count(method(instance, "downgradeToHiddenEffect", "()Z"), instanceOwner, "setDetailsFrom", "(Lnet/minecraft/world/effect/MobEffectInstance;)V") == 1,
                    "26 server effect tick owns one derived application and delegates one exact hidden promotion");
        } else {
            var tick = method(instance, "tick", "(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z");
            check(count(tick, "applyEffectTick") == 1 && count(tick, "setDetailsFrom") == 1,
                    "1.21.1 one derived native effect tick and one hidden promotion, no persisted source guessing");
        }
        var copyConstructor = method(instance, "<init>", "(Lnet/minecraft/world/effect/MobEffectInstance;)V");
        var details = method(instance, "setDetailsFrom", "(Lnet/minecraft/world/effect/MobEffectInstance;)V");
        check(count(copyConstructor, "setDetailsFrom") == 1 && count(details, "clear") == (neo && !modern ? 1 : 0)
                && count(details, "addAll") == (neo && !modern ? 1 : 0),
                "native restoration uses the details copy, including 1.21.1 NeoForge instance cure sets");
        if (modern) check(instance.fields.stream().noneMatch(field -> field.name.equals("cures")),
                "26 instance details have no legacy per-instance cure-set field");
        check(instance.fields.stream().filter(f -> f.name.equals("hiddenEffect") && f.desc.equals("Lnet/minecraft/world/effect/MobEffectInstance;")).count() == 1,
                "one exact hidden-chain accessor field");
        String instant = modern
                ? "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"
                : "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V";
        var splash = modern
                ? method(read(source, "world/entity/projectile/throwableitemprojectile/ThrownSplashPotion"), "onHitAsPotion",
                        "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/HitResult;)V")
                : method(read(source, "world/entity/projectile/ThrownPotion"), "applySplash", null);
        var cloud = modern
                ? method(read(source, "world/entity/AreaEffectCloud"), "serverTick", "(Lnet/minecraft/server/level/ServerLevel;)V")
                : method(read(source, "world/entity/AreaEffectCloud"), "tick", "()V");
        check(count(splash, effectOwner, "applyInstantenousEffect", instant) == 1,
                "one exact generic splash instant dispatch protects custom overrides");
        check(count(cloud, effectOwner, "applyInstantenousEffect", instant) == 1,
                "one exact generic cloud instant dispatch protects custom overrides");
        check(count(splash, livingOwner, "addEffect", addDescriptor) == 1, "one exact splash timed source annotation");
        check(count(cloud, livingOwner, "addEffect", addDescriptor) == 1, "one exact cloud timed source annotation");
        if (modern) {
            var arrow = read(source, "world/entity/projectile/arrow/Arrow");
            var postHurt = method(arrow, "doPostHurtEffects", "(Lnet/minecraft/world/entity/LivingEntity;)V");
            check(count(postHurt, "net/minecraft/world/item/alchemy/PotionContents", "forEachEffect", "(Ljava/util/function/Consumer;F)V") == 1
                    && count(postHurt, "addEffect") == 0
                    && count(method(arrow, "lambda$doPostHurtEffects$0", "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/effect/MobEffectInstance;)V"),
                            livingOwner, "addEffect", addDescriptor) == 1,
                    "26 tipped arrow consolidates base/custom effects into one annotated consumer and its exact native application");
        } else check(count(method(read(source, "world/entity/projectile/Arrow"), "doPostHurtEffects", null), "addEffect") == 2,
                "1.21.1 exactly two tipped-arrow branches retain direct identity and secondary state");
        check(method(read(source, "world/effect/MobEffect"), "applyInstantenousEffect", instant) != null, "base instant fallback exists");
        check(method(read(source, "world/effect/HealOrHarmMobEffect"), "applyInstantenousEffect", instant) != null, "native overriding instant fallback exists");
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
    private static long count(MethodNode method, String owner, String name, String descriptor) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        return count;
    }
    private static int index(MethodNode method, String owner, String name, String descriptor) {
        int index = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                    && call.desc.equals(descriptor)) return index;
            index++;
        }
        return -1;
    }
    private static void check(boolean okay, String message) { checks++; if (!okay) throw new AssertionError(message); }
}
