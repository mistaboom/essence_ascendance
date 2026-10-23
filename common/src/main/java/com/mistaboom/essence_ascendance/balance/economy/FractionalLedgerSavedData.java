package com.mistaboom.essence_ascendance.balance.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=26.1.2
/*import net.minecraft.world.level.saveddata.SavedDataType;*/
//? if >=26.1.2
/*import net.minecraft.resources.ResourceLocation;*/
import com.mistaboom.essence_ascendance.data.NbtVersionCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Personal reward carry only. Reservoir carry lives with player balances in EssenceSavedData. */
public final class FractionalLedgerSavedData extends SavedData {
    //? if >=26.1.2
    /*private static final SavedDataType<FractionalLedgerSavedData> DATA_TYPE = new SavedDataType<>(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "essence_ascendance_fractional_accounts"), FractionalLedgerSavedData::new, CompoundTag.CODEC.xmap(tag -> load(tag, null), data -> data.saveTag(new CompoundTag())), DataFixTypes.LEVEL);*/
    private final Map<UUID, Map<String, Long>> accounts = new HashMap<>();

    public static FractionalLedgerSavedData get(MinecraftServer server) {
        //? if >=26.1.2
        /*return server.overworld().getDataStorage().computeIfAbsent(DATA_TYPE);*/
        //? if <26.1.2
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                //? if <26.1.2
                FractionalLedgerSavedData::new, FractionalLedgerSavedData::load, DataFixTypes.LEVEL),
                //? if <26.1.2
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
            if (key.startsWith("dissolution/")) throw new IllegalArgumentException("Reservoir carry belongs in EssenceSavedData");
            if (value < 0 || value >= FractionalAmountService.SCALE) throw new IllegalArgumentException("Invalid account carry " + key);
            if (value != 0) validated.put(key, value);
        });
        if (validated.isEmpty()) accounts.remove(owner);
        else accounts.put(owner, validated);
        setDirty();
    }

    //? if <26.1.2
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        //? if <26.1.2
        return saveTag(tag);
    //? if <26.1.2
    }

    private CompoundTag saveTag(CompoundTag tag) {
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
            CompoundTag account = NbtVersionCompat.getCompound(tag, ownerKey);
            Map<String, Long> carry = new TreeMap<>();
            for (String key : account.getAllKeys()) {
                if (key.startsWith("dissolution/")) throw new IllegalStateException(
                        "Old dissolution carry save is incompatible; recreate the development world");
                long value = NbtVersionCompat.getLong(account, key);
                if (value < 0 || value >= FractionalAmountService.SCALE)
                    throw new IllegalStateException("Invalid saved fractional account " + owner + ":" + key);
                if (value != 0) carry.put(key, value);
            }
            result.accounts.put(owner, carry);
        }
        return result;
    }
}
