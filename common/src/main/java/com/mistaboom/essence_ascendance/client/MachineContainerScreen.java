package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiOverlayStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

/**
 * Shared adapter between Minecraft's container lifecycle and the Ascendance UI
 * foundation. Machine subclasses declare widgets, content and bindings; this
 * host owns reflow, overlay ordering, event capture, scrolling and tooltips.
 */
public abstract class MachineContainerScreen<M extends AbstractContainerMenu>
        extends AbstractContainerScreen<M> {

    public static final int HEADER_BUTTON_SIZE = 15;
    public static final int OVERLAY_CLOSE_SIZE = 12;
    public static final int FOREGROUND_OVERLAY_Z = 300;

    private final MachineScreenLayout.Spec layoutSpec;
    private final Map<String, Integer> overlayScrollOffsets = new HashMap<>();

    protected MachineContainerScreen(
            M menu,
            Inventory playerInventory,
            Component title,
            MachineScreenLayout.Spec layoutSpec
    ) {
        super(menu, playerInventory, title);
        this.layoutSpec = layoutSpec;
        imageWidth = layoutSpec.imageWidth();
        imageHeight = layoutSpec.imageHeight();
        inventoryLabelX = layoutSpec.inventoryLabelX();
        inventoryLabelY = layoutSpec.inventoryLabelY();
    }

    @Override
    protected final void init() {
        super.init();
        initMachineWidgets();
        synchronizeWidgetFocus();
    }

    protected abstract void initMachineWidgets();

    /** Refresh visibility, labels and coordinates from current menu/client state. */
    protected void updateMachineWidgets() {
    }

    /** Active foreground layers. Returning an empty list leaves vanilla input untouched. */
    protected List<MachineOverlay> machineOverlays() {
        return List.of();
    }

    protected final MachineScreenLayout.Composition machineComposition() {
        return MachineScreenLayout.compose(layoutSpec, leftPos, topPos);
    }

    protected final UiBounds mainPanelBounds() {
        return machineComposition().mainPanel();
    }

    protected final MachineScreenLayout.SidePanel sidePanel(
            int panelWidth,
            int panelHeight,
            MachineScreenLayout.SidePreference preference
    ) {
        return MachineScreenLayout.sidePanel(
                mainPanelBounds(),
                width,
                height,
                panelWidth,
                panelHeight,
                preference
        );
    }

    protected final Button addMachineButton(
            Component label,
            Button.OnPress action,
            UiBounds bounds
    ) {
        return addRenderableWidget(Button.builder(label, action)
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .build());
    }

    protected final Button addHeaderButton(
            Component label,
            boolean right,
            Button.OnPress action
    ) {
        int x = right
                ? leftPos + imageWidth - HEADER_BUTTON_SIZE - 5
                : leftPos + 5;
        return addMachineButton(
                label,
                action,
                new UiBounds(x, topPos + 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE)
        );
    }

    protected final Button addOverlayCloseButton(Button.OnPress action, UiBounds panelBounds) {
        return addMachineButton(
                Component.literal("X"),
                action,
                overlayCloseBounds(panelBounds)
        );
    }

    protected final UiBounds overlayCloseBounds(UiBounds panelBounds) {
        return new UiBounds(
                panelBounds.right() - OVERLAY_CLOSE_SIZE - 6,
                panelBounds.y() + 4,
                OVERLAY_CLOSE_SIZE,
                OVERLAY_CLOSE_SIZE
        );
    }

    protected final void place(AbstractWidget widget, UiBounds bounds) {
        widget.setRectangle(bounds.width(), bounds.height(), bounds.x(), bounds.y());
    }

    protected final MachineOverlay infoOverlay(
            String id,
            UiBounds bounds,
            MachineInfoPanel content,
            List<AbstractWidget> controls
    ) {
        MachineInfoPanel.Layout layout = content.layout(font, bounds.width());
        int maximumScroll = layout.viewport(bounds.height(), 0).maximumOffset();
        return new MachineOverlay(
                id,
                bounds,
                FOREGROUND_OVERLAY_Z,
                UiOverlayStack.PointerPolicy.CAPTURE_BOUNDS,
                UiOverlayStack.TooltipPolicy.SUPPRESS_BOUNDS,
                maximumScroll > 0,
                maximumScroll,
                10,
                controls,
                (graphics, mouseX, mouseY, partialTick, scrollOffset) ->
                        content.render(graphics, font, bounds, layout, scrollOffset),
                (mouseX, mouseY, scrollOffset) ->
                        layout.styleAt(font, bounds, scrollOffset, mouseX, mouseY)
        );
    }

    protected final MachineOverlay panelOverlay(
            String id,
            UiBounds bounds,
            List<AbstractWidget> controls,
            MachineOverlay.Renderer renderer
    ) {
        return new MachineOverlay(
                id,
                bounds,
                FOREGROUND_OVERLAY_Z,
                UiOverlayStack.PointerPolicy.CAPTURE_BOUNDS,
                UiOverlayStack.TooltipPolicy.SUPPRESS_BOUNDS,
                false,
                0,
                10,
                controls,
                renderer,
                null
        );
    }

    @Override
    public final void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        updateMachineWidgets();
        synchronizeWidgetFocus();
        super.render(graphics, mouseX, mouseY, partialTick);

        List<MachineOverlay> overlays = currentOverlays();
        for (MachineOverlay overlay : overlays) {
            int scrollOffset = scrollOffset(overlay);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, overlay.zOrder());
            overlay.renderer().render(graphics, mouseX, mouseY, partialTick, scrollOffset);
            for (AbstractWidget control : overlay.controls()) {
                if (control.visible) {
                    control.render(graphics, mouseX, mouseY, partialTick);
                }
            }
            graphics.pose().popPose();
            var hoveredStyle = overlay.styleAt(mouseX, mouseY, scrollOffset);
            if (hoveredStyle != null) {
                graphics.renderComponentHoverEffect(font, hoveredStyle, mouseX, mouseY);
            }
        }

        UiOverlayStack stack = overlayStack(overlays);
        if (stack.allowsTooltip(mouseX, mouseY)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        // Vanilla derives hoveredSlot (including drop/hotbar/offhand keys) through this hook.
        return pointerOwner(currentOverlays(), mouseX, mouseY) == null
                && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateMachineWidgets();
        List<MachineOverlay> overlays = currentOverlays();
        MachineOverlay owner = pointerOwner(overlays, mouseX, mouseY);
        if (owner == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (int index = owner.controls().size() - 1; index >= 0; index--) {
            AbstractWidget control = owner.controls().get(index);
            if (control.visible && control.mouseClicked(mouseX, mouseY, button)) {
                setFocused(control);
                return true;
            }
        }
        var style = owner.styleAt(mouseX, mouseY, scrollOffset(owner));
        if (style != null && handleComponentClicked(style)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        List<MachineOverlay> overlays = currentOverlays();
        MachineOverlay owner = pointerOwner(overlays, mouseX, mouseY);
        if (owner == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        for (AbstractWidget control : owner.controls()) {
            if (control.visible) {
                control.mouseReleased(mouseX, mouseY, button);
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        List<MachineOverlay> overlays = currentOverlays();
        MachineOverlay owner = pointerOwner(overlays, mouseX, mouseY);
        if (owner == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        for (AbstractWidget control : owner.controls()) {
            if (control.visible && control.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        List<MachineOverlay> overlays = currentOverlays();
        MachineOverlay owner = pointerOwner(overlays, mouseX, mouseY);
        if (owner == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (scrollY != 0.0D && owner.maximumScroll() > 0) {
            int direction = scrollY > 0.0D ? -1 : 1;
            setScrollOffset(owner, scrollOffset(owner) + direction * owner.scrollStep());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        List<MachineOverlay> overlays = currentOverlays();
        MachineOverlay owner = keyboardOwner(overlays);
        if (owner != null) {
            if (routeScrollKey(owner, keyCode)) {
                return true;
            }
            if (keyCode == GLFW_KEY_TAB && cycleOverlayFocus(owner, hasShiftDown() ? -1 : 1)) {
                return true;
            }
            GuiEventListener focused = getFocused();
            if (focused != null && owner.controls().contains(focused)
                    && focused.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (owner.pointerPolicy() == UiOverlayStack.PointerPolicy.MODAL) {
                if (keyCode == GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        List<MachineOverlay> overlays = currentOverlays();
        MachineOverlay owner = keyboardOwner(overlays);
        if (owner != null) {
            GuiEventListener focused = getFocused();
            if (focused != null && owner.controls().contains(focused)
                    && focused.keyReleased(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (owner.pointerPolicy() == UiOverlayStack.PointerPolicy.MODAL) {
                return true;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        List<MachineOverlay> overlays = currentOverlays();
        MachineOverlay owner = keyboardOwner(overlays);
        if (owner != null) {
            GuiEventListener focused = getFocused();
            if (focused != null && owner.controls().contains(focused)
                    && focused.charTyped(character, modifiers)) {
                return true;
            }
            if (owner.pointerPolicy() == UiOverlayStack.PointerPolicy.MODAL) {
                return true;
            }
        }
        return super.charTyped(character, modifiers);
    }

    /** Recipe viewers can consume the same dynamic bounds as the screen host. */
    public final List<net.minecraft.client.renderer.Rect2i> overlayInteractionAreas() {
        return currentOverlays().stream()
                .map(MachineOverlay::bounds)
                .map(UiBounds::toMinecraftRect)
                .toList();
    }

    protected final void drawCentered(
            GuiGraphics graphics,
            Component text,
            int relativeY,
            int color
    ) {
        graphics.drawString(
                font,
                text,
                (imageWidth - font.width(text)) / 2,
                relativeY,
                color,
                false
        );
    }

    private List<MachineOverlay> currentOverlays() {
        List<MachineOverlay> overlays = new ArrayList<>(machineOverlays());
        overlays.sort(java.util.Comparator.comparingInt(MachineOverlay::zOrder));
        for (MachineOverlay overlay : overlays) {
            setScrollOffset(overlay, scrollOffset(overlay));
        }
        return List.copyOf(overlays);
    }

    private UiOverlayStack overlayStack(List<MachineOverlay> overlays) {
        return new UiOverlayStack(overlays.stream().map(MachineOverlay::layer).toList());
    }

    private MachineOverlay pointerOwner(
            List<MachineOverlay> overlays,
            double mouseX,
            double mouseY
    ) {
        return overlayStack(overlays).pointerOwner(mouseX, mouseY)
                .flatMap(layer -> overlays.stream()
                        .filter(overlay -> overlay.id().equals(layer.id()))
                        .findFirst())
                .orElse(null);
    }

    private MachineOverlay keyboardOwner(List<MachineOverlay> overlays) {
        return overlayStack(overlays).keyboardOwner()
                .flatMap(layer -> overlays.stream()
                        .filter(overlay -> overlay.id().equals(layer.id()))
                        .findFirst())
                .orElse(null);
    }

    private int scrollOffset(MachineOverlay overlay) {
        return Math.clamp(
                overlayScrollOffsets.getOrDefault(overlay.id(), 0),
                0,
                overlay.maximumScroll()
        );
    }

    private void setScrollOffset(MachineOverlay overlay, int requested) {
        overlayScrollOffsets.put(
                overlay.id(),
                Math.clamp(requested, 0, overlay.maximumScroll())
        );
    }

    private boolean routeScrollKey(MachineOverlay overlay, int keyCode) {
        int current = scrollOffset(overlay);
        int requested = switch (keyCode) {
            case GLFW_KEY_UP -> current - overlay.scrollStep();
            case GLFW_KEY_DOWN -> current + overlay.scrollStep();
            case GLFW_KEY_PAGE_UP -> current - Math.max(overlay.scrollStep(), overlay.bounds().height() - 10);
            case GLFW_KEY_PAGE_DOWN -> current + Math.max(overlay.scrollStep(), overlay.bounds().height() - 10);
            case GLFW_KEY_HOME -> 0;
            case GLFW_KEY_END -> overlay.maximumScroll();
            default -> Integer.MIN_VALUE;
        };
        if (requested == Integer.MIN_VALUE) {
            return false;
        }
        setScrollOffset(overlay, requested);
        return true;
    }

    private boolean cycleOverlayFocus(MachineOverlay overlay, int direction) {
        List<AbstractWidget> candidates = overlay.controls().stream()
                .filter(widget -> widget.visible && widget.active)
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        int current = candidates.indexOf(getFocused());
        int next = current < 0
                ? (direction < 0 ? candidates.size() - 1 : 0)
                : Math.floorMod(current + Integer.signum(direction), candidates.size());
        setFocused(candidates.get(next));
        return true;
    }

    private void synchronizeWidgetFocus() {
        GuiEventListener focused = getFocused();
        if (focused instanceof AbstractWidget widget && !widget.visible) {
            setFocused(null);
        }
    }
}
