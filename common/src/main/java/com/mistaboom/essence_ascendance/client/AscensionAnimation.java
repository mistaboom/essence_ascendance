package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.procedural.GuiProceduralGeometry;
import com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Local handoff/camera lease and quiet announcement. All world art lives in the shared recipe runtime. */
public final class AscensionAnimation {
    private static WorldVisualEvent pending;
    private static WorldVisualEvent active;
    private static ClientLevel level;
    private static LocalPlayer player;
    private static CameraType previousPerspective;
    private static long startedTick;
    private static long pendingTick;
    private static final int CAMERA_EASE_TICKS = 16;

    private AscensionAnimation() { }

    public static boolean ceremony(WorldVisualEvent event) {
        return event.recipeId().equals(TransientVisualIds.WORLD_ASCENDANCE_CEREMONY);
    }

    /** The server event can precede the next screen tick that consumes the transaction ack. */
    public static boolean defer(WorldVisualEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (!ceremony(event) || event.parameterA() > 0.5F || client.player == null
                || event.sourceEntityId() != client.player.getId()
                || !(client.screen instanceof AscendanceNexusScreen)) return false;
        clear();
        pending = event;
        level = client.level;
        player = client.player;
        pendingTick = level.getGameTime();
        return true;
    }

    /** Called only after the Nexus has validated acceptance + matching snapshot and closed. */
    public static void confirmed(ResourceLocation newTier) {
        if (pending != null && pending.color().name().equalsIgnoreCase(newTier.getPath())) releasePending();
    }

    private static void releasePending() {
        WorldVisualEvent event = pending;
        pending = null;
        if (event != null) TransientWorldVisuals.emit(event);
    }

    /** Runtime admission, rather than a tier snapshot, is the single local presentation trigger. */
    public static void started(WorldVisualEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (!ceremony(event) || client.player == null || client.player.getId() != event.sourceEntityId()) return;
        clear();
        active = event;
        level = client.level;
        player = client.player;
        startedTick = level.getGameTime();
        if (event.parameterA() < 0.5F && client.screen == null
                && client.getCameraEntity() == player && !player.isSleeping() && !player.isSpectator()) {
            previousPerspective = client.options.getCameraType();
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    /** Runs even with hidden HUD, no world rendering, a changed dimension, or an open screen. */
    public static void tick() {
        if (level == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level != level || client.player != player || player == null || !player.isAlive()
                || player.isRemoved() || client.getConnection() == null
                || !client.getConnection().getConnection().isConnected()) { clear(); return; }
        if (pending != null) {
            if (level.getGameTime() - pendingTick > 100) { clear(); return; }
            // Also handles a manually closed Nexus while its accepted request was in flight.
            if (!(client.screen instanceof AscendanceNexusScreen)) releasePending();
        }
        if (active == null) return;
        long age = level.getGameTime() - startedTick;
        if (age < 0 || age >= active.lifetimeTicks() + CAMERA_EASE_TICKS
                || age < active.lifetimeTicks() && !TransientWorldVisuals.contains(active)) {
            clear(); return;
        }
        if (previousPerspective != null && (client.screen != null || player.isSleeping()
                || player.isSpectator() || client.getCameraEntity() != player
                || client.options.getCameraType() != CameraType.THIRD_PERSON_BACK)) clear();
    }

    /** Modify the requested distance BEFORE vanilla collision clipping, never after it. */
    public static float cameraDistance(float vanilla, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (active == null || previousPerspective == null || client.level != level || client.player != player
                || client.getCameraEntity() != player || !player.isAlive()
                || client.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return vanilla;
        double age = level.getGameTime() - startedTick + partialTick;
        double initial = previousPerspective.isFirstPerson() ? 0 : 1;
        double arrival = ProceduralMotion.smoothStep(age / CAMERA_EASE_TICKS);
        double departure = ProceduralMotion.smoothStep((age - active.lifetimeTicks()) / CAMERA_EASE_TICKS);
        double distance = initial + (2.4 - initial) * arrival;
        return (float) (vanilla * (distance + (initial - distance) * departure));
    }

    public static void clear() {
        if (previousPerspective != null) Minecraft.getInstance().options.setCameraType(previousPerspective);
        previousPerspective = null;
        active = null;
        pending = null;
        level = null;
        player = null;
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (active == null || client.level != level || client.player != player
                || client.screen != null || client.options.hideGui) return;
        double p = (level.getGameTime() - startedTick + client.getTimer().getGameTimeDeltaPartialTick(true))
                / active.lifetimeTicks();
        double caption = (p - 0.52) / 0.48;
        if (caption <= 0 || caption >= 1) return;
        float fade = (float) ProceduralMotion.fadeEnvelope(caption, 5, 4);
        if (fade < 0.025F) return; // Font treats nearly zero packed alpha as opaque.
        var tierId = ResourceLocation.fromNamespaceAndPath("essence_ascendance",
                active.color().name().toLowerCase(Locale.ROOT));
        var tierName = AscendanceTierRegistry.get(tierId).map(EssenceText::ascendanceTier)
                .orElseGet(() -> EssenceText.gui("nexus.attunement.title"));
        var title = EssenceText.gui(active.parameterA() > 0.5F ? "ceremony.awakening" : "nexus.attunement.ascended", tierName);
        int cx = graphics.guiWidth() / 2, y = Math.max(16, graphics.guiHeight() / 5);
        int color = GuiProceduralGeometry.opacity(active.color().rgb(), (int) (fade * 255));
        float scale = Math.min(1, (graphics.guiWidth() - 24F) / Math.max(1, client.font.width(title)));
        graphics.pose().pushPose();
        graphics.pose().translate(cx, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(client.font, title, 0, 0, color);
        GuiProceduralGeometry.beam(graphics, -28, 13, 28, 13,
                GuiProceduralGeometry.opacity(active.color().rgb(), (int) (fade * 90)));
        graphics.pose().popPose();
    }
}
