package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.EssenceAscendanceClient;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

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
    }


    private void onClientSetup(
            FMLClientSetupEvent event
    ) {

        event.enqueueWork(
                () ->
                        EssenceAscendanceClient.init(
                                (
                                        item,
                                        propertyId,
                                        property
                                ) ->
                                        ItemProperties.register(
                                                item,
                                                propertyId,

                                                /*
                                                 * Force NeoForge's public
                                                 * ItemPropertyFunction
                                                 * overload instead of the
                                                 * private vanilla clamped
                                                 * overload.
                                                 */
                                                (ItemPropertyFunction) property
                                        )
                        )
        );
    }
}