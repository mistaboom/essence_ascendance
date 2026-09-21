package com.mistaboom.essence_ascendance.utility;

public final class SharedTargetWorkTest {
    public static void main(String[] args) {
        Object level = new Object(), target = new Object();
        check(SharedTargetWork.claim(level,target,"gift",0,20));
        for (int i=0;i<20;i++) check(!SharedTargetWork.claim(level,target,"gift",i,20));
        check(SharedTargetWork.claim(level,target,"gift",20,20));
        check(SharedTargetWork.claim(level,target,"herd",20,1));
        for (int i=0;i<SharedTargetWork.MAX_WORK_PER_TICK;i++) SharedTargetWork.visit(level,21);
        check(!SharedTargetWork.claim(level,new Object(),"processor",21,1));
        check(SharedTargetWork.claim(level,target,"processor",22,1));
        check(SharedTargetWork.claim(new Object(),new Object(),"processor",21,1));
        check(SharedTargetWork.claimPosition(level, 123, "crop", 30, 20));
        check(!SharedTargetWork.claimPosition(level, 123, "crop", 31, 20));
        check(SharedTargetWork.claimPosition(level, 123, "crop", 50, 20));
        for (int units=2;units<100;units++) {
            int limit = com.mistaboom.essence_ascendance.gathering.GatheringSalvageService.materialRecoveryLimit(units);
            check(limit >= 1 && limit < units);
        }
        var stationary = new com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.Sample(false,true,false,false,false,0,0,"stationary",0);
        var falling = new com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.Sample(false,false,false,false,false,0,0,"falling",.5);
        var forced = new com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.Sample(false,true,true,true,true,1,0,"forced",1);
        var teleport = new com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.Sample(true,true,false,true,true,20,0,"teleport",20);
        check(!stationary.qualifiedSpatialMovement(.01)); check(falling.qualifiedSpatialMovement(.01));
        check(!forced.qualifiedSpatialMovement(.01)); check(!teleport.qualifiedSpatialMovement(.01));
        System.out.println("SharedTargetWorkTest PASS: overlap, independent channels, work bounds and recovery");
    }
    private static void check(boolean pass) { if (!pass) throw new AssertionError("Shared target work contract"); }
}
