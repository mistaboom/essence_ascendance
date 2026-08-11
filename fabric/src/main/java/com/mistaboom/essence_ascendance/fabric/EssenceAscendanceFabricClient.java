package com.mistaboom.essence_ascendance.fabric;

import com.mistaboom.essence_ascendance.client.EssenceAscendanceClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;

public final class EssenceAscendanceFabricClient
        implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        EssenceAscendanceClient.init(
                FabricModelPredicateProviderRegistry::register
        );
    }
}