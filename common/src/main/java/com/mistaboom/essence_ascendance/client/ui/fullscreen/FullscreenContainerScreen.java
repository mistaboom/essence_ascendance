package com.mistaboom.essence_ascendance.client.ui.fullscreen;

//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
//? if >=26.1.2
/*import net.minecraft.client.input.MouseButtonEvent;*/
//? if >=26.1.2
/*import net.minecraft.client.input.KeyEvent;*/
//? if >=26.1.2
/*import net.minecraft.client.input.CharacterEvent;*/

/** Menu lifecycle adapter. Menu authority, close policy and network actions stay in the host. */
public abstract class FullscreenContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected final FullscreenComposition fullscreen = new FullscreenComposition();

    protected FullscreenContainerScreen(M menu, Inventory inventory, Component title) {
        //? if >=26.1.2
        /*super(menu, inventory, title, 0, 0);*/
        //? if <26.1.2
        super(menu, inventory, title);
        //? if <26.1.2
        imageWidth = 0; imageHeight = 0;
        titleLabelX = 0; titleLabelY = 0; inventoryLabelX = 0; inventoryLabelY = 0;
    }

    protected abstract FullscreenComposition.Scene composeFullscreen();
    protected void beforeFullscreenFrame() { }
    //? if >=26.1.2
    /*protected void renderFullscreenTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) { }*/
    //? if <26.1.2
    protected void renderFullscreenTooltips(GuiGraphics graphics, int mouseX, int mouseY) { }
    protected final void refreshFullscreen() {
        // Direct list additions outside initialization must not become render, input or narration overlays.
        clearFocus(); clearWidgets();
        fullscreen.update(composeFullscreen());
    }

    @Override protected void init() {
        FullscreenSidebarCompatibility.register(getClass());
        super.init(); fullscreen.resized(); refreshFullscreen();
    }
    @Override public void removed() { fullscreen.clear(); super.removed(); }
    //? if >=26.1.2
    /*@Override public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {*/
    //? if <26.1.2
    @Override protected final void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        //? if >=26.1.2
        /*beforeFullscreenFrame(); refreshFullscreen();*/
        fullscreen.renderBase(graphics, font, mouseX, mouseY, partialTick);
    }
    //? if >=26.1.2
    /*@Override protected final void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) { }*/
    //? if <26.1.2
    @Override protected final void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }
    //? if >=26.1.2
    /*@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {*/
    //? if <26.1.2
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <26.1.2
        beforeFullscreenFrame();
        //? if <26.1.2
        refreshFullscreen();
        //? if >=26.1.2
        /*super.extractRenderState(graphics, mouseX, mouseY, partialTick);*/
        //? if <26.1.2
        super.render(graphics, mouseX, mouseY, partialTick);
        fullscreen.renderOverlays(graphics, font, mouseX, mouseY, partialTick);
        if (fullscreen.allowsTooltip(mouseX, mouseY)) renderFullscreenTooltips(graphics, mouseX, mouseY);
    }
    //? if >=26.1.2
    /*@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { refreshFullscreen(); return fullscreen.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick); }*/
    //? if <26.1.2
    @Override public boolean mouseClicked(double x, double y, int button) { refreshFullscreen(); return fullscreen.mouseClicked(x, y, button) || super.mouseClicked(x, y, button); }
    //? if >=26.1.2
    /*@Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { refreshFullscreen(); return fullscreen.mouseDragged(event.x(), event.y(), event.button(), dx, dy) || super.mouseDragged(event, dx, dy); }*/
    //? if <26.1.2
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { refreshFullscreen(); return fullscreen.mouseDragged(x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy); }
    //? if >=26.1.2
    /*@Override public boolean mouseReleased(MouseButtonEvent event) { refreshFullscreen(); return fullscreen.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event); }*/
    //? if <26.1.2
    @Override public boolean mouseReleased(double x, double y, int button) { refreshFullscreen(); return fullscreen.mouseReleased(x, y, button) || super.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        refreshFullscreen();
        return fullscreen.mouseScrolled(x, y, dx, dy) || super.mouseScrolled(x, y, dx, dy);
    }
    //? if >=26.1.2
    /*@Override public boolean keyPressed(KeyEvent event) { refreshFullscreen(); return fullscreen.keyPressed(event.key(), event.scancode(), event.modifiers()) || super.keyPressed(event); }*/
    //? if <26.1.2
    @Override public boolean keyPressed(int key, int scan, int modifiers) { refreshFullscreen(); return fullscreen.keyPressed(key, scan, modifiers) || super.keyPressed(key, scan, modifiers); }
    //? if >=26.1.2
    /*@Override public boolean charTyped(CharacterEvent event) { refreshFullscreen(); return fullscreen.charTyped(event.codepoint(), 0) || super.charTyped(event); }*/
    //? if <26.1.2
    @Override public boolean charTyped(char character, int modifiers) { refreshFullscreen(); return fullscreen.charTyped(character, modifiers) || super.charTyped(character, modifiers); }
}
