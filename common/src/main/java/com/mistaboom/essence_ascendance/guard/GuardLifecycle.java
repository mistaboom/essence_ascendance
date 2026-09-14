package com.mistaboom.essence_ascendance.guard;

import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.WeakHashMap;

/** Continuous native use identity. Never reconstruct a perfect window from saved/client use ticks. */
public final class GuardLifecycle {
    private static final Map<ServerPlayer, Entry> ACTIVE = new WeakHashMap<>();
    private static long sequence;
    private GuardLifecycle() { }
    private static final class Entry {
        final ItemStack stack;
        final InteractionHand hand;
        final Object level;
        final long started, identity;
        final int delay;
        final String effectiveTier;
        boolean timingValid = true;
        Entry(ServerPlayer player) {
            stack = player.getUseItem(); hand = player.getUsedItemHand(); level = player.level();
            started = player.level().getGameTime(); identity = ++sequence;
            delay = EquipmentShieldService.raiseDelayTicks(player, stack);
            effectiveTier = EquipmentShieldService.context(player, stack).effectiveTier().name();
        }
    }
    public record Snapshot(boolean functional, String item, long identity, String hand, String effectiveTier,
                           long startTick, long readyTick, boolean nativeReady) {
        public static Snapshot empty() { return new Snapshot(false, "none", 0, "none", "none", -1, -1, false); }
    }
    public static void started(ServerPlayer player) {
        if (EquipmentShieldService.isUsingShield(player)) ACTIVE.put(player, new Entry(player));
    }
    public static Snapshot observe(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || !EquipmentShieldService.isUsingShield(player)) {
            ACTIVE.remove(player); return Snapshot.empty();
        }
        ItemStack stack = player.getUseItem();
        Entry entry = ACTIVE.get(player);
        if (entry != null && (entry.stack != stack || entry.hand != player.getUsedItemHand()
                || entry.level != player.level() || player.level().getGameTime() < entry.started)) {
            ACTIVE.remove(player); entry = null;
        }
        var equipment = EquipmentShieldService.context(player, stack);
        if (entry != null && (entry.delay != EquipmentShieldService.raiseDelayTicks(player, stack)
                || !entry.effectiveTier.equals(equipment.effectiveTier().name()))) entry.timingValid = false;
        long start = entry == null || !entry.timingValid ? -1 : entry.started;
        return new Snapshot(true, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                entry == null ? 0 : entry.identity, player.getUsedItemHand().name(), equipment.effectiveTier().name(),
                start, start < 0 ? -1 : start + entry.delay, player.isBlocking());
    }
    /** Changing a skill cannot create a fresh window while the native use remains continuous. */
    public static void invalidateTiming(ServerPlayer player) {
        Entry entry = ACTIVE.get(player); if (entry != null) entry.timingValid = false;
    }
    public static void forget(ServerPlayer player) { ACTIVE.remove(player); }
    public static void clear() { ACTIVE.clear(); }
}
