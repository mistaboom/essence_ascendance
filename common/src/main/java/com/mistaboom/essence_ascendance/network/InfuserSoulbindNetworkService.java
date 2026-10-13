package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserMenu;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Confirmation delivery uses the existing live Infuser menu as its complete authorization scope. */
public final class InfuserSoulbindNetworkService {
    private static boolean initialized;
    private InfuserSoulbindNetworkService() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        if (Platform.getEnvironment() == Env.SERVER) NetworkManager.registerS2CPayloadType(InfuserSoulbindPromptPayload.TYPE, InfuserSoulbindPromptPayload.CODEC);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, InfuserSoulbindPayload.TYPE, InfuserSoulbindPayload.CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) handle(player, payload);
                }));
    }
    public static void prompt(ServerPlayer player, int menuId, UUID receipt) {
        if (NetworkManager.canPlayerReceive(player, InfuserSoulbindPromptPayload.TYPE))
            NetworkManager.sendToPlayer(player, new InfuserSoulbindPromptPayload(menuId, receipt));
    }
    public static boolean handle(ServerPlayer player, InfuserSoulbindPayload payload) {
        return player.containerMenu instanceof EssenceInfuserMenu menu && menu.containerId == payload.menuId()
                && menu.confirmSoulbind(player, payload.receipt());
    }
}
