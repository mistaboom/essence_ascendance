package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleDissolutionMode;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleEssences;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleMenu;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleStatePayload;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* Shared-style normal machine screen for the Essence Crucible. */
public final class EssenceCrucibleScreen
        extends MachineContainerScreen<EssenceCrucibleMenu> {

    private static final int BORDER = MachineScreenUi.BORDER;
    private static final int TEXT = MachineScreenUi.TEXT;
    private static final int MUTED = MachineScreenUi.MUTED;

    private static final int PANEL_MARGIN = 10;
    private static final int PANEL_Y = 72;
    private static final int PANEL_HEIGHT = 106;
    private static final int FULL_PANEL_WIDTH =
            MachineScreenLayout.MAIN_PANEL_WIDTH - PANEL_MARGIN * 2;
    private static final int STATUS_PANEL_Y = 182;
    private static final int STATUS_PANEL_HEIGHT = 17;

    private static final int INFO_PANEL_WIDTH = MachineScreenLayout.INFO_PANEL_WIDTH;
    private static final int INFO_PANEL_HEIGHT = 198;

    private static final int VENT_PANEL_WIDTH = 172;
    private static final int SETTINGS_HEADER_HEIGHT = 27;
    private static final int DISSOLUTION_MODE_SECTION_HEIGHT = 43;
    private static final int VENT_SECTION_HEIGHT = 27;
    private static final int VENT_ROW_HEIGHT = 14;
    private static final int VENT_FOOTER_HEIGHT = 8;
    private static final int VENT_BUTTON_WIDTH = 38;
    private static final int VENT_BUTTON_HEIGHT = 12;

    private Button channelButton;
    private Button infoButton;
    private Button settingsButton;
    private Button infoCloseButton;
    private Button settingsCloseButton;
    private Button dissolutionModeButton;
    private final Button[] ventButtons =
            new Button[EssenceCrucibleEssences.ORDERED.size()];
    private boolean infoOpen;
    private boolean settingsOpen;

    public EssenceCrucibleScreen(
            EssenceCrucibleMenu menu,
            Inventory playerInventory,
            Component title
    ) {
        super(
                menu,
                playerInventory,
                title,
                new MachineScreenLayout.Spec(
                        MachineScreenLayout.MAIN_PANEL_WIDTH,
                        319,
                        new UiBounds(32, 235, 166, 80),
                        34,
                        224
                )
        );
    }

    @Override
    protected void initMachineWidgets() {
        channelButton = addMachineButton(
                EssenceText.gui("crucible.button.start_channeling"),
                button -> toggleChannel(),
                new UiBounds(leftPos + 35, topPos + 202, 160, 20)
        );

        settingsButton = addHeaderButton(
                Component.literal("\u2699"),
                false,
                button -> {
                    settingsOpen = !settingsOpen;
                    if (settingsOpen) {
                        infoOpen = false;
                    }
                }
        );

        infoButton = addHeaderButton(
                Component.literal("i"),
                true,
                button -> {
                    infoOpen = !infoOpen;
                    if (infoOpen) {
                        settingsOpen = false;
                    }
                }
        );

        infoCloseButton = addOverlayCloseButton(
                button -> infoOpen = false,
                infoPanelBounds()
        );
        infoCloseButton.visible = false;

        UiBounds initialVentBounds = sidePanel(
                VENT_PANEL_WIDTH,
                SETTINGS_HEADER_HEIGHT + VENT_SECTION_HEIGHT + VENT_FOOTER_HEIGHT,
                MachineScreenLayout.SidePreference.LEFT_FIRST
        ).bounds();
        settingsCloseButton = addOverlayCloseButton(
                button -> settingsOpen = false,
                initialVentBounds
        );
        settingsCloseButton.visible = false;

        dissolutionModeButton = addMachineButton(
                EssenceText.dissolutionMode(EssenceCrucibleDissolutionMode.SMART_ROUND_ROBIN),
                button -> cycleDissolutionMode(),
                new UiBounds(
                        initialVentBounds.x() + 7,
                        initialVentBounds.y() + SETTINGS_HEADER_HEIGHT + 12,
                        VENT_PANEL_WIDTH - 14,
                        16
                )
        );
        dissolutionModeButton.visible = false;

        for (int i = 0; i < ventButtons.length; i++) {
            final int essenceIndex = i;
            Button ventButton = addMachineButton(
                    EssenceText.gui("crucible.button.vent"),
                    button -> ventEssence(essenceIndex),
                    new UiBounds(
                            initialVentBounds.right() - VENT_BUTTON_WIDTH - 6,
                            initialVentBounds.y() + SETTINGS_HEADER_HEIGHT
                                    + DISSOLUTION_MODE_SECTION_HEIGHT
                                    + VENT_SECTION_HEIGHT
                                    + i * VENT_ROW_HEIGHT,
                            VENT_BUTTON_WIDTH,
                            VENT_BUTTON_HEIGHT
                    )
            );
            ventButton.visible = false;
            ventButtons[i] = ventButton;
        }

        /*
         * JEI recipe navigation can recreate this screen while the server's
         * Crucible menu never closed. Explicitly request a fresh authoritative
         * snapshot so an unchanged LAST_SENT cache cannot leave us waiting.
         */
        EssenceCrucibleClientState.requestState(menu.containerId);
    }

    @Override
    public void removed() {
        /*
         * Do not clear here. JEI and similar temporary screen transitions call
         * removed() even though this container menu is still open. Keeping the
         * menu-id-scoped snapshot prevents the Crucible from blanking while
         * the replacement screen is displayed.
         */
        super.removed();
    }

    @Override
    public void onClose() {
        /* Actual player close: stale state is no longer useful. */
        EssenceCrucibleClientState.clear();
        super.onClose();
    }

    private void toggleChannel() {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        if (state == null || !state.allowed()) {
            return;
        }

        EssenceCrucibleClientState.requestChannel(
                menu.containerId,
                !state.channeling()
        );
    }


    private void cycleDissolutionMode() {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);
        if (state == null
                || !state.allowed()
                || !settingsOpen
                || menu.activeMachineSlots() <= 1) {
            return;
        }

        EssenceCrucibleDissolutionMode current =
                EssenceCrucibleDissolutionMode.fromSerializedName(
                        state.dissolutionMode()
                );
        EssenceCrucibleClientState.requestDissolutionMode(
                menu.containerId,
                current.next().serializedName()
        );
    }

    private void ventEssence(int allEssenceIndex) {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);
        if (state == null || !state.allowed() || !settingsOpen) {
            return;
        }

        if (allEssenceIndex < 0
                || allEssenceIndex >= EssenceCrucibleEssences.ORDERED.size()) {
            return;
        }

        EssenceCrucibleClientState.requestVent(
                menu.containerId,
                EssenceCrucibleEssences.ORDERED.get(allEssenceIndex).id()
        );
    }

    @Override
    protected List<MachineOverlay> machineOverlays() {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);
        if (settingsOpen && state != null && state.allowed()) {
            UiBounds bounds = ventPanelBounds(state);
            List<AbstractWidget> controls = new ArrayList<>();
            controls.add(settingsCloseButton);
            controls.add(dissolutionModeButton);
            controls.addAll(List.of(ventButtons));
            return List.of(panelOverlay(
                    "crucible_settings",
                    bounds,
                    controls,
                    (graphics, mouseX, mouseY, partialTick, scrollOffset) ->
                            renderVentPopup(graphics, state, bounds)
            ));
        }
        if (infoOpen && state != null) {
            UiBounds bounds = infoPanelBounds();
            return List.of(infoOverlay(
                    "crucible_info",
                    bounds,
                    infoPanel(state),
                    List.of(infoCloseButton)
            ));
        }
        return List.of();
    }

    @Override
    protected void updateMachineWidgets() {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        if (channelButton != null) {
            boolean active = state != null && state.channeling();
            channelButton.setMessage(
                    EssenceText.gui(active
                            ? "crucible.button.stop_channeling"
                            : "crucible.button.start_channeling")
            );
            channelButton.active = state != null
                    && state.allowed()
                    && (active || state.total() > 0L);
        }

        if (infoButton != null) {
            infoButton.setMessage(
                    Component.literal("i")
            );
            infoButton.active = state != null;
        }

        if (settingsButton != null) {
            settingsButton.setMessage(
                    Component.literal("\u2699")
            );
            settingsButton.active = state != null && state.allowed();
        }

        if (infoCloseButton != null) {
            infoCloseButton.visible = infoOpen && state != null;
            place(infoCloseButton, overlayCloseBounds(infoPanelBounds()));
        }
        if (settingsCloseButton != null) {
            settingsCloseButton.visible = settingsOpen
                    && state != null
                    && state.allowed();
            if (state != null) {
                place(settingsCloseButton, overlayCloseBounds(ventPanelBounds(state)));
            }
        }

        if (dissolutionModeButton != null) {
            boolean showMode = settingsOpen
                    && state != null
                    && state.allowed()
                    && menu.activeMachineSlots() > 1;
            dissolutionModeButton.visible = showMode;
            if (showMode) {
                UiBounds bounds = ventPanelBounds(state);
                place(dissolutionModeButton, new UiBounds(
                        bounds.x() + 7,
                        bounds.y() + SETTINGS_HEADER_HEIGHT + 12,
                        bounds.width() - 14,
                        16
                ));
                EssenceCrucibleDissolutionMode mode =
                        EssenceCrucibleDissolutionMode.fromSerializedName(
                                state.dissolutionMode()
                        );
                dissolutionModeButton.setMessage(
                        EssenceText.dissolutionMode(mode)
                );
            }
        }

        updateVentButtons(state);
    }

    private void updateVentButtons(
            EssenceCrucibleStatePayload state
    ) {
        for (Button button : ventButtons) {
            if (button != null) {
                button.visible = false;
            }
        }

        if (!settingsOpen || state == null || !state.allowed()) {
            return;
        }

        long[] values = enabledEssenceValues(state);

        int firstVentRowY = ventPanelFirstRowY(state);
        UiBounds panelBounds = ventPanelBounds(state);
        for (int i = 0; i < values.length && i < ventButtons.length; i++) {
            Button button = ventButtons[i];
            place(button, new UiBounds(
                    panelBounds.right() - VENT_BUTTON_WIDTH - 6,
                    firstVentRowY + i * VENT_ROW_HEIGHT,
                    VENT_BUTTON_WIDTH,
                    VENT_BUTTON_HEIGHT
            ));
            button.visible = true;
            button.active = values[i] > 0L;
        }
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        int x = leftPos;
        int y = topPos;

        MachineScreenUi.panel(graphics, x, y, imageWidth, imageHeight);

        /* Active pylon count expands the real distinct-item input lanes. */
        for (int slot = 0; slot < EssenceCrucibleMenu.MAX_MACHINE_SLOTS; slot++) {
            if (!menu.shouldRenderMachineSlot(slot)) {
                continue;
            }

            int border = menu.isMachineSlotCurrentlyAvailable(slot)
                    ? BORDER
                    : MachineScreenUi.MUTED_BAD;
            MachineScreenUi.itemSlot(graphics, x, y, menu.getSlot(slot), border);
        }

        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        /* Visual dissolution progress replaces the old numeric text line. */
        int barX = x + 70;
        int barY = y + 56;
        int barWidth = 90;
        int barHeight = 10;
        MachineScreenUi.progressBar(
                graphics,
                barX,
                barY,
                barWidth,
                barHeight,
                state == null ? 0 : state.processingTicks(),
                state == null ? 1 : Math.max(1, state.dissolutionTicksPerItem())
        );

        MachineScreenUi.accentedInset(
                graphics,
                x + PANEL_MARGIN,
                y + PANEL_Y,
                FULL_PANEL_WIDTH,
                PANEL_HEIGHT
        );
        MachineScreenUi.accentedInset(
                graphics,
                x + PANEL_MARGIN,
                y + STATUS_PANEL_Y,
                FULL_PANEL_WIDTH,
                STATUS_PANEL_HEIGHT
        );

        /* Player inventory slot backing. */
        MachineScreenUi.inset(graphics, x + 32, y + 235, 166, 80);

    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        drawCentered(graphics, EssenceText.gui("crucible.title"), 7, TEXT);
        drawCentered(graphics, EssenceText.term("input_slots"), 20, MUTED);

        if (state == null) {
            drawCentered(graphics, EssenceText.term("synchronizing"), 56, MUTED);
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
            return;
        }

        int contentLeft = PANEL_MARGIN + 6;
        int indentedLeft = contentLeft + 4;
        int contentRight = PANEL_MARGIN + FULL_PANEL_WIDTH - 6;

        MachineScreenUi.sectionHeader(
                graphics, font, EssenceText.term("essence"), contentLeft, 78
        );

        long[] essenceValues = state.essenceAmounts();
        for (int i = 0; i < essenceValues.length; i++) {
            int rowY = 92 + i * 12;
            MachineScreenUi.row(
                    graphics,
                    font,
                    EssenceText.essenceShort(EssenceCrucibleEssences.ORDERED.get(i)),
                    format(essenceValues[i]),
                    indentedLeft,
                    contentRight,
                    rowY,
                    TEXT
            );
        }

        MachineScreenUi.row(
                graphics,
                font,
                EssenceText.term("total"),
                format(state.total()) + " / " + format(state.reservoirCapacity()),
                indentedLeft,
                contentRight,
                166,
                TEXT
        );

        MachineScreenUi.row(
                graphics,
                font,
                EssenceText.term("status"),
                statusText(state),
                contentLeft,
                contentRight,
                186,
                statusColor(state)
        );

        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }

    private void renderVentPopup(
            GuiGraphics graphics,
            EssenceCrucibleStatePayload state,
            UiBounds bounds
    ) {
        int panelX = bounds.x();
        int panelY = bounds.y();
        long[] values = enabledEssenceValues(state);

        MachineScreenUi.panel(
                graphics,
                panelX,
                panelY,
                bounds.width(),
                bounds.height()
        );

        int textX = panelX + 7;
        int textWidth = VENT_PANEL_WIDTH - 14;
        MachineScreenUi.sectionHeader(graphics, font, EssenceText.term("settings"), textX, panelY + 7);

        int cursorY = panelY + SETTINGS_HEADER_HEIGHT;
        if (showDissolutionModeSetting(state)) {
            MachineScreenUi.sectionHeader(
                    graphics,
                    font,
                    EssenceText.term("dissolution_mode"),
                    textX,
                    cursorY
            );

            EssenceCrucibleDissolutionMode mode =
                    EssenceCrucibleDissolutionMode.fromSerializedName(
                            state.dissolutionMode()
                    );
            MachineScreenUi.fitted(
                    graphics,
                    font,
                    dissolutionModeDescription(mode),
                    textX,
                    cursorY + 29,
                    textWidth,
                    MUTED
            );
            cursorY += DISSOLUTION_MODE_SECTION_HEIGHT;
        }

        MachineScreenUi.sectionHeader(
                graphics,
                font,
                EssenceText.term("reservoir_vent"),
                textX,
                cursorY
        );
        graphics.drawString(
                font,
                EssenceText.gui("crucible.vent_warning"),
                textX,
                cursorY + 10,
                MUTED,
                false
        );

        int firstRowY = ventPanelFirstRowY(state);
        for (int i = 0; i < values.length; i++) {
            int rowY = firstRowY + i * VENT_ROW_HEIGHT;
            Component name = displayName(EssenceCrucibleEssences.ORDERED.get(i));

            graphics.drawString(
                    font,
                    name,
                    panelX + 7,
                    rowY + 2,
                    MUTED,
                    false
            );
            drawRightAlignedAbsolute(
                    graphics,
                    format(values[i]),
                    panelX + VENT_PANEL_WIDTH - VENT_BUTTON_WIDTH - 11,
                    rowY + 2,
                    values[i] > 0L ? TEXT : MUTED
            );
        }
    }

    private Component dissolutionModeDescription(EssenceCrucibleDissolutionMode mode) {
        return EssenceText.gui("crucible.dissolution_mode." + mode.serializedName() + ".description");
    }

    private boolean showDissolutionModeSetting(EssenceCrucibleStatePayload state) {
        return state != null && menu.activeMachineSlots() > 1;
    }

    private int ventPanelFirstRowY(EssenceCrucibleStatePayload state) {
        return ventPanelBounds(state).y()
                + SETTINGS_HEADER_HEIGHT
                + (showDissolutionModeSetting(state)
                        ? DISSOLUTION_MODE_SECTION_HEIGHT
                        : 0)
                + VENT_SECTION_HEIGHT;
    }

    private int ventPanelHeight(EssenceCrucibleStatePayload state) {
        return SETTINGS_HEADER_HEIGHT
                + (showDissolutionModeSetting(state)
                        ? DISSOLUTION_MODE_SECTION_HEIGHT
                        : 0)
                + VENT_SECTION_HEIGHT
                + enabledEssenceValues(state).length * VENT_ROW_HEIGHT
                + VENT_FOOTER_HEIGHT;
    }

    private long[] enabledEssenceValues(
            EssenceCrucibleStatePayload state
    ) {
        return state.essenceAmounts();
    }

    private Component displayName(
            EssenceDefinition essence
    ) {
        return EssenceText.essenceShort(essence);
    }

    private UiBounds ventPanelBounds(EssenceCrucibleStatePayload state) {
        return sidePanel(
                VENT_PANEL_WIDTH,
                ventPanelHeight(state),
                MachineScreenLayout.SidePreference.LEFT_FIRST
        ).bounds();
    }

    /*
     * Mekanism-style informational side panel. It prefers an external side,
     * but on narrow screens may overlap the Crucible as an opaque foreground
     * panel so it is never clipped off-screen.
     */
    private MachineInfoPanel infoPanel(EssenceCrucibleStatePayload state) {
        return new MachineInfoPanel()
                .title(EssenceText.term("info"))
                .metadata(EssenceText.gui("owner", state.ownerName()))
                .metadata(EssenceText.gui("access", accessText(state.accessMode())))
                .section(EssenceText.term("crucible"))
                .line(EssenceText.gui("capacity_value", format(state.reservoirCapacity())))
                .line(EssenceText.gui("pylons_value", state.activePylonCount(), state.maxActivePylons()))
                .line(EssenceText.gui("pylon_radius_value", String.format(Locale.ROOT, "%.1f", state.pylonRadius())))
                .line(EssenceText.gui("input_slots_value", menu.activeMachineSlots()))
                .section(EssenceText.term("channeling"))
                .line(EssenceText.gui("rate_per_second", format(state.transferRatePerSecond())))
                .line(EssenceText.gui("range_blocks", String.format(Locale.ROOT, "%.1f", state.transferRange())))
                .section(EssenceText.term("dissolution"))
                .line(EssenceText.gui("items_batch_value", state.simultaneousItemProcesses()))
                .line(EssenceText.gui("batches_second_value", batchesPerSecond(state)));
    }

    private static Component accessText(String accessMode) {
        if (accessMode == null || accessMode.isBlank()) {
            return EssenceText.term("unknown");
        }
        return EssenceText.gui("access_mode." + accessMode.toLowerCase(Locale.ROOT));
    }

    private static Component statusText(EssenceCrucibleStatePayload state) {
        String key;
        if (!state.allowed()) {
            key = "access_denied";
        } else if (state.channeling() && state.dissolving()) {
            key = "channeling_dissolving";
        } else if (state.channeling()) {
            key = "channeling";
        } else if (state.dissolving()) {
            key = "dissolving";
        } else if (state.total() >= state.reservoirCapacity()) {
            key = "reservoir_full";
        } else {
            key = "idle";
        }
        return EssenceText.gui("crucible.status." + key);
    }

    private static int statusColor(EssenceCrucibleStatePayload state) {
        if (!state.allowed()) {
            return MachineScreenUi.BAD;
        }
        if (state.channeling() || state.dissolving()) {
            return MachineScreenUi.GOOD;
        }
        if (state.total() >= state.reservoirCapacity()) {
            return MachineScreenUi.WARN;
        }
        return MachineScreenUi.MUTED;
    }

    private UiBounds infoPanelBounds() {
        return sidePanel(
                INFO_PANEL_WIDTH,
                INFO_PANEL_HEIGHT,
                MachineScreenLayout.SidePreference.RIGHT_FIRST
        ).bounds();
    }

    private void drawRightAlignedAbsolute(
            GuiGraphics graphics,
            String text,
            int right,
            int y,
            int color
    ) {
        graphics.drawString(
                font,
                text,
                right - font.width(text),
                y,
                color,
                false
        );
    }

    private static String batchesPerSecond(EssenceCrucibleStatePayload state) {
        int ticks = Math.max(1, state.dissolutionTicksPerItem());
        double perSecond = 20.0D / ticks;
        if (Math.abs(perSecond - Math.rint(perSecond)) < 0.0001D) {
            return String.format(Locale.ROOT, "%,.0f", perSecond);
        }
        return String.format(Locale.ROOT, "%,.2f", perSecond);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

}
