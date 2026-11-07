package com.mistaboom.essence_ascendance.achievement;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.advancements.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit real-client rendering probe. No world, grants, gameplay hooks or production registration. */
public final class AchievementPresentationNativeFixture {
    private static final List<AdvancementHolder> SELECTED = new ArrayList<>();
    private static final AdvancementTree TREE = new AdvancementTree();
    private static final List<String> REPORT = new ArrayList<>();
    private static volatile Throwable failure;
    private static volatile boolean saving;
    private static boolean started;
    private static int index;
    private static int waitTicks;
    private static int checks;
    private static Path output;
    private static long began;

    private AchievementPresentationNativeFixture() { }

    public static void init() {
        if (!Boolean.getBoolean("essence.achievement.presentation"))
            throw new IllegalStateException("Explicit achievement presentation fixture required");
        began = System.nanoTime();
        ClientTickEvent.CLIENT_POST.register(client -> {
            try {
                if (failure != null) throw new AssertionError("Native capture failed", failure);
                if (System.nanoTime() - began > TimeUnit.MINUTES.toNanos(5))
                    throw new AssertionError("Achievement presentation fixture timed out: " + client.screen);
                if (!started) {
                    if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
                    started = true;
                    prepare(client);
                    show(client);
                    return;
                }
                if (saving || --waitTicks > 0) return;
                if (index >= SELECTED.size()) {
                    REPORT.add("PASS: " + checks + " checks; " + SELECTED.size()
                            + " native tree/widget/tooltip/toast captures. No gameplay award claim.");
                    Files.write(output.resolve("presentation-report.txt"), REPORT, StandardCharsets.UTF_8);
                    System.out.println("AchievementPresentationNativeFixture: " + REPORT.get(REPORT.size()-1));
                    client.stop();
                    return;
                }
                saving = true;
                Path file = output.resolve(SELECTED.get(index).id().getPath().replace('/', '-') + ".png");
                //? if >=26.1.2 {
                /*Screenshot.takeScreenshot(client.getMainRenderTarget(), pixels -> {
                    try (pixels) { pixels.writeToFile(file); }
                    catch (Throwable e) { failure = e; }
                    finally { saving = false; }
                });*/
                //? }
                //? if <26.1.2 {
                try (var pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) { pixels.writeToFile(file); }
                saving = false;
                //? }
                index++;
                if (index < SELECTED.size()) show(client);
                else waitTicks = 4;
            } catch (Throwable e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static void prepare(Minecraft client) throws Exception {
        check(client.level == null && client.player == null, "Fixture must never enter a gameplay world");
        // 26 binds registered default ItemStack components on world join. This no-world
        // fixture reuses the repository's native initializer binding, before any icon is created.
        // It does not replace the registered defaults or synthesize any icon components.
        //? if >=26.1.2
        /*bindNativeComponents();*/
        output = Path.of(System.getProperty("essence.achievement.presentationOutput"));
        Files.createDirectories(output);
        Path root = Path.of(System.getProperty("essence.achievement.presentationResources"));
        Path definitions = root.resolve("data/essence_ascendance/advancement/achievements");
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .createSerializationContext(JsonOps.INSTANCE);
        List<AdvancementHolder> all = new ArrayList<>();
        try (var paths = Files.walk(definitions)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = path.getFileName().toString().replace(".json", "");
                var id = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "achievements/" + name);
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                var value = Advancement.CODEC.parse(ops, json).getOrThrow();
                var holder = new AdvancementHolder(id, value);
                all.add(holder);
                if (!name.matches("a[0-9]{3}")) continue;
                SELECTED.add(holder);
                var display = value.display().orElseThrow();
                check(!display.isHidden(), id + " is unexpectedly hidden");
                check(!display.getTitle().getString().startsWith("advancement."), id + " title is untranslated");
                check(!display.getDescription().getString().startsWith("advancement."), id + " description is untranslated");
                ItemStack icon = icon(display);
                check(!icon.isEmpty(), id + " icon decoded to an empty item");
                var iconJson = json.getAsJsonObject("display").getAsJsonObject("icon");
                check(BuiltInRegistries.ITEM.getKey(icon.getItem()).toString().equals(iconJson.get("id").getAsString()),
                        id + " icon item changed during native decoding");
                String tier = "default";
                if (iconJson.has("components")) {
                    var custom = iconJson.getAsJsonObject("components").getAsJsonObject("minecraft:custom_data");
                    if (custom != null && custom.has("essence_ascendance_equipment")) {
                        tier = custom.getAsJsonObject("essence_ascendance_equipment").get("tier").getAsString();
                        check(com.mistaboom.essence_ascendance.equipment.EquipmentTierData.tier(icon).serializedName().equals(tier),
                                id + " equipment tier component did not reach native appearance");
                    }
                    if (custom != null && custom.has("essence_ascendance_focus")) {
                        tier = custom.getAsJsonObject("essence_ascendance_focus").get("tier").getAsString();
                        var focusTier = com.mistaboom.essence_ascendance.pylon.EssenceFocusData.tier(icon);
                        check(focusTier != null && focusTier.serializedName().equals(tier),
                                id + " Focus tier component did not reach native appearance");
                    }
                }
                // Native item-model selection, including component-sensitive overrides.
                //? if >=26.1.2 {
                /*var model = new net.minecraft.client.renderer.item.TrackingItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(model, icon,
                        net.minecraft.world.item.ItemDisplayContext.GUI, null, null, 0);
                check(!model.isEmpty(), id + " icon has no native GUI model");
                var material = model.pickParticleMaterial(net.minecraft.util.RandomSource.create(0));
                var itemId = BuiltInRegistries.ITEM.getKey(icon.getItem());
                Path modelDefinition = root.resolve("assets/" + itemId.getNamespace() + "/items/" + itemId.getPath() + ".json");
                boolean special = Files.isRegularFile(modelDefinition) && JsonParser.parseString(Files.readString(modelDefinition))
                        .getAsJsonObject().getAsJsonObject("model").get("type").getAsString().equals("minecraft:special");
                // Native special renderers use their own mesh/material; their optional particle is not an icon texture.
                check(special || (material != null && !material.sprite().contents().name().getPath().equals("missingno")),
                        id + " ordinary icon resolved a missing texture");*/
                //? }
                //? if <26.1.2 {
                var model = client.getItemRenderer().getModel(icon, null, null, 0);
                // ItemRenderer.getModel deliberately returns the in-hand spyglass/trident model.
                // Its render(GUI) branch replaces that with this exact inventory model before drawing.
                if (icon.is(net.minecraft.world.item.Items.SPYGLASS) || icon.is(net.minecraft.world.item.Items.TRIDENT))
                    model = client.getModelManager().getModel(net.minecraft.client.resources.model.ModelResourceLocation
                            .inventory(BuiltInRegistries.ITEM.getKey(icon.getItem())));
                check(model != client.getModelManager().getMissingModel(), id + " icon resolved a missing model");
                if (!model.isCustomRenderer()) {
                    int quads = 0;
                    for (int side = -1; side < net.minecraft.core.Direction.values().length; side++) {
                        var face = side < 0 ? null : net.minecraft.core.Direction.values()[side];
                        for (var quad : model.getQuads(null, face, net.minecraft.util.RandomSource.create(42))) {
                            quads++;
                            check(!quad.getSprite().contents().name().getPath().equals("missingno"),
                                    id + " native GUI quad resolved a missing texture");
                        }
                    }
                    check(quads > 0, id + " ordinary GUI icon has no drawn geometry");
                }
                //? }
                int titleWidth = client.font.width(display.getTitle());
                var toastLines = client.font.split(display.getTitle(), 125);
                check(toastLines.stream().allMatch(line -> client.font.width(line) <= 125),
                        id + " native toast line overflows");
                REPORT.add(id + " | " + display.getTitle().getString() + " | icon="
                        + BuiltInRegistries.ITEM.getKey(icon.getItem()) + " | tier=" + tier + " | titlePx=" + titleWidth
                        + " | toastLines=" + toastLines.size() + " | frame=" + display.getType());
            }
        }
        check(SELECTED.size() == 69, "Expected exactly 69 selected native displays");
        TREE.addAll(all);
        check(TREE.nodes().size() == all.size(), "Native tree has unresolved parents");
        for (var rootNode : TREE.roots()) TreeNodePosition.run(rootNode);
        Set<String> occupied = new HashSet<>();
        for (var node : TREE.nodes()) {
            var display = node.advancement().display().orElseThrow();
            check(occupied.add(node.root().holder().id() + ":" + display.getX() + ":" + display.getY()),
                    "Native advancement widgets overlap at " + node.holder().id());
        }
        System.out.println("AchievementPresentationNativeFixture: decoded all 69 icons/displays; capturing native UI");
    }

    private static ItemStack icon(DisplayInfo display) {
        //? if >=26.1.2
        /*return display.getIcon().create();*/
        //? if <26.1.2
        return display.getIcon();
    }

    //? if >=26.1.2 {
    /*private static void bindNativeComponents() throws Exception {
        boolean bound = true;
        for (var holder : BuiltInRegistries.ITEM.listElements().toList()) {
            try { holder.components(); }
            catch (NullPointerException absent) {
                if (!"Components not bound yet".equals(absent.getMessage())) throw absent;
                bound = false;
                break;
            }
        }
        if (bound) {
            REPORT.add("Native item component defaults were already bound; no fixture rebinding");
            return;
        }
        if (!Boolean.getBoolean("essence.achievement.presentationNativeData")) {
            com.mistaboom.essence_ascendance.verification.NativeTestBootstrap2612.bindBuiltInComponents();
            REPORT.add("Native registered defaults bound through existing detached Fabric bootstrap");
            return;
        }
        // NeoForge validates component equality, including HolderSets. A data-generation
        // lookup contains placeholder tags, so use the genuine native data-pack loader.
        // This loads installed mod/vanilla registries and tags without opening any world.
        var packs = net.minecraft.server.packs.repository.ServerPacksSource.createVanillaTrustedRepository();
        var config = new net.minecraft.server.WorldLoader.InitConfig(
                new net.minecraft.server.WorldLoader.PackConfig(packs,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT, false, true),
                net.minecraft.commands.Commands.CommandSelection.ALL,
                net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
        try (var resources = net.minecraft.server.WorldLoader.load(config,
                context -> new net.minecraft.server.WorldLoader.DataLoadOutput<>(Boolean.TRUE, context.datapackDimensions()),
                (manager, data, registries, cookie) -> manager,
                Runnable::run, Runnable::run).get(60, TimeUnit.SECONDS)) {
            for (var holder : BuiltInRegistries.ITEM.listElements().toList()) holder.components();
            REPORT.add("Native WorldLoader loaded installed data packs, tags and validated item components: "
                    + packs.getSelectedIds());
        }
    }*/
    //? }

    private static void show(Minecraft client) {
        client.setScreen(new CaptureScreen(SELECTED.get(index)));
        waitTicks = 5;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    /** Uses the actual native widgets and toast renderer, only placing them on a fixture canvas. */
    private static final class CaptureScreen extends Screen {
        private final AdvancementHolder holder;
        private final AdvancementToast toast;
        private AdvancementTab tab;
        private AdvancementWidget selected;

        CaptureScreen(AdvancementHolder holder) {
            super(Component.literal("Achievement presentation fixture"));
            this.holder = holder;
            this.toast = new AdvancementToast(holder);
        }

        @Override protected void init() {
            // AdvancementsScreen supplies native tooltip bounds; it is never installed or connected.
            var bounds = new AdvancementsScreen(null);
            bounds.width = width;
            bounds.height = height;
            var node = TREE.get(holder);
            tab = AdvancementTab.create(minecraft, bounds, 0, node.root());
            check(tab != null, "Native root tab was not created");
            check(tab.getTitle().getString().equals("Essence Ascendance"),
                    "Selected tab must use the mod name without renaming its root achievement");
            if (index == 0) {
                for (var namespace : List.of("minecraft", "another_mod")) {
                    var controlRoot = new AdvancementNode(new AdvancementHolder(
                            ResourceLocation.fromNamespaceAndPath(namespace, "presentation_fixture_control"),
                            node.root().advancement()), null);
                    var controlTab = AdvancementTab.create(minecraft, bounds, 1, controlRoot);
                    check(controlTab != null, "Nonselected native tab must still be created: " + namespace);
                    check(controlTab.getTitle().equals(controlRoot.advancement().display().orElseThrow().getTitle()),
                            "Nonselected tab title must remain native: " + namespace);
                }
                REPORT.add("PASS: mod tab title is separate from achievement titles; vanilla/other-mod tabs retain native titles");
            }
            for (var entry : TREE.nodes()) if (entry != node.root() && entry.root().equals(node.root()))
                tab.addAdvancement(entry);
            selected = tab.getWidget(holder);
            check(selected != null, "Native widget absent for " + holder.id());
            check(selected.getWidth() <= width - 32, "Native tooltip exceeds fixture viewport: " + holder.id());
            // Center each real viewport on this node to cover every scrolled portion of the native tree.
            try {
                int titleLineCount = 1;
                //? if >=26.1.2 {
                /*var title = AdvancementWidget.class.getDeclaredField("titleLines"); title.setAccessible(true);
                @SuppressWarnings("unchecked")
                var nativeTitleLines = (List<net.minecraft.util.FormattedCharSequence>) title.get(selected);
                titleLineCount = nativeTitleLines.size();
                StringBuilder nativeTitle = new StringBuilder();
                for (var line : nativeTitleLines) {
                    check(font.width(line) <= selected.getWidth() - 8, "Native title line clips: " + holder.id());
                    nativeTitle.append(text(line));
                }
                check(nativeTitle.toString().replaceAll("\\s", "").equals(holder.value().display().orElseThrow()
                                .getTitle().getString().replaceAll("\\s", "")),
                        "Native widget lost approved title text: " + holder.id());*/
                //? }
                //? if <26.1.2 {
                var title = AdvancementWidget.class.getDeclaredField("title"); title.setAccessible(true);
                var nativeTitle = (net.minecraft.util.FormattedCharSequence) title.get(selected);
                check(text(nativeTitle).equals(holder.value().display().orElseThrow().getTitle().getString()),
                        "Native widget clips approved title: " + holder.id() + " -> " + text(nativeTitle));
                if (index == 0) {
                    var longest = SELECTED.stream().max(Comparator.comparingInt(entry ->
                            font.width(entry.value().display().orElseThrow().getTitle()))).orElseThrow();
                    var controlNode = new AdvancementNode(new AdvancementHolder(
                            ResourceLocation.fromNamespaceAndPath("minecraft", "presentation_fixture_control"), longest.value()), null);
                    var controlWidget = new AdvancementWidget(tab, minecraft, controlNode, longest.value().display().orElseThrow());
                    var controlTitle = (net.minecraft.util.FormattedCharSequence) title.get(controlWidget);
                    check(font.width(longest.value().display().orElseThrow().getTitle()) > 163,
                            "Negative control requires an actual long approved title");
                    check(font.width(controlTitle) <= 163 && !text(controlTitle).equals(
                                    longest.value().display().orElseThrow().getTitle().getString()),
                            "Selected-title presentation changed vanilla/nonselected widgets");
                    REPORT.add("PASS: nonselected long-title widget retains vanilla 163px truncation");
                }
                //? }
                var description = AdvancementWidget.class.getDeclaredField("description"); description.setAccessible(true);
                @SuppressWarnings("unchecked")
                var lines = (List<net.minecraft.util.FormattedCharSequence>) description.get(selected);
                StringBuilder nativeDescription = new StringBuilder();
                for (var line : lines) {
                    check(font.width(line) <= selected.getWidth() - 8,
                            "Native description line clips: " + holder.id());
                    nativeDescription.append(text(line));
                }
                check(nativeDescription.toString().replaceAll("\\s", "").equals(holder.value().display().orElseThrow()
                                .getDescription().getString().replaceAll("\\s", "")),
                        "Native tooltip lost unlock condition text: " + holder.id());
                check(26 + (lines.size() + titleLineCount - 1) * 9 <= height - 80,
                        "Native description exceeds viewport height: " + holder.id());
                REPORT.add("widget " + holder.id() + " | width=" + selected.getWidth() + " | titleLines=" + titleLineCount
                        + " | descriptionLines=" + lines.size());
                var x = AdvancementTab.class.getDeclaredField("scrollX"); x.setAccessible(true);
                var y = AdvancementTab.class.getDeclaredField("scrollY"); y.setAccessible(true);
                var centered = AdvancementTab.class.getDeclaredField("centered"); centered.setAccessible(true);
                x.setDouble(tab, 117 - selected.getX());
                y.setDouble(tab, 56 - selected.getY());
                centered.setBoolean(tab, true);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }

        private static String text(net.minecraft.util.FormattedCharSequence sequence) {
            StringBuilder result = new StringBuilder();
            sequence.accept((position, style, codePoint) -> { result.appendCodePoint(codePoint); return true; });
            return result.toString();
        }

        //? if >=26.1.2 {
        /*@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF141A24);
            graphics.text(font, holder.id().toString(), 16, 10, 0xFFFFFFFF);
            graphics.text(font, "Native tab: " + tab.getTitle().getString(), 16, 25, 0xFFC7D0DB);
            tab.extractContents(graphics, 16, 42);
            graphics.text(font, "Native unlock tooltip", 16, 170, 0xFFC7D0DB);
            selected.extractHover(graphics, 32 - selected.getX(), 285 - selected.getY(), 1.0F, 0, 0);
            graphics.pose().pushMatrix();
            graphics.pose().translate(width - 176, 42);
            toast.extractRenderState(graphics, font, 2500);
            graphics.pose().popMatrix();
            graphics.text(font, "Native toast at 2.5 s", width - 176, 82, 0xFFC7D0DB);
            graphics.text(font, "Fixture display only; no gameplay grant", 16, height - 18, 0xFFC7D0DB);
        }*/
        //? }
        //? if <26.1.2 {
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF141A24);
            graphics.drawString(font, holder.id().toString(), 16, 10, 0xFFFFFFFF);
            graphics.drawString(font, "Native tab: " + tab.getTitle().getString(), 16, 25, 0xFFC7D0DB);
            tab.drawContents(graphics, 16, 42);
            graphics.drawString(font, "Native unlock tooltip", 16, 170, 0xFFC7D0DB);
            selected.drawHover(graphics, 32 - selected.getX(), 285 - selected.getY(), 1.0F, 0, 0);
            graphics.pose().pushPose();
            graphics.pose().translate(width - 176, 42, 0);
            toast.render(graphics, minecraft.getToasts(), 2500);
            graphics.pose().popPose();
            graphics.drawString(font, "Native toast at 2.5 s", width - 176, 82, 0xFFC7D0DB);
            graphics.drawString(font, "Fixture display only; no gameplay grant", 16, height - 18, 0xFFC7D0DB);
        }
        //? }
    }
}
