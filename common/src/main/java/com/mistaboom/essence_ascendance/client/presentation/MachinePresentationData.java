package com.mistaboom.essence_ascendance.client.presentation;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.InfuserBalanceSettings;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureStats;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContribution;

import java.util.ArrayList;
import java.util.List;

/** Pure machine/Focus reference projection over an explicit synchronized runtime. */
public final class MachinePresentationData {
    private MachinePresentationData() { }

    public enum FocusState { NONE, LATENT, UPGRADED }

    public record FocusProfile(FocusState state, EssenceFocusTier completedTier, boolean installed,
                               boolean pylonOperational, boolean infuserOperational,
                               EssencePylonContribution pylonContribution,
                               int infuserEfficiencyBasisPoints, long infuserThroughputPerSecond,
                               long essentiumIngotCapacity) { }

    public record FocusUpgrade(EssenceFocusTier targetTier, EssenceFocusTier requiredInstalledTier,
                               long minimumPerEssence, long totalEssenceRequired) { }

    public record Projection(PresentationContext.Availability availability,
                             EssenceCrucibleStructureStats crucibleBaseline,
                             double pylonLinkRadius, int maximumActivePylons, double infuserLinkRange,
                             int conversionEfficiencyBasisPoints, int carrierExtractionEfficiencyBasisPoints,
                             List<FocusProfile> focusProfiles, List<FocusUpgrade> focusUpgrades) {
        public boolean ready() { return availability == PresentationContext.Availability.READY; }
    }

    public static Projection project(PresentationContext context) {
        if (context.runtime().availability() != PresentationContext.Availability.READY
                || context.runtime().definition() == null) {
            return new Projection(context.runtime().availability(), null, 0, 0, 0, 0, 0,
                    List.of(), List.of());
        }
        RuntimeBalanceDefinition runtime = context.runtime().definition();
        InfuserBalanceSettings infuser = runtime.config().infuserBalance();
        List<FocusProfile> profiles = new ArrayList<>();
        profiles.add(new FocusProfile(FocusState.NONE, null, false, false, false,
                zeroContribution(), 0, 0, 0));
        profiles.add(new FocusProfile(FocusState.LATENT, null, true, true, true,
                runtime.pylon("empty"), infuser.noFocusEfficiencyBasisPoints(),
                infuser.noFocusInfusionThroughputPerSecond(),
                infuser.grade(EssenceFocusTier.DORMANT.serializedName()).ingotCapacity()));
        for (EssenceFocusTier tier : EssenceFocusTier.values()) {
            InfuserBalanceSettings.GradeSettings grade = infuser.grade(tier.serializedName());
            profiles.add(new FocusProfile(FocusState.UPGRADED, tier, true, true, true,
                    runtime.pylon(tier.serializedName()), grade.efficiencyBasisPoints(),
                    grade.infusionThroughputPerSecond(), grade.ingotCapacity()));
        }
        List<FocusUpgrade> upgrades = new ArrayList<>();
        for (EssenceFocusTier target : EssenceFocusTier.values()) {
            InfuserBalanceSettings.FocusUpgradeSettings values = infuser.focusUpgrade(target.serializedName());
            EssenceFocusTier required = target == EssenceFocusTier.DORMANT
                    ? null : EssenceFocusTier.values()[target.ordinal() - 1];
            upgrades.add(new FocusUpgrade(target, required, values.minimumPerAttributeEssence(),
                    values.totalEssenceRequired()));
        }
        return new Projection(PresentationContext.Availability.READY, runtime.crucible(),
                runtime.config().pylonRadius(), runtime.config().maxActivePylons(), infuser.linkRange(),
                infuser.conversionEfficiencyBasisPoints(), infuser.carrierExtractionEfficiencyBasisPoints(),
                List.copyOf(profiles), List.copyOf(upgrades));
    }

    private static EssencePylonContribution zeroContribution() {
        return new EssencePylonContribution(0, 0, 0, 0, 0);
    }
}
