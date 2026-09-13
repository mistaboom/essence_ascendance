package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** One persistent welcome gift when a player first reaches a powered tier. */
public final class DormantGuidebookService {
    public enum DeliveryResult {
        INVENTORY, DROPPED, FAILED;

        public boolean delivered() { return this != FAILED; }
    }

    private DormantGuidebookService() {
    }

    /**
     * Called from authoritative progression/lifecycle reconciliation so direct
     * tier commands and normal ascension deliver the same reward. Failed drops
     * leave the receipt pending for the next reconciliation.
     */
    public static boolean deliverIfEligible(ServerPlayer player) {
        EssenceSavedData saved = EssenceSavedData.get(player.server);
        PlayerEssenceData data = saved.getPlayerData(player.getUUID());
        var placement = new java.util.concurrent.atomic.AtomicReference<>(DeliveryResult.FAILED);
        boolean delivered = deliverIfEligible(data, book -> {
            placement.set(placeBook(player, book));
            return placement.get().delivered();
        });
        if (delivered) {
            saved.setDirty();
            player.sendSystemMessage(EssenceText.guide(placement.get() == DeliveryResult.INVENTORY
                    ? "delivery.inventory" : "delivery.dropped").withStyle(ChatFormatting.AQUA));
        }
        return delivered;
    }

    /** Explicit operator recovery supplies a copy regardless of the previous delivery receipt. */
    public static DeliveryResult giveForAdmin(ServerPlayer player) {
        EssenceSavedData saved = EssenceSavedData.get(player.server);
        DeliveryResult result = giveForAdmin(saved.getPlayerData(player.getUUID()), book -> placeBook(player, book));
        if (result.delivered()) saved.setDirty();
        return result;
    }

    static DeliveryResult giveForAdmin(PlayerEssenceData data, Function<ItemStack, DeliveryResult> delivery) {
        DeliveryResult result = delivery.apply(createPlaceholder());
        if (result.delivered()) data.markDormantGuidebookReceived();
        return result;
    }

    private static DeliveryResult placeBook(ServerPlayer player, ItemStack book) {
        DeliveryResult result = placeBook(book, player.getInventory(), remainder -> {
            var dropped = player.drop(remainder, false);
            if (dropped == null) return false;
            dropped.setNoPickUpDelay();
            dropped.setTarget(player.getUUID());
            return true;
        });
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        return result;
    }

    /** Shared delivery decision: only successful inventory/drop delivery earns a receipt. */
    static boolean deliverIfEligible(PlayerEssenceData data, Predicate<ItemStack> delivery) {
        if (!data.getTier().grantsPower() || data.hasReceivedDormantGuidebook()) return false;
        if (!delivery.test(createPlaceholder())) return false;
        data.markDormantGuidebookReceived();
        return true;
    }

    static DeliveryResult placeBook(ItemStack book, Inventory inventory, Predicate<ItemStack> drop) {
        // Inventory.add(stack) silently discards an unplaceable item in Creative.
        // Choose a real receiving slot first; otherwise preserve the book for a drop.
        int slot = inventory.getSlotWithRemainingSpace(book);
        if (slot < 0) slot = inventory.getFreeSlot();
        if (slot >= 0) {
            inventory.add(slot, book);
            inventory.setChanged();
            if (book.isEmpty()) return DeliveryResult.INVENTORY;
        }
        return drop.test(book) ? DeliveryResult.DROPPED : DeliveryResult.FAILED;
    }

    public static ItemStack createPlaceholder() {
        ItemStack book = new ItemStack(Items.BOOK);
        book.set(DataComponents.ITEM_NAME, EssenceText.guide("placeholder.title")
                .withStyle(ChatFormatting.AQUA));
        book.set(DataComponents.LORE, new ItemLore(List.of(
                EssenceText.guide("placeholder.welcome").withStyle(ChatFormatting.GRAY),
                EssenceText.guide("placeholder.contents").withStyle(ChatFormatting.DARK_GRAY)
        )));
        return book;
    }
}
