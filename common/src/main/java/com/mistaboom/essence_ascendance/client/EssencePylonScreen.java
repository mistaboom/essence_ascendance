package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.EssencePylonStatePayload;
import com.mistaboom.essence_ascendance.pylon.EssencePylonMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class EssencePylonScreen
        extends AbstractContainerScreen<EssencePylonMenu> {

    private static final int PANEL = 0xFF20242B;
    private static final int PANEL_INNER = 0xFF2D333D;
    private static final int BORDER = 0xFF8A70B5;
    private static final int TEXT = 0xFFE9E9EF;
    private static final int MUTED = 0xFFAEB4C0;
    private static final int GOOD = 0xFF86D98C;
    private static final int WARN = 0xFFE4C36A;

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
    }

    @Override
    public void removed() {
        super.removed();
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

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        outline(graphics, x, y, imageWidth, imageHeight, BORDER);

        graphics.fill(x + 105, y + 31, x + 125, y + 51, PANEL_INNER);
        outline(graphics, x + 105, y + 31, 20, 20, BORDER);

        graphics.fill(x + 10, y + 58, x + 220, y + 158, PANEL_INNER);
        outline(graphics, x + 10, y + 58, 210, 100, BORDER);

        graphics.fill(x + 32, y + 174, x + 198, y + 254, PANEL_INNER);
        outline(graphics, x + 32, y + 174, 166, 80, 0xFF535B68);
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        EssencePylonStatePayload state =
                EssencePylonClientState.snapshotFor(menu.containerId);

        drawCentered(graphics, "ESSENCE PYLON", 7, TEXT);
        drawCentered(graphics, "Focus", 20, MUTED);

        if (state == null) {
            drawCentered(graphics, "Synchronizing...", 67, MUTED);
            graphics.drawString(font, playerInventoryTitle, 34, 163, MUTED, false);
            return;
        }

        int statusColor = state.active() ? GOOD : WARN;
        String status;
        if (!state.linked()) {
            status = "UNLINKED";
        } else if (!state.active()) {
            status = "INACTIVE / LIMIT";
        } else {
            status = "ACTIVE";
        }

        graphics.drawString(font, "Status", 16, 64, MUTED, false);
        drawRightAligned(graphics, status, 214, 64, statusColor);

        graphics.drawString(font, "Focus", 16, 76, MUTED, false);
        drawRightAligned(graphics, state.focusName(), 214, 76, TEXT);

        graphics.drawString(font, "Transfer", 16, 88, MUTED, false);
        drawRightAligned(graphics, "+" + format(state.transferRatePerSecondBonus()) + "/sec", 214, 88, TEXT);

        graphics.drawString(font, "Range", 16, 100, MUTED, false);
        drawRightAligned(graphics, "+" + decimal(state.transferRangeBonus()) + " blocks", 214, 100, TEXT);

        graphics.drawString(font, "Reservoir", 16, 112, MUTED, false);
        drawRightAligned(graphics, "+" + format(state.reservoirCapacityBonus()) + " / family", 214, 112, TEXT);

        graphics.drawString(font, "Dissolution", 16, 124, MUTED, false);
        drawRightAligned(
                graphics,
                "+" + decimal(state.dissolutionSpeedBonus() * 100.0D) + "% speed",
                214,
                124,
                TEXT
        );

        graphics.drawString(font, "Batch items", 16, 136, MUTED, false);
        drawRightAligned(
                graphics,
                "+" + state.simultaneousItemProcessesBonus(),
                214,
                136,
                TEXT
        );

        if (state.linked()) {
            BlockPos linked = BlockPos.of(state.linkedCruciblePos());
            String linkedText = linked.getX() + ", " + linked.getY() + ", " + linked.getZ();
            graphics.drawString(font, "Crucible", 16, 148, MUTED, false);
            drawRightAligned(graphics, linkedText, 214, 148, TEXT);
        }

        graphics.drawString(font, playerInventoryTitle, 34, 163, MUTED, false);
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
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void drawCentered(GuiGraphics graphics, String text, int y, int color) {
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
        graphics.drawString(font, text, right - font.width(text), y, color, false);
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

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
