package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Permanent bridge over the configurable milestone framework.
 *
 * <p>Definitions, providers, targets, and live completion all remain owned by
 * {@link MilestoneService}. Once a configured milestone succeeds, its stable
 * definition ID can also be captured in player data. That preserves permanent
 * skill-purchase eligibility if a server later changes the configured target,
 * while still allowing uncaptured gates to follow config overrides.</p>
 */
public final class PermanentMilestoneService {

    private PermanentMilestoneService() {
    }

    /** Resolves effective permanent state without mutating player data. */
    public static List<Resolution> resolveAll(
            ServerPlayer player,
            Collection<ResourceLocation> milestoneIds
    ) {
        return evaluateAll(player, milestoneIds, false);
    }

    /**
     * Resolves state and permanently captures every newly completed configured
     * milestone. Saving and revision changes occur only for first capture.
     */
    public static List<Resolution> captureCompleted(
            ServerPlayer player,
            Collection<ResourceLocation> milestoneIds
    ) {
        return evaluateAll(player, milestoneIds, true);
    }

    public static Set<ResourceLocation> completedIds(
            Collection<Resolution> resolutions
    ) {
        Objects.requireNonNull(resolutions, "Milestone resolutions cannot be null");

        Set<ResourceLocation> completed = new LinkedHashSet<>();
        for (Resolution resolution : resolutions) {
            if (resolution.complete()) {
                completed.add(resolution.milestoneId());
            }
        }
        return Collections.unmodifiableSet(completed);
    }

    private static List<Resolution> evaluateAll(
            ServerPlayer player,
            Collection<ResourceLocation> milestoneIds,
            boolean capture
    ) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(milestoneIds, "Milestone IDs cannot be null");

        EssenceSavedData savedData = EssenceSavedData.get(player.server);
        PlayerEssenceData playerData = savedData.getPlayerData(player.getUUID());
        EssenceServerConfig config = EssenceConfigManager.get();
        Set<ResourceLocation> uniqueIds = new LinkedHashSet<>(milestoneIds);
        List<Resolution> resolutions = new ArrayList<>(uniqueIds.size());

        for (ResourceLocation milestoneId : uniqueIds) {
            Objects.requireNonNull(
                    milestoneId,
                    "Milestone IDs cannot contain null"
            );

            boolean captured = playerData.hasCompletedMilestone(milestoneId);
            String displayName = config
                    .getMilestone(milestoneId)
                    .map(MilestoneDefinition::displayName)
                    .orElse(milestoneId.toString());

            /* Captured completion is deliberately independent of later config. */
            if (captured) {
                resolutions.add(
                        new Resolution(
                                milestoneId,
                                displayName,
                                true,
                                true,
                                true,
                                false
                        )
                );
                continue;
            }

            MilestoneProgress providerProgress;
            try {
                providerProgress = MilestoneService.evaluate(
                        player,
                        MilestoneRequirement.milestone(milestoneId)
                );
            } catch (RuntimeException exception) {
                EssenceAscendance.LOGGER.error(
                        "Permanent milestone '{}' provider evaluation failed",
                        milestoneId,
                        exception
                );
                resolutions.add(
                        new Resolution(
                                milestoneId,
                                displayName,
                                false,
                                false,
                                false,
                                false
                        )
                );
                continue;
            }

            boolean providerComplete = providerProgress.resolvable()
                    && providerProgress.complete();

            if (capture && !captured && providerComplete) {
                savedData.completeMilestone(player.getUUID(), milestoneId);
                captured = true;
            }

            /* A captured permanent result remains usable even if config breaks. */
            boolean complete = captured || providerComplete;
            boolean resolvable = captured || providerProgress.resolvable();

            resolutions.add(
                    new Resolution(
                            milestoneId,
                            displayName,
                            resolvable,
                            complete,
                            captured,
                            providerComplete
                    )
            );
        }

        return List.copyOf(resolutions);
    }

    public record Resolution(
            ResourceLocation milestoneId,
            String displayName,
            boolean resolvable,
            boolean complete,
            boolean captured,
            boolean providerComplete
    ) {
        public Resolution {
            Objects.requireNonNull(milestoneId, "Milestone ID cannot be null");
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("Milestone display name cannot be blank");
            }
            if (complete && !resolvable) {
                throw new IllegalArgumentException(
                        "A completed permanent milestone must be resolvable"
                );
            }
        }
    }
}
