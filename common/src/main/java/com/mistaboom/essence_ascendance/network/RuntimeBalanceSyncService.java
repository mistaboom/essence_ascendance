package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** Sends a fresh profile before future player/menu previews and on live replacement. */
public final class RuntimeBalanceSyncService {
    private static boolean initialized;
    private static RuntimeBalanceDefinition encoded;
    private static RuntimeBalancePayload payload;
    private static final Map<ServerPlayer,RuntimeBalanceDefinition> SENT=new WeakHashMap<>();
    private RuntimeBalanceSyncService() {}
    public static void init() {
        if(initialized)return;
        if(Platform.getEnvironment()==Env.SERVER)NetworkManager.registerS2CPayloadType(RuntimeBalancePayload.TYPE,RuntimeBalancePayload.CODEC);
        PlayerEvent.PLAYER_JOIN.register(RuntimeBalanceSyncService::send);
        PlayerEvent.PLAYER_QUIT.register(SENT::remove);
        TickEvent.PLAYER_POST.register(player->{if(player instanceof ServerPlayer serverPlayer && serverPlayer.tickCount%5==0)send(serverPlayer);});
        initialized=true;
    }
    public static void send(ServerPlayer player) {
        RuntimeBalanceDefinition current=EssenceConfigManager.serverRuntime();
        if(current==null||SENT.get(player)==current||!NetworkManager.canPlayerReceive(player,RuntimeBalancePayload.TYPE))return;
        if(encoded!=current) { payload=new RuntimeBalancePayload(current.toJson().toString());encoded=current; }
        NetworkManager.sendToPlayer(player,payload);
        SENT.put(player,current);
        LatentOreCatalogSync.send(player);
    }
    public static void syncAll(MinecraftServer server) {for(var player:server.getPlayerList().getPlayers())send(player);}
    public static void clear() {SENT.clear();encoded=null;payload=null;}
}
