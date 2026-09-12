package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/**
 * Immutable result emitted only after the server observes real health or
 * absorption loss. The measured loss is reusable by effects that scale from
 * the confirmed hit. Secondary skill damage uses SkillProcDamageService
 * instead and can never be mistaken for this primary contract.
 */
public record AttackResultContext(
        ServerPlayer attacker,
        LivingEntity target,
        AttackCategory category,
        DamageSource source,
        double damageDealt,
        long gameTime,
        Origin origin
) {
    public AttackResultContext {
        Objects.requireNonNull(attacker);
        Objects.requireNonNull(target);
        Objects.requireNonNull(category);
        Objects.requireNonNull(source);
        Objects.requireNonNull(origin);
        if (!Double.isFinite(damageDealt) || damageDealt <= 0.0) {
            throw new IllegalArgumentException("Confirmed attack damage must be finite and positive");
        }
    }

    public static AttackResultContext primary(ServerPlayer attacker, LivingEntity target,
                                              AttackCategory category, DamageSource source,
                                              double damageDealt) {
        return new AttackResultContext(attacker, target, category, source, damageDealt,
                attacker.serverLevel().getGameTime(), Origin.PRIMARY);
    }

    public boolean primary() {
        return origin == Origin.PRIMARY;
    }

    public enum Origin {
        PRIMARY,
        SECONDARY
    }
}
