package com.mistaboom.essence_ascendance.achievement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.*;
//? if <26.1.2
import net.minecraft.advancements.critereon.ImpossibleTrigger;
//? if >=26.1.2
/*import net.minecraft.advancements.criterion.ImpossibleTrigger;*/
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Resource ownership, native layout and discovery outcomes; pixel rendering belongs to the client fixture. */
public final class AchievementPresentationTest {
    private static int checks;
    private static final String PREFIX = "essence_ascendance:achievements/";

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        var previousLocale = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar"));
            check(AchievementPresentation.selectedIds().size() == 69
                            && AchievementPresentation.selectedIds().contains(ResourceLocation.parse("essence_ascendance:achievements/a022")),
                    "First presentation initialization uses ASCII save identities under non-Latin number formatting");
        } finally { Locale.setDefault(Locale.Category.FORMAT, previousLocale); }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var entries = resources();
        nativeDiscoveryAndLayout(entries);
        discoveryBoundaries();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("Achievement presentation: " + checks + " resource, native layout and discovery checks passed");
    }

    private static Map<ResourceLocation, JsonObject> resources() throws Exception {
        Path repository = Path.of(System.getProperty("essence.repository.root"));
        JsonObject catalog = read(repository.resolve("docs/achievements/catalog.json"));
        JsonObject presentation = read(repository.resolve("docs/achievements/presentation.json"));
        JsonObject language = resource("assets/essence_ascendance/lang/en_us.json");
        Set<String> authoredSources = new HashSet<>();
        read(repository.resolve("art/asset-manifest.json")).getAsJsonArray("assets").forEach(asset ->
                authoredSources.add("art/" + asset.getAsJsonObject().get("source").getAsString()));
        check(presentation.get("schemaVersion").getAsInt() == 1, "Known presentation inventory schema");
        check(presentation.get("root").getAsString().equals(AchievementPresentation.ROOT.toString()), "Selected root identity");
        Map<Integer, JsonObject> approved = new HashMap<>();
        catalog.getAsJsonArray("achievements").forEach(value -> approved.put(value.getAsJsonObject().get("id").getAsInt(), value.getAsJsonObject()));
        Map<ResourceLocation, JsonObject> entries = new LinkedHashMap<>();
        Set<String> frames = new HashSet<>();
        int roots = 0;
        for (var value : presentation.getAsJsonArray("entries")) {
            var entry = value.getAsJsonObject();
            int numeric = entry.get("id").getAsInt();
            check(approved.containsKey(numeric), "Presentation uses only approved numeric IDs: " + numeric);
            String id = entry.get("resourceId").getAsString();
            check(id.equals(approved.get(numeric).get("resourceId").getAsString()), "Presentation stable resource: " + numeric);
            check(entries.put(ResourceLocation.parse(id), entry) == null, "No duplicate presentation ID: " + id);
            var advancement = resource("data/essence_ascendance/advancement/achievements/a%03d.json".formatted(numeric));
            var display = advancement.getAsJsonObject("display");
            check(display != null, "Every selected rule has final display data: " + id);
            check(!display.get("hidden").getAsBoolean(), "No selected challenge is secretly hidden: " + id);
            check(display.get("show_toast").getAsBoolean() && entry.get("showToast").getAsBoolean(), "Native toast enabled: " + id);
            String frame = entry.get("frame").getAsString(); frames.add(frame);
            check(Set.of("task", "goal", "challenge").contains(frame), "Native frame type: " + id);
            check(display.get("frame").getAsString().equals(frame), "Frame inventory matches: " + id);
            boolean chat = entry.get("announceToChat").getAsBoolean();
            check(display.get("announce_to_chat").getAsBoolean() == chat && (!chat || frame.equals("challenge")), "Chat limited to documented major challenges: " + id);
            check(!advancement.has("rewards"), "Presentation adds no gameplay rewards: " + id);
            check(!advancement.get("sends_telemetry_event").getAsBoolean(), "No telemetry introduced: " + id);
            String criterion = numeric == 22 ? "dissolved" : "achieved";
            var criteria = advancement.getAsJsonObject("criteria");
            check(criteria.size() == 1 && criteria.has(criterion), "Stable completion criterion only: " + id);
            check(criteria.getAsJsonObject(criterion).get("trigger").getAsString().equals("minecraft:impossible"), "Only authoritative server awards: " + id);
            check(advancement.getAsJsonArray("requirements").toString().equals("[[\"" + criterion + "\"]]"), "Parent layout is not an award prerequisite: " + id);
            String titleKey = "advancement.essence_ascendance.a%03d".formatted(numeric);
            check(display.getAsJsonObject("title").size() == 1 && display.getAsJsonObject("title").get("translate").getAsString().equals(titleKey), "Native translated title key: " + id);
            check(language.get(titleKey).getAsString().equals(approved.get(numeric).get("name").getAsString()), "Exact approved title: " + id);
            check(display.getAsJsonObject("description").size() == 1 && display.getAsJsonObject("description").get("translate").getAsString().equals(titleKey + ".description"), "Native translated description key: " + id);
            check(language.get(titleKey + ".description").getAsString().equals(entry.get("description").getAsString()), "Reviewed description inventory matches: " + id);
            check(!entry.get("description").getAsString().isBlank(), "Description is present: " + id);
            check(!entry.get("lane").getAsString().isBlank(), "Every item has a documented layout lane: " + id);
            if (entry.get("parent").isJsonNull()) {
                roots++;
                check(id.equals(AchievementPresentation.ROOT.toString()) && !advancement.has("parent"), "Only the selected entry is a root");
                String background = presentation.get("background").getAsString();
                if (System.getProperty("essence.minecraft").startsWith("26.")) background = background.replace(":textures/", ":").replace(".png", "");
                check(display.get("background").getAsString().equals(background), "Native background format for this version");
            } else {
                check(advancement.get("parent").getAsString().equals(entry.get("parent").getAsString()), "Documented layout parent: " + id);
                check(!display.has("background"), "Only root owns tab background: " + id);
            }
            check(display.getAsJsonObject("icon").equals(entry.getAsJsonObject("icon")), "ItemStack icon/components inventory matches: " + id);
            icon(entry, repository, authoredSources);
        }
        check(roots == 1 && entries.size() == 69, "One selected root and exactly 69 earnable displays");
        check(entries.keySet().equals(AchievementPresentation.selectedIds()), "UI discovery is bounded to exactly the approved roster");
        check(frames.equals(Set.of("task", "goal", "challenge")), "All native frame categories used consistently");
        for (var entry : entries.entrySet()) {
            Set<ResourceLocation> seen = new HashSet<>();
            var cursor = entry.getKey();
            while (cursor != null) {
                check(seen.add(cursor), "No parent cycle: " + entry.getKey());
                check(entries.containsKey(cursor), "Parents are selected achievements in this tab: " + cursor);
                var parent = entries.get(cursor).get("parent");
                cursor = parent.isJsonNull() ? null : ResourceLocation.parse(parent.getAsString());
            }
        }
        var legacy = resource("data/essence_ascendance/advancement/water_skiing.json");
        check(!legacy.has("display"), "Legacy progress cannot become a seventieth visible challenge, even when completed");
        check(legacy.getAsJsonObject("criteria").size() == 1 && legacy.getAsJsonObject("criteria").has("travel"), "Legacy saved criterion retained");
        check(legacy.getAsJsonArray("requirements").toString().equals("[[\"travel\"]]"), "Legacy saved requirement retained");
        check(!legacy.has("rewards"), "Legacy progress has no selected successor reward/migration");
        return entries;
    }

    private static void icon(JsonObject entry, Path repository, Set<String> authoredSources) throws Exception {
        var icon = entry.getAsJsonObject("icon");
        var id = ResourceLocation.parse(icon.get("id").getAsString());
        check(!icon.has("count") || icon.get("count").getAsInt() == 1, "Single real item icon: " + id);
        var provenance = entry.getAsJsonObject("provenance");
        check(!provenance.get("source").getAsString().isBlank() && !provenance.get("kind").getAsString().isBlank(), "Icon provenance recorded: " + id);
        if (id.getNamespace().equals("minecraft")) {
            check(BuiltInRegistries.ITEM.containsKey(id) && !id.getPath().equals("air"), "Vanilla icon is an actual registered item: " + id);
        } else {
            check(id.getNamespace().equals("essence_ascendance"), "No external art or fake icon namespace: " + id);
            String source = provenance.get("source").getAsString();
            check(authoredSources.contains(source) && Files.isRegularFile(repository.resolve(source)), "Mod icon reuses an existing manifest source: " + id);
            resource("assets/essence_ascendance/models/item/" + id.getPath() + ".json");
            if (System.getProperty("essence.minecraft").startsWith("26.")) resource("assets/essence_ascendance/items/" + id.getPath() + ".json");
        }
        if (icon.has("components")) {
            var components = icon.getAsJsonObject("components");
            check(components.keySet().equals(Set.of("minecraft:custom_data")), "Icons carry appearance data only: " + id);
            var custom = components.getAsJsonObject("minecraft:custom_data");
            CustomData.CODEC.parse(JsonOps.INSTANCE, custom).getOrThrow();
            if (custom.has("essence_ascendance_equipment")) {
                String tier = custom.getAsJsonObject("essence_ascendance_equipment").get("tier").getAsString();
                var selected = Arrays.stream(EquipmentTier.values()).filter(value -> value.serializedName().equals(tier)).findFirst();
                check(selected.isPresent(), "Explicit valid equipment tier: " + id);
                check(CustomData.CODEC.parse(JsonOps.INSTANCE, custom).getOrThrow().equals(EquipmentTierData.tierData(selected.orElseThrow())), "Tier appearance uses the canonical equipment component encoding: " + id);
            }
            if (custom.has("essence_ascendance_focus")) {
                String tier = custom.getAsJsonObject("essence_ascendance_focus").get("tier").getAsString();
                var selected = Arrays.stream(EssenceFocusTier.values()).filter(value -> value.serializedName().equals(tier)).findFirst();
                check(selected.isPresent() && id.getPath().equals("essence_focus"), "Explicit valid Focus tier on Focus icon");
                check(CustomData.CODEC.parse(JsonOps.INSTANCE, custom).getOrThrow().equals(EssenceFocusData.tierData(selected.orElseThrow())), "Focus appearance uses the canonical Focus component encoding");
            }
        }
    }

    private static void nativeDiscoveryAndLayout(Map<ResourceLocation, JsonObject> entries) {
        var tree = new AdvancementTree();
        tree.addAll(entries.entrySet().stream().map(entry -> holder(entry.getKey(),
                entry.getValue().get("parent").isJsonNull() ? null : ResourceLocation.parse(entry.getValue().get("parent").getAsString()), false, false)).toList());
        check(tree.nodes().size() == 69, "Native tree resolves every selected parent");
        var root = tree.get(AchievementPresentation.ROOT);
        TreeNodePosition.run(root);
        Set<String> occupied = new HashSet<>();
        Map<ResourceLocation, AdvancementProgress> progress = new HashMap<>();
        for (var node : tree.nodes()) {
            var display = node.advancement().display().orElseThrow();
            check(Float.isFinite(display.getX()) && Float.isFinite(display.getY()), "Native position is finite");
            check(occupied.add(display.getX() + ":" + display.getY()), "Native nodes do not overlap");
            if (node.parent() != null) check(display.getX() > node.parent().advancement().display().orElseThrow().getX(), "Layout flows from parent to child");
            var state = new AdvancementProgress(); state.update(node.advancement().requirements());
            progress.put(node.holder().id(), state);
        }
        Map<ResourceLocation, Boolean> nativeVisible = new HashMap<>(), presented = new HashMap<>();
        AdvancementVisibilityEvaluator.evaluateVisibility(root, node -> progress.get(node.holder().id()).isDone(),
                (node, visible) -> nativeVisible.put(node.holder().id(), visible));
        check(nativeVisible.values().stream().noneMatch(Boolean::booleanValue), "Control: native unearned tree would be undiscovered");
        int[] predicateCalls = {0};
        AdvancementVisibilityEvaluator.evaluateVisibility(root, node -> { predicateCalls[0]++; return progress.get(node.holder().id()).isDone(); },
                AchievementPresentation.revealing((node, visible) -> check(presented.put(node.holder().id(), visible) == null, "One discovery output per native node")));
        check(predicateCalls[0] == 69 && presented.size() == 69 && presented.values().stream().allMatch(Boolean::booleanValue), "All 69 visible before earning root without altering completion predicate");
        check(progress.values().stream().noneMatch(AdvancementProgress::hasProgress), "Discovery awards no criteria or completion");
        var deep = tree.nodes().stream().max(Comparator.comparingDouble(node -> node.advancement().display().orElseThrow().getX())).orElseThrow();
        var deepProgress = progress.get(deep.holder().id());
        check(deepProgress.grantProgress("achieved") && deepProgress.isDone(), "A deep leaf completes independently of its unearned parents");
        check(!progress.get(root.holder().id()).hasProgress(), "Completing a leaf does not grant the selected root");
        deepProgress.revokeProgress("achieved");
        check(!deepProgress.isDone() && AchievementPresentation.discoverable(deep), "Native revoke removes completion while preserving discovery");
    }

    private static void discoveryBoundaries() {
        var root = node(AchievementPresentation.ROOT.toString(), null, false, false);
        var selected = node(PREFIX + "a001", root, false, false);
        check(AchievementPresentation.discoverable(selected), "Selected visible node revealed");
        check(!AchievementPresentation.discoverable(node(PREFIX + "a002", root, true, false)), "Explicit datapack hidden flag respected");
        check(!AchievementPresentation.discoverable(node(PREFIX + "a003", root, false, true)), "Display removed by datapack respected");
        check(!AchievementPresentation.discoverable(node(PREFIX + "a089", root, false, false)), "Deleted or unselected candidate never added to discovery");
        check(!AchievementPresentation.discoverable(node("essence_ascendance:water_skiing", root, false, false)), "Legacy identity is not a selected challenge");
        var hiddenAncestor = node(PREFIX + "a004", root, true, false);
        check(!AchievementPresentation.discoverable(node(PREFIX + "a005", hiddenAncestor, false, false)), "Explicitly hidden ancestor does not leak descendants");
        var vanillaRoot = node("minecraft:adventure/root", null, false, false);
        var reparented = node(PREFIX + "a001", vanillaRoot, false, false);
        check(!AchievementPresentation.discoverable(reparented), "Datapack reparenting outside selected tab retains native behavior");
        check(!AchievementPresentation.discoverable(vanillaRoot), "Vanilla discovery unaffected");
        boolean[] result = {false};
        var output = AchievementPresentation.revealing((node, visible) -> result[0] = visible);
        output.accept(vanillaRoot, true); check(result[0], "Native completed visibility remains true");
        output.accept(vanillaRoot, false); check(!result[0], "Native hidden visibility remains false outside selected tab");
    }

    private static AdvancementNode node(String id, AdvancementNode parent, boolean hidden, boolean absent) {
        var result = new AdvancementNode(holder(ResourceLocation.parse(id), parent == null ? null : parent.holder().id(), hidden, absent), parent);
        if (parent != null) parent.addChild(result);
        return result;
    }

    private static AdvancementHolder holder(ResourceLocation id, ResourceLocation parent, boolean hidden, boolean absent) {
        // The layout/discovery fixture uses a vanilla icon. Real selected item decoding is checked in a loaded client.
        //? if <26.1.2
        var icon = new ItemStack(Items.BOOK);
        //? if >=26.1.2
        /*var icon = new net.minecraft.world.item.ItemStackTemplate(Items.BOOK);*/
        var display = new DisplayInfo(icon, Component.literal(id.toString()), Component.literal("Fixture"), Optional.empty(), AdvancementType.TASK, true, false, hidden);
        return new AdvancementHolder(id, new Advancement(Optional.ofNullable(parent), absent ? Optional.empty() : Optional.of(display),
                AdvancementRewards.EMPTY, Map.of("achieved", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())),
                AdvancementRequirements.allOf(List.of("achieved")), false));
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = AchievementPresentationTest.class.getClassLoader().getResourceAsStream(path)) {
            check(stream != null, "Processed resource exists: " + path);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
