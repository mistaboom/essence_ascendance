package com.mistaboom.essence_ascendance.archive;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.attunement.AttunementSnapshot;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.EssencePresentationData;
import com.mistaboom.essence_ascendance.client.presentation.MachinePresentationData;
import com.mistaboom.essence_ascendance.client.presentation.EquipmentPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.MechanicsPresentationData;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.network.BonusTrackSnapshot;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload;
import com.mistaboom.essence_ascendance.presentation.PresentationMetric;
import com.mistaboom.essence_ascendance.progression.BonusTrackCurve;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
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
            remainingReferenceParity(runtime);
            playerReferenceContracts(runtime);
        } finally {
            EssenceConfigManager.clearClient();
        }
        System.out.println("ReferencePresentationTest: " + checks + " shared reference assertions PASS");
    }

    private static void remainingReferenceParity(RuntimeBalanceDefinition runtime) {
        PresentationContext context = PresentationContext.catalog(runtime);
        ArchiveCatalog catalog = ArchiveCatalog.DEFAULT;
        check(catalog.entries(ArchiveMode.REFERENCE, ArchiveSection.REFERENCE_ESSENCES.id()).size()
                        == EssenceRegistry.size() + 1,
                "Essence Reference must contain every registry Essence and Item Yields, without an overview");
        check(catalog.entry(id("reference/essences/item_yields")) != null,
                "Item Yields lacks a stable canonical Archive destination");
        check(catalog.entries(ArchiveMode.REFERENCE, ArchiveSection.REFERENCE_MACHINES.id()).size() == 6,
                "Machine Reference does not cover Crucible, Pylon, Infuser, Focus, Nexus, and Channelstone");
        check(catalog.entries(ArchiveMode.REFERENCE, ArchiveSection.REFERENCE_EQUIPMENT.id()).size()
                        == EquipmentProfileRegistry.size() + 2,
                "Equipment Reference does not cover every profile plus infusion/lifecycle");
        check(catalog.entries(ArchiveMode.REFERENCE, ArchiveSection.REFERENCE_MECHANICS.id()).size() == 6,
                "Mechanics Reference is missing a cross-cutting semantic provider");

        for (var essence : EssenceRegistry.values()) {
            var projection = EssencePresentationData.project(essence);
            check(projection.bonuses().stream().allMatch(stat -> stat.essenceType().id().equals(essence.id()))
                            && projection.bonuses().size() == EssenceStatRegistry.values().stream()
                            .filter(stat -> stat.essenceType().id().equals(essence.id())).count(),
                    "Essence Bonus associations diverge from the stat registry " + essence.id());
            check(projection.skills().stream().allMatch(skill -> skill.essenceId().equals(essence.id()))
                            && projection.skills().size() == SkillRegistry.values().stream()
                            .filter(skill -> skill.essenceId().equals(essence.id())).count(),
                    "Essence Skill associations diverge from the Skill registry " + essence.id());
        }

        var machines = MachinePresentationData.project(context);
        check(machines.ready() && machines.crucibleBaseline() == runtime.crucible()
                        && machines.pylonLinkRadius() == runtime.config().pylonRadius()
                        && machines.maximumActivePylons() == runtime.config().maxActivePylons()
                        && machines.infuserLinkRange() == runtime.config().infuserBalance().linkRange(),
                "Machine projection is not reading the explicit synchronized runtime");
        check(machines.focusProfiles().size() == EssenceFocusTier.values().length + 2,
                "Focus projection does not separate empty, Latent, and upgraded states");
        var none = machines.focusProfiles().getFirst();
        var latent = machines.focusProfiles().get(1);
        check(none.state() == MachinePresentationData.FocusState.NONE && !none.installed()
                        && !none.pylonOperational() && !none.infuserOperational()
                        && none.infuserThroughputPerSecond() == 0,
                "No-Focus state was presented as an installed/operational Focus");
        check(latent.state() == MachinePresentationData.FocusState.LATENT && latent.installed()
                        && latent.completedTier() == null && latent.pylonOperational() && latent.infuserOperational()
                        && latent.pylonContribution().equals(runtime.pylon("empty"))
                        && latent.infuserEfficiencyBasisPoints()
                        == runtime.config().infuserBalance().noFocusEfficiencyBasisPoints()
                        && latent.infuserThroughputPerSecond()
                        == runtime.config().infuserBalance().noFocusInfusionThroughputPerSecond()
                        && latent.essentiumIngotCapacity() == runtime.config().infuserBalance()
                        .grade(EssenceFocusTier.DORMANT.serializedName()).ingotCapacity(),
                "Installed Latent Focus lost baseline Pylon/Infuser operation or gained a fake Dormant tier");
        for (EssenceFocusTier tier : EssenceFocusTier.values()) {
            var row = machines.focusProfiles().stream().filter(value -> value.completedTier() == tier).findFirst().orElseThrow();
            var grade = runtime.config().infuserBalance().grade(tier.serializedName());
            check(row.state() == MachinePresentationData.FocusState.UPGRADED && row.installed()
                            && row.pylonContribution().equals(runtime.pylon(tier.serializedName()))
                            && row.infuserEfficiencyBasisPoints() == grade.efficiencyBasisPoints()
                            && row.infuserThroughputPerSecond() == grade.infusionThroughputPerSecond()
                            && row.essentiumIngotCapacity() == grade.ingotCapacity(),
                    "Upgraded Focus row diverges from native runtime values " + tier);
        }
        check(machines.focusUpgrades().getFirst().requiredInstalledTier() == null
                        && machines.focusUpgrades().getFirst().targetTier() == EssenceFocusTier.DORMANT,
                "Latent-to-Dormant Focus upgrade prerequisite was mislabeled as a completed tier");

        EquipmentProfiles.init();
        var equipment = EquipmentPresentationData.project(context);
        check(equipment.ready() && equipment.profiles().size() == EquipmentProfileRegistry.size()
                        && equipment.tierBaselines().size() == EquipmentTier.values().length,
                "Equipment projection does not cover the registered profiles and tiers");
        for (var profile : equipment.profiles()) {
            check(profile.baselineMultipliers().equals(profile.definition().baselineMultipliers()),
                    "Equipment baseline multipliers were copied incorrectly " + profile.definition().id());
            for (var tier : equipment.tierBaselines()) for (var property : profile.baselineMultipliers().keySet()) {
                double expected = EquipmentBaselineService.resolvedValue(tier.baseline().value(property),
                        profile.definition(), property, equipment.quantizedBaselines());
                check(Double.isFinite(expected) && expected >= 0,
                        "Equipment effective baseline resolver produced an invalid presentation value");
            }
        }
        check(equipment.infusionTargets().size() == EquipmentTier.values().length - 1
                        && equipment.repairEssencePerDurability() == runtime.config().infuserBalance().repair().essencePerDurability()
                        && equipment.fracturedLatentIngotCount() == runtime.config().infuserBalance().repair().fracturedLatentIngotCount(),
                "Equipment infusion/repair projection diverges from authoritative settings");

        var mechanics = MechanicsPresentationData.project(context);
        check(mechanics.ready() && mechanics.attunementChapters().size() == runtime.attunement().chapters().size(),
                "Mechanics projection omitted Attunement chapters");
        for (String retired : List.of("essences/overview", "machines/overview", "equipment/overview", "mechanics/progression"))
            check(catalog.entry(id("reference/" + retired)) == null, "Retired Reference entry is still visible: " + retired);
        check(MechanicsPresentationData.maximumInvestmentMultiplier(mechanics.attunementPolicy())
                        == 1 + mechanics.attunementPolicy().maximumAcceleration(),
                "Attunement maximum acceleration was mislabeled as the final multiplier");
        mechanics.attunementChapters().forEach(chapter -> chapter.activities().forEach(activity -> {
            double expected = activity.rate().contributionPerUnit()
                    / chapter.chapter().categories().get(activity.rate().categoryId()).target() * 100;
            double actual = MechanicsPresentationData.baseSealPercent(activity.rate(),
                    chapter.chapter().categories().get(activity.rate().categoryId()));
            check(Math.abs(actual - expected) < Math.max(1e-12, expected * 1e-12),
                    "Seal percentage diverges from native contribution normalization");
        }));
        mechanics.attunementChapters().forEach(chapter -> check(chapter.categories().size()
                        == chapter.chapter().categories().size()
                        && chapter.activities().size() == chapter.chapter().activities().size(),
                "Attunement Reference omitted categories or registered activity rates"));

        for (var document : List.of(ReferenceDocuments.focus(context), ReferenceDocuments.infuser(context),
                ReferenceDocuments.pylon(context))) {
            for (var block : document.blocks()) if (block instanceof SemanticDocument.Table<?> table
                    && table.data().column("focus") != null) {
                check(cell(table, "focus", 0).equals("None") && cell(table, "focus", 1).equals("Latent")
                                && cell(table, "focus", 2).equals("Dormant"),
                        "Focus cells must distinguish all states without repeating their column header");
                for (int row = 0; row < table.data().rows().size(); row++)
                    check(!cell(table, "focus", row).contains("Focus"), "Focus table repeats the column header in a cell");
            }
        }
        for (ArchiveEntry entry : catalog.entries()) if (entry.mode() == ArchiveMode.REFERENCE) {
            requireLocalized(entry.content().get());
        }
    }

    private static void playerReferenceContracts(RuntimeBalanceDefinition runtime) {
        var context = PresentationContext.catalog(runtime);
        var catalog = ArchiveCatalog.DEFAULT;
        for (var entry : catalog.entries()) {
            requireLocalized(entry.navigationSummary());
            check(!entry.navigationSummary().getString().isBlank(), "Navigation description missing");
            // Rich article and summary text remain available to Search independently of the short navigation label.
            check(catalog.searchableText(entry).contains(entry.summary()), "Search discarded the detailed summary");
        }
        for (var essence : EssenceRegistry.values()) {
            var document = ReferenceDocuments.essence(essence);
            var models = document.blocks().stream().filter(SemanticDocument.ItemRow.class::isInstance)
                    .map(SemanticDocument.ItemRow.class::cast).toList();
            check(models.size() == 3, "Essence needs nugget, ingot, and block rows");
            for (int form = 0; form < models.size(); form++) {
                check(models.get(form).items().size() == EssenceFocusTier.values().length, "Missing Essentium grades");
                for (int grade = 0; grade < EssenceFocusTier.values().length; grade++) {
                    var model = models.get(form).items().get(grade);
                    check(model.resource().equals(id(List.of("essentium_nugget", "essentium_ingot", "essentium_block").get(form))),
                            "Essentium forms are out of order");
                    var expected = com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData.carrierData(
                            new com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData.Value(essence, EssenceFocusTier.values()[grade], 1));
                    check(model.components().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).orElseThrow().equals(expected),
                            "Essentium image lacks the category and grade components used by the native model");
                    check(model.label().getString().isBlank() == false, "Essentium image is not searchable");
                }
            }
            var tables = document.blocks().stream().filter(SemanticDocument.Table.class::isInstance)
                    .map(block -> (SemanticDocument.Table<?>) block).toList();
            check(tables.size() == 2 && tables.getFirst().data().columns().size() == 2,
                    "Essence associations contain a redundant category column");
            for (var table : tables) {
                check(table.expanded(), "Reference association table has nested scrolling");
                for (var row : table.data().rows())
                    check(row.target() != null && catalog.entry(ResourceLocation.parse(row.target())) != null,
                            "Bonus/Skill association is not a working navigation target");
            }
            String text = flattenAll(document);
            check(!text.contains(essence.id().toString()) && !text.contains("derived from"),
                    "Essence article leaks internal identity or registry prose");
        }

        var data = EquipmentPresentationData.project(context);
        var armor = data.profiles().stream().filter(p -> p.definition().id().equals(EquipmentProfiles.ARMOR.id())).findFirst().orElseThrow();
        var armorDoc = ReferenceDocuments.equipmentProfile(EquipmentProfiles.ARMOR.id(), "ascendance_helmet", context);
        var armorTables = armorDoc.blocks().stream().filter(SemanticDocument.Table.class::isInstance)
                .map(block -> (SemanticDocument.Table<?>) block).toList();
        check(armorTables.size() == EquipmentTier.values().length, "Armor needs one table per tier");
        var slots = List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET);
        for (int tierIndex = 0; tierIndex < data.tierBaselines().size(); tierIndex++) {
            var tier = data.tierBaselines().get(tierIndex);
            var table = armorTables.get(tierIndex);
            check(armorDoc.blocks().stream().filter(SemanticDocument.Heading.class::isInstance)
                            .map(SemanticDocument.Heading.class::cast).anyMatch(heading -> heading.text().getStyle().getColor() != null
                                    && heading.text().getStyle().getColor().getValue() == AscendancePalette.tierMetalRgb(tier.tier())),
                    "Armor tier heading must use the readable canonical tier-metal color");
            check(table.expanded() && table.data().rows().size() == 4, "Armor rows or full-height scrolling are wrong");
            check(table.data().columns().stream().map(column -> column.id()).toList()
                            .equals(List.of("item", "armor", "toughness", "durability", "bonus_share")),
                    "Armor table is not item, armor, toughness, durability, worn share");
            double sum = 0;
            for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
                var slot = slots.get(slotIndex);
                double physical = EquipmentPresentationData.physicalValue(armor, tier,
                        com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.ARMOR, data.quantizedBaselines(), slot);
                sum += physical;
                check(Math.abs(Double.parseDouble(cell(table, "armor", slotIndex)) - physical) <= 0.00005,
                        "Armor displayed value is not the slot's native physical share");
                check(EquipmentPresentationData.wornShare(tier.tier(), slot) == (tier.tier() == EquipmentTier.LATENT ? 0
                                : com.mistaboom.essence_ascendance.equipment.ArmorStatWeights.weightFor(slot)),
                        "Latent armor grants an Essence share, or upgraded armor uses the wrong share");
                assertTierModel(table, slotIndex, tier.tier());
            }
            double whole = EquipmentBaselineService.resolvedValue(tier.baseline().fullSetArmor(), armor.definition(),
                    com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.ARMOR, data.quantizedBaselines());
            check(Math.abs(sum - whole) < 1e-8, "Armor slot projection lost full-set physical points");
            check(EquipmentPresentationData.nativeDurability(armor, tier, ResourceLocation.parse("minecraft:iron_helmet"))
                            .orElseThrow() == net.minecraft.world.item.Items.IRON_HELMET.getDefaultInstance().getMaxDamage(),
                    "Displayed non-shield durability uses a generated target instead of current usable durability");
            check(EquipmentPresentationData.nativeDurability(armor, tier, id("missing_test_item")).isEmpty(),
                    "Missing item data silently became real zero durability");
        }
        for (var profile : data.profiles()) {
            if (profile.definition().id().equals(EquipmentProfiles.ARMOR.id())) continue;
            String path = profile.definition().id().getPath();
            String item = path.equals("magic_caster") ? "ascendance_caster" : "ascendance_" + path;
            var document = ReferenceDocuments.equipmentProfile(profile.definition().id(), item, context);
            var table = (SemanticDocument.Table<?>) document.blocks().stream().filter(SemanticDocument.Table.class::isInstance).findFirst().orElseThrow();
            check(table.expanded() && table.data().rows().size() == EquipmentTier.values().length,
                    "Equipment must show all tier rows without a nested scrollbar");
            for (int tier = 0; tier < EquipmentTier.values().length; tier++) assertTierModel(table, tier, EquipmentTier.values()[tier]);
            var links = document.blocks().stream().filter(SemanticDocument.Links.class::isInstance)
                    .map(SemanticDocument.Links.class::cast).flatMap(block -> block.links().stream())
                    .map(SemanticDocument.Link::target).collect(java.util.stream.Collectors.toSet());
            for (var bonus : profile.applicability()) check(links.contains(id("reference/bonuses/" + bonus.statId().getPath()).toString()),
                    "Applicable equipment Bonus is not clickable");
            if (profile.definition().id().equals(EquipmentProfiles.SHIELD.id())) {
                for (int i = 0; i < data.tierBaselines().size(); i++) {
                    var tier = data.tierBaselines().get(i);
                    check(cell(table, "durability", i).equals(Integer.toString(tier.shieldDurability())),
                            "Shield durability does not use native shield tier settings");
                    double reflect = com.mistaboom.essence_ascendance.equipment.ShieldMath.blockedPercent(
                            tier.shieldNativeReflectionPercent(), 0, tier.shieldBlockAmplification());
                    check(cell(table, "block_reflection", i).contains(PresentationMetric.DisplayConversion.NATIVE.format(reflect)),
                            "Shield block reflection diverges from native math");
                }
            }
        }
        var infusion = ReferenceDocuments.equipmentInfusion(context);
        int tableIndex = 0;
        while (!(infusion.blocks().get(tableIndex) instanceof SemanticDocument.Table)) tableIndex++;
        check(infusion.blocks().get(tableIndex - 1) instanceof SemanticDocument.Heading heading
                        && flatten(heading.text()).equals("Ascension Costs"), "Infusion cost table lacks its heading");

        var focus = ReferenceDocuments.focus(context);
        var upgrades = (SemanticDocument.Table<?>) focus.blocks().getLast();
        for (int width : List.of(300, 340, 480, 800)) {
            var widths = upgrades.data().measure(width, 1, value -> flatten(value).length() * 6);
            for (int column = 0; column < 2; column++) for (int row = 0; row < upgrades.data().rows().size(); row++)
                check(cell(upgrades, column == 0 ? "target" : "installed", row).length() * 6 <= widths.get(column).width() - 8,
                        "A Focus upgrade tier wraps a trailing letter at article width " + width);
        }
        for (var document : List.of(armorDoc, GuideDocuments.beginning(context), GuideDocuments.essence())) {
            check(document.blocks().getLast() instanceof SemanticDocument.Links
                            && document.blocks().get(document.blocks().size() - 2) instanceof SemanticDocument.Heading heading
                            && heading.level() == SemanticDocument.HeadingLevel.SECTION && flatten(heading.text()).equals("See Also"),
                    "Footer navigation needs a See Also heading and section divider");
        }
        var lifecycle = ReferenceDocuments.equipmentLifecycle(context);
        for (var block : lifecycle.blocks()) if (block instanceof SemanticDocument.Requirements requirements)
            for (var requirement : requirements.rows()) {
                String keyword = flatten(requirement.label());
                Integer expected = switch (keyword) {
                    case "Soulbound" -> com.mistaboom.essence_ascendance.visual.AscendanceUiPalette.SOULBOUND;
                    case "Fractured" -> com.mistaboom.essence_ascendance.visual.AscendanceUiPalette.FRACTURED;
                    default -> null;
                };
                if (expected != null) check(requirement.label().getStyle().getColor() != null
                                && requirement.label().getStyle().getColor().getValue() == expected,
                        "Lifecycle keyword lost its canonical tooltip color: " + keyword);
            }
    }

    private static <R> String cell(SemanticDocument.Table<R> table, String column, int row) {
        return flatten(table.data().column(column).display(table.data().rows().get(row).value()));
    }

    private static <R> void assertTierModel(SemanticDocument.Table<R> table, int row, EquipmentTier tier) {
        var column = table.data().columns().getFirst();
        check(column.isItem(), "First equipment column is not an item image");
        var model = column.item(table.data().rows().get(row).value());
        var encoded = model.components().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).orElseThrow();
        check(encoded.equals(com.mistaboom.essence_ascendance.equipment.EquipmentTierData.tierData(tier)),
                "Equipment illustration lost its completed tier");
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
        PresentationContext mixed = new PresentationContext(
                new PresentationContext.Runtime(PresentationContext.Availability.LOADING, null),
                PresentationContext.Player.ready(snapshot), new PresentationContext.Revision(0, 19, true, 4, 7));
        String waiting = flattenAll(ArchiveDocuments.skillReference(skill, mixed));
        check(!waiting.contains("1/0") && ArchiveDocuments.skillReference(skill, mixed).blocks().stream()
                        .noneMatch(block -> block instanceof SemanticDocument.StatRows rows && rows.rows().stream()
                                .anyMatch(row -> row.label().getContents() instanceof TranslatableContents translated
                                        && translated.getKey().endsWith("next_rank"))),
                "Ready player plus loading profile invents zero maximum rank or maximum-rank completion");
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
