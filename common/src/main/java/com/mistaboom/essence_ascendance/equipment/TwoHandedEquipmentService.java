package com.mistaboom.essence_ascendance.equipment;
import com.mistaboom.essence_ascendance.network.PlayerMessageCompat;

import com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server authority. Slots, stacks and native equipment queries are never replaced or filtered. */
public final class TwoHandedEquipmentService {
    public enum Status { PENDING, COMPATIBLE, INCOMPATIBLE }
    public record Decision(Status status, boolean twoHanded, boolean actionWeapon,
                           boolean offhandUse, boolean offhandVisible, long revision, long generation) {
        public boolean mayAttack() { return status == Status.COMPATIBLE; }
    }
    private static final Decision PENDING = new Decision(Status.PENDING, false, false, false, true, 0, -1);
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
    private static final ThreadLocal<Integer> COMBAT = ThreadLocal.withInitial(() -> 0);
    private TwoHandedEquipmentService() { }

    private static final class State {
        ItemStack main, off;
        HandKey mainKey, offKey;
        int selected = -1, depth;
        long mainRevision, offRevision, revision, lastMessage = Long.MIN_VALUE;
        boolean refresh = true;
        Decision decision = PENDING;
    }
    private record HandKey(net.minecraft.world.item.Item item, Object components) { }
    private static HandKey key(ItemStack stack) {
        return stack.isEmpty() ? new HandKey(net.minecraft.world.item.Items.AIR, null)
                : new HandKey(stack.getItem(), TwoHandedEquipmentPolicy.semanticKey(stack));
    }
    private static long revision(ItemStack stack) { return ((EquipmentPolicyStack)(Object) stack).essenceAscendance$equipmentRevision(); }
    private static boolean current(ServerPlayer player, State s) {
        return s.main == player.getMainHandItem() && s.off == player.getOffhandItem()
                && s.selected == selectedSlot(player) && s.mainRevision == revision(s.main)
                && s.offRevision == revision(s.off) && s.decision.generation() == TwoHandedEquipmentPolicy.generation();
    }

    /** O(1), with no role/provider/tag/component inspection. Unexpected staleness fails quietly until reconciliation. */
    public static Decision decision(ServerPlayer player) {
        State s = STATES.get(player);
        if (s == null) return PENDING;
        if (s.depth != 0 || s.refresh || !current(player, s)) {
            s.refresh = true;
            return new Decision(Status.PENDING, s.decision.twoHanded(), s.decision.actionWeapon(),
                    false, s.decision.offhandVisible(), s.revision, TwoHandedEquipmentPolicy.generation());
        }
        return s.decision;
    }
    public static boolean validateAttack(ServerPlayer player) {
        var d = decision(player);
        if (d.status() == Status.INCOMPATIBLE) {
            var s = STATES.get(player);
            long now = player.level().getGameTime();
            if (s.lastMessage == Long.MIN_VALUE || now < s.lastMessage || now - s.lastMessage >= 20) {
                PlayerMessageCompat.send(player, Component.translatable("message.essence_ascendance.incompatible_offhand"), true);
                s.lastMessage = now;
            }
        }
        return d.mayAttack();
    }
    public static boolean permitsOffhandUse(Player player) {
        return player instanceof ServerPlayer server ? decision(server).offhandUse()
                : com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.clientDecision(player).offhandUse();
    }
    public static boolean permitsHeldShield(Player player) { return permitsOffhandUse(player); }

    /** Nest across packet, menu, inventory and equipment setters; resolve only the completed outer operation. */
    public static void begin(Player player) {
        if (player instanceof ServerPlayer server) STATES.computeIfAbsent(server, p -> new State()).depth++;
    }
    public static void end(Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        var s = STATES.get(server);
        if (s == null || s.depth == 0) return;
        if (--s.depth == 0) reconcile(server);
    }
    public static void refresh(ServerPlayer player) {
        STATES.computeIfAbsent(player, p -> new State()).refresh = true;
        reconcile(player);
    }
    public static void reconcile(ServerPlayer player) {
        var s = STATES.computeIfAbsent(player, p -> new State());
        if (s.depth != 0) return;
        if (COMBAT.get() != 0) {
            if (!current(player, s) && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND)
                player.stopUsingItem();
            return;
        }
        if (!s.refresh && current(player, s)) { TwoHandedPolicyPayload.sync(player, s.decision); return; }
        resolve(player, s);
    }
    private static void capture(ServerPlayer player, State s) {
        WeaponActionRuntime.equipmentChanged(player);
        s.main = player.getMainHandItem(); s.off = player.getOffhandItem();
        s.selected = selectedSlot(player);
        s.mainRevision = revision(s.main); s.offRevision = revision(s.off);
        if (!s.main.isEmpty()) ((EquipmentPolicyStack)(Object)s.main).essenceAscendance$watchHand(player);
        if (!s.off.isEmpty()) ((EquipmentPolicyStack)(Object)s.off).essenceAscendance$watchHand(player);
    }
    private static void resolve(ServerPlayer player, State s) {
        var mainKey = key(player.getMainHandItem());
        var offKey = key(player.getOffhandItem());
        boolean sameRules = s.decision.generation() == TwoHandedEquipmentPolicy.generation();
        boolean sameMain = sameRules && mainKey.equals(s.mainKey);
        boolean sameOff = sameRules && offKey.equals(s.offKey);
        capture(player, s);
        if (sameMain && sameOff) { s.refresh = false; TwoHandedPolicyPayload.sync(player, s.decision); return; }
        var caps = sameMain ? null : TwoHandedEquipmentPolicy.capabilities(s.main);
        boolean two = sameMain ? s.decision.twoHanded()
                : caps.hands() == EquipmentWeaponCapabilities.HandRequirement.MAIN_HAND_TWO_HANDED;
        boolean compatible = !two || (sameOff && s.decision.twoHanded() ? s.decision.mayAttack()
                : TwoHandedEquipmentPolicy.compatible(s.off, player));
        s.decision = new Decision(compatible ? Status.COMPATIBLE : Status.INCOMPATIBLE, two,
                sameMain ? s.decision.actionWeapon() : caps.melee() == EquipmentWeaponCapabilities.MeleeExecution.ACTION, !two, !two,
                ++s.revision, TwoHandedEquipmentPolicy.generation());
        s.mainKey = mainKey; s.offKey = offKey;
        s.refresh = false;
        if (two && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) player.stopUsingItem();
        TwoHandedPolicyPayload.sync(player, s.decision);
    }

    /** Called only by the two observed hand stacks; no global ownership lookup or scan. */
    public static void stackChanged(ServerPlayer player, ItemStack stack, boolean becameEmpty) {
        var s = STATES.get(player);
        if (s == null || (stack != player.getMainHandItem() && stack != player.getOffhandItem())) return;
        // Native totem consumption is compatible -> empty, even during damage. Update stamps without classification.
        if (becameEmpty && stack == s.off && s.decision.status() == Status.COMPATIBLE
                && s.depth == 0 && s.main == player.getMainHandItem() && s.mainRevision == revision(s.main)
                && s.decision.generation() == TwoHandedEquipmentPolicy.generation()) {
            capture(player, s);
            s.offKey = new HandKey(net.minecraft.world.item.Items.AIR, null);
            s.decision = new Decision(Status.COMPATIBLE, s.decision.twoHanded(), s.decision.actionWeapon(),
                    s.decision.offhandUse(), s.decision.offhandVisible(), ++s.revision, s.decision.generation());
            s.refresh = false;
            TwoHandedPolicyPayload.sync(player, s.decision);
            return;
        }
        s.refresh = true;
        if (s.depth == 0 && COMBAT.get() == 0) reconcile(player);
    }
    public static void enterCombat() { COMBAT.set(COMBAT.get() + 1); }
    public static void exitCombat() { COMBAT.set(COMBAT.get() - 1); }
    public static void rulesChanged() {
        for (var player : new ArrayList<>(STATES.keySet())) {
            STATES.get(player).refresh = true;
            reconcile(player);
        }
    }
    public static void forget(ServerPlayer player) { STATES.remove(player); TwoHandedPolicyPayload.forget(player); }

    private static int selectedSlot(ServerPlayer player) {
        //? if >=26.1.2
        /*return player.getInventory().getSelectedSlot();*/
        //? if <26.1.2
        return player.getInventory().selected;
    }
}
