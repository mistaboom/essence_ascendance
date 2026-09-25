package com.mistaboom.essence_ascendance.archive;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.client.ClientPacketDispatch;
import com.mistaboom.essence_ascendance.client.ItemEssenceTooltipClientState;
import com.mistaboom.essence_ascendance.client.archive.*;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.content.EntryListView;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Search acceptance through canonical documents and the actual shared editor/router; no graphics context. */
public final class ArchiveSearchTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        Language original = Language.getInstance();
        Map<String, String> translations = new HashMap<>();
        try (var stream = ArchiveSearchTest.class.getResourceAsStream("/assets/essence_ascendance/lang/en_us.json")) {
            JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8))
                    .getAsJsonObject().entrySet().forEach(e -> translations.put(e.getKey(), e.getValue().getAsString()));
        }
        Language language = language(original, translations);
        Language.inject(language);
        EssenceConfigManager.installClient(RuntimeBalanceDefinition.bootstrap());
        try {
            discoverability(); matching(); cache(language, translations); editing();
        } finally { EssenceConfigManager.clearClient(); Language.inject(original); }
        System.out.println("ArchiveSearchTest: " + checks + " global Search assertions PASS");
    }
    private static ItemEssenceTooltipClientState.YieldSnapshot yields(long generation, boolean ready) {
        var row = new ItemEssenceTooltipClientState.YieldRow(ResourceLocation.withDefaultNamespace("diamond"),
                List.of(new ItemEssenceTooltipClientState.Yield(ResourceLocation.parse("essence_ascendance:offense"), 1L)));
        return new ItemEssenceTooltipClientState.YieldSnapshot(generation, ready,
                ready ? Map.of(row.itemId(), row.outputs()) : Map.of(), ready ? List.of(row) : List.of());
    }
    private static void discoverability() {
        var catalog = ArchiveCatalog.DEFAULT;
        var index = ArchiveSearch.index(catalog, yields(1, true));
        for (var entry : catalog.entries()) {
            var matches = index.results(entry.title().getString());
            var result = matches.stream().filter(r -> r.target().equals(entry.id().toString())).findFirst().orElseThrow();
            check(index.results(entry.mode().label().getString() + " " + catalog.section(entry.section()).label().getString())
                    .stream().anyMatch(r -> r.target().equals(result.target())), "Missing mode/section discovery: " + entry.id());
            check(result.summary().getString().equals(entry.mode().label().getString() + " › "
                    + catalog.section(entry.section()).label().getString()), "Source path missing");
            check(result.title().getStyle().equals(entry.title().getStyle()), "Result lost canonical color/emphasis");
            check(catalog.searchableText(entry).contains(entry.summary()), "Compact display erased rich metadata");
            check(!result.title().getString().contains("essence_ascendance:"), "Developer ID leaked into title");
        }
        for (String query : List.of("seals", "repair", "Fracture", "harvesting", "percentage points", "missing durability"))
            check(!index.results(query).isEmpty(), "Missing real vocabulary/body/metric: " + query);
        check(index.results("repair").stream().anyMatch(r -> r.target().contains(":guide/"))
                && index.results("repair").stream().anyMatch(r -> r.target().contains(":reference/")), "Cross-mode body search restricted");
        check(index.results("diamond offense").stream().anyMatch(r -> r.target().equals("yield:minecraft:diamond")), "Missing yield category metadata");
        check(index.entries().stream().noneMatch(r -> r.target().endsWith("/overview") || r.target().endsWith("/progression")), "Removed placeholder returned");
        var missingYields = ArchiveSearch.index(catalog, yields(2, false));
        check(missingYields.entries().size() == catalog.entries().size() && !missingYields.results("repair").isEmpty(), "Yield readiness disables articles");
        check(index.entries().stream().map(ArchiveSearch.Result::target).distinct().count() == index.entries().size(), "Duplicate canonical targets");
        check(ArchiveSearch.status(0, true).getString().equals("No matching results"), "No-match status not localized");
        check(ArchiveSearch.status(19, true).getString().contains("19"), "Result count missing");
        check(ArchiveSearch.status(0, false).getString().contains("articles remain searchable"), "Unavailable yield status missing");
    }
    private static ArchiveSearch.Result result(String target, String title, String body) {
        return new ArchiveSearch.Result(target, Component.literal(title), Component.literal("Reference › Test"), title + " " + body);
    }
    private static void matching() {
        var exact = result("b", "Repair", "durability");
        var index = new ArchiveSearch.Index(List.of(result("a", "Other", "repair"), exact,
                result("c", "Repair equipment", ""), exact, result("d", "Épée 星", "CAFÉ  astral"),
                result("yield:minecraft:diamond", "Repair", "")));
        check(index.results("  REPAIR  ").stream().map(ArchiveSearch.Result::target).toList()
                .equals(List.of("b", "c", "a", "yield:minecraft:diamond")), "Title ranking/dedup/page protection failed");
        check(index.results("cafe\u0301\u00a0\u2003星").size() == 1, "Unicode normalization/multi-term matching failed");
        check(index.results("astral\t\nÉPÉE").size() == 1, "Whitespace/case matching failed");
        check(index.results("unknown").isEmpty(), "No-match query returned unrelated results");
        var tied = new ArchiveSearch.Index(List.of(result("z", "Same", ""), result("a", "Same", "")));
        check(tied.results("same").getFirst().target().equals("a"), "Tie order depends on input order");
        var manyItems = new ArrayList<ArchiveSearch.Result>();
        for (int i = 0; i < 500; i++) manyItems.add(result("yield:test:" + i, "Repair " + i, ""));
        manyItems.add(result("article", "Durability", "repair"));
        var matches = new ArchiveSearch.Index(manyItems).results("repair");
        check(matches.size() == 501 && matches.getFirst().target().equals("article"), "Item group hides explanatory pages or drops matches");
    }
    private static void cache(Language original, Map<String, String> translations) {
        var cache = new ArchiveSearch.Cache(); var snapshot = yields(10, true);
        var catalog = ArchiveCatalog.DEFAULT; var revision = PresentationContext.capture().revision();
        var first = cache.get(catalog, snapshot, revision);
        for (String query : List.of("repair", "skills", "seals", "")) {
            first.results(query);
            check(cache.get(catalog, snapshot, revision) == first, "Keystrokes regenerate documents");
        }
        String key = "guide.essence_ascendance.archive.entry.guide.beginning.welcome.title";
        var changed = new HashMap<>(translations); changed.put(key, "Nouvelle étoile");
        Language.inject(language(original, changed));
        var translated = cache.get(catalog, snapshot, PresentationContext.capture().revision());
        check(translated != first && translated.results("nouvelle étoile").stream()
                .anyMatch(r -> r.target().endsWith("guide/beginning/welcome")), "Language refresh retained stale index");
        Language.inject(original);
        var beforeReload = cache.get(catalog, snapshot, PresentationContext.capture().revision());
        ClientPacketDispatch.resourcesReloaded();
        check(cache.get(catalog, snapshot, PresentationContext.capture().revision()) != beforeReload, "Resource reload retained index");
        var beforeRuntime = cache.get(catalog, snapshot, PresentationContext.capture().revision());
        EssenceConfigManager.installClient(RuntimeBalanceDefinition.bootstrap());
        check(cache.get(catalog, snapshot, PresentationContext.capture().revision()) != beforeRuntime, "Runtime refresh retained index");
        var unavailable = cache.get(catalog, yields(11, false), new PresentationContext.Revision(0, -1, false, 999, 0));
        check(unavailable.entries().stream().noneMatch(r -> r.target().startsWith("yield:")), "Connection/mapping reset retained old items");
        check(cache.get(catalog, yields(12, true), PresentationContext.capture().revision()).entries().stream()
                .anyMatch(r -> r.target().equals("yield:minecraft:diamond")), "New mapping generation did not refresh");
        AtomicInteger builds = new AtomicInteger();
        var subject = new ArchiveSubject(ResourceLocation.parse("test:subject"), Component.literal("Subject"), List.of());
        var entry = new ArchiveEntry(ResourceLocation.parse("test:article"), ArchiveMode.GUIDE,
                ArchiveSection.GUIDE_BEGINNING.id(), subject.id(), Component.literal("Fixture"), Component.literal("Summary"), () -> {
                    builds.incrementAndGet(); return new SemanticDocument(Component.literal("Fixture"), List.of());
                });
        var replacement = new ArchiveCatalog(List.of(ArchiveSection.GUIDE_BEGINNING), List.of(subject), List.of(entry));
        var noYields = yields(13, false);
        var projected = cache.get(replacement, noYields, PresentationContext.capture().revision());
        for (String query : List.of("f", "fi", "fix", "fixture")) projected.results(query);
        check(projected.entries().size() == 1 && builds.get() == 1, "Catalog replacement or query caching failed");
    }
    private static void editing() {
        AtomicReference<String> clipboard = new AtomicReference<>("étoile\u00a0星");
        AtomicInteger edits = new AtomicInteger();
        var navigator = new ArchiveNavigator(ArchiveCatalog.DEFAULT, ArchiveNavigationState.Snapshot.initial());
        navigator.selectMode(ArchiveMode.SEARCH);
        int visits = navigator.snapshot().history().size();
        var field = new FullscreenTextField("", Component.literal("Search"), value -> {
            navigator.setQuery(value); edits.incrementAndGet();
        }, clipboard::get, clipboard::set);
        Font font = new Font(ignored -> null, false) {
            @Override public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
            @Override public int width(FormattedText text) { return width(text.getString()); }
        };
        field.bounds(new UiBounds(10, 20, 90, 24)); field.prepare(font);
        var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), 300, 300, false);
        var router = new FullscreenComposition();
        router.update(new FullscreenComposition.Builder("search", frame)
                .region(new FullscreenComposition.Region("field", new UiBounds(10, 20, 90, 24), (g, x, y, tick) -> {}, field, true)).build());
        router.mouseClicked(20, 25, 0); router.mouseReleased(20, 25, 0);
        check(router.keyPressed(86, 0, 2) && field.query().equals("étoile 星"), "Paste not routed/normalized");
        router.keyPressed(65, 0, 2); router.keyPressed(67, 0, 2);
        check(clipboard.get().equals("étoile 星"), "Select-all/copy failed");
        router.keyPressed(88, 0, 2); check(field.query().isEmpty(), "Cut did not delete selection");
        router.keyPressed(86, 0, 2); router.keyPressed(268, 0, 0); router.keyPressed(262, 0, 1);
        check(field.selection().equals("é"), "Shift selection failed");
        router.charTyped('E', 0); check(field.query().startsWith("Etoile"), "Typing E failed to replace selection");
        check(!field.key(256, 0, 0), "Editor consumes Escape instead of host close");
        field.query("a😀b"); field.key(263, 0, 0); field.key(259, 0, 0);
        check(field.query().equals("ab"), "Backspace splits supplementary Unicode code point");
        field.query("alpha beta"); field.key(263, 0, 2); field.key(259, 0, 2);
        check(field.query().equals("beta"), "Control word editing failed");
        field.query("abcdefghijklmnopqrstuvwxyz");
        check(field.visibleStart() > 0, "Long query does not reveal caret");
        field.key(268, 0, 0); check(field.visibleStart() == 0, "Home does not restore start");
        field.click(28, 25, 0); check(field.cursor() == 2, "Mouse ignores clicked glyph position");
        field.drag(46, 25, 0, 18, 0); field.release(46, 25, 0);
        check(field.selection().equals("cde"), "Mouse drag selection failed");
        check(edits.get() > 0 && navigator.snapshot().history().size() == visits, "Editing creates history micro-actions");
        var list = new EntryListView<Integer>(ignored -> {});
        var rows = java.util.stream.IntStream.range(0, 20).mapToObj(i -> new EntryListView.Entry<>("row/" + i,
                Component.literal("Long real result title number " + i), Component.literal("Reference › Skills"), i)).toList();
        for (int width : List.of(120, 300, 900)) {
            list.prepare(new UiBounds(10, 20, width, 92), rows); list.restore("row/19", 18);
            check(list.overflowTextAt(font, 12, 25).isPresent() == (width < 210), "Overflow labels disagree with available result width");
            if (width == 120) check(list.overflowTextAt(font, 12, 25).orElseThrow().getString().endsWith("18"), "Hover ignores results scroll");
            check(list.overflowTextAt(font, 12, 19).isEmpty(), "Hover escapes results viewport");
        }
    }
    private static Language language(Language original, Map<String, String> values) {
        return new Language() {
            @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, original.getOrDefault(key, fallback)); }
            @Override public boolean has(String key) { return values.containsKey(key) || original.has(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return original.getVisualOrder(text); }
        };
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
