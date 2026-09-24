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
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/** Procedural machine GUI using the shared normal-machine presentation system. */
public final class EssenceInfuserScreen
        extends MachineContainerScreen<EssenceInfuserMenu> {

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
        super(
                menu,
                playerInventory,
                title,
                new MachineScreenLayout.Spec(
                        230,
                        338,
                        new UiBounds(32, 254, 166, 80),
                        34,
                        243
                )
        );
    }

    @Override
    protected void initMachineWidgets() {
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

        processingButton = addMachineButton(
                EssenceText.gui("infuser.button.start_processing"),
                button -> clickMenuButton(EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING),
                new UiBounds(leftPos + 45, topPos + 220, 140, 20)
        );

        infoButton = addHeaderButton(
                Component.literal("i"),
                true,
                button -> infoOpen = !infoOpen
        );

        infoCloseButton = addOverlayCloseButton(
                button -> infoOpen = false,
                infoPanelBounds()
        );
        infoCloseButton.visible = false;
    }

    private Button addCycleButton(int x, int y, String label, int id) {
        return addMachineButton(
                Component.literal(label),
                button -> clickMenuButton(id),
                new UiBounds(x, y, 18, 18)
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

        MachineScreenUi.inputSlot(graphics, x, y, menu.getSlot(EssenceInfuserBlockEntity.INPUT_SLOT));
        MachineScreenUi.outputSlot(graphics, x, y, menu.getSlot(EssenceInfuserBlockEntity.OUTPUT_SLOT));
        MachineScreenUi.inputSlot(graphics, x, y, menu.getSlot(EssenceInfuserBlockEntity.FOCUS_SLOT));

        EssenceInfuserWorkpieceMode mode = menu.workpieceMode();
        if (mode == EssenceInfuserWorkpieceMode.EQUIPMENT
                || (mode == EssenceInfuserWorkpieceMode.REPAIR
                    && menu.repairLatentIngotRequired() > 0)) {
            MachineScreenUi.inputSlot(graphics, x, y, menu.getSlot(EssenceInfuserBlockEntity.COMPONENT_SLOT));
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
        drawCentered(graphics, EssenceText.gui("infuser.title"), 6, MachineScreenUi.TEXT);

        graphics.drawString(font, EssenceText.term("focus"), 72, 29, MachineScreenUi.MUTED, false);
        EssenceInfuserWorkpieceMode mode = menu.workpieceMode();
        if (mode == EssenceInfuserWorkpieceMode.FOCUS) {
            graphics.drawString(font, EssenceText.term("workpiece"), 50, 48, MachineScreenUi.MUTED, false);
            graphics.drawString(font, EssenceText.term("upgrade"), 141, 48, MachineScreenUi.MUTED, false);
            renderFocusInfusionLabels(graphics);
        } else if (mode == EssenceInfuserWorkpieceMode.EQUIPMENT) {
            graphics.drawString(font, EssenceText.term("matrix"), 22, 48, MachineScreenUi.MUTED, false);
            graphics.drawString(font, EssenceText.term("equipment"), 62, 48, MachineScreenUi.MUTED, false);
            renderEquipmentInfusionLabels(graphics);
        } else if (mode == EssenceInfuserWorkpieceMode.REPAIR) {
            if (menu.repairLatentIngotRequired() > 0) {
                graphics.drawString(font, EssenceText.equipmentTier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.LATENT), 23, 48, MachineScreenUi.MUTED, false);
                graphics.drawString(font, EssenceText.term("equipment"), 62, 48, MachineScreenUi.MUTED, false);
            } else {
                graphics.drawString(font, EssenceText.term("equipment"), 50, 48, MachineScreenUi.MUTED, false);
            }
            graphics.drawString(font, EssenceText.term("output"), 144, 48, MachineScreenUi.MUTED, false);
            renderRepairLabels(graphics);
        } else if (mode == EssenceInfuserWorkpieceMode.ESSENTIUM) {
            graphics.drawString(font, EssenceText.equipmentTier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.LATENT), 59, 48, MachineScreenUi.MUTED, false);
            graphics.drawString(font, EssenceText.term("essentium"), 140, 48, MachineScreenUi.MUTED, false);
            renderCarrierLabels(graphics);
        } else {
            graphics.drawString(font, EssenceText.term("workpiece"), 50, 48, MachineScreenUi.MUTED, false);
            graphics.drawString(font, EssenceText.term("output"), 144, 48, MachineScreenUi.MUTED, false);
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
        Component title = EssenceText.gui("infuser.empty.title");
        graphics.drawString(
                font,
                title,
                (imageWidth - font.width(title)) / 2,
                115,
                MachineScreenUi.TEXT,
                false
        );

        Component hint = EssenceText.gui("infuser.empty.hint");
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
                EssenceText.term("status"),
                statusText(menu.statusCode()),
                16,
                214,
                201,
                statusColor(menu.statusCode())
        );
    }

    private void renderCarrierLabels(GuiGraphics graphics) {
        drawSelectionLine(graphics, EssenceText.term("source"), menu.sourceEssence(), 89);
        drawSelectionLine(graphics, EssenceText.term("target"), menu.targetEssence(), 113);

        MachineScreenUi.row(
                graphics, font, EssenceText.term("status"), statusText(menu.statusCode()),
                16, 214, 144, statusColor(menu.statusCode())
        );
        MachineScreenUi.row(
                graphics, font, EssenceText.term("installed_focus"), focusText(),
                16, 214, 156
        );
        MachineScreenUi.row(
                graphics, font, EssenceText.term("efficiency"),
                String.format(Locale.ROOT, "%.1f%%", menu.efficiencyBasisPoints() / 100.0D),
                16, 214, 168
        );
        MachineScreenUi.row(
                graphics, font, EssenceText.term("essence_available"), format(menu.sourceAvailable()),
                16, 214, 180
        );
    }

    private void renderRepairLabels(GuiGraphics graphics) {
        drawSelectionLine(graphics, EssenceText.term("essence"), menu.sourceEssence(), 89);

        MachineScreenUi.row(
                graphics, font, EssenceText.term("status"), statusText(menu.statusCode()),
                16, 214, 144, statusColor(menu.statusCode())
        );
        MachineScreenUi.row(
                graphics, font, EssenceText.term("missing_durability"),
                format(menu.repairMissingDurability()),
                16, 214, 156
        );
        MachineScreenUi.row(
                graphics, font, EssenceText.term("essence_cost"),
                format(menu.sourceRequired()),
                16, 214, 168
        );
        MachineScreenUi.row(
                graphics, font, EssenceText.term("essence_available"),
                format(menu.sourceAvailable()),
                16, 214, 180
        );
        if (menu.repairLatentIngotRequired() > 0) {
            int present = menu.getSlot(EssenceInfuserBlockEntity.COMPONENT_SLOT)
                    .getItem()
                    .getCount();
            MachineScreenUi.row(
                    graphics, font, EssenceText.term("latent_ingot"),
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
        Component currentName = EssenceFocusData.tier(menu.workpieceStack()) == null
                ? EssenceText.equipmentTier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.LATENT)
                : EssenceText.focusTier(EssenceFocusData.tier(menu.workpieceStack()));
        Component targetName = target == null ? EssenceText.term("unknown") : EssenceText.focusTier(target);
        MachineScreenUi.sectionHeader(graphics, font, EssenceText.term("essence_focus"), 16, 88);
        MachineScreenUi.row(
                graphics,
                font,
                EssenceText.term("tier"),
                EssenceText.gui("tier_transition", currentName, targetName),
                20,
                210,
                100
        );

        int rowY = 112;
        long minimum = menu.focusMinimumPerEssence();
        for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
            long current = menu.focusContribution(essence);
            Component label = shortName(essence);
            String value = format(current) + " / " + format(minimum);
            int color = current >= minimum ? MachineScreenUi.GOOD : MachineScreenUi.TEXT;
            MachineScreenUi.row(graphics, font, label, value, 20, 210, rowY, color);
            rowY += 10;
        }

        MachineScreenUi.row(
                graphics,
                font,
                EssenceText.term("total"),
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
                EssenceText.term("status"),
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
            MachineScreenUi.sectionHeader(graphics, font, EssenceText.term("equipment_infusion"), 16, 88);
            MachineScreenUi.wrapped(
                    graphics,
                    font,
                    EssenceText.gui("infuser.equipment.waiting_hint"),
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
                    EssenceText.term("status"),
                    EssenceText.gui("infuser.status.waiting_equipment"),
                    16,
                    214,
                    201,
                    MachineScreenUi.WARN
            );
            return;
        }
        Component itemName = menu.workpieceStack().getHoverName();
        MachineScreenUi.sectionHeader(graphics, font, itemName, 16, 88);
        MachineScreenUi.row(
                graphics, font, EssenceText.term("tier"),
                EssenceText.gui("tier_transition", EssenceText.equipmentTier(recipe.currentTier()), EssenceText.equipmentTier(recipe.targetTier())),
                20, 210, 100
        );
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
        MachineScreenUi.row(graphics, font, EssenceText.term("total"), format(menu.equipmentTotalContributed()) + " / " + format(recipe.totalRequired()),
                20, 210, 176, menu.equipmentTotalContributed() >= recipe.totalRequired() ? MachineScreenUi.GOOD : MachineScreenUi.TEXT);
        MachineScreenUi.row(graphics, font, EssenceText.term("status"), statusText(menu.statusCode()), 16, 214, 201, statusColor(menu.statusCode()));
    }

    private void drawSelectionLine(
            GuiGraphics graphics,
            Component label,
            EssenceDefinition essence,
            int y
    ) {
        graphics.drawString(font, label, 16, y, MachineScreenUi.MUTED, false);
        Component value = shortName(essence);
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
    protected void updateMachineWidgets() {
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
            boolean focusInstalled = menu.getSlot(EssenceInfuserBlockEntity.FOCUS_SLOT).hasItem();
            processingButton.active = hasActionableWorkpiece
                    && (focusInstalled || menu.processingEnabled());
            if (mode != EssenceInfuserWorkpieceMode.NONE) {
                String actionKey = repairMode
                        ? "repair"
                        : (focusMode || equipmentMode) ? "infusion" : "processing";
                String verbKey;
                if (!menu.processingEnabled()) {
                    verbKey = "start";
                } else if (menu.statusCode()
                        == EssenceInfuserBlockEntity.STATUS_PLAYER_CHANNELING) {
                    verbKey = "resume";
                } else {
                    verbKey = "stop";
                }
                processingButton.setMessage(EssenceText.gui(
                        "infuser.button." + verbKey + "_" + actionKey
                ));
            }
        }
        if (infoCloseButton != null) {
            infoCloseButton.visible = infoOpen;
            place(infoCloseButton, overlayCloseBounds(infoPanelBounds()));
        }
    }

    @Override
    protected List<MachineOverlay> machineOverlays() {
        if (!infoOpen) {
            return List.of();
        }
        UiBounds bounds = infoPanelBounds();
        return List.of(infoOverlay(
                "infuser_info",
                bounds,
                infoPanel(),
                List.of(infoCloseButton)
        ));
    }

    private MachineInfoPanel infoPanel() {
        MachineInfoPanel info = new MachineInfoPanel()
                .title(EssenceText.term("info"))
                .metadata(EssenceText.gui("owner", ownerText()))
                .section(EssenceText.term("link"))
                .line(EssenceText.gui("crucible_link", linkedText()))
                .line(EssenceText.gui(
                        "link_radius_blocks",
                        String.format(Locale.ROOT, "%.1f", EssenceInfuserBalance.linkRange())
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
            info.section(EssenceText.term("workpiece"))
                    .line(EssenceText.gui("infuser.info.insert_workpiece"));
        }
        return info;
    }

    private void renderCarrierInfo(MachineInfoPanel info) {
        info.section(EssenceText.term("infusion"))
                .line(EssenceText.gui("infuser.info.source_cost_per_item", format(menu.sourceRequired())))
                .line(EssenceText.gui("infuser.info.output_essence_per_item", format(menu.targetCapacity())))
                .line(EssenceText.gui("rate_per_second", format(menu.infusionThroughputPerSecond())))
                .line(EssenceText.gui(
                        "infuser.info.processing_time_per_item",
                        String.format(Locale.ROOT, "%.2f", menu.requiredProcessingTicks() / 20.0D)
                ))
                .section(EssenceText.term("automation"))
                .line(EssenceText.gui("infuser.info.input_automated"))
                .line(EssenceText.gui("infuser.info.output_essentium"));
    }

    private void renderRepairInfo(MachineInfoPanel info) {
        info.section(EssenceText.term("repair"))
                .line(EssenceText.gui("missing_durability_value", format(menu.repairMissingDurability())))
                .line(EssenceText.gui("essence_cost_value", format(menu.sourceRequired())))
                .line(EssenceText.gui("rate_per_second", format(menu.infusionThroughputPerSecond())));

        if (menu.repairLatentIngotRequired() > 0) {
            info.line(EssenceText.gui(
                    "infuser.info.fractured_material",
                    menu.repairLatentIngotRequired(),
                    Component.translatable("item.essence_ascendance.latent_ingot")
            ));
        }

        info.section(EssenceText.term("automation"))
                .line(EssenceText.gui("infuser.info.input_manual"))
                .line(EssenceText.gui("infuser.info.output_repair"));
    }

    private void renderEquipmentInfo(MachineInfoPanel info) {
        EquipmentInfusionRecipe recipe = menu.equipmentRecipe().orElse(null);
        info.section(EssenceText.term("equipment_infusion"));
        if (recipe == null) {
            info.line(EssenceText.gui("infuser.info.matrix_waiting"));
        } else {
            info.line(EssenceText.gui("target_value", EssenceText.equipmentTier(recipe.targetTier())))
                    .line(EssenceText.gui("matrices_value", recipe.matrixCount()))
                    .line(EssenceText.gui("rate_per_second", format(menu.infusionThroughputPerSecond())));
        }

        info.section(EssenceText.term("automation"))
                .line(EssenceText.gui("infuser.info.input_manual"))
                .line(EssenceText.gui("infuser.info.output_equipment"));
    }

    private void renderFocusInfo(MachineInfoPanel info) {
        EssenceFocusTier target = menu.focusTargetTier();
        EssenceFocusTier required = menu.focusRequiredInstalledTier();

        info.section(EssenceText.term("focus_infusion"))
                .line(EssenceText.gui("target_value", target == null ? EssenceText.term("unknown") : EssenceText.focusTier(target)))
                .line(EssenceText.gui("required_focus_value", required == null
                        ? EssenceText.equipmentTier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.LATENT)
                        : EssenceText.focusTier(required)))
                .line(EssenceText.gui("rate_per_second", format(menu.infusionThroughputPerSecond())))
                .section(EssenceText.term("automation"))
                .line(EssenceText.gui("infuser.info.input_manual"))
                .line(EssenceText.gui("infuser.info.output_focus"));
    }

    private UiBounds infoPanelBounds() {
        return sidePanel(
                INFO_PANEL_WIDTH,
                INFO_PANEL_HEIGHT,
                MachineScreenLayout.SidePreference.RIGHT_FIRST
        ).bounds();
    }

    private Component ownerText() {
        return minecraft != null && minecraft.player != null
                ? Component.literal(minecraft.player.getGameProfile().getName())
                : EssenceText.term("bound_player");
    }

    private Component linkedText() {
        if (!menu.linked()) {
            return EssenceText.term("none");
        }
        var pos = menu.linkedPos();
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    private Component focusText() {
        if (!menu.getSlot(EssenceInfuserBlockEntity.FOCUS_SLOT).hasItem()) {
            return EssenceText.gui("infuser.focus.none");
        }
        var focus = menu.installedFocusTier();
        return focus == null
                ? EssenceText.equipmentTier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.LATENT)
                : EssenceText.focusTier(focus);
    }

    private static Component statusText(int status) {
        String key = switch (status) {
            case EssenceInfuserBlockEntity.STATUS_STOPPED -> "stopped";
            case EssenceInfuserBlockEntity.STATUS_UNLINKED -> "unlinked";
            case EssenceInfuserBlockEntity.STATUS_INVALID_SELECTION -> "invalid_selection";
            case EssenceInfuserBlockEntity.STATUS_INSUFFICIENT_SOURCE -> "insufficient_source";
            case EssenceInfuserBlockEntity.STATUS_OUTPUT_BLOCKED -> "output_blocked";
            case EssenceInfuserBlockEntity.STATUS_PROCESSING -> "processing";
            case EssenceInfuserBlockEntity.STATUS_INVALID_INPUT -> "invalid_input";
            case EssenceInfuserBlockEntity.STATUS_PLAYER_CHANNELING -> "player_channeling";
            case EssenceInfuserBlockEntity.STATUS_FOCUS_TIER_REQUIRED -> "focus_tier_required";
            case EssenceInfuserBlockEntity.STATUS_FOCUS_MALFORMED -> "focus_malformed";
            case EssenceInfuserBlockEntity.STATUS_NEEDS_FOCUS -> "needs_focus";
            case EssenceInfuserBlockEntity.STATUS_COMPONENT_REQUIRED -> "component_required";
            case EssenceInfuserBlockEntity.STATUS_REPAIR_MATERIAL_REQUIRED -> "repair_material_required";
            default -> "waiting_input";
        };
        return EssenceText.gui("infuser.status." + key);
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

    private static Component shortName(EssenceDefinition essence) {
        return essence == null ? EssenceText.term("none") : EssenceText.essenceShort(essence);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }
}
