package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.MachineOutcome;
import com.mistaboom.essence_ascendance.gameplay.DissolutionOutcome;
import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.math.BigInteger;
import java.util.*;

/** Real rule transitions/codec boundaries; native producers are tested separately in disposable worlds. */
public final class MachineAchievementTest {
    private static int checks;
    private static int action;
    private static final UUID ALICE = new UUID(1,1), BOB = new UUID(2,2), SOURCE = new UUID(3,3), MACHINE = new UUID(4,4);
    private static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";
    private static final long TICK = 50;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        com.mistaboom.essence_ascendance.tier.AscendanceTiers.init();
        singles(); receipts(); categories(); sources(); dimensions(); inputItems(); days(); persistence(); caps(); malformed();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Machine achievement rules: " + checks + " checks passed");
    }
    private static UUID action() { return new UUID(10, ++action); }
    private static MachineOutcome.Infusion infusion(UUID actor, UUID machine, UUID source, String dimension, MachineOutcome.Result result, int mask, int tier) {
        return new MachineOutcome.Infusion(action(), actor, dimension, 0, machine, source, result, "essence_ascendance:test_output", 1, mask, tier, TICK);
    }
    private static MachineOutcome.Infrastructure infrastructure(boolean active, int count, int limit, boolean running) {
        return new MachineOutcome.Infrastructure(action(), ALICE, OVERWORLD, 0, MACHINE, active, count, limit, running, TICK);
    }
    private static DissolutionOutcome dissolution(UUID machine, long day, int mask, Set<String> inputs) {
        return new DissolutionOutcome(action().toString(), ALICE, ResourceLocation.parse(OVERWORLD), 0, 1, BigInteger.ONE, TICK, machine, inputs, mask, day);
    }
    private static Set<Integer> awards(MachineOutcome fact) {
        Set<Integer> awards = new HashSet<>();
        check(new MachineAchievementRules().consume(fact,TICK,true,new AchievementProgress(),awards::add), "Committed operation accepted"); return awards;
    }
    private static void singles() {
        check(awards(new MachineOutcome.Channel(action(),ALICE,OVERWORLD,0,MACHINE,1,TICK)).equals(Set.of(24)), "Positive channel earns24");
        check(awards(infrastructure(true,1,2,false)).equals(Set.of(46)), "Functioning activated Pylon earns46 without running Crucible");
        check(awards(infrastructure(false,2,2,true)).equals(Set.of(57)), "Running full positive active slots earns57");
        check(awards(infrastructure(true,2,2,true)).equals(Set.of(46,57)), "One committed network transition may earn both");
        check(awards(infrastructure(false,0,0,true)).isEmpty(), "Zero configured slots cannot grant vacuously");
        check(awards(infrastructure(true,0,2,true)).isEmpty(), "Merely installed or invalid Pylon does not earn46");
        check(awards(infrastructure(false,1,2,true)).isEmpty(), "Capacity increase exposes missing active slot");
        check(awards(infrastructure(false,1,1,true)).contains(57), "Current lower positive capacity respected on next operation");
        check(awards(infrastructure(false,2,2,false)).isEmpty(), "Placed full infrastructure without running cannot earn57");
        for (var result: MachineOutcome.Result.values()) for (int tier=0;tier<=5;tier++) {
            var earned = awards(infusion(ALICE,MACHINE,SOURCE,OVERWORLD,result,result==MachineOutcome.Result.FILLED_ESSENTIUM?1:0,tier));
            check(earned.contains(49), "Any successful completion earns49");
            check(earned.contains(63)==(result==MachineOutcome.Result.WEAPON_UPGRADE&&tier==5), "63 only committed Transcendent weapon upgrade");
            check(earned.contains(73)==(result==MachineOutcome.Result.ROD_UPGRADE&&tier==5), "73 only committed Transcendent rod upgrade");
        }
        expectInvalid(() -> new MachineOutcome.Channel(action(),ALICE,OVERWORLD,0,MACHINE,0,TICK));
        expectInvalid(() -> infusion(ALICE,MACHINE,SOURCE,OVERWORLD,MachineOutcome.Result.FILLED_ESSENTIUM,0,0));
        expectInvalid(() -> infusion(ALICE,MACHINE,SOURCE,OVERWORLD,MachineOutcome.Result.OTHER,64,0));
        expectInvalid(() -> infusion(ALICE,MACHINE,SOURCE,OVERWORLD,MachineOutcome.Result.OTHER,0,6));
    }
    private static void receipts() {
        var rules=new MachineAchievementRules(); var state=new AchievementProgress(); var earned=new HashSet<Integer>();
        var fact=infusion(ALICE,MACHINE,SOURCE,OVERWORLD,MachineOutcome.Result.OTHER,0,0);
        check(!rules.consume(fact,TICK,false,state,earned::add)&&earned.isEmpty(),"Creative/ineligible operator cannot advance state");
        check(!rules.consume((MachineOutcome)null,TICK,true,state,earned::add),"Failed/canceled job has no fact");
        check(!rules.consume(fact,TICK-1,true,state,earned::add),"Future fact rejects");
        check(rules.consume(fact,TICK,true,state,earned::add),"Fresh fact accepted"); earned.clear();
        check(!rules.consume(fact,TICK+1,true,state,earned::add)&&earned.isEmpty(),"Duplicate cannot regrant after native revoke");
        check(!rules.consume(fact,TICK+200,true,state,earned::add),"Old receipt rejects at exact expiry");
        check(rules.consume(infusion(ALICE,MACHINE,SOURCE,OVERWORLD,MachineOutcome.Result.OTHER,0,0),TICK,true,state,earned::add)&&earned.contains(49),"New committed retry may reearn");
        var bob=new MachineOutcome.Infusion(fact.actionId(),BOB,OVERWORLD,0,MACHINE,SOURCE,MachineOutcome.Result.OTHER,"minecraft:stone",1,0,0,TICK);
        check(rules.consume(bob,TICK,true,new AchievementProgress(),ignored->{}),"Same receipt identity scoped by actor");
        var capped=new MachineAchievementRules();
        for(int i=0;i<4096;i++) check(capped.consume(infusion(ALICE,MACHINE,null,OVERWORLD,MachineOutcome.Result.OTHER,0,0),TICK,true,new AchievementProgress(),ignored->{}),"Bounded live receipt accepted");
        check(!capped.consume(infusion(ALICE,MACHINE,null,OVERWORLD,MachineOutcome.Result.OTHER,0,0),TICK,true,state,ignored->{}),"Receipt capacity never evicts live history");
    }
    private static void categories() {
        var state=new AchievementProgress();
        for(int bit=0;bit<6;bit++) {
            check(state.machineCategories(1<<bit)==(bit==5),"Six distinct positive filled categories complete only at sixth");
            check(!state.machineCategories(1<<bit),"Repeated category cannot replay collection");
            state=AchievementProgress.load(state.save());
        }
        check(!state.machineCategories(63),"Saturated lifetime set does not restore revoked award");
        var rules=new MachineAchievementRules(); var untouched=new AchievementProgress();
        check(rules.consume(infusion(ALICE,MACHINE,null,OVERWORLD,MachineOutcome.Result.OTHER,63,0),TICK,true,untouched,ignored->{}),"Unrelated recipe can carry positive supply facts");
        check(untouched.machineCategories(63),"Only actual filled Essentium result contributes51");
    }
    private static void sources() {
        var state=new AchievementProgress(); UUID second=new UUID(5,5);
        check(!state.infuserSource(SOURCE,MACHINE),"First finished Infuser not three");
        check(!state.infuserSource(SOURCE,MACHINE),"One Infuser repeated cannot become distinct");
        check(!state.infuserSource(second,new UUID(6,6)),"Different physical Crucible does not merge financial pool");
        check(!state.infuserSource(SOURCE,new UUID(7,7)),"Second physical Infuser");
        state=AchievementProgress.load(state.save());
        check(state.infuserSource(SOURCE,new UUID(8,8)),"Third finishes same source after restart");
        check(!state.infuserSource(SOURCE,new UUID(9,9)),"Saturated source collection cannot replay after revoke");
        check(!state.infuserSource(new UUID(30,30),MACHINE),"Replaced Crucible cannot inherit address collection");
        check(!state.infuserSource(null,MACHINE),"Unknown/mixed physical supply cannot fabricate source");
    }
    private static void dimensions() {
        var state=new AchievementProgress();
        check(!state.machineDimension("minecraft:the_end"),"End is not Nether");
        check(!state.machineDimension(OVERWORLD),"First dimension partial");
        state=AchievementProgress.load(state.save());
        check(state.machineDimension(NETHER),"Different machine/workpiece Nether operation completes197");
        check(!state.machineDimension(NETHER),"Saturated dimensions do not restore revoke");
    }
    private static void inputItems() {
        var state=new AchievementProgress();
        for(int i=0;i<42;i++) {
            check(state.dissolvedItems(Set.of("minecraft:input_"+i))==(i==41),"42 actual distinct input registry identities");
            check(!state.dissolvedItems(Set.of("minecraft:input_"+i)),"Repeated input count/Essence yield cannot add identity");
        }
        check(!state.dissolvedItems(Set.of("minecraft:another")),"Set capped at42 without revoke replay");
        check(!new AchievementProgress().dissolvedItems(Set.of()),"Slot/unsupported/failed operation supplies no consumed inputs");
    }
    private static void days() {
        var state=new AchievementProgress();
        check(!state.dissolvedDay(SOURCE,23998,31),"Five categories partial");
        check(state.dissolvedDay(SOURCE,23999,32),"Calendar day completes before boundary");
        check(!state.dissolvedDay(SOURCE,23999,32),"Same day saturation cannot replay revoke");
        check(!state.dissolvedDay(SOURCE,24000,32),"Rollover resets, never rolling24000 window");
        check(state.dissolvedDay(SOURCE,24001,31),"Fresh day can earn with new positive credits");
        state=new AchievementProgress();
        check(!state.dissolvedDay(SOURCE,1000,31),"Sleep precondition");
        check(!state.dissolvedDay(SOURCE,25000,32),"Sleep jump resets calendar bucket");
        check(!state.dissolvedDay(SOURCE,24999,31),"Backwards clock within day invalidates bucket");
        check(state.dissolvedDay(SOURCE,25000,32),"Fresh post-rewind bucket may complete");
        state=new AchievementProgress(); state.dissolvedDay(SOURCE,1000,31);
        check(!state.dissolvedDay(MACHINE,1001,32),"Two Crucibles same pool do not merge");
        check(state.dissolvedDay(SOURCE,1002,32),"Interleaved separate source retains own bucket");
        state=new AchievementProgress(); state.dissolvedDay(SOURCE,-2,31);
        check(state.dissolvedDay(SOURCE,-1,32),"Negative clocks use floor calendar days");
        check(!state.dissolvedDay(SOURCE,0,32),"Negative-to-zero day rollover");
        state=new AchievementProgress(); state.dissolvedDay(SOURCE,1000,31); state.invalidateMachineDay();
        check(!state.dissolvedDay(SOURCE,1001,32),"Explicit rewind followed by forward still invalidates previous history");
        var rules=new MachineAchievementRules(); var earned=new HashSet<Integer>();
        rules.consume(dissolution(SOURCE,1002,31,Set.of("minecraft:stone")),TICK,true,state,earned::add);
        check(earned.contains(190),"Actual dissolution fact combines only positive output category mask");
    }
    private static void persistence() {
        var player=new PlayerEssenceData(); var state=player.achievementProgress();
        state.machineCategories(31); state.machineDimension(OVERWORLD); state.dissolvedItems(Set.of("minecraft:stone"));
        state.infuserSource(SOURCE,MACHINE); state.infuserSource(SOURCE,new UUID(20,20)); state.dissolvedDay(SOURCE,12,31);
        var restored=PlayerEssenceData.load(player.save()).achievementProgress();
        check(restored.save().equals(state.save()),"Real player record roundtrip all versioned partials");
        check(restored.machineCategories(32)&&restored.machineDimension(NETHER),"Lifetime category/dimension survive restart/death/logout");
        check(restored.infuserSource(SOURCE,new UUID(21,21)),"Source identity collection durable");
        check(restored.dissolvedDay(SOURCE,13,32),"Same-day partial durable across restart");
        var legacy=AchievementProgress.load(new CompoundTag()); check(legacy.empty(),"Legacy absence remains empty without historical backfill");
    }
    private static void caps() {
        var state=new AchievementProgress();
        for(int i=0;i<100;i++) { state.infuserSource(new UUID(100,i),MACHINE); state.dissolvedDay(new UUID(100,i),1000,1); }
        var entries=NbtVersionCompat.getCompound(state.save(),"entries");
        check(NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(entries,"a056"),"groups").size()==64,"Source groups bounded to64");
        check(NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(entries,"a190"),"groups").size()==64,"Calendar groups bounded to64");
        var restored=AchievementProgress.load(state.save());
        check(!restored.infuserSource(new UUID(100,99),new UUID(44,44)),"Most recent source survives LRU pressure");
        check(restored.infuserSource(new UUID(100,99),new UUID(45,45)),"Preserved current candidate completes");
        check(restored.dissolvedDay(new UUID(100,99),1001,62),"Recent calendar group preserved");
    }
    private static void malformed() {
        var state=new AchievementProgress(); state.machineCategories(1); var root=state.save();
        var entry=NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(root,"entries"),"a051"); entry.putInt("version",9); entry.putString("future","retained");
        var future=AchievementProgress.load(root); check(!future.machineCategories(63)&&future.save().equals(root),"Future entry opaque and disabled");
        root.putInt("version",9); future=AchievementProgress.load(root);
        check(!future.machineDimension(OVERWORLD)&&future.save().equals(root),"Future envelope opaque");
        var bad=new AchievementProgress(); bad.machineCategories(1); root=bad.save();
        NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(root,"entries"),"a051").putInt("mask",64);
        var rejected=AchievementProgress.load(root);
        check(!rejected.machineCategories(63),"Malformed known mask disabled locally");
        check(!rejected.machineDimension(OVERWORLD)&&rejected.machineDimension(NETHER),"Unrelated valid entry still works");
        check(!new AchievementProgress().dissolvedItems(Set.of("not a registry id")),"Invalid registry identity rejects");
        for (String id : List.of("", "minecraft:", "stone"))
            check(!new AchievementProgress().dissolvedItems(Set.of(id)),"Blank/empty/noncanonical input IDs cannot become distinct saved item identities");
    }
    private static void expectInvalid(Runnable op) { try { op.run(); throw new AssertionError("Invalid fact accepted"); } catch(IllegalArgumentException expected) { checks++; } }
    private static void check(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
}
