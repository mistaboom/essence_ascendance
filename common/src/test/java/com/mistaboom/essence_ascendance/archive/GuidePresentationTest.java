package com.mistaboom.essence_ascendance.archive;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.client.presentation.MachinePresentationData;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Executable contract for the player-facing Guide curriculum and its live-fact relationships. */
public final class GuidePresentationTest {
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
        EquipmentProfiles.init();
        try (var stream = GuidePresentationTest.class.getResourceAsStream(
                "/assets/essence_ascendance/lang/en_us.json")) {
            language = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        }
        RuntimeBalanceDefinition runtime = RuntimeBalanceDefinition.bootstrap();
        PresentationContext context = PresentationContext.catalog(runtime);
        List<SemanticDocument> documents = List.of(
                GuideDocuments.beginning(context), GuideDocuments.essence(), GuideDocuments.machines(context),
                GuideDocuments.infusion(context), GuideDocuments.ascendance(context), GuideDocuments.skills());
        curriculum(documents);
        liveFacts(context, documents);
        navigationAndSearch(documents);
        semanticPresentation(documents);
        System.out.println("GuidePresentationTest: " + checks + " Guide assertions PASS");
    }

    private static void curriculum(List<SemanticDocument> documents) {
        ArchiveCatalog catalog = ArchiveCatalog.DEFAULT;
        check(catalog.sections(ArchiveMode.GUIDE).equals(ArchiveSection.GUIDE),
                "Guide section order changed");
        check(documents.size() == ArchiveSection.GUIDE.size(), "Guide does not have one lesson per curriculum section");
        for (ArchiveSection section : ArchiveSection.GUIDE)
            check(catalog.entries(ArchiveMode.GUIDE, section.id()).size() == 1,
                    "Guide section does not expose exactly one real lesson " + section.id());
        for (SemanticDocument document : documents) {
            check(document.searchableText().size() >= 15, "Guide lesson is still placeholder-sized: " + key(document.title()));
            check(document.blocks().stream().noneMatch(block -> block instanceof SemanticDocument.Heading heading
                            && key(heading.text()).equals(key(document.title()))),
                    "Guide repeats its article title as a heading");
            for (SemanticDocument.Block block : document.blocks()) {
                if (!(block instanceof SemanticDocument.Table<?> table)) continue;
                        check(table.layoutPolicy() == SemanticDocument.TableLayoutPolicy.ARTICLE_FLOW,
                                "Guide introduced a nested table viewport");
                        for (int width : List.of(300, 480, 800)) {
                            var measured = table.data().measure(width, 5, value -> translated(value).length() * 6);
                            check(measured.stream().mapToInt(value -> value.width() + 5).sum() - 5 == width,
                                    "Guide table did not allocate its complete usable width at " + width);
                            check(measured.stream().allMatch(value -> value.width() > 0),
                                    "Guide table collapsed a column at supported width " + width);
                        }
            }
        }
        List<SemanticDocument.ItemIllustration> heroes = List.of(documents.get(0), documents.get(3),
                        documents.get(4), documents.get(5)).stream()
                .map(document -> document.blocks().stream()
                        .filter(SemanticDocument.ItemIllustration.class::isInstance)
                        .map(SemanticDocument.ItemIllustration.class::cast).findFirst().orElseThrow()).toList();
        check(heroes.stream().map(hero -> hero.preferredWidth() + "x" + hero.preferredHeight()).distinct().count() == 1,
                "Guide hero figures do not share one box size");
    }

    private static void liveFacts(PresentationContext context, List<SemanticDocument> documents) {
        MachinePresentationData.Projection machines = MachinePresentationData.project(context);
        check(machines.ready(), "Guide fixture runtime is unavailable");
        check(machines.focusProfiles().stream().map(MachinePresentationData.FocusProfile::state).collect(
                        java.util.stream.Collectors.toSet()).equals(Set.of(MachinePresentationData.FocusState.NONE,
                        MachinePresentationData.FocusState.LATENT, MachinePresentationData.FocusState.UPGRADED)),
                "Guide provider collapsed a real Focus state");
        SemanticDocument machine = documents.get(2);
        SemanticDocument.Table<?> machineTable = machine.blocks().stream()
                .filter(block -> block instanceof SemanticDocument.Table<?>).map(block -> (SemanticDocument.Table<?>) block)
                .findFirst().orElseThrow();
        check(itemCaptionsEmpty(machineTable, "machine"),
                "Machine image column still renders narrow descriptive captions");
        SemanticDocument.ItemRow focusModels = machine.blocks().stream()
                .filter(SemanticDocument.ItemRow.class::isInstance).map(SemanticDocument.ItemRow.class::cast)
                .filter(row -> row.items().size() == 3).findFirst().orElseThrow();
        check(focusModels.items().getFirst().components().isEmpty(), "Latent Focus illustration has a fake completed tier");
        check(!focusModels.items().get(1).components().isEmpty()
                        && !focusModels.items().get(2).components().isEmpty()
                        && !focusModels.items().get(1).components().equals(focusModels.items().get(2).components()),
                "Completed Focus illustrations reused one default model state");
        check(EssenceFocusData.tierData(EssenceFocusTier.DORMANT).copyTag().toString().contains("dormant")
                        && EssenceFocusData.tierData(EssenceFocusTier.TRANSCENDENT).copyTag().toString().contains("transcendent"),
                "Shared Focus encoder does not preserve requested tiers");

        SemanticDocument essence = documents.get(1);
        SemanticDocument.ItemRow categories = essence.blocks().stream()
                .filter(SemanticDocument.ItemRow.class::isInstance).map(SemanticDocument.ItemRow.class::cast)
                .findFirst().orElseThrow();
        check(categories.items().size() == EssenceRegistry.size(), "Guide does not illustrate every live Essence category");
        check(categories.items().stream().map(item -> item.components().toString()).distinct().count() == EssenceRegistry.size(),
                "Guide Essence illustrations lost their category data");

        SemanticDocument ascendance = documents.get(4);
        SemanticDocument.Table<?> chapterTable = ascendance.blocks().stream()
                .filter(block -> block instanceof SemanticDocument.Table<?>).map(block -> (SemanticDocument.Table<?>) block)
                .findFirst().orElseThrow();
        check(chapterTable.data().rows().size() == com.mistaboom.essence_ascendance.client.presentation
                        .MechanicsPresentationData.project(context).attunementChapters().size(),
                "Guide Ascension table diverges from live Attunement chapters");
        String ascendanceText = resolvedText(ascendance);
        check(!ascendanceText.toLowerCase(java.util.Locale.ROOT).contains("milestone gates")
                        || ascendanceText.contains("not Ascension requirements"),
                "Guide restored the retired milestone gate path");
        check(ascendanceText.contains("Player Ascension") && ascendanceText.contains("equipment Ascension"),
                "Guide does not distinguish player and equipment advancement");
    }

    private static void navigationAndSearch(List<SemanticDocument> documents) {
        ArchiveCatalog catalog = ArchiveCatalog.DEFAULT;
        Set<ResourceLocation> linked = new HashSet<>();
        for (SemanticDocument document : documents) for (SemanticDocument.Block block : document.blocks()) {
            if (!(block instanceof SemanticDocument.Links links)) continue;
            for (SemanticDocument.Link link : links.links()) {
                ResourceLocation target = ResourceLocation.parse(link.target());
                linked.add(target);
                check(catalog.entry(target) != null, "Guide link does not target a real Archive entry: " + target);
            }
        }
        check(linked.contains(id("reference/bonuses/" + EssenceStats.DURABILITY_EFFICIENCY.id().getPath())),
                "Durability Efficiency does not navigate to its Bonus reference");
        check(linked.contains(id("reference/skills/" + SkillIds.MASTERWORK_TEMPERING.getPath())),
                "Masterwork Tempering does not navigate to its Skill reference");
        for (ArchiveEntry entry : catalog.entries(ArchiveMode.GUIDE, ArchiveSection.GUIDE_SKILLS.id())) {
            List<Component> searchable = catalog.searchableText(entry);
            check(searchable.stream().map(GuidePresentationTest::translated).anyMatch(text -> text.contains("Shift")),
                    "Guide's localized controls are absent from shared Search");
        }
    }

    private static void semanticPresentation(List<SemanticDocument> documents) {
        long largeFigures = documents.stream().flatMap(document -> document.blocks().stream())
                .filter(SemanticDocument.ItemIllustration.class::isInstance).count();
        check(largeFigures >= 4, "Guide does not use useful-size component-aware item figures");
        SemanticDocument infusion = documents.get(3);
        List<SemanticDocument.Requirement> requirements = infusion.blocks().stream()
                .filter(SemanticDocument.Requirements.class::isInstance).map(SemanticDocument.Requirements.class::cast)
                .flatMap(value -> value.rows().stream()).toList();
        Component soulbound = requirements.stream().map(SemanticDocument.Requirement::label)
                .filter(label -> key(label).endsWith("soulbound")).findFirst().orElseThrow();
        Component fractured = requirements.stream().map(SemanticDocument.Requirement::label)
                .filter(label -> key(label).endsWith("fractured")).findFirst().orElseThrow();
        check(soulbound.getStyle().getColor() != null
                        && soulbound.getStyle().getColor().getValue() == AscendanceUiPalette.SOULBOUND,
                "Soulbound lost its semantic color");
        check(fractured.getStyle().getColor() != null
                        && fractured.getStyle().getColor().getValue() == AscendanceUiPalette.FRACTURED,
                "Fractured lost its semantic color");
        for (SemanticDocument document : documents) {
            String text = resolvedText(document);
            check(!text.contains("ItemStack") && !text.contains("registry ID") && !text.contains("synchronization")
                            && !text.contains("transaction internals"),
                    "Guide exposes development terminology");
        }
    }

    private static String resolvedText(SemanticDocument document) {
        return document.searchableText().stream().map(GuidePresentationTest::translated)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String translated(Component component) {
        if (!(component.getContents() instanceof TranslatableContents translatable)) return component.getString();
        String template = language.has(translatable.getKey()) ? language.get(translatable.getKey()).getAsString()
                : translatable.getKey();
        Object[] args = translatable.getArgs();
        for (int index = 0; index < args.length; index++) {
            String value = args[index] instanceof Component argument ? translated(argument) : String.valueOf(args[index]);
            template = template.replace("%" + (index + 1) + "$s", value).replaceFirst("%s",
                    java.util.regex.Matcher.quoteReplacement(value));
        }
        return template;
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey() : component.getString();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("essence_ascendance", path);
    }

    private static <R> boolean itemCaptionsEmpty(SemanticDocument.Table<R> table, String columnId) {
        ReadOnlyDataTable.Column<R, ?> column = table.data().column(columnId);
        return column != null && column.isItem() && table.data().rows().stream()
                .allMatch(row -> column.item(row.value()).caption().getString().isEmpty());
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
