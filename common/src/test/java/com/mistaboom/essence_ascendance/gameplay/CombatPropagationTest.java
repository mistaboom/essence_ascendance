package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.skill.effect.PropagationBudget;
import java.util.ArrayList;
import java.util.UUID;

/** Causal observation views must never reset or change the existing damage propagation budget. */
public final class CombatPropagationTest {
    private static int checks;
    public static void main(String[] args) {
        UUID actor = id(1), seed = id(2), first = id(3), second = id(4), third = id(5), overflow = id(6);
        var accepted = new CombatOutcome.Cause("survival-action", actor, CombatOutcome.Weapon.UNKNOWN, true);
        var rejected = new CombatOutcome.Cause("rejected-prerequisite", actor, CombatOutcome.Weapon.UNKNOWN, false);
        var root = new PropagationBudget(2, 3, accepted);
        var branch = root.withCombatCause(rejected);
        var unknown = branch.withCombatCause(null);
        check(root != branch && branch != unknown, "Different observation origins have separate views");
        check(root.withCombatCause(accepted) == root, "Same immutable origin needs no additional view");
        check(root.combatCause() == accepted && branch.combatCause() == rejected && unknown.combatCause() == null,
                "Rejected and missing origins do not poison eligible sibling provenance");

        root.seed(seed);
        check(branch.visited(seed) && unknown.visited(seed), "Root seed is shared across every view");
        check(!branch.tryVisit(seed, 0) && root.affectedTargets() == 0, "Seed deduplication never spends another target");
        check(branch.tryVisit(first, 0), "Rejected-credit branch retains ordinary damage traversal");
        check(root.visited(first) && unknown.visited(first) && root.affectedTargets() == 1,
                "Branch traversal spends the shared root budget");
        check(!root.tryVisit(first, 1) && unknown.affectedTargets() == 1,
                "Cross-view repeated victims remain deduplicated");
        check(!unknown.tryVisit(second, -1) && !unknown.tryVisit(second, 3),
                "Views preserve the existing generation boundaries");
        check(!root.visited(second) && branch.affectedTargets() == 1,
                "Rejected generation never mutates shared traversal");
        check(root.tryVisit(second, 2) && branch.affectedTargets() == 2,
                "Root traversal is equally visible to an already created branch");
        check(unknown.tryVisit(third, 1), "Unknown-credit branch can spend the final gameplay target");
        check(root.affectedTargets() == 3 && branch.affectedTargets() == 3 && unknown.affectedTargets() == 3,
                "Every view shares the exact exhausted count");
        check(!root.canContinue(0) && !branch.canContinue(1) && !unknown.canContinue(2),
                "No view can continue after the original cap");
        check(!root.tryVisit(overflow, 0) && !branch.tryVisit(overflow, 0) && !unknown.tryVisit(overflow, 0),
                "A fresh provenance view cannot reset the original target cap");
        check(new ArrayList<>(branch.visitedIds()).equals(java.util.List.of(seed, first, second, third)),
                "Shared visitation keeps deterministic insertion order");
        try { branch.visitedIds().add(overflow); throw new AssertionError("Mutable visited set exposed"); }
        catch (UnsupportedOperationException expected) { checks++; }
        var independent = new PropagationBudget(2, 3, accepted);
        check(independent.tryVisit(first, 0) && independent.affectedTargets() == 1,
                "Independent roots keep independent budgets even with equal causes");
        check(root.combatCause().eligibleOrigin() && !branch.combatCause().eligibleOrigin(),
                "Traversal cannot upgrade rejected provenance or downgrade a legitimate sibling");
        System.out.println("Combat propagation invariants passed: " + checks);
    }
    private static UUID id(long n) { return new UUID(0, n); }
    private static void check(boolean valid, String message) { checks++; if (!valid) throw new AssertionError(message); }
}
