package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Bounded, source-owned target buildup with weak entity references and exact expiry. */
public final class SourceOwnedBuildupState implements SkillEffectState {
    private static final int MAX_TRACKED_TARGETS = 256;
    private final UUID ownerId;
    private final ExpiryPolicy expiryPolicy;
    private final Consumer<LivingEntity> cleanup;
    private final Map<UUID, Entry> targets = new HashMap<>();

    public SourceOwnedBuildupState(UUID ownerId) {
        this(ownerId, ExpiryPolicy.SHARED_WINDOW, ignored -> { });
    }

    public SourceOwnedBuildupState(UUID ownerId, Consumer<LivingEntity> cleanup) {
        this(ownerId, ExpiryPolicy.SHARED_WINDOW, cleanup);
    }

    public SourceOwnedBuildupState(UUID ownerId, ExpiryPolicy expiryPolicy,
                                   Consumer<LivingEntity> cleanup) {
        this.ownerId = Objects.requireNonNull(ownerId);
        this.expiryPolicy = Objects.requireNonNull(expiryPolicy);
        this.cleanup = Objects.requireNonNull(cleanup);
    }

    public int add(LivingEntity target, long now, int cap, int gain, int durationTicks) {
        if (cap <= 0 || gain <= 0 || !target.isAlive() || target.isRemoved()) return 0;
        if (!targets.containsKey(target.getUUID()) && targets.size() >= MAX_TRACKED_TARGETS) {
            targets.entrySet().stream()
                    .min(Comparator.<Map.Entry<UUID, Entry>>comparingLong(
                                    entry -> entry.getValue().stacks.nextExpiry())
                            .thenComparing(entry -> entry.getKey().toString()))
                    .ifPresent(entry -> remove(entry.getKey()));
        }
        Entry entry = targets.computeIfAbsent(target.getUUID(), ignored -> new Entry(target, expiryPolicy));
        entry.target = new WeakReference<>(target);
        entry.stacks.reconcile(now, cap);
        for (int i = 0; i < gain; i++) entry.stacks.grant(now, cap, durationTicks, false);
        return entry.stacks.count();
    }

    public void remove(Entity target) {
        remove(target.getUUID());
    }

    public void remove(UUID targetId) {
        Entry removed = targets.remove(targetId);
        if (removed != null) cleanup(removed.target.get());
    }

    public void reconcile(ServerPlayer owner, long now, int cap) {
        if (!owner.getUUID().equals(ownerId)) {
            clear();
            return;
        }
        for (UUID id : List.copyOf(targets.keySet())) {
            Entry entry = targets.get(id);
            LivingEntity target = entry == null ? null : entry.target.get();
            if (entry != null) entry.stacks.reconcile(now, cap);
            if (entry == null || entry.stacks.count() <= 0 || cap <= 0 || target == null
                    || !target.isAlive() || target.isRemoved() || target.level() != owner.level()
                    || owner.serverLevel().getEntity(id) != target) {
                remove(id);
            }
        }
    }

    public int size() {
        return targets.size();
    }

    public Set<UUID> targetIds() {
        return Set.copyOf(targets.keySet());
    }

    public Snapshot mostDeveloped(long now, int cap) {
        if (cap <= 0) return null;
        return targets.values().stream()
                .filter(entry -> entry.stacks.count() > 0 && entry.target.get() != null)
                .map(entry -> new Snapshot(entry.target.get(), entry.stacks.count(), entry.stacks.nextExpiry()))
                .max(Comparator.comparingInt(Snapshot::stacks)
                        .thenComparing(snapshot -> snapshot.target().getUUID().toString()))
                .orElse(null);
    }

    public List<Snapshot> snapshots(long now) {
        List<Snapshot> result = new ArrayList<>();
        for (Entry entry : targets.values()) {
            LivingEntity target = entry.target.get();
            if (target != null && entry.stacks.count() > 0) {
                result.add(new Snapshot(target, entry.stacks.count(), entry.stacks.nextExpiry()));
            }
        }
        result.sort(Comparator.comparing(snapshot -> snapshot.target().getUUID().toString()));
        return List.copyOf(result);
    }

    @Override
    public void clear() {
        for (Entry entry : targets.values()) cleanup(entry.target.get());
        targets.clear();
    }

    private void cleanup(LivingEntity target) {
        if (target != null) cleanup.accept(target);
    }

    public record Snapshot(LivingEntity target, int stacks, long expiresAt) { }

    public enum ExpiryPolicy {
        SHARED_WINDOW,
        INDEPENDENT_STACKS
    }

    private static final class Entry {
        WeakReference<LivingEntity> target;
        final TimedStackState stacks;

        Entry(LivingEntity target, ExpiryPolicy policy) {
            this.target = new WeakReference<>(target);
            this.stacks = policy == ExpiryPolicy.SHARED_WINDOW
                    ? TimedStackState.shared() : TimedStackState.independent();
        }
    }
}
