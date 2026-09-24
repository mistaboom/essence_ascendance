package com.mistaboom.essence_ascendance.archive;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.attunement.AttunementSnapshot;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.network.BonusTrackSnapshot;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload;
import com.mistaboom.essence_ascendance.presentation.PresentationMetric;
import com.mistaboom.essence_ascendance.progression.BonusTrackCurve;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillRankPolicy;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Executable contract for the shared Skills/Bonuses reference data layer. */
public final class ReferencePresentationTest {
    private static int checks;
    private static JsonObject language;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EssenceTypes.init();
        EssenceStats.init();
        AscendanceTiers.init();
        MilestoneProviders.init();
        Milestones.init();
        Skills.init();
        try (var stream = ReferencePresentationTest.class.getResourceAsStream(
                "/assets/essence_ascendance/lang/en_us.json")) {
            language = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        }
        RuntimeBalanceDefinition runtime = RuntimeBalanceDefinition.bootstrap();
        EssenceConfigManager.installClient(runtime);
        try {
            registryCoverage(runtime);
            skillParity(runtime);
            rankSpecificFixture(runtime);
            bonusParity(runtime);
            currentPlayerContext(runtime);
            readinessAndProfileChanges(runtime);
            semanticFormatting(runtime);
            isolatedChangedValueFixture(runtime);
        } finally {
            EssenceConfigManager.clearClient();
        }
        System.out.println("ReferencePresentationTest: " + checks + " shared reference assertions PASS");
    }

    private static void registryCoverage(RuntimeBalanceDefinition runtime) {
        ArchiveCatalog catalog = ArchiveCatalog.DEFAULT;
        long skillEntries = catalog.entries(ArchiveMode.REFERENCE, ArchiveSection.REFERENCE_SKILLS.id()).size();
        long bonusEntries = catalog.entries(ArchiveMode.REFERENCE, ArchiveSection.REFERENCE_BONUSES.id()).size();
        check(skillEntries == SkillRegistry.size(), "Archive does not enumerate every registered Skill");
        check(bonusEntries == EssenceStatRegistry.size(), "Archive does not enumerate every registered Bonus");
        check(catalog.entry(id("reference/skills/ranks")) == null
                        && catalog.entry(id("reference/bonuses/overview")) == null,
                "Placeholder Skill/Bonus reference entries are still visible");
        for (var skill : SkillRegistry.values())
            check(catalog.entry(id("reference/skills/" + skill.id().getPath())) != null,
                    "Missing browsable Skill entry " + skill.id());
        for (var stat : EssenceStatRegistry.values())
            check(catalog.entry(id("reference/bonuses/" + stat.id().getPath())) != null,
                    "Missing browsable Bonus entry " + stat.id());
        for (var skill : SkillRegistry.values())
            check(catalog.entry(id("reference/skills/" + skill.id().getPath())).title()
                            .getStyle().getColor().getValue() == AscendancePalette.categoryRgb(skill.essenceId()),
                    "Skill title is not category-colored " + skill.id());
        for (var stat : EssenceStatRegistry.values())
            check(catalog.entry(id("reference/bonuses/" + stat.id().getPath())).title()
                            .getStyle().getColor().getValue() == AscendancePalette.categoryRgb(stat.category()),
                    "Bonus title is not category-colored " + stat.id());
        check(SkillPresentationData.tierName(AscendanceTiers.RESONANT.id()).getStyle().getColor().getValue()
                        == AscendancePalette.tierMetalRgb(AscendanceTiers.RESONANT.id()),
                "Tier references do not use the canonical tier color");

        PresentationContext context = PresentationContext.catalog(runtime);
        for (var skill : SkillRegistry.values()) requireLocalized(ArchiveDocuments.skillReference(skill, context));
        for (var stat : EssenceStatRegistry.values()) requireLocalized(ArchiveDocuments.bonusReference(stat, context));
    }

    private static void skillParity(RuntimeBalanceDefinition runtime) {
        PresentationContext context = PresentationContext.catalog(runtime);
        Set<String> identities = new HashSet<>();
        int behaviorLines = 0;
        for (var skill : SkillRegistry.values()) {
            SkillPresentationData.Projection projection = SkillPresentationData.project(skill, context);
            SkillBalanceRuntime.ResolvedSkill curve = runtime.skillCurves().get(skill.id().toString());
            check(projection.runtimeReady() && projection.maximumRank() == curve.maximumRank()
                            && projection.ranks().size() == curve.maximumRank(),
                    "Resolved maximum rank disagrees for " + skill.id());
            for (var rank : projection.ranks()) {
                var nativeSettings = SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(),
                        Map.of(skill.id(), rank.rank()), runtime.skillCurves());
                check(rank.cost() == curve.ranks().get(rank.rank() - 1).cost(),
                        "Rank cost disagrees with generated curve " + skill.id());
                check(rank.requiredTier().equals(skill.requiredTierId(rank.rank()))
                                && rank.prerequisites().equals(skill.prerequisiteRanks(rank.rank()))
                                && rank.requirements().equals(skill.requirements(rank.rank())),
                        "Rank-specific gates disagree with native APIs " + skill.id() + "/" + rank.rank());
                List<SkillTooltipRegistry.Binding> bindings = SkillTooltipRegistry.bindings(skill.id());
                check(rank.effects().size() == bindings.size(), "Effect binding coverage changed " + skill.id());
                for (int index = 0; index < bindings.size(); index++) {
                    var binding = bindings.get(index);
                    var effect = rank.effects().get(index);
                    check(effect.prose().equals(binding.render(nativeSettings)),
                            "Article prose did not use the native tooltip binding " + binding.id());
                    check(effect.metrics().size() == binding.metrics().size(), "Metric arity changed " + binding.id());
                    for (int metricIndex = 0; metricIndex < binding.metrics().size(); metricIndex++) {
                        var metric = binding.metrics().get(metricIndex);
                        var expected = metric.read(nativeSettings);
                        var actual = effect.metrics().get(metricIndex);
                        check(expected.equals(actual),
                                "Metric identity/value mismatch " + metric.id());
                        if (rank.rank() == 1)
                            check(identities.add(metric.id()), "Duplicate metric identity " + metric.id());
                    }
                    if (binding.behavior() != null) {
                        behaviorLines++;
                        check(effect.behavior() != null && effect.behavior().id().equals(binding.behavior().id()),
                                "Nonnumeric behavior binding was lost");
                    }
                }
                check(SkillPresentationData.resolveEffects(runtime, skill.id(), Map.of(skill.id(), rank.rank()))
                                .equals(rank.effects().stream().map(SkillPresentationData.EffectLine::prose).toList()),
                        "Tooltip and table effect projections diverged " + skill.id());
            }
        }
        check(behaviorLines > 0, "Nonnumeric Skill behavior has no structured descriptors");
    }

    private static void bonusParity(RuntimeBalanceDefinition runtime) {
        PresentationContext context = PresentationContext.catalog(runtime);
        for (var stat : EssenceStatRegistry.values()) {
            var definition = runtime.config().balanceProfile().bonusTrack(stat.id());
            BonusPresentationData.Projection catalog = BonusPresentationData.project(stat, context);
            check(catalog.runtimeReady() && catalog.checkpoints().size() == definition.checkpoints().size(),
                    "Bonus checkpoint coverage changed " + stat.id());
            long fullCap = definition.checkpoints().getLast().cumulativeCap();
            BonusTrackSnapshot track = BonusTrackSnapshot.from(definition, runtime.config().balanceProfile());
            var state = new ClientEssenceState.StatSnapshot(stat.id(), 0, 0, fullCap, 0,
                    definition.maximumEffect(), definition.maximumEffect(), 0, track);
            for (var checkpoint : definition.checkpoints()) {
                BonusPresentationData.Projection projection = BonusPresentationData.project(
                        stat, state, checkpoint.tierId(), checkpoint.cumulativeCap());
                double expectedProgression = definition.purchaseStyle().continuousBenefits()
                        ? BonusTrackCurve.progressionForInvestment(definition.checkpoints(),
                                definition.investmentExponent(), checkpoint.cumulativeCap(), checkpoint.tierId())
                        : BonusTrackCurve.realizedProgressionForInvestment(definition.checkpoints(),
                                definition.investmentExponent(), definition.snapPoints(),
                                checkpoint.cumulativeCap(), checkpoint.tierId());
                check(Math.abs(projection.effectValue() - definition.maximumEffect() * expectedProgression) < 1e-9,
                        "Bonus projection disagrees with native curve " + stat.id() + "/" + checkpoint.tierId());
                var row = projection.checkpoints().stream()
                        .filter(value -> value.tierId().equals(checkpoint.tierId())).findFirst().orElseThrow();
                check(row.segmentCost() == checkpoint.segmentCost()
                                && row.cumulativeCap() == checkpoint.cumulativeCap()
                                && row.available() == checkpoint.available()
                                && row.purchasable() == checkpoint.purchasable(),
                        "Bonus article copied or reconstructed a checkpoint " + stat.id());
            }
        }
    }

    private static void rankSpecificFixture(RuntimeBalanceDefinition runtime) {
        SkillDefinition base = SkillRegistry.require(SkillIds.RUNNING_MOMENTUM);
        ResourceLocation requirementId = id("fixture/rank_two_investment");
        var requirement = new BonusInvestmentRequirement(requirementId, base.essenceId(), 123,
                "skill_requirement.essence_ascendance.type.bonus_investment");
        SkillRankPolicy policy = new SkillRankPolicy(base.rankPolicy().maximumRank(),
                base.rankPolicy().projectionRanks(), base.rankPolicy().curve(), base.rankPolicy().refundRule(),
                Map.of(2, new SkillRankPolicy.Gates(AscendanceTiers.ASCENDANT.id(),
                        Map.of(SkillIds.MOMENTUM_VAULT, 2), List.of(requirement))));
        SkillDefinition fixture = new SkillDefinition(base.id(), base.essenceId(), base.nameTranslationKey(),
                base.descriptionTranslationKey(), base.requiredTierId(), base.costBand(), base.prerequisites(),
                base.requirements(), base.choiceGroup(), base.replacementTarget(), base.activationPolicy(),
                base.displayOrder(), base.layoutHint(), policy);
        var projection = SkillPresentationData.project(fixture, PresentationContext.catalog(runtime));
        check(projection.ranks().size() >= 2, "Rank-gate fixture lacks a second generated rank");
        check(!projection.ranks().getFirst().requirements().contains(requirement)
                        && projection.ranks().get(1).requirements().contains(requirement)
                        && projection.ranks().get(1).requiredTier().equals(AscendanceTiers.ASCENDANT.id())
                        && projection.ranks().get(1).prerequisites().get(SkillIds.MOMENTUM_VAULT) == 2,
                "Rank-specific requirement/tier/prerequisite inheritance was flattened to base metadata");
        Component rendered = SkillPresentationData.requirementDescription(fixture, 2, requirementId,
                null, Map.of());
        check(rendered.getContents() instanceof TranslatableContents translated
                        && translated.getKey().equals(requirement.translationKey()),
                "Rank-specific requirement description did not resolve the refined binding");
    }

    private static void currentPlayerContext(RuntimeBalanceDefinition runtime) {
        var skill = SkillRegistry.require(SkillIds.RUNNING_MOMENTUM);
        var stat = EssenceStatRegistry.values().iterator().next();
        var trackDefinition = runtime.config().balanceProfile().bonusTrack(stat.id());
        var track = BonusTrackSnapshot.from(trackDefinition, runtime.config().balanceProfile());
        long stored = track.checkpoints().stream().filter(point -> point.purchasable()).findFirst()
                .map(point -> Math.max(1L, point.cumulativeCap() / 2)).orElse(0L);
        long cap = track.checkpoints().getLast().cumulativeCap();
        double progression = BonusPresentationData.benefitProgression(track, stored, AscendanceTiers.TRANSCENDENT.id());
        var statState = new ClientEssenceState.StatSnapshot(stat.id(), stored, stored, cap, progression,
                trackDefinition.maximumEffect(), trackDefinition.maximumEffect(),
                trackDefinition.maximumEffect() * progression, track);
        var receipt = new ClientEssenceState.SkillPurchaseSnapshot(skill.id(), skill.essenceId(),
                List.of(runtime.skillCurves().get(skill.id().toString()).ranks().getFirst().cost()));
        ClientEssenceState.Snapshot snapshot = snapshot(runtime, Map.of(skill.id(), receipt), Map.of(stat.id(), statState));
        PresentationContext context = new PresentationContext(
                new PresentationContext.Runtime(PresentationContext.Availability.READY, runtime),
                PresentationContext.Player.ready(snapshot), new PresentationContext.Revision(
                        System.identityHashCode(runtime), snapshot.playerRevision(), true, 4, 7));

        Map<String, SkillBalanceRuntime.ResolvedSkill> installed = SkillBalanceRuntime.snapshot();
        var skillProjection = SkillPresentationData.project(skill, context);
        var bonusProjection = BonusPresentationData.project(stat, context);
        check(skillProjection.playerReady() && skillProjection.currentRank() == 1
                        && skillProjection.playerState() != null && skillProjection.nextRank() != null,
                "Current Skill ownership/rank context is absent");
        check(skillProjection.ranks().stream().allMatch(rank -> rank.playerEligibility() != null),
                "Rank-specific current-player eligibility was not evaluated");
        check(bonusProjection.playerReady() && bonusProjection.storedInvestment() == stored
                        && Math.abs(bonusProjection.effectValue() - statState.scaledBonus()) < 1e-9,
                "Current Bonus state is not sourced from the synchronized snapshot");
        check(SkillBalanceRuntime.snapshot() == installed && snapshot.ownedSkills().get(skill.id()) == receipt,
                "A read-only projection mutated or replaced gameplay/player state");
    }

    private static void readinessAndProfileChanges(RuntimeBalanceDefinition runtime) {
        PresentationContext loading = new PresentationContext(
                new PresentationContext.Runtime(PresentationContext.Availability.LOADING, null),
                PresentationContext.Player.loading(), new PresentationContext.Revision(0, -1, false, 1, 1));
        var skill = SkillRegistry.require(SkillIds.RUNNING_MOMENTUM);
        var stat = EssenceStatRegistry.values().iterator().next();
        check(!SkillPresentationData.project(skill, loading).runtimeReady()
                        && SkillPresentationData.project(skill, loading).maximumRank() == 0,
                "Runtime loading was collapsed into a real rank-one curve");
        check(!BonusPresentationData.project(stat, loading).runtimeReady(),
                "Runtime loading was collapsed into a real zero Bonus");
        PresentationContext catalog = PresentationContext.catalog(runtime);
        check(SkillPresentationData.project(skill, catalog).playerAvailability()
                        == PresentationContext.Availability.CONTEXT_REQUIRED,
                "General catalog values were confused with zeroed current-player state");
        PresentationContext unavailable = new PresentationContext(
                new PresentationContext.Runtime(PresentationContext.Availability.UNAVAILABLE, null),
                PresentationContext.Player.unavailable(), new PresentationContext.Revision(0, -1, false, 2, 1));
        check(SkillPresentationData.project(skill, unavailable).runtimeAvailability()
                        == PresentationContext.Availability.UNAVAILABLE,
                "Unavailable and loading runtime states were merged");
    }

    private static void semanticFormatting(RuntimeBalanceDefinition runtime) {
        Set<PresentationMetric.SemanticValueType> types = new HashSet<>();
        for (var skill : SkillRegistry.values()) for (var binding : SkillTooltipRegistry.bindings(skill.id()))
            binding.metrics().forEach(metric -> types.add(metric.valueType()));
        check(types.containsAll(Set.of(PresentationMetric.SemanticValueType.PERCENTAGE,
                        PresentationMetric.SemanticValueType.PERCENTAGE_POINTS,
                        PresentationMetric.SemanticValueType.SECONDS,
                        PresentationMetric.SemanticValueType.COUNT,
                        PresentationMetric.SemanticValueType.BLOCKS,
                        PresentationMetric.SemanticValueType.MULTIPLIER)),
                "Skill metrics do not distinguish semantic units");
        var tiny = PresentationMetric.always("test/tiny", Component.literal("Tiny"),
                ignored -> 0.00001234, PresentationMetric.SemanticValueType.FRACTION,
                PresentationMetric.DisplayConversion.NATIVE).read(new Object());
        check(tiny.formatted().equals("0.00001234") && tiny.rawValue() != 0,
                "Small nonzero values were rounded to zero");
        check(PresentationMetric.DisplayConversion.FRACTION_TO_PERCENT.format(0.125).equals("12.5")
                        && PresentationMetric.DisplayConversion.TICKS_TO_SECONDS.format(5).equals("0.25"),
                "Fraction/percentage and tick/second conversions are ambiguous");
        for (var stat : EssenceStatRegistry.values())
            check(BonusPresentationData.metric(stat).valueType() != null
                            && language.has(BonusPresentationData.descriptionKey(stat)),
                    "Bonus metric lacks semantic unit or localized label " + stat.id());
    }

    private static void isolatedChangedValueFixture(RuntimeBalanceDefinition runtime) {
        var skill = SkillRegistry.require(SkillIds.RUNNING_MOMENTUM);
        var originalCurve = runtime.skillCurves().get(skill.id().toString());
        List<SkillBalanceRuntime.ResolvedRank> changedRanks = new ArrayList<>(originalCurve.ranks());
        int index = originalCurve.maximumRank() - 1;
        var originalRank = changedRanks.get(index);
        var changedRank = new SkillBalanceRuntime.ResolvedRank(originalRank.rank(), originalRank.cost() + 37,
                originalRank.powerMultiplier(), originalRank.parameters());
        changedRanks.set(index, changedRank);
        Map<String, SkillBalanceRuntime.ResolvedSkill> curves = new LinkedHashMap<>(runtime.skillCurves());
        curves.put(skill.id().toString(), new SkillBalanceRuntime.ResolvedSkill(originalCurve.maximumRank(), changedRanks));
        RuntimeBalanceDefinition changed = new RuntimeBalanceDefinition(runtime.config(), runtime.crucible(),
                runtime.pylons(), curves, runtime.composition(), runtime.attunement());

        Map<String, SkillBalanceRuntime.ResolvedSkill> installed = SkillBalanceRuntime.snapshot();
        var before = SkillPresentationData.project(skill, PresentationContext.catalog(runtime));
        var after = SkillPresentationData.project(skill, PresentationContext.catalog(changed));
        check(before.ranks().get(index).cost() == originalRank.cost()
                        && after.ranks().get(index).cost() == changedRank.cost(),
                "Changed runtime fixture did not flow into documentation data");
        String beforeArticle = flattenAll(ArchiveDocuments.skillReference(skill, PresentationContext.catalog(runtime)));
        String afterArticle = flattenAll(ArchiveDocuments.skillReference(skill, PresentationContext.catalog(changed)));
        check(!beforeArticle.equals(afterArticle) && afterArticle.contains(Long.toString(changedRank.cost())),
                "Article did not update from an isolated changed value fixture");
        check(SkillBalanceRuntime.snapshot() == installed,
                "Preview installed a temporary global Skill profile");
        check(!PresentationContext.catalog(runtime).revision().equals(PresentationContext.catalog(changed).revision()),
                "Profile identity does not invalidate presentation caches");
    }

    private static ClientEssenceState.Snapshot snapshot(RuntimeBalanceDefinition runtime,
                                                        Map<ResourceLocation, ClientEssenceState.SkillPurchaseSnapshot> skills,
                                                        Map<ResourceLocation, ClientEssenceState.StatSnapshot> stats) {
        var progress = new ClientEssenceState.ProgressSnapshot(PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER,
                null, 0, 0, 0, 0, 0, 0, 0, List.of(), true, false);
        return new ClientEssenceState.Snapshot(true, 19, AscendanceTiers.TRANSCENDENT.id(),
                runtime.config().balanceProfile().id(), Map.of(), stats, Map.of(), Set.of(), skills, Map.of(),
                AttunementSnapshot.empty(), progress);
    }

    private static void requireLocalized(SemanticDocument document) {
        for (Component component : document.searchableText()) requireLocalized(component);
    }

    private static void requireLocalized(Component component) {
        if (component.getContents() instanceof TranslatableContents translated) {
            check(language.has(translated.getKey()), "Missing reference localization " + translated.getKey());
            for (Object argument : translated.getArgs()) if (argument instanceof Component nested) requireLocalized(nested);
        }
        component.getSiblings().forEach(ReferencePresentationTest::requireLocalized);
    }

    private static String flattenAll(SemanticDocument document) {
        return document.searchableText().stream().map(ReferencePresentationTest::flatten)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static String flatten(Component component) {
        String value;
        if (component.getContents() instanceof TranslatableContents translated) {
            Object[] args = Arrays.stream(translated.getArgs())
                    .map(argument -> argument instanceof Component nested ? flatten(nested) : argument).toArray();
            value = String.format(Locale.ROOT, language.get(translated.getKey()).getAsString(), args);
        } else value = component.getString();
        for (Component sibling : component.getSiblings()) value += flatten(sibling);
        return value;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("essence_ascendance", path);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
