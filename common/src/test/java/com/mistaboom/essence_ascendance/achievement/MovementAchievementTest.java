package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.MovementOutcome;
import java.util.*;

/** Actual threshold consumer, administrative-revoke checkpoints and bounded actor/receipt rejection. */
public final class MovementAchievementTest {
    private static int checks,next;
    private static final UUID ALICE=new UUID(7,1),BOB=new UUID(7,2);
    private static MovementOutcome.Context c(UUID actor,long tick){return new MovementOutcome.Context("movement-rule-"+(++next),actor,"minecraft:overworld",tick);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        thresholds();administration();receipts();bounds();
        System.out.println("Movement achievement rules: "+checks+" checks passed");
    }
    private static Set<Integer> consume(MovementOutcome f){var out=new HashSet<Integer>();check(new MovementAchievementRules().consume(f,50,true,out::add),"Valid movement fact accepted");return out;}
    private static void thresholds(){
        check(consume(new MovementOutcome.Glide(c(ALICE,50),"g",2,1000)).equals(Set.of(99)),"Two boosts earn distance but not triple");
        check(consume(new MovementOutcome.Glide(c(ALICE,50),"g",3,999.999)).equals(Set.of(95)),"Three boosts below distance threshold");
        check(consume(new MovementOutcome.Glide(c(ALICE,50),"g",3,1000)).equals(Set.of(95,99)),"Exact glide thresholds");
        check(consume(new MovementOutcome.Glide(c(ALICE,50),"g",0,1000)).isEmpty(),"Unboosted glide rejects");
        check(consume(new MovementOutcome.Sprint(c(ALICE,50),"s",499.999,4,false)).isEmpty(),"Below sprint and vault thresholds");
        check(consume(new MovementOutcome.Sprint(c(ALICE,50),"s",500,5,true)).equals(Set.of(101,201,216)),"Exact sprint/vault plus Rush qualifies");
        check(consume(new MovementOutcome.Surface(c(ALICE,50),"w",31.999)).isEmpty(),"Below water threshold rejects");
        check(consume(new MovementOutcome.Surface(c(ALICE,50),"w",32)).equals(Set.of(102)),"Exact water threshold qualifies");
        for(double cap:new double[]{0.01,0.2,0.4,2.5}){
            check(consume(new MovementOutcome.Momentum(c(ALICE,50),Math.nextDown(cap),cap)).isEmpty(),"Below generated cap rejects "+cap);
            check(consume(new MovementOutcome.Momentum(c(ALICE,50),cap,cap)).equals(Set.of(100)),"Generated effective cap qualifies "+cap);
        }
        check(consume(new MovementOutcome.Momentum(c(ALICE,50),0,0)).isEmpty(),"Zero configured bonus cannot earn cap");
    }
    private static void administration(){
        var rules=new MovementAchievementRules();var out=new HashSet<Integer>();
        var initial=new MovementOutcome.Glide(c(ALICE,50),"g1",3,1000);rules.consume(initial,50,true,out::add);
        out.clear();rules.consume(new MovementOutcome.Glide(c(ALICE,50),"g1",4,1010),50,true,out::add);
        check(out.isEmpty(),"Consumed session cannot restore a revoked completion from later same-glide checkpoints");
        rules.consume(new MovementOutcome.Glide(c(ALICE,50),"g2",3,1000),50,true,out::add);
        check(out.equals(Set.of(95,99)),"Fresh glide may re-earn after revoke");
        out.clear();rules.consume(new MovementOutcome.Momentum(c(ALICE,50),0.4,0.4),50,true,out::add);out.clear();
        rules.consume(new MovementOutcome.Momentum(c(ALICE,50),0.4,0.4),50,true,out::add);
        check(out.isEmpty(),"Unchanged maximum cannot replay revoke");
        rules.consume(new MovementOutcome.Momentum(c(ALICE,50),0,0),50,true,out::add);
        rules.consume(new MovementOutcome.Momentum(c(ALICE,50),0.4,0.4),50,true,out::add);
        check(out.equals(Set.of(100)),"Fresh cap transition may re-earn");
        out.clear();rules.consume(new MovementOutcome.AirborneHit(c(ALICE,50),"air1","hit1"),50,true,out::add);out.clear();
        rules.consume(new MovementOutcome.AirborneHit(c(ALICE,50),"air1","hit2"),50,true,out::add);
        check(out.isEmpty(),"Second hit in same airborne session cannot replay revoke");
    }
    private static void receipts(){
        var rules=new MovementAchievementRules();var out=new HashSet<Integer>();
        var fact=new MovementOutcome.Surface(c(ALICE,50),"w1",32);
        check(rules.consume(fact,50,true,out::add),"Initial committed receipt accepted");out.clear();
        check(!rules.consume(fact,50,true,out::add)&&out.isEmpty(),"Duplicate receipt rejected");
        check(!rules.consume(new MovementOutcome.Surface(c(ALICE,51),"future",32),50,true,out::add),"Future callback rejected");
        check(!rules.consume(new MovementOutcome.Surface(c(ALICE,0),"old",32),200,true,out::add),"Exact receipt expiry rejects");
        check(!rules.consume(new MovementOutcome.Surface(c(ALICE,50),"creative",32),50,false,out::add),"Creative/ineligible delivery rejects");
        rules.consume(new MovementOutcome.Surface(c(BOB,50),"w1",32),50,true,out::add);
        check(out.equals(Set.of(102)),"Distinct actor is isolated even with same session string");
    }
    private static void bounds(){
        var rules=new MovementAchievementRules();var out=new HashSet<Integer>();
        for(int i=0;i<MovementAchievementRules.MAX_ACTORS;i++)check(rules.consume(new MovementOutcome.Surface(c(new UUID(9,i),50),"w",1),50,true,out::add),"Within actor capacity accepted");
        check(!rules.consume(new MovementOutcome.Surface(c(ALICE,50),"overflow",32),50,true,out::add),"Actor overflow fails closed");
        rules.forget(new UUID(9,0));
        check(rules.consume(new MovementOutcome.Surface(c(ALICE,50),"new",32),50,true,out::add)&&out.equals(Set.of(102)),"Lifecycle forget releases bounded capacity");
        reject(()->new MovementOutcome.Surface(c(ALICE,50),"w",Double.NaN));
        reject(()->new MovementOutcome.Glide(c(ALICE,50),"g",1025,0));
        reject(()->new MovementOutcome.Sprint(c(ALICE,50),"s",-1,0,false));
        reject(()->new MovementOutcome.Momentum(c(ALICE,50),1,0.4));
    }
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Invalid fact accepted");}catch(IllegalArgumentException expected){checks++;}}
}
