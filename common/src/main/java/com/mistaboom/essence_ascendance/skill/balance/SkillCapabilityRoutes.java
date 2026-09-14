package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Explicit mechanical contracts for catalog capabilities. A declaration does not enable a skill effect. */
public final class SkillCapabilityRoutes {
    private SkillCapabilityRoutes() {}
    // These skills are explicitly designed to use Abilities#flyingSpeed. Their FLIGHT axis alone
    // is insufficient proof of compatibility; keep this contract independent of display names.
    private static final Set<ResourceLocation> STANDARD_FLIGHT = Set.of(SkillIds.FATIGUE_FLIGHT, SkillIds.UNTETHERED_FLIGHT);
    public static List<CapabilityEvidence> declaredRoutes() {
        var powered = AscendanceTierRegistry.values().stream().filter(AscendanceTierDefinition::grantsPower)
                .sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        List<CapabilityEvidence> result = new ArrayList<>();
        for (var skill : SkillRegistry.values()) if (STANDARD_FLIGHT.contains(skill.id())) {
            var semantics = SkillBalanceSemantics.require(skill.id());
            int index = requiredIndex(skill, powered, new HashSet<>());
            if (index >= powered.size()) continue;
            result.add(new CapabilityEvidence(skill.id().toString(), ProgressionBand.at(index),
                    Map.of(CapabilityAxis.FLIGHT, 1.0, CapabilityAxis.ABILITIES_FLYING_SPEED, 1.0), true,
                    semantics.implemented() ? 1 : .6,
                    (semantics.implemented() ? "IMPLEMENTED" : "DECLARED_NOT_IMPLEMENTED")
                            + ": explicit standard-flight Abilities#flyingSpeed contract; catalog required tier and prerequisites apply; "
                            + "this declaration reserves profile progression and does not grant flight"));
        }
        return List.copyOf(result);
    }
    private static int requiredIndex(SkillDefinition skill, List<AscendanceTierDefinition> tiers, Set<ResourceLocation> visited) {
        if (!visited.add(skill.id())) return 0;
        int index = 0;
        while (index < tiers.size() && !tiers.get(index).id().equals(skill.requiredTierId())) index++;
        for (var prerequisite : skill.prerequisites())
            index = Math.max(index, requiredIndex(SkillRegistry.require(prerequisite), tiers, visited));
        return index;
    }
}
