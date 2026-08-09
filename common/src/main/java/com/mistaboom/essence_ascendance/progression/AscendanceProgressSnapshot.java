package com.mistaboom.essence_ascendance.progression;

import net.minecraft.resources.ResourceLocation;

public record AscendanceProgressSnapshot(
        ResourceLocation currentTierId,
        ResourceLocation nextTierId,
        long effectiveInvestment,
        long requiredInvestment,
        int developedStats,
        int requiredDevelopedStats,
        int representedCategories,
        int requiredRepresentedCategories,
        MilestoneProgress worldProgress
) {

    public AscendanceProgressSnapshot {

        if (currentTierId == null
                || nextTierId == null) {

            throw new IllegalArgumentException(
                    "Progress snapshot tier IDs cannot be null"
            );
        }

        if (effectiveInvestment < 0
                || requiredInvestment < 0) {

            throw new IllegalArgumentException(
                    "Investment progress cannot be negative"
            );
        }

        if (developedStats < 0
                || requiredDevelopedStats < 0
                || representedCategories < 0
                || requiredRepresentedCategories < 0) {

            throw new IllegalArgumentException(
                    "Progress counts cannot be negative"
            );
        }

        if (worldProgress == null) {
            throw new IllegalArgumentException(
                    "World progress cannot be null"
            );
        }
    }


    public boolean investmentComplete() {
        return effectiveInvestment
                >= requiredInvestment;
    }


    public boolean developedStatsComplete() {
        return developedStats
                >= requiredDevelopedStats;
    }


    public boolean categoriesComplete() {
        return representedCategories
                >= requiredRepresentedCategories;
    }


    public boolean worldProgressComplete() {
        return worldProgress.resolvable()
                && worldProgress.complete();
    }


    public boolean readyToAscend() {
        return investmentComplete()
                && developedStatsComplete()
                && categoriesComplete()
                && worldProgressComplete();
    }
}