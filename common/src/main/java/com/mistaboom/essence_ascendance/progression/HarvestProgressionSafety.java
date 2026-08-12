package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Detects configuration combinations where an Ascendance transition requires
 * a vanilla mining milestone that the current tier's own Ascendance tools
 * cannot reach.
 *
 * This validator intentionally WARNS rather than clamps. A modpack may choose
 * to require outside tools, alternate progression, or custom milestone logic;
 * the configuration remains authoritative.
 */
public final class HarvestProgressionSafety {

    private static final String MINE_DIAMOND_ADVANCEMENT =
            "minecraft:story/mine_diamond";

    private static final String OBTAIN_ANCIENT_DEBRIS_ADVANCEMENT =
            "minecraft:nether/obtain_ancient_debris";

    private HarvestProgressionSafety() {
    }

    public static List<Issue> evaluate(EssenceServerConfig config) {
        Objects.requireNonNull(config, "Config cannot be null");

        List<Issue> issues = new ArrayList<>();

        for (AscendanceAdvancementDefinition advancement :
                config.advancements().values()) {

            int requiredLevel = requiredHarvestLevel(
                    config,
                    advancement.worldRequirement()
            );

            if (requiredLevel <= 0) {
                continue;
            }

            var tier = AscendanceTierRegistry
                    .get(advancement.fromTierId())
                    .orElse(null);

            if (tier == null) {
                continue;
            }

            EquipmentBaselineConfig.TierBaseline baseline =
                    config.equipmentBaselineConfig().baselineFor(tier);

            if (baseline.harvestLevel() < requiredLevel) {
                issues.add(
                        new Issue(
                                advancement.id(),
                                advancement.fromTierId(),
                                advancement.toTierId(),
                                baseline.harvestLevel(),
                                requiredLevel
                        )
                );
            }
        }

        return List.copyOf(issues);
    }

    public static void logWarnings(EssenceServerConfig config) {
        for (Issue issue : evaluate(config)) {
            EssenceAscendance.LOGGER.warn(
                    "Ascendance harvest progression hazard: transition '{}' ({} -> {}) "
                            + "requires at least logical harvest level {}, but the from-tier "
                            + "is configured for level {}. Ascendance tools alone may be unable "
                            + "to obtain the material required by that transition.",
                    issue.advancementId(),
                    issue.fromTierId(),
                    issue.toTierId(),
                    issue.requiredHarvestLevel(),
                    issue.configuredHarvestLevel()
            );
        }
    }

    private static int requiredHarvestLevel(
            EssenceServerConfig config,
            MilestoneRequirement requirement
    ) {
        if (requirement instanceof MilestoneRequirement.Always) {
            return 0;
        }

        if (requirement instanceof MilestoneRequirement.Milestone milestone) {
            return config
                    .getMilestone(milestone.milestoneId())
                    .map(HarvestProgressionSafety::milestoneHarvestLevel)
                    .orElse(0);
        }

        if (requirement instanceof MilestoneRequirement.AllOf allOf) {
            int maximum = 0;
            for (MilestoneRequirement child : allOf.children()) {
                maximum = Math.max(
                        maximum,
                        requiredHarvestLevel(config, child)
                );
            }
            return maximum;
        }

        if (requirement instanceof MilestoneRequirement.AnyOf anyOf) {
            int minimum = Integer.MAX_VALUE;
            for (MilestoneRequirement child : anyOf.children()) {
                minimum = Math.min(
                        minimum,
                        requiredHarvestLevel(config, child)
                );
            }
            return minimum == Integer.MAX_VALUE ? 0 : minimum;
        }

        return 0;
    }

    private static int milestoneHarvestLevel(MilestoneDefinition milestone) {
        if (!milestone.providerId().equals(MilestoneProviders.ADVANCEMENT)) {
            return 0;
        }

        return switch (milestone.target()) {
            case MINE_DIAMOND_ADVANCEMENT -> 2;
            case OBTAIN_ANCIENT_DEBRIS_ADVANCEMENT -> 3;
            default -> 0;
        };
    }

    public record Issue(
            ResourceLocation advancementId,
            ResourceLocation fromTierId,
            ResourceLocation toTierId,
            int configuredHarvestLevel,
            int requiredHarvestLevel
    ) {
    }
}
