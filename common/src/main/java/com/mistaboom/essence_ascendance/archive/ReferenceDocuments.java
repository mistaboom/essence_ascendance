package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.EquipmentPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.EssencePresentationData;
import com.mistaboom.essence_ascendance.client.presentation.MachinePresentationData;
import com.mistaboom.essence_ascendance.client.presentation.MechanicsPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.ui.content.ItemPresentation;
import com.mistaboom.essence_ascendance.equipment.EquipmentActivationType;
import com.mistaboom.essence_ascendance.equipment.ShieldMath;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.world.entity.EquipmentSlot;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Remaining registry/runtime-backed Reference articles. */
final class ReferenceDocuments {
    private ReferenceDocuments() { }

    private record Cells(Component first, Component second, Component third, Component fourth,
                         Component fifth, Component sixth) { }

    static SemanticDocument essence(EssenceDefinition essence) {
        EssencePresentationData.Projection value = EssencePresentationData.project(essence);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        for (String form : List.of("essentium_nugget", "essentium_ingot", "essentium_block"))
            blocks.add(new SemanticDocument.ItemRow(EssencePresentationData.carrierModels(essence, id(form))));
        List<ReadOnlyDataTable.Row<Cells>> bonusRows = value.bonuses().stream().map(stat ->
                linkedRow(stat.id().toString(), "reference/bonuses/" + stat.id().getPath(),
                        BonusPresentationData.name(stat),
                        g("archive.value.unit." + stat.unit().name().toLowerCase(Locale.ROOT)))).toList();
        blocks.add(heading("archive.reference.essence.bonuses"));
        blocks.add(table(List.of(textColumn("name", g("archive.table.bonus"), 125, 1, Cells::first),
                textColumn("unit", g("archive.reference.unit"), 116, 0, Cells::second).keepValuesTogether()), bonusRows));
        List<ReadOnlyDataTable.Row<Cells>> skillRows = value.skills().stream().map(skill ->
                linkedRow(skill.id().toString(), "reference/skills/" + skill.id().getPath(),
                        SkillPresentationData.skillName(skill),
                        Component.translatable("skill_activation.essence_ascendance."
                                + skill.activationPolicy().name().toLowerCase(Locale.ROOT)))).toList();
        blocks.add(heading("archive.reference.essence.skills"));
        blocks.add(table(List.of(textColumn("name", g("archive.table.skill"), 132, 1, Cells::first),
                textColumn("activation", g("archive.reference.activation"), 86, 0, Cells::second)), skillRows));
        return new SemanticDocument(value.name(), blocks);
    }

    static SemanticDocument itemYieldsDestination() {
        return new SemanticDocument(g("archive.entry.reference.essences.item_yields.title"), List.of(
                new SemanticDocument.Icon(id("ascendance_archive"), g("archive.entry.reference.essences.item_yields.title")),
                new SemanticDocument.Paragraph(g("archive.reference.item_yields.body")),
                new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"),
                        g("archive.reference.item_yields.essentium_explanation")),
                new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, g("archive.see_also")),
                new SemanticDocument.Links(List.of(new SemanticDocument.Link(
                        "essence_ascendance:reference/machines/infuser",
                        g("archive.reference.item_yields.essentium_mechanic"),
                        SemanticDocument.LinkRelation.RELATED)))));
    }

    static SemanticDocument crucible(PresentationContext context) {
        MachinePresentationData.Projection data = MachinePresentationData.project(context);
        List<SemanticDocument.Block> blocks = machineIntro("crucible", "essence_crucible");
        if (!data.ready()) blocks.add(runtimeState(data.availability()));
        else {
            var base = data.crucibleBaseline();
            blocks.add(new SemanticDocument.StatRows(List.of(
                    stat("item_slots", base.usableItemSlots()), stat("reservoir_per_essence", base.reservoirCapacity()),
                    statDecimal("transfer_range", base.transferRange(), "blocks"), statRate("transfer_rate", base.transferRatePerSecond()),
                    statTicks("dissolution_time", base.dissolutionTicksPerItem()), stat("simultaneous", base.simultaneousItemProcesses()),
                    stat("automation_ports", base.automationConnectionPorts()), stat("active_pylons", base.activePylonCount()))));
            blocks.add(new SemanticDocument.Paragraph(g("archive.reference.machine.crucible.formula")));
            blocks.add(new SemanticDocument.Requirements(List.of(
                    info("ownership", "archive.reference.machine.crucible.ownership"),
                    info("inputs", "archive.reference.machine.crucible.inputs"),
                    info("automation", "archive.reference.machine.crucible.automation"),
                    info("failures", "archive.reference.machine.crucible.failures"))));
        }
        return new SemanticDocument(blockName("essence_crucible"), blocks);
    }

    static SemanticDocument pylon(PresentationContext context) {
        MachinePresentationData.Projection data = MachinePresentationData.project(context);
        List<SemanticDocument.Block> blocks = machineIntro("pylon", "essence_pylon");
        if (!data.ready()) blocks.add(runtimeState(data.availability()));
        else {
            blocks.add(new SemanticDocument.StatRows(List.of(
                    new SemanticDocument.StatRow(g("archive.reference.machine.pylon.link_radius"), decimal(data.pylonLinkRadius(), "blocks")),
                    new SemanticDocument.StatRow(g("archive.reference.machine.pylon.maximum_active"), integer(data.maximumActivePylons())))));
            blocks.add(focusTable(data, true));
            blocks.add(new SemanticDocument.Requirements(List.of(info("setup", "archive.reference.machine.pylon.setup"),
                    info("ownership", "archive.reference.machine.pylon.ownership"),
                    info("automation", "archive.reference.machine.pylon.automation"),
                    info("limits", "archive.reference.machine.pylon.limits"))));
        }
        return new SemanticDocument(blockName("essence_pylon"), blocks);
    }

    static SemanticDocument infuser(PresentationContext context) {
        MachinePresentationData.Projection data = MachinePresentationData.project(context);
        List<SemanticDocument.Block> blocks = machineIntro("infuser", "essence_infuser");
        if (!data.ready()) blocks.add(runtimeState(data.availability()));
        else {
            blocks.add(new SemanticDocument.StatRows(List.of(
                    new SemanticDocument.StatRow(g("archive.reference.machine.infuser.link_range"), decimal(data.infuserLinkRange(), "blocks")),
                    new SemanticDocument.StatRow(g("archive.reference.machine.infuser.conversion_efficiency"), basisPoints(data.conversionEfficiencyBasisPoints())),
                    new SemanticDocument.StatRow(g("archive.reference.machine.infuser.extraction_efficiency"), basisPoints(data.carrierExtractionEfficiencyBasisPoints())))));
            blocks.add(focusTable(data, false));
            blocks.add(new SemanticDocument.Paragraph(g("archive.reference.machine.infuser.timing_formula")));
            blocks.add(new SemanticDocument.Paragraph(g("archive.reference.machine.infuser.carrier_formula")));
            blocks.add(new SemanticDocument.Requirements(List.of(info("operations", "archive.reference.machine.infuser.operations"),
                    info("inputs", "archive.reference.machine.infuser.inputs"),
                    info("automation", "archive.reference.machine.infuser.automation"),
                    info("failures", "archive.reference.machine.infuser.failures"))));
        }
        return new SemanticDocument(blockName("essence_infuser"), blocks);
    }

    static SemanticDocument focus(PresentationContext context) {
        MachinePresentationData.Projection data = MachinePresentationData.project(context);
        List<SemanticDocument.Block> blocks = machineIntro("focus", "essence_focus");
        if (!data.ready()) blocks.add(runtimeState(data.availability()));
        else {
            blocks.add(heading("archive.reference.machine.focus.pylon_effects"));
            blocks.add(focusTable(data, true));
            blocks.add(heading("archive.reference.machine.focus.infuser_effects"));
            blocks.add(focusTable(data, false));
            List<ReadOnlyDataTable.Row<Cells>> rows = data.focusUpgrades().stream().map(value -> row(
                    value.targetTier().serializedName(), focusTier(value.targetTier()),
                    value.requiredInstalledTier() == null ? equipmentTier(EquipmentTier.LATENT) : focusTier(value.requiredInstalledTier()),
                    integer(value.minimumPerEssence()), integer(value.totalEssenceRequired()))).toList();
            blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, g("archive.reference.machine.focus.upgrades")));
            blocks.add(table(List.of(textColumn("target", g("archive.table.target"), 92, 1, Cells::first),
                    textColumn("installed", g("archive.table.installed_focus"), 92, 1, Cells::second),
                    textColumn("minimum", g("archive.table.minimum_each"), 80, 0, Cells::third),
                    textColumn("total", g("archive.table.total"), 60, 0, Cells::fourth)), rows));
        }
        return new SemanticDocument(itemName("essence_focus"), blocks);
    }

    static SemanticDocument nexus() { return staticMachine("nexus", "ascendance_nexus"); }
    static SemanticDocument channelstone() { return staticMachine("channelstone", "channelstone"); }

    private static SemanticDocument staticMachine(String key, String item) {
        List<SemanticDocument.Block> blocks = machineIntro(key, item);
        blocks.add(new SemanticDocument.Requirements(List.of(info("setup", "archive.reference.machine." + key + ".setup"),
                info("operation", "archive.reference.machine." + key + ".operation"),
                info("limits", "archive.reference.machine." + key + ".limits"))));
        return new SemanticDocument(item.equals("essence_focus") ? itemName(item) : blockName(item), blocks);
    }

    private record EquipmentRow(ItemPresentation item, List<Component> stats) { }

    static SemanticDocument equipmentProfile(ResourceLocation profileId, String item, PresentationContext context) {
        EquipmentPresentationData.Projection data = EquipmentPresentationData.project(context);
        EquipmentPresentationData.Profile profile = data.profiles().stream()
                .filter(value -> value.definition().id().equals(profileId)).findFirst().orElse(null);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.Paragraph(g("archive.reference.equipment.base_note")));
        if (!data.ready() || profile == null) blocks.add(runtimeState(data.availability()));
        else {
            boolean armor = profileId.equals(EquipmentProfiles.ARMOR.id());
            boolean shield = profileId.equals(EquipmentProfiles.SHIELD.id());
            if (armor) {
                for (var tier : data.tierBaselines()) {
                    blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, equipmentTier(tier.tier())));
                    List<ReadOnlyDataTable.Row<EquipmentRow>> rows = new ArrayList<>();
                    addArmorRow(rows, data, profile, tier, "ascendance_helmet", EquipmentSlot.HEAD);
                    addArmorRow(rows, data, profile, tier, "ascendance_chestplate", EquipmentSlot.CHEST);
                    addArmorRow(rows, data, profile, tier, "ascendance_leggings", EquipmentSlot.LEGS);
                    addArmorRow(rows, data, profile, tier, "ascendance_boots", EquipmentSlot.FEET);
                    blocks.add(equipmentTable(rows, false, List.of("armor", "toughness", "durability", "bonus_share")));
                }
                blocks.add(new SemanticDocument.Paragraph(g("archive.reference.equipment.armor.shares")));
            } else {
                blocks.add(heading("archive.reference.equipment.base_stats"));
                List<EquipmentBaselineProperty> properties = new ArrayList<>();
                List<String> headers = new ArrayList<>();
                if (!shield) {
                    if (profileId.equals(EquipmentProfiles.RANGED_WEAPON.id())) {
                        properties.addAll(List.of(EquipmentBaselineProperty.RANGED_DAMAGE, EquipmentBaselineProperty.RANGED_ATTACK_SPEED));
                        headers.addAll(List.of("ranged_damage", "draw_rate"));
                    } else if (profileId.equals(EquipmentProfiles.MAGIC_CASTER.id())) {
                        properties.addAll(List.of(EquipmentBaselineProperty.MAGIC_DAMAGE, EquipmentBaselineProperty.MAGIC_CAST_SPEED));
                        headers.addAll(List.of("magic_damage", "cast_rate"));
                    } else {
                        properties.addAll(List.of(EquipmentBaselineProperty.MELEE_DAMAGE, EquipmentBaselineProperty.MELEE_ATTACK_SPEED));
                        headers.addAll(List.of("attack_damage", "attack_speed"));
                    }
                }
                headers.add("durability");
                boolean tool = profile.baselineMultipliers().containsKey(EquipmentBaselineProperty.MINING_SPEED);
                if (tool) headers.addAll(List.of("mining_speed", "harvest_level"));
                if (shield) headers.addAll(List.of("passive_reflection", "reflect_amplification", "block_reflection"));
                List<ReadOnlyDataTable.Row<EquipmentRow>> rows = new ArrayList<>();
                for (var tier : data.tierBaselines()) {
                    List<Component> values = new ArrayList<>();
                    for (var property : properties)
                        values.add(decimal(EquipmentPresentationData.physicalValue(profile, tier, property,
                                data.quantizedBaselines(), null), "number"));
                    values.add(durability(profile, tier, id(item)));
                    if (tool) {
                        values.add(decimal(EquipmentPresentationData.physicalValue(profile, tier, EquipmentBaselineProperty.MINING_SPEED,
                                data.quantizedBaselines(), null), "number"));
                        values.add(integer(tier.baseline().harvestLevel()));
                    }
                    if (shield) {
                        values.add(decimal(tier.shieldNativeReflectionPercent(), "percent"));
                        values.add(decimal(tier.shieldBlockAmplification(), "multiplier"));
                        values.add(decimal(ShieldMath.blockedPercent(tier.shieldNativeReflectionPercent(), 0,
                                tier.shieldBlockAmplification()), "percent"));
                    }
                    rows.add(new ReadOnlyDataTable.Row<>(tier.tier().serializedName(),
                            new EquipmentRow(EquipmentPresentationData.itemModel(id(item), tier.tier())
                                    .withCaption(equipmentTier(tier.tier())), List.copyOf(values))));
                }
                blocks.add(equipmentTable(rows, true, headers));
                if (profileId.equals(EquipmentProfiles.RANGED_WEAPON.id()) || profileId.equals(EquipmentProfiles.MAGIC_CASTER.id()))
                    blocks.add(new SemanticDocument.Paragraph(g("archive.reference.equipment.action_timing")));
                if (tool) blocks.add(new SemanticDocument.Paragraph(g("archive.reference.equipment.harvest_note")));
                if (shield) {
                    blocks.add(heading("archive.reference.equipment.shield.reflection"));
                    blocks.add(new SemanticDocument.Requirements(List.of(
                            info("passive_reflection", "archive.reference.equipment.shield.passive"),
                            info("block_reflection", "archive.reference.equipment.shield.block"),
                            info("guarding", "archive.reference.equipment.shield.guarding"))));
                }
            }
            blocks.add(heading("archive.reference.equipment.applicability"));
            blocks.add(new SemanticDocument.Paragraph(g("archive.reference.equipment.bonus_note")));
            for (EquipmentActivationType activation : EquipmentActivationType.values()) {
                List<SemanticDocument.Link> links = profile.applicability().stream()
                        .filter(value -> value.activation() == activation).map(value -> {
                            var stat = EssenceStatRegistry.get(value.statId()).orElseThrow();
                            Component label = BonusPresentationData.name(stat);
                            if (value.strength() != 1) label = g("archive.value.bonus_strength", label, format(value.strength() * 100));
                            return new SemanticDocument.Link(id("reference/bonuses/" + stat.id().getPath()).toString(),
                                    label, SemanticDocument.LinkRelation.RELATED);
                        }).toList();
                if (!links.isEmpty()) {
                    blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SUBSECTION,
                            g("archive.value.activation." + activation.name().toLowerCase(Locale.ROOT))));
                    blocks.add(new SemanticDocument.Links(links));
                }
            }
            blocks.add(heading("archive.see_also"));
            blocks.add(new SemanticDocument.Links(List.of(
                    new SemanticDocument.Link(id("reference/equipment/infusion").toString(),
                            g("archive.entry.reference.equipment.infusion.title"), SemanticDocument.LinkRelation.RELATED),
                    new SemanticDocument.Link(id("reference/equipment/lifecycle").toString(),
                            g("archive.entry.reference.equipment.lifecycle.title"), SemanticDocument.LinkRelation.RELATED))));
        }
        return new SemanticDocument(profileTitle(profileId), blocks);
    }

    private static void addArmorRow(List<ReadOnlyDataTable.Row<EquipmentRow>> rows,
                                    EquipmentPresentationData.Projection data, EquipmentPresentationData.Profile profile,
                                    EquipmentPresentationData.TierBaseline tier, String item, EquipmentSlot slot) {
        rows.add(new ReadOnlyDataTable.Row<>(slot.getName(), new EquipmentRow(
                EquipmentPresentationData.itemModel(id(item), tier.tier()), List.of(
                decimal(EquipmentPresentationData.physicalValue(profile, tier, EquipmentBaselineProperty.ARMOR,
                        data.quantizedBaselines(), slot), "number"),
                decimal(EquipmentPresentationData.physicalValue(profile, tier, EquipmentBaselineProperty.TOUGHNESS,
                        data.quantizedBaselines(), slot), "number"),
                durability(profile, tier, id(item)),
                decimal(EquipmentPresentationData.wornShare(tier.tier(), slot) * 100, "percent")))));
    }

    private static Component durability(EquipmentPresentationData.Profile profile,
                                        EquipmentPresentationData.TierBaseline tier, ResourceLocation item) {
        var value = EquipmentPresentationData.nativeDurability(profile, tier, item);
        return value.isPresent() ? integer(value.getAsInt()) : g("archive.value.unavailable");
    }

    private static SemanticDocument.Table<EquipmentRow> equipmentTable(List<ReadOnlyDataTable.Row<EquipmentRow>> rows,
                                                                        boolean tierCaption, List<String> headers) {
        List<ReadOnlyDataTable.Column<EquipmentRow, ?>> columns = new ArrayList<>();
        columns.add(ReadOnlyDataTable.Column.item("item", Component.empty(), tierCaption ? 90 : 36, EquipmentRow::item));
        for (int index = 0; index < headers.size(); index++) {
            int cell = index;
            columns.add(ReadOnlyDataTable.Column.text(headers.get(index), g("archive.equipment.table." + headers.get(index)),
                    58, 1, row -> row.stats().get(cell), null));
        }
        return new SemanticDocument.Table<>(new ReadOnlyDataTable<>(columns, rows),
                SemanticDocument.TableLayoutPolicy.ARTICLE_FLOW);
    }

    static SemanticDocument equipmentInfusion(PresentationContext context) {
        EquipmentPresentationData.Projection data = EquipmentPresentationData.project(context);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.Illustration(id("essence_infuser"), g("archive.reference.equipment.infusion.caption"), 132, 76));
        blocks.add(new SemanticDocument.Paragraph(g("archive.reference.equipment.infusion.body")));
        if (!data.ready()) blocks.add(runtimeState(data.availability()));
        else {
            List<ReadOnlyDataTable.Row<Cells>> targets = data.infusionTargets().stream().map(value -> row(
                    value.tier().serializedName(), equipmentTier(value.tier()), integer(value.totalEssenceRequired()),
                    integer(value.matrixCount()))).toList();
            blocks.add(heading("archive.reference.equipment.ascension_costs"));
            blocks.add(table(List.of(textColumn("tier", g("archive.table.target"), 78, 0, Cells::first),
                    textColumn("essence", g("archive.table.total_essence"), 86, 0, Cells::second),
                    textColumn("matrices", g("archive.table.matrices"), 72, 0, Cells::third)), targets));
            List<ReadOnlyDataTable.Row<Cells>> weights = data.equipmentEssenceWeights().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(entry -> row(entry.getKey(), equipmentFamilyTitle(entry.getKey()),
                            join(entry.getValue().entrySet().stream().<Component>map(value ->
                                    g("archive.value.weight", essenceName(value.getKey()), value.getValue())).toList()))).toList();
            blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, g("archive.reference.equipment.essence_weights")));
            blocks.add(table(List.of(textColumn("family", g("archive.table.profile"), 100, 0, Cells::first),
                    textColumn("weights", g("archive.table.essence_weights"), 170, 1, Cells::second)), weights));
            blocks.add(new SemanticDocument.Requirements(List.of(info("compatibility", "archive.reference.equipment.infusion.compatibility"),
                    info("streaming", "archive.reference.equipment.infusion.streaming"),
                    info("soulbound", "archive.reference.equipment.infusion.soulbound"))));
        }
        return new SemanticDocument(g("archive.entry.reference.equipment.infusion.title"), blocks);
    }

    static SemanticDocument equipmentLifecycle(PresentationContext context) {
        EquipmentPresentationData.Projection data = EquipmentPresentationData.project(context);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.Illustration(id("ascendance_chestplate"), g("archive.reference.equipment.lifecycle.caption"), 120, 72));
        blocks.add(new SemanticDocument.Paragraph(g("archive.reference.equipment.lifecycle.body")));
        if (!data.ready()) blocks.add(runtimeState(data.availability()));
        else blocks.add(new SemanticDocument.StatRows(List.of(
                new SemanticDocument.StatRow(g("archive.reference.equipment.repair_cost"),
                        g("archive.reference.equipment.repair_cost.value", data.repairEssencePerDurability())),
                new SemanticDocument.StatRow(g("archive.reference.equipment.fractured_material"),
                        g("archive.reference.equipment.fractured_material.value", data.fracturedLatentIngotCount())))));
        blocks.add(new SemanticDocument.Requirements(List.of(
                info("fractured", "archive.reference.equipment.lifecycle.fractured"),
                info("repair", "archive.reference.equipment.lifecycle.repair"),
                info("durability", "archive.reference.equipment.lifecycle.durability"),
                info("soulbound", "archive.reference.equipment.lifecycle.soulbound"),
                info("death", "archive.reference.equipment.lifecycle.death"),
                info("enchantments", "archive.reference.equipment.lifecycle.enchantments"),
                info("exceptions", "archive.reference.equipment.lifecycle.exceptions"))));
        return new SemanticDocument(g("archive.entry.reference.equipment.lifecycle.title"), blocks);
    }

    static SemanticDocument mechanics(String topic, PresentationContext context) {
        MechanicsPresentationData.Projection data = MechanicsPresentationData.project(context);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.Icon(id("ascendance_nexus"), g("archive.entry.reference.mechanics." + topic + ".title")));
        blocks.add(new SemanticDocument.Paragraph(g("archive.reference.mechanics." + topic + ".body")));
        if (!data.ready()) blocks.add(runtimeState(data.availability()));
        else if (topic.equals("attunement")) {
            var policy = data.attunementPolicy();
            blocks.add(new SemanticDocument.StatRows(List.of(
                    new SemanticDocument.StatRow(g("archive.reference.mechanics.attunement.acceleration"),
                            decimal(MechanicsPresentationData.maximumInvestmentMultiplier(policy), "multiplier")),
                    new SemanticDocument.StatRow(g("archive.reference.mechanics.attunement.repetition_floor"), decimal(policy.repetitionFloor() * 100, "percent")),
                    new SemanticDocument.StatRow(g("archive.reference.mechanics.attunement.variety"), decimal(policy.varietyStrength() * 100, "percent")))));
            blocks.add(new SemanticDocument.Paragraph(g("archive.reference.mechanics.attunement.formula")));
            for (var chapter : data.attunementChapters()) {
                blocks.add(new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION,
                        g("archive.value.tier_transition", tierName(chapter.chapter().fromTierId()), tierName(chapter.chapter().toTierId()))));
                blocks.add(new SemanticDocument.Paragraph(g("archive.reference.mechanics.attunement.seals",
                        chapter.chapter().requiredCategories(), chapter.categories().size())));
                List<ReadOnlyDataTable.Row<Cells>> investments = chapter.categories().stream().map(category ->
                        row(category.categoryId(), categoryName(category.categoryId()), integer(category.investmentReference())
                                .copy().withStyle(style -> style.withColor(AscendancePalette.categoryRgb(ResourceLocation.parse(category.categoryId()))))))
                        .toList();
                blocks.add(table(List.of(textColumn("category", g("archive.reference.category"), 90, 1, Cells::first),
                        textColumn("investment", g("archive.table.acceleration_investment"), 132, 1, Cells::second)), investments));
                List<ReadOnlyDataTable.Row<Cells>> activities = chapter.activities().stream().map(activity -> {
                    var rate = activity.rate();
                    var category = chapter.chapter().categories().get(rate.categoryId());
                    return row(rate.activityId(), categoryName(rate.categoryId()), Component.translatable(activity.method().labelKey()),
                            decimal(MechanicsPresentationData.baseSealPercent(rate, category), "percent"),
                            g("archive.attunement.unit." + rate.units()));
                }).toList();
                blocks.add(table(List.of(textColumn("category", g("archive.reference.category"), 72, 0, Cells::first),
                        textColumn("activity", g("archive.table.activity"), 112, 1, Cells::second),
                        textColumn("rate", g("archive.table.seal_rate"), 72, 0, Cells::third),
                        textColumn("unit", g("archive.reference.unit"), 92, 0, Cells::fourth).keepValuesTogether()), activities));
            }
            blocks.add(heading("archive.reference.mechanics.attunement.activities"));
            if (!data.attunementChapters().isEmpty())
                blocks.add(new SemanticDocument.Requirements(data.attunementChapters().getFirst().activities().stream()
                        .map(activity -> new SemanticDocument.Requirement(
                                Component.translatable(activity.method().labelKey()).withStyle(style -> style.withColor(
                                        AscendancePalette.categoryRgb(ResourceLocation.parse(activity.method().categoryId())))),
                                Component.translatable(activity.method().descriptionKey()), SemanticDocument.RequirementStatus.INFORMATION)).toList()));
        } else {
            blocks.add(new SemanticDocument.Requirements(mechanicRequirements(topic)));
        }
        return new SemanticDocument(g("archive.entry.reference.mechanics." + topic + ".title"), blocks);
    }

    private static List<SemanticDocument.Requirement> mechanicRequirements(String topic) {
        return switch (topic) {
            case "allocation_storage" -> List.of(info("reservoir", "archive.reference.mechanics.allocation_storage.reservoir"),
                    info("available", "archive.reference.mechanics.allocation_storage.available"),
                    info("invested", "archive.reference.mechanics.allocation_storage.invested"),
                    info("atomic", "archive.reference.mechanics.allocation_storage.atomic"));
            case "overcap" -> List.of(info("stored", "archive.reference.mechanics.overcap.stored"),
                    info("effective", "archive.reference.mechanics.overcap.effective"),
                    info("transactions", "archive.reference.mechanics.overcap.transactions"));
            case "repair_durability" -> List.of(info("ordinary", "archive.reference.mechanics.repair_durability.ordinary"),
                    info("artifacts", "archive.reference.mechanics.repair_durability.artifacts"),
                    info("overdurability", "archive.reference.mechanics.repair_durability.overdurability"));
            case "death_retention" -> List.of(info("skills", "archive.reference.mechanics.death_retention.skills"),
                    info("artifacts", "archive.reference.mechanics.death_retention.artifacts"),
                    info("inventory", "archive.reference.mechanics.death_retention.inventory"));
            default -> List.of(info("server", "archive.entry.reference.mechanics.status.row.server.detail"),
                    info("client", "archive.entry.reference.mechanics.status.row.client.detail"));
        };
    }

    private static SemanticDocument.Table<Cells> focusTable(MachinePresentationData.Projection data, boolean pylon) {
        List<ReadOnlyDataTable.Row<Cells>> rows = data.focusProfiles().stream().map(value -> {
            Component state = focusState(value);
            if (pylon) return row("pylon/" + focusKey(value), state,
                    booleanValue(value.pylonOperational()), integer(value.pylonContribution().reservoirCapacityBonus()),
                    decimal(value.pylonContribution().transferRangeBonus(), "blocks"),
                    integer(value.pylonContribution().transferRatePerSecondBonus()),
                    decimal(value.pylonContribution().dissolutionSpeedBonus() * 100, "percent"));
            return row("infuser/" + focusKey(value), state, booleanValue(value.infuserOperational()),
                    basisPoints(value.infuserEfficiencyBasisPoints()), integer(value.infuserThroughputPerSecond()),
                    integer(value.essentiumIngotCapacity()));
        }).toList();
        if (pylon) return table(List.of(textColumn("focus", g("archive.table.focus_state"), 88, 0, Cells::first),
                textColumn("operation", g("archive.table.operates"), 62, 0, Cells::second),
                textColumn("reservoir", g("archive.table.reservoir_bonus"), 76, 0, Cells::third),
                textColumn("range", g("archive.table.range_bonus"), 70, 0, Cells::fourth),
                textColumn("rate", g("archive.table.rate_bonus"), 76, 0, Cells::fifth),
                textColumn("speed", g("archive.table.speed_bonus"), 72, 0, Cells::sixth)), rows);
        return table(List.of(textColumn("focus", g("archive.table.focus_state"), 92, 0, Cells::first),
                textColumn("operation", g("archive.table.operates"), 66, 0, Cells::second),
                textColumn("efficiency", g("archive.table.efficiency"), 76, 0, Cells::third),
                textColumn("throughput", g("archive.table.throughput"), 92, 0, Cells::fourth),
                textColumn("capacity", g("archive.table.ingot_capacity"), 92, 0, Cells::fifth)), rows);
    }

    private static String focusKey(MachinePresentationData.FocusProfile value) {
        return value.completedTier() == null ? value.state().name().toLowerCase(Locale.ROOT)
                : value.completedTier().serializedName();
    }
    private static Component focusState(MachinePresentationData.FocusProfile value) {
        if (value.state() == MachinePresentationData.FocusState.NONE) return g("archive.value.none");
        if (value.state() == MachinePresentationData.FocusState.LATENT) return equipmentTier(EquipmentTier.LATENT);
        return focusTier(value.completedTier());
    }

    private static List<SemanticDocument.Block> machineIntro(String key, String item) {
        Component title = item.equals("essence_focus") ? itemName(item) : blockName(item);
        List<SemanticDocument.Block> blocks = new ArrayList<>();
        blocks.add(new SemanticDocument.Illustration(id(item), g("archive.reference.machine." + key + ".caption"), 132, 76));
        blocks.add(new SemanticDocument.Paragraph(g("archive.reference.machine." + key + ".body", title)));
        return blocks;
    }

    private static SemanticDocument.Requirement info(String label, String detailKey) {
        Component title = g("archive.reference.label." + label);
        title = switch (label) {
            case "soulbound" -> title.copy().withColor(AscendanceUiPalette.SOULBOUND);
            case "fractured" -> title.copy().withColor(AscendanceUiPalette.FRACTURED);
            default -> title;
        };
        return new SemanticDocument.Requirement(title, g(detailKey),
                SemanticDocument.RequirementStatus.INFORMATION);
    }
    private static SemanticDocument.StatRow stat(String key, long value) {
        return new SemanticDocument.StatRow(g("archive.reference.machine." + key), integer(value));
    }
    private static SemanticDocument.StatRow statDecimal(String key, double value, String unit) {
        return new SemanticDocument.StatRow(g("archive.reference.machine." + key), decimal(value, unit));
    }
    private static SemanticDocument.StatRow statRate(String key, long value) {
        return new SemanticDocument.StatRow(g("archive.reference.machine." + key), g("archive.value.per_second", value));
    }
    private static SemanticDocument.StatRow statTicks(String key, int value) {
        return new SemanticDocument.StatRow(g("archive.reference.machine." + key), g("archive.value.ticks_seconds", value, format(value / 20.0)));
    }
    private static SemanticDocument.Callout runtimeState(PresentationContext.Availability state) {
        return new SemanticDocument.Callout(SemanticDocument.CalloutKind.NOTE, g("archive.callout.note"),
                g("archive.reference.runtime." + state.name().toLowerCase(Locale.ROOT)));
    }
    private static Component booleanValue(boolean value) { return g(value ? "archive.value.yes" : "archive.value.no"); }
    private static Component basisPoints(int value) { return decimal(value / 100.0, "percent"); }
    private static Component decimal(double value, String unit) { return g("archive.value.reference_unit." + unit, format(value)); }
    private static Component integer(long value) { return Component.literal(Long.toString(value)); }
    private static String format(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum() != 0 || value == 0 ? rounded.toPlainString()
                : com.mistaboom.essence_ascendance.presentation.PresentationMetric.DisplayConversion.NATIVE.format(value);
    }
    private static Component blockName(String path) { return Component.translatable("block." + EssenceAscendance.MOD_ID + "." + path); }
    private static Component itemName(String path) { return Component.translatable("item." + EssenceAscendance.MOD_ID + "." + path); }
    static Component profileTitle(ResourceLocation profileId) {
        return g("archive.entry.reference.equipment.profile." + profileId.getPath() + ".title");
    }
    private static Component equipmentFamilyTitle(String key) {
        String item = switch (key) {
            case "helmet", "chestplate", "leggings", "boots" -> "ascendance_" + key;
            case "melee_weapon", "ranged_weapon", "pickaxe", "axe", "shovel", "hoe", "shield" -> "ascendance_" + key;
            case "magic_caster" -> "ascendance_caster";
            default -> null;
        };
        return item == null ? Component.literal(key) : itemName(item);
    }
    private static Component essenceName(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
        return EssenceRegistry.get(id).<Component>map(essence -> EssenceText.essenceShort(essence).withStyle(style -> style.withColor(AscendancePalette.categoryRgb(essence)))).orElse(Component.literal(path));
    }
    private static Component categoryName(String value) {
        ResourceLocation parsed = value.contains(":") ? ResourceLocation.tryParse(value)
                : ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, value);
        return parsed == null ? Component.literal(value) : EssenceRegistry.get(parsed)
                .<Component>map(essence -> EssenceText.essenceShort(essence).withColor(AscendancePalette.categoryRgb(essence)))
                .orElse(Component.literal(value));
    }
    private static Component tierName(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? Component.literal(value) : AscendanceTierRegistry.get(parsed)
                .<Component>map(tier -> EssenceText.ascendanceTier(tier).withStyle(style -> style.withColor(AscendancePalette.tierMetalRgb(tier.id())))).orElse(Component.literal(value));
    }
    private static Component join(List<Component> components) {
        if (components.isEmpty()) return g("archive.value.none");
        Component result = Component.empty();
        for (int i = 0; i < components.size(); i++) {
            if (i > 0) result = result.copy().append(Component.literal(" · "));
            result = result.copy().append(components.get(i));
        }
        return result;
    }
    private static ReadOnlyDataTable.Row<Cells> row(String key, Component... values) {
        Component empty = Component.empty();
        return new ReadOnlyDataTable.Row<>(key, new Cells(values.length > 0 ? values[0] : empty,
                values.length > 1 ? values[1] : empty, values.length > 2 ? values[2] : empty,
                values.length > 3 ? values[3] : empty, values.length > 4 ? values[4] : empty,
                values.length > 5 ? values[5] : empty));
    }
    private static SemanticDocument.Table<Cells> table(List<ReadOnlyDataTable.Column<Cells, ?>> columns,
                                                        List<ReadOnlyDataTable.Row<Cells>> rows) {
        return new SemanticDocument.Table<>(new ReadOnlyDataTable<>(columns, rows),
                SemanticDocument.TableLayoutPolicy.ARTICLE_FLOW);
    }
    private static ReadOnlyDataTable.Column<Cells, Component> textColumn(String id, Component header, int width,
                                                                         int weight,
                                                                         java.util.function.Function<Cells, Component> value) {
        return new ReadOnlyDataTable.Column<>(id, header, width, weight, ReadOnlyDataTable.Alignment.LEFT,
                value, component -> component, null);
    }
    private static Component equipmentTier(EquipmentTier tier) {
        return EssenceText.equipmentTier(tier).withStyle(style -> style.withColor(AscendancePalette.tierMetalRgb(tier)));
    }
    private static Component focusTier(EssenceFocusTier tier) {
        return EssenceText.focusTier(tier).withStyle(style -> style.withColor(AscendancePalette.tierMetalRgb(id(tier.serializedName()))));
    }
    private static SemanticDocument.Heading heading(String key) {
        return new SemanticDocument.Heading(SemanticDocument.HeadingLevel.SECTION, g(key));
    }
    private static ReadOnlyDataTable.Row<Cells> linkedRow(String key, String target, Component... values) {
        return new ReadOnlyDataTable.Row<>(key, row(key, values).value(), id(target).toString());
    }
    private static Component g(String path, Object... args) { return ArchiveText.guide(path, args); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path); }
}
