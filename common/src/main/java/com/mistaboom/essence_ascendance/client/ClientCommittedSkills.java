package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Cached common skill evaluation of the exact synchronized committed snapshot, independent of any screen/draft. */
public final class ClientCommittedSkills {
    private static ClientEssenceState.Snapshot previous;
    private static Set<ResourceLocation> effective = Set.of();
    private ClientCommittedSkills() { }
    public static boolean isEffective(ResourceLocation skill) {
        var snapshot = ClientEssenceState.snapshot();
        if (snapshot != previous) {
            previous = snapshot; effective = Set.of();
            if (snapshot.ready() && snapshot.tierId() != null) {
                Map<ResourceLocation, Integer> ranks = new LinkedHashMap<>();
                snapshot.ownedSkills().forEach((id, receipt) -> ranks.put(id, receipt.rank()));
                Map<ResourceLocation, Long> totals = new LinkedHashMap<>();
                for (var stat : EssenceStatRegistry.values()) {
                    var saved = snapshot.stats().get(stat.id());
                    long amount = saved == null ? 0 : Math.max(0, saved.storedInvestment());
                    totals.merge(stat.essenceType().id(), amount, (a, b) -> b > Long.MAX_VALUE - a ? Long.MAX_VALUE : a + b);
                }
                var context = SkillEvaluationContext.committed(snapshot.tierId(), ranks, snapshot.loadoutSelections(),
                        snapshot.completedMilestones(), Set.of(), totals);
                Set<ResourceLocation> active = new LinkedHashSet<>();
                SkillStateEvaluator.evaluateAll(context).forEach((id, result) -> { if (result.effective()) active.add(id); });
                effective = Set.copyOf(active);
            }
        }
        return effective.contains(skill);
    }
}
