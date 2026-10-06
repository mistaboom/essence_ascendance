package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.platform.Platform;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** The same optional inventory-control policy applies to both fullscreen host lifecycles. */
public final class FullscreenSidebarCompatibility {
    // Stable, untranslated widget identities from the optional providers. This is
    // independent of their classes, loader event APIs, widget position and language.
    private static final Set<String> injectedInventoryControls = Set.of("gui.darkmodeeverywhere.dark_mode");
    private static final Set<String> registered = new HashSet<>();
    private static boolean unavailable;

    private FullscreenSidebarCompatibility() { }

    public static boolean suppressInjectedWidget(Class<?> screenType, Component message) {
        return (FullscreenContainerScreen.class.isAssignableFrom(screenType)
                || FullscreenScreen.class.isAssignableFrom(screenType))
                && message.getContents() instanceof TranslatableContents translation
                && injectedInventoryControls.contains(translation.getKey());
    }

    /** Called after native initialization/rebuild, including optional loader listeners. */
    public static void suppressInjectedWidgets(Screen screen, Consumer<GuiEventListener> remove) {
        if (!(screen instanceof FullscreenContainerScreen<?> || screen instanceof FullscreenScreen)) return;
        for (GuiEventListener child : List.copyOf(screen.children())) {
            if (child instanceof AbstractWidget widget && suppressInjectedWidget(screen.getClass(), widget.getMessage())) {
                if (screen.getFocused() == child) screen.setFocused(null);
                // Screen.removeWidget removes rendering, input and narration together.
                remove.accept(child);
            }
        }
    }

    static synchronized void register(Class<?> screenType) {
        if (unavailable || registered.contains(screenType.getName()) || !Platform.isModLoaded("ftblibrary")) return;
        try {
            // Public FTB Library API; string linkage keeps FTB entirely optional on both loaders.
            Class<?> api = Class.forName("dev.ftb.mods.ftblibrary.api.client.FTBLibraryClientApi");
            Object instance = api.getMethod("get").invoke(null);
            api.getMethod("addSidebarScreenBlacklist", String[].class)
                    .invoke(instance, (Object)new String[]{screenType.getName()});
            registered.add(screenType.getName());
        } catch (ReflectiveOperationException | LinkageError error) {
            unavailable = true;
            EssenceAscendance.LOGGER.warn("FTB Library fullscreen sidebar exclusion API unavailable; fullscreen inventory buttons may remain visible", error);
        }
    }
}
