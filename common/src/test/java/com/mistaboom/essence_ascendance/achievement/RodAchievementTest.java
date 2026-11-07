package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.RodOutcome;
import java.util.*;

public final class RodAchievementTest {
    private static int checks,sequence;
    private static final UUID A=new UUID(1,1), T=new UUID(2,2);
    public static void main(String[] args) {
        boundary(RodOutcome.Kind.ANCHORED_WATER_DISPLACEMENT,20,1);
        boundary(RodOutcome.Kind.RELEASED_HOSTILE_DISPLACEMENT,20,3);
        boundary(RodOutcome.Kind.SUPPORTED_GRAPPLE_LANDING,10,5);
        for (var kind:List.of(RodOutcome.Kind.HOSTILE_REELED,RodOutcome.Kind.ITEM_REELED)) {
            check(awards(fact(kind,1,Math.nextDown(8d))).isEmpty(),"Below eight initial range rejects");
            check(awards(fact(kind,1,8)).equals(Set.of(kind==RodOutcome.Kind.HOSTILE_REELED ? 4 : 7)),"Eight inclusive initial range");
        }
        check(awards(fact(RodOutcome.Kind.LAUNCHED_HOSTILE_FATAL_FALL,0,0)).equals(Set.of(2)),"Confirmed causal native fatal fall");
        var rules=new RodAchievementRules(); var earned=new HashSet<Integer>(); var fact=fact(RodOutcome.Kind.ANCHORED_WATER_DISPLACEMENT,20,0);
        check(rules.consume(fact,10,true,earned::add),"Fresh success consumed"); earned.clear();
        check(!rules.consume(fact,10,true,earned::add) && earned.isEmpty(),"Duplicate receipt cannot restore revoke");
        var later=new RodOutcome(fact.actionId(),A,T,"minecraft:overworld",11,fact.kind(),21,0);
        check(!rules.consume(later,11,true,earned::add),"Same session crossing cannot restore revoke");
        check(!new RodAchievementRules().consume(fact,10,false,earned::add),"Creative/ineligible outcome rejected");
        check(!new RodAchievementRules().consume(fact,210,true,earned::add),"Expired outcome rejected at exact200");
        check(!new RodAchievementRules().consume(fact,9,true,earned::add),"Future outcome rejected");
        check(awards(fact(RodOutcome.Kind.ANCHORED_WATER_DISPLACEMENT,Double.NaN,0)).isEmpty(),"Nonfinite metric rejected");
        System.out.println("Rod achievement rules: "+checks+" checks passed");
    }
    private static void boundary(RodOutcome.Kind kind,double threshold,int id) {
        check(awards(fact(kind,Math.nextDown(threshold),0)).isEmpty(),"Below exact threshold "+id);
        check(awards(fact(kind,threshold,0)).equals(Set.of(id)),"Exact threshold "+id);
        check(awards(fact(kind,Math.nextUp(threshold),0)).equals(Set.of(id)),"Above threshold "+id);
    }
    private static RodOutcome fact(RodOutcome.Kind kind,double distance,double range) {
        return new RodOutcome("rod-"+(++sequence),A,T,"minecraft:overworld",10,kind,distance,range);
    }
    private static Set<Integer> awards(RodOutcome fact) { Set<Integer> ids=new HashSet<>(); new RodAchievementRules().consume(fact,10,true,ids::add); return ids; }
    private static void check(boolean result,String description) { checks++; if (!result) throw new AssertionError(description); }
}
