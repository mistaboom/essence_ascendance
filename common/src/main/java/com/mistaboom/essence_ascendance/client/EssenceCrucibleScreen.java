package com.mistaboom.essence_ascendance.client;

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

    private static final int PANEL = 0xFF20242B;
    private static final int PANEL_INNER = 0xFF2D333D;
    private static final int BORDER = 0xFF8A70B5;
    private static final int TEXT = 0xFFE9E9EF;
    private static final int MUTED = 0xFFAEB4C0;

    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_GAP = 12;
    private static final int PANEL_Y = 72;
    private static final int PANEL_HEIGHT = 106;
    private static final int TWO_COLUMN_WIDTH = 132;
    private static final int FULL_PANEL_WIDTH = 276;
    private static final int TOTAL_PANEL_Y = 182;
    private static final int TOTAL_PANEL_HEIGHT = 17;

    private static final int INFO_PANEL_WIDTH = 154;
    private static final int INFO_PANEL_HEIGHT = 92;
    private static final int SIDE_PANEL_GAP = 4;

    private static final int VENT_PANEL_WIDTH = 172;
    private static final int VENT_HEADER_HEIGHT = 29;
    private static final int VENT_ROW_HEIGHT = 14;
    private static final int VENT_FOOTER_HEIGHT = 8;
    private static final int VENT_BUTTON_WIDTH = 38;
    private static final int VENT_BUTTON_HEIGHT = 12;

    private Button channelButton;
    private Button infoButton;
    private Button settingsButton;
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
                                leftPos + 90,
                                topPos + 202,
                                120,
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
                                    ventPanelY + VENT_HEADER_HEIGHT + i * VENT_ROW_HEIGHT,
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

        if (infoOpen && state != null) {
            renderInfoPopup(graphics, state);
        }

        renderTooltip(graphics, mouseX, mouseY);
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

        for (int i = 0; i < values.length && i < ventButtons.length; i++) {
            Button button = ventButtons[i];
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

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        outline(graphics, x, y, imageWidth, imageHeight, BORDER);

        /* Centered machine slot. */
        graphics.fill(x + 140, y + 32, x + 160, y + 52, PANEL_INNER);
        outline(graphics, x + 140, y + 32, 20, 20, BORDER);

        EssenceCrucibleStatePayload state =
                EssenceCrucibleClientState.snapshotFor(menu.containerId);
        boolean showSkill =
                state != null && state.skillEssencesEnabled();

        int attributeWidth =
                showSkill
                        ? TWO_COLUMN_WIDTH
                        : FULL_PANEL_WIDTH;

        graphics.fill(
                x + PANEL_MARGIN,
                y + PANEL_Y,
                x + PANEL_MARGIN + attributeWidth,
                y + PANEL_Y + PANEL_HEIGHT,
                PANEL_INNER
        );
        outline(
                graphics,
                x + PANEL_MARGIN,
                y + PANEL_Y,
                attributeWidth,
                PANEL_HEIGHT,
                BORDER
        );

        if (showSkill) {
            int skillX = x + PANEL_MARGIN + TWO_COLUMN_WIDTH + PANEL_GAP;
            graphics.fill(
                    skillX,
                    y + PANEL_Y,
                    skillX + TWO_COLUMN_WIDTH,
                    y + PANEL_Y + PANEL_HEIGHT,
                    PANEL_INNER
            );
            outline(
                    graphics,
                    skillX,
                    y + PANEL_Y,
                    TWO_COLUMN_WIDTH,
                    PANEL_HEIGHT,
                    BORDER
            );
        }

        /* Enabled-family total/capacity stays independent of either column. */
        graphics.fill(
                x + PANEL_MARGIN,
                y + TOTAL_PANEL_Y,
                x + PANEL_MARGIN + FULL_PANEL_WIDTH,
                y + TOTAL_PANEL_Y + TOTAL_PANEL_HEIGHT,
                PANEL_INNER
        );
        outline(
                graphics,
                x + PANEL_MARGIN,
                y + TOTAL_PANEL_Y,
                FULL_PANEL_WIDTH,
                TOTAL_PANEL_HEIGHT,
                BORDER
        );

        /* Player inventory slot backing. */
        graphics.fill(x + 67, y + 235, x + 233, y + 315, PANEL_INNER);
        outline(graphics, x + 67, y + 235, 166, 80, 0xFF535B68);

        if (settingsOpen && state != null && state.allowed()) {
            renderVentPopup(graphics, state);
        }
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
        drawCentered(graphics, "Slot", 20, MUTED);

        if (state == null) {
            drawCentered(graphics, "Synchronizing...", 56, MUTED);
            graphics.drawString(font, playerInventoryTitle, 69, 224, MUTED, false);
            return;
        }

        int required = Math.max(1, state.dissolutionTicksPerItem());
        int progress = Math.min(required, Math.max(0, state.processingTicks()));
        drawCentered(
                graphics,
                "Dissolution: " + progress + "/" + required,
                56,
                MUTED
        );

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
        int panelHeight =
                VENT_HEADER_HEIGHT
                        + values.length * VENT_ROW_HEIGHT
                        + VENT_FOOTER_HEIGHT;

        graphics.fill(
                panelX,
                panelY,
                panelX + VENT_PANEL_WIDTH,
                panelY + panelHeight,
                PANEL
        );
        outline(
                graphics,
                panelX,
                panelY,
                VENT_PANEL_WIDTH,
                panelHeight,
                BORDER
        );

        graphics.drawString(
                font,
                "Reservoir Vent",
                panelX + 7,
                panelY + 6,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                "Vented Essence is lost.",
                panelX + 7,
                panelY + 17,
                MUTED,
                false
        );

        for (int i = 0; i < values.length; i++) {
            int rowY = panelY + VENT_HEADER_HEIGHT + i * VENT_ROW_HEIGHT;
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
        int leftX =
                leftPos - SIDE_PANEL_GAP - VENT_PANEL_WIDTH;

        if (leftX >= 4) {
            return leftX;
        }

        return leftPos + imageWidth + SIDE_PANEL_GAP;
    }

    /*
     * Mekanism-style informational side panel: it is anchored outside the
     * Crucible's main rectangle and never displaces or covers the machine UI.
     */
    private void renderInfoPopup(
            GuiGraphics graphics,
            EssenceCrucibleStatePayload state
    ) {
        int panelX = infoPanelX();
        int panelY = topPos + 4;

        graphics.fill(
                panelX,
                panelY,
                panelX + INFO_PANEL_WIDTH,
                panelY + INFO_PANEL_HEIGHT,
                PANEL
        );
        outline(
                graphics,
                panelX,
                panelY,
                INFO_PANEL_WIDTH,
                INFO_PANEL_HEIGHT,
                BORDER
        );

        int textX = panelX + 7;
        int textWidth = INFO_PANEL_WIDTH - 14;

        graphics.drawString(font, "Info", textX, panelY + 7, TEXT, false);
        drawFittedAbsolute(
                graphics,
                "Owner: " + state.ownerName(),
                textX,
                panelY + 20,
                textWidth,
                MUTED
        );
        drawFittedAbsolute(
                graphics,
                "Access: " + state.accessMode(),
                textX,
                panelY + 31,
                textWidth,
                MUTED
        );
        drawFittedAbsolute(
                graphics,
                "Rate: " + format(state.transferRatePerSecond()) + "/sec",
                textX,
                panelY + 42,
                textWidth,
                TEXT
        );
        drawFittedAbsolute(
                graphics,
                String.format(Locale.ROOT, "Range: %.1f blocks", state.transferRange()),
                textX,
                panelY + 53,
                textWidth,
                TEXT
        );
        drawFittedAbsolute(
                graphics,
                "Capacity: " + format(state.reservoirCapacity()),
                textX,
                panelY + 64,
                textWidth,
                TEXT
        );
        drawFittedAbsolute(
                graphics,
                "Pylons: " + state.activePylonCount(),
                textX,
                panelY + 75,
                textWidth,
                MUTED
        );
    }

    private int infoPanelX() {
        int rightX =
                leftPos + imageWidth + SIDE_PANEL_GAP;

        if (rightX + INFO_PANEL_WIDTH <= width - 4) {
            return rightX;
        }

        return leftPos - SIDE_PANEL_GAP - INFO_PANEL_WIDTH;
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

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static void outline(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
