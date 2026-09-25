package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.EquipmentPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.EssencePresentationData;
import com.mistaboom.essence_ascendance.client.presentation.MachinePresentationData;
import com.mistaboom.essence_ascendance.client.presentation.MechanicsPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.client.ui.content.ItemPresentation;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Player curriculum assembled from the same registry/runtime projections as the exhaustive Reference. */
final class GuideDocuments {
    private static final int HERO_WIDTH = 140;
    private static final int HERO_HEIGHT = 82;
    private GuideDocuments() { }

    private record MachineRow(ItemPresentation item, Component purpose, Component requirement) { }
    private record FocusRow(Component focus, Component operation, Component consequence) { }
    private record ChapterRow(Component transition, Component seals) { }
    private record ActivationRow(Component type, Component behavior) { }

    static SemanticDocument beginning(PresentationContext context) {
        MechanicsPresentationData.Projection progression = MechanicsPresentationData.project(context);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(itemFigure("latent_ore", "archive.guide.beginning.ore_caption", HERO_WIDTH, HERO_HEIGHT));
        blocks.add(paragraph("archive.guide.beginning.intro"));
        blocks.add(heading("archive.guide.beginning.path"));
        blocks.add(new SemanticDocument.OrderedSteps(List.of(
                g("archive.guide.beginning.step.find"),
                g("archive.guide.beginning.step.unlock"),
                g("archive.guide.beginning.step.nexus"),
                g("archive.guide.beginning.step.seals"),
                g("archive.guide.beginning.step.ascend"))));
        if (!progression.ready()) {
            blocks.add(runtimeState(progression.availability()));
        } else if (!progression.attunementChapters().isEmpty()) {
            var first = progression.attunementChapters().getFirst().chapter();
            blocks.add(new SemanticDocument.Requirements(List.of(
                    info("archive.guide.beginning.first_chapter",
                            g("archive.guide.beginning.first_chapter.detail",
                                    tierName(first.fromTierId()), tierName(first.toTierId()),
                                    first.requiredCategories(), first.categories().size())),
                    info("archive.guide.beginning.no_old_gates", g("archive.guide.beginning.no_old_gates.detail")))));
        }
        blocks.add(new SemanticDocument.ItemIllustration(new ItemPresentation(id("ascendance_archive"),
                DataComponentPatch.EMPTY, itemName("ascendance_archive"),
                g("archive.guide.beginning.archive_caption")), 124, 68));
        blocks.add(heading("archive.guide.beginning.delivery"));
        blocks.add(new SemanticDocument.Requirements(List.of(
                info("archive.guide.beginning.delivery.when", g("archive.guide.beginning.delivery.when.detail")),
                info("archive.guide.beginning.delivery.where", g("archive.guide.beginning.delivery.where.detail")),
                info("archive.guide.beginning.first_actions", g("archive.guide.beginning.first_actions.detail")))));
        blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.TIP, g("archive.callout.tip"),
                g("archive.guide.beginning.tip")));
        seeAlso(blocks, link("guide/essence/collecting", "archive.entry.guide.essence.collecting.title",
                        SemanticDocument.LinkRelation.NEXT),
                link("reference/mechanics/attunement", "archive.entry.reference.mechanics.attunement.title",
                        SemanticDocument.LinkRelation.RELATED),
                link("reference/machines/nexus", "archive.entry.reference.machines.nexus.title",
                        SemanticDocument.LinkRelation.RELATED));
        return document("archive.entry.guide.beginning.welcome.title", blocks);
    }

    static SemanticDocument essence() {
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        List<ItemPresentation> categories = EssenceRegistry.values().stream()
                .map(essence -> EssencePresentationData.carrierModels(essence, id("essentium_ingot")).getFirst()
                        .withCaption(Component.empty())).toList();
        blocks.add(new SemanticDocument.ItemRow(categories));
        blocks.add(paragraph("archive.guide.essence.intro"));
        blocks.add(heading("archive.guide.essence.obtain"));
        blocks.add(new SemanticDocument.OrderedSteps(List.of(
                g("archive.guide.essence.step.lookup"),
                g("archive.guide.essence.step.dissolve"),
                g("archive.guide.essence.step.channel"),
                g("archive.guide.essence.step.spend"))));
        blocks.add(heading("archive.guide.essence.flow"));
        blocks.add(new SemanticDocument.Requirements(List.of(
                info("archive.guide.essence.reservoir", g("archive.guide.essence.reservoir.detail")),
                info("archive.guide.essence.available", g("archive.guide.essence.available.detail")),
                info("archive.guide.essence.invested", g("archive.guide.essence.invested.detail")),
                info("archive.guide.essence.restrictions", g("archive.guide.essence.restrictions.detail")))));
        blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.WARNING, g("archive.callout.warning"),
                g("archive.guide.essence.failure")));
        blocks.add(heading("archive.guide.essence.categories"));
        blocks.add(new SemanticDocument.Links(EssenceRegistry.values().stream().map(essence ->
                new SemanticDocument.Link(id("reference/essences/" + essence.id().getPath()).toString(),
                        EssenceText.essenceShort(essence).withColor(AscendancePalette.categoryRgb(essence)),
                        SemanticDocument.LinkRelation.RELATED)).toList()));
        seeAlso(blocks, link("guide/beginning/welcome", "archive.entry.guide.beginning.welcome.title",
                        SemanticDocument.LinkRelation.PREVIOUS),
                link("guide/machines/first_network", "archive.entry.guide.machines.first_network.title",
                        SemanticDocument.LinkRelation.NEXT),
                link("reference/essences/item_yields", "archive.entry.reference.essences.item_yields.title",
                        SemanticDocument.LinkRelation.RELATED),
                link("reference/mechanics/allocation_storage", "archive.entry.reference.mechanics.allocation_storage.title",
                        SemanticDocument.LinkRelation.RELATED));
        return document("archive.entry.guide.essence.collecting.title", blocks);
    }

    static SemanticDocument machines(PresentationContext context) {
        MachinePresentationData.Projection data = MachinePresentationData.project(context);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.ItemRow(List.of(
                blockItem("channelstone", "archive.guide.machines.caption.channelstone"),
                blockItem("essence_crucible", "archive.guide.machines.caption.crucible"),
                blockItem("essence_pylon", "archive.guide.machines.caption.pylon"),
                MachinePresentationData.focusModel(null, g("archive.guide.machines.caption.focus")),
                blockItem("essence_infuser", "archive.guide.machines.caption.infuser"),
                blockItem("ascendance_nexus", "archive.guide.machines.caption.nexus"))));
        blocks.add(paragraph("archive.guide.machines.intro"));
        blocks.add(heading("archive.guide.machines.roles"));
        blocks.add(machineTable());
        if (!data.ready()) {
            blocks.add(runtimeState(data.availability()));
        } else {
            blocks.add(heading("archive.guide.machines.linked_setup"));
            blocks.add(new SemanticDocument.Requirements(List.of(
                    info("archive.guide.machines.claim", g("archive.guide.machines.claim.detail")),
                    info("archive.guide.machines.pylon_link", g("archive.guide.machines.pylon_link.detail",
                            decimal(data.pylonLinkRadius()), data.maximumActivePylons())),
                    info("archive.guide.machines.infuser_link", g("archive.guide.machines.infuser_link.detail",
                            decimal(data.infuserLinkRange()))),
                    info("archive.guide.machines.channelstone", g("archive.guide.machines.channelstone.detail")))));
            blocks.add(heading("archive.guide.machines.focus_states"));
            blocks.add(new SemanticDocument.ItemRow(List.of(
                    MachinePresentationData.focusModel(null, g("archive.guide.machines.focus.latent.caption")),
                    MachinePresentationData.focusModel(EssenceFocusTier.DORMANT,
                            g("archive.guide.machines.focus.dormant.caption")),
                    MachinePresentationData.focusModel(EssenceFocusTier.TRANSCENDENT,
                            g("archive.guide.machines.focus.transcendent.caption")))));
            blocks.add(focusStates(data));
        }
        blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.WARNING, g("archive.callout.warning"),
                g("archive.guide.machines.failure")));
        seeAlso(blocks, link("guide/essence/collecting", "archive.entry.guide.essence.collecting.title",
                        SemanticDocument.LinkRelation.PREVIOUS),
                link("guide/infusion/first_focus", "archive.entry.guide.infusion.first_focus.title",
                        SemanticDocument.LinkRelation.NEXT),
                link("reference/machines/focus", "archive.entry.reference.machines.focus.title",
                        SemanticDocument.LinkRelation.RELATED));
        return document("archive.entry.guide.machines.first_network.title", blocks);
    }

    static SemanticDocument infusion(PresentationContext context) {
        EquipmentPresentationData.Projection equipment = EquipmentPresentationData.project(context);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.ItemIllustration(blockItem("essence_infuser",
                "archive.guide.infusion.infuser_caption"), HERO_WIDTH, HERO_HEIGHT));
        blocks.add(paragraph("archive.guide.infusion.intro"));
        blocks.add(heading("archive.guide.infusion.operations"));
        blocks.add(new SemanticDocument.OrderedSteps(List.of(
                g("archive.guide.infusion.step.link"),
                g("archive.guide.infusion.step.focus"),
                g("archive.guide.infusion.step.workpiece"),
                g("archive.guide.infusion.step.materials"),
                g("archive.guide.infusion.step.complete"))));
        blocks.add(new SemanticDocument.ItemRow(List.of(
                EquipmentPresentationData.itemModel(id("ascendance_chestplate"), EquipmentTier.LATENT)
                        .withCaption(g("archive.guide.infusion.equipment.latent.caption")),
                EquipmentPresentationData.itemModel(id("ascendance_chestplate"), EquipmentTier.DORMANT)
                        .withCaption(g("archive.guide.infusion.equipment.dormant.caption")),
                EquipmentPresentationData.itemModel(id("ascendance_chestplate"), EquipmentTier.TRANSCENDENT)
                        .withCaption(g("archive.guide.infusion.equipment.transcendent.caption")))));
        blocks.add(heading("archive.guide.infusion.equipment"));
        blocks.add(new SemanticDocument.Requirements(List.of(
                info("archive.guide.infusion.compatibility", g("archive.guide.infusion.compatibility.detail")),
                info("archive.guide.infusion.progress", g("archive.guide.infusion.progress.detail")),
                keywordInfo("archive.guide.infusion.soulbound", "archive.guide.infusion.soulbound.detail",
                        AscendanceUiPalette.SOULBOUND),
                info("archive.guide.infusion.ascension_costs", g("archive.guide.infusion.ascension_costs.detail")))));
        blocks.add(headingComponent(g("archive.guide.infusion.fracture").copy()
                .withColor(AscendanceUiPalette.FRACTURED)));
        if (!equipment.ready()) {
            blocks.add(runtimeState(equipment.availability()));
        } else {
            blocks.add(new SemanticDocument.Requirements(List.of(
                    keywordInfo("archive.guide.infusion.fractured", "archive.guide.infusion.fractured.detail",
                            AscendanceUiPalette.FRACTURED),
                    info("archive.guide.infusion.repair", g("archive.guide.infusion.repair.detail",
                            equipment.repairEssencePerDurability(), equipment.fracturedLatentIngotCount())),
                    info("archive.guide.infusion.recovery", g("archive.guide.infusion.recovery.detail",
                            g("archive.guide.infusion.soulbound").copy().withColor(AscendanceUiPalette.SOULBOUND))),
                    info("archive.guide.infusion.enchantments", g("archive.guide.infusion.enchantments.detail")),
                    info("archive.guide.infusion.reinforcement", g("archive.guide.infusion.reinforcement.detail",
                            ArchiveText.term("skill:masterwork_tempering"))),
                    new SemanticDocument.Requirement(ArchiveText.term("bonus:durability_efficiency"),
                            g("archive.guide.infusion.durability_efficiency.detail"),
                            SemanticDocument.RequirementStatus.INFORMATION))));
        }
        blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.WARNING, g("archive.callout.warning"),
                g("archive.guide.infusion.failure")));
        seeAlso(blocks, link("guide/machines/first_network", "archive.entry.guide.machines.first_network.title",
                        SemanticDocument.LinkRelation.PREVIOUS),
                link("guide/ascendance/nexus", "archive.entry.guide.ascendance.nexus.title",
                        SemanticDocument.LinkRelation.NEXT),
                link("reference/equipment/infusion", "archive.entry.reference.equipment.infusion.title",
                        SemanticDocument.LinkRelation.RELATED),
                link("reference/equipment/lifecycle", "archive.entry.reference.equipment.lifecycle.title",
                        SemanticDocument.LinkRelation.RELATED),
                new SemanticDocument.Link(id("reference/bonuses/" + EssenceStats.DURABILITY_EFFICIENCY.id().getPath()).toString(),
                        ArchiveText.term("bonus:durability_efficiency"), SemanticDocument.LinkRelation.RELATED),
                new SemanticDocument.Link(id("reference/skills/" + SkillIds.MASTERWORK_TEMPERING.getPath()).toString(),
                        ArchiveText.term("skill:masterwork_tempering"), SemanticDocument.LinkRelation.RELATED));
        return document("archive.entry.guide.infusion.first_focus.title", blocks);
    }

    static SemanticDocument ascendance(PresentationContext context) {
        MechanicsPresentationData.Projection data = MechanicsPresentationData.project(context);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.ItemIllustration(blockItem("ascendance_nexus",
                "archive.guide.ascendance.nexus_caption"), HERO_WIDTH, HERO_HEIGHT));
        blocks.add(paragraph("archive.guide.ascendance.intro"));
        blocks.add(heading("archive.guide.ascendance.path"));
        blocks.add(new SemanticDocument.OrderedSteps(List.of(
                g("archive.guide.ascendance.step.chapter"),
                g("archive.guide.ascendance.step.activity"),
                g("archive.guide.ascendance.step.variety"),
                g("archive.guide.ascendance.step.seals"),
                g("archive.guide.ascendance.step.nexus"))));
        if (!data.ready()) {
            blocks.add(runtimeState(data.availability()));
        } else {
            List<ReadOnlyDataTable.Row<ChapterRow>> chapters = data.attunementChapters().stream().map(value -> {
                var chapter = value.chapter();
                return new ReadOnlyDataTable.Row<>(chapter.id(), new ChapterRow(
                        g("archive.value.tier_transition", tierName(chapter.fromTierId()), tierName(chapter.toTierId())),
                        g("archive.guide.ascendance.seal_count", chapter.requiredCategories(), chapter.categories().size())));
            }).toList();
            blocks.add(guideTable(List.of(
                    textColumn("transition", g("archive.guide.ascendance.table.transition"), 150, 1, ChapterRow::transition),
                    textColumn("seals", g("archive.guide.ascendance.table.seals"), 120, 1, ChapterRow::seals)), chapters));
            blocks.add(heading("archive.guide.ascendance.activities"));
            Map<String, MechanicsPresentationData.AttunementActivity> activities = new LinkedHashMap<>();
            data.attunementChapters().stream().findFirst().ifPresent(chapter -> chapter.activities()
                    .forEach(activity -> activities.putIfAbsent(activity.method().activityId(), activity)));
            blocks.add(new SemanticDocument.Requirements(activities.values().stream().map(activity ->
                    new SemanticDocument.Requirement(
                            Component.translatable(activity.method().labelKey()).withColor(AscendancePalette.categoryRgb(
                                    ResourceLocation.parse(activity.method().categoryId()))),
                            Component.translatable(activity.method().descriptionKey()),
                            SemanticDocument.RequirementStatus.INFORMATION)).toList()));
            blocks.add(new SemanticDocument.Paragraph(g("archive.guide.ascendance.acceleration",
                    decimal(MechanicsPresentationData.maximumInvestmentMultiplier(data.attunementPolicy())))));
        }
        blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.WARNING, g("archive.callout.warning"),
                g("archive.guide.ascendance.failure")));
        blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"),
                g("archive.guide.ascendance.equipment_distinction")));
        seeAlso(blocks, link("guide/infusion/first_focus", "archive.entry.guide.infusion.first_focus.title",
                        SemanticDocument.LinkRelation.PREVIOUS),
                link("guide/skills/choosing", "archive.entry.guide.skills.choosing.title",
                        SemanticDocument.LinkRelation.NEXT),
                link("reference/mechanics/attunement", "archive.entry.reference.mechanics.attunement.title",
                        SemanticDocument.LinkRelation.RELATED));
        return document("archive.entry.guide.ascendance.nexus.title", blocks);
    }

    static SemanticDocument skills() {
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.ItemIllustration(blockItem("ascendance_nexus",
                "archive.guide.skills.nexus_caption"), HERO_WIDTH, HERO_HEIGHT));
        blocks.add(paragraph("archive.guide.skills.intro"));
        blocks.add(heading("archive.guide.skills.buying"));
        blocks.add(new SemanticDocument.OrderedSteps(List.of(
                g("archive.guide.skills.step.category"),
                g("archive.guide.skills.step.inspect"),
                g("archive.guide.skills.step.purchase"),
                g("archive.guide.skills.step.review"),
                g("archive.guide.skills.step.apply"))));
        List<ReadOnlyDataTable.Row<ActivationRow>> activationRows = java.util.Arrays.stream(SkillActivationPolicy.values())
                .map(policy -> new ReadOnlyDataTable.Row<>(policy.name().toLowerCase(Locale.ROOT), new ActivationRow(
                        Component.translatable(policy.translationKey()),
                        g("archive.guide.skills.activation." + policy.name().toLowerCase(Locale.ROOT))))).toList();
        blocks.add(heading("archive.guide.skills.activation"));
        blocks.add(guideTable(List.of(
                textColumn("type", g("archive.guide.skills.table.type"), 104, 0, ActivationRow::type),
                textColumn("behavior", g("archive.guide.skills.table.behavior"), 190, 1, ActivationRow::behavior)), activationRows));
        blocks.add(heading("archive.guide.skills.ranks"));
        blocks.add(new SemanticDocument.Requirements(List.of(
                info("archive.guide.skills.rank_cost", g("archive.guide.skills.rank_cost.detail")),
                info("archive.guide.skills.rank_requirements", g("archive.guide.skills.rank_requirements.detail")),
                info("archive.guide.skills.prerequisites", g("archive.guide.skills.prerequisites.detail")),
                info("archive.guide.skills.choices", g("archive.guide.skills.choices.detail")),
                info("archive.guide.skills.replacements", g("archive.guide.skills.replacements.detail")))));
        blocks.add(heading("archive.guide.skills.controls"));
        blocks.add(new SemanticDocument.Requirements(List.of(
                info("archive.guide.skills.hover", g("archive.guide.skills.hover.detail")),
                info("archive.guide.skills.shift", g("archive.guide.skills.shift.detail")),
                info("archive.guide.skills.click", g("archive.guide.skills.click.detail")),
                info("archive.guide.skills.hud", g("archive.guide.skills.hud.detail")),
                info("archive.guide.skills.plan", g("archive.guide.skills.plan.detail")))));
        blocks.add(new SemanticDocument.Callout(SemanticDocument.CalloutKind.WARNING, g("archive.callout.warning"),
                g("archive.guide.skills.failure")));
        seeAlso(blocks, link("guide/ascendance/nexus", "archive.entry.guide.ascendance.nexus.title",
                        SemanticDocument.LinkRelation.PREVIOUS),
                link("reference/machines/nexus", "archive.entry.reference.machines.nexus.title",
                        SemanticDocument.LinkRelation.RELATED),
                link("reference/mechanics/status", "archive.entry.reference.mechanics.status.title",
                        SemanticDocument.LinkRelation.RELATED));
        return document("archive.entry.guide.skills.choosing.title", blocks);
    }

    private static SemanticDocument.Table<MachineRow> machineTable() {
        List<ReadOnlyDataTable.Row<MachineRow>> rows = List.of(
                machineRow("channelstone", "archive.guide.machines.role.channelstone", "archive.guide.machines.need.channelstone"),
                machineRow("essence_crucible", "archive.guide.machines.role.crucible", "archive.guide.machines.need.crucible"),
                machineRow("essence_pylon", "archive.guide.machines.role.pylon", "archive.guide.machines.need.pylon"),
                new ReadOnlyDataTable.Row<>("focus", new MachineRow(
                        MachinePresentationData.focusModel(null, Component.empty()),
                        g("archive.guide.machines.role.focus"), g("archive.guide.machines.need.focus")),
                        id("reference/machines/focus").toString()),
                machineRow("essence_infuser", "archive.guide.machines.role.infuser", "archive.guide.machines.need.infuser"),
                machineRow("ascendance_nexus", "archive.guide.machines.role.nexus", "archive.guide.machines.need.nexus"));
        return guideTable(List.of(
                ReadOnlyDataTable.Column.item("machine", g("archive.guide.machines.table.machine"), 50, MachineRow::item),
                textColumn("purpose", g("archive.guide.machines.table.purpose"), 125, 1, MachineRow::purpose),
                textColumn("requirement", g("archive.guide.machines.table.requirement"), 150, 1, MachineRow::requirement)), rows);
    }

    private static ReadOnlyDataTable.Row<MachineRow> machineRow(String item, String role, String need) {
        String reference = item.equals("ascendance_nexus") ? "nexus" : item.replace("essence_", "");
        return new ReadOnlyDataTable.Row<>(item, new MachineRow(
                blockItem(item, "archive.guide.machines.caption." + reference).withCaption(Component.empty()),
                g(role), g(need)), id("reference/machines/" + reference).toString());
    }

    private static SemanticDocument.Table<FocusRow> focusStates(MachinePresentationData.Projection data) {
        var none = data.focusProfiles().stream().filter(value -> value.state() == MachinePresentationData.FocusState.NONE)
                .findFirst().orElseThrow();
        var latent = data.focusProfiles().stream().filter(value -> value.state() == MachinePresentationData.FocusState.LATENT)
                .findFirst().orElseThrow();
        var upgraded = data.focusProfiles().stream().filter(value -> value.state() == MachinePresentationData.FocusState.UPGRADED)
                .findFirst().orElseThrow();
        List<ReadOnlyDataTable.Row<FocusRow>> rows = List.of(
                focusRow("none", g("archive.value.none"), none.pylonOperational(), "archive.guide.machines.focus.none"),
                focusRow("latent", equipmentTier(EquipmentTier.LATENT), latent.pylonOperational(),
                        "archive.guide.machines.focus.latent"),
                focusRow("upgraded", g("archive.guide.machines.focus.upgraded_label",
                                focusTier(upgraded.completedTier()), focusTier(EssenceFocusTier.TRANSCENDENT)),
                        upgraded.pylonOperational(), "archive.guide.machines.focus.upgraded"));
        return guideTable(List.of(
                textColumn("focus", g("archive.table.focus_state"), 108, 0, FocusRow::focus),
                textColumn("operation", g("archive.table.operates"), 70, 0, FocusRow::operation),
                textColumn("consequence", g("archive.guide.machines.table.consequence"), 180, 1,
                        FocusRow::consequence)), rows);
    }

    private static ReadOnlyDataTable.Row<FocusRow> focusRow(String key, Component label, boolean operates,
                                                             String consequence) {
        return new ReadOnlyDataTable.Row<>(key, new FocusRow(label,
                g(operates ? "archive.value.yes" : "archive.value.no"), g(consequence)));
    }

    private static SemanticDocument document(String title, List<SemanticDocument.Block> blocks) {
        return new SemanticDocument(g(title), blocks);
    }

    private static SemanticDocument.Paragraph paragraph(String key) {
        return new SemanticDocument.Paragraph(g(key));
    }

    private static SemanticDocument.Heading heading(String key) {
        return headingComponent(g(key));
    }

    private static SemanticDocument.Heading headingComponent(Component component) {
        return new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, component);
    }

    private static SemanticDocument.Requirement info(String labelKey, Component detail) {
        return new SemanticDocument.Requirement(g(labelKey), detail, SemanticDocument.RequirementStatus.INFORMATION);
    }

    private static SemanticDocument.Requirement keywordInfo(String labelKey, String detailKey, int color) {
        Component keyword = g(labelKey).copy().withColor(color);
        return new SemanticDocument.Requirement(keyword, g(detailKey, keyword),
                SemanticDocument.RequirementStatus.INFORMATION);
    }

    private static SemanticDocument.Callout runtimeState(PresentationContext.Availability state) {
        return new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"),
                g("archive.reference.runtime." + state.name().toLowerCase(Locale.ROOT)));
    }

    private static SemanticDocument.ItemIllustration itemFigure(String item, String caption, int width, int height) {
        return new SemanticDocument.ItemIllustration(blockItem(item, caption), width, height);
    }

    private static ItemPresentation blockItem(String path, String caption) {
        return new ItemPresentation(id(path), DataComponentPatch.EMPTY, blockName(path), g(caption));
    }

    @SafeVarargs
    private static void seeAlso(List<SemanticDocument.Block> blocks, SemanticDocument.Link... links) {
        blocks.add(heading("archive.see_also"));
        blocks.add(new SemanticDocument.Links(List.of(links)));
    }

    private static SemanticDocument.Link link(String path, String label, SemanticDocument.LinkRelation relation) {
        return new SemanticDocument.Link(id(path).toString(), g(label), relation);
    }

    private static <R> SemanticDocument.Table<R> guideTable(List<ReadOnlyDataTable.Column<R, ?>> columns,
                                                             List<ReadOnlyDataTable.Row<R>> rows) {
        return new SemanticDocument.Table<>(new ReadOnlyDataTable<>(columns, rows),
                SemanticDocument.TableLayoutPolicy.ARTICLE_FLOW);
    }

    private static <R> ReadOnlyDataTable.Column<R, Component> textColumn(String id, Component header, int width,
                                                                         int weight,
                                                                         java.util.function.Function<R, Component> value) {
        return ReadOnlyDataTable.Column.text(id, header, width, weight, value, null);
    }

    private static Component tierName(String id) { return SkillPresentationData.tierName(ResourceLocation.parse(id)); }
    private static Component equipmentTier(EquipmentTier tier) {
        return EssenceText.equipmentTier(tier).withColor(AscendancePalette.tierMetalRgb(tier));
    }
    private static Component focusTier(EssenceFocusTier tier) {
        return EssenceText.focusTier(tier).withColor(AscendancePalette.tierMetalRgb(
                ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, tier.serializedName())));
    }
    private static String decimal(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
    private static Component blockName(String path) {
        return Component.translatable("block." + EssenceAscendance.MOD_ID + "." + path);
    }
    private static Component itemName(String path) {
        return Component.translatable("item." + EssenceAscendance.MOD_ID + "." + path);
    }
    private static Component g(String key, Object... args) { return ArchiveText.guide(key, args); }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }
}
