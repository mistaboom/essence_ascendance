package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.network.AscendanceNexusTransactionResultPayload;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillChoiceGroup;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationContext;
import com.mistaboom.essence_ascendance.skill.SkillPurchaseEligibility;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.SkillStateEvaluator;
import com.mistaboom.essence_ascendance.skill.requirement.PermanentMilestoneRequirement;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Server-authoritative validator and single commit point for a complete Nexus
 * draft. No mutation occurs until Bonus targets, purchases, selections, and an
 * optional Ascension have all passed validation against the same baseline.
 */
public final class AscendanceNexusTransactionService {

    private AscendanceNexusTransactionService() {
    }

    public static Result apply(
            ServerPlayer player,
            long baseNexusRevision,
            ResourceLocation baseTierId,
            ResourceLocation baseBalanceProfileId,
            Map<ResourceLocation, Long> finalBonusTargets,
            Map<ResourceLocation, Integer> requestedPurchases,
            Map<ResourceLocation, Optional<ResourceLocation>> loadoutChanges,
            boolean ascend
    ) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(baseTierId, "Base tier ID cannot be null");
        Objects.requireNonNull(
                baseBalanceProfileId,
                "Base balance-profile ID cannot be null"
        );
        Objects.requireNonNull(finalBonusTargets, "Final Bonus targets cannot be null");
        Objects.requireNonNull(requestedPurchases, "Requested purchases cannot be null");
        Objects.requireNonNull(loadoutChanges, "Loadout changes cannot be null");

        EssenceSavedData savedData =
                EssenceSavedData.get(player.server);
        PlayerEssenceData playerData =
                savedData.getPlayerData(player.getUUID());
        BalanceProfileDefinition balanceProfile =
                EssenceConfigManager.get().balanceProfile();

        if (baseNexusRevision < 0L
                || playerData.nexusRevision() != baseNexusRevision) {
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.STALE,
                    playerData.nexusRevision()
            );
        }

        if (playerData.nexusRevision() == Long.MAX_VALUE) {
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.TRANSACTION_FAILED,
                    playerData.nexusRevision()
            );
        }

        if (!playerData.getTierId().equals(baseTierId)
                || !balanceProfile.id().equals(baseBalanceProfileId)) {
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.STALE,
                    playerData.nexusRevision()
            );
        }

        if (AscendanceTierRegistry.get(playerData.getTierId()).isEmpty()) {
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.CONFIGURATION_ERROR,
                    playerData.nexusRevision()
            );
        }

        /* A complete proposal contains exactly one target for every live stat. */
        if (finalBonusTargets.size() != EssenceStatRegistry.size()) {
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.INCOMPLETE_BONUS_STATE,
                    playerData.nexusRevision()
            );
        }

        for (Map.Entry<ResourceLocation, Long> entry :
                finalBonusTargets.entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || entry.getValue() < 0L) {
                return Result.failure(
                        AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL,
                        playerData.nexusRevision()
                );
            }

            if (EssenceStatRegistry.get(entry.getKey()).isEmpty()) {
                return Result.failure(
                        AscendanceNexusTransactionResultPayload.Status.UNKNOWN_STAT,
                        playerData.nexusRevision()
                );
            }
        }

        Map<ResourceLocation, Long> targetInvestments =
                new LinkedHashMap<>(playerData.getAllInvested());
        Map<ResourceLocation, Long> targetAvailable =
                new LinkedHashMap<>(playerData.getAllAvailable());
        Map<ResourceLocation, SkillPurchase> targetOwnedSkills =
                new LinkedHashMap<>(playerData.getOwnedSkills());
        Map<ResourceLocation, ResourceLocation> targetLoadoutSelections =
                new LinkedHashMap<>(playerData.getLoadoutSelections());

        Map<ResourceLocation, Long> bonusSpending = new LinkedHashMap<>();
        Map<ResourceLocation, Long> bonusRefunds = new LinkedHashMap<>();
        Map<ResourceLocation, Long> skillSpending = new LinkedHashMap<>();
        Map<ResourceLocation, Long> skillRefunds = new LinkedHashMap<>();
        Map<ResourceLocation, Long> currentBonusByEssence = new LinkedHashMap<>();
        Map<ResourceLocation, Long> projectedBonusByEssence = new LinkedHashMap<>();

        try {
            for (StatDefinition stat : EssenceStatRegistry.values()) {
                Long boxedTarget = finalBonusTargets.get(stat.id());
                if (boxedTarget == null) {
                    return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.INCOMPLETE_BONUS_STATE,
                            playerData.nexusRevision()
                    );
                }

                long target = boxedTarget;
                long current = playerData.getInvested(stat);
                long cap = balanceProfile.getInvestmentCap(
                        playerData.getTier(),
                        stat
                );

                if (cap < 0L) {
                    return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.CONFIGURATION_ERROR,
                            playerData.nexusRevision()
                    );
                }

                /* Existing over-cap storage may remain or be reduced, never raised. */
                if (target > cap && target > current) {
                    return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.CAP_EXCEEDED,
                            playerData.nexusRevision()
                    );
                }

                putOrRemove(targetInvestments, stat.id(), target);

                if (target > current) {
                    mergeExact(
                            bonusSpending,
                            stat.essenceType().id(),
                            target - current
                    );
                } else if (target < current) {
                    mergeExact(
                            bonusRefunds,
                            stat.essenceType().id(),
                            current - target
                    );
                }

                /* Live skill thresholds use allocated Bonus, not effective cap. */
                mergeExact(
                        currentBonusByEssence,
                        stat.essenceType().id(),
                        current
                );
                mergeExact(
                        projectedBonusByEssence,
                        stat.essenceType().id(),
                        target
                );
            }

            Map<ResourceLocation, Integer> finalRanks = new LinkedHashMap<>(playerData.getSkillRanks());
            for (var request : requestedPurchases.entrySet()) {
                ResourceLocation skillId = request.getKey();
                Integer targetRank = request.getValue();
                SkillDefinition skill = skillId == null ? null : SkillRegistry.get(skillId).orElse(null);
                if (skill == null || targetRank == null || targetRank < 0) {
                    return Result.failure(AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL,
                            playerData.nexusRevision());
                }
                if (targetRank > skill.maximumRank()) {
                    return Result.failure(AscendanceNexusTransactionResultPayload.Status.SKILL_MAX_RANK,
                            playerData.nexusRevision());
                }
                int currentRank = playerData.skillRank(skillId);
                if (targetRank == currentRank) {
                    return Result.failure(AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL,
                            playerData.nexusRevision());
                }
                if (targetRank < currentRank) {
                    if (skill.rankPolicy().refundRule()
                            != com.mistaboom.essence_ascendance.skill.SkillRankPolicy.RefundRule.EXACT_PAID) {
                        return Result.failure(AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL,
                                playerData.nexusRevision());
                    }
                    SkillPurchase paid = playerData.getSkillPurchase(skillId).orElseThrow();
                    mergeExact(skillRefunds, paid.essenceId(), paid.refundAbove(targetRank));
                    if (targetRank == 0) targetOwnedSkills.remove(skillId);
                    else targetOwnedSkills.put(skillId, paid.retain(targetRank));
                }
                if (targetRank == 0) finalRanks.remove(skillId);
                else finalRanks.put(skillId, targetRank);
            }
            Set<ResourceLocation> finalOwnedIds = finalRanks.keySet();
            targetLoadoutSelections.entrySet().removeIf(entry -> !finalOwnedIds.contains(entry.getValue()));

            // A refund must retain every paid descendant's rank prerequisites. Refund the child first.
            for (var owned : finalRanks.entrySet()) {
                SkillDefinition skill = SkillRegistry.get(owned.getKey()).orElse(null);
                if (skill == null) continue;
                for (var prerequisite : skill.prerequisiteRanks(owned.getValue()).entrySet()) {
                    if (finalRanks.getOrDefault(prerequisite.getKey(), 0) < prerequisite.getValue()) {
                        return Result.failure(AscendanceNexusTransactionResultPayload.Status.SKILL_PREREQUISITE_REQUIRED,
                                playerData.nexusRevision());
                    }
                }
            }

            for (Map.Entry<ResourceLocation, Optional<ResourceLocation>> entry :
                    loadoutChanges.entrySet()) {
                ResourceLocation groupId = entry.getKey();
                Optional<ResourceLocation> selected = entry.getValue();
                if (groupId == null || selected == null) {
                    return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL,
                            playerData.nexusRevision()
                    );
                }
                ResourceLocation currentSelection =
                        playerData.getLoadoutSelections().get(groupId);
                boolean exactAuthoritativeNoOp = currentSelection != null
                        && selected.isPresent()
                        && selected.get().equals(currentSelection);
                if (exactAuthoritativeNoOp) {
                    continue;
                }
                if (currentSelection != null
                        && SkillRegistry.get(currentSelection).isEmpty()) {
                    return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.INVALID_LOADOUT,
                            playerData.nexusRevision()
                    );
                }

                SkillChoiceGroup group =
                        SkillRegistry.choiceGroup(groupId).orElse(null);
                if (group == null) {
                    SkillDefinition directControl =
                            SkillRegistry.get(groupId).orElse(null);

                    if (directControl == null) {
                        return Result.failure(
                                AscendanceNexusTransactionResultPayload.Status.INVALID_LOADOUT,
                                playerData.nexusRevision()
                        );
                    }

                    boolean supportedDirectControl =
                            directControl.activationPolicy()
                                    == SkillActivationPolicy.TOGGLE
                                    || directControl.activationPolicy()
                                    == SkillActivationPolicy.AUTOMATIC;
                    if (!supportedDirectControl
                            || (selected.isPresent()
                            && (!selected.get().equals(directControl.id())
                            || !finalOwnedIds.contains(directControl.id())))) {
                        return Result.failure(
                                AscendanceNexusTransactionResultPayload.Status.INVALID_LOADOUT,
                                playerData.nexusRevision()
                        );
                    }

                    if (selected.isPresent()) {
                        targetLoadoutSelections.put(groupId, directControl.id());
                    } else {
                        targetLoadoutSelections.remove(groupId);
                    }
                    continue;
                }

                if (selected.isEmpty()) {
                    if (!group.allowNoSelection()) {
                        return Result.failure(
                                AscendanceNexusTransactionResultPayload.Status.INVALID_LOADOUT,
                                playerData.nexusRevision()
                        );
                    }
                    targetLoadoutSelections.remove(groupId);
                    continue;
                }

                ResourceLocation selectedSkillId = selected.get();
                if (!group.memberIds().contains(selectedSkillId)
                        || !finalOwnedIds.contains(selectedSkillId)) {
                    return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.INVALID_LOADOUT,
                            playerData.nexusRevision()
                    );
                }

                targetLoadoutSelections.put(groupId, selectedSkillId);
            }

            if (!validKnownLoadoutSelections(
                    targetLoadoutSelections,
                    finalOwnedIds,
                    playerData.getLoadoutSelections()
            )) {
                return Result.failure(
                        AscendanceNexusTransactionResultPayload.Status.INVALID_LOADOUT,
                        playerData.nexusRevision()
                );
            }

            List<PermanentMilestoneService.Resolution> milestoneResolutions =
                    PermanentMilestoneService.resolveAll(
                            player,
                            SkillRegistry.referencedPermanentMilestoneIds()
                    );
            Set<ResourceLocation> completedMilestones =
                    PermanentMilestoneService.completedIds(milestoneResolutions);
            Map<ResourceLocation, PermanentMilestoneService.Resolution>
                    milestonesById = new LinkedHashMap<>();
            for (PermanentMilestoneService.Resolution resolution :
                    milestoneResolutions) {
                milestonesById.put(resolution.milestoneId(), resolution);
            }

            SkillEvaluationContext skillContext = new SkillEvaluationContext(
                    playerData.getTierId(),
                    playerData.getSkillRanks(),
                    finalRanks,
                    playerData.getLoadoutSelections(),
                    targetLoadoutSelections,
                    completedMilestones,
                    Set.of(),
                    currentBonusByEssence,
                    projectedBonusByEssence
            );

            List<SkillDefinition> purchaseOrder =
                    SkillRegistry.topologicalOrder(requestedPurchases.keySet());

            for (SkillDefinition skill : purchaseOrder) {
                int currentRank = playerData.skillRank(skill.id());
                int targetRank = requestedPurchases.get(skill.id());
                for (int rank = currentRank + 1; rank <= targetRank; rank++) {
                    for (var requirement : skill.requirements(rank)) {
                        if (!(requirement instanceof PermanentMilestoneRequirement milestone)) continue;
                        PermanentMilestoneService.Resolution resolution = milestonesById.get(milestone.milestoneId());
                        if (resolution == null || !resolution.resolvable()) {
                            return Result.failure(AscendanceNexusTransactionResultPayload.Status.CONFIGURATION_ERROR,
                                    playerData.nexusRevision());
                        }
                    }
                    SkillPurchaseEligibility eligibility = SkillStateEvaluator.evaluatePurchaseEligibility(skill, skillContext, rank);
                    if (!eligibility.tierSatisfied()) return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.SKILL_TIER_REQUIRED, playerData.nexusRevision());
                    if (!eligibility.prerequisitesSatisfied()) return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.SKILL_PREREQUISITE_REQUIRED, playerData.nexusRevision());
                    if (!eligibility.requirementsSatisfied()) return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.SKILL_REQUIREMENT_INCOMPLETE, playerData.nexusRevision());
                    EssenceDefinition essence = EssenceRegistry.get(skill.essenceId()).orElse(null);
                    if (essence == null) return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.CONFIGURATION_ERROR, playerData.nexusRevision());
                    long cost = skill.cost(balanceProfile, rank);
                    mergeExact(skillSpending, essence.id(), cost);
                    SkillPurchase previous = targetOwnedSkills.get(skill.id());
                    if (previous != null && !previous.essenceId().equals(essence.id())) {
                        return Result.failure(AscendanceNexusTransactionResultPayload.Status.CONFIGURATION_ERROR,
                                playerData.nexusRevision());
                    }
                    targetOwnedSkills.put(skill.id(), previous == null
                            ? new SkillPurchase(essence.id(), cost) : previous.append(cost));
                }
            }

            for (EssenceDefinition essence : EssenceRegistry.values()) {
                long budget = Math.addExact(
                        playerData.getAvailable(essence),
                        Math.addExact(bonusRefunds.getOrDefault(essence.id(), 0L),
                                skillRefunds.getOrDefault(essence.id(), 0L))
                );
                long totalSpending = Math.addExact(
                        bonusSpending.getOrDefault(essence.id(), 0L),
                        skillSpending.getOrDefault(essence.id(), 0L)
                );

                if (totalSpending > budget) {
                    return Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.INSUFFICIENT_ESSENCE,
                            playerData.nexusRevision()
                    );
                }

                putOrRemove(
                        targetAvailable,
                        essence.id(),
                        budget - totalSpending
                );
            }
        } catch (ArithmeticException exception) {
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL,
                    playerData.nexusRevision()
            );
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.error(
                    "Could not validate Ascendance Nexus transaction for player {}",
                    player.getUUID(),
                    exception
            );
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.CONFIGURATION_ERROR,
                    playerData.nexusRevision()
            );
        }

        ResourceLocation targetTierId = playerData.getTierId();
        if (ascend) {
            AscendanceEvaluationResult evaluation;
            try {
                evaluation = AscendanceEngine.evaluateProjected(
                        player,
                        targetAvailable,
                        targetInvestments,
                        targetOwnedSkills
                );
            } catch (RuntimeException exception) {
                EssenceAscendance.LOGGER.error(
                        "Could not evaluate projected Ascension for player {}",
                        player.getUUID(),
                        exception
                );
                return Result.failure(
                        AscendanceNexusTransactionResultPayload.Status.CONFIGURATION_ERROR,
                        playerData.nexusRevision()
                );
            }

            if (evaluation.status()
                    == AscendanceEvaluationResult.Status.MAX_TIER) {
                return Result.failure(
                        AscendanceNexusTransactionResultPayload.Status.MAX_TIER,
                        playerData.nexusRevision()
                );
            }
            if (evaluation.status()
                    == AscendanceEvaluationResult.Status.CONFIGURATION_ERROR) {
                return Result.failure(
                        AscendanceNexusTransactionResultPayload.Status.CONFIGURATION_ERROR,
                        playerData.nexusRevision()
                );
            }
            if (evaluation.progress() == null
                    || !evaluation.progress().readyToAscend()) {
                return Result.failure(
                        AscendanceNexusTransactionResultPayload.Status.ASCENSION_NOT_READY,
                        playerData.nexusRevision()
                );
            }

            targetTierId = evaluation.nextTier().id();
        }

        boolean hasChanges =
                !playerData.getAllInvested().equals(targetInvestments)
                        || !playerData.getAllAvailable().equals(targetAvailable)
                        || !playerData.getOwnedSkills().equals(targetOwnedSkills)
                        || !playerData.getLoadoutSelections().equals(targetLoadoutSelections)
                        || !playerData.getTierId().equals(targetTierId);

        if (!hasChanges) {
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL,
                    playerData.nexusRevision()
            );
        }

        /* Final stale/config check at the exact commit boundary. */
        if (playerData.nexusRevision() != baseNexusRevision
                || !playerData.getTierId().equals(baseTierId)
                || !EssenceConfigManager.get()
                .balanceProfile()
                .id()
                .equals(baseBalanceProfileId)) {
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.STALE,
                    playerData.nexusRevision()
            );
        }

        try {
            boolean committed = savedData.applyNexusTransaction(
                    player.getUUID(),
                    targetInvestments,
                    targetAvailable,
                    targetOwnedSkills,
                    targetLoadoutSelections,
                    targetTierId
            );

            if (!committed) {
                return Result.failure(
                        AscendanceNexusTransactionResultPayload.Status.TRANSACTION_FAILED,
                        playerData.nexusRevision()
                );
            }
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.error(
                    "Validated Ascendance Nexus transaction failed to commit for player {}",
                    player.getUUID(),
                    exception
            );
            return Result.failure(
                    AscendanceNexusTransactionResultPayload.Status.TRANSACTION_FAILED,
                    playerData.nexusRevision()
            );
        }

        if (ascend) com.mistaboom.essence_ascendance.attunement.AttunementGameplay.forget(player);
        return Result.success(
                playerData.nexusRevision(),
                ascend
        );
    }

    private static boolean validKnownLoadoutSelections(
            Map<ResourceLocation, ResourceLocation> selections,
            Set<ResourceLocation> finalOwnedIds,
            Map<ResourceLocation, ResourceLocation> authoritativeSelections
    ) {
        for (Map.Entry<ResourceLocation, ResourceLocation> entry :
                selections.entrySet()) {
            if (entry.getValue().equals(
                    authoritativeSelections.get(entry.getKey())
            )) {
                continue;
            }
            Optional<SkillChoiceGroup> knownGroup =
                    SkillRegistry.choiceGroup(entry.getKey());

            if (knownGroup.isEmpty()) {
                SkillDefinition directControl =
                        SkillRegistry.get(entry.getKey()).orElse(null);

                if (directControl == null) {
                    return false;
                }

                boolean supportedDirectControl =
                        directControl.activationPolicy()
                                == SkillActivationPolicy.TOGGLE
                                || directControl.activationPolicy()
                                == SkillActivationPolicy.AUTOMATIC;
                if (!supportedDirectControl
                        || !entry.getKey().equals(entry.getValue())
                        || !finalOwnedIds.contains(entry.getValue())) {
                    return false;
                }
                continue;
            }

            if (!knownGroup.get().memberIds().contains(entry.getValue())
                    || !finalOwnedIds.contains(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static void mergeExact(
            Map<ResourceLocation, Long> values,
            ResourceLocation id,
            long amount
    ) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Cannot merge a negative Essence amount");
        }
        values.put(
                id,
                Math.addExact(values.getOrDefault(id, 0L), amount)
        );
    }

    private static void putOrRemove(
            Map<ResourceLocation, Long> values,
            ResourceLocation id,
            long amount
    ) {
        if (amount == 0L) {
            values.remove(id);
        } else {
            values.put(id, amount);
        }
    }

    public record Result(
            AscendanceNexusTransactionResultPayload.Status status,
            long nexusRevision,
            boolean ascended
    ) {
        public static Result success(
                long nexusRevision,
                boolean ascended
        ) {
            return new Result(
                    AscendanceNexusTransactionResultPayload.Status.SUCCESS,
                    nexusRevision,
                    ascended
            );
        }

        public static Result failure(
                AscendanceNexusTransactionResultPayload.Status status,
                long nexusRevision
        ) {
            if (status == AscendanceNexusTransactionResultPayload.Status.SUCCESS) {
                throw new IllegalArgumentException("Failure result cannot use SUCCESS");
            }
            return new Result(status, nexusRevision, false);
        }

        public boolean accepted() {
            return status == AscendanceNexusTransactionResultPayload.Status.SUCCESS;
        }
    }
}
