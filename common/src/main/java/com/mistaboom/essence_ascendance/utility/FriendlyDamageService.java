package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import com.mistaboom.essence_ascendance.skill.effect.SkillProcDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/** Responsible-player attribution and friendly-damage rejection shared by combat adapters. */
public final class FriendlyDamageService {
    private FriendlyDamageService() { }

    public record Snapshot(long lastBlockedAt, String targetName) { }

    public static boolean prevents(LivingEntity target, DamageSource source) {
        ServerPlayer owner = responsiblePlayer(source);
        if (owner == null || target == null || target.level() != owner.level()) return false;
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(owner);
        if (!context.isEffective(SkillIds.FRIENDLY_FIRE_WARD) || !AllyTargetingService.allied(owner, target)) return false;
        context.state(SkillIds.FRIENDLY_FIRE_WARD, State::new)
                .record(context.now(), target.getDisplayName().getString());
        return true;
    }

    public static Snapshot snapshot(SkillEffectRuntime.Context context) {
        State state = context.existingState(SkillIds.FRIENDLY_FIRE_WARD);
        return state == null ? null : state.snapshot();
    }

    private static ServerPlayer responsiblePlayer(DamageSource source) {
        SkillProcDamageService.ProcContext proc = SkillProcDamageService.current();
        if (proc != null) return proc.owner();
        if (source == null) return null;
        Entity causing = source.getEntity();
        if (causing instanceof ServerPlayer player) return player;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        return null;
    }

    private static final class State implements SkillEffectState {
        private long lastBlockedAt = Long.MIN_VALUE;
        private String targetName = "";

        void record(long now, String name) {
            lastBlockedAt = now;
            targetName = name == null ? "" : name;
        }

        Snapshot snapshot() { return new Snapshot(lastBlockedAt, targetName); }

        @Override public void clear() {
            lastBlockedAt = Long.MIN_VALUE;
            targetName = "";
        }
    }
}
