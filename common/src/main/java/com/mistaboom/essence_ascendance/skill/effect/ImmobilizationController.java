package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectileTargeting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Transient movement-only control. AI, facing, attacks, animation and downward gravity remain native. */
public final class ImmobilizationController {
    private static final Map<Entity, Entry> ROOTS = new WeakHashMap<>();
    private ImmobilizationController() { }

    /** A hit may refresh expiry, but never shift the original cap; expiry guarantees a duration-long recovery gap. */
    public static final class Window {
        public final long startedAt, maximumExpiry;
        public final int duration;
        private long expiresAt;
        public Window(long now, int duration, int maximumDuration) {
            if (duration <= 0 || maximumDuration < duration || now > Long.MAX_VALUE - maximumDuration - (long) duration)
                throw new IllegalArgumentException("Invalid immobilization window");
            this.startedAt = now; this.duration = duration; maximumExpiry = now + maximumDuration;
            expiresAt = now + duration;
        }
        public boolean refresh(long now) {
            if (!active(now)) return false;
            expiresAt = Math.min(maximumExpiry, Math.max(expiresAt, now + duration)); return true;
        }
        public boolean active(long now) { return now >= startedAt && now < expiresAt; }
        public boolean recovered(long now) { return now < startedAt || now >= expiresAt + duration; }
        public long expiresAt() { return expiresAt; }
    }

    private static final class Entry {
        final UUID owner;
        final ResourceLocation dimension;
        final Window window;
        final double tolerance;
        Vec3 expectedPosition;
        Entry(ServerPlayer owner, LivingEntity target, int duration, int maximum, double tolerance) {
            this.owner = owner.getUUID(); dimension = target.level().dimension().location();
            window = new Window(target.level().getGameTime(), duration, maximum);
            this.tolerance = tolerance; expectedPosition = target.position();
        }
    }

    public static boolean apply(ServerPlayer owner, LivingEntity target, int duration, int maximum, double tolerance) {
        if (!ProjectileTargeting.canHarm(owner, target) || target.isPassenger() || target.isVehicle()
                || !Double.isFinite(tolerance) || tolerance <= 0) return false;
        Entry existing = entry(target);
        if (existing != null) return existing.window.refresh(target.level().getGameTime());
        ROOTS.put(target, new Entry(owner, target, duration, maximum, tolerance));
        target.setDeltaMovement(constrain(target.getDeltaMovement())); target.hasImpulse = true;
        return true;
    }

    /** Piston movement, gravity and knockback enter the same real translation boundary. Downward settling stays safe. */
    public static Vec3 constrain(Vec3 movement) {
        return new Vec3(0, Double.isFinite(movement.y) ? Math.min(0, movement.y) : 0, 0);
    }
    public static Vec3 movement(Entity entity, Vec3 requested) {
        if (!active(entity)) return requested;
        entity.setDeltaMovement(constrain(entity.getDeltaMovement()));
        return constrain(requested);
    }
    public static void moved(Entity entity) {
        Entry entry = ROOTS.get(entity);
        if (entry != null) entry.expectedPosition = entity.position();
    }
    public static boolean active(Entity entity) {
        Entry entry = entry(entity);
        return entry != null && entry.window.active(entity.level().getGameTime());
    }
    public static double tolerance(Entity entity) {
        Entry entry = entry(entity); return entry == null ? 0 : entry.tolerance;
    }
    public static void tick(Entity entity) {
        if (active(entity)) entity.setDeltaMovement(constrain(entity.getDeltaMovement()));
    }
    private static Entry entry(Entity entity) {
        if (entity.level().isClientSide) return null;
        Entry entry = ROOTS.get(entity);
        if (entry == null) return null;
        // External teleporting releases the bind instead of dragging a creature back across space or dimensions.
        if (!entity.isAlive() || entity.isRemoved() || entity.isPassenger() || entity.isVehicle()
                || entity.isSpectator() || entity.isInvulnerable()
                || entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().invulnerable
                || !entry.dimension.equals(entity.level().dimension().location())
                || !Double.isFinite(entity.getX()) || !Double.isFinite(entity.getY()) || !Double.isFinite(entity.getZ())
                || entry.expectedPosition.distanceToSqr(entity.position()) > entry.tolerance * entry.tolerance
                || entry.window.recovered(entity.level().getGameTime())) {
            ROOTS.remove(entity); return null;
        }
        return entry;
    }
    public static void remove(Entity entity) { ROOTS.remove(entity); }
    public static void clear() { ROOTS.clear(); }
    public static List<String> diagnostics(ServerPlayer owner) {
        return ROOTS.entrySet().stream().filter(row -> row.getValue().owner.equals(owner.getUUID()))
                .map(row -> "Root #" + row.getKey().getId() + ": active=" + row.getValue().window.active(owner.level().getGameTime())
                        + "; expiry=" + row.getValue().window.expiresAt() + "; cap=" + row.getValue().window.maximumExpiry
                        + "; recovery=" + (row.getValue().window.expiresAt() + row.getValue().window.duration)
                        + "; tolerance=" + row.getValue().tolerance + "; gravity=downward; attacks=enabled")
                .sorted().limit(16).toList();
    }
}
