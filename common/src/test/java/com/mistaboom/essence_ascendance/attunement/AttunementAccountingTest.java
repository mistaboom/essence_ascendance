package com.mistaboom.essence_ascendance.attunement;

import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import java.util.*;

/** Executable real-NBT and accounting invariants; live loader events are separate acceptance checks. */
public final class AttunementAccountingTest {
    private static int assertions;
    private static final String OFF="essence_ascendance:offense", DEF="essence_ascendance:defense", VIT="essence_ascendance:vitality", MOB="essence_ascendance:mobility", UTI="essence_ascendance:utility";
    private static final AttunementProfile.Policy POLICY=new AttunementProfile.Policy(2,.1,.5,8);
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init(); MilestoneProviders.init(); Milestones.init(); Skills.init();
        for(int i=1;i<=5;i++) check(AttunementProfile.requiredCategories(6,i,5,1)==i,"Procedural six-category breadth including Latent onboarding");
        var freshPlayer = new PlayerEssenceData();
        check(freshPlayer.getTierId().equals(AscendanceTiers.LATENT.id()) && !freshPlayer.getTier().grantsPower(),
                "New players begin in a zero-power Latent tier");
        for(int categories=1;categories<=30;categories++)for(int tiers=1;tiers<8;tiers++)for(int i=1;i<=tiers;i++) {
            int n=AttunementProfile.requiredCategories(categories,i,tiers,1); check(n>=1&&n<=Math.max(1,categories-1),"Unusual registry breadth");
        }
        for(var essence:EssenceRegistry.values())check(AttunementActivityRegistry.values().stream().filter(a -> a.categoryId().equals(essence.id().toString())&&a.baseGameAccessible()).count()>=2,"Every category has multiple unrestricted methods");
        var chapter=chapter();
        var baseline=new AttunementLedger();baseline.chapter(chapter.id());
        long normal=apply(baseline,"one","deal_damage","minecraft:zombie",10,0,chapter).finalContribution();
        check(normal>0,"Zero investment progresses");
        double previous=1;
        for(long invested=0;invested<=2000;invested+=10) {double multiplier=AttunementLedger.investmentMultiplier(invested,1000,2);check(multiplier>=previous&&multiplier<=3,"Monotonic capped investment acceleration");previous=multiplier;}
        check(AttunementLedger.investmentMultiplier(Long.MAX_VALUE,1000,2)==3,"Extreme investment cap");
        var skilled=new AttunementLedger();skilled.chapter(chapter.id());
        long enhanced=apply(skilled,"one","deal_damage","minecraft:zombie",20,0,chapter).finalContribution();
        check(enhanced>normal,"Generic skill improved confirmed outcome; base action still works without skill");
        AttunementActivityRegistry.register(new AttunementActivity("test:future_heal",VIT,"health","health",true));
        var futureChapter=chapter();var future=new AttunementLedger();future.chapter(futureChapter.id());
        check(apply(future,"rescue","test:future_heal","test:outcome",10,0,futureChapter).credited(),"Future registered skill outcome uses shared math");
        var repeats=new AttunementLedger();repeats.chapter(chapter.id());long last=Long.MAX_VALUE;
        for(int i=0;i<24;i++) {var c=apply(repeats,"kill"+i,"defeat_enemies","minecraft:zombie",10,0,chapter);check(c.finalContribution()>0,"Hostile farms remain productive");check(c.finalContribution()<=last,"Repeat approaches floor");last=c.finalContribution();}
        check(last==10_000,"Repeat approaches the configured floor of undiscounted value");
        var grind=new AttunementLedger();grind.chapter(chapter.id());int grindActions=0;
        while(grind.progress(OFF)<AttunementLedger.SCALE&&grindActions<2_000) {
            var c=apply(grind,"grind"+grindActions,"defeat_enemies","minecraft:zombie",1_000,0,chapter);
            check(c.finalContribution()>0,"Repeated grind action remains productive");grindActions++;
        }
        check(grind.progress(OFF)==AttunementLedger.SCALE,"One repeated source can grind a seal to completion through diminishing returns");
        for(int i=0;i<8;i++)apply(repeats,"varied"+i,"defeat_enemies","enemy:"+i,10,0,chapter);
        var restored=apply(repeats,"returned","defeat_enemies","minecraft:zombie",10,0,chapter);
        check(restored.finalContribution()>=normal&&restored.varietyMultiplier()>1,"Different recent sources restore efficiency and bounded variety");
        var duplicate=new AttunementLedger();duplicate.chapter(chapter.id());
        var small=apply(duplicate,"anvil","use_anvils","repair",5,0,chapter);
        var large=apply(duplicate,"anvil","repair_equipment","repair",20,0,chapter);
        check(duplicate.progress(UTI)==small.finalContribution()+large.finalContribution(),"Same-category second outcome adds only highest-credit difference");
        check(duplicate.progress(UTI)==enhanced,"Overlapping anvil and repair count once");
        check(duplicate.methodProgress("use_anvils")==0&&duplicate.methodProgress("repair_equipment")==enhanced,"Method attribution transfers to winning outcome");
        check(!apply(duplicate,"anvil","use_anvils","repair",5,0,chapter).credited(),"Duplicate completion is rejected");
        var almost=new AttunementLedger();almost.chapter(chapter.id());
        var noRepeat=new AttunementProfile.Policy(0,1,0,8);
        for(int i=0;i<19;i++)almost.contribute("fill"+i,AttunementEvent.Outcome.eligible("gain_experience","xp",5000),chapter,noRepeat,0);
        almost.contribute("earlier",AttunementEvent.Outcome.eligible("use_anvils","earlier",4000),chapter,noRepeat,0);
        almost.contribute("clipped",AttunementEvent.Outcome.eligible("use_anvils","repair",2000),chapter,noRepeat,0);
        almost.contribute("clipped",AttunementEvent.Outcome.eligible("repair_equipment","repair",4000),chapter,noRepeat,0);
        check(almost.methodProgress("use_anvils")==40_000_000L&&almost.methodProgress("repair_equipment")==10_000_000L,"Near-complete root upgrade transfers only accounted credit, preserving unrelated method totals");

        check(apply(duplicate,"run-root","run","region:one",10,0,chapter).credited(),"Sprint distance credits Mobility");
        check(apply(duplicate,"run-root","consume_hunger","sprint",2,0,chapter).credited(),"Same action actual hunger credits Vitality");
        check(duplicate.progress(MOB)>0&&duplicate.progress(VIT)>0,"Multi-category outcomes coexist");
        var cancelled=duplicate.contribute("self",AttunementEvent.Outcome.rejected("take_damage","self","self_damage"),chapter,POLICY,0);
        check(!cancelled.credited()&&duplicate.progress(DEF)==0,"Rejected self-damage cannot create Defense");
        long beforeVitality=duplicate.progress(VIT);
        duplicate.contribute("self-heal",AttunementEvent.Outcome.rejected("heal_health","self","self_damage"),chapter,POLICY,0);
        check(duplicate.progress(VIT)==beforeVitality,"Rejected self-damage recovery cannot create Vitality");
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})check(!apply(duplicate,"invalid"+value,"run","idle",value,0,chapter).credited(),"Non-real units rejected");
        var microscopic=new AttunementLedger();microscopic.chapter(chapter.id());
        check(apply(microscopic,"tiny","deal_damage","small",1e-20,0,chapter).finalContribution()==1,"Small legitimate gains retain nonzero accounting quantum");
        var extreme=new AttunementLedger();extreme.chapter(chapter.id());
        var huge=apply(extreme,"huge","deal_damage","boss",Double.MAX_VALUE,Long.MAX_VALUE,chapter);
        check(huge.finalContribution()==AttunementLedger.SCALE,"A legitimate extreme outcome can complete a seal without overflow");
        var latentChapter=new AttunementProfile.Chapter("latent_chapter",AscendanceTiers.LATENT.id().toString(),
                AscendanceTiers.DORMANT.id().toString(),1,chapter.categories(),chapter.activities());
        var latentPlayer=new PlayerEssenceData();latentPlayer.attunement().chapter(latentChapter.id());
        apply(latentPlayer.attunement(),"ordinary-play","deal_damage","minecraft:zombie",Double.MAX_VALUE,0,latentChapter);
        check(AttunementService.shouldPromoteAutomatically(latentPlayer,latentChapter),"Completed Latent seal did not request automatic promotion");
        latentPlayer.setTier(AscendanceTiers.DORMANT);
        check(!AttunementService.shouldPromoteAutomatically(latentPlayer,latentChapter),"Powered chapters attempted automatic Ascension");
        duplicate.setRejectedHealingMicros(42_000_000);
        check(duplicate.discover("biome:forest")&&!duplicate.discover("biome:forest"),"Discoveries once per chapter");
        var reload=AttunementLedger.load(duplicate.save());
        check(reload.progress(UTI)==duplicate.progress(UTI)&&reload.rejectedHealingMicros()==42_000_000,"NBT persistence keeps exact progress and rejected healing debt");
        check(!reload.discover("biome:forest")&&!apply(reload,"anvil","repair_equipment","repair",20,0,chapter).credited(),"Reconnect preserves discovery and root deduplication");
        var sourceReload=AttunementLedger.load(repeats.save());
        check(apply(sourceReload,"after-restart","defeat_enemies","minecraft:zombie",10,0,chapter).repetitionMultiplier()<1,"Repetition survives restart");
        for(int i=0;i<2000;i++)apply(reload,"bounded"+i,"run","region:"+i,1,0,chapter);
        check(reload.save().getList("roots",10).size()<=1024,"Root identity ledger bounded");
        check(reload.save().getCompound("history").getList(MOB,10).size()<=AttunementLedger.MAX_HISTORY,"Recent source history bounded");
        volumeAndCategoryHistory(chapter);
        var data=new PlayerEssenceData();data.setTier(AscendanceTiers.DORMANT);data.attunement().chapter(chapter.id());
        apply(data.attunement(),"earned","deal_damage","enemy",10,0,chapter);
        data.setAvailable(EssenceTypes.OFFENSE,1000);
        check(AttunementService.investment(data,OFF)==0,"Wallet never accelerates");
        check(data.invest(EssenceStats.MELEE_DAMAGE,300),"Valid Bonus investment");
        long developedBonus = AttunementService.investment(data, OFF);
        check(developedBonus > 0, "Realized Bonus development accelerates Attunement");
        var paid=new SkillPurchase(EssenceTypes.OFFENSE.id(),List.of(17L,31L,79L));
        data.recordSkillPurchase(ResourceLocation.parse("test:historical_skill"),paid);
        check(AttunementService.investment(data,OFF)==developedBonus+127,"Investment uses actual historical receipts even without a current skill definition");
        long earned=data.attunement().progress(OFF);
        check(data.applyNexusTransaction(Map.of(),Map.of(EssenceTypes.OFFENSE.id(),1000L),data.getOwnedSkills(),data.getLoadoutSelections(),data.getTierId()),"Bonus respec committed");
        check(data.attunement().progress(OFF)==earned&&AttunementService.investment(data,OFF)==127,"Respec preserves earned progress and changes future acceleration");
        var playerReload=PlayerEssenceData.load(data.save());
        check(playerReload.attunement().progress(OFF)==earned,"Saved player state survives death/respawn/reconnect shared UUID storage");
        check(data.applyNexusTransaction(data.getAllInvested(),data.getAllAvailable(),data.getOwnedSkills(),data.getLoadoutSelections(),AscendanceTiers.AWAKENED.id()),"Tier commit succeeds");
        check(data.attunement().progress(OFF)==0&&data.getAvailable(EssenceTypes.OFFENSE)==1000,"Ascension clears chapter without consuming Essence");
        data.attunement().chapter("next");check(data.getTierId().equals(AscendanceTiers.AWAKENED.id()),"Empty chapter never revokes earned tier");
        reload.chapter("next");check(reload.progress(UTI)==0&&reload.discover("biome:forest"),"No activity overflow; discoveries reset each chapter");
        check(!data.save().contains("completed_attunements"),"Obsolete skill Attunement persistence removed");
        var methodDefinitions=new LinkedHashMap<String,AttunementProfile.Method>();
        for(var a:AttunementActivityRegistry.values())methodDefinitions.put(a.id(),new AttunementProfile.Method(a.id(),a.categoryId(),a.units(),a.calibrationFamily(),a.labelKey(),a.descriptionKey(),a.baseGameAccessible()));
        var missing=new AttunementProfile(POLICY,Map.of(),methodDefinitions,Map.of(),List.of());
        check(!AttunementService.snapshot(new PlayerEssenceData(),missing).maximumTier(),"Missing nonmax generated chapter is unavailable, not maximum tier");
        var finalPlayer=new PlayerEssenceData();finalPlayer.setTier(AscendanceTiers.TRANSCENDENT);
        check(AttunementService.snapshot(finalPlayer,missing).maximumTier()&&AttunementService.snapshot(finalPlayer,missing).categories().stream().allMatch(AttunementSnapshot.Category::completed),"Real maximum tier displays completed registry constellation");

        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("AttunementAccountingTest: "+assertions+" checks PASS");
    }
    private static AttunementProfile.Chapter chapter() {
        Map<String,AttunementProfile.Category> categories=new LinkedHashMap<>();
        for(var e:EssenceRegistry.values())categories.put(e.id().toString(),new AttunementProfile.Category(e.id().toString(),100_000,1000));
        Map<String,AttunementProfile.Rate> rates=new LinkedHashMap<>();
        for(var a:AttunementActivityRegistry.values())rates.put(a.id(),new AttunementProfile.Rate(a.id(),a.categoryId(),1,1,a.units(),"test evidence"));
        return new AttunementProfile.Chapter("chapter",AscendanceTiers.DORMANT.id().toString(),AscendanceTiers.AWAKENED.id().toString(),2,categories,rates);
    }
    private static void volumeAndCategoryHistory(AttunementProfile.Chapter chapter) {
        var whole = new AttunementLedger(); whole.chapter(chapter.id());
        var split = new AttunementLedger(); split.chapter(chapter.id());
        apply(whole,"whole","run","region:room",100,0,chapter);
        for (int i=0;i<1000;i++) apply(split,"part"+i,"run","region:room",.1,0,chapter);
        check(Math.abs(whole.progress(MOB)-split.progress(MOB))<=1000,"Packet subdivision changed distance credit beyond fixed-point rounding");
        var nextWhole=apply(whole,"next","run","region:room",1,0,chapter);
        var nextSplit=apply(split,"next","run","region:room",1,0,chapter);
        check(Math.abs(nextWhole.repetitionMultiplier()-nextSplit.repetitionMultiplier())<1e-10,"Packet count changed persistent repetition pressure");
        apply(whole,"variation","run","region:other",10,0,chapter);
        apply(split,"variation","run","region:other",10,0,chapter);
        long wholeBefore=whole.progress(MOB), splitBefore=split.progress(MOB);
        apply(whole,"varied-whole","run","region:room",10,0,chapter);
        for(int i=0;i<1000;i++) apply(split,"varied-split"+i,"run","region:room",.01,0,chapter);
        check(Math.abs((whole.progress(MOB)-wholeBefore)-(split.progress(MOB)-splitBefore))<=1000,
                "Packet subdivision changed variety-adjusted credit beyond fixed-point rounding");
        apply(split,"hit","deal_damage","zombie",10,0,chapter);
        apply(split,"xp","gain_experience","xp",10,0,chapter);
        for (int i=0;i<1000;i++) apply(split,"more"+i,"run","region:road",.1,0,chapter);
        check(!split.recent(OFF).isEmpty()&&!split.recent(UTI).isEmpty(),"Movement evicted other category histories");
        var saved=AttunementLedger.load(split.save());
        check(saved.recent(OFF).equals(split.recent(OFF))&&saved.recent(UTI).equals(split.recent(UTI)),"Category audit history lost across restart");
        check(saved.recent(MOB).size()==32,"Per-category audit history is not bounded");
        var two=new AttunementLedger(); two.chapter(chapter.id());
        apply(two,"a","run","a",8,0,chapter); apply(two,"b","run","b",8,0,chapter);
        check(apply(two,"c","run","a",1,0,chapter).varietyMultiplier()>1.1,"Two substantial sources failed to earn meaningful variety");
    }
    private static AttunementContribution apply(AttunementLedger ledger,String root,String method,String source,double value,long investment,AttunementProfile.Chapter chapter) {
        return ledger.contribute(root,AttunementEvent.Outcome.eligible(method,source,value),chapter,POLICY,investment);
    }
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
}
