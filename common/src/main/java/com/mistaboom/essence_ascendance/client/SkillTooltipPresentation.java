package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationResult;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
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
        return current == 0 ? 1 : shift && current < maximum ? current + 1 : current;
    }
    public static boolean previewsNext(int current, int maximum, boolean shift) {
        return shift && current > 0 && current < maximum;
    }
    public static Component rankPurchaseHint(boolean shift) {
        return EssenceText.gui("nexus.skills.tooltip." + (shift ? "rank_purchase_preview" : "rank_purchase"));
    }
    /** The node is the sole price display; always resolve the next committed rank's real cost. */
    public static Component nodePurchaseLabel(SkillDefinition skill,
            com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition profile,
            int purchasedRank, java.util.function.LongFunction<String> format) {
        if (purchasedRank >= skill.maximumRank()) return EssenceText.gui("nexus.skills.node.max");
        if (profile == null) return Component.literal("—");
        return Component.literal(format.apply(skill.cost(profile, purchasedRank + 1)));
    }
    public void append(SemanticTooltip tooltip, SkillDefinition skill, Map<ResourceLocation, SkillEvaluationResult> evaluations) {
        append(tooltip, skill, evaluations, false);
    }
    public void append(SemanticTooltip tooltip, SkillDefinition skill,
                       Map<ResourceLocation, SkillEvaluationResult> evaluations, boolean shift) {
        var state = evaluations.get(skill.id());
        if (state == null) return;
        int rank = displayedRank(state.currentRank(), skill.maximumRank(), shift);
        tooltip.field(EssenceText.gui("nexus.skills.tooltip.display_rank",
                SemanticTooltip.value(rank), SemanticTooltip.value(skill.maximumRank())));
        RuntimeBalanceDefinition profile = EssenceConfigManager.clientRuntime();
        if (profile == null) profile = EssenceConfigManager.serverRuntime();
        if (profile == null) {
            tooltip.hint(EssenceText.gui("nexus.skills.tooltip.generated_waiting"));
            return;
        }
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
        if (state.currentRank() >= skill.maximumRank())
            tooltip.hint(EssenceText.gui("nexus.skills.tooltip.max_rank"));
    }
    public static List<Component> resolve(RuntimeBalanceDefinition profile, ResourceLocation skill,
                                          Map<ResourceLocation, Integer> ranks) {
        var resolved = SkillRankEffectScaling.applyResolved(profile.config().skillEffects(), ranks, profile.skillCurves());
        return SkillTooltipRegistry.lines(skill).stream().map(line -> line.render(resolved)).toList();
    }
}
