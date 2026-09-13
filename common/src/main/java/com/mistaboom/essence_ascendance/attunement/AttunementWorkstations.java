package com.mistaboom.essence_ascendance.attunement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** One completed result extraction, across vanilla workstations, including shift-click batches. */
public final class AttunementWorkstations {
    private AttunementWorkstations() { }

    public static Receipt before(AbstractContainerMenu menu, int clickedSlot, ServerPlayer player) {
        String method;
        int output;
        if (menu instanceof AnvilMenu) { method = "use_anvils"; output = AnvilMenu.RESULT_SLOT; }
        else if (menu instanceof SmithingMenu) { method = "use_smithing_tables"; output = SmithingMenu.RESULT_SLOT; }
        else if (menu instanceof MerchantMenu) { method = "trade_villagers"; output = 2; }
        else if (menu instanceof LoomMenu) { method = "use_looms"; output = 3; }
        else if (menu instanceof GrindstoneMenu) { method = "use_grindstones"; output = 2; }
        else if (menu instanceof CartographyTableMenu) { method = "use_cartography_tables"; output = 2; }
        else if (menu instanceof StonecutterMenu) { method = "use_stonecutters"; output = 1; }
        else return null;
        if (clickedSlot != output || !AttunementGameplay.eligible(player)
                || menu != player.containerMenu || !menu.stillValid(player) || !menu.getSlot(output).mayPickup(player)) return null;
        ItemStack result = menu.getSlot(output).getItem();
        if (result.isEmpty()) return null;
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < output; slot++) inputs.add(menu.getSlot(slot).getItem().copy());
        return new Receipt(AttunementGameplay.action("workstation"), method, output,
                List.copyOf(inputs), result.copy(), player.experienceLevel, player.experienceProgress,
                menu instanceof com.mistaboom.essence_ascendance.equipment.AnvilMenuCostView cost ? cost.essenceAscendance$originalCost() : 0);
    }

    public static void completed(AbstractContainerMenu menu, ServerPlayer player, Receipt receipt) {
        if (receipt == null) return;
        double consumedValue = 0;
        int consumedCount = 0;
        StringBuilder inputs = new StringBuilder();
        for (int slot = 0; slot < receipt.output; slot++) {
            ItemStack before = receipt.inputs.get(slot), after = menu.getSlot(slot).getItem();
            int consumed = consumed(before, after);
            if (consumed > 0) {
                consumedCount += consumed;
                consumedValue += AttunementGameplay.value(before.copyWithCount(consumed));
                inputs.append(AttunementGameplay.itemSignature(before)).append(';');
            }
        }
        // Merely taking back inputs, moving a preview, or failed pickup never consumes result inputs.
        if (consumedCount == 0) return;
        double value = Math.max(consumedValue, AttunementGameplay.value(receipt.result));
        int levelCost = Math.max(receipt.originalCost, Math.max(0, receipt.experienceLevel - player.experienceLevel));
        double experience = AttunementGameplay.experienceCost(receipt.experienceLevel, receipt.experienceProgress, levelCost);
        value = Math.max(value, AttunementGameplay.experienceOperationValue(player, experience));
        String source = receipt.method + ":" + inputs + "=>" + AttunementGameplay.itemSignature(receipt.result);
        AttunementGameplay.award(player, receipt.root, receipt.method, source, value);
        ItemStack original = receipt.inputs.get(0);
        if (original.isDamageableItem() && receipt.result.is(original.getItem())) {
            int repaired = Math.max(0, original.getDamageValue() - receipt.result.getDamageValue());
            // The common engine chooses the highest Utility contribution for this root, never their sum.
            AttunementGameplay.award(player, receipt.root, "repair_equipment", source, repaired);
        }
    }

    public static int consumed(ItemStack before, ItemStack after) {
        if (before.isEmpty()) return 0;
        return Math.max(0, before.getCount() - (ItemStack.isSameItemSameComponents(before, after) ? after.getCount() : 0));
    }
    public record Receipt(String root, String method, int output, List<ItemStack> inputs, ItemStack result,
                          int experienceLevel, float experienceProgress, int originalCost) { }
}
