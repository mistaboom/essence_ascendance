package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.EssenceAscendance;
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
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@Mod(
        value = EssenceAscendance.MOD_ID,
        dist = Dist.CLIENT
)
public final class EssenceAscendanceNeoForgeClient {

    public EssenceAscendanceNeoForgeClient(
            IEventBus modBus
    ) {
        modBus.addListener(
                this::onClientSetup
        );
        modBus.addListener(
                this::registerMenuScreens
        );
        modBus.addListener(
                this::registerRenderers
        );
        modBus.addListener(
                this::registerClientExtensions
        );
    }

    private void registerMenuScreens(
            RegisterMenuScreensEvent event
    ) {
        event.register(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_MENU.get(),
                EssenceCrucibleScreen::new
        );
        event.register(
                EssencePylonContent.ESSENCE_PYLON_MENU.get(),
                EssencePylonScreen::new
        );
        event.register(
                EssenceInfuserContent.ESSENCE_INFUSER_MENU.get(),
                EssenceInfuserScreen::new
        );
        event.register(
                AscendanceNexusContent.ASCENDANCE_NEXUS_MENU.get(),
                AscendanceNexusScreen::new
        );
    }

    private void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerBlockEntityRenderer(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_BLOCK_ENTITY.get(),
                EssenceCrucibleRenderer::new
        );
        event.registerBlockEntityRenderer(
                EssencePylonContent.ESSENCE_PYLON_BLOCK_ENTITY.get(),
                EssencePylonRenderer::new
        );
        event.registerBlockEntityRenderer(
                AscendanceNexusContent.ASCENDANCE_NEXUS_BLOCK_ENTITY.get(),
                AscendanceNexusRenderer::new
        );
        event.registerBlockEntityRenderer(
                EssenceInfuserContent.ESSENCE_INFUSER_BLOCK_ENTITY.get(),
                EssenceInfuserRenderer::new
        );
    }

    private void registerClientExtensions(
            RegisterClientExtensionsEvent event
    ) {
        IClientItemExtensions machineRenderer = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return EssenceMachineItemRenderer.instance();
            }
        };

        event.registerItem(
                machineRenderer,
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_ITEM.get(),
                EssencePylonContent.ESSENCE_PYLON_ITEM.get(),
                EssencePylonContent.ESSENCE_FOCUS.get(),
                AscendanceNexusContent.ASCENDANCE_NEXUS_ITEM.get(),
                EssenceInfuserContent.ESSENCE_INFUSER_ITEM.get()
        );
    }

    private void onClientSetup(
            FMLClientSetupEvent event
    ) {
        event.enqueueWork(
                () -> EssenceAscendanceClient.init(
                        ItemProperties::register
                )
        );
    }
}
