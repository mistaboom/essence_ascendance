package com.mistaboom.essence_ascendance.posture;

import com.mistaboom.essence_ascendance.config.PostureBalanceSettings;
import net.minecraft.world.phys.Vec3;

/** Deterministic meter/geometry/probability contracts, separate from transformed native fixtures. */
public final class PostureMeterTest {
    private static int checks;
    public static void main(String[] args) {
        var settings = PostureBalanceSettings.defaults();
        for (var choice : PostureMeter.Choice.values()) {
            var meter = new PostureMeter(); meter.select(choice);
            for (int tick=1; tick<=500; tick++) {
                meter.tick(tick,.1,0,true,false,true,true,settings);
                double once=meter.meter(); meter.tick(tick,.1,0,true,false,true,true,settings);
                check(meter.meter()==once,"Duplicate tick never builds or drains");
                check(meter.meter()>=0 && meter.meter()<=1,"All posture meters remain bounded");
            }
            if (choice==PostureMeter.Choice.EVASIVE) check(meter.meter()==1,"Continuous native intentional movement reaches cap");
            if (choice==PostureMeter.Choice.BULWARK) check(meter.meter()==0,"Movement never builds Bulwark");
            meter.select(PostureMeter.Choice.NONE); check(meter.meter()==0 && meter.stacks()==0,"Switching clears all memory");
        }
        for (int mode=0;mode<6;mode++) {
            var meter=new PostureMeter(); meter.select(PostureMeter.Choice.EVASIVE);
            for(int t=1;t<=100;t++)meter.tick(t,.1,0,true,false,true,true,settings);
            for(int t=101;t<=130;t++)meter.tick(t,mode==0?.0001:.1,0,mode!=1,mode==2,mode!=3,false,settings);
            if(mode<4)check(meter.meter()==0,"Noise, absent intent, force and unsupported mode drain rather than build");
        }
        var bulwark=new PostureMeter(); bulwark.select(PostureMeter.Choice.BULWARK);
        for(int t=1;t<=settings.movement().stableTicks()-1;t++)bulwark.tick(t,0,0,false,false,true,true,settings);
        check(bulwark.meter()==0 && !bulwark.still(),"Hysteresis settling interval cannot build");
        for(int t=settings.movement().stableTicks();t<200;t++)bulwark.tick(t,.001,.2,false,false,true,true,settings);
        check(bulwark.meter()==1 && bulwark.still(),"Packet jitter permits stable threat-facing charge");
        bulwark.tick(200,.01,2,false,false,true,true,settings);
        check(bulwark.still(),"Exit hysteresis accepts jitter above entry thresholds");
        bulwark.tick(201,0,4,false,false,true,true,settings);
        check(!bulwark.still() && bulwark.meter()<1,"Material turn immediately stops resistance eligibility and drains");
        for(int t=202;t<=230;t++)bulwark.tick(t,0,0,false,false,true,false,settings);
        check(bulwark.meter()==0,"Staring into empty space cannot retain charge");
        var evade=new PostureMeter();evade.select(PostureMeter.Choice.EVASIVE);
        for(int t=1;t<=100;t++)evade.tick(t,.1,0,true,false,true,false,settings);
        check(evade.hit(1,100,"mod:physical",true,false,true,settings) && evade.meter()==.5,"Successful dodge consumes configured meter fraction");
        check(!evade.hit(1,100,"mod:physical",true,true,true,settings) && evade.meter()==.5,"Duplicate incoming event cannot consume twice");
        evade.tick(101,0,0,true,false,true,false,settings,true);
        check(evade.meter()==.5&&evade.reason().equals("waiting_for_movement_sample"),"Brief missing motion samples only hold charge; input alone cannot build it");
        evade.tick(102,0,0,false,false,true,false,settings,true);
        close(evade.meter(),.45,"Released input overrides movement sample grace");
        evade.tick(103,.1,0,true,true,true,false,settings,true);
        close(evade.meter(),.4,"Actual forced movement still drains during movement sample grace");
        evade.tick(104,0,0,true,false,false,false,settings,true);
        close(evade.meter(),.35,"Unsupported movement still drains during movement sample grace");
        evade.hit(2,101,"mod:physical",true,true,false,settings);check(evade.meter()==0,"Actual taken hit drains configured meter");
        var adapt=new PostureMeter();adapt.select(PostureMeter.Choice.ADAPTIVE);
        adapt.hit(99,1,"mod:nested",false,true,false,settings);
        check(adapt.adaptation("mod:alpha",1,settings.adaptive())==0,"First exact registry type is unmitigated");
        adapt.hit(1,1,"mod:alpha",true,true,false,settings);
        check(adapt.stacks()==1,"Newer nested secondary event cannot steal enclosing primary adaptation");
        close(adapt.adaptation("mod:alpha",2,settings.adaptive()),.05,"Second same type receives first mitigation step");
        close(adapt.adaptation("mod:beta",2,settings.adaptive()),0,"Different exact registry identity resets before mitigation");
        adapt.hit(2,2,"mod:beta",false,true,false,settings);check(adapt.damageType().equals("mod:alpha"),"Ineligible secondary never changes adaptation");
        adapt.hit(3,3,"mod:beta",true,false,false,settings);check(adapt.damageType().equals("mod:alpha"),"Accepted zero probes never reseed");
        adapt.hit(4,4,"mod:beta",true,true,false,settings);check(adapt.damageType().equals("mod:beta")&&adapt.stacks()==1,"Different confirmed hit reseeds one stack");
        for(int t=5;t<50;t++)adapt.hit(t,t,"mod:beta",true,true,false,settings);
        check(adapt.stacks()==settings.adaptive().maximumStacks(),"Adaptation cap bounded");
        close(adapt.adaptation("mod:beta",50,settings.adaptive()),.2,"Only eligible extra hits contribute resistance");
        long expiry=adapt.expiresAt();adapt.expire(expiry-1);check(adapt.stacks()>0,"Adaptation remains through final active tick");
        adapt.expire(expiry);check(adapt.stacks()==0&&adapt.damageType().isEmpty(),"Exact expiry clears bounded identity memory");
        for(double chance:new double[]{0,.01,.2,.75}) {
            int hits=0;for(int n=0;n<10000;n++)if(PostureMeter.dodge(n/10000d,chance))hits++;
            check(hits==(int)(chance*10000),"Uniform deterministic probability boundary");
        }
        check(!PostureMeter.dodge(Double.NaN,.5)&&!PostureMeter.dodge(-1,.5)&&!PostureMeter.dodge(1,.5),"Invalid rolls fail closed");
        check(!PostureMeter.dodge(.2,.2)&&PostureMeter.dodge(0,.2),"Dodge interval is half-open");
        check(ThreatFacingResolver.facing(Vec3.ZERO,new Vec3(0,0,1),new Vec3(0,0,5),60),"Frontal threat accepted");
        check(!ThreatFacingResolver.facing(Vec3.ZERO,new Vec3(0,0,1),new Vec3(0,0,-5),60),"Rear threat rejected");
        check(!ThreatFacingResolver.facing(Vec3.ZERO,Vec3.ZERO,new Vec3(0,0,5),60),"No invented view direction");
        close(PostureService.angle(179,-179),2,"Yaw wrap avoids false full turn");
        evade.tick(500,20,0,true,false,true,false,settings);check(evade.meter()==0,"Teleport/discontinuity resets");
        boolean rejected=false;try{new PostureBalanceSettings.Evasive(80,20,Double.NaN,.5,1);}catch(IllegalArgumentException ex){rejected=true;}
        check(rejected,"Nonfinite config rejected");
        System.out.println("PostureMeterTest: "+checks+" deterministic checks passed");
    }
    private static void close(double a,double b,String message){check(Math.abs(a-b)<1e-9,message);}
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
}
