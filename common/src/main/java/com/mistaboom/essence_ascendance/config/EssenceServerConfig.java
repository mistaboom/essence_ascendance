package com.mistaboom.essence_ascendance.config;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.ArmorChassisConfig;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EssenceServerConfig {

    private final int configVersion;

    private final BalanceProfileDefinition balanceProfile;

    private final Map<ResourceLocation, MilestoneDefinition> milestones;

    private final Map<ResourceLocation, AscendanceAdvancementDefinition> advancements;

    private final Map<ResourceLocation, AscendanceAdvancementDefinition> advancementsByFromTier;

    private final Map<ResourceLocation, Double> statMaxBonuses;

    private final ArmorChassisConfig armorChassisConfig;


    public EssenceServerConfig(
            int configVersion,
            BalanceProfileDefinition balanceProfile,
            Map<ResourceLocation, MilestoneDefinition> milestones,
            Map<ResourceLocation, AscendanceAdvancementDefinition> advancements,
            Map<ResourceLocation, Double> statMaxBonuses,
            ArmorChassisConfig armorChassisConfig
    ) {

        this.configVersion =
                configVersion;

        this.balanceProfile =
                balanceProfile;

        this.milestones =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                milestones
                        )
                );

        this.advancements =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                advancements
                        )
                );

        this.statMaxBonuses =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                statMaxBonuses
                        )
                );

        this.armorChassisConfig =
                armorChassisConfig;


        Map<ResourceLocation, AscendanceAdvancementDefinition> byTier =
                new LinkedHashMap<>();


        for (AscendanceAdvancementDefinition advancement :
                advancements.values()) {

            AscendanceAdvancementDefinition previous =
                    byTier.put(
                            advancement.fromTierId(),
                            advancement
                    );


            if (previous != null) {

                throw new IllegalArgumentException(
                        "Multiple Ascendance advancement definitions originate from tier "
                                + advancement.fromTierId()
                );
            }
        }


        this.advancementsByFromTier =
                Collections.unmodifiableMap(
                        byTier
                );
    }


    public int configVersion() {
        return configVersion;
    }


    public BalanceProfileDefinition balanceProfile() {
        return balanceProfile;
    }


    public Optional<MilestoneDefinition> getMilestone(
            ResourceLocation milestoneId
    ) {

        return Optional.ofNullable(
                milestones.get(
                        milestoneId
                )
        );
    }


    public Map<ResourceLocation, MilestoneDefinition> milestones() {
        return milestones;
    }


    public Map<ResourceLocation, Double> statMaxBonuses() {
        return statMaxBonuses;
    }


    public double statMaxBonus(
            StatDefinition stat
    ) {

        Double value =
                statMaxBonuses.get(
                        stat.id()
                );


        if (value == null) {

            throw new IllegalStateException(
                    "No stat scaling definition exists for "
                            + stat.id()
            );
        }


        return value;
    }


    public ArmorChassisConfig armorChassisConfig() {
        return armorChassisConfig;
    }


    public Optional<AscendanceAdvancementDefinition> getAdvancement(
            ResourceLocation advancementId
    ) {

        return Optional.ofNullable(
                advancements.get(
                        advancementId
                )
        );
    }


    public Optional<AscendanceAdvancementDefinition> getAdvancementForTier(
            ResourceLocation tierId
    ) {

        return Optional.ofNullable(
                advancementsByFromTier.get(
                        tierId
                )
        );
    }


    public Map<ResourceLocation, AscendanceAdvancementDefinition> advancements() {
        return advancements;
    }
}