package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectileTargeting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Creature-only bursts: no native explosion, block mutation, fire placement, drops or knockback. */
public final class SafeCreatureAreaService {
    private static final int REJECTION_SAMPLE_LIMIT = 16;
    private static final int RECENT_SKILL_LIMIT = 8;
    private static final Map<ServerPlayer, LinkedHashMap<ResourceLocation, String>> RECENT = new WeakHashMap<>();
    private SafeCreatureAreaService() { }

    public record Candidate(UUID id, Vec3 center, boolean eligible) { }
    /** Observe the existing bounded-area query; never perform a second entity scan for diagnostics. */
    private static final class Rejections {
        final java.util.TreeMap<UUID, String> sample = new java.util.TreeMap<>();
        int count;
        void add(LivingEntity target, String reason) {
            count++;
            sample.put(target.getUUID(), reason);
            if (sample.size() > REJECTION_SAMPLE_LIMIT) sample.pollLastEntry();
        }
    }

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
        return burst(owner, origin, radius, limit, damage, skill, kind, budget, generation,
                beforeDamage, target -> true, target -> {});
    }
    /** Optional extra geometry/hostility policy and feedback after a confirmed native damage write. */
    public static int burst(ServerPlayer owner, Vec3 origin, double radius, int limit, float damage,
                            ResourceLocation skill, SkillProcDamageService.DamageKind kind,
                            PropagationBudget budget, int generation, Consumer<LivingEntity> beforeDamage,
                            Predicate<LivingEntity> eligible, Consumer<LivingEntity> afterDamage) {
        if (!owner.isAlive() || owner.isRemoved() || owner.isSpectator() || !Float.isFinite(damage)
                || damage <= 0 || !Double.isFinite(radius) || radius <= 0 || limit <= 0
                || !budget.canContinue(generation)) return 0;
        Rejections rejected = new Rejections();
        List<LivingEntity> nearby = owner.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, origin).inflate(radius), target -> {
                    if (!ProjectileTargeting.canHarm(owner, target)) {
                        rejected.add(target, target == owner ? "owner_excluded" : "combat_team_pvp_immunity_or_lifecycle");
                        return false;
                    }
                    if (!eligible.test(target)) {
                        rejected.add(target, "extra_hostility_or_geometry_policy"); return false;
                    }
                    return true;
                });
        Map<UUID, LivingEntity> entities = new java.util.HashMap<>();
        nearby.forEach(target -> entities.put(target.getUUID(), target));
        List<UUID> selected = select(origin, radius, limit, budget.visitedIds(), nearby.stream()
                .map(target -> new Candidate(target.getUUID(), target.getBoundingBox().getCenter(), true)).toList());
        Set<UUID> selection = Set.copyOf(selected);
        for (LivingEntity target : nearby) {
            if (selection.contains(target.getUUID())) continue;
            rejected.add(target, budget.visitedIds().contains(target.getUUID()) ? "excluded_or_previously_visited"
                    : target.getBoundingBox().getCenter().distanceToSqr(origin) > radius * radius ? "outside_sphere" : "target_limit");
        }
        int accepted = 0;
        for (UUID id : selected) {
            LivingEntity target = entities.get(id);
            // Nested death callbacks may have changed eligibility or consumed the shared propagation budget.
            if (!ProjectileTargeting.canHarm(owner, target)) { rejected.add(target, "changed_combat_eligibility"); continue; }
            if (!eligible.test(target)) { rejected.add(target, "changed_extra_policy"); continue; }
            if (!budget.tryVisit(id, generation)) { rejected.add(target, "shared_propagation_budget"); continue; }
            beforeDamage.accept(target);
            if (SkillProcDamageService.hurt(owner, target, damage, kind, skill, budget, generation)) { accepted++; afterDamage.accept(target); }
            else rejected.add(target, "native_damage_unconfirmed");
        }
        var recent = RECENT.computeIfAbsent(owner, ignored -> new LinkedHashMap<>());
        recent.remove(skill);
        recent.put(skill, "Safe area: skill=" + skill + "; responsible=" + owner.getUUID() + "; radius=" + radius
                + "; selected=" + selected + "; accepted=" + accepted + "; rejected_count=" + rejected.count
                + "; rejected_sample=" + rejected.sample + "; omitted=" + Math.max(0, rejected.count - rejected.sample.size())
                + "; terrain/fire=false; generation=" + generation);
        while (recent.size() > RECENT_SKILL_LIMIT) recent.remove(recent.keySet().iterator().next());
        return accepted;
    }
    public static List<String> diagnostics(ServerPlayer player) {
        var recent = RECENT.get(player);
        return recent == null || recent.isEmpty() ? List.of() : List.of(recent.lastEntry().getValue());
    }
    /** Preserve the latest relevant effect even when another safe-area skill has fired afterward. */
    public static List<String> diagnostics(ServerPlayer player, ResourceLocation skill) {
        var recent = RECENT.get(player);
        String line = recent == null ? null : recent.get(skill);
        return line == null ? List.of() : List.of(line);
    }
    public static void clearDiagnostics() { RECENT.clear(); }
}
