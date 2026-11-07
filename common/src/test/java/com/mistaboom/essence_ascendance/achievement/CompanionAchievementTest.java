package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.gameplay.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import java.util.*;

/** Actual rule outcomes, bounded observation continuity and versioned partial serialization. */
public final class CompanionAchievementTest {
    private static final UUID ALICE=new UUID(7,1),BOB=new UUID(7,2),CAT=new UUID(7,3),OTHER_CAT=new UUID(7,4),PET=new UUID(7,5);
    private static final String DIM="minecraft:overworld";
    private static int checks,action;
    private static final class Harness {
        final CompanionAchievementRules rules=new CompanionAchievementRules();
        AchievementProgress state=new AchievementProgress(); final Set<Integer> awards=new HashSet<>();
        boolean consume(CompanionOutcome fact){return rules.consume(fact,50,true,state,awards::add);}
    }
    private static CompanionOutcome.Context c(){return new CompanionOutcome.Context("companion-"+ ++action,ALICE,DIM,50);}
    private static UUID victim(){return new UUID(71,++action);}
    private static CompanionOutcome.CatFact cat(UUID id,UUID owner,double distance,boolean complete){return new CompanionOutcome.CatFact(new OutcomePolicies.CatCandidate(id,owner,DIM,true,true,true,distance*distance),complete);}
    private static CompanionOutcome.CatKill kill(CompanionOutcome.CatFact...cats){return new CompanionOutcome.CatKill(c(),victim(),List.of(cats));}
    public static void main(String[]args){
        Thread.currentThread().setUncaughtExceptionHandler((thread,failure)->failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        bonded();selection();damage();continuity();persistence();receipts();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Companion achievement rules: "+checks+" checks passed");
    }
    private static void bonded(){
        for(boolean hostile:new boolean[]{false,true})for(boolean atContact:new boolean[]{false,true})for(boolean atCommit:new boolean[]{false,true})
            for(UUID first:new UUID[]{ALICE,BOB})for(UUID last:new UUID[]{ALICE,BOB}){
                var h=new Harness();h.consume(new CompanionOutcome.BondedKill(c(),PET,victim(),first,last,hostile,atContact,atCommit));
                check(h.awards.contains(118)==(hostile&&atContact&&atCommit&&ALICE.equals(first)&&ALICE.equals(last)),"Actual empowerment at both boundaries and unchanged correct owner required");
            }
        var h=new Harness();var receipt=new CompanionOutcome.BondedKill(c(),PET,victim(),ALICE,ALICE,true,true,true);h.consume(receipt);h.awards.clear();h.consume(receipt);
        check(h.awards.isEmpty(),"Duplicate companion death callback cannot replay native revoke");
        check(!h.rules.consume(new CompanionOutcome.BondedKill(c(),PET,victim(),ALICE,ALICE,true,true,true),50,false,h.state,h.awards::add),"Creative/admin/offline owner cannot receive success");
    }
    private static void selection(){
        var h=new Harness();h.consume(kill(cat(CAT,ALICE,3,true),cat(OTHER_CAT,ALICE,2,true),cat(PET,BOB,1,true)));
        check(OTHER_CAT.equals(h.state.selectedCat()),"Nearest own eligible cat binds at first qualifying kill");
        for(int i=0;i<3;i++)h.consume(kill(cat(CAT,ALICE,1,true),cat(OTHER_CAT,ALICE,8,true)));
        check(!h.awards.contains(119),"Two cats never double-count a single hostile death");
        h.consume(kill(cat(CAT,ALICE,1,true)));
        check(!h.awards.contains(119)&&OTHER_CAT.equals(h.state.selectedCat()),"Missing chosen cat never substitutes another nearer cat");
        h.consume(kill(cat(OTHER_CAT,ALICE,8.00001,true)));
        check(!h.awards.contains(119),"Outside inclusive eight-block feet-to-feet radius rejects");
        h.consume(kill(cat(OTHER_CAT,ALICE,8,true)));
        check(h.awards.contains(119),"Fifth confirmed hostile kill with same cat at exact radius earns");
        var ties=new Harness();ties.consume(kill(cat(OTHER_CAT,ALICE,2,true),cat(CAT,ALICE,2,true)));
        check(CAT.equals(ties.state.selectedCat()),"Equal-distance candidate selection uses lexicographic UUID");
        var unknown=new Harness();unknown.consume(kill(cat(CAT,ALICE,1,false),cat(OTHER_CAT,ALICE,2,true)));
        check(unknown.state.selectedCat()==null,"Unknown nearest-cat fight evidence rejects rather than replacing it with a farther cat");
        var teammate=new Harness();for(int i=0;i<6;i++)teammate.consume(kill(cat(CAT,BOB,1,true)));
        check(teammate.state.selectedCat()==null&&teammate.awards.isEmpty(),"Nearby teammate's cat cannot fabricate ownership");
    }
    private static void damage(){
        for(String reason:List.of("accepted_damage","ownership_lost","cat_dead","cat_removed")){
            var h=new Harness();for(int i=0;i<4;i++)h.consume(kill(cat(CAT,ALICE,2,true)));
            var invalid=new CompanionOutcome.CatInvalidated(c(),CAT,reason);
            check(h.rules.consume(invalid,50,false,h.state,h.awards::add)&&h.state.selectedCat()==null,"Damage/death/ownership invalidation works offline and ineligible: "+reason);
            h.consume(kill(cat(CAT,ALICE,2,true)));check(!h.awards.contains(119),"New first kill cannot use invalidated prior four");
        }
        var h=new Harness();for(int i=0;i<4;i++)h.consume(kill(cat(CAT,ALICE,2,true)));
        h.consume(new CompanionOutcome.CatInvalidated(c(),OTHER_CAT,"accepted_damage"));h.consume(kill(cat(CAT,ALICE,2,true)));
        check(h.awards.contains(119),"Damage to another cat cannot erase the chosen cat's evidence");
        h.awards.clear();h.consume(kill(cat(CAT,ALICE,2,true)));check(h.awards.isEmpty(),"Saturated durable count cannot replay revoke");
        h.consume(new CompanionOutcome.CatInvalidated(c(),CAT,"accepted_damage_between_encounters"));
        for(int i=0;i<5;i++)h.consume(kill(cat(OTHER_CAT,ALICE,2,true)));
        check(h.awards.contains(119)&&OTHER_CAT.equals(h.state.selectedCat()),"Actual damage between encounters ends collection and permits a fresh challenge");
    }
    private static void continuity(){
        var evidence=new CompanionCatEvidence();evidence.observe(CAT,ALICE,DIM,20);
        check(evidence.undamaged(CAT,ALICE,DIM,20,20),"Native loaded observation can establish complete current-tick encounter evidence");
        evidence.observe(CAT,ALICE,DIM,21);check(evidence.undamaged(CAT,ALICE,DIM,20,21),"Consecutive loaded observations preserve encounter coverage");
        evidence.damaged(CAT,ALICE,DIM,21);check(!evidence.undamaged(CAT,ALICE,DIM,20,21),"Accepted health/absorption loss invalidates complete fight evidence");
        evidence.observe(CAT,ALICE,DIM,22);check(evidence.undamaged(CAT,ALICE,DIM,22,22),"New encounter after damage may begin fresh evidence");
        evidence.observe(CAT,ALICE,DIM,25);check(!evidence.undamaged(CAT,ALICE,DIM,22,25),"Observation disappearance cannot hide damage during an old encounter");
        evidence.removed(CAT);check(!evidence.undamaged(CAT,ALICE,DIM,25,25),"Unload/removal closes native evidence immediately");
        evidence.observe(CAT,BOB,DIM,26);check(!evidence.undamaged(CAT,ALICE,DIM,26,26),"Ownership transfer does not reuse first owner's history");
        evidence.observe(CAT,ALICE,"minecraft:the_nether",27);check(!evidence.undamaged(CAT,ALICE,DIM,26,27),"Dimension changes cannot bridge unobserved fight evidence");
        var sameTick=new CompanionCatEvidence();sameTick.observe(CAT,ALICE,DIM,50);sameTick.damaged(CAT,ALICE,DIM,50);sameTick.removed(CAT);
        check(!sameTick.undamaged(CAT,ALICE,DIM,50,50),"Closed observation cannot qualify a cat even in the removal tick");
        sameTick.observe(CAT,ALICE,DIM,50);
        check(!sameTick.undamaged(CAT,ALICE,DIM,50,50),"Same-tick unload and reobservation preserve already accepted cat damage");
        sameTick.removed(CAT);sameTick.observe(CAT,BOB,DIM,50);sameTick.removed(CAT);sameTick.observe(CAT,ALICE,DIM,50);
        check(!sameTick.undamaged(CAT,ALICE,DIM,50,50),"Ownership away and back in the damage tick cannot launder the same fight's loss");
        var protectedFight=new Harness();
        for(int i=0;i<5;i++)protectedFight.consume(kill(cat(CAT,ALICE,2,sameTick.undamaged(CAT,ALICE,DIM,50,50))));
        check(!protectedFight.awards.contains(119)&&protectedFight.state.selectedCat()==null,"Five real rule inputs with laundered same-fight cat damage never create a protected collection");
        sameTick.observe(CAT,ALICE,DIM,51);
        check(sameTick.undamaged(CAT,ALICE,DIM,51,51),"Fresh later encounter can qualify after prior damage without permanent cat exclusion");
        var bounded=new CompanionCatEvidence();for(int i=0;i<CompanionCatEvidence.MAX_CATS+1;i++)bounded.observe(new UUID(91,i),ALICE,DIM,50);
        check(bounded.size()==CompanionCatEvidence.MAX_CATS,"Loaded observation table has fixed cap");
        check(!bounded.undamaged(new UUID(91,CompanionCatEvidence.MAX_CATS),ALICE,DIM,50,50),"Capacity exhaustion fails closed for unknown evidence");
        bounded.observe(CAT,ALICE,DIM,250);check(bounded.size()==1,"Expired native observations clean up on producer activity");
    }
    private static void persistence(){
        var h=new Harness();for(int i=0;i<4;i++)h.consume(kill(cat(CAT,ALICE,2,true)));
        h.state=AchievementProgress.load(h.state.save());h.state.realDeath();
        check(CAT.equals(h.state.selectedCat()),"Verified cat-bound kills survive save/reload and player real death");
        h.consume(kill(cat(CAT,ALICE,2,true)));check(h.awards.contains(119),"Fresh verified fifth kill completes saved collection after lifecycle reset");
        var root=h.state.save();var entries=NbtVersionCompat.getCompound(root,"entries");var entry=NbtVersionCompat.getCompound(entries,"a119");entry.putString("future_field","retain");
        var restored=AchievementProgress.load(root);restored.invalidateCat(CAT);
        check("retain".equals(NbtVersionCompat.getString(NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(restored.save(),"entries"),"a119"),"future_field")),"Known entry preserves unknown fields while invalidating");
        entry.putInt("version",2);var future=AchievementProgress.load(root);check(!future.catKill(CAT)&&root.equals(future.save()),"Future cat rule version roundtrips opaque");
        entry.putInt("version",1);entry.putString("cat","not-a-uuid");var malformed=AchievementProgress.load(root);
        check(malformed.selectedCat()==null&&!malformed.catKill(CAT)&&root.equals(malformed.save()),"Malformed known selected UUID is isolated and cannot be used");
    }
    private static void receipts(){
        var h=new Harness();var v=victim();h.consume(new CompanionOutcome.CatKill(c(),v,List.of(cat(CAT,ALICE,2,true))));
        for(int i=0;i<4;i++)h.consume(new CompanionOutcome.CatKill(c(),v,List.of(cat(CAT,ALICE,2,true))));
        check(!h.awards.contains(119),"Duplicate same-victim native callbacks with different action IDs count once");
        var expired=new CompanionOutcome.CatInvalidated(new CompanionOutcome.Context("expired",ALICE,DIM,0),CAT,"damage");
        check(!h.rules.consume(expired,200,false,h.state,h.awards::add)&&CAT.equals(h.state.selectedCat()),"Expired callbacks cannot erase fresh cat progress");
        for(int i=0;i<CommittedReceiptWindow.MAX_RECEIPTS;i++)h.consume(new CompanionOutcome.BondedKill(c(),PET,victim(),ALICE,ALICE,true,true,true));
        check(h.rules.consume(new CompanionOutcome.CatInvalidated(c(),CAT,"damage"),50,false,h.state,h.awards::add)&&h.state.selectedCat()==null,"Invalidation survives full success-receipt capacity");
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
