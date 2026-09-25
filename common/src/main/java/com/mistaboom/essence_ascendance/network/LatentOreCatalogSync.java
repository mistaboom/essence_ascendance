package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.worldgen.PrimarySubstrateDiscovery;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class LatentOreCatalogSync {
    private static boolean initialized;
    private static final Map<ServerPlayer, Set<ResourceLocation>> SENT = new WeakHashMap<>();
    private LatentOreCatalogSync() {}
    public static void init() {
        if (initialized) return;
        if (Platform.getEnvironment() == Env.SERVER)
            NetworkManager.registerS2CPayloadType(LatentOreCatalogPayload.TYPE, LatentOreCatalogPayload.CODEC);
        PlayerEvent.PLAYER_JOIN.register(LatentOreCatalogSync::send);
        PlayerEvent.PLAYER_QUIT.register(SENT::remove);
        LifecycleEvent.SERVER_STARTED.register(PrimarySubstrateDiscovery::selectedHostIds);
        LifecycleEvent.SERVER_STOPPED.register(server -> SENT.clear());
        initialized = true;
    }
    public static void send(ServerPlayer player) {
        if (!NetworkManager.canPlayerReceive(player, LatentOreCatalogPayload.TYPE)) return;
        Set<ResourceLocation> hosts = PrimarySubstrateDiscovery.selectedHostIds(player.server);
        if (hosts.equals(SENT.get(player))) return;
        NetworkManager.sendToPlayer(player, new LatentOreCatalogPayload(hosts));
        SENT.put(player, hosts);
    }
}
