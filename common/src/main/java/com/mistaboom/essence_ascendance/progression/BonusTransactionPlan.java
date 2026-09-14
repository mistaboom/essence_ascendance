package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.network.AscendanceNexusTransactionResultPayload.Status;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure complete-target validation and exact accounting used by the server's single atomic Nexus commit. */
public final class BonusTransactionPlan {
    private BonusTransactionPlan() { }

    public static Result resolve(PlayerEssenceData player, BalanceProfileDefinition profile,
                                  Map<ResourceLocation, Long> targets) {
        if (targets.size() != EssenceStatRegistry.size()) return Result.failure(Status.INCOMPLETE_BONUS_STATE);
        for (var entry : targets.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)
                return Result.failure(Status.INVALID_PROPOSAL);
            if (EssenceStatRegistry.get(entry.getKey()).isEmpty()) return Result.failure(Status.UNKNOWN_STAT);
        }
        Map<ResourceLocation, Long> investments = new LinkedHashMap<>(player.getAllInvested());
        Map<ResourceLocation, Long> spending = new LinkedHashMap<>(), refunds = new LinkedHashMap<>();
        Map<ResourceLocation, Long> currentTotals = new LinkedHashMap<>(), targetTotals = new LinkedHashMap<>();
        try {
            for (var stat : EssenceStatRegistry.values()) {
                Long target = targets.get(stat.id());
                if (target == null) return Result.failure(Status.INCOMPLETE_BONUS_STATE);
                long current = player.getInvested(stat), cap = profile.getInvestmentCap(player.getTier(), stat);
                if (cap < 0) return Result.failure(Status.CONFIGURATION_ERROR);
                if (target > cap && target > current) return Result.failure(Status.CAP_EXCEEDED);
                if (!TierInvestmentPolicy.validTarget(stat, player.getTier(), profile, current, target))
                    return Result.failure(Status.INVALID_PROPOSAL);
                if (target == 0) investments.remove(stat.id()); else investments.put(stat.id(), target);
                if (target > current) add(spending, stat.essenceType().id(), target - current);
                else if (target < current) add(refunds, stat.essenceType().id(), current - target);
                // Existing live skill requirements intentionally retain allocated Essence semantics.
                add(currentTotals, stat.essenceType().id(), current);
                add(targetTotals, stat.essenceType().id(), target);
            }
        } catch (ArithmeticException overflow) {
            return Result.failure(Status.INVALID_PROPOSAL);
        }
        return new Result(Status.SUCCESS, new Plan(investments, spending, refunds, currentTotals, targetTotals));
    }

    public record Plan(Map<ResourceLocation, Long> investments, Map<ResourceLocation, Long> spending,
                       Map<ResourceLocation, Long> refunds, Map<ResourceLocation, Long> currentCategoryTotals,
                       Map<ResourceLocation, Long> targetCategoryTotals) {
        public Plan {
            investments = Map.copyOf(investments); spending = Map.copyOf(spending); refunds = Map.copyOf(refunds);
            currentCategoryTotals = Map.copyOf(currentCategoryTotals); targetCategoryTotals = Map.copyOf(targetCategoryTotals);
        }

        /** Skill refunds may fund Bonuses and Bonus refunds may fund skills in the same proposal. */
        public Settlement settle(Map<ResourceLocation, Long> available, Map<ResourceLocation, Long> skillRefunds,
                                 Map<ResourceLocation, Long> skillSpending) {
            Map<ResourceLocation, Long> balances = new LinkedHashMap<>(available);
            try {
                for (var essence : EssenceRegistry.values()) {
                    ResourceLocation id = essence.id();
                    long budget = Math.addExact(available.getOrDefault(id, 0L),
                            Math.addExact(refunds.getOrDefault(id, 0L), skillRefunds.getOrDefault(id, 0L)));
                    long cost = Math.addExact(spending.getOrDefault(id, 0L), skillSpending.getOrDefault(id, 0L));
                    if (budget < 0 || cost < 0) return new Settlement(Status.INVALID_PROPOSAL, Map.of());
                    if (cost > budget) return new Settlement(Status.INSUFFICIENT_ESSENCE, Map.of());
                    if (budget == cost) balances.remove(id); else balances.put(id, budget - cost);
                }
            } catch (ArithmeticException overflow) {
                return new Settlement(Status.INVALID_PROPOSAL, Map.of());
            }
            return new Settlement(Status.SUCCESS, balances);
        }
    }

    public record Result(Status status, Plan plan) {
        private static Result failure(Status status) { return new Result(status, null); }
        public boolean accepted() { return status == Status.SUCCESS; }
    }
    public record Settlement(Status status, Map<ResourceLocation, Long> balances) {
        public Settlement { balances = Map.copyOf(balances); }
        public boolean accepted() { return status == Status.SUCCESS; }
    }
    private static void add(Map<ResourceLocation, Long> amounts, ResourceLocation id, long amount) {
        amounts.put(id, Math.addExact(amounts.getOrDefault(id, 0L), amount));
    }
}
