package com.mistaboom.essence_ascendance.neoforge;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Tests the actual production transaction journal against NeoForge's native transfer API. */
public final class MachineTransfer2612Test {
    private static int checks;
    private static final class Inventory extends MachineNeoForgeResourceHandler {
        final ItemStack[] items = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        int active = 3, commits, flags;
        boolean allowed = true;
        Inventory() { super(3); }
        public int size() { return active; }
        ItemStack stack(int i) { return items[i]; }
        void write(int i, ItemStack stack) { items[i] = stack; }
        boolean insertable(int i, ItemResource resource) { return allowed && i != 1; }
        boolean extractable(int i, ItemResource resource) { return allowed && i == 1; }
        int capacity(int i, ItemResource resource) { return i == 0 ? 4 : resource.getMaxStackSize(); }
        int contextFlags(int i, ItemStack before, ItemStack after) {
            return ItemStack.isSameItemSameComponents(before, after) ? 0 : i == 0 ? 3 : 1;
        }
        void committed(int value) { commits++; flags = value; }
    }
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemResource stone = ItemResource.of(Items.STONE);
        ItemResource named = stone.with(DataComponents.CUSTOM_NAME, Component.literal("Distinct components"));
        Inventory inventory = new Inventory();
        try (var root = Transaction.openRoot()) {
            check(inventory.insert(0, stone, 99, root) == 4, "Workpiece capacity is enforced");
            check(inventory.items[0].getCount() == 4 && inventory.commits == 0, "Provisional write has no commit effects");
        }
        check(inventory.items[0].isEmpty() && inventory.commits == 0, "Root abort restores content and suppresses effects");
        try (var root = Transaction.openRoot()) {
            inventory.insert(0, stone, 2, root);
            try (var nested = Transaction.open(root)) {
                inventory.insert(2, named, 3, nested);
            }
            check(inventory.items[2].isEmpty() && inventory.items[0].getCount() == 2, "Nested abort preserves outer writes");
            try (var nested = Transaction.open(root)) {
                inventory.insert(2, named, 3, nested);
                nested.commit();
            }
            check(inventory.commits == 0, "Nested commit does not publish root effects");
            root.commit();
        }
        check(inventory.commits == 1 && inventory.flags == 3, "Root publishes combined context flags exactly once");
        check(inventory.items[2].getCount() == 3 && named.matches(inventory.items[2]), "Components survive transfer");
        try (var root = Transaction.openRoot()) {
            check(inventory.insert(2, stone, 1, root) == 0, "Different components never merge");
            check(inventory.insert(1, stone, 1, root) == 0, "Output insertion denied");
            check(inventory.extract(0, stone, 1, root) == 0, "Input extraction denied");
            root.commit();
        }
        check(inventory.commits == 1, "Rejected changes publish nothing");
        inventory.items[1] = named.toStack(5);
        try (var root = Transaction.openRoot()) {
            check(inventory.extract(1, named, 2, root) == 2, "Output extraction accepted");
            try (var nested = Transaction.open(root)) { inventory.insert(0, stone, 2, nested); nested.commit(); }
        }
        check(inventory.items[1].getCount() == 5 && inventory.items[0].getCount() == 2 && inventory.commits == 1,
                "Outer abort also restores committed child writes");
        try (var root = Transaction.openRoot()) {
            inventory.extract(1, named, 5, root);
            root.commit();
        }
        check(inventory.items[1].isEmpty() && inventory.commits == 2 && inventory.flags == 0, "Extraction commits without stale input flags");
        inventory.allowed = false;
        try (var root = Transaction.openRoot()) {
            check(inventory.insert(0, stone, 1, root) == 0, "Live access policy is consulted on each operation");
            root.commit();
        }
        inventory.active = 1;
        try { inventory.getResource(2); throw new AssertionError("Inactive lane exposed"); }
        catch (IndexOutOfBoundsException expected) { checks++; }
        System.out.println("NeoForge 26.1.2 native transaction invariants passed: " + checks);
    }
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
}
