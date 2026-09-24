package com.mistaboom.essence_ascendance.client.presentation;

import com.mistaboom.essence_ascendance.attunement.AttunementProfile;
import com.mistaboom.essence_ascendance.attunement.AttunementLedger;
import com.mistaboom.essence_ascendance.attunement.AttunementPacing;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

/** Current seal-based Ascension policy, not the retired investment/milestone gates. */
public final class MechanicsPresentationData {
    private MechanicsPresentationData() { }

    public record AttunementActivity(AttunementProfile.Rate rate, AttunementProfile.Method method) { }
    public record AttunementChapter(AttunementProfile.Chapter chapter, List<AttunementProfile.Category> categories,
                                    List<AttunementActivity> activities) { }
    public record Projection(PresentationContext.Availability availability, AttunementProfile.Policy attunementPolicy,
                             List<AttunementChapter> attunementChapters) {
        public boolean ready() { return availability == PresentationContext.Availability.READY; }
    }

    public static double maximumInvestmentMultiplier(AttunementProfile.Policy policy) {
        return AttunementLedger.investmentMultiplier(1, 1, policy.maximumAcceleration());
    }

    public static double baseSealPercent(AttunementProfile.Rate rate, AttunementProfile.Category category) {
        return 100 / AttunementPacing.rawUnits(rate, category);
    }

    public static Projection project(PresentationContext context) {
        if (context.runtime().availability() != PresentationContext.Availability.READY
                || context.runtime().definition() == null)
            return new Projection(context.runtime().availability(), null, List.of());
        AttunementProfile attunement = context.runtime().definition().attunement();
        List<AttunementChapter> chapters = attunement.chapters().values().stream()
                .sorted(Comparator.comparingInt(chapter -> AscendanceTierRegistry.get(
                        ResourceLocation.parse(chapter.fromTierId())).orElseThrow().order()))
                .map(chapter -> new AttunementChapter(chapter,
                        chapter.categories().values().stream().sorted(Comparator.comparing(AttunementProfile.Category::categoryId)).toList(),
                        chapter.activities().values().stream().sorted(Comparator.comparing(AttunementProfile.Rate::categoryId)
                                        .thenComparing(AttunementProfile.Rate::activityId))
                                .map(rate -> new AttunementActivity(rate, attunement.methods().get(rate.activityId())))
                                .toList()))
                .toList();
        return new Projection(PresentationContext.Availability.READY, attunement.policy(), chapters);
    }
}
