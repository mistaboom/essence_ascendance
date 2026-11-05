package com.mistaboom.essence_ascendance.client.audio;

import com.mistaboom.essence_ascendance.audio.*;
import com.mistaboom.essence_ascendance.client.ClientPacketDispatch;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;

/** Client-only composition root. Loaded snapshots and accepted events enter before any visual filtering. */
public final class AudioRuntime {
    private static final NativeAudio NATIVE=new NativeAudio();
    private static final AudioEngine ENGINE=new AudioEngine(NATIVE);
    private static final AudioCatalog.Profile FLIGHT=AudioCatalog.get().profile("mobility.thrust");
    private static Object world,player;
    private static boolean listenerAlive;
    private static long clock,sequence;
    private static int scanChunk;
    private static int scanX=Integer.MIN_VALUE,scanZ=Integer.MIN_VALUE;
    private static boolean scanPending=true;
    private static long nextScan;
    private static int entityCursor;
    private static Iterator<BlockEntity> cursor=Collections.emptyIterator();
    private AudioRuntime() { }
    public static void init() { AudioUi.install(AudioRuntime::ui); AudioSourceBridge.install(AudioRuntime::snapshotChanged); AudioDevelopment.init(); }
    static long clock() { return clock; }
    public static void clear() { ENGINE.clear(); CaptionRouter.clear(); world=player=null; listenerAlive=false; entityCursor=0; cursor=Collections.emptyIterator(); nextScan=0; scanChunk=0; scanX=scanZ=Integer.MIN_VALUE; scanPending=true; }
    public static void resourcesReloaded() { ENGINE.invalidate(); NATIVE.invalidated(); CaptionRouter.clear(); }
    private static void prepare() {
        ClientPacketDispatch.preparePresentationConnection();
        var mc=Minecraft.getInstance();
        if(world!=mc.level || player!=mc.player) { clear(); world=mc.level; player=mc.player; }
        boolean alive=mc.player!=null && mc.player.isAlive();
        if(listenerAlive && !alive) { ENGINE.clear(); CaptionRouter.clear(); }
        listenerAlive=alive;
        ENGINE.tick(clock,mc.player==null?Vec3.ZERO:mc.player.position(),mc.isPaused());
    }
    public static void tick() {
        var mc=Minecraft.getInstance();
        prepare();
        clock++;
        ENGINE.tick(clock,mc.player==null?Vec3.ZERO:mc.player.position(),mc.isPaused());
        CaptionRouter.tick(clock);
        if(mc.isPaused() || !listenerAlive) return;
        com.mistaboom.essence_ascendance.client.ChannelSessionsClientState.tick();
        discoverMachines();
        discoverEntities();
    }
    public static void ui(String semantic,Object control) {
        prepare(); var cue=AudioCatalog.get().recipe(semantic);
        if(cue!=null && cue.audience().scope()==AudioCatalog.Scope.UI) ENGINE.oneShot(cue,new AudioEngine.Context(control,Vec3.ZERO,++sequence));
    }
    /** Extension point for tracked client source adapters. Resolve the profile once at the adapter boundary. */
    public static void reconcile(Object key,AudioCatalog.Profile profile,AudioEngine.Context context,boolean active,long revision,java.util.function.BooleanSupplier valid) {
        prepare(); ENGINE.reconcile(key,profile,context,active,revision,valid);
    }
    /** Loaded facts are observed locally; ordinary server clock correction is not a new source incarnation. */
    private static void reconcile(Object key,AudioCatalog.Profile profile,AudioEngine.Context context,java.util.function.BooleanSupplier valid,java.util.function.BooleanSupplier activity) {
        prepare(); ENGINE.reconcile(key,profile,context,true,clock,valid,activity);
    }
    /** Cleanup never depends on spawn admission or connection preparation. */
    public static void remove(Object key) { ENGINE.remove(key); }
    public static void world(com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent event,long occurrence,long originatingTick) {
        prepare();
        var level=Minecraft.getInstance().level;
        if(level==null || !level.dimension().location().equals(event.dimension()) || !FeedbackTime.fresh(originatingTick,level.getGameTime())) return;
        var cue=AudioCatalog.get().recipe(event.recipeId().toString(),event.parameterA(),event.parameterB());
        if(cue==null || cue.audience().scope()==AudioCatalog.Scope.UI) return;
        var entity=event.sourceEntityId()<0?null:level.getEntity(event.sourceEntityId());
        if(cue.audience().scope()==AudioCatalog.Scope.PLAYER) {
            if(entity==Minecraft.getInstance().player && entity!=null)
                ENGINE.oneShot(cue,new AudioEngine.Context(entity,Vec3.ZERO,occurrence));
        } else {
            Object source=event.sourceEntityId()<0?net.minecraft.core.BlockPos.containing(event.position())
                    :AudioSources.oneShotSource(entity==null || entity.isRemoved()?null:entity,occurrence);
            ENGINE.oneShot(cue,new AudioEngine.Context(source,event.position(),occurrence));
        }
    }
    /** Called only after acceptance of a correlated private result, independently of screen rendering. */
    public static void player(String semantic,Object source,long occurrence) {
        prepare(); var cue=AudioCatalog.get().recipe(semantic);
        if(Minecraft.getInstance().player!=null && cue!=null && cue.audience().scope()==AudioCatalog.Scope.PLAYER)
            ENGINE.oneShot(cue,new AudioEngine.Context(source,Vec3.ZERO,occurrence));
    }
    private static void snapshotChanged(BlockEntity source) {
        machineChanged(source);
        // Linked transfer destinations follow the same bounded loaded-chunk cursor.
        if(!scanPending) { scanChunk=0; cursor=Collections.emptyIterator(); }
        scanPending=true; nextScan=0;
    }
    public static void machineChanged(BlockEntity source) {
        if(source!=null && source.isRemoved()) { ENGINE.removeIncarnation(source); return; }
        var mc=Minecraft.getInstance();
        if(mc.player==null || !mc.player.isAlive() || source==null || source.getLevel()==null || !source.getLevel().isClientSide || source.getLevel()!=mc.level) return;
        if(source instanceof com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity crucible) playerTransfers(crucible);
        else if(source instanceof EssenceInfuserBlockEntity infuser) infuserTransfer(infuser);
    }
    private static boolean loaded(BlockEntity source) {
        var level=source.getLevel();
        return level!=null && Minecraft.getInstance().level==level && !source.isRemoved()
                && loadedAt(source.getBlockPos())==source;
    }
    /** Lookup must never create a block entity: setLevel fires before insertion into the chunk map. */
    private static BlockEntity loadedAt(net.minecraft.core.BlockPos position) {
        var level=Minecraft.getInstance().level;
        if(level==null) return null;
        var chunk=level.getChunkSource().getChunk(position.getX()>>4,position.getZ()>>4,ChunkStatus.FULL,false);
        return chunk==null?null:chunk.getBlockEntities().get(position);
    }
    private static Vec3 midpoint(BlockEntity source,Vec3 destination) {
        return Vec3.atCenterOf(source.getBlockPos()).add(destination).scale(.5);
    }
    private static void playerTransfers(com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity source) {
        if(!loaded(source)) return;
        var level=Minecraft.getInstance().level; var state=source.visualState();
        var profile=AudioCatalog.get().profile(AudioSources.beamProfile(true,state.activePylons().size()));
        for(var stream:state.playerStreams()) {
            if(!AudioSources.playerTransfer(stream)) continue;
            var destination=level.getPlayerByUUID(stream.player());
            if(destination==null || !destination.isAlive() || destination.isRemoved()) continue;
            var key=new AudioEngine.Source(source,"transfer",destination);
            reconcile(key,profile,new AudioEngine.Context(key,midpoint(source,destination.position()),0,()->midpoint(source,destination.position())),
                    ()->loaded(source) && destination.level()==level && !destination.isRemoved() && destination.isAlive()
                            && level.getPlayerByUUID(stream.player())==destination,
                    ()->AudioSources.playerTransfer(source.visualState(),stream.player()));
        }
    }
    private static void infuserTransfer(EssenceInfuserBlockEntity destination) {
        if(!loaded(destination) || !AudioSources.infuserTransfer(destination.visualState())) return;
        var linked=destination.visualState().linkedCrucible();
        if(!(loadedAt(linked) instanceof com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity source) || !loaded(source)) return;
        var key=new AudioEngine.Source(source,"transfer",destination);
        var profile=AudioCatalog.get().profile(AudioSources.beamProfile(false,source.visualState().activePylons().size()));
        reconcile(key,profile,new AudioEngine.Context(key,midpoint(source,Vec3.atCenterOf(destination.getBlockPos())),0),
                ()->loaded(source) && loaded(destination) && source.getBlockPos().equals(destination.visualState().linkedCrucible()),
                ()->AudioSources.infuserTransfer(destination.visualState()));
    }
    /** Native tracking already owns these players. One bounded cursor feeds the common controller. */
    private static void discoverEntities() {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) return;
        var level=mc.level; var players=level.players(); int count=Math.min(32,players.size());
        for(int n=0;n<count;n++) {
            if(entityCursor>=players.size()) entityCursor=0;
            var source=players.get(entityCursor++);
            boolean active=source.isAlive() && !source.isRemoved() && AudioSources.thrust(((com.mistaboom.essence_ascendance.visual.FlightVisualState)source).essenceAscendance$flightFlags());
            // The owned activity/position suppliers already follow changes. Discovery only admits missing bodies.
            if(!active) continue;
            var key=new AudioEngine.Source(source,"thrust");
            if(ENGINE.containsSource(key)) continue;
            ENGINE.reconcile(key,FLIGHT,new AudioEngine.Context(key,source.position(),0,source::position),true,clock,
                    ()->mc.level==level && !source.isRemoved() && source.isAlive() && level.getEntity(source.getId())==source,
                    ()->AudioSources.thrust(((com.mistaboom.essence_ascendance.visual.FlightVisualState)source).essenceAscendance$flightFlags()));
        }
    }
    /** Bounded recovery over already loaded nearby chunks, also reconsiders sources omitted under saturation. */
    private static void discoverMachines() {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) return;
        int x=mc.player.blockPosition().getX()>>4,z=mc.player.blockPosition().getZ()>>4;
        if(x!=scanX || z!=scanZ) { scanX=x; scanZ=z; scanChunk=0; cursor=Collections.emptyIterator(); scanPending=true; nextScan=0; }
        if(clock<nextScan) return;
        scanPending=true;
        int visited=0,chunks=0;
        try {
            while(visited<32 && chunks<25) {
                if(cursor.hasNext()) { machineChanged(cursor.next()); visited++; continue; }
                if(scanChunk>=25) { scanChunk=0; scanPending=false; nextScan=clock+20; break; }
                int index=scanChunk++;
                var chunk=mc.level.getChunkSource().getChunk(scanX+index%5-2,scanZ+index/5-2,ChunkStatus.FULL,false);
                chunks++; cursor=chunk==null?Collections.emptyIterator():chunk.getBlockEntities().values().iterator();
            }
        } catch(ConcurrentModificationException ignored) { cursor=Collections.emptyIterator(); }
    }
    /** Development preview shares the same resolved cue, admission, caption and lifecycle path. */
    private static final Object AUDITION = new Object();
    static void auditionStart(String profile) {
        prepare(); var mc=Minecraft.getInstance(); var resolved=AudioCatalog.get().profile(profile);
        long expires=clock+200;
        ENGINE.reconcile(AUDITION,resolved,new AudioEngine.Context(AUDITION,mc.player==null?Vec3.ZERO:mc.player.position(),++sequence),true,clock,()->clock<expires);
    }
    static void auditionStop() { ENGINE.remove(AUDITION); }
    static void audition(String semantic) {
        prepare(); var cue=AudioCatalog.get().recipe(semantic); if(cue==null) return;
        var mc=Minecraft.getInstance(); Vec3 pos=mc.player==null?Vec3.ZERO:mc.player.position();
        ENGINE.oneShot(cue,new AudioEngine.Context(AudioRuntime.class,pos,++sequence));
    }
}
