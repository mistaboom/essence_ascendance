package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionData;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative death/respawn preservation for soulbound Ascendance
 * artifacts.
 */
public final class SoulboundEquipmentService {

    private static boolean initialized = false;

    private SoulboundEquipmentService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        PlayerEvent.PLAYER_JOIN.register(SoulboundEquipmentService::restorePending);

        /*
         * Normally restoration succeeds immediately on respawn. The tick retry
         * is deliberately cheap and covers modded respawn flows/full inventories
         * without ever dropping the protected artifact into the world.
         */
        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                SoulboundRecoverySavedData recovery =
                        SoulboundRecoverySavedData.get(serverPlayer.server);
                /*
                 * Never restore into the dead ServerPlayer that still exists
                 * while the death screen is open. Minecraft replaces/copies
                 * that instance during respawn; restoring there would clear the
                 * recovery queue and then lose the artifact when the old player
                 * instance is discarded. The first tick of the new living
                 * ServerPlayer is the safe recovery point.
                 */
                if (serverPlayer.isAlive()
                        && recovery.hasPending(serverPlayer.getUUID())) {
                    restorePending(serverPlayer);
                }

                /*
                 * Compatibility migration for artifacts invested before
                 * soulbinding existed. Fresh Latent/zero-progress equipment is
                 * intentionally ignored. Once per second is ample and keeps the
                 * steady-state cost negligible.
                 */
                if (serverPlayer.level().getGameTime() % 20L == 0L) {
                    bindLegacyInvestedEquipment(serverPlayer);
                }
            }
        });

        initialized = true;
    }

    private static void bindLegacyInvestedEquipment(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!EquipmentTierData.isAscendanceEquipment(stack)
                    || SoulboundEquipmentData.isSoulbound(stack)) {
                continue;
            }

            boolean historicallyInvested =
                    EquipmentTierData.tier(stack) != EquipmentTier.LATENT
                            || EquipmentInfusionData.totalContributed(stack) > 0L;
            if (!historicallyInvested) {
                continue;
            }

            SoulboundEquipmentData.bind(
                    stack,
                    player.getUUID(),
                    player.getGameProfile().getName()
            );
            EssenceAscendance.LOGGER.info(
                    "Migrated invested Ascendance artifact {} to soulbound owner {}",
                    stack.getHoverName().getString(),
                    player.getGameProfile().getName()
            );
        }
    }

    /**
     * Called from the loader mixins before vanilla may destroy Vanishing items
     * or turn the inventory into ItemEntity death drops.
     */
    public static void captureBeforeDeathDrops(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        SoulboundRecoverySavedData recovery =
                SoulboundRecoverySavedData.get(player.server);

        int captured = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            SoulboundEquipmentData.Binding binding =
                    SoulboundEquipmentData.read(stack).orElse(null);
            if (binding == null) {
                continue;
            }

            /*
             * Recovery follows the artifact's bound owner, not necessarily the
             * player who happened to be carrying it at death.
             */
            if (!recovery.ensureQueued(binding.ownerId(), slot, stack)) {
                continue;
            }

            inventory.setItem(slot, ItemStack.EMPTY);
            captured++;
        }

        if (captured > 0) {
            EssenceAscendance.LOGGER.info(
                    "Protected {} soulbound Ascendance artifact(s) from death drops for player {}",
                    captured,
                    player.getGameProfile().getName()
            );
        }
    }

    public static void restorePending(ServerPlayer player) {
        /*
         * This guard is intentionally repeated here so every current/future
         * caller is safe. A dead player can continue ticking while the death
         * screen is open, but its inventory is not the post-respawn inventory.
         */
        if (!player.isAlive()) {
            return;
        }

        SoulboundRecoverySavedData recovery =
                SoulboundRecoverySavedData.get(player.server);
        UUID ownerId = player.getUUID();
        List<SoulboundRecoverySavedData.PendingArtifact> pending =
                recovery.pending(ownerId);
        if (pending.isEmpty()) {
            return;
        }

        Inventory inventory = player.getInventory();
        List<SoulboundRecoverySavedData.PendingArtifact> remaining = new ArrayList<>();
        int restored = 0;
        boolean changed = false;

        for (SoulboundRecoverySavedData.PendingArtifact artifact : pending) {
            ItemStack protectedStack = artifact.stack();
            SoulboundEquipmentData.Binding binding =
                    SoulboundEquipmentData.read(protectedStack).orElse(null);

            if (binding == null || !binding.ownerId().equals(ownerId)) {
                /* Malformed/stale recovery data is discarded rather than duped. */
                changed = true;
                continue;
            }

            if (inventoryContainsBinding(inventory, binding.bindingId())) {
                /*
                 * A keep-inventory/modded respawn path already preserved the
                 * same artifact. Drop the recovery copy to guarantee exactly one.
                 */
                changed = true;
                continue;
            }

            if (restoreOne(inventory, artifact.preferredSlot(), protectedStack)) {
                restored++;
                changed = true;
            } else {
                remaining.add(artifact);
            }
        }

        if (changed) {
            recovery.replace(ownerId, remaining);
        }

        if (restored > 0) {
            player.inventoryMenu.broadcastChanges();
            EssenceAscendance.LOGGER.info(
                    "Restored {} soulbound Ascendance artifact(s) to {}",
                    restored,
                    player.getGameProfile().getName()
            );
        }
    }

    private static boolean restoreOne(
            Inventory inventory,
            int preferredSlot,
            ItemStack stack
    ) {
        ItemStack copy = stack.copy();

        if (preferredSlot >= 0
                && preferredSlot < inventory.getContainerSize()
                && inventory.getItem(preferredSlot).isEmpty()) {
            inventory.setItem(preferredSlot, copy);
            return true;
        }

        /* Ascendance equipment is non-stackable, so this is all-or-nothing. */
        return inventory.add(copy);
    }

    private static boolean inventoryContainsBinding(
            Inventory inventory,
            UUID bindingId
    ) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            UUID existingId = SoulboundEquipmentData.read(inventory.getItem(slot))
                    .map(SoulboundEquipmentData.Binding::bindingId)
                    .orElse(null);
            if (bindingId.equals(existingId)) {
                return true;
            }
        }
        return false;
    }
}
