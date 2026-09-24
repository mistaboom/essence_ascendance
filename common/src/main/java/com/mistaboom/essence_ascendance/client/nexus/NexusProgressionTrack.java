package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.progression.BonusTrackCurve;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Immutable data required to render one progression track. */
public record NexusProgressionTrack(
        StatDefinition stat,
        ClientEssenceState.StatSnapshot state,
        List<NexusMilestoneView> milestones
) {
    public NexusProgressionTrack {
        Objects.requireNonNull(stat, "Progression stat cannot be null");
        Objects.requireNonNull(state, "Progression state cannot be null");
        milestones = List.copyOf(milestones);
    }

    public NexusBonusTrackLayout layout(int top, int bottom) {
        return new NexusBonusTrackLayout(state.track(), top, bottom);
    }

    /** Keep the grabbed track's live preview visible even when the pointer leaves its hover area. */
    public static NexusProgressionTrack tooltipTrack(List<NexusProgressionTrack> tracks, int draggingIndex,
                                                     NexusProgressionTrack hovered) {
        return draggingIndex >= 0 && draggingIndex < tracks.size() ? tracks.get(draggingIndex) : hovered;
    }

    public double progression(long target, ResourceLocation tierId) {
        var resolved = state.track();
        long effective = Math.clamp(target, 0L, state.currentInvestmentCap());
        return BonusPresentationData.benefitProgression(resolved, effective, tierId);
    }

    /** Handle position follows exact funding, independently of completed gameplay benefits. */
    public double fundingProgression(long target, ResourceLocation tierId) {
        var resolved = state.track();
        return BonusTrackCurve.progressionForInvestment(resolved.checkpoints(), resolved.investmentExponent(),
                Math.clamp(target, 0L, state.currentInvestmentCap()), tierId);
    }

    public int handleY(long target, ResourceLocation tierId, int top, int bottom) {
        return layout(top, bottom).yForEffect(fundingProgression(target, tierId));
    }

    /** Exact final target for a drag; affordability is supplied by the shared cross-mode draft. */
    public long dragTarget(double effect, long affordableTarget, ResourceLocation tierId) {
        var resolved = state.track();
        if (!resolved.available() || state.currentInvestmentCap() == 0) return 0;
        long maximum = Math.clamp(affordableTarget, 0L, state.currentInvestmentCap());
        return Math.min(maximum, BonusTrackCurve.investmentForProgression(resolved.checkpoints(),
                resolved.investmentExponent(), effect, tierId));
    }

    /** Compact slider footer; units and current/draft comparisons belong in the semantic tooltip. */
    public static String effectLabel(StatUnit unit, double value) {
        String number = java.math.BigDecimal.valueOf(Math.rint(value * 100.0) / 100.0)
                .stripTrailingZeros().toPlainString();
        return "+" + number + (unit == StatUnit.PERCENT ? "%" : "");
    }
}
