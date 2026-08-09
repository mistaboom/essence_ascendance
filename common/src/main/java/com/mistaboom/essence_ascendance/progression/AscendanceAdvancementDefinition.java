package com.mistaboom.essence_ascendance.progression;

import net.minecraft.resources.ResourceLocation;

public record AscendanceAdvancementDefinition(
        ResourceLocation id,
        ResourceLocation fromTierId,
        ResourceLocation toTierId,
        long totalInvestmentMultiplier,
        int minimumDevelopedStats,
        int minimumRepresentedCategories,
        double developedStatThreshold,
        MilestoneRequirement worldRequirement
) {

    public AscendanceAdvancementDefinition {

        if (id == null
                || fromTierId == null
                || toTierId == null) {

            throw new IllegalArgumentException(
                    "Ascendance advancement IDs cannot be null"
            );
        }

        if (totalInvestmentMultiplier <= 0) {
            throw new IllegalArgumentException(
                    "Total investment multiplier must be greater than zero"
            );
        }

        if (minimumDevelopedStats < 0) {
            throw new IllegalArgumentException(
                    "Minimum developed stats cannot be negative"
            );
        }

        if (minimumRepresentedCategories < 0) {
            throw new IllegalArgumentException(
                    "Minimum represented categories cannot be negative"
            );
        }

        if (developedStatThreshold <= 0.0D
                || developedStatThreshold > 1.0D) {

            throw new IllegalArgumentException(
                    "Developed-stat threshold must be greater than 0 and no greater than 1"
            );
        }

        if (worldRequirement == null) {
            throw new IllegalArgumentException(
                    "World milestone requirement cannot be null"
            );
        }
    }


    public long getRequiredInvestment(
            long currentTierDefaultCap
    ) {
        return Math.multiplyExact(
                currentTierDefaultCap,
                totalInvestmentMultiplier
        );
    }


    public long getDevelopedThreshold(
            long statCap
    ) {
        return (long) Math.ceil(
                statCap
                        * developedStatThreshold
        );
    }
}