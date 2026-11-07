package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.gathering.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mojang.authlib.GameProfile;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Actual default skill growth/rewards and native farmland fallOn in a disposable world.
 * Controls skill ownership, loaded farm/water geometry, RNG seed and pulse time, never success facts. */
public final class FarmingAchievementNativeFixture {
    private static int checks;
    private static boolean cancelItemSpawns;
    private static int canceledItemSpawns;
    private static final List<Integer> IDS=List.of(128,131,204);
    private FarmingAchievementNativeFixture() { }
    public static void run(ServerLevel level,ServerPlayer observer)throws Exception {
        checks=0;cancelItemSpawns=false;canceledItemSpawns=0;
        EntityEvent.ADD.register((entity,world)->{
            if(cancelItemSpawns&&entity instanceof ItemEntity){canceledItemSpawns++;return EventResult.interruptFalse();}
            return EventResult.pass();
        });
        ServerPlayer p=player(level,"AchFarming");online(p,true);
        try {
            for(int id:IDS){check(advancement(p,id)!=null,"Native farming advancement exists "+id);revoke(p,id);}
            growth(level,p,observer);pocket(level,p,observer);trample(level,p,observer);
            p.getAdvancements().save();
            for(int id:IDS){check(done(p,id),"Actual farming completion before native save "+id);revoke(p,id);}
            p.getAdvancements().reload(level.getServer().getAdvancements());
            for(int id:IDS)check(done(p,id),"Native advancement JSON restores farming completion "+id);
            System.out.println("FarmingAchievementNativeFixture: "+checks+" assertions passed; actual five-crop default Verdant Stride advances, Pocket Nets swimming inventory/overflow rewards and canceled spawn rejection, transformed farmland fallOn mature preservation and unprotected destruction, creative/admin rejection, advancement save/reload; controlled geometry/RNG/pulse time");
        }finally{cancelItemSpawns=false;SkillEffectRuntime.reset(p);online(p,false);p.discard();}
    }
    private static void growth(ServerLevel level,ServerPlayer p,ServerPlayer observer)throws Exception {
        enable(p,SkillIds.VERDANT_STRIDE);p.setPos(250.5,120,250.5);
        Block[] kinds={Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,Blocks.BEETROOTS,Blocks.TORCHFLOWER_CROP};
        BlockPos[] positions={new BlockPos(251,120,250),new BlockPos(249,120,250),new BlockPos(250,120,251),new BlockPos(250,120,249),new BlockPos(251,120,251)};
        for(int i=0;i<kinds.length;i++)plant(level,positions[i],kinds[i].defaultBlockState());
        var wheat=(CropBlock)Blocks.WHEAT;
        wheat.performBonemeal(level,level.random,positions[0],level.getBlockState(positions[0]));
        check(!done(p,128)&&!NbtVersionCompat.getCompound(data(p).achievementProgress().save(),"entries").contains("a128"),"Native growth outside Verdant invocation does not create an achievement receipt");
        level.setBlockAndUpdate(positions[0],wheat.getStateForAge(0));
        var tuning=SkillEffectRuntime.context(p).settings().gathering().verdantStride();
        check(tuning.radiusBlocks()>2&&tuning.growthChance()>0,"Default generated Verdant radius/chance permit five ordinary nearby crop types");
        p.getRandom().setSeed(70128);
        for(int pulse=0;pulse<4096&&!done(p,128);pulse++){
            advance(level,tuning.growthPulseTicks());GatheringRuralService.tickVerdantStride(SkillEffectRuntime.context(p));
        }
        check(done(p,128),"Actual default Verdant invocations advance five distinct ordinary crop types");
        check(!done(observer,128),"Nearby observer gets no growth credit");
        var partial=data(p).achievementProgress().save();p.getAdvancements().save();revoke(p,128);
        for(int i=0;i<5;i++){advance(level,tuning.growthPulseTicks());GatheringRuralService.tickVerdantStride(SkillEffectRuntime.context(p));}
        check(!done(p,128),"Saturated genuine growth collection cannot replay native revoke");
        reloadRecord(p);check(data(p).achievementProgress().save().equals(partial),"Bounded crop history survives actual player record roundtrip");
        p.getAdvancements().reload(level.getServer().getAdvancements());
        check(done(p,128),"Native JSON restores earlier genuinely earned crop completion");
    }
    private static void pocket(ServerLevel level,ServerPlayer p,ServerPlayer observer){
        enable(p,SkillIds.POCKET_NETS);p.getInventory().clearContent();p.setPos(270.5,121,270.5);
        p.setSprinting(false);p.setSwimming(false);p.baseTick();
        var tuning=SkillEffectRuntime.context(p).settings().gathering().pocketNets();
        check(tuning.dropChance()>0,"Default Pocket Nets configuration has available rewards");
        check(FishingLootService.giveOrDrop(p,List.of(ItemStack.EMPTY))==0&&!done(p,131),"Empty reward delivery creates no successful Pocket Nets outcome");
        advance(level,tuning.pulseTicks()+1);GatheringFishingService.tickPocketNets(SkillEffectRuntime.context(p));
        check(!done(p,131)&&inventoryCount(p)==0,"No successful reward while dry/inactive cannot earn swimming achievement");
        for(int x=268;x<=272;x++)for(int z=268;z<=272;z++)for(int y=119;y<=124;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState());
        p.baseTick();p.setSprinting(true);p.setSwimming(true);
        check(p.isInWater()&&p.isSwimming(),"Fixture uses actual native water and swimming flag");
        p.getRandom().setSeed(70131);
        GatheringFishingService.tickPocketNets(SkillEffectRuntime.context(p));
        for(int slot=0;slot<p.getInventory().getContainerSize();slot++)p.getInventory().setItem(slot,new ItemStack(Items.BEDROCK,64));
        int fullInventory=inventoryCount(p);cancelItemSpawns=true;
        for(int pulse=0;pulse<4096&&canceledItemSpawns==0;pulse++){
            advance(level,tuning.pulseTicks());GatheringFishingService.tickPocketNets(SkillEffectRuntime.context(p));
        }
        cancelItemSpawns=false;
        check(canceledItemSpawns>0&&inventoryCount(p)==fullInventory&&!done(p,131),"Actual canceled overflow reward spawn cannot fabricate delivered Pocket Nets credit");
        check(GatheringFishingService.pocketNetHudState(SkillEffectRuntime.context(p)).lastDrops()==0,"Canceled reward spawn remains a missing reward in existing skill HUD state");
        for(int pulse=0;pulse<4096&&!done(p,131);pulse++){
            advance(level,tuning.pulseTicks());GatheringFishingService.tickPocketNets(SkillEffectRuntime.context(p));
        }
        check(done(p,131)&&inventoryCount(p)==fullInventory,"Actual accepted overflow item spawn earns swimming reward without inventory space");
        revoke(p,131);p.getInventory().clearContent();
        for(int pulse=0;pulse<4096&&!done(p,131);pulse++){
            advance(level,tuning.pulseTicks());GatheringFishingService.tickPocketNets(SkillEffectRuntime.context(p));
        }
        check(done(p,131)&&inventoryCount(p)>0,"Actual Pocket Nets loaded loot reaches player inventory while swimming");
        check(!done(observer,131),"Nearby observer receives no Pocket Nets reward achievement");
        revoke(p,131);p.setSwimming(false);
        int before=inventoryCount(p);
        for(int pulse=0;pulse<4096&&inventoryCount(p)==before;pulse++){
            advance(level,tuning.pulseTicks());GatheringFishingService.tickPocketNets(SkillEffectRuntime.context(p));
        }
        check(inventoryCount(p)>before&&!done(p,131),"Existing water+sprint skill reward without actual swimming does not weaken the achievement condition");
        p.setSwimming(true);
        for(int pulse=0;pulse<4096&&!done(p,131);pulse++){
            advance(level,tuning.pulseTicks());GatheringFishingService.tickPocketNets(SkillEffectRuntime.context(p));
        }
        check(done(p,131),"Fresh actual swimming reward can re-earn after revoke");
        p.setSwimming(false);p.setSprinting(false);
    }
    private static void trample(ServerLevel level,ServerPlayer p,ServerPlayer observer){
        enable(p,SkillIds.VERDANT_STRIDE);p.setPos(290.5,120,290.5);p.baseTick();
        BlockPos ground=new BlockPos(290,119,290);var wheat=(CropBlock)Blocks.WHEAT;
        plant(level,ground.above(),wheat.getStateForAge(0));
        BlockState farmland=level.getBlockState(ground),immature=level.getBlockState(ground.above());
        Blocks.FARMLAND.fallOn(level,farmland,ground,p,2);
        check(level.getBlockState(ground).equals(farmland)&&level.getBlockState(ground.above()).equals(immature)&&!done(p,204),"Native protected trample under immature crop preserves blocks but cannot earn");
        var mature=wheat.getStateForAge(wheat.getMaxAge());level.setBlockAndUpdate(ground.above(),mature);
        Blocks.FARMLAND.fallOn(level,farmland,ground,p,2);
        check(done(p,204)&&level.getBlockState(ground).equals(farmland)&&level.getBlockState(ground.above()).equals(mature),"Transformed native turnToDirt prevention preserves exact farmland and mature crop");
        check(!done(observer,204),"Nearby player receives no trample credit");
        revoke(p,204);
        p.setGameMode(GameType.CREATIVE);Blocks.FARMLAND.fallOn(level,farmland,ground,p,2);
        check(!done(p,204),"Creative prevention cannot fabricate a survival achievement");
        p.setGameMode(GameType.SURVIVAL);SkillEffectRuntime.refresh(p);plant(level,ground.above(),mature);
        try(var suppression=AttunementGameplay.suppressAdministrativeOutcomes()){
            Blocks.FARMLAND.fallOn(level,level.getBlockState(ground),ground,p,2);
        }
        check(!done(p,204),"Administratively suppressed native prevention cannot fabricate a survival achievement");
        enable(p);plant(level,ground.above(),mature);
        Blocks.FARMLAND.fallOn(level,level.getBlockState(ground),ground,p,2);
        check(level.getBlockState(ground).is(Blocks.DIRT)&&!done(p,204),"Native unprotected trample actually destroys farmland and cannot grant prevention");
        enable(p,SkillIds.VERDANT_STRIDE);plant(level,ground.above(),mature);
        Blocks.FARMLAND.fallOn(level,level.getBlockState(ground),ground,p,2);
        check(done(p,204),"Fresh survival prevented trample can re-earn native revoke");
    }
    private static void plant(ServerLevel level,BlockPos pos,BlockState crop){
        level.getChunkAt(pos);level.setBlockAndUpdate(pos.below(),Blocks.FARMLAND.defaultBlockState());level.setBlockAndUpdate(pos,crop);
        level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
    }
    private static int inventoryCount(ServerPlayer p){int count=0;for(int slot=0;slot<p.getInventory().getContainerSize();slot++)count+=p.getInventory().getItem(slot).getCount();return count;}
    private static com.mistaboom.essence_ascendance.data.PlayerEssenceData data(ServerPlayer p){return EssenceSavedData.get(p.server).getPlayerData(p.getUUID());}
    @SuppressWarnings("unchecked")private static void reloadRecord(ServerPlayer p)throws Exception {
        var saved=EssenceSavedData.get(p.server);var field=EssenceSavedData.class.getDeclaredField("players");field.setAccessible(true);
        ((Map<UUID,PlayerEssenceData>)field.get(saved)).put(p.getUUID(),PlayerEssenceData.load(data(p).save()));saved.setDirty();
    }
    private static void enable(ServerPlayer p,ResourceLocation...skills){
        var data=data(p);data.setTier(AscendanceTiers.TRANSCENDENT);SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);data.clearAllSkillsForAdmin();
        Set<ResourceLocation> required=new LinkedHashSet<>(Arrays.asList(skills));var queue=new ArrayDeque<>(required);
        while(!queue.isEmpty()){var definition=SkillRegistry.require(queue.remove());for(var prerequisite:definition.prerequisiteRanks(definition.maximumRank()).keySet())if(required.add(prerequisite))queue.add(prerequisite);}
        var ordered=SkillRegistry.topologicalOrder(required);data.grantAllSkillsForAdmin(ordered);
        for(var definition:ordered){var id=definition.id();if(definition.choiceGroup()!=null)data.setLoadoutSelection(definition.choiceGroup(),id);else if(definition.activationPolicy()==SkillActivationPolicy.TOGGLE)data.setLoadoutSelection(id,id);}
        p.setGameMode(GameType.SURVIVAL);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        for(var id:skills)check(SkillEffectRuntime.context(p).isEffective(id),"Fixture native farming skill effective "+id);
    }
    private static ServerPlayer player(ServerLevel level,String name){
        var profile=new GameProfile(UUID.randomUUID(),name);var client=ClientInformation.createDefault();var p=new ServerPlayer(level.getServer(),level,profile,client);
        var transport=new Connection(PacketFlow.SERVERBOUND);new ServerGamePacketListenerImpl(level.getServer(),transport,p,new CommonListenerCookie(profile,0,client,false));
        new EmbeddedChannel(transport).close().syncUninterruptibly();return p;
    }
    @SuppressWarnings("unchecked")private static void online(ServerPlayer p,boolean add)throws Exception {
        var list=p.server.getPlayerList();var players=PlayerList.class.getDeclaredField("players");var ids=PlayerList.class.getDeclaredField("playersByUUID");players.setAccessible(true);ids.setAccessible(true);
        var online=(List<ServerPlayer>)players.get(list);var byId=(Map<UUID,ServerPlayer>)ids.get(list);
        if(add){online.add(p);byId.put(p.getUUID(),p);}else{online.remove(p);byId.remove(p.getUUID());}
    }
    private static void advance(ServerLevel level,long ticks){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+ticks);}
    private static net.minecraft.advancements.AdvancementHolder advancement(ServerPlayer p,int id){return p.server.getAdvancements().get(AchievementService.resource(id));}
    private static boolean done(ServerPlayer p,int id){return p.getAdvancements().getOrStartProgress(advancement(p,id)).isDone();}
    private static void revoke(ServerPlayer p,int id){p.getAdvancements().revoke(advancement(p,id),"achieved");}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
