package com.mistaboom.essence_ascendance.gathering;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

/** Positive-provenance transitions and bounded cleanup; no tag-only natural-origin inference. */
public final class GeneratedOreObservationTest {
    private static int checks;
    private static final String OVERWORLD="minecraft:overworld",NETHER="minecraft:the_nether";
    public static void main(String[]args){
        Thread.currentThread().setUncaughtExceptionHandler((thread,failure)->failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        transitions();bounds();expiry();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Generated ore provenance: "+checks+" checks passed");
    }
    private static void transitions(){
        var window=new GeneratedOreObservations.Window();var position=new BlockPos(1,10,1);var iron=Blocks.IRON_ORE.defaultBlockState();var gold=Blocks.GOLD_ORE.defaultBlockState();
        check(!window.matches(OVERWORLD,position,iron,1),"An ore state with no positive generation observation is unknown");
        window.written(OVERWORLD,position,iron,true,2);
        check(window.matches(OVERWORLD,position,iron,3),"Successful native ore-generation write supplies current-state proof");
        check(!window.matches(NETHER,position,iron,3),"Proof never crosses dimension identity");
        check(!window.matches(OVERWORLD,position.offset(1,0,0),iron,3),"Proof never crosses position identity");
        check(!window.matches(OVERWORLD,position,gold,3),"Different ore state at same address cannot reuse an earlier block proof");
        window.mutation(OVERWORLD,position);
        check(!window.matches(OVERWORLD,position,iron,4),"Player/command mutation including no-op same-state write closes proof");
        window.written(OVERWORLD,position,gold,true,5);
        check(window.matches(OVERWORLD,position,gold,6)&&!window.matches(OVERWORLD,position,iron,6),"Later positive worldgen ore replacement updates actual state");
        window.written(OVERWORLD,position,Blocks.STONE.defaultBlockState(),false,7);
        check(!window.matches(OVERWORLD,position,gold,8),"Successful non-ore worldgen replacement erases prior ore proof");
        window.written(OVERWORLD,position,iron,true,9);window.unloaded(OVERWORLD,GeneratedOreObservations.chunkAddress(position.getX()>>4,position.getZ()>>4));
        check(!window.matches(OVERWORLD,position,iron,10),"Native chunk unload closes session evidence without retaining historical ore positions");
        check(!new GeneratedOreObservations.Window().matches(OVERWORLD,position,iron,11),"Fresh server/restart session does not infer old-world natural ore");
    }
    private static void bounds(){
        var window=new GeneratedOreObservations.Window();var iron=Blocks.IRON_ORE.defaultBlockState();
        for(int i=0;i<GeneratedOreObservations.MAX_ORES_PER_CHUNK;i++)window.written(OVERWORLD,new BlockPos(i&15,i>>8,(i>>4)&15),iron,true,1);
        var overflow=new BlockPos(0,100,0);window.written(OVERWORLD,overflow,iron,true,1);
        check(!window.matches(OVERWORLD,overflow,iron,2),"Full chunk capacity rejects new unrecorded ore without claiming its origin");
        check(window.matches(OVERWORLD,new BlockPos(0,0,0),iron,2),"Capacity pressure retains current known block proof");
        window.mutation(OVERWORLD,new BlockPos(0,0,0));window.written(OVERWORLD,overflow,iron,true,3);
        check(window.matches(OVERWORLD,overflow,iron,4),"A real mutation frees capacity for a later positively observed generated block");
        var chunks=new GeneratedOreObservations.Window();
        for(int i=0;i<GeneratedOreObservations.MAX_CHUNKS;i++)chunks.written(OVERWORLD,new BlockPos(i*16,20,0),iron,true,1);
        check(chunks.matches(OVERWORLD,new BlockPos(0,20,0),iron,2),"Reading active known chunk refreshes LRU scope");
        chunks.written(OVERWORLD,new BlockPos(GeneratedOreObservations.MAX_CHUNKS*16,20,0),iron,true,3);
        check(chunks.matches(OVERWORLD,new BlockPos(0,20,0),iron,4)&&!chunks.matches(OVERWORLD,new BlockPos(16,20,0),iron,4),"Chunk cap evicts only least-recently observed proof and cannot manufacture a replacement");
        check(chunks.chunks()<=GeneratedOreObservations.MAX_CHUNKS&&window.ores()<=GeneratedOreObservations.MAX_ORES_PER_CHUNK,"Both live scopes remain bounded after replacement");
    }
    private static void expiry(){
        var window=new GeneratedOreObservations.Window();var iron=Blocks.IRON_ORE.defaultBlockState();var pos=new BlockPos(1,10,1);
        window.written(OVERWORLD,pos,iron,true,1);
        check(window.matches(OVERWORLD,pos,iron,GeneratedOreObservations.EXPIRY_NANOS),"Proof remains usable immediately before its inactivity deadline");
        check(window.matches(OVERWORLD,pos,iron,GeneratedOreObservations.EXPIRY_NANOS+1),"Legitimate observation renews active chunk inactivity");
        check(!window.matches(OVERWORLD,pos,iron,2*GeneratedOreObservations.EXPIRY_NANOS+1),"Exact thirty-minute inactivity expires unknown evidence");
        window.written(OVERWORLD,pos,iron,true,3*GeneratedOreObservations.EXPIRY_NANOS);
        check(!window.matches(OVERWORLD,pos,iron,1),"Backward evidence-clock input fails closed");
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
