package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** Completed native death plus either ordinary player or bounded proc attribution. */
public record SkillDeathContext(LivingEntity victim, DamageSource source,
                                ServerPlayer attributedPlayer,
                                SkillProcDamageService.ProcContext proc) {
    public SkillDeathContext {
        Objects.requireNonNull(victim);
        Objects.requireNonNull(source);
    }

    public boolean ordinaryKillBy(ServerPlayer player) {
        return proc == null && attributedPlayer == player;
    }
}
