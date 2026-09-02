package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.AscendanceNexusScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Optional JEI GUI integration.
 *
 * Crucible/Pylon/Infuser popups intentionally render in front of JEI rather
 * than advertising extra GUI areas that make JEI move. The Nexus remains the
 * one exception because it owns the full logical screen.
 */
@JeiPlugin
@Environment(EnvType.CLIENT)
public final class EssenceAscendanceJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(
                    EssenceAscendance.MOD_ID,
                    "gui_layout"
            );

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        /*
         * The Nexus consumes the full logical screen. Mark that complete area
         * as GUI-owned so JEI does not place its ingredient list over the
         * progression tracks while remaining an optional compile-time bridge.
         */
        registration.addGuiContainerHandler(
                AscendanceNexusScreen.class,
                new IGuiContainerHandler<AscendanceNexusScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(
                            AscendanceNexusScreen screen
                    ) {
                        return List.of(
                                new Rect2i(
                                        0,
                                        0,
                                        screen.width,
                                        screen.height
                                )
                        );
                    }
                }
        );
    }
}
