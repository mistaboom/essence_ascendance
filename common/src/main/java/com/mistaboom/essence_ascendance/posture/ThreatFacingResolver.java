package com.mistaboom.essence_ascendance.posture;

import com.mistaboom.essence_ascendance.config.PostureBalanceSettings;
import com.mistaboom.essence_ascendance.projectile.ProjectileOwnership;
import com.mistaboom.essence_ascendance.projectile.ProjectileTargeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded spatial query with deterministic selection and the existing native hostility/ownership policy. */
public final class ThreatFacingResolver {
    private ThreatFacingResolver() { }
    public record Result(UUID threat, boolean facing, double distance, double dot, int candidates, String decision) {
        public static Result none(String reason) { return new Result(null, false, 0, 0, 0, reason); }
    }
    public static Result resolve(ServerPlayer player, PostureBalanceSettings.Bulwark tuning) {
        List<LivingEntity> candidates = new ArrayList<>();
        player.serverLevel().getEntities(EntityTypeTest.forClass(LivingEntity.class),
                player.getBoundingBox().inflate(tuning.threatRange()),
                target -> valid(player, target, tuning.threatRange()), candidates, tuning.maximumThreats() + 1);
        // Overflow fails closed; enumeration order cannot choose an arbitrary favorable subset.
        if (candidates.size() > tuning.maximumThreats()) return Result.none("threat_candidate_limit");
        return candidates.stream().filter(target -> facing(player.getEyePosition(), player.getLookAngle(),
                        target.getEyePosition(), tuning.facingDegrees()) && player.hasLineOfSight(target))
                .sorted(Comparator.<LivingEntity>comparingDouble(player::distanceToSqr).thenComparing(LivingEntity::getUUID))
                .findFirst().map(target -> new Result(target.getUUID(), true, player.distanceTo(target),
                        dot(player.getEyePosition(), player.getLookAngle(), target.getEyePosition()), candidates.size(), "visible_hostile_frontal_threat"))
                .orElse(new Result(null, false, 0, 0, candidates.size(), "no_visible_frontal_threat"));
    }
    public static boolean incoming(ServerPlayer player, LivingEntity source, PostureBalanceSettings.Bulwark tuning) {
        return valid(player, source, tuning.threatRange()) && player.hasLineOfSight(source)
                && facing(player.getEyePosition(), player.getLookAngle(), source.getEyePosition(), tuning.facingDegrees());
    }
    private static boolean valid(ServerPlayer player, LivingEntity target, double range) {
        return target != null && target.level() == player.level() && target.distanceToSqr(player) <= range * range
                && ProjectileOwnership.hostileDamageSource(player, target) && ProjectileTargeting.hostile(player, target);
    }
    public static double dot(Vec3 origin, Vec3 view, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        if (!Double.isFinite(delta.lengthSqr()) || !Double.isFinite(view.lengthSqr())
                || delta.lengthSqr() < 1e-12 || view.lengthSqr() < 1e-12) return -1;
        return Math.clamp(delta.normalize().dot(view.normalize()), -1, 1);
    }
    public static boolean facing(Vec3 origin, Vec3 view, Vec3 target, double degrees) {
        return Double.isFinite(degrees) && degrees >= 0 && degrees < 90
                && dot(origin, view, target) >= Math.cos(Math.toRadians(degrees));
    }
}
