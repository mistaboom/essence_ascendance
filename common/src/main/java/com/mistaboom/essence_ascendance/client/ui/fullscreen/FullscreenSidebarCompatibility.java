package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.platform.Platform;
import java.util.HashSet;
import java.util.Set;

/** The same optional inventory-sidebar policy applies to both fullscreen host lifecycles. */
final class FullscreenSidebarCompatibility {
    private static final Set<String> registered = new HashSet<>();
    private static boolean unavailable;

    private FullscreenSidebarCompatibility() { }

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
