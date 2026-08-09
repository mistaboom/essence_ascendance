package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EssenceSavedData extends SavedData {

    private static final String DATA_NAME =
            "essence_ascendance_players";

    private static final String PLAYERS_TAG =
            "players";

    private final Map<UUID, PlayerEssenceData> players =
            new HashMap<>();


    /*
     * ============================================================
     * ACCESS
     * ============================================================
     */

    public static EssenceSavedData get(MinecraftServer server) {
        return server
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                EssenceSavedData::new,
                                EssenceSavedData::load,
                                DataFixTypes.LEVEL
                        ),
                        DATA_NAME
                );
    }

    public PlayerEssenceData getPlayerData(UUID playerId) {
        PlayerEssenceData existing = players.get(playerId);

        if (existing != null) {
            return existing;
        }

        PlayerEssenceData created =
                new PlayerEssenceData();

        players.put(playerId, created);

        setDirty();

        return created;
    }


    /*
     * ============================================================
     * MUTATION
     * ============================================================
     */

    public long addEssence(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {
        PlayerEssenceData playerData =
                getPlayerData(playerId);

        long updated =
                playerData.addAvailable(essence, amount);

        setDirty();

        return updated;
    }

    public void setEssence(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {
        PlayerEssenceData playerData =
                getPlayerData(playerId);

        playerData.setAvailable(essence, amount);

        setDirty();
    }

    public boolean invest(
            UUID playerId,
            StatDefinition stat,
            long amount
    ) {
        PlayerEssenceData playerData =
                getPlayerData(playerId);

        boolean success =
                playerData.invest(stat, amount);

        if (success) {
            setDirty();
        }

        return success;
    }


    /*
     * ============================================================
     * SAVE / LOAD
     * ============================================================
     */

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        CompoundTag playersTag =
                new CompoundTag();

        for (Map.Entry<UUID, PlayerEssenceData> entry :
                players.entrySet()) {

            playersTag.put(
                    entry.getKey().toString(),
                    entry.getValue().save()
            );
        }

        tag.put(PLAYERS_TAG, playersTag);

        return tag;
    }

    public static EssenceSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        EssenceSavedData data =
                new EssenceSavedData();

        CompoundTag playersTag =
                tag.getCompound(PLAYERS_TAG);

        for (String key : playersTag.getAllKeys()) {

            try {
                UUID playerId =
                        UUID.fromString(key);

                PlayerEssenceData playerData =
                        PlayerEssenceData.load(
                                playersTag.getCompound(key)
                        );

                data.players.put(
                        playerId,
                        playerData
                );

            } catch (IllegalArgumentException ignored) {
                // Ignore malformed UUID keys rather than
                // preventing the world from loading.
            }
        }

        return data;
    }
}