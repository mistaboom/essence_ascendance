package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.ImmobilizationController;
import com.mistaboom.essence_ascendance.skill.effect.PropagationBudget;
import com.mistaboom.essence_ascendance.skill.effect.SafeCreatureAreaService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillProcDamageService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** The catalog payload choice composes independently with every path and every opted-in launch source. */
final class OffenseProjectilePayloads {
    private OffenseProjectilePayloads() { }
    static void register() {
        ProjectileImpactEffects.register(new Explosive()); ProjectileImpactEffects.register(new Rooting());
    }

    private abstract static class Base implements ProjectileImpactEffects.Payload {
        @Override public CompoundTag snapshot(ServerPlayer owner) {
            var settings = SkillEffectRuntime.resolvedSettings(owner).projectiles();
            CompoundTag data = new CompoundTag();
            data.putInt("TriggerBudget", settings.payloadTriggerBudget());
            data.putInt("Particles", settings.payloadParticleCount());
            return data;
        }
        @Override public boolean validSnapshot(CompoundTag data) {
            return integer(data, "TriggerBudget", 0, 32) && integer(data, "Particles", 0, 64);
        }
        boolean integer(CompoundTag data, String key, int minimum, int maximum) {
            return data.contains(key, net.minecraft.nbt.Tag.TAG_INT) && data.getInt(key) >= minimum && data.getInt(key) <= maximum;
        }
        boolean number(CompoundTag data, String key, double minimum, double maximum) {
            return data.contains(key, net.minecraft.nbt.Tag.TAG_DOUBLE) && Double.isFinite(data.getDouble(key))
                    && data.getDouble(key) >= minimum && data.getDouble(key) <= maximum;
        }
    }
    private static final class Explosive extends Base {
        @Override public ResourceLocation skillId() { return SkillIds.EXPLOSIVE_PAYLOAD; }
        @Override public CompoundTag snapshot(ServerPlayer owner) {
            CompoundTag data = super.snapshot(owner);
            var settings = SkillEffectRuntime.resolvedSettings(owner);
            data.putDouble("Radius", settings.projectiles().explosiveRadius());
            data.putDouble("DamageScale", settings.projectiles().explosiveDamageScale());
            data.putInt("TargetLimit", settings.combustion().targetsPerBurst());
            return data;
        }
        @Override public boolean validSnapshot(CompoundTag data) {
            return super.validSnapshot(data) && number(data, "Radius", 0, 32)
                    && number(data, "DamageScale", 0, 4) && integer(data, "TargetLimit", 0, 100);
        }
        @Override public void impact(ProjectileImpactEffects.Impact impact, CompoundTag data) {
            PropagationBudget budget = new PropagationBudget(0, data.getInt("TargetLimit"));
            // The direct victim already received native damage; the burst affects nearby eligible creatures.
            budget.seed(impact.victim().getUUID());
            SafeCreatureAreaService.burst(impact.owner(), impact.hit().getLocation(), data.getDouble("Radius"),
                    data.getInt("TargetLimit"), (float) (impact.confirmedDamage() * data.getDouble("DamageScale")),
                    skillId(), SkillProcDamageService.DamageKind.EXPLOSIVE_PAYLOAD, budget, 0, target -> { });
            int count = data.getInt("Particles");
            if (count > 0) {
                var point = impact.hit().getLocation();
                impact.owner().serverLevel().sendParticles(ParticleTypes.POOF, point.x, point.y, point.z,
                        count, data.getDouble("Radius") / 8, data.getDouble("Radius") / 8, data.getDouble("Radius") / 8, 0.02);
                impact.owner().serverLevel().playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE,
                        SoundSource.PLAYERS, 0.35F, 1.5F);
            }
        }
    }
    private static final class Rooting extends Base {
        @Override public ResourceLocation skillId() { return SkillIds.ROOTING_PAYLOAD; }
        @Override public CompoundTag snapshot(ServerPlayer owner) {
            CompoundTag data = super.snapshot(owner);
            var settings = SkillEffectRuntime.resolvedSettings(owner).projectiles();
            data.putInt("Duration", settings.rootDurationTicks()); data.putInt("MaximumDuration", settings.rootMaxDurationTicks());
            data.putDouble("MovementTolerance", settings.rootMovementTolerance()); return data;
        }
        @Override public boolean validSnapshot(CompoundTag data) {
            return super.validSnapshot(data) && integer(data, "Duration", 1, 200)
                    && integer(data, "MaximumDuration", data.getInt("Duration"), 400)
                    && number(data, "MovementTolerance", 0.001, 1);
        }
        @Override public void impact(ProjectileImpactEffects.Impact impact, CompoundTag data) {
            if (ImmobilizationController.apply(impact.owner(), impact.victim(), data.getInt("Duration"),
                    data.getInt("MaximumDuration"), data.getDouble("MovementTolerance")) && data.getInt("Particles") > 0)
                SkillProcDamageService.particles(impact.owner(), impact.victim(), ParticleTypes.ENCHANT,
                        data.getInt("Particles"), 0.25, 0.01);
        }
    }
}
