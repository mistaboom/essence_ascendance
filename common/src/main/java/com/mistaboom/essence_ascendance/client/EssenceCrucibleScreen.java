package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleDissolutionMode;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleEssences;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleMenu;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/* Asset-free functional machine screen for the Crucible foundation tranche. */
public final class EssenceCrucibleScreen
        extends AbstractContainerScreen<EssenceCrucibleMenu> {

    private static final int PANEL = MachineScreenUi.PANEL;
    private static final int PANEL_INNER = MachineScreenUi.PANEL_INNER;
    private static final int BORDER = MachineScreenUi.BORDER;
    private static final int TEXT = MachineScreenUi.TEXT;
    private static final int MUTED = MachineScreenUi.MUTED;

    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_GAP = 12;
    private static final int PANEL_Y = 72;
    private static final int PANEL_HEIGHT = 106;
    private static final int TWO_COLUMN_WIDTH = 132;
    private static final int FULL_PANEL_WIDTH = 276;
    private static final int TOTAL_PANEL_Y = 182;
    private static final int TOTAL_PANEL_HEIGHT = 17;

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
            new Button[EssenceCrucibleEssences.ALL_ORDERED.size()];
    private boolean infoOpen;
    private boolean settingsOpen;

    public EssenceCrucibleScreen(
            EssenceCrucibleMenu menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title);
        imageWidth = 300;
        imageHeight = 319;
        inventoryLabelX = 69;
        inventoryLabelY = 224;
    }

    @Override
    protected void init() {
        super.init();

        channelButton = addRenderableWidget(
                Button.builder(
                                Component.literal("START CHANNELING"),
                                button -> toggleChannel()
                        )
                        .bounds(
                                leftPos + 70,
                                topPos + 202,
                                160,
                                20
                        )
                        .build()
        );


        settingsButton = addRenderableWidget(
                Button.builder(
                                Component.literal("\u2699"),
                                button -> {
                                    settingsOpen = !settingsOpen;
                                    if (settingsOpen) {
                                        infoOpen = false;
                                    }
                                }
                        )
                        .bounds(
                                leftPos + 5,
                                topPos + 5,
                                15,
                                15
                        )
                        .build()
        );

        infoButton = addRenderableWidget(
                Button.builder(
                                Component.literal("i"),
                                button -> {
                                    infoOpen = !infoOpen;
                                    if (infoOpen) {
                                        settingsOpen = false;
                                    }
                                }
                        )
                        .bounds(
                                leftPos + imageWidth - 20,
                                topPos + 5,
                                15,
                                15
                        )
                        .build()
        );

        infoCloseButton = addRenderableWidget(
                Button.builder(
                                Component.literal("X"),
                                button -> infoOpen = false
                        )
                        .bounds(
                                infoPanelX() + INFO_PANEL_WIDTH - 18,
                                topPos + 8,
                                12,
                                12
                        )
                        .build()
        );
        infoCloseButton.visible = false;

        settingsCloseButton = addRenderableWidget(
                Button.builder(
                                Component.literal("X"),
                                button -> settingsOpen = false
                        )
                        .bounds(
                                ventPanelX() + VENT_PANEL_WIDTH - 18,
                                topPos + 8,
                                12,
                                12
                        )
                        .build()
        );
        settingsCloseButton.visible = false;

        dissolutionModeButton = addRenderableWidget(
                Button.builder(
                                Component.literal("Smart Round Robin"),
                                button -> cycleDissolutionMode()
                        )
                        .bounds(
                                ventPanelX() + 7,
                                topPos + 4 + SETTINGS_HEADER_HEIGHT + 12,
                                VENT_PANEL_WIDTH - 14,
                                16
                        )
                        .build()
        );
        dissolutionModeButton.visible = false;

        int ventPanelX = ventPanelX();
        int ventPanelY = topPos + 4;
        for (int i = 0; i < ventButtons.length; i++) {
            final int essenceIndex = i;
            Button ventButton = addRenderableWidget(
                    Button.builder(
                                    Component.literal("VENT"),
                                    button -> ventEssence(essenceIndex)
                            )
                            .bounds(
                                    ventPanelX + VENT_PANEL_WIDTH - VENT_BUTTON_WIDTH - 6,
                                    ventPanelY + SETTINGS_HEADER_HEIGHT + DISSOLUTION_MODE_SECTION_HEIGHT + VENT_SECTION_HEIGHT + i * VENT_ROW_HEIGHT,
                                    VENT_BUTTON_WIDTH,
                                    VENT_BUTTON_HEIGHT
                            )
                            .build()
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

        List<EssenceDefinition> enabled =
                EssenceCrucibleEssences.enabledOrdered(
                        state.skillEssencesEnabled()
                );
        if (allEssenceIndex < 0 || allEssenceIndex >= enabled.size()) {
            return;
        }

        EssenceCrucibleClientState.requestVent(
                menu.containerId,
                enabled.get(allEssenceIndex).id()
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        updateButtons();
        super.render(graphics, mouseX, mouseY, partialTick);

        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        /*
         * Side panels are deliberately rendered after the normal container
         * screen. This makes a narrow-screen fallback a true opaque overlay
         * instead of allowing the Crucible labels, slots, or widgets to show
         * through it. Popup-owned buttons are then rendered one final time on
         * top of the panel itself.
         */
        if ((settingsOpen && state != null && state.allowed())
                || (infoOpen && state != null)) {
            /*
             * Vanilla button labels are rendered slightly in front of their
             * button backgrounds. A later same-depth fill can therefore hide
             * the button body while leaving glyphs such as the gear or "i"
             * visible. Put the complete popup pass on its own foreground Z
             * layer so the panel occludes every covered main-GUI element,
             * including widget text, while popup-owned controls remain above
             * the panel.
             */
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            if (settingsOpen && state.allowed()) {
                renderVentPopup(graphics, state);
                renderVentPopupWidgets(graphics, mouseX, mouseY, partialTick);
            } else {
                renderInfoPopup(graphics, state);
                renderInfoPopupWidgets(graphics, mouseX, mouseY, partialTick);
            }
            graphics.pose().popPose();
        }

        /* Do not leak covered slot/tooltips through an overlapping popup. */
        if (!mouseInsideOpenPopup(mouseX, mouseY, state)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private void updateButtons() {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        if (channelButton != null) {
            boolean active = state != null && state.channeling();
            channelButton.setMessage(
                    Component.literal(
                            active
                                    ? "STOP CHANNELING"
                                    : "START CHANNELING"
                    )
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
        }
        if (settingsCloseButton != null) {
            settingsCloseButton.visible = settingsOpen
                    && state != null
                    && state.allowed();
        }

        if (dissolutionModeButton != null) {
            boolean showMode = settingsOpen
                    && state != null
                    && state.allowed()
                    && menu.activeMachineSlots() > 1;
            dissolutionModeButton.visible = showMode;
            if (showMode) {
                EssenceCrucibleDissolutionMode mode =
                        EssenceCrucibleDissolutionMode.fromSerializedName(
                                state.dissolutionMode()
                        );
                dissolutionModeButton.setMessage(
                        Component.literal(mode.displayName())
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
        for (int i = 0; i < values.length && i < ventButtons.length; i++) {
            Button button = ventButtons[i];
            button.setY(firstVentRowY + i * VENT_ROW_HEIGHT);
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

            int slotX = x + EssenceCrucibleMenu.machineSlotX(slot) - 1;
            int slotY = y + EssenceCrucibleMenu.machineSlotY() - 1;
            int border = menu.isMachineSlotCurrentlyAvailable(slot)
                    ? BORDER
                    : 0xFF6E5151;
            graphics.fill(slotX, slotY, slotX + 20, slotY + 20, PANEL_INNER);
            outline(graphics, slotX, slotY, 20, 20, border);
        }

        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        /* Visual dissolution progress replaces the old numeric text line. */
        int barX = x + 105;
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

        boolean showSkill =
                state != null && state.skillEssencesEnabled();

        int attributeWidth =
                showSkill
                        ? TWO_COLUMN_WIDTH
                        : FULL_PANEL_WIDTH;

        MachineScreenUi.accentedInset(
                graphics,
                x + PANEL_MARGIN,
                y + PANEL_Y,
                attributeWidth,
                PANEL_HEIGHT
        );

        if (showSkill) {
            int skillX = x + PANEL_MARGIN + TWO_COLUMN_WIDTH + PANEL_GAP;
            MachineScreenUi.accentedInset(
                    graphics,
                    skillX,
                    y + PANEL_Y,
                    TWO_COLUMN_WIDTH,
                    PANEL_HEIGHT
            );
        }

        /* Enabled-family total/capacity stays independent of either column. */
        MachineScreenUi.accentedInset(
                graphics,
                x + PANEL_MARGIN,
                y + TOTAL_PANEL_Y,
                FULL_PANEL_WIDTH,
                TOTAL_PANEL_HEIGHT
        );

        /* Player inventory slot backing. */
        MachineScreenUi.inset(graphics, x + 67, y + 235, 166, 80);

    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        drawCentered(graphics, "ESSENCE CRUCIBLE", 7, TEXT);
        drawCentered(graphics, "Input Slots", 20, MUTED);

        if (state == null) {
            drawCentered(graphics, "Synchronizing...", 56, MUTED);
            graphics.drawString(font, playerInventoryTitle, 69, 224, MUTED, false);
            return;
        }

        boolean showSkill = state.skillEssencesEnabled();
        int attributeRight =
                showSkill
                        ? PANEL_MARGIN + TWO_COLUMN_WIDTH - 6
                        : PANEL_MARGIN + FULL_PANEL_WIDTH - 6;

        /* Attribute Essence is the actual family name for the six core values. */
        graphics.drawString(font, "Attribute Essence", 18, 78, TEXT, false);

        long[] attributeValues = state.essenceAmounts();
        for (int i = 0; i < attributeValues.length; i++) {
            int rowY = 92 + i * 12;
            graphics.drawString(
                    font,
                    EssenceCrucibleEssences.shortName(i),
                    18,
                    rowY,
                    MUTED,
                    false
            );
            drawRightAligned(
                    graphics,
                    format(attributeValues[i]),
                    attributeRight,
                    rowY,
                    TEXT
            );
        }

        if (showSkill) {
            int skillLeft =
                    PANEL_MARGIN + TWO_COLUMN_WIDTH + PANEL_GAP + 6;
            int skillRight =
                    PANEL_MARGIN + TWO_COLUMN_WIDTH + PANEL_GAP
                            + TWO_COLUMN_WIDTH - 6;

            graphics.drawString(font, "Skill Essence", skillLeft, 78, TEXT, false);

            long[] skillValues = state.skillEssenceAmounts();
            for (int i = 0; i < skillValues.length; i++) {
                int rowY = 92 + i * 9;
                graphics.drawString(
                        font,
                        EssenceCrucibleEssences.skillShortName(i),
                        skillLeft,
                        rowY,
                        MUTED,
                        false
                );
                drawRightAligned(
                        graphics,
                        format(skillValues[i]),
                        skillRight,
                        rowY,
                        TEXT
                );
            }
        }

        graphics.drawString(font, "Total Essence", 18, 186, TEXT, false);
        drawRightAligned(
                graphics,
                format(state.total()) + " / " + format(state.reservoirCapacity()),
                PANEL_MARGIN + FULL_PANEL_WIDTH - 6,
                186,
                TEXT
        );

        graphics.drawString(font, playerInventoryTitle, 69, 224, MUTED, false);
    }

    private void renderVentPopup(
            GuiGraphics graphics,
            EssenceCrucibleStatePayload state
    ) {
        int panelX = ventPanelX();
        int panelY = topPos + 4;
        long[] values = enabledEssenceValues(state);
        List<EssenceDefinition> enabled =
                EssenceCrucibleEssences.enabledOrdered(
                        state.skillEssencesEnabled()
                );
        int panelHeight = ventPanelHeight(state);

        MachineScreenUi.panel(
                graphics,
                panelX,
                panelY,
                VENT_PANEL_WIDTH,
                panelHeight
        );

        int textX = panelX + 7;
        int textWidth = VENT_PANEL_WIDTH - 14;
        MachineScreenUi.sectionHeader(graphics, font, "Settings", textX, panelY + 7);

        int cursorY = panelY + SETTINGS_HEADER_HEIGHT;
        if (showDissolutionModeSetting(state)) {
            MachineScreenUi.sectionHeader(
                    graphics,
                    font,
                    "Dissolution Mode",
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
                "Reservoir Vent",
                textX,
                cursorY
        );
        graphics.drawString(
                font,
                "Vented Essence is lost.",
                textX,
                cursorY + 10,
                MUTED,
                false
        );

        int firstRowY = ventPanelFirstRowY(state);
        for (int i = 0; i < values.length; i++) {
            int rowY = firstRowY + i * VENT_ROW_HEIGHT;
            String name = displayName(enabled.get(i));

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

    private String dissolutionModeDescription(EssenceCrucibleDissolutionMode mode) {
        return switch (mode) {
            case SMART_ROUND_ROBIN -> "Wait briefly, then skip.";
            case STRICT_ROUND_ROBIN -> "Wait until each lane fits.";
            case SKIP_ROUND_ROBIN -> "Use the first lane that fits.";
            case LOWEST_STORED -> "Favor least-stocked Essence.";
            case HIGHEST_STORED -> "Favor most-stocked Essence.";
        };
    }

    private void renderVentPopupWidgets(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (settingsCloseButton != null && settingsCloseButton.visible) {
            settingsCloseButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (dissolutionModeButton != null && dissolutionModeButton.visible) {
            dissolutionModeButton.render(graphics, mouseX, mouseY, partialTick);
        }
        for (Button button : ventButtons) {
            if (button != null && button.visible) {
                button.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderInfoPopupWidgets(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (infoCloseButton != null && infoCloseButton.visible) {
            infoCloseButton.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);

        if (settingsOpen && state != null && state.allowed()
                && pointInsideVentPanel(mouseX, mouseY, state)) {
            if (settingsCloseButton != null
                    && settingsCloseButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (dissolutionModeButton != null
                    && dissolutionModeButton.visible
                    && dissolutionModeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (Button ventButton : ventButtons) {
                if (ventButton != null
                        && ventButton.visible
                        && ventButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            /* Consume all remaining clicks inside the popup. */
            return true;
        }

        if (infoOpen && state != null
                && pointInsideInfoPanel(mouseX, mouseY)) {
            if (infoCloseButton != null
                    && infoCloseButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean mouseInsideOpenPopup(
            double mouseX,
            double mouseY,
            EssenceCrucibleStatePayload state
    ) {
        if (settingsOpen && state != null && state.allowed()) {
            return pointInsideVentPanel(mouseX, mouseY, state);
        }
        return infoOpen && state != null && pointInsideInfoPanel(mouseX, mouseY);
    }

    private boolean pointInsideInfoPanel(double mouseX, double mouseY) {
        int x = infoPanelX();
        int y = topPos + MachineScreenLayout.SIDE_PANEL_TOP_OFFSET;
        return MachineScreenLayout.contains(
                mouseX, mouseY, x, y, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT
        );
    }

    private boolean pointInsideVentPanel(
            double mouseX,
            double mouseY,
            EssenceCrucibleStatePayload state
    ) {
        int x = ventPanelX();
        int y = topPos + 4;
        int height = ventPanelHeight(state);
        return mouseX >= x
                && mouseX < x + VENT_PANEL_WIDTH
                && mouseY >= y
                && mouseY < y + height;
    }

    private boolean showDissolutionModeSetting(EssenceCrucibleStatePayload state) {
        return state != null && menu.activeMachineSlots() > 1;
    }

    private int ventPanelFirstRowY(EssenceCrucibleStatePayload state) {
        return topPos + 4
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
        long[] attribute = state.essenceAmounts();
        if (!state.skillEssencesEnabled()) {
            return attribute;
        }

        long[] skill = state.skillEssenceAmounts();
        long[] combined = new long[attribute.length + skill.length];
        System.arraycopy(attribute, 0, combined, 0, attribute.length);
        System.arraycopy(skill, 0, combined, attribute.length, skill.length);
        return combined;
    }

    private String displayName(
            EssenceDefinition essence
    ) {
        int attributeIndex = EssenceCrucibleEssences.ATTRIBUTE_ORDERED.indexOf(essence);
        if (attributeIndex >= 0) {
            return EssenceCrucibleEssences.shortName(attributeIndex);
        }

        int skillIndex = EssenceCrucibleEssences.SKILL_ORDERED.indexOf(essence);
        if (skillIndex >= 0) {
            return EssenceCrucibleEssences.skillShortName(skillIndex);
        }

        return essence.displayName();
    }

    private int ventPanelX() {
        int preferredLeft =
                leftPos - MachineScreenLayout.SIDE_PANEL_GAP - VENT_PANEL_WIDTH;
        if (preferredLeft >= 4) {
            return preferredLeft;
        }

        int alternateRight =
                leftPos + imageWidth + MachineScreenLayout.SIDE_PANEL_GAP;
        if (alternateRight + VENT_PANEL_WIDTH <= width - 4) {
            return alternateRight;
        }

        /*
         * Narrow-screen fallback: neither external side can fit the panel.
         * Clamp it against the left screen edge, allowing only the amount of
         * overlap with the main Crucible rectangle that is actually required.
         */
        return clampPanelX(preferredLeft, VENT_PANEL_WIDTH);
    }

    /*
     * Mekanism-style informational side panel. It prefers an external side,
     * but on narrow screens may overlap the Crucible as an opaque foreground
     * panel so it is never clipped off-screen.
     */
    private void renderInfoPopup(
            GuiGraphics graphics,
            EssenceCrucibleStatePayload state
    ) {
        int panelX = infoPanelX();
        int panelY = topPos + MachineScreenLayout.SIDE_PANEL_TOP_OFFSET;

        new MachineInfoPanel(
                graphics, font, panelX, panelY, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT
        )
                .title("Info")
                .metadata("Owner: " + state.ownerName())
                .metadata("Access: " + titleCase(state.accessMode()))
                .section("Crucible")
                .line("Capacity: " + format(state.reservoirCapacity()))
                .line("Pylons: " + state.activePylonCount() + "/" + state.maxActivePylons())
                .line(String.format(Locale.ROOT, "Pylon Radius: %.1f", state.pylonRadius()))
                .line("Input Slots: " + menu.activeMachineSlots())
                .section("Channeling")
                .line("Rate: " + format(state.transferRatePerSecond()) + "/sec")
                .line(String.format(Locale.ROOT, "Range: %.1f blocks", state.transferRange()))
                .section("Dissolution")
                .line("Items/Batch: " + state.simultaneousItemProcesses())
                .line("Batches/Sec: " + batchesPerSecond(state));
    }

    private int infoPanelX() {
        return MachineScreenLayout.infoPanelX(leftPos, imageWidth, width);
    }

    private int clampPanelX(int desiredX, int panelWidth) {
        int minX = 4;
        int maxX = Math.max(minX, width - 4 - panelWidth);
        return Math.max(minX, Math.min(desiredX, maxX));
    }

    private void drawCentered(
            GuiGraphics graphics,
            String text,
            int y,
            int color
    ) {
        graphics.drawString(
                font,
                text,
                (imageWidth - font.width(text)) / 2,
                y,
                color,
                false
        );
    }

    private void drawRightAligned(
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

    private void drawFittedAbsolute(
            GuiGraphics graphics,
            String text,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        graphics.drawString(
                font,
                fit(text, maxWidth),
                x,
                y,
                color,
                false
        );
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        int suffixWidth = font.width(suffix);
        int end = text.length();
        while (end > 0
                && font.width(text.substring(0, end)) + suffixWidth > maxWidth) {
            end--;
        }
        return text.substring(0, end) + suffix;
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

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static void outline(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        MachineScreenUi.outline(graphics, x, y, width, height, color);
    }
}
