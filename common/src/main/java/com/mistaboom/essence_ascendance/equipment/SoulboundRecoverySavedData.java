package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crash-safe holding area for soulbound artifacts removed immediately before
 * vanilla creates player death drops.
 *
 * Items remain here until the bound owner can accept them. A persistent queue
 * means a disconnect/restart between death and respawn cannot permanently lose
 * an invested artifact.
 */
public final class SoulboundRecoverySavedData extends SavedData {

    private static final String DATA_NAME = "essence_ascendance_soulbound_recovery";
    private static final String PLAYERS_TAG = "players";
    private static final String ENTRIES_TAG = "entries";
    private static final String SLOT_TAG = "preferred_slot";
    private static final String STACK_TAG = "stack";

    private final Map<UUID, List<PendingArtifact>> pending = new HashMap<>();

    public static SoulboundRecoverySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        SoulboundRecoverySavedData::new,
                        SoulboundRecoverySavedData::load,
                        DataFixTypes.LEVEL
                ),
                DATA_NAME
        );
    }

    /**
     * Queue an artifact unless this exact soulbinding identity is already
     * waiting. Returning true means the caller may safely remove the inventory
     * copy; an existing pending copy is also considered safe.
     */
    public boolean ensureQueued(UUID ownerId, int preferredSlot, ItemStack stack) {
        if (ownerId == null || stack == null || stack.isEmpty()) {
            return false;
        }

        SoulboundEquipmentData.Binding binding =
                SoulboundEquipmentData.read(stack).orElse(null);
        if (binding == null || !binding.ownerId().equals(ownerId)) {
            return false;
        }

        List<PendingArtifact> ownerPending =
                pending.computeIfAbsent(ownerId, ignored -> new ArrayList<>());

        for (PendingArtifact existing : ownerPending) {
            UUID existingBindingId = SoulboundEquipmentData.read(existing.stack())
                    .map(SoulboundEquipmentData.Binding::bindingId)
                    .orElse(null);
            if (binding.bindingId().equals(existingBindingId)) {
                return true;
            }
        }

        ownerPending.add(new PendingArtifact(preferredSlot, stack.copy()));
        setDirty();
        return true;
    }

    public boolean hasPending(UUID ownerId) {
        List<PendingArtifact> entries = pending.get(ownerId);
        return entries != null && !entries.isEmpty();
    }

    public List<PendingArtifact> pending(UUID ownerId) {
        List<PendingArtifact> entries = pending.get(ownerId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<PendingArtifact> copies = new ArrayList<>(entries.size());
        for (PendingArtifact entry : entries) {
            copies.add(new PendingArtifact(entry.preferredSlot(), entry.stack().copy()));
        }
        return List.copyOf(copies);
    }

    public void replace(UUID ownerId, List<PendingArtifact> remaining) {
        if (remaining == null || remaining.isEmpty()) {
            if (pending.remove(ownerId) != null) {
                setDirty();
            }
            return;
        }

        List<PendingArtifact> copies = new ArrayList<>(remaining.size());
        for (PendingArtifact entry : remaining) {
            if (entry.stack() != null && !entry.stack().isEmpty()) {
                copies.add(new PendingArtifact(entry.preferredSlot(), entry.stack().copy()));
            }
        }

        if (copies.isEmpty()) {
            pending.remove(ownerId);
        } else {
            pending.put(ownerId, copies);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();

        for (Map.Entry<UUID, List<PendingArtifact>> playerEntry : pending.entrySet()) {
            ListTag entriesTag = new ListTag();
            for (PendingArtifact artifact : playerEntry.getValue()) {
                if (artifact.stack() == null || artifact.stack().isEmpty()) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.putInt(SLOT_TAG, artifact.preferredSlot());
                entryTag.put(STACK_TAG, artifact.stack().save(registries));
                entriesTag.add(entryTag);
            }

            if (!entriesTag.isEmpty()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.put(ENTRIES_TAG, entriesTag);
                playersTag.put(playerEntry.getKey().toString(), playerTag);
            }
        }

        root.put(PLAYERS_TAG, playersTag);
        return root;
    }

    public static SoulboundRecoverySavedData load(
            CompoundTag root,
            HolderLookup.Provider registries
    ) {
        SoulboundRecoverySavedData data = new SoulboundRecoverySavedData();
        if (!root.contains(PLAYERS_TAG, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag playersTag = root.getCompound(PLAYERS_TAG);
        for (String playerKey : playersTag.getAllKeys()) {
            UUID ownerId;
            try {
                ownerId = UUID.fromString(playerKey);
            } catch (IllegalArgumentException malformedUuid) {
                continue;
            }

            CompoundTag playerTag = playersTag.getCompound(playerKey);
            if (!playerTag.contains(ENTRIES_TAG, Tag.TAG_LIST)) {
                continue;
            }

            ListTag entriesTag = playerTag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
            List<PendingArtifact> entries = new ArrayList<>();
            for (int i = 0; i < entriesTag.size(); i++) {
                CompoundTag entryTag = entriesTag.getCompound(i);
                Tag stackTag = entryTag.get(STACK_TAG);
                if (stackTag == null) {
                    continue;
                }

                ItemStack stack = ItemStack.parse(registries, stackTag).orElse(ItemStack.EMPTY);
                SoulboundEquipmentData.Binding binding =
                        SoulboundEquipmentData.read(stack).orElse(null);
                if (stack.isEmpty()
                        || binding == null
                        || !binding.ownerId().equals(ownerId)) {
                    continue;
                }

                entries.add(new PendingArtifact(entryTag.getInt(SLOT_TAG), stack));
            }

            if (!entries.isEmpty()) {
                data.pending.put(ownerId, entries);
            }
        }

        return data;
    }

    public record PendingArtifact(int preferredSlot, ItemStack stack) {
    }
}
