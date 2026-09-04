package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserMenu;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserWorkpieceMode;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionRecipe;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionRecipe;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** Procedural machine GUI using the shared normal-machine presentation system. */
public final class EssenceInfuserScreen
        extends AbstractContainerScreen<EssenceInfuserMenu> {

    private static final int INFO_PANEL_WIDTH = MachineScreenLayout.INFO_PANEL_WIDTH;
    private static final int INFO_PANEL_HEIGHT = 224;

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
        imageHeight = 338;
        inventoryLabelX = 34;
        inventoryLabelY = 243;
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
                        .bounds(leftPos + 45, topPos + 220, 140, 20)
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

        MachineScreenUi.inputSlot(graphics, x + 63, y + 59);
        MachineScreenUi.outputSlot(graphics, x + 147, y + 59);
        MachineScreenUi.inputSlot(graphics, x + 105, y + 23);

        EssenceInfuserWorkpieceMode mode = menu.workpieceMode();
        if (mode == EssenceInfuserWorkpieceMode.EQUIPMENT
                || (mode == EssenceInfuserWorkpieceMode.REPAIR
                    && menu.repairLatentIngotRequired() > 0)) {
            MachineScreenUi.inputSlot(graphics, x + 39, y + 59);
        }
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
            MachineScreenUi.inset(graphics, x + 10, y + 82, 210, 106);
            MachineScreenUi.inset(graphics, x + 10, y + 194, 210, 22);
        } else if (mode == EssenceInfuserWorkpieceMode.EQUIPMENT) {
            MachineScreenUi.progressBar(
                    graphics, x + 90, y + 61, 50, 14,
                    menu.equipmentTotalContributed(),
                    menu.equipmentRecipe().map(EquipmentInfusionRecipe::totalRequired).orElse(1L)
            );
            MachineScreenUi.inset(graphics, x + 10, y + 82, 210, 106);
            MachineScreenUi.inset(graphics, x + 10, y + 194, 210, 22);
        } else if (mode == EssenceInfuserWorkpieceMode.ESSENTIUM
                || mode == EssenceInfuserWorkpieceMode.REPAIR) {
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
            MachineScreenUi.inset(graphics, x + 10, y + 138, 210, 78);
        } else {
            MachineScreenUi.inset(graphics, x + 10, y + 82, 210, 106);
            MachineScreenUi.inset(graphics, x + 10, y + 194, 210, 22);
        }
        MachineScreenUi.inset(graphics, x + 32, y + 254, 166, 80);
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
        } else if (mode == EssenceInfuserWorkpieceMode.EQUIPMENT) {
            graphics.drawString(font, "Matrix", 22, 48, MachineScreenUi.MUTED, false);
            graphics.drawString(font, "Equipment", 62, 48, MachineScreenUi.MUTED, false);
            renderEquipmentInfusionLabels(graphics);
        } else if (mode == EssenceInfuserWorkpieceMode.REPAIR) {
            if (menu.repairLatentIngotRequired() > 0) {
                graphics.drawString(font, "Latent", 23, 48, MachineScreenUi.MUTED, false);
                graphics.drawString(font, "Equipment", 62, 48, MachineScreenUi.MUTED, false);
            } else {
                graphics.drawString(font, "Equipment", 50, 48, MachineScreenUi.MUTED, false);
            }
            graphics.drawString(font, "Output", 144, 48, MachineScreenUi.MUTED, false);
            renderRepairLabels(graphics);
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

        String hint = "The Infuser conforms to the workpiece placed within.";
        MachineScreenUi.wrapped(
                graphics,
                font,
                hint,
                25,
                132,
                imageWidth - 50,
                MachineScreenUi.MUTED,
                10,
                3
        );
        MachineScreenUi.row(
                graphics,
                font,
                "Status",
                statusText(menu.statusCode()),
                16,
                214,
                201,
                statusColor(menu.statusCode())
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

    private void renderRepairLabels(GuiGraphics graphics) {
        drawSelectionLine(graphics, "Essence", menu.sourceEssence(), 89);

        MachineScreenUi.row(
                graphics, font, "Status", statusText(menu.statusCode()),
                16, 214, 144, statusColor(menu.statusCode())
        );
        MachineScreenUi.row(
                graphics, font, "Missing Durability",
                format(menu.repairMissingDurability()),
                16, 214, 156
        );
        MachineScreenUi.row(
                graphics, font, "Essence Cost",
                format(menu.sourceRequired()),
                16, 214, 168
        );
        MachineScreenUi.row(
                graphics, font, "Essence Available",
                format(menu.sourceAvailable()),
                16, 214, 180
        );
        if (menu.repairLatentIngotRequired() > 0) {
            int present = menu.getSlot(EssenceInfuserBlockEntity.COMPONENT_SLOT)
                    .getItem()
                    .getCount();
            MachineScreenUi.row(
                    graphics, font, "Latent Ingot",
                    present + " / " + menu.repairLatentIngotRequired(),
                    16, 214, 192,
                    present >= menu.repairLatentIngotRequired()
                            ? MachineScreenUi.GOOD
                            : MachineScreenUi.TEXT
            );
        }
    }

    private void renderFocusInfusionLabels(GuiGraphics graphics) {
        EssenceFocusTier target = menu.focusTargetTier();
        String currentName = EssenceFocusData.displayTier(menu.workpieceStack());
        String targetName = target == null ? "Unknown" : target.displayName();
        MachineScreenUi.sectionHeader(graphics, font, "Essence Focus", 16, 88);
        MachineScreenUi.row(
                graphics,
                font,
                "Tier",
                currentName + " -> " + targetName,
                20,
                210,
                100
        );

        int rowY = 112;
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
                176,
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
                201,
                statusColor(menu.statusCode())
        );
    }

    private void renderEquipmentInfusionLabels(GuiGraphics graphics) {
        EquipmentInfusionRecipe recipe = menu.equipmentRecipe().orElse(null);
        if (recipe == null) {
            MachineScreenUi.sectionHeader(graphics, font, "Equipment Infusion", 16, 88);
            MachineScreenUi.wrapped(
                    graphics,
                    font,
                    "Insert Ascendance equipment; the loaded Matrix keeps this mode open.",
                    20,
                    104,
                    190,
                    MachineScreenUi.MUTED,
                    10,
                    4
            );
            MachineScreenUi.row(
                    graphics,
                    font,
                    "Status",
                    "Waiting for Equipment",
                    16,
                    214,
                    201,
                    MachineScreenUi.WARN
            );
            return;
        }
        String itemName = menu.workpieceStack().getHoverName().getString();
        MachineScreenUi.sectionHeader(graphics, font, itemName, 16, 88);
        MachineScreenUi.row(graphics, font, "Tier", recipe.currentTier().displayName() + " -> " + recipe.targetTier().displayName(), 20, 210, 100);
        int rowY = 112;
        for (var entry : recipe.requirements().entrySet()) {
            EssenceDefinition essence = com.mistaboom.essence_ascendance.essence.EssenceRegistry.get(entry.getKey()).orElse(null);
            if (essence == null) continue;
            long current = menu.equipmentContribution(essence);
            long required = entry.getValue();
            MachineScreenUi.row(graphics, font, shortName(essence), format(current) + " / " + format(required), 20, 210, rowY,
                    current >= required ? MachineScreenUi.GOOD : MachineScreenUi.TEXT);
            rowY += 10;
        }
        MachineScreenUi.row(graphics, font, "Total", format(menu.equipmentTotalContributed()) + " / " + format(recipe.totalRequired()),
                20, 210, 176, menu.equipmentTotalContributed() >= recipe.totalRequired() ? MachineScreenUi.GOOD : MachineScreenUi.TEXT);
        MachineScreenUi.row(graphics, font, "Status", statusText(menu.statusCode()), 16, 214, 201, statusColor(menu.statusCode()));
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
        boolean equipmentMode = mode == EssenceInfuserWorkpieceMode.EQUIPMENT;
        boolean repairMode = mode == EssenceInfuserWorkpieceMode.REPAIR;
        boolean sourceSelectable = carrierMode || repairMode;
        sourcePreviousButton.visible = sourceSelectable;
        sourceNextButton.visible = sourceSelectable;
        targetPreviousButton.visible = carrierMode;
        targetNextButton.visible = carrierMode;
        sourcePreviousButton.active = sourceSelectable;
        sourceNextButton.active = sourceSelectable;
        targetPreviousButton.active = carrierMode;
        targetNextButton.active = carrierMode;

        if (processingButton != null) {
            processingButton.visible = mode != EssenceInfuserWorkpieceMode.NONE;
            boolean hasActionableWorkpiece = mode != EssenceInfuserWorkpieceMode.NONE
                    && !(equipmentMode && menu.equipmentRecipe().isEmpty());
            processingButton.active = hasActionableWorkpiece;
            if (mode != EssenceInfuserWorkpieceMode.NONE) {
                String action = repairMode
                        ? "REPAIR"
                        : (focusMode || equipmentMode) ? "INFUSION" : "PROCESSING";
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
        int y = topPos + MachineScreenLayout.SIDE_PANEL_TOP_OFFSET;

        MachineInfoPanel info = new MachineInfoPanel(
                graphics, font, x, y, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT
        )
                .title("Info")
                .metadata("Owner: " + ownerText())
                .section("Link")
                .line("Crucible: " + linkedText())
                .line(String.format(
                        Locale.ROOT,
                        "Link Radius: %.1f blocks",
                        EssenceInfuserBalance.linkRange()
                ));

        EssenceInfuserWorkpieceMode mode = menu.workpieceMode();
        if (mode == EssenceInfuserWorkpieceMode.FOCUS) {
            renderFocusInfo(info);
        } else if (mode == EssenceInfuserWorkpieceMode.EQUIPMENT) {
            renderEquipmentInfo(info);
        } else if (mode == EssenceInfuserWorkpieceMode.REPAIR) {
            renderRepairInfo(info);
        } else if (mode == EssenceInfuserWorkpieceMode.ESSENTIUM) {
            renderCarrierInfo(info);
        } else {
            info.section("Workpiece")
                    .line("Insert an infusable item to view its settings.");
        }
    }

    private void renderCarrierInfo(MachineInfoPanel info) {
        info.section("Infusion")
                .line("Source Cost/Item: " + format(menu.sourceRequired()))
                .line("Output Essence/Item: " + format(menu.targetCapacity()))
                .line("Rate: " + format(menu.infusionThroughputPerSecond()) + "/sec")
                .line(String.format(
                        Locale.ROOT,
                        "Processing Time: %.2f sec/item",
                        menu.requiredProcessingTicks() / 20.0D
                ))
                .section("Automation")
                .line("Input: Manual / Hopper / Pipe")
                .line("Output: Essentium carrier in the output slot.");
    }

    private void renderRepairInfo(MachineInfoPanel info) {
        info.section("Repair")
                .line("Missing Durability: " + format(menu.repairMissingDurability()))
                .line("Essence Cost: " + format(menu.sourceRequired()))
                .line("Rate: " + format(menu.infusionThroughputPerSecond()) + "/sec");

        if (menu.repairLatentIngotRequired() > 0) {
            info.line("Fractured Material: "
                    + menu.repairLatentIngotRequired()
                    + " Latent Ingot");
        }

        info.section("Automation")
                .line("Input: Manual")
                .line(
                        "Output: Repaired equipment moves to the output slot; "
                                + "Fractured state is cleared."
                );
    }

    private void renderEquipmentInfo(MachineInfoPanel info) {
        EquipmentInfusionRecipe recipe = menu.equipmentRecipe().orElse(null);
        info.section("Equipment Infusion");
        if (recipe == null) {
            info.line(
                    "Matrix loaded. Insert Ascendance equipment to continue; "
                            + "the Matrix keeps Equipment mode open."
            );
        } else {
            info.line("Target: " + recipe.targetTier().displayName())
                    .line("Matrices: " + recipe.matrixCount())
                    .line("Rate: " + format(menu.infusionThroughputPerSecond()) + "/sec");
        }

        info.section("Automation")
                .line("Input: Manual")
                .line(
                        "Output: Same equipment, upgraded on completion; "
                                + "partial progress stays on the equipment."
                );
    }

    private void renderFocusInfo(MachineInfoPanel info) {
        EssenceFocusTier target = menu.focusTargetTier();
        EssenceFocusTier required = menu.focusRequiredInstalledTier();

        info.section("Focus Infusion")
                .line("Target: " + (target == null ? "Unknown" : target.displayName()))
                .line("Required Focus: " + (required == null ? "None" : required.displayName()))
                .line("Rate: " + format(menu.infusionThroughputPerSecond()) + "/sec")
                .section("Automation")
                .line("Input: Manual")
                .line(
                        "Output: Same Essence Focus, upgraded on completion; "
                                + "partial progress stays on the Focus."
                );
    }

    private int infoPanelX() {
        return MachineScreenLayout.infoPanelX(leftPos, imageWidth, width);
    }

    private boolean mouseInsideInfo(double mouseX, double mouseY) {
        if (!infoOpen) {
            return false;
        }
        int x = infoPanelX();
        int y = topPos + MachineScreenLayout.SIDE_PANEL_TOP_OFFSET;
        return MachineScreenLayout.contains(
                mouseX, mouseY, x, y, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT
        );
    }

    private String ownerText() {
        return minecraft != null && minecraft.player != null
                ? minecraft.player.getGameProfile().getName()
                : "Bound Player";
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
                        case EssenceInfuserBlockEntity.STATUS_COMPONENT_REQUIRED -> "Needs Ascendance Matrix";
            case EssenceInfuserBlockEntity.STATUS_REPAIR_MATERIAL_REQUIRED -> "Needs Latent Ingot";
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
