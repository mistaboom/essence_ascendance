package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.InfuserSoulbindPayload;
import dev.architectury.networking.NetworkManager;

/** No global pending state: changing or closing the screen discards its confirmation. */
public final class InfuserSoulbindClientState {
    private static boolean initialized;
    private InfuserSoulbindClientState() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, InfuserSoulbindPayload.TYPE, InfuserSoulbindPayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> {
                    var minecraft = net.minecraft.client.Minecraft.getInstance();
                    if (minecraft.player != null && minecraft.player.containerMenu.containerId == payload.menuId()
                            && minecraft.screen instanceof EssenceInfuserScreen screen) screen.confirmSoulbind(payload);
                }));
    }
}
