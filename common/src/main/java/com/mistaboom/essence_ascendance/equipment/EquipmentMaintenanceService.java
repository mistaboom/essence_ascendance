package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Shared inventory ordering/eligibility for maintenance skills and client food prediction. */
public final class EquipmentMaintenanceService {
    private EquipmentMaintenanceService() { }

    public static boolean eligible(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getMaxDamage() > 0
                && !FracturedEquipmentData.isFractured(stack);
    }

    public static boolean hasDamagedItem(Player player) {
        if (player == null) return false;
        for (ItemStack stack : carriedItems(player)) {
            if (eligible(stack) && stack.getDamageValue() > 0) return true;
        }
        return false;
    }

    /** Equipped/active gear first, then hotbar, then the rest; most-worn item wins inside each band. */
    public static List<ItemStack> prioritizedDamagedItems(Player player) {
        if (player == null) return List.of();
        List<Candidate> candidates = new ArrayList<>();
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        add(candidates, seen, player.getMainHandItem(), 0);
        add(candidates, seen, player.getOffhandItem(), 0);
        add(candidates, seen, player.getItemBySlot(EquipmentSlot.HEAD), 0);
        add(candidates, seen, player.getItemBySlot(EquipmentSlot.CHEST), 0);
        add(candidates, seen, player.getItemBySlot(EquipmentSlot.LEGS), 0);
        add(candidates, seen, player.getItemBySlot(EquipmentSlot.FEET), 0);

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) add(candidates, seen, inventory.getItem(slot), 1);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) add(candidates, seen, inventory.getItem(slot), 2);

        candidates.sort(Comparator.comparingInt(Candidate::priority)
                .thenComparing(Comparator.comparingDouble(Candidate::wear).reversed()));
        return candidates.stream().map(Candidate::stack).toList();
    }

    public static List<ItemStack> carriedItems(Player player) {
        if (player == null) return List.of();
        Inventory inventory = player.getInventory();
        List<ItemStack> items = new ArrayList<>(inventory.getContainerSize());
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.isEmpty() && seen.add(stack)) items.add(stack);
        }
        return items;
    }

    private static void add(List<Candidate> candidates, Set<ItemStack> seen, ItemStack stack, int priority) {
        if (!eligible(stack) || stack.getDamageValue() <= 0 || !seen.add(stack)) return;
        candidates.add(new Candidate(stack, priority, (double) stack.getDamageValue() / stack.getMaxDamage()));
    }

    private record Candidate(ItemStack stack, int priority, double wear) { }
}
