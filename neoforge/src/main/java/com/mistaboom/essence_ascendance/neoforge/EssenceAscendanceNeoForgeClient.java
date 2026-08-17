package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.EssenceAscendanceClient;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleScreen;
import com.mistaboom.essence_ascendance.client.EssencePylonRenderer;
import com.mistaboom.essence_ascendance.client.EssencePylonScreen;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
    }

    private void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerBlockEntityRenderer(
                EssencePylonContent.ESSENCE_PYLON_BLOCK_ENTITY.get(),
                EssencePylonRenderer::new
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
