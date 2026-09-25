package com.mistaboom.essence_ascendance.archive;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.client.ItemEssenceTooltipClientState;
import com.mistaboom.essence_ascendance.client.archive.*;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.ui.*;
import com.mistaboom.essence_ascendance.client.ui.content.*;
import com.mistaboom.essence_ascendance.client.ui.data.*;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.presentation.PresentationMetric;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real localized documents and native wrapping with conservative glyph metrics, without an OpenGL context. */
public final class ArchiveAuditTest {
    private static int checks;
    private static final Font FONT = new MetricFont();

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        Language original = Language.getInstance();
        installLanguage(original);
        RuntimeBalanceDefinition runtime = RuntimeBalanceDefinition.bootstrap();
        EssenceConfigManager.installClient(runtime);
        try {
            localizedDocuments();
            keyboardAndLinks();
            itemSearchAndHistory();
            loadingScroll();
            semanticArguments(runtime);
            changedSourceFixtures(runtime);
            nativeSafetyWiring();
        } finally { EssenceConfigManager.clearClient(); Language.inject(original); }
        System.out.println("ArchiveAuditTest: " + checks + " final integration assertions PASS (headless; no visual approval)");
    }

    private static void localizedDocuments() throws Exception {
        for (ArchiveEntry entry : ArchiveCatalog.DEFAULT.entries()) {
            SemanticDocument document = entry.content().get();
            for (var text : document.searchableText()) {
                check(!text.getString().matches("(?s).*%[0-9]+\\$s.*"), "Unfilled semantic argument: " + entry.id());
                for (var run : StyledTextLayout.runs(text)) {
                    var click = run.style().getClickEvent();
                    if (click != null) check(ArchiveCatalog.DEFAULT.entry(ResourceLocation.tryParse(click.getValue())) != null,
                            "Inline reference has no real destination: " + click.getValue());
                }
            }
            for (int width : List.of(160, 300, 480, 800)) {
                ContentViewport article = new ContentViewport((g, i, b, t) -> { }, target -> { });
                article.prepare(FONT, new UiBounds(10, 20, width, 220), document);
                var field = ContentViewport.class.getDeclaredField("tableViews"); field.setAccessible(true);
                @SuppressWarnings("unchecked") Map<Integer, ReadOnlyDataTableView<?>> tables =
                        (Map<Integer, ReadOnlyDataTableView<?>>) field.get(article);
                for (var table : tables.values()) check(!table.hasOverflow(), "Nested Reference/Guide scroll at " + entry.id());
                article.key(269, 0, 0);
                int last = article.scrollOffset();
                article.scroll(20, 30, 0, -1);
                check(article.scrollOffset() == last, "Article End does not reach its final content");
            }
            for (var block : document.blocks()) if (block instanceof SemanticDocument.Table<?> table) {
                check(table.expanded(), "Ordinary document table owns a nested scrollbar: " + entry.id());
                for (int articleWidth : List.of(300, 480, 800)) verifyTable(table, articleWidth - 21);
            }
        }
        for (int width : List.of(300, 480, 800, 1600)) {
            var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), width, 600, true);
            for (var mode : List.of(ArchiveMode.GUIDE, ArchiveMode.REFERENCE)) {
                var sections = ArchiveCatalog.DEFAULT.sections(mode);
                var bar = FullscreenLayout.sectionBar(frame, sections.stream().map(s -> FONT.width(s.label())).toList(),
                        FONT.width(Component.literal("Back")) + 14, FONT.width(Component.literal("Forward")) + 14, 3);
                check(bar.items().size() == sections.size(), "Wrapped tab became inaccessible");
                for (var bounds : bar.items()) check(bounds.x() >= bar.back().right() && bounds.right() <= bar.forward().x(),
                        "Section label overlaps actual history controls");
            }
        }
    }

    private static <R> void verifyTable(SemanticDocument.Table<R> definition, int width) {
        var view = new ReadOnlyDataTableView<>(definition.data());
        int rowHeight = view.uniformRowHeight(FONT, width);
        view.prepare(FONT, new UiBounds(0, 0, width, view.headerHeight() + rowHeight * definition.data().rows().size() + 2));
        check(!view.hasOverflow(), "Expanded table cannot expose all rows");
        if (!view.stacked()) {
            var columns = definition.data().measureContent(width - 2, FONT::width);
            for (int col = 0; col < columns.size(); col++) {
                var column = definition.data().columns().get(col);
                int available = columns.get(col).width() - 8;
                for (var row : definition.data().rows()) {
                    Component text = column.isItem() ? column.item(row.value()).caption() : column.display(row.value());
                    for (var run : StyledTextLayout.runs(text)) for (String word : run.text().split("\\s+"))
                        check(FONT.width(Component.literal(word).setStyle(run.style())) <= available,
                                "Column splits a complete localized token: " + word + " at " + width);
                }
            }
        }
    }

    private static void keyboardAndLinks() throws Exception {
        AtomicReference<String> opened = new AtomicReference<>();
        Component link = StyledTextLayout.link(Component.literal("Transcendent related Skill with a wrapped name")
                .withColor(AscendanceUiPalette.SOULBOUND), "destination/inline");
        var tableData = new ReadOnlyDataTable<Component>(List.of(ReadOnlyDataTable.Column.text(
                "name", Component.literal("Prerequisite"), 60, 1, value -> value, null)),
                java.util.stream.IntStream.range(0, 12).mapToObj(i -> new ReadOnlyDataTable.Row<>("row/" + i, link)).toList());
        SemanticDocument document = new SemanticDocument(Component.literal("Heading"), List.of(
                new SemanticDocument.Paragraph(Component.literal("Before ").append(link)),
                new SemanticDocument.Table<>(tableData, true),
                new SemanticDocument.Links(List.of(new SemanticDocument.Link("destination/footer",
                        Component.literal("A long final related-page link that wraps at narrow widths"), SemanticDocument.LinkRelation.RELATED)))));
        for (int width : List.of(160, 300, 800)) {
            var article = new ContentViewport((g, i, b, t) -> { }, opened::set);
            article.prepare(FONT, new UiBounds(10, 20, width, 100), document);
            article.focused(true);
            int visited = 0;
            while (article.focusStep(1)) {
                article.key(257, 0, 0); visited++;
                check(visited < 20, "Composite Tab traversal cannot exit article");
            }
            check(visited >= 4 && "destination/footer".equals(opened.get()), "Keyboard cannot reach article footer links");
            check(article.scrollOffset() > 0, "Keyboard did not reveal final wrapped link");
            var hitField = ContentViewport.class.getDeclaredField("linkHits"); hitField.setAccessible(true);
            boolean clicked = false;
            for (Object hit : (List<?>) hitField.get(article)) {
                var method = hit.getClass().getDeclaredMethod("bounds"); method.setAccessible(true);
                UiBounds bounds = (UiBounds) method.invoke(hit);
                if (bounds.y() >= 20 && bounds.y() < 120) {
                    opened.set(null); article.click(bounds.x() + 1, bounds.y() + 1, 0);
                    check(opened.get() != null, "Scrolled/wrapped hit region misses its visible link"); clicked = true;
                }
            }
            check(clicked, "Final link is inaccessible after full article scroll");
        }
        var bounded = new ReadOnlyDataTableView<>(tableData);
        bounded.prepare(FONT, new UiBounds(10, 20, 180, 120)); bounded.key(269);
        for (var hit : bounded.textLinks()) check(hit.bounds().y() >= 20 + 1 + bounded.headerHeight()
                        && hit.bounds().bottom() <= 139, "Offscreen inline link escaped its table body clip");

        var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), 500, 400, false);
        var ui = new FullscreenComposition();
        var scene = new FullscreenComposition.Builder("keyboard", frame)
                .control(new FullscreenComposition.Control("disabled", new UiBounds(0, 0, 80, 20), Component.literal("Back"),
                        false, false, FullscreenControls.Style.ACTION, 0xFFFFFF, () -> opened.set("disabled")))
                .control(new FullscreenComposition.Control("action", new UiBounds(90, 0, 80, 20), Component.literal("Open"),
                        true, false, FullscreenControls.Style.ACTION, 0xFFFFFF, () -> opened.set("action"))).build();
        ui.update(scene); ui.mouseClicked(100, 10, 0);
        check(!ui.keyboardFocused("action"), "Mouse click restored the unwanted white focus outline");
        ui.keyPressed(258, 0, 0);
        check(ui.keyboardFocused("action"), "Tab lacks a visible keyboard-only focus outline");
        ui.update(scene); check(ui.keyboardFocused("action"), "Recomposition lost keyboard input modality");
        ui.mouseDragged(100, 10, 0, 0, 0);
        check(!ui.keyboardFocused("action"), "Mouse drag retained keyboard-only outline");
        ui.keyPressed(258, 0, 0); opened.set(null); ui.keyPressed(257, 0, 0);
        check("action".equals(opened.get()), "Keyboard activated a disabled history control");
    }

    private static void itemSearchAndHistory() {
        var ids = List.of("apple", "coal", "diamond", "emerald", "gold_ingot", "iron_ingot", "lapis_lazuli",
                "quartz", "redstone", "stone", "wheat", "wooden_sword");
        var rows = ids.stream().map(name -> new ItemEssenceTooltipClientState.YieldRow(ResourceLocation.withDefaultNamespace(name),
                List.of(new ItemEssenceTooltipClientState.Yield(ResourceLocation.parse("essence_ascendance:offense"), 1L)))).toList();
        var snapshot = new ItemEssenceTooltipClientState.YieldSnapshot(1, true,
                rows.stream().collect(java.util.stream.Collectors.toMap(ItemEssenceTooltipClientState.YieldRow::itemId,
                        ItemEssenceTooltipClientState.YieldRow::outputs)), rows);
        var index = ArchiveSearch.index(ArchiveCatalog.DEFAULT, snapshot);
        check(index.results("missing durability").stream().anyMatch(r -> r.target().contains("lifecycle")),
                "Search does not index rich real article content");
        check(index.results("diamond").stream().anyMatch(r -> r.target().equals("yield:minecraft:diamond")),
                "Global Search is missing canonical item targets");
        var unavailable = new ItemEssenceTooltipClientState.YieldSnapshot(2, false, Map.of(), List.of());
        check(ArchiveSearch.index(ArchiveCatalog.DEFAULT, unavailable).entries().stream().noneMatch(r -> r.target().startsWith("yield:")),
                "Incomplete item mapping leaks into Search");
        var navigator = new ArchiveNavigator(ArchiveCatalog.DEFAULT, ArchiveNavigationState.Snapshot.initial());
        navigator.selectMode(ArchiveMode.SEARCH); navigator.setQuery("sword");
        navigator.selectSearchResult("yield:minecraft:wooden_sword"); navigator.setListScroll(7);
        navigator.openSelectedSearchResult();
        for (int width : List.of(300, 900)) {
            var browser = new ItemYieldBrowser(navigator.yieldBrowser(), navigator::setYieldBrowser);
            var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), width, 420, true);
            var builder = new FullscreenComposition.Builder("browser", frame);
            browser.compose(builder, frame.content(), FONT, snapshot, PresentationContext.capture().revision());
            check(browser.state().selectedRow().equals(ResourceLocation.withDefaultNamespace("wooden_sword"))
                            && browser.state().scroll() > 0 && !browser.state().revealSelection(),
                    "Deep-linked last item stays above/below the visible viewport");
            var saved = browser.state();
            navigator.openTarget("essence_ascendance:reference/machines/focus"); navigator.back();
            check(navigator.yieldBrowser().equals(saved), "Back did not preserve exact browser state");
            navigator.openYieldItem(saved.selectedRow()); check(navigator.canGoForward(), "Current item cleared Forward");
            navigator.openYieldItem(ResourceLocation.withDefaultNamespace("coal"));
            check(!navigator.canGoForward(), "Distinct row deep link was incorrectly treated as current destination");
            navigator.back();
        }
        navigator.back();
        check(navigator.mode() == ArchiveMode.SEARCH && navigator.query().equals("sword")
                        && navigator.selectedResult().equals("yield:minecraft:wooden_sword") && navigator.listScroll() == 7,
                "Search item history did not restore query/selected result/scroll");
    }

    private static void loadingScroll() {
        var article = new ContentViewport((g, i, b, t) -> { }, target -> { });
        var document = new SemanticDocument(Component.literal("Long article"), java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> (SemanticDocument.Block) new SemanticDocument.Paragraph(Component.literal("Paragraph " + i))).toList());
        UiBounds bounds = new UiBounds(0, 0, 300, 120);
        article.prepare(FONT, bounds, document, true); article.key(269, 0, 0);
        int offset = article.scrollOffset();
        article.prepare(FONT, bounds, new SemanticDocument(Component.literal("Loading"), List.of()), false);
        check(article.scrollOffset() == offset, "Loading truncated the remembered article position");
        article.prepare(FONT, bounds, document, true);
        check(article.scrollOffset() == offset, "Ready document did not restore position after a loading interval");
        check(PresentationMetric.DisplayConversion.NATIVE.format(12345).equals("12345"), "Exact integer rounded to plausible wrong value");
        check(PresentationMetric.DisplayConversion.NATIVE.format(0.00001234).equals("0.00001234"), "Small rate became zero");
    }

    private static void semanticArguments(RuntimeBalanceDefinition runtime) {
        for (var skill : SkillRegistry.values()) {
            Component description = Component.translatable(skill.descriptionTranslationKey());
            check(ArchiveText.decorate(description).getString().equals(description.getString()), "Semantic links changed Skill meaning");
            var projection = com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData.project(skill, PresentationContext.catalog(runtime));
            for (var rank : projection.ranks()) for (var effect : rank.effects())
                check(ArchiveText.decorate(effect.prose()).getString().equals(effect.prose().getString()),
                        "Semantic links changed numeric effect arguments: " + skill.id());
        }
        for (String key : List.of("archive.reference.equipment.lifecycle.durability", "archive.reference.equipment.lifecycle.death")) {
            Component text = ArchiveText.guide(key);
            for (var run : StyledTextLayout.runs(text)) if (run.text().equals("Fractured") || run.text().equals("Soulbound"))
                check(run.style().getColor() != null && run.style().getColor().getValue() ==
                                (run.text().equals("Fractured") ? AscendanceUiPalette.FRACTURED : AscendanceUiPalette.SOULBOUND),
                        "Inline semantic keyword lost canonical color");
        }
    }

    private static void nativeSafetyWiring() throws Exception {
        String root = "com/mistaboom/essence_ascendance/client/";
        var machine = read(root + "MachineContainerScreen");
        var gate = method(machine, "isHovering");
        check(calls(gate).stream().anyMatch(c -> c.name.equals("pointerOwner")), "Machine hover bypasses overlay pointer ownership");
        var nativeContainer = read("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen");
        check(nativeContainer.methods.stream().filter(m -> m.name.equals("isHovering") && m.desc.startsWith("(Lnet/minecraft/world/inventory/Slot;"))
                        .flatMap(m -> calls(m).stream()).anyMatch(c -> c.name.equals("isHovering") && c.desc.equals("(IIIIDD)Z")),
                "Native slot hover no longer dispatches the shared overlay gate");
        var nexus = read(root + "AscendanceNexusScreen");
        check(calls(method(nexus, "requestArchive")).stream().anyMatch(c -> c.name.equals("hasStagedChanges")),
                "Archive access bypasses staged draft policy");
        var completion = calls(method(nexus, "consumeTransactionResult"));
        int open = -1, close = -1;
        for (int i = 0; i < completion.size(); i++) {
            if (completion.get(i).name.equals("onClose")) close = i;
            if (completion.get(i).owner.equals(root + "AscendanceArchiveScreen")) open = i;
        }
        check(open > close && close >= 0 && completion.stream().anyMatch(c -> c.name.equals("nexusRevision")),
                "Archive completion does not close native menu after authoritative state");
        check(nexus.methods.stream().noneMatch(m -> Set.of("formatBonus", "formatDecimal", "trimToWidth", "wrapTwoLines").contains(m.name)),
                "Superseded presentation algorithms remain duplicated in Nexus");
    }

    private static void changedSourceFixtures(RuntimeBalanceDefinition runtime) {
        var json = runtime.toJson();
        json.addProperty("pylonRadius", 7.25);
        json.getAsJsonObject("infuser").getAsJsonObject("repair").addProperty("essencePerDurability", 12345);
        var durability = json.getAsJsonObject("shield").getAsJsonObject("durability");
        String tier = durability.keySet().iterator().next(); durability.addProperty(tier, 17003);
        json.getAsJsonObject("attunement").getAsJsonObject("policy").addProperty("maximumAcceleration", 0.42);
        json.getAsJsonObject("effects").getAsJsonObject("frenzy").addProperty("chainTimeoutTicks", 417);
        var changed = RuntimeBalanceDefinition.fromJson(json);
        var context = PresentationContext.catalog(changed);
        check(com.mistaboom.essence_ascendance.client.presentation.MachinePresentationData.project(context).pylonLinkRadius() == 7.25,
                "Changed machine setting did not reach shared presentation");
        var equipment = com.mistaboom.essence_ascendance.client.presentation.EquipmentPresentationData.project(context);
        check(equipment.repairEssencePerDurability() == 12345 && equipment.tierBaselines().stream()
                        .anyMatch(value -> value.shieldDurability() == 17003), "Changed applied equipment settings are stale");
        check(com.mistaboom.essence_ascendance.client.presentation.MechanicsPresentationData.project(context)
                        .attunementPolicy().maximumAcceleration() == 0.42, "Changed seal acceleration is stale");
        check(ReferenceDocuments.equipmentLifecycle(context).searchableText().stream().anyMatch(text -> text.getString().contains("12345")),
                "Changed exact repair value did not reach localized article");

        var skill = SkillRegistry.require(SkillIds.FRENZY);
        var nativeEffects = com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling.applyResolved(
                changed.config().skillEffects(), Map.of(skill.id(), 1), changed.skillCurves());
        check(nativeEffects.frenzy().chainTimeoutTicks() == 417, "Fixture did not change an applied native getter");
        check(ArchiveDocuments.skillReference(skill, PresentationContext.catalog(changed)).searchableText().stream()
                        .anyMatch(text -> text.getString().contains("20.85")), "Applied tick change did not reach localized seconds");
        check(EssenceConfigManager.clientRuntime() == runtime, "Changed-value fixtures replaced installed runtime");
    }

    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = ArchiveAuditTest.class.getResourceAsStream("/" + name + ".class")) {
            new ClassReader(Objects.requireNonNull(stream)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }
    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }
    private static void installLanguage(Language original) throws Exception {
        Map<String, String> values = new HashMap<>();
        try (var stream = ArchiveAuditTest.class.getResourceAsStream("/assets/essence_ascendance/lang/en_us.json")) {
            JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
        }
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, original.getOrDefault(key, fallback)); }
            @Override public boolean has(String key) { return values.containsKey(key) || original.has(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.composite(StyledTextLayout.runs(text).stream()
                        .map(run -> FormattedCharSequence.forward(run.text(), run.style())).toList());
            }
        });
    }
    private static final class MetricFont extends Font {
        private final StringSplitter splitter = new StringSplitter((codePoint, style) -> 6);
        private MetricFont() { super(ignored -> null, false); }
        @Override public int width(String text) { return (int) splitter.stringWidth(text); }
        @Override public int width(FormattedText text) { return (int) splitter.stringWidth(text); }
        @Override public int width(FormattedCharSequence text) { return (int) splitter.stringWidth(text); }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            return splitter.splitLines(text, Math.max(1, width), Style.EMPTY).stream().map(Language.getInstance()::getVisualOrder).toList();
        }
        @Override public String plainSubstrByWidth(String text, int width) { return splitter.plainHeadByWidth(text, width, Style.EMPTY); }
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
