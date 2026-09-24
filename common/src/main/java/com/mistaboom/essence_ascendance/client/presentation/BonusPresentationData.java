package com.mistaboom.essence_ascendance.client.presentation;

import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition;
import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.network.BonusTrackSnapshot;
import com.mistaboom.essence_ascendance.presentation.PresentationMetric;
import com.mistaboom.essence_ascendance.progression.BonusTrackCurve;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared Bonus metric, checkpoint and pure projection bindings. */
public final class BonusPresentationData {
    private BonusPresentationData() { }

    public record ValueSource(double value) { }

    public record CheckpointProjection(ResourceLocation tierId, boolean available, boolean purchasable,
                                       long segmentCost, long cumulativeCap, double effectFraction,
                                       double effectValue) { }

    public record MeaningfulChange(long investment, long additionalCost, double effectValue,
                                   ResourceLocation tierId, boolean tierCheckpoint) { }

    public record Projection(
            StatDefinition stat,
            PresentationContext.Availability runtimeAvailability,
            PresentationContext.Availability playerAvailability,
            StatUnit unit,
            BonusTrackDefinition.PurchaseStyle purchaseStyle,
            BonusTrackDefinition.Applicability applicability,
            ResourceLocation startTier,
            ResourceLocation completionTier,
            double maximumEffect,
            long storedInvestment,
            long targetInvestment,
            long investmentCap,
            double effectValue,
            MeaningfulChange nextChange,
            List<CheckpointProjection> checkpoints,
            PresentationMetric<ValueSource> metric
    ) {
        public Projection { checkpoints = List.copyOf(checkpoints); }
        public boolean runtimeReady() { return runtimeAvailability == PresentationContext.Availability.READY; }
        public boolean playerReady() { return playerAvailability == PresentationContext.Availability.READY; }
        public boolean available() { return applicability == BonusTrackDefinition.Applicability.AVAILABLE; }
    }

    public static Projection project(StatDefinition stat, PresentationContext context) {
        Objects.requireNonNull(stat);
        Objects.requireNonNull(context);
        BonusTrackDefinition definition = context.runtime().ready()
                ? context.runtime().definition().config().balanceProfile().bonusTrack(stat.id()) : null;
        ClientEssenceState.StatSnapshot player = context.player().ready()
                ? context.player().snapshot().stats().get(stat.id()) : null;
        if (player != null) {
            ResourceLocation tier = context.player().snapshot().tierId();
            return project(stat, context.runtime().availability(), context.player().availability(), player.track(),
                    player.transcendentMaximumBonus(), player.storedInvestment(), player.storedInvestment(),
                    player.currentInvestmentCap(), tier, player.scaledBonus());
        }
        if (definition == null) return unavailable(stat, context.runtime().availability(), context.player().availability());
        BonusTrackSnapshot track = BonusTrackSnapshot.from(definition,
                context.runtime().definition().config().balanceProfile());
        long cap = track.checkpoints().getLast().cumulativeCap();
        return project(stat, context.runtime().availability(), context.player().availability(), track,
                definition.maximumEffect(), 0, 0, cap, track.completionTier(), null);
    }

    public static Projection project(StatDefinition stat, ClientEssenceState.StatSnapshot state,
                                     ResourceLocation tier, long target) {
        return project(stat, PresentationContext.Availability.READY, PresentationContext.Availability.READY,
                state.track(), state.transcendentMaximumBonus(), state.storedInvestment(), target,
                state.currentInvestmentCap(), tier, state.scaledBonus());
    }

    private static Projection project(StatDefinition stat,
                                      PresentationContext.Availability runtimeAvailability,
                                      PresentationContext.Availability playerAvailability,
                                      BonusTrackSnapshot track, double maximumEffect,
                                      long stored, long target, long cap, ResourceLocation tier,
                                      Double synchronizedCurrentEffect) {
        long safeCap = Math.clamp(cap, 0L, BonusTrackCurve.maximumInvestment(track.checkpoints(), tier));
        long safeTarget = Math.clamp(target, 0L, safeCap);
        double progression = benefitProgression(track, safeTarget, tier);
        List<CheckpointProjection> checkpoints = track.checkpoints().stream().map(point ->
                new CheckpointProjection(point.tierId(), point.available(), point.purchasable(), point.segmentCost(),
                        point.cumulativeCap(), point.effectFraction(), maximumEffect * point.effectFraction())).toList();
        MeaningfulChange next = nextChange(track, maximumEffect, safeTarget, tier);
        double effectValue = safeTarget == stored && synchronizedCurrentEffect != null
                ? synchronizedCurrentEffect : maximumEffect * progression;
        return new Projection(stat, runtimeAvailability, playerAvailability, track.unit(), track.purchaseStyle(),
                track.applicability(), track.startTier(), track.completionTier(), maximumEffect, stored, safeTarget,
                safeCap, effectValue, next, checkpoints, metric(stat));
    }

    private static Projection unavailable(StatDefinition stat,
                                          PresentationContext.Availability runtimeAvailability,
                                          PresentationContext.Availability playerAvailability) {
        return new Projection(stat, runtimeAvailability, playerAvailability, stat.unit(),
                BonusTrackDefinition.PurchaseStyle.CONTINUOUS, BonusTrackDefinition.Applicability.UNAVAILABLE,
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "unavailable"),
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "unavailable"),
                0, 0, 0, 0, 0, null, List.of(), metric(stat));
    }

    public static double benefitProgression(BonusTrackSnapshot track, long investment, ResourceLocation tier) {
        if (!track.purchaseStyle().continuousBenefits())
            return BonusTrackCurve.realizedProgressionForInvestment(track.checkpoints(), track.investmentExponent(),
                    track.snapPoints(), investment, tier);
        return BonusTrackCurve.progressionForInvestment(track.checkpoints(), track.investmentExponent(), investment, tier);
    }

    private static MeaningfulChange nextChange(BonusTrackSnapshot track, double maximumEffect,
                                               long target, ResourceLocation tier) {
        if (!track.available()) return null;
        if (track.purchaseStyle().continuousBenefits()) {
            return track.checkpoints().stream().filter(point -> point.purchasable() && point.cumulativeCap() > target)
                    .findFirst().map(point -> new MeaningfulChange(point.cumulativeCap(),
                            point.cumulativeCap() - target, maximumEffect * point.effectFraction(),
                            point.tierId(), true)).orElse(null);
        }
        double current = benefitProgression(track, target, tier);
        for (double snap : track.snapPoints()) {
            if (snap <= current) continue;
            long investment = BonusTrackCurve.investmentForProgression(track.checkpoints(),
                    track.investmentExponent(), snap, tier);
            if (investment > target)
                return new MeaningfulChange(investment, investment - target, maximumEffect * snap, tier, false);
        }
        return null;
    }

    public static PresentationMetric<ValueSource> metric(StatDefinition stat) {
        return PresentationMetric.always("presentation/bonus/" + stat.id() + "/value", EssenceText.stat(stat),
                ValueSource::value, semanticType(stat.unit()), PresentationMetric.DisplayConversion.NATIVE);
    }

    public static String descriptionKey(StatDefinition stat) {
        return "stat.essence_ascendance." + stat.id().getPath() + ".description";
    }

    /** Canonical category-colored Bonus name for lists, articles and tooltips. */
    public static Component name(StatDefinition stat) {
        return EssenceText.stat(stat).withStyle(style -> style.withColor(
                AscendancePalette.categoryRgb(stat.category())));
    }

    public static Component description(Projection projection) {
        return description(projection.stat(), projection.effectValue());
    }

    public static Component description(StatDefinition stat, double value) {
        String formatted = metric(stat).read(new ValueSource(value)).formatted();
        return Component.translatable(descriptionKey(stat),
                Component.literal(formatted).withStyle(ChatFormatting.WHITE));
    }

    public static String number(double value) {
        return PresentationMetric.DisplayConversion.NATIVE.format(value);
    }

    private static PresentationMetric.SemanticValueType semanticType(StatUnit unit) {
        return switch (unit) {
            case PERCENT -> PresentationMetric.SemanticValueType.PERCENTAGE_POINTS;
            case BLOCKS -> PresentationMetric.SemanticValueType.BLOCKS;
            case SECONDS -> PresentationMetric.SemanticValueType.SECONDS;
            case LEVELS -> PresentationMetric.SemanticValueType.COUNT;
            case FLAT, HEARTS, HEARTS_PER_SECOND -> PresentationMetric.SemanticValueType.SCALAR;
        };
    }
}
