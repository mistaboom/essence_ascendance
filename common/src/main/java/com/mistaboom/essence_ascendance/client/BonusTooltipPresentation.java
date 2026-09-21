package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.nexus.NexusProgressionTrack;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Bonus descriptions use actual continuous benefits and the generated tier checkpoints. */
public final class BonusTooltipPresentation {
    private BonusTooltipPresentation() { }
    public static String descriptionKey(StatDefinition stat) {
        return "stat.essence_ascendance." + stat.id().getPath() + ".description";
    }
    public static Component description(StatDefinition stat, double value) {
        return Component.translatable(descriptionKey(stat), SemanticTooltip.value(number(value)));
    }
    public static String number(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    public static String number(long value) { return Long.toString(value); }
    public static SemanticTooltip tooltip(NexusProgressionTrack track, ResourceLocation tier, long target) {
        var state = track.state();
        var resolved = state.track();
        boolean preview = target != state.storedInvestment();
        double value = preview ? state.transcendentMaximumBonus() * track.progression(target, tier) : state.scaledBonus();
        var tooltip = new SemanticTooltip().title(EssenceText.stat(track.stat()), AscendancePalette.categoryRgb(track.stat().essenceType().id()));
        if (preview) tooltip.hint(EssenceText.gui("nexus.track.staged_state"));
        tooltip.description(description(track.stat(), value));
        tooltip.field(EssenceText.gui("nexus.track.invested", number(preview ? target : state.storedInvestment())));
        if (preview) tooltip.field(EssenceText.gui("nexus.track.allocation_cost", number(target - state.storedInvestment())));
        var next = resolved.checkpoints().stream().filter(p -> p.purchasable() && p.cumulativeCap() > target).findFirst();
        next.ifPresent(p -> tooltip.field(EssenceText.gui(resolved.purchaseStyle().continuousBenefits()
                        ? "nexus.track.next_checkpoint" : "nexus.track.next_state",
                number(p.cumulativeCap() - target), tierName(p.tierId()))));
        tooltip.section(EssenceText.gui("nexus.track.availability"));
        for (var p : resolved.checkpoints()) {
            if (!AscendanceTierRegistry.get(p.tierId()).map(t -> t.grantsPower()).orElse(false)) continue;
            Component effect = p.available()
                    ? Component.literal(number(state.transcendentMaximumBonus() * p.effectFraction()) + (resolved.unit() == StatUnit.PERCENT ? "%" : ""))
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
