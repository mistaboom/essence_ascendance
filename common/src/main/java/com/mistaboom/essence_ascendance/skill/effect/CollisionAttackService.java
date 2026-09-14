package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** A physical-box sweep of completed native translation, bounded by distance, terrain, action contacts and cooldown. */
public final class CollisionAttackService {
    private CollisionAttackService() { }
    public record Shape(AABB initial, Vec3 movement) {
        public AABB bounds() { return initial.expandTowards(movement); }
    }
    public record Candidate(UUID id, AABB box, boolean eligible, boolean visible) { }
    public record Contact(UUID id, double fraction, Vec3 point) { }
    public static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
    /** Already overlapping targets are deliberately excluded: standing nearby never becomes a fresh impact. */
    public static List<Contact> select(Shape shape, double minimumSpeed, double maximumSweep, int budget,
                                       List<Candidate> candidates) {
        Vec3 movement = shape.movement();
        if (!finite(movement) || movement.horizontalDistance() < minimumSpeed
                || movement.length() > maximumSweep || budget <= 0) return List.of();
        AABB initial = shape.initial();
        Vec3 start = initial.getCenter(), end = start.add(movement);
        Vec3 direction = new Vec3(movement.x, 0, movement.z).normalize();
        return candidates.stream().filter(c -> c.eligible() && c.visible() && !initial.intersects(c.box())
                        && c.box().getCenter().subtract(start).dot(direction) > 0)
                .map(c -> {
                    AABB expanded = c.box().inflate(initial.getXsize() / 2, initial.getYsize() / 2, initial.getZsize() / 2);
                    return expanded.clip(start, end).map(point -> new Contact(c.id(),
                            Math.sqrt(point.distanceToSqr(start) / movement.lengthSqr()), point)).orElse(null);
                }).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(Contact::fraction).thenComparing(Contact::id))
                .limit(budget).toList();
    }
    /** One continuous charge has one contact budget; recent victims stay protected across re-raises. */
    public static final class Ledger {
        private final Map<UUID, Long> recent = new LinkedHashMap<>();
        private final java.util.Set<UUID> contacts = new java.util.HashSet<>();
        private long lastTick = Long.MIN_VALUE;
        private boolean charging;
        public void posture(boolean active, long now) {
            if (now < lastTick) { recent.clear(); contacts.clear(); charging = false; }
            lastTick = now;
            recent.entrySet().removeIf(entry -> entry.getValue() <= now);
            if (!active || !charging) contacts.clear();
            charging = active;
        }
        public int remaining(int maximum) { return charging ? Math.max(0, maximum - contacts.size()) : 0; }
        public boolean eligible(UUID target, long now) { return charging && !contacts.contains(target) && recent.getOrDefault(target, Long.MIN_VALUE) <= now; }
        public boolean accept(UUID target, long now, int cooldown, int maximum) {
            if (!eligible(target, now) || remaining(maximum) == 0) return false;
            contacts.add(target); recent.put(target, now + cooldown);
            while (recent.size() > 128) recent.remove(recent.keySet().iterator().next());
            return true;
        }
    }
    public static List<Contact> sweep(ServerPlayer owner, Shape shape, double minimumSpeed, double maximumSweep,
                                       int budget, Predicate<LivingEntity> eligible) {
        if (!finite(shape.movement()) || shape.movement().length() > maximumSweep) return List.of();
        List<LivingEntity> nearby = owner.serverLevel().getEntitiesOfClass(LivingEntity.class, shape.bounds(), eligible);
        return select(shape, minimumSpeed, maximumSweep, budget, nearby.stream().map(target -> new Candidate(
                target.getUUID(), target.getBoundingBox(), eligible.test(target),
                owner.level().getWorldBorder().isWithinBounds(target.getBoundingBox())
                        && visible(owner, shape.initial().getCenter(), target.getBoundingBox().getCenter()))).toList());
    }
    public static boolean visible(ServerPlayer owner, Vec3 start, Vec3 end) {
        return owner.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    /** Equal forward/lateral impulse clears the lane without launching a target vertically. Stable UUID resolves center ties. */
    public static Vec3 pushDirection(Vec3 movement, Vec3 relative, UUID target) {
        Vec3 forward = new Vec3(movement.x, 0, movement.z).normalize();
        Vec3 side = new Vec3(forward.z, 0, -forward.x);
        double offset = relative.dot(side);
        double sign = offset == 0 ? ((target.getLeastSignificantBits() & 1) == 0 ? 1 : -1) : Math.signum(offset);
        return forward.add(side.scale(sign)).normalize();
    }
}
