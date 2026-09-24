package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.EssencePylonStatePayload;
import com.mistaboom.essence_ascendance.pylon.EssencePylonMenu;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

public final class EssencePylonScreen
        extends MachineContainerScreen<EssencePylonMenu> {

    private static final int INFO_PANEL_WIDTH = MachineScreenLayout.INFO_PANEL_WIDTH;
    private static final int INFO_PANEL_HEIGHT = 172;

    private Button infoButton;
    private Button infoCloseButton;
    private boolean infoOpen;

    public EssencePylonScreen(
            EssencePylonMenu menu,
            Inventory playerInventory,
            Component title
    ) {
        super(
                menu,
                playerInventory,
                title,
                new MachineScreenLayout.Spec(
                        230,
                        258,
                        new UiBounds(32, 174, 166, 80),
                        34,
                        163
                )
        );
    }

    @Override
    protected void initMachineWidgets() {
        EssencePylonClientState.requestState(menu.containerId);

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

    @Override
    public void onClose() {
        EssencePylonClientState.clear();
        super.onClose();
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
        MachineScreenUi.inputSlot(graphics, x, y, menu.getSlot(EssencePylonMenu.FOCUS_SLOT));
        MachineScreenUi.inset(graphics, x + 10, y + 58, 210, 34);
        MachineScreenUi.inset(graphics, x + 32, y + 174, 166, 80);
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        EssencePylonStatePayload state =
                EssencePylonClientState.snapshotFor(menu.containerId);

        drawCentered(graphics, EssenceText.gui("pylon.title"), 7, MachineScreenUi.TEXT);
        drawCentered(graphics, EssenceText.term("focus"), 20, MachineScreenUi.MUTED);

        if (state == null) {
            drawCentered(graphics, EssenceText.term("synchronizing"), 68, MachineScreenUi.MUTED);
            graphics.drawString(
                    font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                    MachineScreenUi.MUTED, false
            );
            return;
        }

        MachineScreenUi.row(
                graphics, font, EssenceText.term("status"), statusText(state),
                16, 214, 66, statusColor(state)
        );
        MachineScreenUi.row(
                graphics, font, EssenceText.term("installed_focus"), focusText(state),
                16, 214, 80
        );

        graphics.drawString(
                font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MachineScreenUi.MUTED, false
        );
    }

    @Override
    protected void updateMachineWidgets() {
        EssencePylonStatePayload state = EssencePylonClientState.snapshotFor(menu.containerId);
        if (infoButton != null) {
            infoButton.active = state != null;
        }
        if (infoCloseButton != null) {
            infoCloseButton.visible = infoOpen && state != null;
            place(infoCloseButton, overlayCloseBounds(infoPanelBounds()));
        }
    }

    @Override
    protected List<MachineOverlay> machineOverlays() {
        EssencePylonStatePayload state = EssencePylonClientState.snapshotFor(menu.containerId);
        if (!infoOpen || state == null) {
            return List.of();
        }
        UiBounds bounds = infoPanelBounds();
        return List.of(infoOverlay(
                "pylon_info",
                bounds,
                infoPanel(state),
                List.of(infoCloseButton)
        ));
    }

    private MachineInfoPanel infoPanel(EssencePylonStatePayload state) {
        return new MachineInfoPanel()
                .title(EssenceText.term("info"))
                .metadata(EssenceText.gui("owner", state.ownerName()))
                .section(EssenceText.term("link"))
                .line(EssenceText.gui("crucible_link", linkedText(state)))
                .line(EssenceText.gui(
                        "link_radius_blocks",
                        String.format(Locale.ROOT, "%.1f", state.pylonRadius())
                ))
                .section(EssenceText.term("contribution"))
                .line(EssenceText.gui("pylon.info.reservoir_bonus", format(state.reservoirCapacityBonus())))
                .line(EssenceText.gui("pylon.info.channel_rate_bonus", format(state.transferRatePerSecondBonus())))
                .line(EssenceText.gui("pylon.info.channel_range_bonus", decimal(state.transferRangeBonus())))
                .line(EssenceText.gui("pylon.info.dissolution_speed_bonus", decimal(state.dissolutionSpeedBonus() * 100.0D)))
                .line(EssenceText.gui("pylon.info.items_batch_bonus", state.simultaneousItemProcessesBonus()));
    }

    private static Component statusText(EssencePylonStatePayload state) {
        if (!state.focusInstalled()) {
            return EssenceText.gui("pylon.status.needs_focus");
        }
        if (!state.linked()) {
            return EssenceText.gui("pylon.status.unlinked");
        }
        if (!state.active()) {
            return EssenceText.gui("pylon.status.inactive_limit");
        }
        return EssenceText.gui("pylon.status.active");
    }

    private static int statusColor(EssencePylonStatePayload state) {
        if (!state.focusInstalled() || !state.linked() || !state.active()) {
            return MachineScreenUi.WARN;
        }
        return MachineScreenUi.GOOD;
    }

    private static Component focusText(EssencePylonStatePayload state) {
        if (!state.focusInstalled()) {
            return EssenceText.term("none");
        }
        if (state.focusTierName().equals("latent")) {
            return EssenceText.equipmentTier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.LATENT);
        }
        for (com.mistaboom.essence_ascendance.pylon.EssenceFocusTier tier
                : com.mistaboom.essence_ascendance.pylon.EssenceFocusTier.values()) {
            if (state.focusTierName().equals(tier.serializedName())) {
                return EssenceText.focusTier(tier);
            }
        }
        return EssenceText.term("unknown");
    }

    private static Component linkedText(EssencePylonStatePayload state) {
        if (!state.linked()) {
            return EssenceText.term("none");
        }
        BlockPos linked = BlockPos.of(state.linkedCruciblePos());
        return Component.literal(linked.getX() + ", " + linked.getY() + ", " + linked.getZ());
    }

    private UiBounds infoPanelBounds() {
        return sidePanel(
                INFO_PANEL_WIDTH,
                INFO_PANEL_HEIGHT,
                MachineScreenLayout.SidePreference.RIGHT_FIRST
        ).bounds();
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
