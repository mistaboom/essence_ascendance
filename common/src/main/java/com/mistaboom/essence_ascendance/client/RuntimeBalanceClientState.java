package com.mistaboom.essence_ascendance.client;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.network.RuntimeBalancePayload;
import dev.architectury.networking.NetworkManager;

/** Connection-scoped server display data; never scans the local pack. */
public final class RuntimeBalanceClientState {
    private static boolean initialized;
    private RuntimeBalanceClientState() {}
    public static void init() {
        if(initialized)return;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,RuntimeBalancePayload.TYPE,RuntimeBalancePayload.CODEC,
                (payload,context)->ClientPacketDispatch.queue(context,()-> {
                    var runtime=RuntimeBalanceDefinition.fromJson(JsonParser.parseString(payload.runtimeJson()).getAsJsonObject());
                    // Player-aware equipment snapshots follow the runtime packet. Drop the previous
                    // profile's numbers until those snapshots arrive, preserving loading behavior.
                    EquipmentTooltipClientState.clear();
                    EssenceConfigManager.installClient(runtime);
                }));
        initialized=true;
    }
    public static void clear() {EssenceConfigManager.clearClient();}
}
