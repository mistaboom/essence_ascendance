package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Ordinary screen adapter; deliberately has no container/menu or transaction lifecycle. */
public abstract class FullscreenScreen extends Screen {
    protected final FullscreenComposition fullscreen = new FullscreenComposition();
    protected FullscreenScreen(Component title) { super(title); }
    protected abstract FullscreenComposition.Scene composeFullscreen();
    protected void beforeFullscreenFrame() { }
    protected void renderFullscreenTooltips(GuiGraphics graphics, int mouseX, int mouseY) { }
    protected final void refreshFullscreen() { fullscreen.update(composeFullscreen()); }

    @Override protected void init() { super.init(); fullscreen.resized(); refreshFullscreen(); }
    @Override public void removed() { fullscreen.clear(); super.removed(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        beforeFullscreenFrame(); refreshFullscreen();
        fullscreen.renderBase(graphics, font, mouseX, mouseY, partialTick);
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
