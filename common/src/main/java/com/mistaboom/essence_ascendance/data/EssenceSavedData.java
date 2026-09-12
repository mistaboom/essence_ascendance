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
     * CRUCIBLE RESERVOIR MUTATION
     * ============================================================
     */

    public long addCrucibleStored(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        long updated =
                playerData.addCrucibleStored(
                        essence,
                        amount
                );

        setDirty();
        return updated;
    }


    public long removeCrucibleStored(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        long removed =
                playerData.removeCrucibleStored(
                        essence,
                        amount
                );

        if (removed > 0L) {
            setDirty();
        }

        return removed;
    }


    public boolean removeCrucibleStoredExact(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {
        PlayerEssenceData playerData =
                getPlayerData(playerId);

        boolean removed =
                playerData.removeCrucibleStoredExact(
                        essence,
                        amount
                );

        if (removed) {
            setDirty();
        }
        return removed;
    }


    public long transferCrucibleToAvailable(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        long moved =
                playerData.transferCrucibleToAvailable(
                        essence,
                        amount
                );

        if (moved > 0L) {
            setDirty();
        }

        return moved;
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


    /** Apply one prevalidated Nexus allocation/reallocation atomically. */
    public boolean applyAllocationTargets(
            UUID playerId,
            Map<StatDefinition, Long> targetInvestments,
            Map<EssenceDefinition, Long> targetAvailable
    ) {
        PlayerEssenceData playerData =
                getPlayerData(playerId);

        boolean changed =
                playerData.applyAllocationTargets(
                        targetInvestments,
                        targetAvailable
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    /**
     * Applies one prevalidated complete Nexus projection atomically and marks
     * the world data dirty only when the authoritative state changed.
     */
    public boolean applyNexusTransaction(
            UUID playerId,
            Map<ResourceLocation, Long> targetInvestments,
            Map<ResourceLocation, Long> targetAvailable,
            Map<ResourceLocation, SkillPurchase> targetOwnedSkills,
            Map<ResourceLocation, ResourceLocation> targetLoadoutSelections,
            ResourceLocation targetTierId
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.applyNexusTransaction(
                        targetInvestments,
                        targetAvailable,
                        targetOwnedSkills,
                        targetLoadoutSelections,
                        targetTierId
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public boolean recordSkillPurchase(
            UUID playerId,
            ResourceLocation skillId,
            SkillPurchase purchase
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.recordSkillPurchase(
                        skillId,
                        purchase
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }

    public int grantAllSkillsForAdmin(UUID playerId, Iterable<com.mistaboom.essence_ascendance.skill.SkillDefinition> definitions) {
        int granted = getPlayerData(playerId).grantAllSkillsForAdmin(definitions);
        if (granted > 0) setDirty();
        return granted;
    }

    public int clearAllSkillsForAdmin(UUID playerId) {
        int removed = getPlayerData(playerId).clearAllSkillsForAdmin();
        setDirty();
        return removed;
    }


    public boolean setLoadoutSelection(
            UUID playerId,
            ResourceLocation selectionId,
            ResourceLocation skillId
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.setLoadoutSelection(
                        selectionId,
                        skillId
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public boolean clearLoadoutSelection(
            UUID playerId,
            ResourceLocation selectionId
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.clearLoadoutSelection(
                        selectionId
                );

        if (changed) {
            setDirty();
        }

        return changed;
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


    /** Persists sub-unit accounting used by server-authoritative menu costs. */
    public boolean setFractionalResourceCostCarry(
            UUID playerId,
            net.minecraft.resources.ResourceLocation channelId,
            double carry
    ) {
        boolean changed = getPlayerData(playerId)
                .setFractionalResourceCostCarry(channelId, carry);

        if (changed) {
            setDirty();
        }
        return changed;
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
     * PERMANENT MILESTONE STATE
     * ============================================================
     */

    public boolean hasCompletedMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {
        return getPlayerData(playerId).hasCompletedMilestone(milestoneId);
    }


    public boolean completeMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {
        PlayerEssenceData playerData = getPlayerData(playerId);
        boolean changed = playerData.completeMilestone(milestoneId);

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public boolean revokeMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {
        PlayerEssenceData playerData = getPlayerData(playerId);
        boolean changed = playerData.revokeMilestone(milestoneId);

        if (changed) {
            setDirty();
        }

        return changed;
    }


    /* Provider compatibility helpers for existing internal milestones. */

    public boolean hasCompletedInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {
        return hasCompletedMilestone(playerId, milestoneId);
    }


    public boolean completeInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {

        return completeMilestone(playerId, milestoneId);
    }


    public boolean revokeInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {

        return revokeMilestone(playerId, milestoneId);
    }


    /*
     * ============================================================
     * PERMANENT PLAYER ATTUNEMENTS
     * ============================================================
     */

    public boolean hasAttunement(
            UUID playerId,
            ResourceLocation attunementId
    ) {
        return getPlayerData(
                playerId
        ).hasAttunement(
                attunementId
        );
    }


    public boolean grantAttunement(
            UUID playerId,
            ResourceLocation attunementId
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.grantAttunement(
                        attunementId
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public boolean revokeAttunement(
            UUID playerId,
            ResourceLocation attunementId
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.revokeAttunement(
                        attunementId
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
