package com.mistaboom.essence_ascendance.fabric.client;

import com.mistaboom.essence_ascendance.client.EssenceAscendanceClient;
import com.mistaboom.essence_ascendance.client.AscendanceNexusScreen;
import com.mistaboom.essence_ascendance.client.AscendanceNexusRenderer;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleRenderer;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleScreen;
import com.mistaboom.essence_ascendance.client.EssenceInfuserRenderer;
import com.mistaboom.essence_ascendance.client.EssenceMachineItemRenderer;
import com.mistaboom.essence_ascendance.client.EssenceInfuserScreen;
import com.mistaboom.essence_ascendance.client.EssencePylonRenderer;
import com.mistaboom.essence_ascendance.client.EssencePylonScreen;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusContent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
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

        BuiltinItemRendererRegistry.INSTANCE.register(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_ITEM.get(),
                (stack, mode, matrices, buffers, light, overlay) ->
                        EssenceMachineItemRenderer.instance().renderByItem(
                                stack, mode, matrices, buffers, light, overlay
                        )
        );
        BuiltinItemRendererRegistry.INSTANCE.register(
                EssencePylonContent.ESSENCE_PYLON_ITEM.get(),
                (stack, mode, matrices, buffers, light, overlay) ->
                        EssenceMachineItemRenderer.instance().renderByItem(
                                stack, mode, matrices, buffers, light, overlay
                        )
        );
        BuiltinItemRendererRegistry.INSTANCE.register(
                AscendanceNexusContent.ASCENDANCE_NEXUS_ITEM.get(),
                (stack, mode, matrices, buffers, light, overlay) ->
                        EssenceMachineItemRenderer.instance().renderByItem(
                                stack, mode, matrices, buffers, light, overlay
                        )
        );
        BuiltinItemRendererRegistry.INSTANCE.register(
                EssenceInfuserContent.ESSENCE_INFUSER_ITEM.get(),
                (stack, mode, matrices, buffers, light, overlay) ->
                        EssenceMachineItemRenderer.instance().renderByItem(
                                stack, mode, matrices, buffers, light, overlay
                        )
        );

        MenuScreens.register(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_MENU.get(),
                EssenceCrucibleScreen::new
        );
        MenuScreens.register(
                EssencePylonContent.ESSENCE_PYLON_MENU.get(),
                EssencePylonScreen::new
        );
        MenuScreens.register(
                EssenceInfuserContent.ESSENCE_INFUSER_MENU.get(),
                EssenceInfuserScreen::new
        );
        MenuScreens.register(
                AscendanceNexusContent.ASCENDANCE_NEXUS_MENU.get(),
                AscendanceNexusScreen::new
        );
        BlockEntityRendererRegistry.register(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_BLOCK_ENTITY.get(),
                EssenceCrucibleRenderer::new
        );
        BlockEntityRendererRegistry.register(
                EssencePylonContent.ESSENCE_PYLON_BLOCK_ENTITY.get(),
                EssencePylonRenderer::new
        );
        BlockEntityRendererRegistry.register(
                AscendanceNexusContent.ASCENDANCE_NEXUS_BLOCK_ENTITY.get(),
                AscendanceNexusRenderer::new
        );
        BlockEntityRendererRegistry.register(
                EssenceInfuserContent.ESSENCE_INFUSER_BLOCK_ENTITY.get(),
                EssenceInfuserRenderer::new
        );
    }
}
