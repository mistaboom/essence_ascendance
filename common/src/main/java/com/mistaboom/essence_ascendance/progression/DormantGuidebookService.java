package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** One persistent Ascendance Archive welcome gift when a player first reaches a powered tier. */
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
        boolean delivered = deliverIfEligible(data, archive -> {
            placement.set(placeBook(player, archive));
            return placement.get().delivered();
        });
        if (delivered) {
            saved.setDirty();
            player.sendSystemMessage(EssenceText.guide(placement.get() == DeliveryResult.INVENTORY
                    ? "delivery.inventory" : "delivery.dropped")
                    .withStyle(style -> style.withColor(AscendancePalette.DORMANT.metalRgb())));
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
        return giveForAdmin(data, delivery, DormantGuidebookService::createArchive);
    }

    static DeliveryResult giveForAdmin(PlayerEssenceData data, Function<ItemStack, DeliveryResult> delivery,
                                       Supplier<ItemStack> archive) {
        DeliveryResult result = delivery.apply(archive.get());
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
        return deliverIfEligible(data, delivery, DormantGuidebookService::createArchive);
    }

    static boolean deliverIfEligible(PlayerEssenceData data, Predicate<ItemStack> delivery,
                                     Supplier<ItemStack> archive) {
        if (!data.getTier().grantsPower() || data.hasReceivedDormantGuidebook()) return false;
        if (!delivery.test(archive.get())) return false;
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

    public static ItemStack createArchive() { return new ItemStack(AscendanceItems.ASCENDANCE_ARCHIVE.get()); }
}
