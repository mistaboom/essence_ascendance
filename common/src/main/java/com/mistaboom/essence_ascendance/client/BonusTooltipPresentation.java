package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.nexus.NexusProgressionTrack;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Bonus descriptions use actual continuous benefits and the generated tier checkpoints. */
public final class BonusTooltipPresentation {
    private BonusTooltipPresentation() { }
    public static String descriptionKey(StatDefinition stat) {
        return BonusPresentationData.descriptionKey(stat);
    }
    public static Component description(StatDefinition stat, double value) {
        return BonusPresentationData.description(stat, value);
    }
    public static String number(double value) {
        return BonusPresentationData.number(value);
    }
    public static String number(long value) { return Long.toString(value); }
    public static SemanticTooltip tooltip(NexusProgressionTrack track, ResourceLocation tier, long target) {
        var state = track.state();
        var resolved = state.track();
        boolean preview = target != state.storedInvestment();
        var projection = BonusPresentationData.project(track.stat(), state, tier, target);
        double value = preview ? projection.effectValue() : state.scaledBonus();
        var tooltip = new SemanticTooltip().title(EssenceText.stat(track.stat()), AscendancePalette.categoryRgb(track.stat().essenceType().id()));
        if (preview) tooltip.hint(EssenceText.gui("nexus.track.staged_state"));
        tooltip.description(description(track.stat(), value));
        tooltip.field(EssenceText.gui("nexus.track.invested", number(preview ? target : state.storedInvestment())));
        if (preview) tooltip.field(EssenceText.gui("nexus.track.allocation_cost", number(target - state.storedInvestment())));
        var next = projection.nextChange();
        if (next != null) tooltip.field(EssenceText.gui(resolved.purchaseStyle().continuousBenefits()
                        ? "nexus.track.next_checkpoint" : "nexus.track.next_state",
                number(next.additionalCost()), tierName(next.tierId())));
        tooltip.section(EssenceText.gui("nexus.track.availability"));
        for (var p : projection.checkpoints()) {
            if (!AscendanceTierRegistry.get(p.tierId()).map(t -> t.grantsPower()).orElse(false)) continue;
            Component effect = p.available()
                    ? Component.literal(number(p.effectValue()) + (resolved.unit() == com.mistaboom.essence_ascendance.stat.StatUnit.PERCENT ? "%" : ""))
                    : EssenceText.gui("nexus.track.unavailable");
            tooltip.detail(Component.empty().append(tierName(p.tierId())).append("  ").append(effect));
        }
        if (resolved.available()) tooltip.field(EssenceText.gui("nexus.track.active_span", tierName(resolved.startTier()), tierName(resolved.completionTier())));
        return tooltip;
    }
    private static Component tierName(ResourceLocation id) {
        return Component.translatable("tier.essence_ascendance." + id.getPath()).withStyle(s -> s.withColor(AscendancePalette.tierMetalRgb(id)));
    }
}
