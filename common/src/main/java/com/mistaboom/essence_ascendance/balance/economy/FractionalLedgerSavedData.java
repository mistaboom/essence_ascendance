package com.mistaboom.essence_ascendance.balance.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Owner/account scoped carry survives machine replacement, logout and restart. */
public final class FractionalLedgerSavedData extends SavedData {
    private final Map<UUID, Map<String, Long>> accounts = new HashMap<>();

    public static FractionalLedgerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                FractionalLedgerSavedData::new, FractionalLedgerSavedData::load, DataFixTypes.LEVEL),
                "essence_ascendance_fractional_accounts");
    }

    public Map<String, Long> snapshot(UUID owner) { return Map.copyOf(accounts.getOrDefault(owner, Map.of())); }

    /** Discard one account when its corresponding whole balance is explicitly cleared. */
    public boolean clear(UUID owner, String account) {
        Map<String, Long> carry = accounts.get(owner);
        if (carry == null || carry.remove(account) == null) return false;
        if (carry.isEmpty()) accounts.remove(owner);
        setDirty();
        return true;
    }

    /** A full player reset must also discard pending sub-unit rewards. */
    public boolean clear(UUID owner) {
        if (accounts.remove(owner) == null) return false;
        setDirty();
        return true;
    }

    public void commit(UUID owner, Map<String, Long> carry) {
        Map<String, Long> validated = new TreeMap<>();
        carry.forEach((key, value) -> {
            if (value < 0 || value >= FractionalAmountService.SCALE) throw new IllegalArgumentException("Invalid account carry " + key);
            if (value != 0) validated.put(key, value);
        });
        if (validated.isEmpty()) accounts.remove(owner);
        else accounts.put(owner, validated);
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        accounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag account = new CompoundTag();
            new TreeMap<>(entry.getValue()).forEach(account::putLong);
            tag.put(entry.getKey().toString(), account);
        });
        return tag;
    }

    private static FractionalLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FractionalLedgerSavedData result = new FractionalLedgerSavedData();
        for (String ownerKey : tag.getAllKeys()) {
            UUID owner = UUID.fromString(ownerKey);
            CompoundTag account = tag.getCompound(ownerKey);
            Map<String, Long> carry = new TreeMap<>();
            for (String key : account.getAllKeys()) {
                long value = account.getLong(key);
                if (value < 0 || value >= FractionalAmountService.SCALE)
                    throw new IllegalStateException("Invalid saved fractional account " + owner + ":" + key);
                if (value != 0) carry.put(key, value);
            }
            result.accounts.put(owner, carry);
        }
        return result;
    }
}
