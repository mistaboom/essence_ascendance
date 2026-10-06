package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenContainerScreen;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScreen;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenSidebarCompatibility;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Inventory injections follow the fullscreen host contract, without an optional mod dependency. */
final class FullscreenInventoryOverlayTest {
    private static int checks;

    static int run() {
        checks = 0;
        Component injected = Component.translatable("gui.darkmodeeverywhere.dark_mode");
        for (Class<?> host : new Class<?>[] { AscendanceNexusScreen.class, AscendanceArchiveScreen.class,
                ContainerFixture.class, ScreenFixture.class }) {
            check(FullscreenSidebarCompatibility.suppressInjectedWidget(host, injected),
                    "Both shipping screens and future fullscreen subclasses share the inventory-widget policy");
        }
        for (Class<?> host : new Class<?>[] { Screen.class, TitleScreen.class, MachineContainerScreen.class }) {
            check(!FullscreenSidebarCompatibility.suppressInjectedWidget(host, injected),
                    "Other screens retain optional inventory/title controls");
        }
        check(!FullscreenSidebarCompatibility.suppressInjectedWidget(ScreenFixture.class,
                        Component.literal("Dark Mode")),
                "Displayed text is not used as an optional widget identity");
        check(!FullscreenSidebarCompatibility.suppressInjectedWidget(ScreenFixture.class,
                        Component.translatable("gui.darkmodeeverywhere.light_mode")),
                "An unrelated translated control is not suppressed");
        check(!FullscreenSidebarCompatibility.suppressInjectedWidget(ScreenFixture.class,
                        Component.translatable("gui.essence_ascendance.dark_mode")),
                "Host controls are preserved even when their labels have a similar meaning");

        var screen = new ScreenFixture();
        Button darkMode = screen.add(injected);
        Button hostButton = screen.add(Component.translatable("gui.essence_ascendance.done"));
        screen.setFocused(darkMode);
        screen.filter();
        check(screen.children().equals(java.util.List.of(hostButton)),
                "The injected control is removed while host controls remain in native input routing");
        check(screen.getFocused() == null && !darkMode.isFocused(),
                "Removing the injected control also clears its native keyboard focus");
        screen.setFocused(hostButton);
        screen.filter();
        check(screen.getFocused() == hostButton && screen.children().size() == 1,
                "Repeated initialization filtering preserves retained controls and their focus");
        screen.add(injected);
        screen.add(injected);
        screen.filter();
        check(screen.children().equals(java.util.List.of(hostButton)),
                "Resize/rebuild filtering removes every matching injection without modifying a live iteration");
        return checks;
    }

    private static final class ScreenFixture extends FullscreenScreen {
        ScreenFixture() { super(Component.empty()); }
        @Override protected FullscreenComposition.Scene composeFullscreen() { throw new UnsupportedOperationException(); }
        Button add(Component label) { return addRenderableWidget(Button.builder(label, ignored -> { }).build()); }
        void filter() { FullscreenSidebarCompatibility.suppressInjectedWidgets(this, this::removeWidget); }
    }

    private static final class ContainerFixture extends FullscreenContainerScreen<AbstractContainerMenu> {
        ContainerFixture(AbstractContainerMenu menu, Inventory inventory) { super(menu, inventory, Component.empty()); }
        @Override protected FullscreenComposition.Scene composeFullscreen() { throw new UnsupportedOperationException(); }
    }

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
