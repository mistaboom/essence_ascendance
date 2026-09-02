package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserMenu;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserWorkpieceMode;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionRecipe;
import com.mistaboom.essence_ascendance.pylon.EssencePylonFocusTier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** Procedural machine GUI using the shared normal-machine presentation system. */
public final class EssenceInfuserScreen
        extends AbstractContainerScreen<EssenceInfuserMenu> {

    private static final int INFO_PANEL_WIDTH = 174;
    private static final int INFO_PANEL_HEIGHT = 178;
    private static final int SIDE_PANEL_GAP = 4;

    private Button sourcePreviousButton;
    private Button sourceNextButton;
    private Button targetPreviousButton;
    private Button targetNextButton;
    private Button processingButton;
    private Button infoButton;
    private Button infoCloseButton;
    private boolean infoOpen;

    public EssenceInfuserScreen(
            EssenceInfuserMenu menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title);
        imageWidth = 230;
        imageHeight = 316;
        inventoryLabelX = 34;
        inventoryLabelY = 221;
    }

    @Override
    protected void init() {
        super.init();

        sourcePreviousButton = addCycleButton(
                leftPos + 66, topPos + 84, "<", EssenceInfuserMenu.BUTTON_SOURCE_PREVIOUS
        );
        sourceNextButton = addCycleButton(
                leftPos + 198, topPos + 84, ">", EssenceInfuserMenu.BUTTON_SOURCE_NEXT
        );
        targetPreviousButton = addCycleButton(
                leftPos + 66, topPos + 108, "<", EssenceInfuserMenu.BUTTON_TARGET_PREVIOUS
        );
        targetNextButton = addCycleButton(
                leftPos + 198, topPos + 108, ">", EssenceInfuserMenu.BUTTON_TARGET_NEXT
        );

        processingButton = addRenderableWidget(
                Button.builder(
                                Component.literal("START PROCESSING"),
                                button -> clickMenuButton(EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING)
                        )
                        .bounds(leftPos + 45, topPos + 198, 140, 20)
                        .build()
        );

        infoButton = addRenderableWidget(
                Button.builder(
                                Component.literal("i"),
                                button -> infoOpen = !infoOpen
                        )
                        .bounds(leftPos + imageWidth - 20, topPos + 5, 15, 15)
                        .build()
        );

        infoCloseButton = addRenderableWidget(
                Button.builder(
                                Component.literal("X"),
                                button -> infoOpen = false
                        )
                        .bounds(infoPanelX() + INFO_PANEL_WIDTH - 18, topPos + 8, 12, 12)
                        .build()
        );
        infoCloseButton.visible = false;
    }

    private Button addCycleButton(int x, int y, String label, int id) {
        return addRenderableWidget(
                Button.builder(
                                Component.literal(label),
                                button -> clickMenuButton(id)
                        )
                        .bounds(x, y, 18, 18)
                        .build()
        );
    }

    private void clickMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
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

        slotBox(graphics, x + 63, y + 59);
        slotBox(graphics, x + 147, y + 59);
        slotBox(graphics, x + 105, y + 23);

        EssenceInfuserWorkpieceMode mode = menu.workpieceMode();
        if (mode == EssenceInfuserWorkpieceMode.FOCUS) {
            MachineScreenUi.progressBar(
                    graphics,
                    x + 90,
                    y + 61,
                    50,
                    14,
                    menu.focusTotalContributed(),
                    menu.focusTotalRequired()
            );
            MachineScreenUi.inset(graphics, x + 10, y + 82, 210, 86);
            MachineScreenUi.inset(graphics, x + 10, y + 172, 210, 22);
        } else if (mode == EssenceInfuserWorkpieceMode.ESSENTIUM) {
            MachineScreenUi.progressBar(
                    graphics,
                    x + 90,
                    y + 61,
                    50,
                    14,
                    menu.processingTicks(),
                    menu.requiredProcessingTicks()
            );
            MachineScreenUi.inset(graphics, x + 10, y + 82, 210, 50);
            MachineScreenUi.inset(graphics, x + 10, y + 138, 210, 56);
        } else {
            MachineScreenUi.inset(graphics, x + 10, y + 82, 210, 112);
        }
        MachineScreenUi.inset(graphics, x + 32, y + 232, 166, 80);
    }

    private static void slotBox(GuiGraphics graphics, int x, int y) {
        MachineScreenUi.inset(graphics, x, y, 18, 18);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, "ESSENCE INFUSER", 6, MachineScreenUi.TEXT);

        graphics.drawString(font, "Focus", 72, 29, MachineScreenUi.MUTED, false);
        EssenceInfuserWorkpieceMode mode = menu.workpieceMode();
        if (mode == EssenceInfuserWorkpieceMode.FOCUS) {
            graphics.drawString(font, "Workpiece", 50, 48, MachineScreenUi.MUTED, false);
            graphics.drawString(font, "Upgrade", 141, 48, MachineScreenUi.MUTED, false);
            renderFocusInfusionLabels(graphics);
        } else if (mode == EssenceInfuserWorkpieceMode.ESSENTIUM) {
            graphics.drawString(font, "Latent", 59, 48, MachineScreenUi.MUTED, false);
            graphics.drawString(font, "Essentium", 140, 48, MachineScreenUi.MUTED, false);
            renderCarrierLabels(graphics);
        } else {
            graphics.drawString(font, "Workpiece", 50, 48, MachineScreenUi.MUTED, false);
            graphics.drawString(font, "Output", 144, 48, MachineScreenUi.MUTED, false);
            renderEmptyWorkpieceLabels(graphics);
        }

        graphics.drawString(
                font,
                playerInventoryTitle,
                inventoryLabelX,
                inventoryLabelY,
                MachineScreenUi.MUTED,
                false
        );
    }


    private void renderEmptyWorkpieceLabels(GuiGraphics graphics) {
        String title = "Insert an infusable item";
        graphics.drawString(
                font,
                title,
                (imageWidth - font.width(title)) / 2,
                115,
                MachineScreenUi.TEXT,
                false
        );

        String hint = "The interface adapts to the workpiece.";
        MachineScreenUi.fitted(
                graphics,
                font,
                hint,
                25,
                132,
                imageWidth - 50,
                MachineScreenUi.MUTED
        );
    }

    private void renderCarrierLabels(GuiGraphics graphics) {
        drawSelectionLine(graphics, "Source", menu.sourceEssence(), 89);
        drawSelectionLine(graphics, "Target", menu.targetEssence(), 113);

        MachineScreenUi.row(
                graphics, font, "Status", statusText(menu.statusCode()),
                16, 214, 144, statusColor(menu.statusCode())
        );
        MachineScreenUi.row(
                graphics, font, "Installed Focus", focusText(),
                16, 214, 156
        );
        MachineScreenUi.row(
                graphics, font, "Efficiency",
                String.format(Locale.ROOT, "%.1f%%", menu.efficiencyBasisPoints() / 100.0D),
                16, 214, 168
        );
        MachineScreenUi.row(
                graphics, font, "Essence Available", format(menu.sourceAvailable()),
                16, 214, 180
        );
    }

    private void renderFocusInfusionLabels(GuiGraphics graphics) {
        EssencePylonFocusTier target = menu.focusTargetTier();
        String targetName = target == null ? "Unknown" : target.displayName();
        MachineScreenUi.sectionHeader(
                graphics,
                font,
                "Focus Infusion -> " + targetName,
                16,
                88
        );

        int rowY = 100;
        long minimum = menu.focusMinimumPerEssence();
        for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
            long current = menu.focusContribution(essence);
            String label = shortName(essence);
            String value = format(current) + " / " + format(minimum);
            int color = current >= minimum ? MachineScreenUi.GOOD : MachineScreenUi.TEXT;
            MachineScreenUi.row(graphics, font, label, value, 20, 210, rowY, color);
            rowY += 10;
        }

        MachineScreenUi.row(
                graphics,
                font,
                "Total",
                format(menu.focusTotalContributed()) + " / " + format(menu.focusTotalRequired()),
                20,
                210,
                160,
                menu.focusTotalContributed() >= menu.focusTotalRequired()
                        ? MachineScreenUi.GOOD
                        : MachineScreenUi.TEXT
        );
        MachineScreenUi.row(
                graphics,
                font,
                "Status",
                statusText(menu.statusCode()),
                16,
                214,
                179,
                statusColor(menu.statusCode())
        );
    }

    private void drawSelectionLine(
            GuiGraphics graphics,
            String label,
            EssenceDefinition essence,
            int y
    ) {
        graphics.drawString(font, label, 16, y, MachineScreenUi.MUTED, false);
        String value = shortName(essence);
        int valueLeft = 88;
        int valueRight = 196;
        int valueX = valueLeft + Math.max(0, (valueRight - valueLeft - font.width(value)) / 2);
        MachineScreenUi.fitted(
                graphics,
                font,
                value,
                valueX,
                y,
                valueRight - valueX,
                MachineScreenUi.TEXT
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

        if (infoOpen) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            renderInfoPopup(graphics);
            infoCloseButton.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }

        if (!mouseInsideInfo(mouseX, mouseY)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private void updateButtons() {
        EssenceInfuserWorkpieceMode mode = menu.workpieceMode();
        boolean carrierMode = mode == EssenceInfuserWorkpieceMode.ESSENTIUM;
        boolean focusMode = mode == EssenceInfuserWorkpieceMode.FOCUS;
        sourcePreviousButton.visible = carrierMode;
        sourceNextButton.visible = carrierMode;
        targetPreviousButton.visible = carrierMode;
        targetNextButton.visible = carrierMode;
        sourcePreviousButton.active = carrierMode;
        sourceNextButton.active = carrierMode;
        targetPreviousButton.active = carrierMode;
        targetNextButton.active = carrierMode;

        if (processingButton != null) {
            processingButton.visible = mode != EssenceInfuserWorkpieceMode.NONE;
            processingButton.active = mode != EssenceInfuserWorkpieceMode.NONE;
            if (mode != EssenceInfuserWorkpieceMode.NONE) {
                String action = focusMode ? "INFUSION" : "PROCESSING";
                String label;
                if (!menu.processingEnabled()) {
                    label = "START " + action;
                } else if (menu.statusCode()
                        == EssenceInfuserBlockEntity.STATUS_PLAYER_CHANNELING) {
                    label = "RESUME " + action;
                } else {
                    label = "STOP " + action;
                }
                processingButton.setMessage(Component.literal(label));
            }
        }
        if (infoCloseButton != null) {
            infoCloseButton.visible = infoOpen;
            infoCloseButton.setX(infoPanelX() + INFO_PANEL_WIDTH - 18);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (infoOpen && mouseInsideInfo(mouseX, mouseY)) {
            if (infoCloseButton != null
                    && infoCloseButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderInfoPopup(GuiGraphics graphics) {
        int x = infoPanelX();
        int y = topPos + 4;
        MachineScreenUi.panel(graphics, x, y, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT);

        int textX = x + 7;
        int width = INFO_PANEL_WIDTH - 14;
        MachineScreenUi.sectionHeader(graphics, font, "Info", textX, y + 7);

        MachineScreenUi.sectionHeader(graphics, font, "Link", textX, y + 25);
        MachineScreenUi.indentedLine(
                graphics, font, "Crucible: " + linkedText(),
                textX, y + 36, width
        );
        MachineScreenUi.indentedLine(
                graphics, font,
                String.format(Locale.ROOT, "Range: %.1f blocks", EssenceInfuserBalance.linkRange()),
                textX, y + 47, width
        );

        EssenceInfuserWorkpieceMode mode = menu.workpieceMode();
        if (mode == EssenceInfuserWorkpieceMode.FOCUS) {
            renderFocusInfo(graphics, textX, y, width);
        } else if (mode == EssenceInfuserWorkpieceMode.ESSENTIUM) {
            renderCarrierInfo(graphics, textX, y, width);
        } else {
            MachineScreenUi.sectionHeader(graphics, font, "Workpiece", textX, y + 64);
            MachineScreenUi.indentedLine(
                    graphics, font, "Insert an infusable item to view its settings.",
                    textX, y + 75, width
            );
        }
    }

    private void renderCarrierInfo(GuiGraphics graphics, int textX, int y, int width) {
        MachineScreenUi.sectionHeader(graphics, font, "Infusion", textX, y + 64);
        MachineScreenUi.indentedLine(
                graphics, font, "Source Cost/Item: " + format(menu.sourceRequired()),
                textX, y + 75, width
        );
        MachineScreenUi.indentedLine(
                graphics, font, "Output Essence/Item: " + format(menu.targetCapacity()),
                textX, y + 86, width
        );
        MachineScreenUi.indentedLine(
                graphics, font,
                String.format(Locale.ROOT, "Processing Time: %.2f sec/item", menu.requiredProcessingTicks() / 20.0D),
                textX, y + 97, width
        );
        double perMinute = 1200.0D / Math.max(1, menu.requiredProcessingTicks());
        MachineScreenUi.indentedLine(
                graphics, font,
                String.format(Locale.ROOT, "Max Throughput: %.2f items/min", perMinute),
                textX, y + 108, width
        );

        MachineScreenUi.sectionHeader(graphics, font, "Automation", textX, y + 125);
        MachineScreenUi.indentedLine(
                graphics, font, "Input: Latent Carrier",
                textX, y + 136, width
        );
        MachineScreenUi.indentedLine(
                graphics, font, "Output: Essentium",
                textX, y + 147, width
        );
    }

    private void renderFocusInfo(GuiGraphics graphics, int textX, int y, int width) {
        EssencePylonFocusTier target = menu.focusTargetTier();
        EssencePylonFocusTier required = menu.focusRequiredInstalledTier();

        MachineScreenUi.sectionHeader(graphics, font, "Focus Infusion", textX, y + 64);
        MachineScreenUi.indentedLine(
                graphics, font,
                "Target: " + (target == null ? "Unknown" : target.displayName()),
                textX, y + 75, width
        );
        MachineScreenUi.indentedLine(
                graphics, font,
                "Required Focus: " + (required == null ? "None" : required.displayName()),
                textX, y + 86, width
        );
        MachineScreenUi.indentedLine(
                graphics, font,
                "Minimum/Essence: " + format(menu.focusMinimumPerEssence()),
                textX, y + 97, width
        );
        MachineScreenUi.indentedLine(
                graphics, font,
                "Total Required: " + format(menu.focusTotalRequired()),
                textX, y + 108, width
        );
        MachineScreenUi.indentedLine(
                graphics, font,
                "Infusion Rate: " + format(menu.focusInfusionRatePerSecond()) + "/sec",
                textX, y + 119, width
        );

        MachineScreenUi.sectionHeader(graphics, font, "Automation", textX, y + 136);
        MachineScreenUi.indentedLine(
                graphics, font, "Workpiece Input: Manual",
                textX, y + 147, width
        );
        MachineScreenUi.indentedLine(
                graphics, font, "Output: Upgraded Focus",
                textX, y + 158, width
        );
    }

    private int infoPanelX() {
        int right = leftPos + imageWidth + SIDE_PANEL_GAP;
        if (right + INFO_PANEL_WIDTH <= width - 4) {
            return right;
        }
        int left = leftPos - SIDE_PANEL_GAP - INFO_PANEL_WIDTH;
        if (left >= 4) {
            return left;
        }
        return Math.max(4, Math.min(right, width - 4 - INFO_PANEL_WIDTH));
    }

    private boolean mouseInsideInfo(double mouseX, double mouseY) {
        if (!infoOpen) {
            return false;
        }
        int x = infoPanelX();
        int y = topPos + 4;
        return mouseX >= x && mouseX < x + INFO_PANEL_WIDTH
                && mouseY >= y && mouseY < y + INFO_PANEL_HEIGHT;
    }

    private String linkedText() {
        if (!menu.linked()) {
            return "None";
        }
        var pos = menu.linkedPos();
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private String focusText() {
        var focus = menu.installedFocusTier();
        return focus == null ? "None / Dormant" : focus.displayName();
    }

    private static String statusText(int status) {
        return switch (status) {
            case EssenceInfuserBlockEntity.STATUS_STOPPED -> "Stopped";
            case EssenceInfuserBlockEntity.STATUS_UNLINKED -> "Paused - No Crucible";
            case EssenceInfuserBlockEntity.STATUS_INVALID_SELECTION -> "Invalid Selection";
            case EssenceInfuserBlockEntity.STATUS_INSUFFICIENT_SOURCE -> "Waiting - Need Essence";
            case EssenceInfuserBlockEntity.STATUS_OUTPUT_BLOCKED -> "Paused - Output Blocked";
            case EssenceInfuserBlockEntity.STATUS_PROCESSING -> "Processing";
            case EssenceInfuserBlockEntity.STATUS_INVALID_INPUT -> "Invalid Workpiece";
            case EssenceInfuserBlockEntity.STATUS_PLAYER_CHANNELING -> "Paused - Player Channeling";
            case EssenceInfuserBlockEntity.STATUS_FOCUS_TIER_REQUIRED -> "Needs Stronger Focus";
            case EssenceInfuserBlockEntity.STATUS_FOCUS_MALFORMED -> "Invalid Focus Data";
            default -> "Waiting for Input";
        };
    }

    private static int statusColor(int status) {
        return switch (status) {
            case EssenceInfuserBlockEntity.STATUS_PROCESSING -> MachineScreenUi.GOOD;
            case EssenceInfuserBlockEntity.STATUS_INVALID_SELECTION,
                 EssenceInfuserBlockEntity.STATUS_INVALID_INPUT,
                 EssenceInfuserBlockEntity.STATUS_FOCUS_MALFORMED -> MachineScreenUi.BAD;
            case EssenceInfuserBlockEntity.STATUS_STOPPED -> MachineScreenUi.MUTED;
            default -> MachineScreenUi.WARN;
        };
    }

    private void drawCentered(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawString(font, text, (imageWidth - font.width(text)) / 2, y, color, false);
    }

    private static String shortName(EssenceDefinition essence) {
        if (essence == null) {
            return "None";
        }
        String display = essence.displayName();
        return display.endsWith(" Essence")
                ? display.substring(0, display.length() - " Essence".length())
                : display;
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }
}
