package com.mistaboom.essence_ascendance.balance;

import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BalanceProfileDefinition {

    private final ResourceLocation id;

    private final String displayName;

    private final Map<ResourceLocation, Double> tierFractions;
    private final double investmentExponent;
    private final Map<ResourceLocation, BonusTrackDefinition> bonusTracks;

    /*
     * Default maximum invested Essence for each Ascendance tier.
     *
     * ResourceLocation IDs are used rather than tier order so
     * persistent/configuration identity remains stable.
     */
    private final Map<ResourceLocation, Long> defaultTierCaps;

    /*
     * Optional per-stat overrides.
     *
     * Outer key:
     *     stat ID
     *
     * Inner key:
     *     tier ID
     *
     * Value:
     *     investment cap
     *
     * Most stats will simply use defaultTierCaps.
     */
    private final Map<
            ResourceLocation,
            Map<ResourceLocation, Long>
            > statOverrides;


    public BalanceProfileDefinition(
            ResourceLocation id,
            String displayName,
            Map<ResourceLocation, Long> defaultTierCaps,
            Map<
                    ResourceLocation,
                    Map<ResourceLocation, Long>
                    > statOverrides
    ) {
        this(id, displayName, defaultTierCaps, statOverrides, Map.of(), 1.0);
    }

    public BalanceProfileDefinition(ResourceLocation id, String displayName,
            Map<ResourceLocation, Long> defaultTierCaps,
            Map<ResourceLocation, Map<ResourceLocation, Long>> statOverrides,
            Map<ResourceLocation, Double> tierFractions, double investmentExponent) {
        this(id, displayName, defaultTierCaps, statOverrides, tierFractions, investmentExponent, Map.of());
    }

    public BalanceProfileDefinition(ResourceLocation id, String displayName,
            Map<ResourceLocation, Long> defaultTierCaps,
            Map<ResourceLocation, Map<ResourceLocation, Long>> statOverrides,
            Map<ResourceLocation, Double> tierFractions, double investmentExponent,
            Map<ResourceLocation, BonusTrackDefinition> bonusTracks) {
        this.bonusTracks = Collections.unmodifiableMap(new java.util.TreeMap<>(bonusTracks));
        this.tierFractions = Collections.unmodifiableMap(new LinkedHashMap<>(tierFractions));
        if (!Double.isFinite(investmentExponent) || investmentExponent <= 0 || investmentExponent > 1)
            throw new IllegalArgumentException("Investment exponent must be in (0, 1]");
        this.investmentExponent = investmentExponent;
        this.id =
                id;

        this.displayName =
                displayName;

        this.defaultTierCaps =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                defaultTierCaps
                        )
                );

        Map<
                ResourceLocation,
                Map<ResourceLocation, Long>
                > copiedOverrides =
                new LinkedHashMap<>();

        for (Map.Entry<
                ResourceLocation,
                Map<ResourceLocation, Long>
                > entry : statOverrides.entrySet()) {

            copiedOverrides.put(
                    entry.getKey(),
                    Collections.unmodifiableMap(
                            new LinkedHashMap<>(
                                    entry.getValue()
                            )
                    )
            );
        }

        this.statOverrides =
                Collections.unmodifiableMap(
                        copiedOverrides
                );

        validate();
    }


    public ResourceLocation id() {
        return id;
    }


    public String displayName() {
        return displayName;
    }

    public Map<ResourceLocation, Double> tierFractions() { return tierFractions; }
    public double investmentExponent() { return investmentExponent; }
    public Map<ResourceLocation, BonusTrackDefinition> bonusTracks() { return bonusTracks; }
    public BonusTrackDefinition bonusTrack(ResourceLocation statId) { return bonusTracks.get(statId); }
    public double tierFraction(AscendanceTierDefinition tier, int index, int count) {
        return tierFractions.getOrDefault(tier.id(), (index + 1.0) / count);
    }


    /*
     * Returns the effective investment cap for a stat at a tier.
     *
     * Resolution order:
     *
     * 1. Per-stat tier override
     * 2. Profile's default tier cap
     */
    public long getInvestmentCap(
            AscendanceTierDefinition tier,
            StatDefinition stat
    ) {
        BonusTrackDefinition track = bonusTracks.get(stat.id());
        if (track != null) return track.checkpoint(tier.id()).cumulativeCap();
        Map<ResourceLocation, Long> statCaps =
                statOverrides.get(
                        stat.id()
                );

        if (statCaps != null) {

            Long override =
                    statCaps.get(
                            tier.id()
                    );

            if (override != null) {
                return override;
            }
        }

        Long defaultCap =
                defaultTierCaps.get(
                        tier.id()
                );

        if (defaultCap == null) {
            throw new IllegalStateException(
                    "Balance profile "
                            + id
                            + " has no investment cap for tier "
                            + tier.id()
            );
        }

        return defaultCap;
    }


    public long getDefaultInvestmentCap(
            AscendanceTierDefinition tier
    ) {
        Long cap =
                defaultTierCaps.get(
                        tier.id()
                );

        if (cap == null) {
            throw new IllegalStateException(
                    "Balance profile "
                            + id
                            + " has no investment cap for tier "
                            + tier.id()
            );
        }

        return cap;
    }


    public Map<ResourceLocation, Long> defaultTierCaps() {
        return defaultTierCaps;
    }


    public Map<
            ResourceLocation,
            Map<ResourceLocation, Long>
            > statOverrides() {

        return statOverrides;
    }


    private void validate() {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Balance profile ID cannot be null"
            );
        }

        if (displayName == null
                || displayName.isBlank()) {

            throw new IllegalArgumentException(
                    "Balance profile display name cannot be blank"
            );
        }


        for (Map.Entry<ResourceLocation, Long> entry :
                defaultTierCaps.entrySet()) {

            if (entry.getValue() < 0) {
                throw new IllegalArgumentException(
                        "Negative investment cap for tier "
                                + entry.getKey()
                                + " in balance profile "
                                + id
                );
            }
        }


        for (Map.Entry<
                ResourceLocation,
                Map<ResourceLocation, Long>
                > statEntry :
                statOverrides.entrySet()) {

            for (Map.Entry<ResourceLocation, Long> tierEntry :
                    statEntry.getValue().entrySet()) {

                if (tierEntry.getValue() < 0) {
                    throw new IllegalArgumentException(
                            "Negative investment cap for stat "
                                    + statEntry.getKey()
                                    + " at tier "
                                    + tierEntry.getKey()
                                    + " in balance profile "
                                    + id
                    );
                }
            }
        }
    }
}
