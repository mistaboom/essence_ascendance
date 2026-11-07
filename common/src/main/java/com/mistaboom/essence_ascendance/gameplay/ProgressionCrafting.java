package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.attunement.AttunementWorkstations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Vanilla crafting-result observation after actual ingredient consumption, including shift-click. */
public final class ProgressionCrafting {
    private ProgressionCrafting() { }
    public record Receipt(String action, List<ItemStack> inputs, ItemStack result) { }

    public static Receipt before(AbstractContainerMenu menu, int slot, ServerPlayer player) {
        int size = menu instanceof CraftingMenu ? 9 : menu instanceof InventoryMenu ? 4 : 0;
        if (size == 0 || slot != 0 || !AttunementGameplay.eligible(player) || menu != player.containerMenu
                || !menu.stillValid(player) || !menu.getSlot(0).mayPickup(player)) return null;
        ItemStack result = menu.getSlot(0).getItem();
        if (result.isEmpty()) return null;
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 1; i <= size; i++) inputs.add(menu.getSlot(i).getItem().copy());
        return new Receipt(AttunementGameplay.action("crafting"), List.copyOf(inputs), result.copy());
    }

    public static void completed(AbstractContainerMenu menu, ServerPlayer player, Receipt receipt) {
        if (receipt == null || !AttunementGameplay.eligible(player)) return;
        boolean consumed = false;
        for (int i = 0; i < receipt.inputs().size(); i++)
            consumed |= AttunementWorkstations.consumed(receipt.inputs().get(i), menu.getSlot(i + 1).getItem()) > 0;
        if (!consumed) return;
        // A shift-click may craft further batches; this is the first confirmed recipe output only.
        ProgressionOutcomes.publish(player.server, new ProgressionOutcome.Crafted(receipt.action(), player.getUUID(),
                player.server.overworld().getGameTime(), BuiltInRegistries.ITEM.getKey(receipt.result().getItem()),
                receipt.result().getCount()));
    }
}
