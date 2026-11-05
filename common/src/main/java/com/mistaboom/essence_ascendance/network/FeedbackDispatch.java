package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.audio.*;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import com.mistaboom.essence_ascendance.visual.transientfx.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Shared audience selection. Private data never enters a public recipient union. */
public final class FeedbackDispatch {
    private static final AtomicLong SEQUENCE=new AtomicLong(new java.util.Random().nextLong());
    private FeedbackDispatch() { }
    public static long nextOccurrence() { return SEQUENCE.incrementAndGet(); }
    public static boolean nearby(Vec3 listener,Vec3 source,double radius) {
        return Double.isFinite(radius) && radius>0 && listener.distanceToSqr(source)<=radius*radius;
    }
    public static void nearby(ServerLevel level,Vec3 position,double radius,Consumer<ServerPlayer> delivery) {
        for(var player:level.players()) if(nearby(player.position(),position,radius)) delivery.accept(player);
    }
    public static void send(ServerPlayer player,CustomPacketPayload payload) {
        if(NetworkManager.canPlayerReceive(player,payload.type())) NetworkManager.sendToPlayer(player,payload);
    }
    /** Block, entity and private occurrences share the negotiated semantic envelope. */
    public static void world(ServerLevel level,String semantic,Vec3 position) {
        worldOccurrence(level,semantic,null,position);
    }
    /** Entity occurrences preserve source identity independently of their emission position. */
    public static void world(ServerLevel level,String semantic,Entity source,Vec3 position) {
        if(source!=null && source.level()==level) worldOccurrence(level,semantic,source,position);
    }
    private static void worldOccurrence(ServerLevel level,String semantic,Entity source,Vec3 position) {
        var cue=AudioCatalog.get().recipe(semantic);
        if(cue==null || cue.audience().scope()!=AudioCatalog.Scope.WORLD) return;
        var payload=payload(level,semantic,source,position);
        nearby(level,position,cue.audience().radius(),player->send(player,payload));
    }
    public static void player(ServerPlayer player,String semantic) {
        var cue=AudioCatalog.get().recipe(semantic);
        if(cue!=null && cue.audience().scope()==AudioCatalog.Scope.PLAYER && player.connection!=null)
            send(player,payload(player.serverLevel(),semantic,player,player.position()));
    }
    private static WorldVisualEventPayload payload(ServerLevel level,String semantic,Entity source,Vec3 position) {
        long occurrence=nextOccurrence();
        var recipe=semantic.indexOf(':')>=0 ? ResourceLocation.parse(semantic)
                : ResourceLocation.fromNamespaceAndPath("essence_ascendance",semantic);
        var event=new WorldVisualEvent(recipe,level.dimension().location(),position,
                source==null?WorldVisualEvent.NO_ENTITY:source.getId(),WorldVisualEvent.NO_ENTITY,
                null,Vec3.ZERO,1,0,VisualIntensity.MICRO,SemanticVisualColor.UTILITY,1,occurrence,0,0);
        return new WorldVisualEventPayload(event,occurrence,level.getGameTime(),0);
    }
}
