package com.mistaboom.essence_ascendance.achievementcontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Disposable fixture control only. Never included in the production mod or its mixin configuration. */
public final class HarvestRemovalCancellation {
    private static Level level;
    private static long position;
    private static int intercepted;
    private HarvestRemovalCancellation(){ }
    public static synchronized void deny(Level target,BlockPos address){level=target;position=address.asLong();intercepted=0;}
    public static synchronized void allow(){level=null;}
    public static synchronized int intercepted(){return intercepted;}
    public static synchronized boolean rejects(Level target,BlockPos address){
        if(level!=target||position!=address.asLong())return false;
        intercepted++;return true;
    }
}
