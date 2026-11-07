package com.mistaboom.essence_ascendance.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Positive current-state worldgen evidence. Absence, an ore tag or lack of a placement record proves nothing.
 * Native worker threads supply successful writes; no SavedData or mutable world objects enter this cache. */
public final class GeneratedOreObservations {
    public static final int MAX_CHUNKS = 256;
    public static final int MAX_ORES_PER_CHUNK = 1_024;
    public static final long EXPIRY_NANOS = TimeUnit.MINUTES.toNanos(30);
    private static final Map<MinecraftServer,Window> SERVERS = new WeakHashMap<>();
    private GeneratedOreObservations() { }
    private static synchronized Window window(MinecraftServer server) { return SERVERS.computeIfAbsent(server,ignored->new Window()); }
    /** Called only after a native WorldGenRegion write, including the vanilla OreFeature bulk path. */
    public static void generated(MinecraftServer server,String dimension,BlockPos position,BlockState state) {
        var proof=window(server);
        synchronized(proof){proof.written(dimension,position,state,NaturalOreDropService.isOreType(state),System.nanoTime());}
    }
    public static boolean matches(MinecraftServer server,String dimension,BlockPos position,BlockState state) {
        if(!NaturalOreDropService.isOreType(state))return false;
        var proof=window(server);
        synchronized(proof){return proof.matches(dimension,position,state,System.nanoTime());}
    }
    /** Any attempted non-worldgen write closes old proof before same-state placement/admin shortcuts. */
    public static void mutation(MinecraftServer server,String dimension,BlockPos position) {
        window(server).mutation(dimension,position);
    }
    public static void unloaded(MinecraftServer server,String dimension,ChunkPos chunk) { window(server).unloaded(dimension,chunkAddress(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4)); }
    public static synchronized void clear(MinecraftServer server) { SERVERS.remove(server); }
    static long chunkAddress(int x,int z){return ((long)x&0xffffffffL)|(((long)z&0xffffffffL)<<32);}

    /** Bounded scalar addresses and immutable canonical BlockState values, independently testable. */
    static final class Window {
        private record ChunkKey(String dimension,long chunk) { }
        private static final class ChunkProof {
            final Map<Long,BlockState> ores=new HashMap<>();
            long touched;
            ChunkProof(long now){touched=now;}
        }
        private final LinkedHashMap<ChunkKey,ChunkProof> chunks=new LinkedHashMap<>(16,.75f,true);
        private long nextSweep=Long.MIN_VALUE;
        synchronized void written(String dimension,BlockPos position,BlockState state,boolean ore,long now) {
            expire(now);
            var key=key(dimension,position);var chunk=chunks.get(key);
            if(chunk!=null&&expired(chunk,now)){chunks.remove(key);chunk=null;}
            if(!ore){if(chunk!=null)chunk.ores.remove(position.asLong());return;}
            if(chunk==null){
                if(chunks.size()>=MAX_CHUNKS)chunks.remove(chunks.keySet().iterator().next());
                chunk=new ChunkProof(now);chunks.put(key,chunk);
            }
            chunk.touched=now;
            if(chunk.ores.containsKey(position.asLong())||chunk.ores.size()<MAX_ORES_PER_CHUNK)chunk.ores.put(position.asLong(),state);
        }
        synchronized boolean matches(String dimension,BlockPos position,BlockState state,long now) {
            expire(now);var chunk=chunks.get(key(dimension,position));
            if(chunk==null)return false;
            if(expired(chunk,now)){chunks.remove(key(dimension,position));return false;}
            chunk.touched=now;
            return chunk.ores.get(position.asLong())==state;
        }
        synchronized void mutation(String dimension,BlockPos position) {
            var chunk=chunks.get(key(dimension,position));if(chunk!=null)chunk.ores.remove(position.asLong());
        }
        synchronized void unloaded(String dimension,long chunk) { chunks.remove(new ChunkKey(dimension,chunk)); }
        synchronized int chunks(){return chunks.size();}
        synchronized int ores(){return chunks.values().stream().mapToInt(chunk->chunk.ores.size()).sum();}
        private static ChunkKey key(String dimension,BlockPos position){return new ChunkKey(dimension,chunkAddress(position.getX()>>4,position.getZ()>>4));}
        private static boolean expired(ChunkProof chunk,long now){return now<chunk.touched||now-chunk.touched>=EXPIRY_NANOS;}
        private void expire(long now){
            if(nextSweep!=Long.MIN_VALUE&&now<nextSweep)return;
            chunks.values().removeIf(chunk->expired(chunk,now));nextSweep=now+TimeUnit.MINUTES.toNanos(1);
        }
    }
}
