package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

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

    public static EssenceSavedData get(
            MinecraftServer server
    ) {

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


    public PlayerEssenceData getPlayerData(
            UUID playerId
    ) {

        PlayerEssenceData existing =
                players.get(
                        playerId
                );

        if (existing != null) {
            return existing;
        }


        PlayerEssenceData created =
                new PlayerEssenceData();

        players.put(
                playerId,
                created
        );

        setDirty();

        return created;
    }


    /*
     * ============================================================
     * ESSENCE MUTATION
     * ============================================================
     */

    public long addEssence(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        long updated =
                playerData.addAvailable(
                        essence,
                        amount
                );

        setDirty();

        return updated;
    }


    public void setEssence(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        playerData.setAvailable(
                essence,
                amount
        );

        setDirty();
    }


    /*
     * ============================================================
     * STAT INVESTMENT
     * ============================================================
     */

    public boolean invest(
            UUID playerId,
            StatDefinition stat,
            long amount
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean success =
                playerData.invest(
                        stat,
                        amount
                );

        if (success) {
            setDirty();
        }

        return success;
    }


    public void setInvested(
            UUID playerId,
            StatDefinition stat,
            long amount
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        playerData.setInvested(
                stat,
                amount
        );

        setDirty();
    }


    public void clearAll(
            UUID playerId
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        playerData.clearAll();

        setDirty();
    }


    /*
     * ============================================================
     * ASCENDANCE TIER
     * ============================================================
     */

    public AscendanceTierDefinition getTier(
            UUID playerId
    ) {

        return getPlayerData(
                playerId
        ).getTier();
    }


    public void setTier(
            UUID playerId,
            AscendanceTierDefinition tier
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        playerData.setTier(
                tier
        );

        setDirty();
    }


    /*
     * ============================================================
     * INTERNAL MILESTONES
     * ============================================================
     */

    public boolean hasCompletedInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {

        return getPlayerData(
                playerId
        ).hasCompletedMilestone(
                milestoneId
        );
    }


    public boolean completeInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.completeMilestone(
                        milestoneId
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public boolean revokeInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.revokeMilestone(
                        milestoneId
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    /*
     * ============================================================
     * SAVE / LOAD
     * ============================================================
     */

    @Override
    public CompoundTag save(
            CompoundTag root,
            HolderLookup.Provider registries
    ) {

        EssenceDataMigration.writeCurrentVersion(
                root
        );


        CompoundTag playersTag =
                new CompoundTag();


        for (Map.Entry<UUID, PlayerEssenceData> entry :
                players.entrySet()) {

            playersTag.put(
                    entry.getKey().toString(),
                    entry.getValue().save()
            );
        }


        root.put(
                PLAYERS_TAG,
                playersTag
        );


        return root;
    }


    public static EssenceSavedData load(
            CompoundTag root,
            HolderLookup.Provider provider
    ) {

        EssenceDataMigration.MigrationResult migration =
                EssenceDataMigration.migrate(
                        root
                );


        CompoundTag migratedRoot =
                migration.root();


        EssenceSavedData data =
                new EssenceSavedData();


        CompoundTag playersTag =
                migratedRoot.getCompound(
                        PLAYERS_TAG
                );


        for (String key :
                playersTag.getAllKeys()) {

            try {

                UUID playerId =
                        UUID.fromString(
                                key
                        );


                PlayerEssenceData playerData =
                        PlayerEssenceData.load(
                                playersTag.getCompound(
                                        key
                                )
                        );


                data.players.put(
                        playerId,
                        playerData
                );


            } catch (IllegalArgumentException ignored) {

                /*
                 * Ignore malformed UUID keys rather than preventing
                 * the entire world from loading.
                 */
            }
        }


        if (migration.migrated()) {
            data.setDirty();
        }


        return data;
    }
}