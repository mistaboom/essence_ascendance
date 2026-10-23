package com.mistaboom.essence_ascendance.fabric.client;

import com.mistaboom.essence_ascendance.client.AscendanceNexusRenderer;
import com.mistaboom.essence_ascendance.client.AscendanceNexusScreen;
import com.mistaboom.essence_ascendance.client.EssenceAscendanceClient;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleRenderer;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleScreen;
import com.mistaboom.essence_ascendance.client.EssenceInfuserRenderer;
import com.mistaboom.essence_ascendance.client.EssenceInfuserScreen;
import com.mistaboom.essence_ascendance.client.EssencePylonRenderer;
import com.mistaboom.essence_ascendance.client.EssencePylonScreen;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusContent;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/** Fabric 26 client registration, using native item-model codecs and shared presentation. */
public final class EssenceAscendanceFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        FabricItemModelCodecs2612.register();
        LatentOreFabricModels.register();
        EssenceAscendanceClient.init();

        MenuScreens.register(EssenceCrucibleContent.TEAM_MENU.get(),
                com.mistaboom.essence_ascendance.client.TeamScreen::new);
        MenuScreens.register(EssenceCrucibleContent.MACHINE_ACCESS_MENU.get(),
                com.mistaboom.essence_ascendance.client.MachineAccessScreen::new);
        MenuScreens.register(EssenceCrucibleContent.ESSENCE_CRUCIBLE_MENU.get(), EssenceCrucibleScreen::new);
        MenuScreens.register(EssencePylonContent.ESSENCE_PYLON_MENU.get(), EssencePylonScreen::new);
        MenuScreens.register(EssenceInfuserContent.ESSENCE_INFUSER_MENU.get(), EssenceInfuserScreen::new);
        MenuScreens.register(AscendanceNexusContent.ASCENDANCE_NEXUS_MENU.get(), AscendanceNexusScreen::new);

        BlockEntityRendererRegistry.register(EssenceCrucibleContent.ESSENCE_CRUCIBLE_BLOCK_ENTITY.get(),
                EssenceCrucibleRenderer::new);
        BlockEntityRendererRegistry.register(EssencePylonContent.ESSENCE_PYLON_BLOCK_ENTITY.get(),
                EssencePylonRenderer::new);
        BlockEntityRendererRegistry.register(AscendanceNexusContent.ASCENDANCE_NEXUS_BLOCK_ENTITY.get(),
                AscendanceNexusRenderer::new);
        BlockEntityRendererRegistry.register(EssenceInfuserContent.ESSENCE_INFUSER_BLOCK_ENTITY.get(),
                EssenceInfuserRenderer::new);
    }
}
