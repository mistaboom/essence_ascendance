package com.mistaboom.essence_ascendance.verification;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Exercises the native effect codec and tick entry points on each Minecraft version. */
public final class MobEffectTestCompat {
    private MobEffectTestCompat() { }

    public static MobEffectInstance roundtrip(MobEffectInstance effect, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries);*/
        //? if >=26.1.2
        /*var tag = MobEffectInstance.CODEC.encodeStart(ops, effect).getOrThrow();*/
        //? if >=26.1.2
        /*return MobEffectInstance.CODEC.parse(ops, tag).getOrThrow();*/
        //? if <26.1.2
        return MobEffectInstance.load((net.minecraft.nbt.CompoundTag) effect.save());
    }

    public static boolean tick(MobEffectInstance effect, LivingEntity target, Runnable changed) {
        //? if >=26.1.2
        /*return effect.tickServer((net.minecraft.server.level.ServerLevel) target.level(), target, changed);*/
        //? if <26.1.2
        return effect.tick(target, changed);
    }
}
