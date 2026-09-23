package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Source-owned, target-bound condition records with idempotent cleanup. */
public final class TargetConditionState implements SkillEffectState {
    private static final int MAX_TRACKED_TARGETS = 256;
    private final UUID ownerId;
    private final Consumer<LivingEntity> cleanup;
    private final Map<UUID, Entry> targets = new HashMap<>();

    public TargetConditionState(UUID ownerId) {
        this(ownerId, ignored -> { });
    }

    public TargetConditionState(UUID ownerId, Consumer<LivingEntity> cleanup) {
        this.ownerId = Objects.requireNonNull(ownerId);
        this.cleanup = Objects.requireNonNull(cleanup);
    }

    public void apply(LivingEntity target, long now, int durationTicks) {
        apply(target, now, durationTicks, 0.0, 0);
    }

    /** Applies or refreshes a condition with optional reusable periodic-effect metadata. */
    public void apply(LivingEntity target, long now, int durationTicks,
                      double magnitude, int pulseIntervalTicks) {
        apply(target, now, durationTicks, magnitude, pulseIntervalTicks, null);
    }
    public void apply(LivingEntity target, long now, int durationTicks,
                      double magnitude, int pulseIntervalTicks, Lineage lineage) {
        if (!target.isAlive() || target.isRemoved()) return;
        if (!Double.isFinite(magnitude) || magnitude < 0.0 || pulseIntervalTicks < 0) {
            throw new IllegalArgumentException("Invalid condition pulse metadata");
        }
        if (!targets.containsKey(target.getUUID()) && targets.size() >= MAX_TRACKED_TARGETS) {
            targets.entrySet().stream()
                    .min(Comparator.<Map.Entry<UUID, Entry>>comparingLong(entry -> entry.getValue().expiresAt)
                            .thenComparing(entry -> entry.getKey().toString()))
                    .ifPresent(entry -> remove(entry.getKey()));
        }
        Entry entry = targets.get(target.getUUID());
        boolean wasActive = entry != null && entry.target.get() == target && now < entry.expiresAt;
        if (entry == null) {
            entry = new Entry(target);
            targets.put(target.getUUID(), entry);
        }
        entry.target = new WeakReference<>(target);
        entry.expiresAt = SkillEffectMath.expiresAt(now, durationTicks);
        entry.magnitude = magnitude;
        entry.lineage = lineage;
        if (pulseIntervalTicks > 0) {
            if (!wasActive || entry.pulseIntervalTicks != pulseIntervalTicks || entry.nextPulseAt <= 0L) {
                entry.nextPulseAt = pulseAt(now, pulseIntervalTicks);
            }
            entry.pulseIntervalTicks = pulseIntervalTicks;
        } else {
            entry.nextPulseAt = 0L;
            entry.pulseIntervalTicks = 0;
        }
    }

    public boolean active(LivingEntity target, long now) {
        Entry entry = targets.get(target.getUUID());
        return entry != null && entry.target.get() == target && now < entry.expiresAt;
    }

    public long remaining(LivingEntity target, long now) {
        Entry entry = targets.get(target.getUUID());
        return entry == null ? 0 : SkillEffectMath.remaining(entry.expiresAt, now);
    }

    public boolean consume(LivingEntity target, long now) {
        if (!active(target, now)) return false;
        remove(target);
        return true;
    }

    public void remove(Entity target) {
        Entry removed = targets.remove(target.getUUID());
        if (removed != null) {
            LivingEntity entity = removed.target.get();
            if (entity != null) cleanup.accept(entity);
        }
    }

    public void reconcile(ServerPlayer owner, long now) {
        if (!owner.getUUID().equals(ownerId)) {
            clear();
            return;
        }
        for (UUID id : List.copyOf(targets.keySet())) {
            Entry entry = targets.get(id);
            LivingEntity target = entry == null ? null : entry.target.get();
            // Native loot queries reconcile the owner while die() is still running. Preserve
            // its timed condition until the completed-death callback consumes it; canceled
            // deaths do not receive a payoff, and later reconciliation still cleans up.
            if (entry == null || now >= entry.expiresAt || target == null
                    || (!target.isAlive() && !SkillEffectRuntime.resolvingDeath(target))
                    || target.isRemoved() || target.level() != owner.level()
                    || owner.serverLevel().getEntity(id) != target) {
                if (entry != null) remove(target == null ? id : target.getUUID());
            }
        }
    }

    public int size() {
        return targets.size();
    }

    public void discardInactiveLineage(java.util.function.Predicate<net.minecraft.resources.ResourceLocation> effective) {
        for (UUID id : List.copyOf(targets.keySet())) {
            Entry entry = targets.get(id);
            if (entry.lineage != null && !effective.test(entry.lineage.sourceSkill())) remove(id);
        }
    }

    public Set<UUID> targetIds() {
        return Set.copyOf(targets.keySet());
    }

    public List<LivingEntity> activeTargets(long now) {
        return targets.values().stream()
                .filter(entry -> now < entry.expiresAt)
                .map(entry -> entry.target.get())
                .filter(Objects::nonNull)
                .toList();
    }

    /** Emits at most one pulse per target per server tick and skips catch-up bursts after stalls. */
    public List<Pulse> drainDuePulses(long now) {
        List<Pulse> pulses = new java.util.ArrayList<>();
        for (Entry entry : targets.values()) {
            LivingEntity target = entry.target.get();
            if (target != null && now < entry.expiresAt && entry.pulseIntervalTicks > 0
                    && entry.nextPulseAt > 0L && now >= entry.nextPulseAt) {
                pulses.add(new Pulse(target, entry.magnitude, entry.lineage));
                entry.nextPulseAt = pulseAt(now, entry.pulseIntervalTicks);
            }
        }
        return List.copyOf(pulses);
    }

    @Override
    public void clear() {
        for (Entry entry : targets.values()) {
            LivingEntity target = entry.target.get();
            if (target != null) cleanup.accept(target);
        }
        targets.clear();
    }

    private void remove(UUID targetId) {
        Entry removed = targets.remove(targetId);
        if (removed != null) {
            LivingEntity target = removed.target.get();
            if (target != null) cleanup.accept(target);
        }
    }

    private static long pulseAt(long now, int intervalTicks) {
        return now > Long.MAX_VALUE - intervalTicks ? Long.MAX_VALUE : now + intervalTicks;
    }

    public record Lineage(PropagationBudget root, int generation, net.minecraft.resources.ResourceLocation sourceSkill) { }
    public record Pulse(LivingEntity target, double magnitude, Lineage lineage) { }

    private static final class Entry {
        WeakReference<LivingEntity> target;
        long expiresAt;
        double magnitude;
        long nextPulseAt;
        int pulseIntervalTicks;
        Lineage lineage;

        Entry(LivingEntity target) {
            this.target = new WeakReference<>(target);
        }
    }
}
