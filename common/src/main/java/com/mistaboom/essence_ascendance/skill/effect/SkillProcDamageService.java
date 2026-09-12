package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/**
 * Safe attributed secondary damage. Its explicit context remains active for
 * nested death callbacks while EquipmentDamageService blocks primary procs,
 * outgoing skill multipliers, reflection loops, and first-hit bookkeeping.
 */
public final class SkillProcDamageService {
    private static final ThreadLocal<ProcContext> CURRENT = new ThreadLocal<>();

    private SkillProcDamageService() { }

    public static ProcContext current() {
        return CURRENT.get();
    }

    public static boolean hurt(ServerPlayer owner, LivingEntity target, float amount,
                               DamageKind kind, ResourceLocation sourceSkill,
                               PropagationBudget root, int generation) {
        if (!Float.isFinite(amount) || amount <= 0.0F || generation < 0
                || !root.visited(target.getUUID()) || !target.isAlive() || target.isRemoved()
                || !EquipmentDamageService.canSkillHarm(owner, target)) return false;
        DamageSource source = kind == DamageKind.BURNING
                ? owner.damageSources().onFire()
                : owner.damageSources().indirectMagic(owner, owner);
        ProcContext previous = CURRENT.get();
        ProcContext context = new ProcContext(owner, kind, sourceSkill, root, generation);
        CURRENT.set(context);
        final boolean[] accepted = {false};
        try {
            EquipmentDamageService.withSecondarySkillDamage(
                    () -> accepted[0] = target.hurt(source, amount));
            return accepted[0];
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void particles(ServerPlayer owner, LivingEntity target, ParticleOptions particle,
                                 int count, double spread, double speed) {
        ServerLevel level = owner.serverLevel();
        if (target.level() != level) return;
        level.sendParticles(particle, target.getX(), target.getY(0.55), target.getZ(),
                Math.max(1, count), spread, spread, spread, speed);
    }

    public enum DamageKind {
        BURNING,
        COMBUSTION,
        ICE_SHARD,
        LIGHTNING_ARC
    }

    public record ProcContext(ServerPlayer owner, DamageKind kind, ResourceLocation sourceSkill,
                              PropagationBudget root, int generation) {
        public ProcContext {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(sourceSkill);
            Objects.requireNonNull(root);
        }
    }
}
