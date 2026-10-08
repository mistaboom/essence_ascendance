package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.requirement.*;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Every saved native rank/track through the actual shared gates, settlement and
 * atomic player-state/NBT APIs. No fake acquisition or network/player simulation. */
final class NativeProfileStateAudit {
    private static int checks;
    static int verify(RuntimeBalanceDefinition runtime) {
        checks = 0;
        SkillBalanceRuntime.withCurves(runtime.skillCurves(), () -> {
            skills(runtime); bonuses(runtime); return null;
        });
        return checks;
    }
    private static void skills(RuntimeBalanceDefinition runtime) {
        for (var skill : SkillRegistry.values()) {
            var paid = new ArrayList<Long>();
            long previousCost = 0;
            for (int rank = 1; rank <= skill.maximumRank(); rank++) {
                var tier = AscendanceTierRegistry.get(skill.requiredTierId(rank)).orElseThrow();
                var parents = new TreeMap<>(skill.prerequisiteRanks(rank));
                for (var parent : parents.entrySet()) {
                    check(parent.getValue() <= SkillRegistry.require(parent.getKey()).maximumRank(), "Impossible parent rank " + skill.id());
                    check(AscendanceTierRegistry.get(SkillRegistry.require(parent.getKey()).requiredTierId(parent.getValue())).orElseThrow().order()
                            < tier.order(), "Prerequisite tier is not strictly earlier " + skill.id());
                }
                var milestones = new HashSet<ResourceLocation>();
                var discoveries = new HashSet<ResourceLocation>();
                var bonus = new TreeMap<ResourceLocation, Long>();
                for (var requirement : skill.requirements(rank)) {
                    if (requirement instanceof PermanentMilestoneRequirement m) milestones.add(m.milestoneId());
                    else if (requirement instanceof BonusInvestmentRequirement b) bonus.merge(b.essenceId(), b.minimumInvestment(), Math::max);
                    else if (requirement instanceof DiscoveryRequirement d) discoveries.add(d.discoveryId());
                    else throw new AssertionError("Unaudited requirement " + requirement);
                }
                var context = SkillEvaluationContext.committed(tier.id(), parents, Map.of(), milestones, discoveries, bonus);
                check(SkillStateEvaluator.evaluatePurchaseEligibility(skill, context, rank).satisfied(), "Complete purchase gates rejected " + skill.id() + "/" + rank);
                for (var earlier : AscendanceTierRegistry.values()) if (earlier.order() < tier.order())
                    check(!SkillStateEvaluator.evaluatePurchaseEligibility(skill, SkillEvaluationContext.committed(earlier.id(), parents,
                            Map.of(), milestones, discoveries, bonus), rank).satisfied(), "Early tier bypass " + skill.id());
                for (var parent : parents.keySet()) {
                    var missing = new TreeMap<>(parents); int needed = missing.remove(parent);
                    if (needed > 1) missing.put(parent, needed - 1);
                    check(!SkillStateEvaluator.evaluatePurchaseEligibility(skill, SkillEvaluationContext.committed(tier.id(), missing,
                            Map.of(), milestones, discoveries, bonus), rank).satisfied(), "Parent rank bypass " + skill.id());
                }
                for (var milestone : milestones) {
                    var missing = new HashSet<>(milestones); missing.remove(milestone);
                    check(!SkillStateEvaluator.evaluatePurchaseEligibility(skill, SkillEvaluationContext.committed(tier.id(), parents,
                            Map.of(), missing, discoveries, bonus), rank).satisfied(), "Milestone bypass " + skill.id());
                }
                for (var discovery : discoveries) {
                    var missing = new HashSet<>(discoveries); missing.remove(discovery);
                    check(!SkillStateEvaluator.evaluatePurchaseEligibility(skill, SkillEvaluationContext.committed(tier.id(), parents,
                            Map.of(), milestones, missing, bonus), rank).satisfied(), "Discovery gate bypass " + skill.id());
                }
                for (var b : bonus.entrySet()) {
                    long capacity = EssenceStatRegistry.values().stream().filter(s -> s.essenceType().id().equals(b.getKey()))
                            .mapToLong(s -> runtime.config().balanceProfile().getInvestmentCap(tier, s)).reduce(0, Math::addExact);
                    check(capacity >= b.getValue(), "Bonus gate cannot be funded at skill tier " + skill.id());
                    if (b.getValue() > 0) {
                        var shortfall = new TreeMap<>(bonus); shortfall.put(b.getKey(), b.getValue() - 1);
                        check(!SkillStateEvaluator.evaluatePurchaseEligibility(skill, SkillEvaluationContext.committed(tier.id(), parents,
                                Map.of(), milestones, discoveries, shortfall), rank).satisfied(), "Bonus gate bypass " + skill.id());
                    }
                }
                long cost = skill.cost(runtime.config().balanceProfile(), rank);
                // Native meaningful-state pricing may price adjacent equal-sized improvements equally.
                check(cost > 0 && cost >= previousCost, "Nonpositive/decreasing rank cost " + skill.id()); previousCost = cost; paid.add(cost);
                var receipt = new SkillPurchase(skill.essenceId(), paid);
                var state = new PlayerEssenceData();
                var previousSkills = rank == 1 ? Map.<ResourceLocation, SkillPurchase>of()
                        : Map.of(skill.id(), receipt.retain(rank - 1));
                state.applyNexusTransaction(Map.of(), Map.of(skill.essenceId(), cost), previousSkills, Map.of(), tier.id());
                var zeroTargets = new TreeMap<ResourceLocation, Long>();
                EssenceStatRegistry.values().forEach(stat -> zeroTargets.put(stat.id(), 0L));
                var plan = BonusTransactionPlan.resolve(state, runtime.config().balanceProfile(), zeroTargets);
                check(plan.accepted(), "Native skill settlement baseline rejected");
                var spending = Map.of(skill.essenceId(), cost);
                var beforeFunding = state.save().copy();
                check(!plan.plan().settle(Map.of(skill.essenceId(), cost - 1), Map.of(), spending).accepted()
                        && beforeFunding.equals(state.save()), "Underfunded skill rank accepted or mutated state");
                var settlement = plan.plan().settle(state.getAllAvailable(), Map.of(), spending);
                check(settlement.accepted() && settlement.balances().isEmpty(), "Exact native skill funding failed");
                long revision = state.nexusRevision();
                check(state.applyNexusTransaction(Map.of(), settlement.balances(), Map.of(skill.id(), receipt), Map.of(), tier.id()), "Atomic rank commit rejected");
                var restored = PlayerEssenceData.load(state.save());
                check(state.nexusRevision() == revision + 1 && restored.nexusRevision() == state.nexusRevision()
                        && restored.getOwnedSkills().equals(state.getOwnedSkills()) && restored.skillRank(skill.id()) == rank, "Rank state/revision/NBT mismatch");
                check(receipt.refundAbove(rank - 1) == cost, "Last-rank refund does not match actual receipt");
                var refund = plan.plan().settle(restored.getAllAvailable(), Map.of(skill.essenceId(), receipt.refundAbove(rank - 1)), Map.of());
                check(refund.accepted() && refund.balances().equals(Map.of(skill.essenceId(), cost)), "Native skill refund failed conservation");
                check(!restored.applyNexusTransaction(Map.of(), Map.of(), restored.getOwnedSkills(), Map.of(), tier.id())
                        && restored.nexusRevision() == state.nexusRevision(), "Idempotent rank replay changes revision");
                var beforeInvalid = restored.save().copy(); var rewritten = new ArrayList<>(paid);
                rewritten.set(0, Math.addExact(rewritten.getFirst(), 1));
                try {
                    restored.applyNexusTransaction(Map.of(), Map.of(), Map.of(skill.id(), new SkillPurchase(skill.essenceId(), rewritten)), Map.of(), tier.id());
                    throw new AssertionError("Historical rank receipt rewrite accepted");
                } catch (IllegalArgumentException expected) {
                    check(beforeInvalid.equals(restored.save()), "Rejected rank mutation partially changed state");
                }
            }
        }
    }
    private static void bonuses(RuntimeBalanceDefinition runtime) {
        var profile = runtime.config().balanceProfile();
        for (var tier : AscendanceTierRegistry.values()) {
            var targets = new TreeMap<ResourceLocation, Long>();
            var budgets = new TreeMap<ResourceLocation, Long>();
            for (var stat : EssenceStatRegistry.values()) {
                long cap = profile.getInvestmentCap(tier, stat); targets.put(stat.id(), cap);
                budgets.merge(stat.essenceType().id(), cap, Math::addExact);
                double previous = -1;
                for (int sample = 0; sample <= 64; sample++) {
                    long amount = cap / 64 * sample + (cap % 64) * sample / 64;
                    double effect = StatScalingService.realizedProgressionForInvestment(stat, amount, tier, profile);
                    check(Double.isFinite(effect) && effect >= previous && effect >= 0 && effect <= 1, "Invalid Bonus curve " + stat.id());
                    previous = effect;
                }
            }
            var state = new PlayerEssenceData();
            state.applyNexusTransaction(Map.of(), budgets, Map.of(), Map.of(), tier.id());
            var before = state.save().copy(); long revision = state.nexusRevision();
            var plan = BonusTransactionPlan.resolve(state, profile, targets);
            check(plan.accepted(), "Complete native Bonus target rejected " + tier.id());
            var settlement = plan.plan().settle(budgets, Map.of(), Map.of());
            check(settlement.accepted() && settlement.balances().values().stream().allMatch(v -> v == 0), "Exact Bonus funding failed");
            if (budgets.values().stream().anyMatch(v -> v > 0)) {
                var insufficient = new TreeMap<>(budgets); var id = insufficient.entrySet().stream().filter(e -> e.getValue() > 0).findFirst().orElseThrow().getKey();
                insufficient.put(id, insufficient.get(id) - 1);
                check(!plan.plan().settle(insufficient, Map.of(), Map.of()).accepted() && before.equals(state.save()), "Rejected settlement mutates state");
            }
            boolean changed = state.applyNexusTransaction(plan.plan().investments(), settlement.balances(), Map.of(), Map.of(), tier.id());
            check(state.nexusRevision() == revision + (changed ? 1 : 0), "Bonus atomic revision mismatch");
            var restored = PlayerEssenceData.load(state.save());
            check(restored.save().equals(state.save()), "Bonus NBT changed committed state");
            var zero = new TreeMap<ResourceLocation, Long>(); targets.keySet().forEach(k -> zero.put(k, 0L));
            var refund = BonusTransactionPlan.resolve(restored, profile, zero);
            check(refund.accepted() && refund.plan().settle(Map.of(), Map.of(), Map.of()).balances().equals(
                    budgets.entrySet().stream().filter(e -> e.getValue() > 0).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))), "Bonus refund does not conserve paid Essence");
        }
    }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
}
