package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
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
        // Native reflection is a separately attributed nested action. Preserve the outer context
        // for its remaining targets, but never lend its player/skill to the reflector's damage.
        return visibleContext(CURRENT.get(), EquipmentDamageService.isReflectionInProgress());
    }

    static <T> T visibleContext(T context, boolean reflecting) { return reflecting ? null : context; }

    public static boolean hurt(ServerPlayer owner, LivingEntity target, float amount,
                               DamageKind kind, ResourceLocation sourceSkill,
                               PropagationBudget root, int generation) {
        if (!Float.isFinite(amount) || amount <= 0.0F || generation < 0
                || !root.visited(target.getUUID()) || !target.isAlive() || target.isRemoved()
                || !EquipmentDamageService.canSkillHarm(owner, target)) return false;
        DamageSource source = kind == DamageKind.BURNING
                ? owner.damageSources().onFire()
                : new DamageSource(owner.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(Objects.requireNonNull(kind.damageType(), "Native returned shots keep their existing damage source")),
                        owner, owner);
        var result = EquipmentDamageService.measureDamage(target, source,
                () -> withAttributedDamage(owner, sourceSkill, kind, root, generation, () -> target.hurt(source, amount)));
        return result.accepted() && result.loss() > 0;
    }

    /** Retains the native damage source/physics while sharing secondary attribution and recursion protection. */
    public static boolean withAttributedDamage(ServerPlayer owner, ResourceLocation sourceSkill, DamageKind kind,
                                               PropagationBudget root, int generation,
                                               java.util.function.BooleanSupplier nativeHurt) {
        ProcContext previous = CURRENT.get();
        ProcContext context = new ProcContext(owner, kind, sourceSkill, root, generation);
        CURRENT.set(context);
        final boolean[] accepted = {false};
        try {
            EquipmentDamageService.withSecondarySkillDamage(
                    () -> accepted[0] = nativeHurt.getAsBoolean());
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
        BURNING(DamageTypes.ON_FIRE),
        COMBUSTION(DamageTypes.INDIRECT_MAGIC),
        EXPLOSIVE_PAYLOAD(DamageTypes.PLAYER_EXPLOSION),
        REDIRECTED_PROJECTILE(null),
        CROWD_REPRISAL(DamageTypes.THORNS),
        ICE_SHARD(DamageTypes.INDIRECT_MAGIC),
        LIGHTNING_ARC(DamageTypes.INDIRECT_MAGIC);

        private final ResourceKey<DamageType> damageType;
        DamageKind(ResourceKey<DamageType> damageType) { this.damageType = damageType; }
        /** A null type means the caller must retain the original projectile source through withAttributedDamage. */
        public ResourceKey<DamageType> damageType() { return damageType; }

        public boolean reflectedOutcome() { return this == REDIRECTED_PROJECTILE || this == CROWD_REPRISAL; }
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
