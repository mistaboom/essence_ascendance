package com.mistaboom.essence_ascendance.fabric.client;

import com.mistaboom.essence_ascendance.client.EssenceAscendanceClient;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleScreen;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.item.ItemProperties;

/*
 * Fabric client entrypoint.
 *
 * Fabric API's transitive access widener exposes vanilla ItemProperties
 * registration methods, so the common client definition can attach the same
 * item predicates used by vanilla bows to our custom BowItem.
 */
public final class EssenceAscendanceFabricClient
        implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EssenceAscendanceClient.init(
                ItemProperties::register
        );

        MenuScreens.register(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_MENU.get(),
                EssenceCrucibleScreen::new
        );
    }
}
