package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleScreen;
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
 * Optional JEI integration for Crucible side panels.
 *
 * This class is only discovered and loaded by JEI when JEI is present. The
 * project depends on JEI's common API at compile time only, so Essence
 * Ascendance continues to run normally without JEI installed.
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
        registration.addGuiContainerHandler(
                EssenceCrucibleScreen.class,
                new IGuiContainerHandler<EssenceCrucibleScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(
                            EssenceCrucibleScreen screen
                    ) {
                        return screen.extraGuiAreas();
                    }
                }
        );
    }
}
