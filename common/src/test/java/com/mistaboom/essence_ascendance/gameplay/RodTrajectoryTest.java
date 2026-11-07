package com.mistaboom.essence_ascendance.gameplay;

import java.util.*;
import static com.mistaboom.essence_ascendance.gameplay.RodTrajectoryTracker.*;

public final class RodTrajectoryTest {
    private static int checks,next;
    private static final UUID A=new UUID(1,1),B=new UUID(2,2),T=new UUID(3,3),H=new UUID(4,4);
    private static final String D="minecraft:overworld";
    private static final Point ZERO=new Point(0,0,0);
    public static void main(String[] args) {
        water(); grapples(); launches(); arrival(); lifecycle(); provenance(); bounds();
        System.out.println("Rod trajectories: "+checks+" checks passed");
    }
    private static Origin origin(boolean anchor,boolean hostile,boolean item,boolean wet,boolean up) {
        return new Origin("pull-"+(++next),A,H,anchor?null:T,D,0,ZERO,new Point(8,0,0),anchor,hostile,item,8,wet,up);
    }
    private static void water() {
        var tracker=new RodTrajectoryTracker(); var out=new ArrayList<RodOutcome>(); tracker.start(origin(true,false,false,true,false),true);
        for (int i=1;i<=20;i++) tracker.sampleAnchor(A,D,i,new Point(i,0,0),4,false,true,false,out::add);
        check(out.getLast().distance()==20,"Exact20 includes initial exposed origin");
        int before=out.size(); tracker.sampleAnchor(A,D,20,new Point(20,0,0),4,false,true,false,out::add);
        check(out.size()==before,"Duplicate position tick produces no outcome or reset");
        tracker.sampleAnchor(A,D,21,new Point(19,0,0),4,false,true,false,out::add);
        check(out.getLast().distance()==19,"Displacement decreases after backtracking rather than accumulating path");
        tracker.sampleAnchor(A,D,22,new Point(18,0,0),4,false,false,false,out::add);
        tracker.sampleAnchor(A,D,23,new Point(19,0,0),4,false,true,false,out::add);
        check(out.getLast().distance()==1,"False/covered water sample breaks contiguous route");
        tracker.release(A,H,23,null); before=out.size();
        tracker.sampleAnchor(A,D,24,new Point(20,0,0),4,false,true,false,out::add);
        check(out.size()==before,"Release ends anchored water pull");
        tracker.sampleAnchor(A,D,25,new Point(40,0,0),4,false,true,false,out::add);
        check(tracker.size()==0,"Teleport removes complete trajectory rather than clipping it");
        tracker.start(origin(true,false,false,true,false),true); out.clear();
        for(int i=1;i<=19;i++) tracker.sampleAnchor(A,D,i,new Point(i,0,0),4,false,true,false,out::add);
        tracker.sampleAnchor(A,D,20,new Point(19.5,0,0),4,false,true,false,out::add);
        tracker.sampleAnchor(A,D,20,new Point(20,0,0),4,false,true,false,true,out::add);
        check(out.getLast().distance()==20,"Successful release checkpoint includes later same-tick exact20 endpoint");
        tracker.release(A,H,20,null);
    }
    private static void grapples() {
        var tracker=new RodTrajectoryTracker(); var out=new ArrayList<RodOutcome>(); tracker.start(origin(true,false,false,false,true),true);
        for(int i=1;i<=10;i++) tracker.sampleAnchor(A,D,i,new Point(0,i,0),4,false,false,false,out::add);
        check(out.isEmpty(),"Midair height alone cannot prove supported roof");
        tracker.release(A,H,10,null);
        tracker.sampleAnchor(A,D,11,new Point(0,10,0),4,false,false,true,out::add);
        check(out.size()==1 && out.getFirst().kind()==RodOutcome.Kind.SUPPORTED_GRAPPLE_LANDING && out.getFirst().distance()==10,"Supported landing exact10 after release");
        check(tracker.size()==0,"Grapple ends on supported landing");
        tracker.start(origin(true,false,false,false,true),true); out.clear();
        for(int i=1;i<=10;i++) tracker.sampleAnchor(A,D,i,new Point(0,i,0),4,false,false,true,out::add);
        check(out.isEmpty(),"Supported stair walking cannot substitute airborne grapple");
    }
    private static void launches() {
        var tracker=new RodTrajectoryTracker(); var out=new ArrayList<RodOutcome>(); tracker.start(origin(false,true,false,false,true),true);
        tracker.release(A,H,0,ZERO);
        for(int i=1;i<=20;i++) tracker.sampleTarget(T,D,i,new Point(i,1,0),4,false,false,false,false,out::add);
        check(out.getLast().distance()==20,"Released hostile exact20 displacement");
        tracker.died(T,D,20,true,new Point(20,0,0),4,out::add);
        check(out.getLast().kind()==RodOutcome.Kind.LAUNCHED_HOSTILE_FATAL_FALL,"Fatal fall consumes bounded causal launch");
        int before=out.size(); tracker.died(T,D,20,true,new Point(20,0,0),4,out::add);
        check(out.size()==before,"Duplicate death cannot replay");
        tracker.start(origin(false,true,false,false,true),true); out.clear();
        tracker.sampleTarget(T,D,1,new Point(1,1,0),4,false,false,false,false,out::add);
        tracker.damaged(T,false); tracker.died(T,D,1,true,new Point(1,0,0),4,out::add);
        check(out.isEmpty(),"Intervening nonfall damage invalidates causal fall");
        tracker.start(origin(false,true,false,false,false),true); tracker.died(T,D,1,true,ZERO,4,out::add);
        check(out.isEmpty(),"Grounded incidental force is insufficient launch evidence");
        tracker.start(origin(false,true,false,false,true),true);
        tracker.sampleTarget(T,D,1,new Point(0,1,0),4,false,false,false,false,out::add);
        tracker.sampleTarget(T,D,2,ZERO,4,false,true,false,false,out::add);
        tracker.died(T,D,3,true,ZERO,4,out::add);
        check(out.isEmpty(),"Surviving landing cannot attribute unrelated later fall");
        tracker.start(origin(false,true,false,false,true),true);
        tracker.landed(T); tracker.died(T,D,1,true,ZERO,4,out::add);
        check(out.isEmpty(),"Accepted nonfatal fall ends launch causality before a same-tick second fall");
        tracker.start(origin(false,true,false,false,true),true);
        tracker.sampleTarget(T,D,1,ZERO,4,false,true,false,false,out::add);
        tracker.died(T,D,2,true,ZERO,4,out::add);
        check(out.isEmpty(),"Surviving grounded physics step expires unobserved opening kick before unrelated later fall");
    }
    private static void arrival() {
        var tracker=new RodTrajectoryTracker(); var out=new ArrayList<RodOutcome>(); tracker.start(origin(false,true,false,false,false),true);
        tracker.sampleTarget(T,D,1,ZERO,4,false,true,false,true,out::add);
        check(out.isEmpty(),"Player approaching stationary target cannot substitute causal pull");
        tracker.sampleTarget(T,D,2,new Point(1,0,0),4,false,true,false,false,out::add);
        check(out.isEmpty(),"Native range false rejects");
        tracker.sampleTarget(T,D,3,new Point(2,0,0),4,false,true,false,true,out::add);
        check(out.size()==1 && out.getFirst().kind()==RodOutcome.Kind.HOSTILE_REELED,"Same meaningfully pulled hostile range fact");
        tracker.removed(T); out.clear();
        tracker.sampleTarget(T,D,4,new Point(3,0,0),4,false,true,false,true,out::add);
        check(out.isEmpty(),"Unload/replacement rejects target substitution");
        tracker.start(origin(false,false,true,false,false),true);
        tracker.arrived(T,B,D,1,new Point(1,0,0),4,out::add);
        check(out.isEmpty(),"Nearby teammate item touch rejected");
        tracker.arrived(T,A,D,1,new Point(1,0,0),4,out::add);
        check(out.size()==1 && out.getFirst().kind()==RodOutcome.Kind.ITEM_REELED,"Original actor native item range is confirming evidence");
        out.clear(); tracker.start(origin(false,false,true,false,false),true); tracker.release(A,H,0,ZERO);
        tracker.sampleTarget(T,D,1,new Point(1,0,0),4,false,true,true,false,out::add);
        check(tracker.size()==1,"Item water/ground does not impose hostile launch reset policy");
        tracker.arrived(T,A,D,2,new Point(2,0,0),4,out::add);
        check(out.size()==1 && out.getFirst().kind()==RodOutcome.Kind.ITEM_REELED,"Released residual item pull can enter pickup range");
    }
    private static void lifecycle() {
        for (String mode:List.of("dimension","gap","mounted","water","hookFailure","logout","expiry","backwards","creative")) {
            var tracker=new RodTrajectoryTracker(); var out=new ArrayList<RodOutcome>(); var origin=origin(false,true,false,false,true); tracker.start(origin,true);
            switch(mode) {
                case "dimension" -> tracker.sampleTarget(T,"minecraft:the_nether",1,ZERO,4,false,false,false,false,out::add);
                case "gap" -> tracker.sampleTarget(T,D,2,ZERO,4,false,false,false,false,out::add);
                case "mounted" -> tracker.sampleTarget(T,D,1,ZERO,4,true,false,false,false,out::add);
                case "water" -> {
                    tracker.sampleTarget(T,D,1,ZERO,4,false,false,true,false,out::add);
                    check(tracker.size()==1,"Water invalidates hostile launch while retaining the still-held reel session");
                    tracker.sampleTarget(T,D,2,new Point(1,0,0),4,false,false,true,true,out::add);
                    check(out.size()==1 && out.getFirst().kind()==RodOutcome.Kind.HOSTILE_REELED,"Same hostile can still be genuinely reeled into range through water");
                    out.clear(); tracker.died(T,D,2,true,new Point(1,0,0),4,out::add);
                    check(out.isEmpty(),"Water-invalidated launch cannot earn a later fatal-fall outcome");
                }
                case "hookFailure" -> tracker.hookRemoved(H);
                case "logout" -> tracker.forget(A);
                case "expiry" -> tracker.prune(200);
                case "backwards" -> tracker.prune(-1);
                case "creative" -> tracker.start(origin,false);
            }
            check(tracker.size()==0,"Explicit reset: "+mode);
        }
        var tracker=new RodTrajectoryTracker(); var origin=origin(false,true,false,false,true); tracker.start(origin,true);
        tracker.release(A,H,0,ZERO); tracker.hookRemoved(H);
        check(tracker.size()==1,"Successful release survives native bobber removal");
        tracker.start(new Origin("competing",B,H,T,D,0,ZERO,ZERO,false,true,false,8,false,true),true);
        check(tracker.targets(A).isEmpty() && tracker.targets(B).size()==1,"Competing launch transfers causal ownership");
    }
    private static void bounds() {
        var tracker=new RodTrajectoryTracker();
        for(int i=0;i<MAX_SESSIONS;i++) check(tracker.start(new Origin("bound-"+i,new UUID(20,i),H,new UUID(30,i),D,0,ZERO,ZERO,false,true,false,8,false,true),true),"Bound accepts active slot");
        check(!tracker.start(origin(false,true,false,false,true),true) && tracker.size()==MAX_SESSIONS,"Capacity fails closed without evicting live attribution");
        tracker.prune(199); check(tracker.size()==MAX_SESSIONS,"One tick before timeout remains active");
        tracker.prune(200); check(tracker.size()==0,"Exact timeout clears all");
    }
    private static void provenance() {
        var tracker=new RodTrajectoryTracker();
        tracker.start(origin(false,true,false,false,true),true);
        check(tracker.eligibleForce(A,T,true) && tracker.size()==1,"Eligible continuing positive force retains its session");
        check(!tracker.eligibleForce(A,T,false) && tracker.size()==0,"Ineligible continuing force clears original survival session immediately");
        check(tracker.eligibleForce(A,T,true) && tracker.size()==0,"Later eligibility cannot reconstruct the erased action history");
        tracker.start(origin(true,false,false,false,true),true);
        tracker.start(new Origin("other-owner",B,H,T,D,0,ZERO,ZERO,false,true,false,8,false,true),true);
        tracker.eligibleForce(A,T,false);
        check(tracker.size()==0,"Ineligible competing force clears actor sessions and another actor's old target attribution");
        UUID second=new UUID(8,8);
        tracker.start(origin(false,false,true,false,false),true);
        tracker.start(new Origin("second-item",B,new UUID(9,9),second,D,0,ZERO,ZERO,false,false,true,8,false,false),true);
        check(!tracker.merged(T,second,8,1,8,1) && tracker.size()==2,"No-op or canceled merge preserves both live item histories");
        check(tracker.merged(T,second,8,1,9,0) && tracker.size()==0,"Committed merge invalidates receiver survivor and donor UUID histories");
        tracker.start(origin(false,false,true,false,false),true);
        check(!tracker.pickedUp(T,B,8,8) && tracker.size()==1,"Other actor's full inventory/no-op touch does not erase the rod cause");
        check(!tracker.pickedUp(T,A,8,7) && tracker.size()==1,"Original actor's consumed count does not become teammate attribution");
        check(tracker.pickedUp(T,B,8,7) && tracker.size()==0,"Actual different-actor partial pickup invalidates surviving item UUID");
    }
    private static void check(boolean result,String description) { checks++; if(!result) throw new AssertionError(description); }
}
