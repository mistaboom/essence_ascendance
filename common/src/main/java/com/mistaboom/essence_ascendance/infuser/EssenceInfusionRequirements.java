package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic Essence requirement shape for an Infuser recipe.
 *
 * Every entry in {@code minimumByEssence} is a hard per-Essence floor. The
 * recipe may additionally require a larger {@code totalRequired}; any amount
 * above the summed minimums is flexible and may be satisfied by recipe-specific
 * eligible Essences. This covers both current built-in cases:
 *
 * - Essentium conversion: one source Essence minimum == total required.
 * - Focus infusion: six Attribute-Essence minimums plus a flexible remainder.
 */
public record EssenceInfusionRequirements(
        Map<ResourceLocation, Long> minimumByEssence,
        long totalRequired
) {

    public EssenceInfusionRequirements {
        if (minimumByEssence == null) {
            throw new IllegalArgumentException("Infusion Essence minimums cannot be null");
        }
        if (totalRequired < 0L) {
            throw new IllegalArgumentException("Infusion total cannot be negative");
        }

        Map<ResourceLocation, Long> normalized = new LinkedHashMap<>();
        long minimumTotal = 0L;
        for (Map.Entry<ResourceLocation, Long> entry : minimumByEssence.entrySet()) {
            ResourceLocation id = entry.getKey();
            Long amount = entry.getValue();
            if (id == null || amount == null || amount < 0L) {
                throw new IllegalArgumentException("Invalid Infuser Essence minimum");
            }
            if (amount == 0L) {
                continue;
            }
            minimumTotal = Math.addExact(minimumTotal, amount);
            normalized.put(id, amount);
        }
        if (totalRequired < minimumTotal) {
            throw new IllegalArgumentException(
                    "Infusion total must be at least the sum of its Essence minimums"
            );
        }
        minimumByEssence = Map.copyOf(normalized);
    }

    public static EssenceInfusionRequirements none() {
        return new EssenceInfusionRequirements(Map.of(), 0L);
    }

    public long minimumFor(EssenceDefinition essence) {
        return essence == null ? 0L : minimumByEssence.getOrDefault(essence.id(), 0L);
    }

    public long minimumTotal() {
        long result = 0L;
        for (long amount : minimumByEssence.values()) {
            result = Math.addExact(result, amount);
        }
        return result;
    }

    public long flexibleRemainder() {
        return Math.max(0L, totalRequired - minimumTotal());
    }

    public boolean hasFlexibleRemainder() {
        return flexibleRemainder() > 0L;
    }
}
