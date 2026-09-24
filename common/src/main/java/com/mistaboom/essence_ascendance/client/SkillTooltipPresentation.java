package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationResult;
import com.mistaboom.essence_ascendance.skill.SkillPrerequisiteStatus;
import com.mistaboom.essence_ascendance.skill.SkillRequirementStatus;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Read-only rank projection through the same native parameter resolver as gameplay. */
public final class SkillTooltipPresentation {
    private RuntimeBalanceDefinition cachedProfile;
    private ResourceLocation cachedSkill;
    private Map<ResourceLocation, Integer> cachedRanks = Map.of();
    private List<Component> cachedLines = List.of();

    public static int displayedRank(int current, int maximum, boolean shift) {
        int normal = Math.max(1, current);
        return previewsNext(current, maximum, shift) ? normal + 1 : normal;
    }
    public static boolean previewsNext(int current, int maximum, boolean shift) {
        return shift && Math.max(1, current) < maximum;
    }
    public static Component rankPurchaseHint(boolean shift) {
        return EssenceText.gui("nexus.skills.tooltip." + (shift ? "rank_purchase_preview" : "rank_purchase"));
    }
    /** Normal hover explains only outstanding gates, using the complete staged loadout. */
    public static List<SkillPrerequisiteStatus> unmetPrerequisites(SkillEvaluationResult state,
            Map<ResourceLocation, SkillEvaluationResult> evaluations, boolean shift) {
        if (shift) return List.of();
        return state.prerequisites().stream().filter(status -> {
            if (!status.projectedOwned()) return true;
            // A replacement deliberately suppresses its direct target; ownership is its actual gate.
            if (status.skillId().equals(state.definition().replacementTarget())) return false;
            var prerequisite = evaluations.get(status.skillId());
            return prerequisite == null || !prerequisite.projectedEffective();
        }).toList();
    }
    public static List<SkillRequirementStatus> unmetRequirements(SkillEvaluationResult state, boolean shift) {
        return shift ? List.of() : state.requirements().stream()
                .filter(status -> !status.projectedSatisfied()).toList();
    }
    /** The node is the sole price display; always resolve the next committed rank's real cost. */
    public static Component nodePurchaseLabel(SkillDefinition skill,
            com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition profile,
            int purchasedRank, java.util.function.LongFunction<String> format) {
        RuntimeBalanceDefinition runtime = EssenceConfigManager.clientRuntime();
        if (runtime == null) runtime = EssenceConfigManager.serverRuntime();
        if (profile == null || runtime == null
                || !runtime.config().balanceProfile().id().equals(profile.id())) return Component.literal("—");
        int maximum = SkillPresentationData.maximumRank(runtime, skill);
        if (maximum == 0) return Component.literal("—");
        if (purchasedRank >= maximum) return EssenceText.gui("nexus.skills.node.max");
        return Component.literal(format.apply(SkillPresentationData.rankCost(runtime, skill, purchasedRank + 1)));
    }
    public void append(SemanticTooltip tooltip, SkillDefinition skill, Map<ResourceLocation, SkillEvaluationResult> evaluations) {
        append(tooltip, skill, evaluations, false);
    }
    public void append(SemanticTooltip tooltip, SkillDefinition skill,
                       Map<ResourceLocation, SkillEvaluationResult> evaluations, boolean shift) {
        var state = evaluations.get(skill.id());
        if (state == null) return;
        RuntimeBalanceDefinition profile = EssenceConfigManager.clientRuntime();
        if (profile == null) profile = EssenceConfigManager.serverRuntime();
        int maximum = SkillPresentationData.maximumRank(profile, skill);
        if (profile == null || maximum == 0) {
            tooltip.hint(EssenceText.gui("nexus.skills.tooltip.generated_waiting"));
            return;
        }
        int rank = displayedRank(state.currentRank(), maximum, shift);
        tooltip.field(EssenceText.gui("nexus.skills.tooltip.display_rank",
                SemanticTooltip.value(rank), SemanticTooltip.value(maximum)));
        // A hover never projects unrelated staged purchases or selections.
        var ranks = new TreeMap<ResourceLocation, Integer>();
        evaluations.forEach((id, evaluation) -> {
            if (evaluation.effective()) ranks.put(id, evaluation.currentRank());
        });
        ranks.put(skill.id(), rank);
        if (profile != cachedProfile || !skill.id().equals(cachedSkill) || !ranks.equals(cachedRanks)) {
            cachedLines = resolve(profile, skill.id(), ranks);
            cachedProfile = profile; cachedSkill = skill.id(); cachedRanks = Map.copyOf(ranks);
        }
        // One shared paragraph treatment for every skill, including wrapped continuation lines.
        cachedLines.forEach(line -> tooltip.gap().detail(line));
        tooltip.gap();
        if (state.currentRank() >= maximum)
            tooltip.hint(EssenceText.gui("nexus.skills.tooltip.max_rank"));
    }
    public static List<Component> resolve(RuntimeBalanceDefinition profile, ResourceLocation skill,
                                          Map<ResourceLocation, Integer> ranks) {
        return SkillPresentationData.resolveEffects(profile, skill, ranks);
    }
}
