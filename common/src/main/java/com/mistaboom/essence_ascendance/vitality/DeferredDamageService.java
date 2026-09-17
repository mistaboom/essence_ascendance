package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.mixin.DeferredDamageNativeAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Final-health obligations use native death/totem handling, but never mitigation or on-hit procs twice. */
public final class DeferredDamageService {
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("essence_ascendance", "delayed_pain"));
    private static final ThreadLocal<ServerPlayer> PAYING = new ThreadLocal<>();
    private DeferredDamageService() { }
    public static boolean paying(ServerPlayer player) { return PAYING.get() == player; }
    public static boolean deferred(DamageSource source) { return source.is(TYPE); }
    public static double pay(ServerPlayer player, LinearDamageQueue.Payment<VitalityDamageLedger.Source> payment) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.getAbilities().invulnerable) return 0;
        Entity attacker = payment.source().attacker() == null ? null : player.serverLevel().getEntity(payment.source().attacker());
        DamageSource source = new DamageSource(player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE), attacker);
        ServerPlayer previous = PAYING.get();
        int immunity = player.invulnerableTime, animation = player.hurtTime, duration = player.hurtDuration;
        var nativeAccess = (DeferredDamageNativeAccess) player;
        float lastHurt = nativeAccess.essenceAscendance$lastHurt();
        final double roundingUnit = Math.ulp(player.getHealth());
        PAYING.set(player);
        try {
            double[] accepted = {0};
            EquipmentDamageService.withSecondarySkillDamage(() -> {
                var result = EquipmentDamageService.measureDamage(player, source,
                        () -> player.hurt(source, (float)Math.min(Float.MAX_VALUE, payment.amount())));
                accepted[0] = result.accepted() && result.loss() > 0
                        ? (Math.abs(result.loss() - payment.amount()) <= roundingUnit
                        ? payment.amount() : Math.min(payment.amount(), result.loss())) : 0;
            });
            return accepted[0];
        } finally {
            // Paying an old wound must neither reset ordinary immunity nor show twenty new flinches/second.
            player.invulnerableTime = immunity; player.hurtTime = animation; player.hurtDuration = duration;
            nativeAccess.essenceAscendance$lastHurt(lastHurt);
            if (previous == null) PAYING.remove(); else PAYING.set(previous);
        }
    }
}
