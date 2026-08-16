package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PlayerEssenceData {

    private static final String AVAILABLE_TAG =
            "available";

    private static final String INVESTED_TAG =
            "invested";

    private static final String CRUCIBLE_RESERVOIR_TAG =
            "crucible_reservoir";

    private static final String TIER_TAG =
            "tier";

    private static final String COMPLETED_MILESTONES_TAG =
            "completed_milestones";


    private final Map<ResourceLocation, Long> availableEssence =
            new LinkedHashMap<>();

    private final Map<ResourceLocation, Long> investedEssence =
            new LinkedHashMap<>();

    /*
     * Essence dissolved by owned Crucibles but not yet channeled into the
     * player's spendable AVAILABLE balance. This is intentionally player-
     * owned rather than block-owned so multiple Crucibles share one capped
     * reservoir and breaking/replacing a Crucible cannot lose the stored
     * Essence.
     *
     * Changes here do not bump the normal progression revision because the
     * existing player Essence sync payload does not expose this reservoir.
     * The Crucible screen has its own server-authoritative state payload.
     */
    private final Map<ResourceLocation, Long> crucibleReservoir =
            new LinkedHashMap<>();

    private final Set<ResourceLocation> completedMilestones =
            new LinkedHashSet<>();


    private ResourceLocation currentTierId =
            AscendanceTiers.DORMANT.id();

    /*
     * Runtime-only mutation revision used by server -> client synchronization.
     *
     * This value is deliberately NOT serialized. Its only purpose is to let
     * runtime systems cheaply detect that a player's authoritative progression
     * state changed.
     */
    private long revision = 0L;


    public long revision() {
        return revision;
    }


    private void bumpRevision() {
        if (revision < Long.MAX_VALUE) {
            revision++;
        }
    }


    /*
     * ============================================================
     * AVAILABLE ESSENCE
     * ============================================================
     */

    public long getAvailable(
            EssenceDefinition essence
    ) {
        return getAvailable(
                essence.id()
        );
    }


    public long getAvailable(
            ResourceLocation essenceId
    ) {
        return availableEssence.getOrDefault(
                essenceId,
                0L
        );
    }


    public long addAvailable(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Essence amount must be greater than zero"
            );
        }

        long current =
                getAvailable(
                        essence
                );

        long updated =
                Math.addExact(
                        current,
                        amount
                );

        availableEssence.put(
                essence.id(),
                updated
        );

        bumpRevision();

        return updated;
    }


    public void setAvailable(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Essence amount cannot be negative"
            );
        }

        long current =
                getAvailable(
                        essence
                );

        if (current == amount) {
            return;
        }

        if (amount == 0) {

            availableEssence.remove(
                    essence.id()
            );

        } else {

            availableEssence.put(
                    essence.id(),
                    amount
            );
        }

        bumpRevision();
    }


    public Map<ResourceLocation, Long> getAllAvailable() {
        return Collections.unmodifiableMap(
                availableEssence
        );
    }


    /*
     * ============================================================
     * CRUCIBLE RESERVOIR
     * ============================================================
     */

    public long getCrucibleStored(
            EssenceDefinition essence
    ) {
        return getCrucibleStored(
                essence.id()
        );
    }


    public long getCrucibleStored(
            ResourceLocation essenceId
    ) {
        return crucibleReservoir.getOrDefault(
                essenceId,
                0L
        );
    }


    public long addCrucibleStored(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount <= 0L) {
            throw new IllegalArgumentException(
                    "Crucible Essence amount must be greater than zero"
            );
        }

        long updated = Math.addExact(
                getCrucibleStored(essence),
                amount
        );

        crucibleReservoir.put(
                essence.id(),
                updated
        );

        return updated;
    }


    public long removeCrucibleStored(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount <= 0L) {
            throw new IllegalArgumentException(
                    "Crucible Essence amount must be greater than zero"
            );
        }

        long current = getCrucibleStored(essence);
        long removed = Math.min(current, amount);
        if (removed <= 0L) {
            return 0L;
        }

        long remaining = current - removed;
        if (remaining == 0L) {
            crucibleReservoir.remove(
                    essence.id()
            );
        } else {
            crucibleReservoir.put(
                    essence.id(),
                    remaining
            );
        }

        return removed;
    }


    public long transferCrucibleToAvailable(
            EssenceDefinition essence,
            long requestedAmount
    ) {
        if (requestedAmount <= 0L) {
            throw new IllegalArgumentException(
                    "Transfer amount must be greater than zero"
            );
        }

        long stored = getCrucibleStored(essence);
        if (stored <= 0L) {
            return 0L;
        }

        long available = getAvailable(essence);
        long room = Long.MAX_VALUE - available;
        if (room <= 0L) {
            return 0L;
        }

        long moved = Math.min(
                requestedAmount,
                Math.min(stored, room)
        );
        if (moved <= 0L) {
            return 0L;
        }

        long remainingStored = stored - moved;
        if (remainingStored == 0L) {
            crucibleReservoir.remove(
                    essence.id()
            );
        } else {
            crucibleReservoir.put(
                    essence.id(),
                    remainingStored
            );
        }

        availableEssence.put(
                essence.id(),
                available + moved
        );

        /* AVAILABLE changed, so the normal player Essence payload must sync. */
        bumpRevision();

        return moved;
    }


    public Map<ResourceLocation, Long> getAllCrucibleStored() {
        return Collections.unmodifiableMap(
                crucibleReservoir
        );
    }


    /*
     * ============================================================
     * INVESTED ESSENCE
     * ============================================================
     */

    public long getInvested(
            StatDefinition stat
    ) {
        return getInvested(
                stat.id()
        );
    }


    public long getInvested(
            ResourceLocation statId
    ) {
        return investedEssence.getOrDefault(
                statId,
                0L
        );
    }


    public boolean invest(
            StatDefinition stat,
            long amount
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Investment amount must be greater than zero"
            );
        }

        EssenceDefinition requiredEssence =
                stat.essenceType();

        long available =
                getAvailable(
                        requiredEssence
                );

        if (available < amount) {
            return false;
        }

        long currentInvestment =
                getInvested(
                        stat
                );

        long newInvestment =
                Math.addExact(
                        currentInvestment,
                        amount
                );

        long remaining =
                available - amount;


        if (remaining == 0) {

            availableEssence.remove(
                    requiredEssence.id()
            );

        } else {

            availableEssence.put(
                    requiredEssence.id(),
                    remaining
            );
        }


        investedEssence.put(
                stat.id(),
                newInvestment
        );

        bumpRevision();

        return true;
    }


    public Map<ResourceLocation, Long> getAllInvested() {
        return Collections.unmodifiableMap(
                investedEssence
        );
    }


    public void setInvested(
            StatDefinition stat,
            long amount
    ) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Invested Essence amount cannot be negative"
            );
        }

        long current =
                getInvested(
                        stat
                );

        if (current == amount) {
            return;
        }

        if (amount == 0) {

            investedEssence.remove(
                    stat.id()
            );

        } else {

            investedEssence.put(
                    stat.id(),
                    amount
            );
        }

        bumpRevision();
    }


    public void clearAvailable() {
        if (availableEssence.isEmpty()) {
            return;
        }

        availableEssence.clear();
        bumpRevision();
    }


    public void clearInvested() {
        if (investedEssence.isEmpty()) {
            return;
        }

        investedEssence.clear();
        bumpRevision();
    }


    public void clearAll() {
        /*
         * Intentionally clears Essence/stat progression only.
         *
         * It does not modify Ascendance tier or completed world
         * milestones.
         */
        if (availableEssence.isEmpty()
                && investedEssence.isEmpty()
                && crucibleReservoir.isEmpty()) {
            return;
        }

        availableEssence.clear();
        investedEssence.clear();
        crucibleReservoir.clear();
        bumpRevision();
    }


    /*
     * ============================================================
     * ASCENDANCE TIER
     * ============================================================
     */

    public AscendanceTierDefinition getTier() {
        return AscendanceTierRegistry
                .get(
                        currentTierId
                )
                .orElse(
                        AscendanceTiers.DORMANT
                );
    }


    public ResourceLocation getTierId() {
        return currentTierId;
    }


    public void setTier(
            AscendanceTierDefinition tier
    ) {
        if (currentTierId.equals(
                tier.id()
        )) {
            return;
        }

        currentTierId =
                tier.id();

        bumpRevision();
    }


    /*
     * ============================================================
     * INTERNAL MILESTONES
     * ============================================================
     */

    public boolean hasCompletedMilestone(
            ResourceLocation milestoneId
    ) {
        return completedMilestones.contains(
                milestoneId
        );
    }


    public boolean completeMilestone(
            ResourceLocation milestoneId
    ) {
        boolean changed =
                completedMilestones.add(
                        milestoneId
                );

        if (changed) {
            bumpRevision();
        }

        return changed;
    }


    public boolean revokeMilestone(
            ResourceLocation milestoneId
    ) {
        boolean changed =
                completedMilestones.remove(
                        milestoneId
                );

        if (changed) {
            bumpRevision();
        }

        return changed;
    }


    public Set<ResourceLocation> getCompletedMilestones() {
        return Collections.unmodifiableSet(
                completedMilestones
        );
    }


    /*
     * ============================================================
     * NBT SERIALIZATION
     * ============================================================
     */

    public CompoundTag save() {

        CompoundTag root =
                new CompoundTag();


        /*
         * Available Essence
         */
        CompoundTag availableTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, Long> entry :
                availableEssence.entrySet()) {

            availableTag.putLong(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(
                AVAILABLE_TAG,
                availableTag
        );


        /*
         * Crucible reservoir Essence
         */
        CompoundTag crucibleReservoirTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, Long> entry :
                crucibleReservoir.entrySet()) {

            crucibleReservoirTag.putLong(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(
                CRUCIBLE_RESERVOIR_TAG,
                crucibleReservoirTag
        );


        /*
         * Invested Essence
         */
        CompoundTag investedTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, Long> entry :
                investedEssence.entrySet()) {

            investedTag.putLong(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(
                INVESTED_TAG,
                investedTag
        );


        /*
         * Ascendance tier
         */
        root.putString(
                TIER_TAG,
                currentTierId.toString()
        );


        /*
         * Internal milestone completion
         */
        CompoundTag milestoneTag =
                new CompoundTag();

        for (ResourceLocation milestoneId :
                completedMilestones) {

            milestoneTag.putBoolean(
                    milestoneId.toString(),
                    true
            );
        }

        root.put(
                COMPLETED_MILESTONES_TAG,
                milestoneTag
        );


        return root;
    }


    public static PlayerEssenceData load(
            CompoundTag root
    ) {

        PlayerEssenceData data =
                new PlayerEssenceData();


        /*
         * ========================================================
         * ASCENDANCE TIER
         * ========================================================
         */

        String savedTier =
                root.getString(
                        TIER_TAG
                );

        if (!savedTier.isBlank()) {

            ResourceLocation tierId =
                    ResourceLocation.tryParse(
                            savedTier
                    );

            if (tierId != null) {

                data.currentTierId =
                        tierId;

                if (AscendanceTierRegistry
                        .get(tierId)
                        .isEmpty()) {

                    EssenceAscendance.LOGGER.warn(
                            "Unknown Ascendance tier '{}' in saved player data. Preserving the ID and treating the player as Dormant until the tier becomes available.",
                            tierId
                    );
                }

            } else {

                EssenceAscendance.LOGGER.warn(
                        "Invalid Ascendance tier ID '{}' in saved player data; defaulting to Dormant",
                        savedTier
                );

                data.currentTierId =
                        AscendanceTiers.DORMANT.id();
            }
        }


        /*
         * ========================================================
         * AVAILABLE ESSENCE
         * ========================================================
         */

        readLongMap(
                root.getCompound(
                        AVAILABLE_TAG
                ),
                data.availableEssence
        );


        /*
         * ========================================================
         * CRUCIBLE RESERVOIR
         * ========================================================
         *
         * Older saves do not contain this tag, so an absent compound
         * naturally loads as an empty reservoir.
         */

        readLongMap(
                root.getCompound(
                        CRUCIBLE_RESERVOIR_TAG
                ),
                data.crucibleReservoir
        );


        /*
         * ========================================================
         * INVESTED ESSENCE
         * ========================================================
         */

        readLongMap(
                root.getCompound(
                        INVESTED_TAG
                ),
                data.investedEssence
        );


        /*
         * ========================================================
         * INTERNAL MILESTONES
         * ========================================================
         *
         * Older saves do not contain this tag, so check for it
         * explicitly.
         */

        if (root.contains(
                COMPLETED_MILESTONES_TAG,
                Tag.TAG_COMPOUND
        )) {

            CompoundTag milestoneTag =
                    root.getCompound(
                            COMPLETED_MILESTONES_TAG
                    );

            for (String key :
                    milestoneTag.getAllKeys()) {

                ResourceLocation milestoneId =
                        ResourceLocation.tryParse(
                                key
                        );

                if (milestoneId == null) {

                    EssenceAscendance.LOGGER.warn(
                            "Ignoring invalid milestone ID '{}' in Essence Ascendance player data",
                            key
                    );

                    continue;
                }

                if (milestoneTag.getBoolean(
                        key
                )) {

                    data.completedMilestones.add(
                            milestoneId
                    );
                }
            }
        }


        return data;
    }


    /*
     * ============================================================
     * NBT HELPERS
     * ============================================================
     */

    private static void readLongMap(
            CompoundTag tag,
            Map<ResourceLocation, Long> target
    ) {

        for (String key :
                tag.getAllKeys()) {

            ResourceLocation id =
                    ResourceLocation.tryParse(
                            key
                    );

            if (id == null) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid ResourceLocation '{}' in Essence Ascendance player data",
                        key
                );

                continue;
            }

            long value =
                    tag.getLong(
                            key
                    );

            if (value > 0) {

                target.put(
                        id,
                        value
                );
            }
        }
    }
}