package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationResult;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** One hovered-skill cache, invalidated by server profile, effective ranks or staged selection changes.
 * Client text has no authority: gameplay remains server-owned and uses the same rank resolver. */
public final class SkillTooltipPresentation {
    private RuntimeBalanceDefinition cachedProfile;
    private ResourceLocation cachedSkill;
    private Map<ResourceLocation, Integer> cachedCurrent = Map.of(), cachedProjected = Map.of();
    private List<Component> current = List.of(), projected = List.of();

    public void append(SemanticTooltip tooltip, SkillDefinition skill, Map<ResourceLocation, SkillEvaluationResult> evaluations) {
        SkillEvaluationResult evaluation = evaluations.get(skill.id());
        var definition = SkillTooltipRegistry.lines(skill.id());
        if (definition.isEmpty() || evaluation == null) {
            fallback(tooltip, skill);
            tooltip.hint(EssenceText.gui("nexus.skills.tooltip." + (SkillEffectRegistry.isImplemented(skill.id())
                    ? "generated_waiting" : "generated_planned")));
            return;
        }
        // Do not display bootstrap defaults as though the server generated them.
        RuntimeBalanceDefinition profile = EssenceConfigManager.clientRuntime();
        if (profile == null) profile = EssenceConfigManager.serverRuntime();
        if (profile == null) {
            fallback(tooltip, skill);
            tooltip.hint(EssenceText.gui("nexus.skills.tooltip.generated_waiting"));
            return;
        }
        int rank = Math.max(1, evaluation.currentRank());
        int nextRank = Math.max(rank, evaluation.projectedRank());
        var actualRanks = ranks(evaluations, false);
        var previewRanks = ranks(evaluations, true);
        actualRanks.put(skill.id(), rank); // disabled/unowned skills explicitly show their when-enabled values
        previewRanks.put(skill.id(), nextRank);
        if (profile != cachedProfile || !skill.id().equals(cachedSkill)
                || !actualRanks.equals(cachedCurrent) || !previewRanks.equals(cachedProjected)) {
            current = resolve(profile, actualRanks, definition);
            projected = resolve(profile, previewRanks, definition);
            cachedProfile = profile; cachedSkill = skill.id();
            cachedCurrent = Map.copyOf(actualRanks); cachedProjected = Map.copyOf(previewRanks);
        }
        tooltip.field(EssenceText.gui("nexus.skills.tooltip." + (evaluation.effective()
                ? "generated_current" : "generated_preview"), SemanticTooltip.value(rank)));
        tooltip.description(current.getFirst());
        current.stream().skip(1).forEach(tooltip::detail);
        if (evaluation.currentRank() > 0 && nextRank > rank) {
            tooltip.section(EssenceText.gui("nexus.skills.tooltip.generated_after", SemanticTooltip.value(nextRank)));
            tooltip.description(projected.getFirst());
            projected.stream().skip(1).forEach(tooltip::detail);
        }
    }
    private static TreeMap<ResourceLocation, Integer> ranks(Map<ResourceLocation, SkillEvaluationResult> evaluations, boolean projected) {
        var result = new TreeMap<ResourceLocation, Integer>();
        evaluations.forEach((id, state) -> {
            if (projected ? state.projectedEffective() : state.effective())
                result.put(id, projected ? state.projectedRank() : state.currentRank());
        });
        return result;
    }
    private static List<Component> resolve(RuntimeBalanceDefinition profile, Map<ResourceLocation, Integer> ranks,
                                           List<SkillTooltipRegistry.Line> definition) {
        var resolved = SkillRankEffectScaling.applyResolved(profile.config().skillEffects(), ranks, profile.skillCurves());
        return definition.stream().map(line -> line.render(resolved)).toList();
    }
    private static void fallback(SemanticTooltip tooltip, SkillDefinition skill) {
        tooltip.description(Component.translatable(skill.descriptionTranslationKey()));
        String key = skill.descriptionTranslationKey() + ".details";
        if (Language.getInstance().has(key)) tooltip.detail(Component.translatable(key));
    }
}
