package com.mistaboom.essence_ascendance.gameplay;

import java.util.UUID;

/** Example second consumer: entity-type sequence queries have no achievement or award dependency. */
public final class CombatSequenceTest {
    private static int checks;
    private static final UUID PLAYER = UUID.randomUUID(), OTHER = UUID.randomUUID();
    public static void main(String[] args) {
        CombatSequences history = new CombatSequences();
        var first = fact(PLAYER,"swipe",UUID.randomUUID(),"zombie",10);
        check(history.killed(first,10).hostileKills()==1,"first victim");
        check(!history.killed(first,10).accepted(),"duplicate callback");
        check(history.killed(fact(PLAYER,"swipe",UUID.randomUUID(),"zombie",10),10).hostileKills()==2,"second victim same tick");
        check(history.repeatedType(PLAYER,"zombie",2,10),"future same-type consumer");
        check(!history.distinctTypes(PLAYER,2,10),"repeated types not distinct");
        check(history.killed(fact(PLAYER,"swipe",UUID.randomUUID(),"skeleton",10),10).hostileKills()==3,"same action third death");
        check(history.distinctTypes(PLAYER,2,10),"future distinct-type consumer ordered suffix");
        check(history.recent(PLAYER,10).get(0).order()<history.recent(PLAYER,10).get(1).order(),"deterministic same tick commit order");
        check(history.killed(fact(OTHER,"swipe",UUID.randomUUID(),"zombie",10),10).hostileKills()==1,"actor isolation");
        check(history.killed(fact(PLAYER,"next",UUID.randomUUID(),"zombie",10),10).hostileKills()==1,"actions do not merge");
        check(!history.killed(fact(PLAYER,"next",first.victim(),"zombie",10),10).accepted(),"victim dedup independent of action key");
        for(int i=0;i<CombatSequences.MAX_ACTIONS*2;i++)
            check(!history.killed(fact(PLAYER,"duplicate-action"+i,first.victim(),"zombie",10),10).accepted(),"duplicate victim cannot allocate action history");
        check(history.killed(fact(PLAYER,"after-duplicates",UUID.randomUUID(),"zombie",10),10).accepted(),"rejected cross-action duplicates cannot exhaust legitimate action capacity");
        check(history.recent(PLAYER,209).size()==5,"within expiration");
        check(history.recent(PLAYER,210).isEmpty(),"expiration at exact boundary");
        check(!history.killed(first,210).accepted(),"old receipt rejects");
        check(!history.killed(fact(PLAYER,"future",UUID.randomUUID(),"zombie",212),211).accepted(),"future receipt rejects");
        history.killed(fact(PLAYER,"new",UUID.randomUUID(),"zombie",300),300);
        var dimension = new CombatOutcome.Context("dimension",PLAYER,UUID.randomUUID(),"skeleton",true,"minecraft:the_nether",301,CombatOutcome.Weapon.UNKNOWN);
        history.killed(dimension,301);
        check(history.recent(PLAYER,301).size()==1,"dimension resets sequence");
        history.forget(PLAYER);
        check(history.recent(PLAYER,301).isEmpty(),"death logout reload explicit reset");
        for(int i=0;i<CombatSequences.MAX_VICTIMS;i++)
            check(history.killed(fact(PLAYER,"bounded",UUID.randomUUID(),"zombie",400),400).accepted(),"victim within cap");
        check(!history.killed(fact(PLAYER,"bounded",UUID.randomUUID(),"zombie",400),400).accepted(),"victim cap fails closed");
        check(history.recent(PLAYER,400).size()==CombatSequences.MAX_KILLS,"bounded sequence retained separately");
        history.clear();
        for(int i=0;i<CombatSequences.MAX_ACTIONS;i++) history.killed(fact(PLAYER,"action"+i,UUID.randomUUID(),"zombie",500),500);
        check(!history.killed(fact(PLAYER,"overflow",UUID.randomUUID(),"zombie",500),500).accepted(),"action cap fails closed");
        history.clear();
        for(int i=0;i<CombatSequences.MAX_ACTORS;i++) history.killed(fact(UUID.randomUUID(),"action",UUID.randomUUID(),"zombie",600),600);
        check(!history.killed(fact(PLAYER,"overflow",UUID.randomUUID(),"zombie",600),600).accepted(),"actor cap fails closed");
        check(history.recent(OTHER,600).isEmpty(),"clear drops prior world history");
        repeatedDeliveryWorkload();
        independentConsumer();
        System.out.println("Combat sequence invariants passed: "+checks);
    }
    private static void repeatedDeliveryWorkload() {
        CombatSequences history=new CombatSequences();
        var original=fact(PLAYER,"first",new UUID(90,1),"minecraft:zombie",1000);
        history.killed(original,1000);
        long began=System.nanoTime();
        for(int i=0;i<100_000;i++)
            check(!history.killed(fact(PLAYER,"rejected-"+i,original.victim(),original.victimType(),1000),1000).accepted(),
                    "repeated duplicate cannot allocate action/victim/ordered state");
        for(int i=1;i<CombatSequences.MAX_ACTIONS;i++)
            check(history.killed(fact(PLAYER,"valid-"+i,new UUID(90,i+1),"minecraft:skeleton",1000),1000).accepted(),
                    "all remaining bounded action slots survive duplicate storm");
        check(!history.killed(fact(PLAYER,"overflow",new UUID(90,1000),"minecraft:zombie",1000),1000).accepted(),"exact action cap remains enforced");
        check(history.recent(PLAYER,1000).size()==CombatSequences.MAX_KILLS,"ordered history remains at 32 after 100064 deliveries");
        check(history.recent(PLAYER,1200).isEmpty(),"idle workload expires completely at boundary");
        check(history.killed(fact(PLAYER,"fresh",new UUID(90,1000),"minecraft:zombie",1200),1200).accepted(),"expired groups release actual capacity");
        System.out.println("Combat sequence repeated delivery workload: 100000 rejected duplicates, 64 live action/victim entries, 32 ordered kills, empty at expiry; "+((System.nanoTime()-began)/1_000_000)+" ms");
    }
    /** Test-only second gameplay consumer. It depends on scalar combat queries and has no award/ID API. */
    private static final class EntityTypeConsumer {
        private final CombatSequences history;
        boolean repeated,distinct;
        EntityTypeConsumer(CombatSequences history){this.history=history;}
        void observed(CombatOutcome.Context committed,long now){
            repeated=history.repeatedType(committed.actor(),committed.victimType(),2,now);
            distinct=history.distinctTypes(committed.actor(),2,now);
        }
    }
    private static void independentConsumer() {
        CombatSequences history=new CombatSequences();var observer=new EntityTypeConsumer(history);
        for(int i=0;i<3;i++){
            var committed=fact(PLAYER,"consumer-"+i,new UUID(91,i),i==2?"minecraft:skeleton":"minecraft:zombie",2000);
            check(history.killed(committed,2000).accepted(),"native owner commits before second-consumer notification");
            observer.observed(committed,2000);
            check(observer.repeated==(i==1)&&observer.distinct==(i==2),"second consumer distinguishes repeated and different entity-type suffixes");
        }
        check(history.recent(PLAYER,2200).isEmpty(),"consumer cannot retain an expired combat window through queries");
    }
    private static CombatOutcome.Context fact(UUID actor,String action,UUID victim,String type,long tick) {
        return new CombatOutcome.Context(action,actor,victim,type,true,"minecraft:overworld",tick,CombatOutcome.Weapon.UNKNOWN);
    }
    private static void check(boolean valid,String message) { checks++; if(!valid)throw new AssertionError(message); }
}
