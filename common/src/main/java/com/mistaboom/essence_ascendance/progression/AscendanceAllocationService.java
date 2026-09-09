package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Server-authoritative atomic Essence allocation/reallocation. */
public final class AscendanceAllocationService {

    private AscendanceAllocationService() {
    }

    public static Result apply(
            ServerPlayer player,
            long basePlayerRevision,
            ResourceLocation baseTierId,
            ResourceLocation baseBalanceProfileId,
            Map<StatDefinition, RequestedTarget> requestedTargets
    ) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(baseTierId, "Base tier ID cannot be null");
        Objects.requireNonNull(baseBalanceProfileId, "Base balance profile ID cannot be null");
        Objects.requireNonNull(requestedTargets, "Allocation targets cannot be null");

        EssenceSavedData savedData =
                EssenceSavedData.get(player.server);
        PlayerEssenceData playerData =
                savedData.getPlayerData(player.getUUID());

        if (basePlayerRevision < 0L
                || playerData.revision() < basePlayerRevision) {
            return Result.stale();
        }

        /*
         * The normal player revision also changes when AVAILABLE Essence changes.
         * Crucible channeling is therefore allowed to advance it while the Nexus
         * is open. Allocation conflicts are detected from the progression state
         * that actually matters: tier/profile plus each touched stat's stored
         * investment.
         */
        if (!playerData.getTierId().equals(baseTierId)
                || !EssenceConfigManager.get()
                .balanceProfile()
                .id()
                .equals(baseBalanceProfileId)) {
            return Result.stale();
        }

        if (requestedTargets.isEmpty()) {
            return Result.noChanges();
        }

        Map<EssenceDefinition, Long> spending =
                new LinkedHashMap<>();
        Map<EssenceDefinition, Long> refunds =
                new LinkedHashMap<>();
        Map<StatDefinition, Long> changedTargets =
                new LinkedHashMap<>();

        try {
            for (Map.Entry<StatDefinition, RequestedTarget> entry :
                    requestedTargets.entrySet()) {
                StatDefinition stat =
                        Objects.requireNonNull(
                                entry.getKey(),
                                "Allocation stat cannot be null"
                        );
                RequestedTarget requested =
                        Objects.requireNonNull(
                                entry.getValue(),
                                "Allocation target cannot be null"
                        );
                long baseInvestment = requested.baseInvestment();
                long target = requested.targetInvestment();

                if (baseInvestment < 0L) {
                    return Result.invalid(
                            "Base allocation targets cannot be negative."
                    );
                }

                long current =
                        playerData.getInvested(stat);

                if (current != baseInvestment) {
                    return Result.stale();
                }

                if (target < 0L) {
                    return Result.invalid(
                            "Allocation targets cannot be negative."
                    );
                }

                StatInvestmentLimit limit =
                        TierInvestmentPolicy.evaluate(
                                playerData,
                                stat
                        );
                long cap = limit.investmentCap();

                if (target > cap) {
                    return Result.invalid(
                            "A staged allocation exceeds the current tier cap."
                    );
                }

                if (target == current) {
                    continue;
                }

                changedTargets.put(stat, target);

                EssenceDefinition essence = stat.essenceType();

                if (target > current) {
                    long amount = target - current;
                    spending.merge(
                            essence,
                            amount,
                            Math::addExact
                    );
                } else {
                    long amount = current - target;
                    refunds.merge(
                            essence,
                            amount,
                            Math::addExact
                    );
                }
            }
        } catch (ArithmeticException exception) {
            return Result.invalid(
                    "Allocation values overflowed the supported Essence range."
            );
        }

        if (changedTargets.isEmpty()) {
            return Result.noChanges();
        }

        Map<EssenceDefinition, Long> finalAvailable =
                new LinkedHashMap<>();

        java.util.LinkedHashSet<EssenceDefinition> affectedEssences =
                new java.util.LinkedHashSet<>();
        affectedEssences.addAll(spending.keySet());
        affectedEssences.addAll(refunds.keySet());

        try {
            for (EssenceDefinition essence : affectedEssences) {
                long available =
                        playerData.getAvailable(essence);
                long refunded =
                        refunds.getOrDefault(essence, 0L);
                long spent =
                        spending.getOrDefault(essence, 0L);

                long budget =
                        Math.addExact(
                                available,
                                refunded
                        );

                if (spent > budget) {
                    return Result.invalid(
                            "Not enough "
                                    + essence.displayName()
                                    + " is available for this allocation."
                    );
                }

                finalAvailable.put(
                        essence,
                        budget - spent
                );
            }
        } catch (ArithmeticException exception) {
            return Result.invalid(
                    "Refunding this allocation would overflow the supported Essence range."
            );
        }

        boolean changed =
                savedData.applyAllocationTargets(
                        player.getUUID(),
                        changedTargets,
                        finalAvailable
                );

        return changed
                ? Result.success()
                : Result.noChanges();
    }

    public record RequestedTarget(
            long baseInvestment,
            long targetInvestment
    ) {
    }

    public enum Status {
        SUCCESS,
        NO_CHANGES,
        STALE,
        INVALID
    }

    public record Result(
            Status status,
            String message
    ) {
        private static Result success() {
            return new Result(
                    Status.SUCCESS,
                    ""
            );
        }

        private static Result noChanges() {
            return new Result(
                    Status.NO_CHANGES,
                    "Nothing changed in the staged allocation."
            );
        }

        private static Result stale() {
            return new Result(
                    Status.STALE,
                    "Your progression changed while the Nexus was open. Review the refreshed allocation and try again."
            );
        }

        private static Result invalid(String message) {
            return new Result(
                    Status.INVALID,
                    message
            );
        }
    }
}
