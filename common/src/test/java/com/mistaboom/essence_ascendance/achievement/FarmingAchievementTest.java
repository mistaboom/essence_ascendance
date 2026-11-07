package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.gameplay.FarmingOutcome;
import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gathering.GatheringRuralService;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import java.util.*;

/** Actual rule consumers, native crop state transitions and bounded player-save progress. */
public final class FarmingAchievementTest {
    private static int checks, next;
    private static final long TICK=50;
    private static final UUID ALICE=new UUID(17,1), BOB=new UUID(17,2);
    private static final String[] CROPS={"minecraft:wheat","minecraft:carrots","minecraft:potatoes","minecraft:beetroots","minecraft:torchflower_crop","minecraft:pitcher_crop"};
    private static FarmingOutcome.Context c(UUID actor,long tick){return new FarmingOutcome.Context("farming-rule-"+ ++next,actor,"minecraft:overworld",tick);}
    private static FarmingOutcome.Context c(){return c(ALICE,TICK);}
    private static final class Harness {
        final FarmingAchievementRules rules=new FarmingAchievementRules();
        AchievementProgress state=new AchievementProgress();
        final Set<Integer> awards=new HashSet<>();
        boolean consume(FarmingOutcome fact){return rules.consume(fact,TICK,true,state,awards::add);}
    }
    public static void main(String[] args){
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        growth();rewards();trample();nativeGrowth();persistence();receipts();malformed();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("FarmingAchievementTest: "+checks+" checks passed");
    }
    private static void growth(){
        var a=new Harness();var b=new Harness();
        for(int i=0;i<4;i++)a.consume(new FarmingOutcome.CropGrowth(c(),CROPS[i],true,true));
        check(a.awards.isEmpty(),"Four successful distinct crops remain below threshold");
        a.consume(new FarmingOutcome.CropGrowth(c(),CROPS[0],true,true));
        a.consume(new FarmingOutcome.CropGrowth(c(),CROPS[4],false,true));
        a.consume(new FarmingOutcome.CropGrowth(c(),CROPS[4],true,false));
        check(a.awards.isEmpty(),"Duplicate crop, ineligible growth and false unchanged growth cannot complete");
        b.consume(new FarmingOutcome.CropGrowth(c(BOB,TICK),CROPS[4],true,true));
        check(b.awards.isEmpty()&&a.awards.isEmpty(),"Teammate's fifth type never fills another player's collection");
        a.state=AchievementProgress.load(a.state.save());
        a.consume(new FarmingOutcome.CropGrowth(c(),CROPS[4],true,true));
        check(a.awards.equals(Set.of(128)),"Fifth successful crop advance earns after player save/reload without requiring maturity");
        a.awards.clear();a.consume(new FarmingOutcome.CropGrowth(c(),CROPS[5],true,true));
        check(a.awards.isEmpty(),"Saturated collection cannot replay revoked completion on further types");
    }
    private static void rewards(){
        for(boolean swim:new boolean[]{false,true})for(int count:new int[]{-1,0,1,3}){
            var h=new Harness();h.consume(new FarmingOutcome.PocketReward(c(),"minecraft:cod",count,swim));
            check(h.awards.contains(131)==(swim&&count>0),"Actual delivered Pocket Nets reward while swimming required");
        }
    }
    private static void trample(){
        for(boolean mature:new boolean[]{false,true})for(boolean farmland:new boolean[]{false,true})for(boolean crop:new boolean[]{false,true}){
            var h=new Harness();h.consume(new FarmingOutcome.TramplePrevented(c(),mature,farmland,crop));
            check(h.awards.contains(204)==(mature&&farmland&&crop),"Prevented native trample must preserve farmland and mature crop");
        }
    }
    private static void nativeGrowth(){
        var wheat=(CropBlock)Blocks.WHEAT;
        check(GatheringRuralService.advancedCropGrowth(wheat.getStateForAge(0),wheat.getStateForAge(1)),"One native age increment is sufficient growth");
        check(!GatheringRuralService.advancedCropGrowth(wheat.getStateForAge(1),wheat.getStateForAge(1)),"Unchanged crop is not success");
        check(!GatheringRuralService.advancedCropGrowth(wheat.getStateForAge(2),wheat.getStateForAge(1)),"Age regression is not growth");
        check(!GatheringRuralService.advancedCropGrowth(wheat.getStateForAge(1),Blocks.AIR.defaultBlockState()),"A broken crop is not growth");
        check(!GatheringRuralService.advancedCropGrowth(Blocks.FARMLAND.defaultBlockState(),Blocks.DIRT.defaultBlockState()),"Trampling is not growth");
        check(!GatheringRuralService.advancedCropGrowth(Blocks.OAK_SAPLING.defaultBlockState(),Blocks.OAK_LOG.defaultBlockState()),"A tree is not a crop type");
        var torch=(CropBlock)Blocks.TORCHFLOWER_CROP;
        check(GatheringRuralService.advancedCropGrowth(torch.getStateForAge(torch.getMaxAge()-1),Blocks.TORCHFLOWER.defaultBlockState()),"Native torchflower final growth retains initial crop identity");
    }
    private static void persistence(){
        var h=new Harness();for(int i=0;i<4;i++)h.consume(new FarmingOutcome.CropGrowth(c(),CROPS[i],true,true));
        var saved=h.state.save();h.state.realDeath();check(h.state.save().equals(saved),"Actual death preserves lifetime crop growth");
        h.state=AchievementProgress.load(h.state.save());check(h.state.save().equals(saved),"Dimension/logout/restart reload preserves lifetime scalar evidence");
        h.consume(new FarmingOutcome.CropGrowth(c(),CROPS[4],true,true));check(h.awards.contains(128),"Fresh fifth receipt after lifecycle boundary completes preserved history");
    }
    private static void receipts(){
        var h=new Harness();var fact=new FarmingOutcome.PocketReward(c(),"minecraft:salmon",1,true);
        check(h.consume(fact)&&h.awards.contains(131),"Fresh successful reward accepted");h.awards.clear();
        check(!h.consume(fact)&&h.awards.isEmpty(),"Duplicate native callback cannot replay completion after revoke");
        check(!h.rules.consume(new FarmingOutcome.PocketReward(c(),"minecraft:cod",1,true),TICK,false,h.state,h.awards::add),"Creative/admin ineligible operations reject");
        check(!h.rules.consume(new FarmingOutcome.PocketReward(c(ALICE,TICK+1),"minecraft:cod",1,true),TICK,true,h.state,h.awards::add),"Future receipt rejects");
        check(!h.rules.consume(new FarmingOutcome.PocketReward(c(ALICE,0),"minecraft:cod",1,true),200,true,h.state,h.awards::add),"Expired receipt rejects");
        var full=new Harness();for(int i=0;i<CommittedReceiptWindow.MAX_RECEIPTS;i++)check(full.consume(new FarmingOutcome.PocketReward(c(),"minecraft:cod",0,true)),"Bounded receipt slot accepted");
        check(!full.consume(new FarmingOutcome.PocketReward(c(),"minecraft:cod",1,true))&&full.awards.isEmpty(),"Receipt capacity fails closed without evicting live dedup evidence");
    }
    private static void malformed(){
        var entry=new CompoundTag();entry.putInt("version",1);var crops=new CompoundTag();for(int i=0;i<6;i++)crops.putBoolean(CROPS[i],true);entry.put("crops",crops);
        var entries=new CompoundTag();entries.put("a128",entry);var root=new CompoundTag();root.putInt("version",1);root.put("entries",entries);
        var malformed=AchievementProgress.load(root);check(!malformed.verdantCrop("minecraft:wheat"),"Oversized crop collection rejected locally");
        check(malformed.save().equals(root),"Malformed source remains preserved without overwriting other state");
        entry.putInt("version",2);var future=AchievementProgress.load(root);check(!future.verdantCrop("minecraft:wheat")&&future.save().equals(root),"Unknown future crop rule remains opaque and unwritable");
        var fresh=new AchievementProgress();check(!fresh.verdantCrop("BAD ID")&&!fresh.verdantCrop("a".repeat(257)),"Invalid crop registry identities reject");
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
