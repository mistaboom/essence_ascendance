package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.utility.BondedCompanionService;
import com.mojang.authlib.GameProfile;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
//? if >=26.1.2 {
/*import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
*///? } else {
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
//? }
import net.minecraft.world.level.GameType;
import java.util.*;

/** Disposable-world native attacks, real taming/ownership changes and measured cat damage. */
public final class CompanionAchievementNativeFixture {
    private static int checks;
    private static final List<Mob> MOBS=new ArrayList<>();
    private static final Set<UUID>CANCELED=new HashSet<>();
    private CompanionAchievementNativeFixture() { }
    public static void run(ServerLevel level,ServerPlayer observer)throws Exception {
        checks=0;
        EntityEvent.LIVING_HURT.register((victim,source,amount)->CANCELED.contains(victim.getUUID())?EventResult.interruptFalse():EventResult.pass());
        var alice=player(level,"AchCompanionA");var bob=player(level,"AchCompanionB");
        online(alice,true);online(bob,true);
        try{
            position(alice,160,120,160);position(bob,160,120,164);
            // OwnableEntity resolves owners through the native level entity/player index.
            level.addNewPlayer(alice);level.addNewPlayer(bob);
            for(int id:List.of(118,119)){check(advancement(alice,id)!=null,"Native companion resource exists "+id);revoke(alice,id);revoke(bob,id);}
            bonded(level,alice,bob);cats(level,alice,bob);
            for(int id:List.of(118,119))check(!done(observer,id),"Nearby observer never receives another owner's companion award "+id);
            alice.getAdvancements().save();for(int id:List.of(118,119))revoke(alice,id);
            alice.getAdvancements().reload(level.getServer().getAdvancements());
            for(int id:List.of(118,119))check(done(alice,id),"Native advancement JSON restores companion completion "+id);
            System.out.println("CompanionAchievementNativeFixture: "+checks+" assertions passed; real empowered pet native kills, ownership transfer, two owners/pets, deterministic bound cats, accepted/canceled damage, missing cat, between-encounter invalidation, offline owner invalidation, saved cat-bound partial and native advancement save/reload");
        }finally{
            CANCELED.clear();MOBS.forEach(Mob::discard);MOBS.clear();
            SkillEffectRuntime.reset(alice);SkillEffectRuntime.reset(bob);online(alice,false);online(bob,false);alice.discard();bob.discard();
        }
    }
    private static void bonded(ServerLevel level,ServerPlayer alice,ServerPlayer bob){
        var wolf=spawn(level,new Wolf(EntityType.WOLF,level),alice.getX()+1,alice.getY(),alice.getZ());wolf.tame(alice);
        var victim=zombie(level,alice);victim.setHealth(1);petAttack(wolf,victim);
        check(!done(alice,118),"Tamed creature's genuine hostile kill without actual empowerment cannot earn");
        enable(alice,SkillIds.BONDED_COMPANION);wolf.tick();
        check(BondedCompanionService.empoweredOwner(wolf)==alice,"Native loaded creature tick applies real configured Bonded Companion empowerment");
        victim=zombie(level,alice);victim.setHealth(1);CANCELED.add(victim.getUUID());petAttack(wolf,victim);CANCELED.clear();
        check(victim.isAlive()&&!done(alice,118),"Canceled pet attack cannot fabricate confirmed hostile death");
        petAttack(wolf,victim);
        check(!victim.isAlive()&&done(alice,118)&&!done(bob,118),"Empowered pet's actual native hostile final blow credits its owner only");
        revoke(alice,118);wolf.tame(bob);victim=zombie(level,bob);victim.setHealth(1);petAttack(wolf,victim);
        check(!done(alice,118)&&!done(bob,118),"Ownership transfer cannot credit previous owner or unequipped new owner through stale modifiers");
        enable(bob,SkillIds.BONDED_COMPANION);wolf.tick();victim=zombie(level,bob);victim.setHealth(1);petAttack(wolf,victim);
        check(done(bob,118)&&!done(alice,118),"New owner's actual effective empowerment earns only for that owner");
        wolf.tame(alice);wolf.tick();victim=zombie(level,alice);victim.setHealth(1);
        try(var ignored=AttunementGameplay.suppressAdministrativeOutcomes()){petAttack(wolf,victim);}
        check(!done(alice,118),"Administrative-scope pet death produces no survival success");
        victim=zombie(level,alice);victim.setHealth(1);petAttack(wolf,victim);check(done(alice,118),"Fresh real empowered pet kill can re-earn revoked completion");
        wolf.discard();
    }
    private static void cats(ServerLevel level,ServerPlayer alice,ServerPlayer bob)throws Exception{
        enable(alice);enable(bob);
        var selected=spawn(level,new Cat(EntityType.CAT,level),alice.getX()+1,alice.getY(),alice.getZ());selected.tame(alice);
        var farther=spawn(level,new Cat(EntityType.CAT,level),alice.getX()+3,alice.getY(),alice.getZ());farther.tame(alice);
        var otherOwner=spawn(level,new Cat(EntityType.CAT,level),alice.getX()+.5,alice.getY(),alice.getZ());otherOwner.tame(bob);
        selected.tick();farther.tick();otherOwner.tick();
        kill(level,alice);check(selected.getUUID().equals(data(alice).achievementProgress().selectedCat()),"Native first hostile kill selects nearest own cat instead of closer teammate cat");
        kill(level,alice);kill(level,alice);kill(level,alice);check(!done(alice,119),"Two owned cats still count each confirmed native kill once");
        CANCELED.add(selected.getUUID());hurt(selected,selected.damageSources().generic(),1);CANCELED.clear();
        check(selected.getUUID().equals(data(alice).achievementProgress().selectedCat()),"Canceled cat damage never invalidates verified collection");
        selected.setPos(alice.getX()+20,alice.getY(),alice.getZ());kill(level,alice);
        check(!done(alice,119)&&selected.getUUID().equals(data(alice).achievementProgress().selectedCat()),"Chosen cat outside radius rejects kill without swapping to another cat");
        selected.setPos(alice.getX()+1,alice.getY(),alice.getZ());
        hurt(farther,farther.damageSources().generic(),1);
        check(selected.getUUID().equals(data(alice).achievementProgress().selectedCat()),"Actual damage to a different owned cat does not reset the bound cat");
        var partial=data(alice).achievementProgress().save();reloadRecord(alice);SkillEffectRuntime.reset(alice);SkillEffectRuntime.refresh(alice);
        check(partial.equals(data(alice).achievementProgress().save()),"Real player-record roundtrip and runtime reset retain verified four cat-bound kills");
        selected.tick();kill(level,alice);check(done(alice,119)&&!done(bob,119),"Fifth real native player kill with bound undamaged cat earns only for killer-owner");
        revoke(alice,119);selected.tame(bob);selected.tame(alice);
        check(data(alice).achievementProgress().selectedCat()==null,"Native ownership transfer away and back in same tick invalidates old verified collection");
        selected.tick();for(int i=0;i<4;i++)kill(level,alice);
        int timeout=SkillEffectRuntime.context(alice).settings().vitality().innerSustenance().combatTimeoutTicks();advance(level,timeout);
        online(alice,false);hurt(selected,selected.damageSources().generic(),1);online(alice,true);
        check(data(alice).achievementProgress().selectedCat()==null,"Actual cat damage between encounters invalidates saved collection while owner offline");
        selected.tick();kill(level,alice);check(!done(alice,119),"Same-tick cat damage at encounter start prevents false protected kill");
        advance(level,timeout);selected.tick();farther.tick();otherOwner.tick();
        for(int i=0;i<4;i++)kill(level,alice);check(!done(alice,119),"Fresh challenge after damage needs all five new confirmed kills");
        selected.discard();check(data(alice).achievementProgress().selectedCat()==null,"Actual bound cat disappearance by discard closes its collection");
        farther.tick();for(int i=0;i<5;i++)kill(level,alice);check(done(alice,119),"A newly selected cat can complete a fresh collection after prior cat removal");
    }
    private static void kill(ServerLevel level,ServerPlayer actor){var victim=zombie(level,actor);hurt(victim,actor.damageSources().playerAttack(actor),1000);check(!victim.isAlive(),"Native player damage actually commits hostile death");}
    private static boolean petAttack(Mob pet,LivingEntity target){target.invulnerableTime=0;
        //? if >=26.1.2
        /*return pet.doHurtTarget((ServerLevel)pet.level(),target);*/
        //? if <26.1.2
        return pet.doHurtTarget(target);
    }
    private static boolean hurt(LivingEntity target,DamageSource source,float amount){target.invulnerableTime=0;
        //? if >=26.1.2
        /*return target.hurtServer((ServerLevel)target.level(),source,amount);*/
        //? if <26.1.2
        return target.hurt(source,amount);
    }
    private static Mob zombie(ServerLevel level,ServerPlayer actor){
        //? if >=26.1.2
        /*return spawn(level,new net.minecraft.world.entity.monster.zombie.Zombie(EntityType.ZOMBIE,level),actor.getX()+4,actor.getY(),actor.getZ());*/
        //? if <26.1.2
        return spawn(level,new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,level),actor.getX()+4,actor.getY(),actor.getZ());
    }
    private static <T extends Mob>T spawn(ServerLevel level,T mob,double x,double y,double z){mob.setPos(x,y,z);mob.setNoAi(true);mob.setNoGravity(true);level.getChunkAt(mob.blockPosition());check(level.addFreshEntity(mob),"Native companion fixture entity enters disposable world");MOBS.add(mob);return mob;}
    private static PlayerEssenceData data(ServerPlayer p){return EssenceSavedData.get(p.server).getPlayerData(p.getUUID());}
    @SuppressWarnings("unchecked")private static void reloadRecord(ServerPlayer p)throws Exception{var saved=EssenceSavedData.get(p.server);var field=EssenceSavedData.class.getDeclaredField("players");field.setAccessible(true);((Map<UUID,PlayerEssenceData>)field.get(saved)).put(p.getUUID(),PlayerEssenceData.load(data(p).save()));saved.setDirty();}
    private static void enable(ServerPlayer p,ResourceLocation...skills){
        var data=data(p);data.setTier(AscendanceTiers.TRANSCENDENT);SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);data.clearAllSkillsForAdmin();
        Set<ResourceLocation>required=new LinkedHashSet<>(Arrays.asList(skills));var queue=new ArrayDeque<ResourceLocation>(required);
        while(!queue.isEmpty()){var definition=SkillRegistry.require(queue.remove());for(var prerequisite:definition.prerequisiteRanks(definition.maximumRank()).keySet())if(required.add(prerequisite))queue.add(prerequisite);}
        var ordered=SkillRegistry.topologicalOrder(required);data.grantAllSkillsForAdmin(ordered);
        for(var definition:ordered){var id=definition.id();if(definition.choiceGroup()!=null)data.setLoadoutSelection(definition.choiceGroup(),id);else if(definition.activationPolicy()==SkillActivationPolicy.TOGGLE)data.setLoadoutSelection(id,id);}
        p.setGameMode(GameType.SURVIVAL);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        for(var id:skills)check(SkillEffectRuntime.context(p).isEffective(id),"Native fixture effective companion skill "+id);
    }
    private static ServerPlayer player(ServerLevel level,String name){var profile=new GameProfile(UUID.randomUUID(),name);var client=ClientInformation.createDefault();var p=new ServerPlayer(level.getServer(),level,profile,client);var transport=new Connection(PacketFlow.SERVERBOUND);new ServerGamePacketListenerImpl(level.getServer(),transport,p,new CommonListenerCookie(profile,0,client,false));new EmbeddedChannel(transport).close().syncUninterruptibly();p.setGameMode(GameType.SURVIVAL);return p;}
    @SuppressWarnings("unchecked")private static void online(ServerPlayer p,boolean add)throws Exception{var list=p.server.getPlayerList();var players=PlayerList.class.getDeclaredField("players");var ids=PlayerList.class.getDeclaredField("playersByUUID");players.setAccessible(true);ids.setAccessible(true);var online=(List<ServerPlayer>)players.get(list);var byId=(Map<UUID,ServerPlayer>)ids.get(list);if(add){online.add(p);byId.put(p.getUUID(),p);}else{online.remove(p);byId.remove(p.getUUID());}}
    private static void position(ServerPlayer p,double x,double y,double z){p.setPos(x,y,z);p.serverLevel().getChunkAt(BlockPos.containing(x,y,z));}
    private static void advance(ServerLevel level,long ticks){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+ticks);}
    private static net.minecraft.advancements.AdvancementHolder advancement(ServerPlayer p,int id){return p.server.getAdvancements().get(AchievementService.resource(id));}
    private static boolean done(ServerPlayer p,int id){return p.getAdvancements().getOrStartProgress(advancement(p,id)).isDone();}
    private static void revoke(ServerPlayer p,int id){p.getAdvancements().revoke(advancement(p,id),"achieved");}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
