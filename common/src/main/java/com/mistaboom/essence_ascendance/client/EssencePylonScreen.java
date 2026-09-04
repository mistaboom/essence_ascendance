package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.EssencePylonStatePayload;
import com.mistaboom.essence_ascendance.pylon.EssencePylonMenu;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class EssencePylonScreen
        extends AbstractContainerScreen<EssencePylonMenu> {

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
        super(menu, playerInventory, title);
        imageWidth = 230;
        imageHeight = 258;
        inventoryLabelX = 34;
        inventoryLabelY = 163;
    }

    @Override
    protected void init() {
        super.init();
        EssencePylonClientState.requestState(menu.containerId);

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
        MachineScreenUi.inputSlot(graphics, x + 105, y + 31);
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
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        EssencePylonStatePayload state = EssencePylonClientState.snapshotFor(menu.containerId);
        if (infoButton != null) {
            infoButton.active = state != null;
        }
        if (infoCloseButton != null) {
            infoCloseButton.visible = infoOpen && state != null;
        }

        if (infoOpen && state != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            renderInfoPopup(graphics, state);
            infoCloseButton.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }

        if (!mouseInsideInfo(mouseX, mouseY, state)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        EssencePylonStatePayload state = EssencePylonClientState.snapshotFor(menu.containerId);
        if (infoOpen && mouseInsideInfo(mouseX, mouseY, state)) {
            if (infoCloseButton != null
                    && infoCloseButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderInfoPopup(GuiGraphics graphics, EssencePylonStatePayload state) {
        int x = infoPanelX();
        int y = topPos + MachineScreenLayout.SIDE_PANEL_TOP_OFFSET;

        new MachineInfoPanel(graphics, font, x, y, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT)
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
        if (!state.linked()) {
            return EssenceText.gui("pylon.status.unlinked");
        }
        if (!state.active()) {
            return EssenceText.gui("pylon.status.inactive_limit");
        }
        return EssenceText.gui("pylon.status.active");
    }

    private static int statusColor(EssencePylonStatePayload state) {
        if (!state.linked() || !state.active()) {
            return MachineScreenUi.WARN;
        }
        return MachineScreenUi.GOOD;
    }

    private static Component focusText(EssencePylonStatePayload state) {
        if (!state.focusInstalled()) {
            return EssenceText.term("none");
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

    private int infoPanelX() {
        return MachineScreenLayout.infoPanelX(leftPos, imageWidth, width);
    }

    private boolean mouseInsideInfo(double mouseX, double mouseY, EssencePylonStatePayload state) {
        if (!infoOpen || state == null) {
            return false;
        }
        int x = infoPanelX();
        int y = topPos + MachineScreenLayout.SIDE_PANEL_TOP_OFFSET;
        return MachineScreenLayout.contains(
                mouseX, mouseY, x, y, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT
        );
    }

    private void drawCentered(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, text, (imageWidth - font.width(text)) / 2, y, color, false);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
