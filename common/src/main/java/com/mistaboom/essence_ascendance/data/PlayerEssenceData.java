package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerEssenceData {

    private static final String AVAILABLE_TAG = "available";
    private static final String INVESTED_TAG = "invested";

    private final Map<ResourceLocation, Long> availableEssence =
            new LinkedHashMap<>();

    private final Map<ResourceLocation, Long> investedEssence =
            new LinkedHashMap<>();


    /*
     * ============================================================
     * AVAILABLE ESSENCE
     * ============================================================
     */

    public long getAvailable(EssenceDefinition essence) {
        return getAvailable(essence.id());
    }

    public long getAvailable(ResourceLocation essenceId) {
        return availableEssence.getOrDefault(essenceId, 0L);
    }

    public long addAvailable(EssenceDefinition essence, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Essence amount must be greater than zero"
            );
        }

        long current = getAvailable(essence);

        long updated = Math.addExact(current, amount);

        availableEssence.put(essence.id(), updated);

        return updated;
    }

    public void setAvailable(EssenceDefinition essence, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Essence amount cannot be negative"
            );
        }

        if (amount == 0) {
            availableEssence.remove(essence.id());
        } else {
            availableEssence.put(essence.id(), amount);
        }
    }

    public Map<ResourceLocation, Long> getAllAvailable() {
        return Collections.unmodifiableMap(availableEssence);
    }


    /*
     * ============================================================
     * INVESTED ESSENCE
     * ============================================================
     */

    public long getInvested(StatDefinition stat) {
        return getInvested(stat.id());
    }

    public long getInvested(ResourceLocation statId) {
        return investedEssence.getOrDefault(statId, 0L);
    }

    public boolean invest(StatDefinition stat, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Investment amount must be greater than zero"
            );
        }

        EssenceDefinition requiredEssence = stat.essenceType();

        long available = getAvailable(requiredEssence);

        if (available < amount) {
            return false;
        }

        long currentInvestment = getInvested(stat);
        long newInvestment = Math.addExact(currentInvestment, amount);

        long remaining = available - amount;

        if (remaining == 0) {
            availableEssence.remove(requiredEssence.id());
        } else {
            availableEssence.put(requiredEssence.id(), remaining);
        }

        investedEssence.put(stat.id(), newInvestment);

        return true;
    }

    public Map<ResourceLocation, Long> getAllInvested() {
        return Collections.unmodifiableMap(investedEssence);
    }

    public void setInvested(StatDefinition stat, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Invested Essence amount cannot be negative"
            );
        }

        if (amount == 0) {
            investedEssence.remove(stat.id());
        } else {
            investedEssence.put(stat.id(), amount);
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
     * NBT SERIALIZATION
     * ============================================================
     */

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();

        CompoundTag availableTag = new CompoundTag();

        for (Map.Entry<ResourceLocation, Long> entry :
                availableEssence.entrySet()) {

            availableTag.putLong(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(AVAILABLE_TAG, availableTag);


        CompoundTag investedTag = new CompoundTag();

        for (Map.Entry<ResourceLocation, Long> entry :
                investedEssence.entrySet()) {

            investedTag.putLong(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(INVESTED_TAG, investedTag);

        return root;
    }

    public static PlayerEssenceData load(CompoundTag root) {
        PlayerEssenceData data = new PlayerEssenceData();

        readLongMap(
                root.getCompound(AVAILABLE_TAG),
                data.availableEssence
        );

        readLongMap(
                root.getCompound(INVESTED_TAG),
                data.investedEssence
        );

        return data;
    }

    private static void readLongMap(
            CompoundTag tag,
            Map<ResourceLocation, Long> target
    ) {
        for (String key : tag.getAllKeys()) {

            ResourceLocation id =
                    ResourceLocation.tryParse(key);

            if (id == null) {
                continue;
            }

            long value = tag.getLong(key);

            if (value > 0) {
                target.put(id, value);
            }
        }
    }
}