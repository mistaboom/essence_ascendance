package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiFocusController;
import com.mistaboom.essence_ascendance.client.ui.UiOverlayStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Lifecycle-free fullscreen composition. A host declares a scene after reflow;
 * this object owns rendering order, controls, focus, capture and event consumption.
 * Stable element IDs preserve focus/capture across refreshes, while a changed page
 * key cancels interactions. No menu, player, transaction or screen is retained.
 */
public final class FullscreenComposition {
    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    }

    public interface Input {
        Input NONE = new Input() { };
        default boolean click(double x, double y, int button) { return false; }
        default boolean drag(double x, double y, int button, double dx, double dy) { return false; }
        default boolean release(double x, double y, int button) { return false; }
        default boolean scroll(double x, double y, double dx, double dy) { return false; }
        default boolean key(int key, int scan, int modifiers) { return false; }
        default boolean character(char character, int modifiers) { return false; }
        default void focused(boolean focused) { }
        /** Called when a page, overlay, resize or removed target invalidates capture. */
        default void cancel() { }
    }

    public record Control(String id, UiBounds bounds, Component label, boolean enabled,
                          boolean selected, FullscreenControls.Style style, int accent, Runnable action) {
        public Control {
            Objects.requireNonNull(id); Objects.requireNonNull(bounds); Objects.requireNonNull(label);
            Objects.requireNonNull(style); Objects.requireNonNull(action);
        }
    }

    public record Region(String id, UiBounds bounds, Renderer renderer, Input input, boolean focusable) {
        public Region {
            Objects.requireNonNull(id); Objects.requireNonNull(bounds);
            Objects.requireNonNull(renderer); Objects.requireNonNull(input);
        }
    }

    public record Overlay(UiOverlayStack.Layer layer, Renderer renderer, List<Control> controls, Input input) {
        public Overlay { controls = List.copyOf(controls); Objects.requireNonNull(input); }
    }

    public record Scene(Object pageKey, FullscreenLayout.Frame frame, int backgroundTop, int backgroundBottom,
                        List<Region> regions, List<Control> controls, List<Overlay> overlays, String primaryInput) {
        public Scene {
            Objects.requireNonNull(pageKey); Objects.requireNonNull(frame);
            regions = List.copyOf(regions); controls = List.copyOf(controls); overlays = List.copyOf(overlays);
            HashSet<String> ids = new HashSet<>();
            regions.forEach(region -> unique(ids, region.id()));
            controls.forEach(control -> unique(ids, control.id()));
            overlays.forEach(overlay -> overlay.controls().forEach(control -> unique(ids, control.id())));
            new UiOverlayStack(overlays.stream().map(Overlay::layer).toList());
        }
        private static void unique(HashSet<String> ids, String id) {
            if (id.isBlank() || !ids.add(id)) throw new IllegalArgumentException("Invalid or duplicate element: " + id);
        }
    }

    /** Explicit assembly shared by both Screen and AbstractContainerScreen hosts. */
    public static final class Builder {
        private final Object pageKey;
        private final FullscreenLayout.Frame frame;
        private int top = 0xFF151518, bottom = 0xFF29292D;
        private final List<Region> regions = new ArrayList<>();
        private final List<Control> controls = new ArrayList<>();
        private final List<Overlay> overlays = new ArrayList<>();
        private String primary;

        public Builder(Object pageKey, FullscreenLayout.Frame frame) { this.pageKey = pageKey; this.frame = frame; }
        public Builder background(int top, int bottom) { this.top = top; this.bottom = bottom; return this; }
        public Builder region(Region region) { regions.add(region); return this; }
        public Builder panel(String id, UiBounds bounds, int surface, int border) {
            return region(new Region(id, bounds, (graphics, x, y, tick) ->
                    FullscreenControls.panel(graphics, bounds, surface, border), Input.NONE, false));
        }
        public Builder control(Control control) { controls.add(control); return this; }
        public Builder overlay(Overlay overlay) { overlays.add(overlay); return this; }
        public Builder primaryInput(String id) { primary = id; return this; }

        public <M> Builder modes(List<Mode<M>> modes, M selected, Consumer<M> choose) {
            List<UiBounds> slots = FullscreenLayout.segments(frame.modes(), modes.size(), 0);
            for (int index = 0; index < modes.size(); index++) {
                Mode<M> mode = modes.get(index);
                control(new Control("mode/" + mode.id(), slots.get(index), mode.label(), mode.available(),
                        Objects.equals(selected, mode.id()), FullscreenControls.Style.MODE, mode.accent(),
                        () -> choose.accept(mode.id())));
            }
            return this;
        }

        /** Secondary navigation is optional, and paging is independent of the selected section. */
        public <M,S> Builder sections(FullscreenNavigation<M,S> navigation, List<Section<S>> sections,
                                     FullscreenLayout.Tabs tabs, Consumer<S> choose) {
            int start = navigation.sectionWindow();
            control(new Control("sections/previous", tabs.leftArrow(), Component.literal("‹"), start > 0,
                    false, FullscreenControls.Style.ARROW, AscendanceUiPalette.INTERACTIVE, () -> navigation.sectionWindow(start - 1)));
            control(new Control("sections/next", tabs.rightArrow(), Component.literal("›"),
                    start + tabs.visibleCount() < sections.size(), false, FullscreenControls.Style.ARROW, AscendanceUiPalette.INTERACTIVE,
                    () -> navigation.sectionWindow(start + 1)));
            for (int index = 0; index < tabs.visibleCount() && start + index < sections.size(); index++) {
                Section<S> section = sections.get(start + index);
                control(new Control("section/" + section.id(), tabs.items().get(index), section.label(), true,
                        Objects.equals(navigation.section(), section.id()), FullscreenControls.Style.TAB,
                        section.accent(), () -> choose.accept(section.id())));
            }
            return this;
        }

        public Scene build() { return new Scene(pageKey, frame, top, bottom, regions, controls, overlays, primary); }
    }

    public record Mode<M>(M id, Component label, boolean available, int accent) { }
    public record Section<S>(S id, Component label, int accent) { }

    private final UiFocusController<String> focus = new UiFocusController<>();
    private Scene scene;
    private UiOverlayStack overlays = new UiOverlayStack(List.of());
    private String focusScope;
    private final Map<String, String> savedFocus = new HashMap<>();
    private String captured;
    private boolean capturedOverlay;
    private int capturedButton = -1;
    private Input capturedInput;

    public Scene scene() { return scene; }
    public String focusedId() { return focus.focused().orElse(null); }

    public void update(Scene next) {
        String previousFocus = focusedId();
        Input previousInput = input(previousFocus);
        boolean pageChanged = scene != null && !Objects.equals(scene.pageKey(), next.pageKey());
        UiOverlayStack nextStack = new UiOverlayStack(next.overlays().stream().map(Overlay::layer).toList());
        String nextScope = nextStack.keyboardOwner().map(UiOverlayStack.Layer::id).orElse(null);
        boolean scopeChanged = !Objects.equals(focusScope, nextScope);
        if (pageChanged || scopeChanged) cancelCapture();
        if (pageChanged) savedFocus.clear();
        else if (scopeChanged) {
            if (previousFocus == null) savedFocus.remove(focusScope);
            else savedFocus.put(focusScope, previousFocus);
        }
        scene = next;
        overlays = nextStack;
        focusScope = nextScope;
        List<String> order = new ArrayList<>();
        activeControls().stream().filter(Control::enabled).map(Control::id).forEach(order::add);
        if (focusScope == null) scene.regions().stream().filter(Region::focusable).map(Region::id).forEach(order::add);
        focus.setOrder(order);
        if (pageChanged) focus.clear();
        if (scopeChanged) {
            focus.focus(savedFocus.get(nextScope));
        }
        if (nextScope != null && focusedId() == null) focus.move(1);
        if (captured != null && (!captureTargetExists() || captureBlockedByModal())) cancelCapture();
        notifyFocusChanged(previousFocus, previousInput);
    }

    public void resized() { cancelCapture(); }
    public void clear() {
        cancelCapture();
        Input current = input(focusedId());
        if (current != null) current.focused(false);
        focus.clear(); scene = null; focusScope = null; savedFocus.clear();
        overlays = new UiOverlayStack(List.of());
    }

    public void renderBase(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        UiBounds screen = scene.frame().screen();
        graphics.fillGradient(screen.x(), screen.y(), screen.right(), screen.bottom(), scene.backgroundTop(), scene.backgroundBottom());
        // Modal layers never leak hover feedback from the covered content.
        int x = overlays.pointerOwner(mouseX, mouseY).isPresent() ? Integer.MIN_VALUE : mouseX;
        int y = x == Integer.MIN_VALUE ? Integer.MIN_VALUE : mouseY;
        for (Region region : scene.regions()) region.renderer().render(graphics, x, y, partialTick);
        for (Control control : scene.controls()) renderControl(graphics, font, control, x, y);
    }

    public void renderOverlays(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        String pointerOwner = overlays.pointerOwner(mouseX, mouseY).map(UiOverlayStack.Layer::id).orElse(null);
        for (UiOverlayStack.Layer layer : overlays.renderingOrder()) {
            Overlay overlay = overlay(layer.id());
            int x = pointerOwner != null && !pointerOwner.equals(layer.id()) ? Integer.MIN_VALUE : mouseX;
            int y = x == Integer.MIN_VALUE ? Integer.MIN_VALUE : mouseY;
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, layer.zOrder());
            try {
                overlay.renderer().render(graphics, x, y, partialTick);
                for (Control control : overlay.controls()) renderControl(graphics, font, control, x, y);
                graphics.flush();
            } finally { graphics.pose().popPose(); }
        }
    }

    public boolean allowsTooltip(double x, double y) { return overlays.allowsTooltip(x, y); }

    public boolean mouseClicked(double x, double y, int button) {
        cancelCapture();
        Scene clickedScene = scene;
        String clickedScope = focusScope;
        var owner = overlays.pointerOwner(x, y);
        if (owner.isPresent()) {
            Overlay overlay = overlay(owner.get().id());
            if (!clickControls(overlay.controls(), x, y, button)
                    && overlay.input().click(x, y, button)) {
                capture(clickedScene, clickedScope, overlay.layer().id(), true, button, overlay.input());
            }
            return true;
        }
        if (clickControls(scene.controls(), x, y, button)) return true;
        for (int index = scene.regions().size() - 1; index >= 0; index--) {
            Region region = scene.regions().get(index);
            if (region.bounds().contains(x, y) && region.input().click(x, y, button)) {
                if (sameInteractionScope(clickedScene, clickedScope)) {
                    if (region.focusable()) setFocus(region.id());
                }
                capture(clickedScene, clickedScope, region.id(), false, button, region.input());
                return true;
            }
        }
        setFocus(null);
        return false;
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (capturedInput != null && capturedButton == button) {
            capturedInput.drag(x, y, button, dx, dy); return true;
        }
        return overlays.pointerOwner(x, y).isPresent();
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (capturedInput == null || capturedButton != button) return overlays.pointerOwner(x, y).isPresent();
        Input released = capturedInput;
        resetCapture();
        released.release(x, y, button);
        return true;
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        var owner = overlays.pointerOwner(x, y);
        if (owner.isPresent()) { overlay(owner.get().id()).input().scroll(x, y, dx, dy); return true; }
        for (int index = scene.regions().size() - 1; index >= 0; index--) {
            Region region = scene.regions().get(index);
            if (region.bounds().contains(x, y) && region.input().scroll(x, y, dx, dy)) return true;
        }
        return false;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 258) { moveFocus((modifiers & 1) != 0 ? -1 : 1); return true; }
        Control control = activeControls().stream().filter(c -> c.id().equals(focusedId())).findFirst().orElse(null);
        if (control != null && (key == 257 || key == 335 || key == 32)) {
            if (control.enabled()) control.action().run();
            return true;
        }
        if (focusScope != null) { overlay(focusScope).input().key(key, scan, modifiers); return true; }
        Input input = input(focusedId());
        if (input != null && input.key(key, scan, modifiers)) return true;
        Input primary = input(scene.primaryInput());
        return primary != null && primary != input && primary.key(key, scan, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        if (focusScope != null) { overlay(focusScope).input().character(character, modifiers); return true; }
        Input input = input(focusedId());
        return input != null && input.character(character, modifiers);
    }

    private void renderControl(GuiGraphics graphics, Font font, Control control, int x, int y) {
        FullscreenControls.button(graphics, font, control.bounds(), control.label(), control.enabled(), control.selected(),
                control.bounds().contains(x, y), activeControls().contains(control) && focus.isFocused(control.id()),
                control.style(), control.accent());
    }
    private boolean clickControls(List<Control> controls, double x, double y, int button) {
        for (int index = controls.size() - 1; index >= 0; index--) {
            Control control = controls.get(index);
            if (!control.bounds().contains(x, y)) continue;
            if (control.enabled()) {
                setFocus(control.id());
                if (button == 0) control.action().run();
            }
            return true;
        }
        return false;
    }
    private void cancelCapture() {
        Input cancelled = capturedInput;
        resetCapture();
        if (cancelled != null) cancelled.cancel();
    }
    private void capture(Scene startedScene, String startedScope, String id, boolean overlay, int button, Input input) {
        captured = id; capturedOverlay = overlay; capturedButton = button; capturedInput = input;
        if (!sameInteractionScope(startedScene, startedScope) || !captureTargetExists() || captureBlockedByModal()) cancelCapture();
    }
    private boolean sameInteractionScope(Scene startedScene, String startedScope) {
        return scene != null && Objects.equals(startedScene.pageKey(), scene.pageKey())
                && Objects.equals(startedScope, focusScope);
    }
    private void resetCapture() {
        captured = null; capturedOverlay = false; capturedInput = null; capturedButton = -1;
    }
    private boolean captureTargetExists() {
        return capturedOverlay
                ? scene.overlays().stream().anyMatch(overlay -> overlay.layer().id().equals(captured))
                : input(captured) != null;
    }
    /** A newly introduced modal invalidates capture even when it does not own the keyboard. */
    private boolean captureBlockedByModal() {
        List<UiOverlayStack.Layer> layers = overlays.renderingOrder();
        for (int index = layers.size() - 1; index >= 0; index--) {
            UiOverlayStack.Layer layer = layers.get(index);
            if (capturedOverlay && layer.id().equals(captured)) return false;
            if (layer.pointerPolicy() == UiOverlayStack.PointerPolicy.MODAL) return true;
        }
        return false;
    }
    private List<Control> activeControls() {
        if (focusScope != null) return overlay(focusScope).controls();
        List<Control> result = new ArrayList<>(scene.controls());
        for (UiOverlayStack.Layer layer : overlays.renderingOrder()) result.addAll(overlay(layer.id()).controls());
        return result;
    }
    private Overlay overlay(String id) { return scene.overlays().stream().filter(o -> o.layer().id().equals(id)).findFirst().orElseThrow(); }
    private Input input(String id) {
        if (scene == null || id == null) return null;
        return scene.regions().stream().filter(region -> region.id().equals(id)).map(Region::input).findFirst().orElse(null);
    }
    private void moveFocus(int direction) {
        String previousFocus = focusedId();
        Input previous = input(previousFocus);
        focus.move(direction);
        notifyFocusChanged(previousFocus, previous);
    }
    private void setFocus(String id) {
        String previousFocus = focusedId();
        Input previous = input(previousFocus);
        focus.focus(id);
        notifyFocusChanged(previousFocus, previous);
    }
    private void notifyFocusChanged(String previousFocus, Input previous) {
        Input current = input(focusedId());
        if (Objects.equals(previousFocus, focusedId()) && previous == current) return;
        if (previous != null) previous.focused(false);
        if (current != null) current.focused(true);
    }
}
