package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.gameplay.SurvivalOutcome;
import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Real rule consumers and player serialization. Native skill success/cooldowns have a separate server fixture. */
public final class SurvivalAchievementTest {
    private static int checks, next;
    private static final long TICK = 50;
    private static final UUID ALICE = new UUID(16,1), BOB = new UUID(16,2), WITCH = new UUID(16,3), OTHER = new UUID(16,4);
    private static final String[] FOODS = {"minecraft:apple", "minecraft:bread", "minecraft:carrot", "minecraft:potato", "minecraft:cooked_beef", "minecraft:cooked_porkchop"};
    private static SurvivalOutcome.Context c(UUID actor, long tick) {
        return new SurvivalOutcome.Context("survival-rule-" + ++next, actor, "minecraft:overworld", tick);
    }
    private static SurvivalOutcome.Context c() { return c(ALICE, TICK); }
    private static final class Harness {
        final SurvivalAchievementRules rules = new SurvivalAchievementRules();
        AchievementProgress state = new AchievementProgress();
        final Set<Integer> awards = new HashSet<>();
        boolean consume(SurvivalOutcome fact) { return rules.consume(fact, TICK, true, state, awards::add); }
        void clear() { awards.clear(); }
    }
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        com.mistaboom.essence_ascendance.tier.AscendanceTiers.init();
        reformation(); secondWind(); meals(); mirror(); debt(); adaptation(); food(); receipts(); persistence(); malformed(); bounds(); invalidFacts();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Survival achievement rules: " + checks + " checks passed");
    }
    private static void reformation() {
        var h = new Harness();
        h.consume(new SurvivalOutcome.SpiritReform(c(),false));
        check(h.awards.isEmpty(), "Failed reformation cannot earn Mostly Dead");
        h.consume(new SurvivalOutcome.SpiritReform(c(),true));
        check(h.awards.equals(Set.of(107)), "Confirmed alive reformation earns immediately without post-reform timer");
    }
    private static void secondWind() {
        var h = new Harness();
        var first = new SurvivalOutcome.SecondWind(c()); h.consume(first);
        check(h.awards.equals(Set.of(176)) && count(h.state) == 1, "First successful proc earns first milestone only");
        h.clear(); h.consume(first);
        check(h.awards.isEmpty() && count(h.state) == 1, "Duplicate prevention callback cannot become second proc");
        h.state = AchievementProgress.load(h.state.save());
        h.consume(new SurvivalOutcome.SpiritReform(c(),true));
        check(count(h.state) == 1, "A prevented death and successful reformation do not reset the same life");
        h.clear(); h.consume(new SurvivalOutcome.SecondWind(c()));
        check(h.awards.contains(177) && count(h.state) == 2, "Second genuine proc survives transient refresh and save/load");
        h.clear(); h.consume(new SurvivalOutcome.SecondWind(c()));
        check(!h.awards.contains(177) && count(h.state) == 2, "Saturated same-life count cannot replay native revoke");
        h.consume(new SurvivalOutcome.Death(c()));
        check(count(h.state) == 0, "Committed real death alone ends life");
        h.clear(); h.consume(new SurvivalOutcome.SecondWind(c()));
        check(!h.awards.contains(177) && count(h.state) == 1, "One proc in each of two lives never merges");
        h.consume(new SurvivalOutcome.SecondWind(c()));
        check(h.awards.contains(177), "Two fresh procs in the new life can re-earn");
        h.clear();
        check(h.rules.consume(new SurvivalOutcome.Death(c()),TICK,false,h.state,h.awards::add) && count(h.state) == 0,
                "Ineligible actual death still resets life without awarding");
        check(h.awards.isEmpty(), "Real death has no success award");
        var untouched = new AchievementProgress();
        check(!untouched.realDeath() && untouched.empty(), "Death of an untouched old-world record creates no empty survival ledger");
    }
    private static void meals() {
        var h = new Harness();
        h.consume(new SurvivalOutcome.AutomaticMeal(c(),"fight-a",1));
        check(h.awards.isEmpty(), "First completed automatic meal checkpoint remains below threshold");
        h.consume(new SurvivalOutcome.AutomaticMeal(c(),"fight-b",1));
        check(h.awards.isEmpty(), "First meal of a new hostile encounter does not reuse previous encounter");
        var second = new SurvivalOutcome.AutomaticMeal(c(),"fight-b",2);
        h.consume(second);
        check(h.awards.equals(Set.of(110)), "Second completed automatic meal checkpoint in one encounter earns");
        h.clear(); h.consume(second);
        check(h.awards.isEmpty(), "Duplicate saturated encounter checkpoint cannot replay revoke");
        h.consume(new SurvivalOutcome.AutomaticMeal(c(),"fight-c",1));
        h.consume(new SurvivalOutcome.AutomaticMeal(c(),"fight-d",1));
        check(h.awards.isEmpty(), "A fresh encounter after logout/dimension/expiry starts with first-meal checkpoint");
        h.consume(new SurvivalOutcome.AutomaticMeal(c(),"fight-d",2));
        check(h.awards.equals(Set.of(110)), "Fresh second-meal checkpoint may re-earn");
        var isolated = new Harness();
        isolated.consume(new SurvivalOutcome.AutomaticMeal(c(),"same",1));
        isolated.consume(new SurvivalOutcome.AutomaticMeal(c(BOB,TICK),"same",1));
        check(isolated.awards.isEmpty(), "Nearby teammate first meals never combine in award consumer");
    }
    private static void mirror() {
        for (boolean witch : new boolean[]{false,true}) for (boolean harmful : new boolean[]{false,true})
            for (boolean applied : new boolean[]{false,true}) for (UUID target : new UUID[]{WITCH,OTHER}) {
                var h = new Harness();
                h.consume(new SurvivalOutcome.MirroredEffect(c(),WITCH,target,witch,harmful,applied));
                check(h.awards.contains(114) == (witch && harmful && applied && target.equals(WITCH)),
                        "Same witch harmful successfully applied reflection required");
            }
    }
    private static void debt() {
        var h = new Harness();
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0,0,false));
        check(h.awards.isEmpty(), "Full ordinary health with no episode is not recovery");
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0,0.2,false));
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.2,0,false));
        check(h.awards.isEmpty(), "Unconfirmed capacity loss cannot arm an achievement");
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0,0.2,true));
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.2,0.2,false));
        check(h.awards.isEmpty(), "Buying Vitality/absolute health growth does not reduce own debt");
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.2,0.4,true));
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.4,0.1,false));
        check(h.awards.isEmpty(), "Partial debt recovery is insufficient");
        h.state = AchievementProgress.load(h.state.save());
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.1,0,false));
        check(h.awards.equals(Set.of(193)), "Same-life zero debt recovery survives save/reload and transient refresh");
        h.clear(); h.consume(new SurvivalOutcome.CeilingDebt(c(),0.1,0,false));
        check(h.awards.isEmpty(), "Consumed episode cannot replay recovery after revoke");
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0,0.1,true));
        h.consume(new SurvivalOutcome.Death(c()));
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.1,0,false));
        check(h.awards.isEmpty(), "Death clears armed episode before death cleanup zeroes debt");
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0,0.1,true));
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.1,0,false));
        check(h.awards.equals(Set.of(193)), "Fresh loss and recovery episode may re-earn");
        h.clear(); h.consume(new SurvivalOutcome.CeilingDebt(c(),0,0.1,true));
        h.rules.consume(new SurvivalOutcome.CeilingDebt(c(),0.1,0,false),TICK,false,h.state,h.awards::add);
        h.rules.consume(new SurvivalOutcome.CeilingDebt(c(),0,0.2,true),TICK,false,h.state,h.awards::add);
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.2,0,false));
        check(h.awards.isEmpty(), "Ineligible zero and admin-created debt cannot reuse an old legitimate armed episode");
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0,0.1,true));
        h.rules.consume(new SurvivalOutcome.CeilingDebt(c(),0.1,0.2,true),TICK,false,h.state,h.awards::add);
        h.consume(new SurvivalOutcome.CeilingDebt(c(),0.2,0,false));
        check(h.awards.isEmpty(), "Ineligible debt addition invalidates previous surviving evidence");
        var absent = new AchievementProgress(); absent.disarmCeilingDebt();
        check(absent.empty(), "Ineligible debt baseline creates no empty partial entry");
    }
    private static void adaptation() {
        for (double maximum : new double[]{0.01,0.12,0.5,0.9}) {
            var h = new Harness();
            h.consume(new SurvivalOutcome.Adaptation(c(),"minecraft:mob_attack",0,Math.nextDown(maximum),maximum));
            check(h.awards.isEmpty(), "Below live configured effective maximum rejects");
            h.consume(new SurvivalOutcome.Adaptation(c(),"minecraft:mob_attack",Math.nextDown(maximum),maximum,maximum));
            check(h.awards.equals(Set.of(200)), "Crossing effective configured maximum earns");
            h.clear(); h.consume(new SurvivalOutcome.Adaptation(c(),"minecraft:mob_attack",maximum,maximum,maximum));
            check(h.awards.isEmpty(), "Repeated capped adaptation is not a new maximum crossing");
        }
        var h = new Harness(); h.consume(new SurvivalOutcome.Adaptation(c(),"minecraft:magic",0,0,0));
        check(h.awards.isEmpty(), "Disabled zero adaptation maximum cannot earn");
    }
    private static void food() {
        var h = new Harness();
        for (String food : FOODS) {
            h.consume(new SurvivalOutcome.Repair(c(),food,0,-1));
            h.consume(new SurvivalOutcome.Repair(c(),food,-1,-1));
            h.consume(new SurvivalOutcome.Repair(c(),food,1,0));
            h.consume(new SurvivalOutcome.Repair(c(),food,1,10));
        }
        check(h.awards.isEmpty() && foods(h.state) == 0, "No repair, negative repair and Y>=0 never enter collection");
        for (int i=0;i<4;i++) {
            h.consume(new SurvivalOutcome.Repair(c(),FOODS[i],1,-0.001));
            h.consume(new SurvivalOutcome.Repair(c(),FOODS[i],2,-64));
        }
        check(h.awards.isEmpty() && foods(h.state) == 4, "Four distinct foods and repeated foods remain below five");
        h.consume(new SurvivalOutcome.Death(c())); h.state = AchievementProgress.load(h.state.save());
        h.consume(new SurvivalOutcome.Repair(c(),FOODS[4],1,-64));
        check(h.awards.equals(Set.of(209)) && foods(h.state) == 5, "Fifth actual repair food qualifies across life/save boundaries");
        h.clear(); h.consume(new SurvivalOutcome.Repair(c(),FOODS[5],1,-64));
        check(h.awards.isEmpty() && foods(h.state) == 5, "Saturated lifetime set cannot replay revoke or grow indefinitely");
        check(!h.state.repairedFood("Bad Food") && foods(h.state) == 5, "Invalid item identity rejected");
    }
    private static void receipts() {
        var h = new Harness(); var fact = new SurvivalOutcome.SecondWind(c());
        h.consume(fact); h.clear();
        check(!h.consume(fact) && h.awards.isEmpty() && count(h.state) == 1, "Duplicate successful effect receipt deduplicates");
        check(!h.rules.consume(new SurvivalOutcome.SecondWind(c(ALICE,51)),50,true,h.state,h.awards::add), "Future receipt rejects");
        check(!h.rules.consume(new SurvivalOutcome.SecondWind(c(ALICE,0)),200,true,h.state,h.awards::add), "Exact receipt expiry rejects");
        check(!h.rules.consume(new SurvivalOutcome.SecondWind(c()),TICK,false,h.state,h.awards::add), "Creative/admin/offline success ineligible");
        check(!h.rules.consume(null,TICK,true,h.state,h.awards::add), "No committed operation rejects");
        var shared = c();
        h.consume(new SurvivalOutcome.AutomaticMeal(shared,"joint",1));
        h.consume(new SurvivalOutcome.Repair(shared,"minecraft:apple",1,-1));
        check(foods(h.state) == 1, "One completed meal and its repair have separate typed receipt identities");
        var bob = new AchievementProgress();
        h.rules.consume(new SurvivalOutcome.SecondWind(c(BOB,TICK)),TICK,true,bob,h.awards::add);
        check(count(bob) == 1 && count(h.state) == 1, "Same-life progress is player-scoped");
    }
    private static void persistence() {
        var player = new PlayerEssenceData(); var state = player.achievementProgress();
        state.secondWind(); state.ceilingDebt(0,0.2,true);
        for(int i=0;i<4;i++) state.repairedFood(FOODS[i]);
        var restored = PlayerEssenceData.load(player.save()).achievementProgress();
        check(restored.save().equals(state.save()), "Actual player save preserves all three survival partials");
        check(restored.secondWind() && restored.ceilingDebt(0.2,0,false) && restored.repairedFood(FOODS[4]), "Fresh final outcomes complete restored partials");
        var restart = AchievementProgress.load(restored.save());
        check(!restart.secondWind() && !restart.ceilingDebt(0.2,0,false) && !restart.repairedFood(FOODS[5]), "Restart preserves consumed thresholds after native revoke");
        check(AchievementProgress.load(new CompoundTag()).empty(), "Legacy record does not manufacture historical procs, debt or foods");
    }
    private static AchievementProgress seed() {
        var state = new AchievementProgress(); state.secondWind(); state.ceilingDebt(0,0.2,true); state.repairedFood(FOODS[0]); return state;
    }
    private static void update(AchievementProgress state,String id) {
        switch(id) {
            case "a177" -> state.secondWind();
            case "a193" -> state.ceilingDebt(0.2,0,false);
            case "a209" -> state.repairedFood(FOODS[1]);
        }
    }
    private static void malformed() {
        for (String id : new String[]{"a177","a193","a209"}) {
            var root = seed().save(); entry(root,id).putInt("version",2); entry(root,id).putString("future","keep");
            var future = AchievementProgress.load(root); update(future,id); future.realDeath();
            check(entry(future.save(),id).equals(entry(root,id)), "Future survival rule version stays opaque through event/death");
            root = seed().save(); entry(root,id).putString("future_field","keep");
            var extended = AchievementProgress.load(root); update(extended,id);
            check(NbtVersionCompat.getString(entry(extended.save(),id),"future_field").equals("keep"), "Unknown fields retained during bounded updates");
            root = seed().save(); var e = entry(root,id);
            switch(id) {
                case "a177" -> e.putInt("count",3);
                case "a193" -> e.putString("active","true");
                case "a209" -> e.putString("foods","minecraft:apple");
            }
            var malformed = AchievementProgress.load(root); update(malformed,id); malformed.realDeath();
            check(entry(malformed.save(),id).equals(entry(root,id)), "Malformed known survival partial remains preserved and disabled locally");
        }
        for(int value:new int[]{-1,3}) {
            var root=seed().save(); entry(root,"a177").putInt("count",value);
            var state=AchievementProgress.load(root); state.secondWind(); state.realDeath();
            check(entry(state.save(),"a177").equals(entry(root,"a177")), "Out-of-bounds same-life counter rejects without resetting old data");
        }
        var root=seed().save(); var set=NbtVersionCompat.getCompound(entry(root,"a209"),"foods");
        for(String food:FOODS) set.putBoolean(food,true);
        var state=AchievementProgress.load(root); state.repairedFood("minecraft:melon_slice");
        check(state.save().equals(root), "Oversized food collection rejected locally");
        for(String badId:new String[]{"Bad Food","apple", "minecraft:"}) {
            root=seed().save(); set=NbtVersionCompat.getCompound(entry(root,"a209"),"foods"); set.putBoolean(badId,true);
            state=AchievementProgress.load(root); state.repairedFood(FOODS[1]);
            check(state.save().equals(root), "Noncanonical/malformed durable food identifier is preserved and disabled");
        }
        root=seed().save(); set=NbtVersionCompat.getCompound(entry(root,"a209"),"foods"); set.putBoolean(FOODS[0],false);
        state=AchievementProgress.load(root); state.repairedFood(FOODS[1]);
        check(state.save().equals(root), "False collection membership is not successful food evidence");
        root=seed().save(); root.putInt("version",2);
        state=AchievementProgress.load(root); update(state,"a177");update(state,"a193");update(state,"a209");state.realDeath();
        check(state.save().equals(root), "Unknown envelope roundtrips opaque under all survival events");
        root=seed().save(); entry(root,"a177").putString("count","1");
        state=AchievementProgress.load(root);
        check(!state.secondWind() && state.repairedFood(FOODS[1]) == false && foods(state)==2, "One malformed entry does not disable unrelated food progress");
    }
    private static void bounds() {
        var h = new Harness();
        h.state.secondWind(); h.state.ceilingDebt(0,0.2,true);
        for(int i=0;i<CommittedReceiptWindow.MAX_RECEIPTS;i++)
            check(h.consume(new SurvivalOutcome.SpiritReform(c(),false)), "Within receipt capacity accepted");
        check(!h.consume(new SurvivalOutcome.SecondWind(c())), "Success receipt overflow fails closed");
        check(h.rules.consume(new SurvivalOutcome.CeilingDebt(c(),0.2,0,false),TICK,false,h.state,h.awards::add),
                "Ineligible debt invalidation bypasses full award receipt capacity");
        check(!h.state.ceilingDebt(0.2,0,false), "Receipt capacity cannot preserve stale ineligible debt episode");
        h.state.ceilingDebt(0,0.2,true);
        check(count(h.state)==1, "Overflow cannot fabricate second successful proc");
        check(h.consume(new SurvivalOutcome.Death(c())) && count(h.state)==0, "Actual death reset bypasses full award receipt capacity");
        check(!h.state.ceilingDebt(0.2,0,false), "Full receipt window cannot preserve armed debt across real death");
        check(h.awards.isEmpty(), "Capacity rejection and real-death cleanup award nothing");
    }
    private static void invalidFacts() {
        rejects(() -> new SurvivalOutcome.Context("",ALICE,"minecraft:overworld",TICK));
        rejects(() -> new SurvivalOutcome.Context("ok",null,"minecraft:overworld",TICK));
        rejects(() -> new SurvivalOutcome.Context("ok",ALICE,"",TICK));
        rejects(() -> new SurvivalOutcome.Context("ok",ALICE,"minecraft:overworld",-1));
        rejects(() -> new SurvivalOutcome.AutomaticMeal(c(),"x".repeat(257),1));
        rejects(() -> new SurvivalOutcome.AutomaticMeal(c(),"f",0));
        rejects(() -> new SurvivalOutcome.AutomaticMeal(c(),"f",3));
        rejects(() -> new SurvivalOutcome.CeilingDebt(c(),Double.NaN,0,false));
        rejects(() -> new SurvivalOutcome.CeilingDebt(c(),0,-1,true));
        rejects(() -> new SurvivalOutcome.Adaptation(c(),"magic",0,Double.POSITIVE_INFINITY,1));
        rejects(() -> new SurvivalOutcome.Repair(c(),"minecraft:apple",1,Double.NaN));
        rejects(() -> new SurvivalOutcome.MirroredEffect(c(),null,WITCH,true,true,true));
    }
    private static CompoundTag entry(CompoundTag root,String id) { return NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(root,"entries"),id); }
    private static int count(AchievementProgress state) { return NbtVersionCompat.getInt(entry(state.save(),"a177"),"count"); }
    private static int foods(AchievementProgress state) { return NbtVersionCompat.getCompound(entry(state.save(),"a209"),"foods").size(); }
    private static void rejects(Runnable action) { try { action.run();throw new AssertionError("Invalid survival fact accepted"); } catch(IllegalArgumentException|NullPointerException expected) { checks++; } }
    private static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
}
