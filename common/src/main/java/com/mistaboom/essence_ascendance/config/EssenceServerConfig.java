package com.mistaboom.essence_ascendance.config;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EssenceServerConfig {

    private final int configVersion;
    private final double pylonRadius;
    private final int maxActivePylons;
    private final InfuserBalanceSettings infuserBalance;
    private final ShieldBalanceSettings shieldBalance;
    private final LatentOreWorldgenSettings latentOreWorldgen;
    private final BalanceProfileDefinition balanceProfile;
    private final Map<ResourceLocation, MilestoneDefinition> milestones;
    private final Map<ResourceLocation, AscendanceAdvancementDefinition> advancements;
    private final Map<ResourceLocation, AscendanceAdvancementDefinition> advancementsByFromTier;
    private final Map<ResourceLocation, Double> statMaxBonuses;
    private final EquipmentBaselineConfig equipmentBaselineConfig;

    public EssenceServerConfig(
            int configVersion,
            double pylonRadius,
            int maxActivePylons,
            InfuserBalanceSettings infuserBalance,
            ShieldBalanceSettings shieldBalance,
            LatentOreWorldgenSettings latentOreWorldgen,
            BalanceProfileDefinition balanceProfile,
            Map<ResourceLocation, MilestoneDefinition> milestones,
            Map<ResourceLocation, AscendanceAdvancementDefinition> advancements,
            Map<ResourceLocation, Double> statMaxBonuses,
            EquipmentBaselineConfig equipmentBaselineConfig
    ) {
        this.shieldBalance = Objects.requireNonNull(shieldBalance, "Shield balance cannot be null");
        this.configVersion = configVersion;
        if (!(pylonRadius > 0.0D) || !Double.isFinite(pylonRadius)) {
            throw new IllegalArgumentException("Pylon radius must be finite and positive");
        }
        if (maxActivePylons < 0) {
            throw new IllegalArgumentException("Maximum active pylons cannot be negative");
        }
        this.pylonRadius = pylonRadius;
        this.maxActivePylons = maxActivePylons;
        this.infuserBalance = Objects.requireNonNull(
                infuserBalance,
                "Infuser balance cannot be null"
        );
        this.latentOreWorldgen = Objects.requireNonNull(
                latentOreWorldgen,
                "Latent Ore worldgen settings cannot be null"
        );
        this.balanceProfile = Objects.requireNonNull(
                balanceProfile,
                "Balance profile cannot be null"
        );
        this.milestones = Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        Objects.requireNonNull(
                                milestones,
                                "Milestone definitions cannot be null"
                        )
                )
        );
        this.advancements = Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        Objects.requireNonNull(
                                advancements,
                                "Advancement definitions cannot be null"
                        )
                )
        );
        this.statMaxBonuses = Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        Objects.requireNonNull(
                                statMaxBonuses,
                                "Stat max bonuses cannot be null"
                        )
                )
        );
        this.equipmentBaselineConfig = Objects.requireNonNull(
                equipmentBaselineConfig,
                "Equipment baseline config cannot be null"
        );

        Map<ResourceLocation, AscendanceAdvancementDefinition> byTier =
                new LinkedHashMap<>();

        for (AscendanceAdvancementDefinition advancement :
                this.advancements.values()) {
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
                Collections.unmodifiableMap(byTier);
    }

    public int configVersion() {
        return configVersion;
    }

    public double pylonRadius() {
        return pylonRadius;
    }

    public int maxActivePylons() {
        return maxActivePylons;
    }

    public ShieldBalanceSettings shieldBalance() { return shieldBalance; }

    public InfuserBalanceSettings infuserBalance() {
        return infuserBalance;
    }

    public LatentOreWorldgenSettings latentOreWorldgen() {
        return latentOreWorldgen;
    }

    public BalanceProfileDefinition balanceProfile() {
        return balanceProfile;
    }

    public Optional<MilestoneDefinition> getMilestone(ResourceLocation milestoneId) {
        return Optional.ofNullable(milestones.get(milestoneId));
    }

    public Map<ResourceLocation, MilestoneDefinition> milestones() {
        return milestones;
    }

    public Map<ResourceLocation, Double> statMaxBonuses() {
        return statMaxBonuses;
    }

    public double statMaxBonus(StatDefinition stat) {
        Objects.requireNonNull(stat, "Stat cannot be null");

        Double value = statMaxBonuses.get(stat.id());
        if (value == null) {
            throw new IllegalStateException(
                    "No stat scaling definition exists for " + stat.id()
            );
        }
        return value;
    }

    public EquipmentBaselineConfig equipmentBaselineConfig() {
        return equipmentBaselineConfig;
    }

    public Optional<AscendanceAdvancementDefinition> getAdvancement(
            ResourceLocation advancementId
    ) {
        return Optional.ofNullable(advancements.get(advancementId));
    }

    public Optional<AscendanceAdvancementDefinition> getAdvancementForTier(
            ResourceLocation tierId
    ) {
        return Optional.ofNullable(advancementsByFromTier.get(tierId));
    }

    public Map<ResourceLocation, AscendanceAdvancementDefinition> advancements() {
        return advancements;
    }
}
