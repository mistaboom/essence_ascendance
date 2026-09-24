package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Menu lifecycle adapter. Menu authority, close policy and network actions stay in the host. */
public abstract class FullscreenContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected final FullscreenComposition fullscreen = new FullscreenComposition();

    protected FullscreenContainerScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 0; imageHeight = 0;
        titleLabelX = 0; titleLabelY = 0; inventoryLabelX = 0; inventoryLabelY = 0;
    }

    protected abstract FullscreenComposition.Scene composeFullscreen();
    protected void beforeFullscreenFrame() { }
    protected void renderFullscreenTooltips(GuiGraphics graphics, int mouseX, int mouseY) { }
    protected final void refreshFullscreen() { fullscreen.update(composeFullscreen()); }

    @Override protected void init() { super.init(); fullscreen.resized(); refreshFullscreen(); }
    @Override public void removed() { fullscreen.clear(); super.removed(); }
    @Override protected final void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        fullscreen.renderBase(graphics, font, mouseX, mouseY, partialTick);
    }
    @Override protected final void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        beforeFullscreenFrame();
        refreshFullscreen();
        super.render(graphics, mouseX, mouseY, partialTick);
        fullscreen.renderOverlays(graphics, font, mouseX, mouseY, partialTick);
        if (fullscreen.allowsTooltip(mouseX, mouseY)) renderFullscreenTooltips(graphics, mouseX, mouseY);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        refreshFullscreen();
        return fullscreen.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        refreshFullscreen();
        return fullscreen.mouseDragged(x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        refreshFullscreen();
        return fullscreen.mouseReleased(x, y, button) || super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        refreshFullscreen();
        return fullscreen.mouseScrolled(x, y, dx, dy) || super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        refreshFullscreen();
        return fullscreen.keyPressed(key, scan, modifiers) || super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        refreshFullscreen();
        return fullscreen.charTyped(character, modifiers) || super.charTyped(character, modifiers);
    }
}
