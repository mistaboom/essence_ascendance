package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerEssenceData {

    private static final String AVAILABLE_TAG =
            "available";

    private static final String INVESTED_TAG =
            "invested";

    private static final String TIER_TAG =
            "tier";


    private final Map<ResourceLocation, Long> availableEssence =
            new LinkedHashMap<>();

    private final Map<ResourceLocation, Long> investedEssence =
            new LinkedHashMap<>();


    /*
     * Store the stable ResourceLocation rather than an enum ordinal
     * or tier order.
     */
    private ResourceLocation currentTierId =
            AscendanceTiers.DORMANT.id();


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
                getAvailable(essence);

        long updated =
                Math.addExact(
                        current,
                        amount
                );

        availableEssence.put(
                essence.id(),
                updated
        );

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
    }


    public Map<ResourceLocation, Long> getAllAvailable() {
        return Collections.unmodifiableMap(
                availableEssence
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


    /*
     * This is currently the normal investment operation.
     *
     * Later, Issue 6.5 will move normal progression validation
     * into the centralized progression transaction service.
     */
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
                getInvested(stat);

        /*
         * Calculate all resulting values before mutating either map.
         * Math.addExact also protects against long overflow.
         */
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

        return true;
    }


    public Map<ResourceLocation, Long> getAllInvested() {
        return Collections.unmodifiableMap(
                investedEssence
        );
    }


    /*
     * Administrative/debug operation.
     *
     * This intentionally bypasses normal investment rules.
     */
    public void setInvested(
            StatDefinition stat,
            long amount
    ) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Invested Essence amount cannot be negative"
            );
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
    }


    public void clearAvailable() {
        availableEssence.clear();
    }


    public void clearInvested() {
        investedEssence.clear();
    }


    public void clearAll() {
        availableEssence.clear();
        investedEssence.clear();
    }


    /*
     * ============================================================
     * ASCENDANCE TIER
     * ============================================================
     */

    public AscendanceTierDefinition getTier() {
        /*
         * If the stored ID is valid but its tier is not currently
         * registered, behave as Dormant without destroying the
         * stored ID.
         */
        return AscendanceTierRegistry
                .get(currentTierId)
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
        currentTierId =
                tier.id();
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

                /*
                 * Preserve valid ResourceLocation IDs even if the
                 * corresponding tier is not currently registered.
                 *
                 * This allows data belonging to a temporarily
                 * missing addon or future tier to survive.
                 *
                 * getTier() safely returns Dormant while that ID
                 * remains unavailable.
                 */
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

                /*
                 * A syntactically invalid ResourceLocation cannot
                 * safely be preserved as an ID.
                 */
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
         * INVESTED ESSENCE
         * ========================================================
         */

        readLongMap(
                root.getCompound(
                        INVESTED_TAG
                ),
                data.investedEssence
        );


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

            /*
             * Ignore malformed IDs rather than preventing the
             * entire player record/world from loading.
             */
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

            /*
             * Zero values do not need to occupy save space.
             * Negative progression values are treated as invalid.
             */
            if (value > 0) {
                target.put(
                        id,
                        value
                );
            }
        }
    }
}