package com.mistaboom.essence_ascendance.guard;

import com.mistaboom.essence_ascendance.config.GuardBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.skill.effect.CrowdControlEligibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Correlated native attempts only. Echo is one resistance-aware knockback, inside secondary scope. */
public final class KnockbackEchoService {
    private KnockbackEchoService() { }
    public record Result(double requested, Vec3 accepted, String decision) {
        public static Result none(String reason) { return new Result(0, Vec3.ZERO, reason); }
    }
    public static Vec3 attempt(double strength, double x, double z) {
        if (!Double.isFinite(strength) || !Double.isFinite(x) || !Double.isFinite(z) || strength <= 0) return Vec3.ZERO;
        double length = Math.hypot(x, z);
        if (!Double.isFinite(length) || length < 1e-7) return Vec3.ZERO;
        // A bounded diagnostic vector; this limit never changes the original native call.
        double magnitude = Math.min(strength, 64);
        return new Vec3(-x / length * magnitude, 0, -z / length * magnitude);
    }
    public static double magnitude(Vec3 attempt, double scale, double cap) {
        if (!Double.isFinite(attempt.x) || !Double.isFinite(attempt.y) || !Double.isFinite(attempt.z)
                || !Double.isFinite(scale) || !Double.isFinite(cap) || scale <= 0 || cap <= 0) return 0;
        return Math.min(cap, Math.hypot(Math.hypot(attempt.x, attempt.z), attempt.y) * scale);
    }
    public static Vec3 bounded(Vec3 vector) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) return Vec3.ZERO;
        double length = Math.hypot(Math.hypot(vector.x, vector.z), vector.y);
        return !Double.isFinite(length) ? Vec3.ZERO : length > 64 ? vector.scale(64 / length) : vector;
    }
    public static Result echo(ServerPlayer defender, LivingEntity source, Vec3 attempt, GuardBalanceSettings.Ward tuning) {
        double strength = magnitude(attempt, tuning.knockbackEchoScale(), tuning.knockbackEchoCap());
        if (strength <= 0) return Result.none("no_finite_positive_attempt");
        if (!CrowdControlEligibility.canControl(defender, source)) return Result.none("source_control_ineligible");
        // Native knockback subtracts this defender-facing direction, pushing the source away from the defender.
        Vec3 direction = defender.position().subtract(source.position());
        if (direction.horizontalDistanceSqr() < 1e-7) direction = attempt;
        if (direction.horizontalDistanceSqr() < 1e-7) return Result.none("no_horizontal_native_echo_direction");
        Vec3 resolved = direction;
        Vec3 before = source.getDeltaMovement();
        EquipmentDamageService.withSecondarySkillDamage(() -> source.knockback(strength, resolved.x, resolved.z));
        source.hurtMarked = true;
        Vec3 accepted = source.getDeltaMovement().subtract(before);
        return new Result(strength, accepted, accepted.lengthSqr() > 0 ? "native_echo_accepted" : "native_echo_resisted_or_canceled");
    }
}
