package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillPrerequisiteStatus;
import com.mistaboom.essence_ascendance.skill.SkillRequirementStatus;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Archive-specific semantic article composition, separate from catalog topology and shared rendering. */
final class ArchiveDocuments {
    private ArchiveDocuments() { }

    static SemanticDocument beginningGuide() {
        String key = "archive.entry.guide.beginning.welcome";
        return new SemanticDocument(g(key + ".title"), List.of(
                new SemanticDocument.Paragraph(g(key + ".body")),
                new SemanticDocument.OrderedSteps(List.of(g(key + ".step.1"), g(key + ".step.2"), g(key + ".step.3"))),
                new SemanticDocument.Callout(SemanticDocument.CalloutKind.TIP, g("archive.callout.tip"), g(key + ".tip")),
                new SemanticDocument.Illustration(id("ascendance_archive"), g(key + ".caption"), 124, 68),
                new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, g("archive.see_also")),
                new SemanticDocument.Links(List.of(new SemanticDocument.Link(id("guide/essence/collecting").toString(),
                        g("archive.entry.guide.essence.collecting.title"), SemanticDocument.LinkRelation.NEXT)))));
    }

    static SemanticDocument illustrated(String key, String item) {
        String entryKey = "archive.entry." + key;
        return new SemanticDocument(g(entryKey + ".title"), List.of(
                new SemanticDocument.Paragraph(g(entryKey + ".body")),
                new SemanticDocument.Illustration(id(item), g(entryKey + ".caption"), 140, 82),
                new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"), g(entryKey + ".note"))));
    }

    static SemanticDocument reference(String key, String item) {
        String entryKey = "archive.entry." + key;
        return new SemanticDocument(g(entryKey + ".title"), List.of(
                new SemanticDocument.Illustration(id(item), g(entryKey + ".caption"), 132, 76),
                new SemanticDocument.Paragraph(g(entryKey + ".body")),
                new SemanticDocument.StatRows(List.of(
                        new SemanticDocument.StatRow(g("archive.reference.identity"), g(entryKey + ".identity")),
                        new SemanticDocument.StatRow(g("archive.reference.role"), g(entryKey + ".role"))))));
    }

    static SemanticDocument skillsGuide() {
        String key = "archive.entry.guide.skills.choosing";
        return new SemanticDocument(g(key + ".title"), List.of(
                new SemanticDocument.Paragraph(g(key + ".body")),
                new SemanticDocument.Requirements(List.of(
                        new SemanticDocument.Requirement(g(key + ".requirement.tier"), g(key + ".requirement.tier.detail"),
                                SemanticDocument.RequirementStatus.INFORMATION),
                        new SemanticDocument.Requirement(g(key + ".requirement.path"), g(key + ".requirement.path.detail"),
                                SemanticDocument.RequirementStatus.INFORMATION))),
                new SemanticDocument.Callout(SemanticDocument.CalloutKind.WARNING, g("archive.callout.warning"), g(key + ".warning"))));
    }

    static SemanticDocument essenceGuide() {
        String key = "archive.entry.guide.essence.collecting";
        return new SemanticDocument(g(key + ".title"), List.of(
                new SemanticDocument.Paragraph(g(key + ".body")),
                new SemanticDocument.Illustration(id("latent_ore"), g(key + ".caption"), 140, 82),
                new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"), g(key + ".note")),
                new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, g("archive.see_also")),
                new SemanticDocument.Links(List.of(
                        new SemanticDocument.Link(id("guide/beginning/welcome").toString(),
                                g("archive.entry.guide.beginning.welcome.title"), SemanticDocument.LinkRelation.PREVIOUS),
                        new SemanticDocument.Link(id("guide/machines/first_network").toString(),
                                g("archive.entry.guide.machines.first_network.title"), SemanticDocument.LinkRelation.NEXT),
                        new SemanticDocument.Link(id("reference/essences/item_yields").toString(),
                                g("archive.entry.reference.essences.item_yields.title"), SemanticDocument.LinkRelation.RELATED)))));
    }

    static SemanticDocument infusionGuide() {
        String key = "archive.entry.guide.infusion.first_focus";
        return new SemanticDocument(g(key + ".title"), List.of(
                new SemanticDocument.Paragraph(g(key + ".body")),
                new SemanticDocument.Illustration(id("essence_infuser"), g(key + ".caption"), 140, 82),
                new SemanticDocument.Illustration(id("essence_focus"), g(key + ".focus_caption"), 108, 68),
                new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"), g(key + ".note"))));
    }

    private record SkillRankRow(Integer rank, Long cost, Component effects) { }
    private record SkillGateRow(Integer rank, Component tier, Component prerequisites, Component requirements) { }

    static SemanticDocument skillReference(SkillDefinition skill, PresentationContext context) {
        SkillPresentationData.Projection projection = SkillPresentationData.project(skill, context);
        Component title = SkillPresentationData.skillName(skill);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.Icon(id("ascendance_nexus"),
                g("archive.reference.skill.icon", title)));
        blocks.add(new SemanticDocument.Paragraph(Component.translatable(skill.descriptionTranslationKey())));

        List<SemanticDocument.StatRow> identity = new ArrayList<>();
        identity.add(new SemanticDocument.StatRow(g("archive.reference.category"), skillCategory(skill)));
        identity.add(new SemanticDocument.StatRow(g("archive.reference.activation"),
                Component.translatable("skill_activation.essence_ascendance."
                        + skill.activationPolicy().name().toLowerCase(java.util.Locale.ROOT))));
        identity.add(new SemanticDocument.StatRow(g("archive.reference.maximum_rank"), projection.maximumRank() > 0
                ? Component.literal(Integer.toString(projection.maximumRank())) : g("archive.value.unavailable")));
        blocks.add(new SemanticDocument.StatRows(identity));
        appendRelationships(blocks, projection);

        if (!projection.runtimeReady()) {
            blocks.add(stateCallout(projection.runtimeAvailability(), "archive.reference.runtime"));
        } else {
            blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION,
                    g("archive.reference.skill.rank_effects")));
            List<ReadOnlyDataTable.Row<SkillRankRow>> rows = projection.ranks().stream().map(rank ->
                    new ReadOnlyDataTable.Row<>("rank/" + rank.rank(), new SkillRankRow(rank.rank(), rank.cost(),
                            join(rank.effects().stream().map(SkillPresentationData.EffectLine::prose).toList(), "  •  ")))).toList();
            blocks.add(new SemanticDocument.Table<>(new ReadOnlyDataTable<>(List.of(
                    column("rank", g("archive.table.rank"), ReadOnlyDataTable.RANK_COLUMN_WIDTH, 0,
                            ReadOnlyDataTable.Alignment.RIGHT, SkillRankRow::rank,
                            value -> Component.literal(Integer.toString(value))),
                    column("cost", g("archive.table.cost"), 64, 0, ReadOnlyDataTable.Alignment.RIGHT,
                            SkillRankRow::cost, value -> essenceAmount(skill, value)),
                    column("effects", g("archive.table.effects"), 150, 1, ReadOnlyDataTable.Alignment.LEFT,
                            SkillRankRow::effects, value -> value)
            ), rows)));

            blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION,
                    g("archive.reference.skill.rank_gates")));
            List<ReadOnlyDataTable.Row<SkillGateRow>> gates = projection.ranks().stream().map(rank ->
                    new ReadOnlyDataTable.Row<>("gate/" + rank.rank(), new SkillGateRow(rank.rank(),
                            SkillPresentationData.tierName(rank.requiredTier()), prerequisites(rank.prerequisites()),
                            requirements(skill, rank.rank(), rank.requirements(), context)))).toList();
            blocks.add(new SemanticDocument.Table<>(new ReadOnlyDataTable<>(List.of(
                    column("rank", g("archive.table.rank"), ReadOnlyDataTable.RANK_COLUMN_WIDTH, 0,
                            ReadOnlyDataTable.Alignment.RIGHT, SkillGateRow::rank,
                            value -> Component.literal(Integer.toString(value))),
                    column("tier", g("archive.table.tier"), 86, 0, ReadOnlyDataTable.Alignment.LEFT,
                            SkillGateRow::tier, value -> value),
                    column("prerequisites", g("archive.table.prerequisites"), 100, 1,
                            ReadOnlyDataTable.Alignment.LEFT, SkillGateRow::prerequisites, value -> value),
                    column("requirements", g("archive.table.requirements"), 110, 1,
                            ReadOnlyDataTable.Alignment.LEFT, SkillGateRow::requirements, value -> value)
            ), gates)));
        }
        appendPlayerSkillContext(blocks, projection, context);
        return new SemanticDocument(title, blocks);
    }

    private static void appendRelationships(List<SemanticDocument.Block> blocks,
                                            SkillPresentationData.Projection projection) {
        List<SemanticDocument.StatRow> relationships = new ArrayList<>();
        if (projection.choiceGroup() != null)
            relationships.add(new SemanticDocument.StatRow(g("archive.reference.choice_group"),
                    Component.translatable(projection.choiceGroup().translationKey())));
        if (!projection.exclusions().isEmpty())
            relationships.add(new SemanticDocument.StatRow(g("archive.reference.excludes"),
                    join(projection.exclusions().stream().map(SkillPresentationData::skillName).toList(), ", ")));
        if (projection.replacementTarget() != null)
            relationships.add(new SemanticDocument.StatRow(g("archive.reference.replaces"),
                    SkillPresentationData.skillName(projection.replacementTarget())));
        if (!projection.replacedBy().isEmpty())
            relationships.add(new SemanticDocument.StatRow(g("archive.reference.replaced_by"),
                    join(projection.replacedBy().stream().map(SkillPresentationData::skillName).toList(), ", ")));
        if (!relationships.isEmpty()) {
            blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION,
                    g("archive.reference.relationships")));
            blocks.add(new SemanticDocument.StatRows(relationships));
            blocks.add(new SemanticDocument.Paragraph(g(projection.replacementTarget() == null
                    ? "archive.reference.choice.consequence" : "archive.reference.replacement.consequence")));
        }
    }

    private static void appendPlayerSkillContext(List<SemanticDocument.Block> blocks,
                                                 SkillPresentationData.Projection projection,
                                                 PresentationContext context) {
        blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION,
                g("archive.reference.current_player")));
        if (!projection.playerReady()) {
            blocks.add(stateCallout(projection.playerAvailability(), "archive.reference.player"));
            return;
        }
        blocks.add(new SemanticDocument.StatRows(List.of(
                new SemanticDocument.StatRow(g("archive.reference.current_rank"),
                        Component.literal(projection.currentRank() + "/" + projection.maximumRank())),
                new SemanticDocument.StatRow(g("archive.reference.next_rank"), projection.nextRank() == null
                        ? g("archive.value.maximum") : Component.literal(Integer.toString(projection.nextRank()))),
                new SemanticDocument.StatRow(g("archive.reference.ownership"),
                        g(projection.currentRank() > 0 ? "archive.value.owned" : "archive.value.unowned")),
                new SemanticDocument.StatRow(g("archive.reference.effect_state"),
                        g(projection.playerState() != null && projection.playerState().effective()
                                ? "archive.value.active" : "archive.value.inactive")))));
        if (projection.nextRank() == null || projection.playerState() == null) return;
        var eligibility = projection.ranks().get(projection.nextRank() - 1).playerEligibility();
        List<SemanticDocument.Requirement> statuses = new ArrayList<>();
        statuses.add(new SemanticDocument.Requirement(g("archive.reference.tier_gate"),
                SkillPresentationData.tierName(projection.ranks().get(projection.nextRank() - 1).requiredTier()),
                eligibility.tierSatisfied() ? SemanticDocument.RequirementStatus.MET : SemanticDocument.RequirementStatus.UNMET));
        for (SkillPrerequisiteStatus status : eligibility.prerequisites())
            statuses.add(new SemanticDocument.Requirement(SkillPresentationData.skillName(status.skillId()),
                    g("archive.value.rank_requirement", status.requiredRank()), status.projectedOwned()
                    ? SemanticDocument.RequirementStatus.MET : SemanticDocument.RequirementStatus.UNMET));
        for (SkillRequirementStatus status : eligibility.requirements())
            statuses.add(new SemanticDocument.Requirement(
                    SkillPresentationData.requirementDescription(projection.definition(), projection.nextRank(),
                            status.requirementId(),
                            context.player().snapshot(), context.player().bonusTotals()),
                    g(status.live() ? "archive.value.live_requirement" : "archive.value.purchase_requirement"),
                    status.projectedSatisfied() ? SemanticDocument.RequirementStatus.MET
                            : SemanticDocument.RequirementStatus.UNMET));
        blocks.add(new SemanticDocument.Requirements(statuses));
    }

    private record BonusCheckpointRow(Component tier, Component availability, Long segmentCost,
                                      Long cumulativeCap, Component benefit) { }

    static SemanticDocument bonusReference(StatDefinition stat, PresentationContext context) {
        BonusPresentationData.Projection projection = BonusPresentationData.project(stat, context);
        Component title = BonusPresentationData.name(stat);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.Icon(id("ascendance_nexus"),
                g("archive.reference.bonus.icon", title)));
        if (!projection.runtimeReady()) {
            blocks.add(stateCallout(projection.runtimeAvailability(), "archive.reference.runtime"));
            return new SemanticDocument(title, blocks);
        }
        blocks.add(new SemanticDocument.Paragraph(BonusPresentationData.description(stat, projection.maximumEffect())));
        blocks.add(new SemanticDocument.StatRows(List.of(
                new SemanticDocument.StatRow(g("archive.reference.category"),
                        EssenceText.category(stat.category()).withStyle(style -> style.withColor(
                                AscendancePalette.categoryRgb(stat.category())))),
                new SemanticDocument.StatRow(g("archive.reference.unit"), unit(stat.unit())),
                new SemanticDocument.StatRow(g("archive.reference.purchase_style"),
                        g("archive.value.purchase_style." + projection.purchaseStyle().name().toLowerCase(java.util.Locale.ROOT))),
                new SemanticDocument.StatRow(g("archive.reference.availability"),
                        g(projection.available() ? "archive.value.available" : "archive.value.unavailable")),
                new SemanticDocument.StatRow(g("archive.reference.start_tier"),
                        SkillPresentationData.tierName(projection.startTier())),
                new SemanticDocument.StatRow(g("archive.reference.completion_tier"),
                        SkillPresentationData.tierName(projection.completionTier())),
                new SemanticDocument.StatRow(g("archive.reference.maximum_effect"),
                        formatted(stat.unit(), projection.maximumEffect())))));
        blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION,
                g("archive.reference.bonus.progression")));
        List<ReadOnlyDataTable.Row<BonusCheckpointRow>> rows = projection.checkpoints().stream().map(point ->
                new ReadOnlyDataTable.Row<>("tier/" + point.tierId(), new BonusCheckpointRow(
                        SkillPresentationData.tierName(point.tierId()),
                        g(point.available() ? "archive.value.available" : "archive.value.unavailable"),
                        point.segmentCost(), point.cumulativeCap(), formatted(stat.unit(), point.effectValue())))).toList();
        blocks.add(new SemanticDocument.Table<>(new ReadOnlyDataTable<>(List.of(
                column("tier", g("archive.table.tier"), 86, 0, ReadOnlyDataTable.Alignment.LEFT,
                        BonusCheckpointRow::tier, value -> value),
                column("availability", g("archive.table.availability"), 66, 0,
                        ReadOnlyDataTable.Alignment.LEFT, BonusCheckpointRow::availability, value -> value),
                column("cost", g("archive.table.segment_cost"), 58, 0, ReadOnlyDataTable.Alignment.RIGHT,
                        BonusCheckpointRow::segmentCost, value -> essenceAmount(stat, value)),
                column("cap", g("archive.table.cumulative_cap"), 60, 0, ReadOnlyDataTable.Alignment.RIGHT,
                        BonusCheckpointRow::cumulativeCap, value -> essenceAmount(stat, value)),
                column("benefit", g("archive.table.benefit"), 72, 1, ReadOnlyDataTable.Alignment.LEFT,
                        BonusCheckpointRow::benefit, value -> value)
        ), rows)));
        blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION,
                g("archive.reference.current_player")));
        if (!projection.playerReady()) {
            blocks.add(stateCallout(projection.playerAvailability(), "archive.reference.player"));
        } else {
            List<SemanticDocument.StatRow> current = new ArrayList<>();
            current.add(new SemanticDocument.StatRow(g("archive.reference.invested"),
                    Component.literal(Long.toString(projection.storedInvestment()))));
            current.add(new SemanticDocument.StatRow(g("archive.reference.current_cap"),
                    Component.literal(Long.toString(projection.investmentCap()))));
            current.add(new SemanticDocument.StatRow(g("archive.reference.current_effect"),
                    formatted(stat.unit(), projection.effectValue())));
            if (projection.nextChange() != null) {
                current.add(new SemanticDocument.StatRow(g("archive.reference.next_change"),
                        g("archive.value.next_change", projection.nextChange().additionalCost(),
                                formatted(stat.unit(), projection.nextChange().effectValue()),
                                SkillPresentationData.tierName(projection.nextChange().tierId()))));
            }
            blocks.add(new SemanticDocument.StatRows(current));
        }
        return new SemanticDocument(title, blocks);
    }

    private static Component prerequisites(Map<ResourceLocation, Integer> prerequisites) {
        if (prerequisites.isEmpty()) return g("archive.value.none");
        return join(prerequisites.entrySet().stream().map(entry ->
                g("archive.value.named_rank", SkillPresentationData.skillName(entry.getKey()), entry.getValue())).toList(), ", ");
    }

    private static Component requirements(SkillDefinition skill, int rank, List<SkillRequirement> requirements,
                                          PresentationContext context) {
        if (requirements.isEmpty()) return g("archive.value.none");
        return join(requirements.stream().map(requirement -> SkillPresentationData.requirementDescription(
                skill, rank, requirement.id(), context.player().snapshot(), context.player().bonusTotals())).toList(), ", ");
    }

    private static Component unit(StatUnit unit) {
        return g("archive.value.unit." + unit.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component formatted(StatUnit unit, double value) {
        return g("archive.value.formatted." + unit.name().toLowerCase(java.util.Locale.ROOT),
                BonusPresentationData.number(value));
    }

    private static SemanticDocument.Callout stateCallout(PresentationContext.Availability state, String prefix) {
        return new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"),
                g(prefix + "." + state.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static Component join(List<Component> values, String separator) {
        if (values.isEmpty()) return Component.empty();
        var result = Component.empty();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) result.append(Component.literal(separator));
            result.append(values.get(index));
        }
        return result;
    }

    private static Component skillCategory(SkillDefinition skill) {
        Component category = EssenceRegistry.get(skill.essenceId()).map(EssenceText::essenceShort)
                .map(Component.class::cast).orElseGet(() -> Component.literal(skill.essenceId().getPath()));
        return category.copy().withStyle(style -> style.withColor(
                AscendancePalette.categoryRgb(skill.essenceId())));
    }

    private static Component essenceAmount(SkillDefinition skill, long value) {
        return Component.literal(Long.toString(value)).withStyle(style -> style.withColor(
                AscendancePalette.categoryRgb(skill.essenceId())));
    }

    private static Component essenceAmount(StatDefinition stat, long value) {
        return Component.literal(Long.toString(value)).withStyle(style -> style.withColor(
                AscendancePalette.categoryRgb(stat.category())));
    }

    private static <R, T> ReadOnlyDataTable.Column<R, T> column(
            String id, Component header, int minimumWidth, int weight,
            ReadOnlyDataTable.Alignment alignment, java.util.function.Function<R, T> value,
            java.util.function.Function<T, Component> presentation) {
        return new ReadOnlyDataTable.Column<>(id, header, minimumWidth, weight,
                alignment, value, presentation, null);
    }

    static SemanticDocument mechanicsReference() {
        String key = "archive.entry.reference.mechanics.status";
        return new SemanticDocument(g(key + ".title"), List.of(
                new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, g(key + ".heading")),
                new SemanticDocument.Paragraph(g(key + ".body")),
                new SemanticDocument.Requirements(List.of(
                        new SemanticDocument.Requirement(g(key + ".row.server"), g(key + ".row.server.detail"),
                                SemanticDocument.RequirementStatus.INFORMATION),
                        new SemanticDocument.Requirement(g(key + ".row.client"), g(key + ".row.client.detail"),
                                SemanticDocument.RequirementStatus.INFORMATION)))));
    }

    private static Component g(String path, Object... args) { return EssenceText.guide(path, args); }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }
}
