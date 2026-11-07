package com.mistaboom.essence_ascendance.gameplay;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Synchronous bounded native item-spawn receipts shared by successful reward producers. */
public final class CommittedItemDrops {
    private static final ThreadLocal<ArrayDeque<Scope>> SCOPES = new ThreadLocal<>();
    private CommittedItemDrops() { }
    public static Scope begin(ServerLevel level) { return begin(level,false); }
    public static Scope beginBlockDrops(ServerLevel level) { return begin(level,true); }
    private static Scope begin(ServerLevel level,boolean exactBlockDrops) {
        var scopes = SCOPES.get(); if (scopes == null) { scopes = new ArrayDeque<>(); SCOPES.set(scopes); }
        var scope = new Scope(level,exactBlockDrops); scopes.push(scope); return scope;
    }
    /** Bind only the directly armed native popResource supplier. Nested plugin drops cannot substitute. */
    public static java.util.function.Supplier<ItemEntity> blockDropSupplier(java.util.function.Supplier<ItemEntity> supplier) {
        var scopes = SCOPES.get(); var scope = scopes == null ? null : scopes.peek();
        if(scope == null || !scope.armed) return supplier;
        scope.armed = false;
        return () -> {
            var item=supplier.get();
            if(item != null && scope.expected.size()<256)scope.expected.add(item.getUUID());
            return item;
        };
    }
    public static void spawned(Entity entity, boolean successful) {
        var scopes = SCOPES.get(); var scope = scopes == null ? null : scopes.peek();
        if (scope == null || !successful || !(entity instanceof ItemEntity item) || item.level() != scope.level || item.getItem().isEmpty()
                || (scope.exactBlockDrops && !scope.expected.contains(item.getUUID()))) return;
        if (scope.identities.size() < 256 && scope.identities.add(item.getUUID()))
            scope.items = (int) Math.min(Integer.MAX_VALUE, (long) scope.items + item.getItem().getCount());
    }
    public static final class Scope implements AutoCloseable {
        private final ServerLevel level;
        private final Set<UUID> identities = new HashSet<>();
        private final Set<UUID> expected = new HashSet<>();
        private final boolean exactBlockDrops;
        private int items;
        private boolean closed;
        private boolean armed;
        private Scope(ServerLevel level,boolean exactBlockDrops) { this.level = level; this.exactBlockDrops = exactBlockDrops; }
        public int items() { return items; }
        public boolean contains(UUID identity) { return identities.contains(identity); }
        public void expectBlockDrop() { if (!exactBlockDrops || closed) throw new IllegalStateException("Not an active block drop receipt"); armed=true; }
        @Override public void close() {
            if (closed) return;
            if (SCOPES.get().peek() != this) throw new IllegalStateException("Item receipt scopes closed out of order");
            SCOPES.get().pop(); if (SCOPES.get().isEmpty()) SCOPES.remove(); closed = true;
        }
    }
}
