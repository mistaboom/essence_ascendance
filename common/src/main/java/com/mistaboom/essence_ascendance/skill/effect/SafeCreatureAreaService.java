package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectileTargeting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Creature-only bursts: no native explosion, block mutation, fire placement, drops or knockback. */
public final class SafeCreatureAreaService {
    private static final Map<ServerPlayer, String> RECENT = new WeakHashMap<>();
    private SafeCreatureAreaService() { }

    public record Candidate(UUID id, Vec3 center, boolean eligible) { }

    /** Pure deterministic target resolution also used by the real server query. */
    public static List<UUID> select(Vec3 origin, double radius, int limit, Set<UUID> excluded,
                                    List<Candidate> candidates) {
        if (!Double.isFinite(radius) || radius <= 0 || limit <= 0) return List.of();
        return candidates.stream().filter(candidate -> candidate.eligible() && !excluded.contains(candidate.id())
                        && candidate.center().distanceToSqr(origin) <= radius * radius)
                .sorted(Comparator.<Candidate>comparingDouble(candidate -> candidate.center().distanceToSqr(origin))
                        .thenComparing(Candidate::id)).limit(limit).map(Candidate::id).toList();
    }

    public static int burst(ServerPlayer owner, Vec3 origin, double radius, int limit, float damage,
                            ResourceLocation skill, SkillProcDamageService.DamageKind kind,
                            PropagationBudget budget, int generation, Consumer<LivingEntity> beforeDamage) {
        if (!owner.isAlive() || owner.isRemoved() || owner.isSpectator() || !Float.isFinite(damage)
                || damage <= 0 || !Double.isFinite(radius) || radius <= 0 || limit <= 0
                || !budget.canContinue(generation)) return 0;
        List<LivingEntity> nearby = owner.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, origin).inflate(radius), target -> ProjectileTargeting.canHarm(owner, target));
        Map<UUID, LivingEntity> entities = new java.util.HashMap<>();
        nearby.forEach(target -> entities.put(target.getUUID(), target));
        List<UUID> selected = select(origin, radius, limit, budget.visitedIds(), nearby.stream()
                .map(target -> new Candidate(target.getUUID(), target.getBoundingBox().getCenter(), true)).toList());
        int accepted = 0;
        for (UUID id : selected) {
            LivingEntity target = entities.get(id);
            // Nested death callbacks may have changed eligibility or consumed the shared propagation budget.
            if (!ProjectileTargeting.canHarm(owner, target) || !budget.tryVisit(id, generation)) continue;
            beforeDamage.accept(target);
            if (SkillProcDamageService.hurt(owner, target, damage, kind, skill, budget, generation)) accepted++;
        }
        RECENT.put(owner, "Safe area: skill=" + skill + "; responsible=" + owner.getUUID() + "; radius=" + radius
                + "; selected=" + selected + "; accepted=" + accepted + "; terrain/fire=false; generation=" + generation);
        return accepted;
    }
    public static List<String> diagnostics(ServerPlayer player) {
        String recent = RECENT.get(player); return recent == null ? List.of() : List.of(recent);
    }
    public static void clearDiagnostics() { RECENT.clear(); }
}
