package com.mistaboom.essence_ascendance.gameplay;

import java.util.*;
import com.mistaboom.essence_ascendance.achievement.MovementAchievementRules;

/** Executable trajectories feed the actual reusable session engine and achievement consumer. */
public final class MovementSessionTest {
    private static int checks,identity;
    private static final UUID ACTOR=new UUID(5,1);
    private static final String WORLD="minecraft:overworld";
    private static String id(){return "movement-test-"+(++identity);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static MovementSessions.Position p(double x,double y,double z){return new MovementSessions.Position(x,y,z);}
    private static final class Run {
        final List<MovementOutcome> facts=new ArrayList<>();
        final Set<Integer> awards=new HashSet<>();
        final MovementAchievementRules rules=new MovementAchievementRules();
        final MovementSessions state=new MovementSessions(ACTOR,MovementSessionTest::id,f->{facts.add(f);rules.consume(f,f.context().committedTick(),true,awards::add);});
        void sample(long tick,double before,double after,boolean observed,boolean sprint,double momentum,boolean water,boolean wings,boolean grounded){
            state.sample(new MovementSessions.Frame(WORLD,tick,p(before,100,0),p(after,100,0),observed,Math.abs(after-before),Math.abs(after-before),64,true,
                    !grounded,grounded,wings,sprint,momentum,0.4,water,true,true));
        }
    }
    public static void main(String[] args){
        sampler();glides();sprints();surface();airborne();resets();baseline();discontinuityBaseline();
        System.out.println("Movement session trajectories: "+checks+" checks passed");
    }
    private static void sampler(){
        var t=new MovementSessionTracker();t.begin("s",WORLD,0,0,0,0);
        var a=t.sample(WORLD,1,10,0,0,64,true);
        check(a.accepted()&&a.horizontalDisplacement()==10&&a.horizontalPath()==10,"Accepted first segment");
        var duplicate=t.sample(WORLD,1,10,0,0,64,true);
        check(!duplicate.accepted()&&!duplicate.discontinuity()&&duplicate.horizontalPath()==10,"Same tick duplicate is ignored");
        var back=t.sample(WORLD,2,0,0,0,64,true);
        check(back.horizontalPath()==20&&back.horizontalDisplacement()==0,"Path and displacement differ on return");
        check(t.sample(WORLD,3,0,0,1,64,false).horizontalPath()==20,"Absent accepted sample cannot grant distance");
        var bad=t.sample(WORLD,4,0,0,70,64,true);
        check(bad.discontinuity()&&!t.active(),"Teleport rejects whole segment without clipping");
        t.begin("s2",WORLD,10,0,0,0);
        check(t.sample("minecraft:the_nether",11,1,0,0,64,true).discontinuity(),"Dimension transition ends trajectory");
        t.begin("s3",WORLD,10,0,0,0);
        check(t.sample(WORLD,9,1,0,0,64,true).discontinuity(),"Backward clock ends trajectory");
        t.begin("s4",WORLD,0,0,0,0);
        var bent=t.sample(WORLD,1,3,4,0,64,true,5,7);
        check(bent.horizontalStep()==5&&bent.spatialStep()==7&&bent.horizontalDisplacement()==3&&bent.verticalDisplacement()==4,"Bent accepted path retained separately");
        t.begin("s5",WORLD,0,0,0,0);
        check(t.sample(WORLD,1,Double.NaN,0,0,64,true).discontinuity(),"Nonfinite positions reject");
        t.begin("s6",WORLD,0,0,0,0);
        check(t.sample(WORLD,1,3,4,0,64,true,2,4).discontinuity(),"Path shorter than observed endpoint chord rejects");
    }
    private static void glides(){
        var r=new Run();r.state.beginGlide(WORLD,0,p(0,100,0));
        for(int i=1;i<=999;i++)r.sample(i,i-1,i,true,false,0,false,true,false);
        check(!r.awards.contains(99),"999 glide blocks without boost remain incomplete");
        r.sample(1000,999,1000,true,false,0,false,true,false);
        check(!r.awards.contains(99),"Exact 1000 requires real boost checkpoint");
        r.state.boost(WORLD,1001);
        check(r.awards.contains(99),"First real boost after 1000 qualifies in same glide");
        r.state.boost(WORLD,1001);
        r.state.boost(WORLD,1002);
        check(!r.awards.contains(95),"Duplicate boost callback and two distinct activations are below three");
        r.state.boost(WORLD,1003);
        check(r.awards.contains(95),"Third distinct successful boost crosses threshold");
        var threeD=new Run();threeD.state.beginGlide(WORLD,0,p(0,100,0));threeD.state.boost(WORLD,1);
        threeD.state.sample(new MovementSessions.Frame(WORLD,2,p(0,100,0),p(0,110,0),true,0,10,64,true,true,false,true,false,0,0.4,false,true,false));
        var glide=(MovementOutcome.Glide)threeD.facts.get(threeD.facts.size()-1);
        check(glide.spatialPath()==10,"Glide uses spatial path, including vertical travel");
        var landed=new Run();landed.state.beginGlide(WORLD,0,p(0,100,0));landed.state.boost(WORLD,1);landed.state.boost(WORLD,2);
        landed.sample(3,0,0,true,false,0,false,false,true);landed.state.boost(WORLD,4);
        check(!landed.awards.contains(95),"Landing ends boost collection");
        landed.state.beginGlide(WORLD,5,p(0,100,0));landed.state.boost(WORLD,6);
        check(!landed.awards.contains(95),"New glide does not inherit two previous boosts");
    }
    private static void sprints(){
        var r=new Run();
        for(int i=1;i<=499;i++)r.sample(i,i-1,i,true,true,0.2,false,false,true);
        check(!r.awards.contains(216),"499 continuous horizontal blocks are below 500");
        r.sample(500,499,500,true,true,0.2,false,false,true);
        check(r.awards.contains(216),"Exactly 500 blocks with live momentum qualifies");
        var stop=new Run();for(int i=1;i<=250;i++)stop.sample(i,i-1,i,true,true,0.2,false,false,true);
        stop.sample(251,250,250,true,true,0.2,false,false,true);
        for(int i=252;i<=501;i++)stop.sample(i,i-2,i-1,true,true,0.2,false,false,true);
        check(!stop.awards.contains(216),"Accepted zero movement splits 250+250 sprint path");
        var wait=new Run();wait.sample(1,0,1,true,true,0.2,false,false,true);wait.sample(2,1,1,false,true,0.2,false,false,true);wait.sample(3,1,2,true,true,0.2,false,false,true);
        var last=(MovementOutcome.Sprint)wait.facts.get(wait.facts.size()-1);
        check(last.horizontalPath()==2,"No packet alone does not imply sprint stop");
        var buff=new Run();for(int i=1;i<=250;i++)buff.sample(i,i-1,i,true,true,0.2,false,false,true);
        buff.sample(251,250,251,true,true,0,false,false,true);for(int i=252;i<=501;i++)buff.sample(i,i-1,i,true,true,0.2,false,false,true);
        check(!buff.awards.contains(216),"Momentum loss splits continuous challenge");
        var vault=new Run();vault.sample(1,0,1,true,true,0.2,false,false,true);
        for(int i=2;i<=5;i++){vault.state.vault(i);vault.sample(i,i-1,i,true,true,0.2,false,false,true);}
        check(!vault.awards.contains(101),"Four distinct vaults are below five");
        vault.state.vault(2);vault.sample(6,5,6,true,true,0.2,false,false,true);
        check(!vault.awards.contains(101),"Same obstacle cannot be counted twice in sprint");
        vault.state.vault(7);vault.sample(7,6,7,true,true,0.2,false,false,true);
        check(vault.awards.contains(101),"Fifth distinct accepted obstacle earns vault achievement");
        var rush=new Run();rush.sample(1,0,1,true,true,0.2,false,false,true);
        check(!rush.awards.contains(201),"Sprint during unproven timer is insufficient");
        rush.state.rush(4);rush.sample(2,1,2,true,true,0.2,false,false,true);
        check(rush.awards.contains(201),"Kill activation plus accepted protected sprint qualifies");
        var expiry=new Run();expiry.state.rush(2);expiry.sample(2,0,1,true,true,0.2,false,false,true);
        check(!expiry.awards.contains(201),"Exact Rush deadline rejects");
    }
    private static void surface(){
        var r=new Run();for(int i=1;i<=31;i++)r.sample(i,i-1,i,true,true,0,true,false,true);
        check(!r.awards.contains(102),"31 supported exposed-water blocks below32");
        r.sample(32,31,32,true,true,0,true,false,true);
        check(r.awards.contains(102),"32 supported water blocks qualifies without inventing Running Momentum requirement");
        var gap=new Run();for(int i=1;i<=16;i++)gap.sample(i,i-1,i,true,true,0,true,false,true);
        gap.sample(17,16,17,true,true,0,false,false,true);for(int i=18;i<=33;i++)gap.sample(i,i-1,i,true,true,0,true,false,true);
        check(!gap.awards.contains(102),"False-water route sample breaks water streak");
        var walk=new Run();for(int i=1;i<=32;i++)walk.sample(i,i-1,i,true,false,0,true,false,true);
        check(!walk.awards.contains(102),"Rod-enabled walking surface cannot replace sprint");
        var independent=new Run();
        for(int i=1;i<=32;i++)independent.state.sample(new MovementSessions.Frame(WORLD,i,p(i-1,100,0),p(i,100,0),true,1,1,64,true,
                false,true,false,true,0.2,0.4,true,false,true));
        check(independent.awards.contains(102)&&!independent.awards.contains(201),"Actual surface support remains usable when momentum movement evidence is unavailable; unproven forced momentum cannot earn Rush sprint");
    }
    private static void airborne(){
        var r=new Run();r.state.hit(WORLD,0,"hit-before-glide");check(!r.awards.contains(145),"Hit before glide start rejects");
        r.state.beginGlide(WORLD,1,p(0,100,0));r.sample(2,0,1,true,false,0,false,false,false);r.state.hit(WORLD,3,"accepted-glaive");
        check(r.awards.contains(145),"Airborne checkpoint survives Wings exit until grounding");
        var land=new Run();land.state.beginGlide(WORLD,0,p(0,100,0));land.sample(1,0,0,true,false,0,false,false,true);land.state.hit(WORLD,2,"late-hit");
        check(!land.awards.contains(145),"Landing cancels airborne ordering checkpoint");
    }
    private static void resets(){
        var r=new Run();r.state.beginGlide(WORLD,0,p(0,100,0));r.state.boost(WORLD,1);r.state.boost(WORLD,2);
        r.sample(3,0,100,true,false,0,false,true,false);r.state.boost(WORLD,4);
        check(!r.awards.contains(95),"Teleport discards causal glide session");
        var restart=new Run();restart.state.beginGlide(WORLD,0,p(0,100,0));restart.state.boost(WORLD,1);restart.state.stop();restart.state.boost(WORLD,2);
        check(!restart.state.active(),"Logout/death/restart stop leaves no session or replay");
        var max=new Run();max.sample(1,0,1,true,true,Math.nextDown(0.4),false,false,true);
        check(!max.awards.contains(100),"Below live configured cap rejects");max.sample(2,1,2,true,true,0.4,false,false,true);
        check(max.awards.contains(100),"Exact effective cap qualifies");
    }
    private static void baseline(){
        var r=new Run();r.state.beginGlide(WORLD,0,p(0,100,0));r.state.boost(WORLD,1);r.state.boost(WORLD,2);r.state.rush(100);
        r.state.baselineMomentum(WORLD,3,0.4,0.4);r.awards.clear();r.facts.clear();
        r.sample(4,0,1,true,true,0.4,false,false,true);
        check(!r.awards.contains(100)&&!r.awards.contains(201),"Administrative/Creative full reservoir baseline cannot become a fresh survival maximum or Rush activation");
        r.state.boost(WORLD,5);r.state.hit(WORLD,5,"stale-airborne");
        check(!r.awards.contains(95)&&!r.awards.contains(145),"Ineligible baseline clears glide and airborne causal challenges");
        r.sample(5,1,2,true,true,0.2,false,false,true);r.sample(6,2,3,true,true,0.4,false,false,true);
        check(r.awards.contains(100),"A later legitimate below-cap to cap transition can earn maximum after baseline");
        r.state.baselineMomentum(WORLD,7,0,0.4);check(!r.state.active(),"Zero baseline leaves no active challenge or causal timer");
        var capacity=new Run();capacity.state.baselineMaximum(0.4,0.4);capacity.sample(1,0,1,true,true,0.4,false,false,true);
        check(!capacity.awards.contains(100),"Unknown current maximum after lost tracking capacity is baselined without inferred history");
        capacity.state.rush(100);capacity.sample(2,1,2,true,true,0.4,false,false,true);
        check(!capacity.awards.contains(100),"Fresh Rush activation at an already-full baseline does not invent a rising maximum");
        capacity.state.maximumWasBelow();capacity.sample(3,2,3,true,true,0.4,false,false,true);
        check(capacity.awards.contains(100),"Affirmative successful fill from below cap admits a new maximum transition after capacity baseline");
    }
    private static void discontinuityBaseline(){
        for(int failure=0;failure<4;failure++){
            var r=new Run();r.sample(10,0,1,true,true,0.4,false,false,true);
            check(r.awards.contains(100),"Fixture first reaches the configured momentum maximum");
            r.awards.clear();r.facts.clear();
            String dimension=failure==1?"minecraft:the_nether":WORLD;
            long tick=failure==2?9:11;
            double after=failure==0?100:2;
            r.state.sample(new MovementSessions.Frame(dimension,tick,p(1,100,0),p(after,100,0),true,
                    after-1,after-1,64,failure!=3,false,true,false,true,0.4,0.4,false,true,true));
            r.state.sample(new MovementSessions.Frame(dimension,12,p(after,100,0),p(after+1,100,0),true,
                    1,1,64,true,false,true,false,true,0.4,0.4,false,true,true));
            check(!r.awards.contains(100)&&r.facts.stream().noneMatch(f->f instanceof MovementOutcome.Momentum),
                    "Rejected route, dimension, backwards time or ineligible frame cannot fabricate a new maximum fact: "+failure);
            r.state.sample(new MovementSessions.Frame(dimension,13,p(after+1,100,0),p(after+2,100,0),true,
                    1,1,64,true,false,true,false,true,0.2,0.4,false,true,true));
            r.state.sample(new MovementSessions.Frame(dimension,14,p(after+2,100,0),p(after+3,100,0),true,
                    1,1,64,true,false,true,false,true,0.4,0.4,false,true,true));
            check(r.awards.contains(100),"Fresh below-cap transition remains earnable after rejected movement: "+failure);
        }
    }
}
