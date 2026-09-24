package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                new SemanticDocument.Links(List.of(
                        new SemanticDocument.Link(id("guide/beginning/welcome").toString(),
                                g("archive.entry.guide.beginning.welcome.title"), SemanticDocument.LinkRelation.PREVIOUS),
                        new SemanticDocument.Link(id("guide/machines/first_network").toString(),
                                g("archive.entry.guide.machines.first_network.title"), SemanticDocument.LinkRelation.NEXT),
                        new SemanticDocument.Link(id("reference/essences/overview").toString(),
                                g("archive.entry.reference.essences.overview.title"), SemanticDocument.LinkRelation.RELATED)))));
    }

    static SemanticDocument infusionGuide() {
        String key = "archive.entry.guide.infusion.first_focus";
        return new SemanticDocument(g(key + ".title"), List.of(
                new SemanticDocument.Paragraph(g(key + ".body")),
                new SemanticDocument.Illustration(id("essence_infuser"), g(key + ".caption"), 140, 82),
                new SemanticDocument.Illustration(id("essence_focus"), g(key + ".focus_caption"), 108, 68),
                new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"), g(key + ".note"))));
    }

    static SemanticDocument bonusesReference() {
        String key = "archive.entry.reference.bonuses.overview";
        return new SemanticDocument(g(key + ".title"), List.of(
                new SemanticDocument.Paragraph(g(key + ".body")),
                new SemanticDocument.StatRows(List.of(
                        new SemanticDocument.StatRow(g(key + ".row.investment"), g(key + ".row.investment.value")),
                        new SemanticDocument.StatRow(g(key + ".row.application"), g(key + ".row.application.value")))),
                new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"), g(key + ".note"))));
    }

    private record SkillRankRow(Integer rank, Long cost, Double power) { }

    static SemanticDocument skillRanksReference() {
        String key = "archive.entry.reference.skills.ranks";
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.Paragraph(g(key + ".body")));
        SkillBalanceRuntime.ResolvedSkill curve = SkillBalanceRuntime.snapshot().get(SkillIds.RUNNING_MOMENTUM.toString());
        if (curve == null) {
            blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE,
                    g("archive.callout.note"), g(key + ".unavailable")));
        } else {
            List<ReadOnlyDataTable.Row<SkillRankRow>> rows = curve.ranks().stream()
                    .limit(curve.maximumRank())
                    .map(rank -> new ReadOnlyDataTable.Row<>("rank/" + rank.rank(),
                            new SkillRankRow(rank.rank(), rank.cost(), rank.powerMultiplier())))
                    .toList();
            ReadOnlyDataTable<SkillRankRow> table = new ReadOnlyDataTable<>(List.of(
                    ReadOnlyDataTable.Column.number("rank", g("archive.table.rank"), 46, 0,
                            SkillRankRow::rank, value -> g("archive.value.number", value)),
                    ReadOnlyDataTable.Column.number("cost", g("archive.table.cost"), 76, 1,
                            SkillRankRow::cost, value -> g("archive.value.cost", value)),
                    ReadOnlyDataTable.Column.number("power", g("archive.table.power"), 76, 1,
                            SkillRankRow::power, value -> g("archive.value.multiplier",
                                    String.format(Locale.ROOT, "%.2f", value)))
            ), rows);
            blocks.add(new SemanticDocument.Table<>(table));
        }
        blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE,
                g("archive.callout.note"), g(key + ".authority")));
        return new SemanticDocument(g(key + ".title"), blocks);
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
