package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.StatProgressionService;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.EquipmentAttributeService;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.*;
import com.mistaboom.essence_ascendance.posture.PostureService;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.vitality.*;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Actual native skill/health/food/effect commits in a disposable server world.
 * Fixture controls skill ownership, inventory, time, damage requests and native respawn readiness.
 * No successful survival receipt or advancement grant is fabricated by this suite. */
public final class SurvivalAchievementNativeFixture {
    private static final List<Integer> IDS=List.of(107,110,114,176,177,193,200,209);
    private static final Set<UUID> CANCELED=new HashSet<>();
    private static final List<Mob> TARGETS=new ArrayList<>();
    private static int checks;
    private SurvivalAchievementNativeFixture() { }

    public static void run(ServerLevel level,ServerPlayer observer) throws Exception {
        checks=0;
        EntityEvent.LIVING_HURT.register((victim,source,amount)->CANCELED.contains(victim.getUUID())?EventResult.interruptFalse():EventResult.pass());
        var p=player(level,"AchSurvival");var failed=player(level,"AchReformFail");
        online(p,true);online(failed,true);
        try {
            position(p,90,120,90);position(failed,110,120,90);
            for(int id:IDS){check(advancement(p,id)!=null,"Native survival resource exists "+id);revoke(p,id);}
            p=secondWind(level,p);spirit(level,p,failed);meals(level,p);mirror(level,p);adaptation(level,p);p=ceiling(level,p);mending(level,p);
            p.getAdvancements().save();
            for(int id:IDS){check(done(p,id),"Actual survival completion before save "+id);check(!done(observer,id),"No nearby observer receives survival completion "+id);revoke(p,id);}
            p.getAdvancements().reload(level.getServer().getAdvancements());
            for(int id:IDS)check(done(p,id),"Native advancement JSON restores survival completion "+id);
            System.out.println("SurvivalAchievementNativeFixture: "+checks+" assertions passed; actual lethal health commits, Spirit reformation, same-life player-record save/reload, food completion/repair, Witch native transfer, damage ceiling debt and configured adaptation; controlled skills/time/health fixtures");
        } finally {
            CANCELED.clear();TARGETS.forEach(Mob::discard);TARGETS.clear();
            SkillEffectRuntime.reset(p);SkillEffectRuntime.reset(failed);online(p,false);online(failed,false);p.discard();failed.discard();
        }
    }

    private static ServerPlayer secondWind(ServerLevel level,ServerPlayer p) throws Exception {
        enable(p,SkillIds.SECOND_WIND);restore(p);
        var source=p.damageSources().generic();
        CANCELED.add(p.getUUID());hurt(p,source,10000);CANCELED.clear();
        check(p.isAlive()&&!done(p,176)&&!done(p,177),"Canceled native lethal request cannot fabricate Second Wind");
        float maximum=p.getMaxHealth();long now=level.getGameTime();
        int cooldown=SkillEffectRuntime.context(p).settings().vitality().deathDefiance().secondWind().cooldownTicks();
        hurt(p,source,10000);
        check(p.isAlive()&&p.getHealth()>0&&p.getHealth()<=1,"Actual lethal native health write survives at unchanged Second Wind health; health="+p.getHealth()+", cooldown="+data(p).getAbilityCooldownUntil(SkillGroups.VITALITY_DEATH_DEFIANCE));
        check(done(p,176)&&!done(p,177),"First actual Second Wind earns only first-proc achievement");
        long ready=data(p).getAbilityCooldownUntil(SkillGroups.VITALITY_DEATH_DEFIANCE);
        check(ready==now+cooldown&&p.getMaxHealth()==maximum,"Normal configured shared cooldown and maximum health remain unchanged");
        var partial=data(p).achievementProgress().save();
        reloadRecord(p);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        check(data(p).achievementProgress().save().equals(partial),"Actual player-record save/load and runtime refresh retain same-life count");
        check(data(p).getAbilityCooldownUntil(SkillGroups.VITALITY_DEATH_DEFIANCE)==ready,"Player-record reload retains normal cooldown");
        check(VitalityDeathDefianceService.interceptHealthWrite(p,source,p.getHealth(),0)==0,"Existing cooldown rejects another lethal interception");
        check(data(p).achievementProgress().save().equals(partial)&&!done(p,177),"Rejected cooldown attempt cannot increase durable same-life count");
        advance(level,cooldown);restore(p);hurt(p,source,10000);
        check(p.isAlive()&&done(p,177),"Second successful native proc in saved same life earns Death Defiance after ordinary cooldown");
        revoke(p,177);hurt(p,p.damageSources().fellOutOfWorld(),10000);
        check(!p.isAlive()&&!done(p,177),"Actual bypass death ends life without replaying same-life completion");
        p=respawn(p);enable(p,SkillIds.SECOND_WIND);hurt(p,source,10000);
        check(p.isAlive()&&p.getHealth()>0&&p.getHealth()<=1&&!done(p,177),"One successful Second Wind in a new life cannot combine with the previous life; health="+p.getHealth()+", cooldown="+data(p).getAbilityCooldownUntil(SkillGroups.VITALITY_DEATH_DEFIANCE));
        advance(level,cooldown);restore(p);hurt(p,source,10000);
        check(p.isAlive()&&done(p,177),"Two successful procs in the new life can freshly re-earn revoked completion");
        return p;
    }
    private static void spirit(ServerLevel level,ServerPlayer p,ServerPlayer failed) {
        enable(p,SkillIds.SPIRIT_WALK);restore(p);
        var tuning=SkillEffectRuntime.context(p).settings().vitality().deathDefiance().spiritWalk();
        long now=level.getGameTime();hurt(p,p.damageSources().generic(),10000);
        check(p.isAlive()&&!done(p,107),"Spirit Walk entry alone is not successful reformation");
        check(data(p).getAbilityCooldownUntil(SkillGroups.VITALITY_DEATH_DEFIANCE)==now+tuning.cooldownTicks(),"Spirit Walk retains normal shared cooldown");
        advance(level,tuning.durationTicks()-1);SkillEffectRuntime.tick(p);
        check(!done(p,107),"Active Spirit Walk cannot award before actual reformation");
        advance(level,1);SkillEffectRuntime.tick(p);
        check(p.isAlive()&&done(p,107),"Actual alive reformation earns immediately without an extra one-minute timer");
        check(p.getHealth()>=Math.min(p.getMaxHealth(),Math.max(1,p.getMaxHealth()*tuning.reformHealthFraction()))-.001,"Normal configured Spirit Walk reform health remains intact");
        enable(failed,SkillIds.SPIRIT_WALK);restore(failed);hurt(failed,failed.damageSources().generic(),10000);
        check(failed.isAlive()&&!done(failed,107),"Failed-reform actor first enters genuine Spirit Walk");
        hurt(failed,failed.damageSources().fellOutOfWorld(),10000);
        check(!failed.isAlive(),"Native bypass damage can actually kill during Spirit Walk");
        advance(level,tuning.durationTicks()+1);SkillEffectRuntime.tick(failed);
        check(!done(failed,107),"Dead/failed Spirit Walk reformation cannot award");
    }
    private static void meals(ServerLevel level,ServerPlayer p) {
        enable(p,SkillIds.FEAST_REFLEX);restore(p);p.getInventory().clearContent();
        p.getInventory().setItem(4,new ItemStack(Items.BREAD,16));p.getFoodData().setFoodLevel(5);p.getFoodData().setSaturation(0);
        advance(level,1);SkillEffectRuntime.tick(p);
        check(p.getInventory().getItem(4).getCount()==15&&!done(p,110),"Completed automatic meal outside hostile encounter does not count as a fight");
        var hostile=zombie(level,p.getX()+2,p.getY(),p.getZ());
        int cadence=new ItemStack(Items.BREAD).getUseDuration(p);
        advance(level,cadence);p.getFoodData().setFoodLevel(5);hurt(p,p.damageSources().mobAttack(hostile),1);
        check(p.getInventory().getItem(4).getCount()==14&&!done(p,110),"One genuine automatic native meal during accepted hostile damage is only one meal");
        advance(level,cadence);p.getFoodData().setFoodLevel(5);SkillEffectRuntime.tick(p);
        check(p.getInventory().getItem(4).getCount()==13&&done(p,110),"Second completed automatic meal in the same live hostile encounter earns Second Breakfast");
        check(data(p).getAbilityCooldownUntil(SkillIds.FEAST_REFLEX)==level.getGameTime()+cadence,"Automatic meal cadence remains its ordinary native generated use time");
        revoke(p,110);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        advance(level,cadence);p.getFoodData().setFoodLevel(5);SkillEffectRuntime.tick(p);
        check(!done(p,110),"Runtime/lifecycle encounter reset cannot combine an old fight meal with a later meal");
        advance(level,cadence);p.getFoodData().setFoodLevel(5);
        try(var suppression=AttunementGameplay.suppressAdministrativeOutcomes()){hurt(p,p.damageSources().mobAttack(hostile),1);}
        advance(level,cadence);p.getFoodData().setFoodLevel(5);SkillEffectRuntime.tick(p);
        check(!done(p,110),"Administrative-scope hostile damage and subsequent automatic meal cannot fabricate a survival fight");
        advance(level,cadence);p.getFoodData().setFoodLevel(5);hurt(p,p.damageSources().mobAttack(hostile),1);
        int timeout=SkillEffectRuntime.context(p).settings().vitality().innerSustenance().combatTimeoutTicks();
        advance(level,timeout);p.getFoodData().setFoodLevel(5);SkillEffectRuntime.tick(p);
        check(!done(p,110),"Automatic meal exactly at the live hostile encounter timeout cannot combine with old first meal");
        advance(level,cadence);p.getFoodData().setFoodLevel(5);hurt(p,p.damageSources().mobAttack(hostile),1);
        advance(level,cadence);p.getFoodData().setFoodLevel(5);SkillEffectRuntime.tick(p);
        check(done(p,110),"Fresh two-meal hostile encounter can re-earn revoked completion");
        p.getInventory().clearContent();restore(p);
    }
    private static void mirror(ServerLevel level,ServerPlayer p) {
        enable(p,SkillIds.STATUS_MIRROR);restore(p);p.removeAllEffects();
        var zombie=zombie(level,p.getX()+3,p.getY(),p.getZ());
        var witch=spawn(level,new Witch(EntityType.WITCH,level),p.getX()+6,p.getY(),p.getZ());
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,200),zombie);
        check(zombie.hasEffect(MobEffects.WEAKNESS)&&!p.hasEffect(MobEffects.WEAKNESS)&&!done(p,114),"Actual harmful reflection to a non-witch cannot earn UNO Reverse");
        advance(level,SkillEffectRuntime.context(p).settings().status().mirrorCooldownTicks());
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,200),witch);
        check(witch.hasEffect(MobEffects.WEAKNESS)&&!p.hasEffect(MobEffects.WEAKNESS)&&done(p,114),"Actual harmful native effect from a witch is reflected to that same witch");
        check(StatusInterceptionService.lastOutcome(p).orElseThrow().responsibleSource().equals(witch.getUUID()),"Real status outcome retains the exact source witch UUID");
        revoke(p,114);advance(level,SkillEffectRuntime.context(p).settings().status().mirrorCooldownTicks());
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100),witch);
        check(!done(p,114),"Beneficial witch effect is not harmful reflection");p.removeAllEffects();
        enable(p,SkillIds.PURE_STATE);p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,400),witch);
        check(!p.hasEffect(MobEffects.WEAKNESS)&&!done(p,114),"Actual Pure State prevention without Status Mirror transfer cannot earn reflection");
        enable(p,SkillIds.STATUS_MIRROR);witch.removeEffect(MobEffects.WEAKNESS);
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,200),witch);
        check(done(p,114),"Fresh confirmed harmful transfer to exact witch can re-earn reflection");
        revoke(p,114);advance(level,SkillEffectRuntime.context(p).settings().status().mirrorCooldownTicks());
        float witchBefore=witch.getHealth(),playerBefore=p.getHealth();
        //? if >=26.1.2
        /*MobEffects.INSTANT_DAMAGE.value().applyInstantenousEffect(level,witch,witch,p,0,1);*/
        //? if <26.1.2
        MobEffects.HARM.value().applyInstantenousEffect(witch,witch,p,0,1);
        check(witch.getHealth()<witchBefore&&p.getHealth()==playerBefore&&done(p,114),"Actual instant harmful native reflection damages the same witch and preserves player health");
    }
    private static void adaptation(ServerLevel level,ServerPlayer p) {
        enable(p,SkillIds.ADAPTIVE_GUARD);restore(p);p.removeAllEffects();
        var hostile=zombie(level,p.getX()+2,p.getY(),p.getZ());var source=p.damageSources().mobAttack(hostile);
        int maximum=SkillEffectRuntime.context(p).settings().posture().adaptive().maximumStacks();
        CANCELED.add(p.getUUID());hurt(p,source,1);CANCELED.clear();
        check(PostureService.snapshot(p).stacks()==0&&!done(p,200),"Canceled damage cannot create adaptive stacks");
        for(int i=1;i<=maximum;i++){
            advance(level,1);restore(p);hurt(p,source,1);
            check(PostureService.snapshot(p).stacks()==i,"Native accepted damage reaches exact configured adaptation stack "+i);
            check(done(p,200)==(i==maximum),"Adaptive achievement follows effective live maximum "+i+"/"+maximum);
        }
        revoke(p,200);advance(level,1);restore(p);hurt(p,source,1);
        check(!done(p,200),"Unchanged saturated adaptation cannot replay revoked completion");
        advance(level,SkillEffectRuntime.context(p).settings().posture().adaptive().windowTicks()+1);
        for(int i=1;i<=maximum;i++){restore(p);hurt(p,source,1);advance(level,1);}
        check(done(p,200),"Fresh below-cap to configured-maximum native adaptation can re-earn completion");
    }
    private static ServerPlayer ceiling(ServerLevel level,ServerPlayer p) throws Exception {
        enable(p,SkillIds.DAMAGE_CEILING);restore(p);
        float maximum=p.getMaxHealth();ceilingHit(p);
        var ledger=VitalityDamageService.ledger(p);
        check(p.isAlive()&&ledger.traumaFraction>0&&p.getMaxHealth()<maximum&&!done(p,193),"Real routed damage commits its own reduced maximum-capacity debt episode");
        double debt=ledger.traumaFraction;
        long investment=EssenceConfigManager.get().balanceProfile().getInvestmentCap(data(p).getTier(),EssenceStats.MAX_HEALTH);
        var chest=new ItemStack(AscendanceItems.ASCENDANCE_CHESTPLATE.get());EquipmentTierData.setTier(chest,EquipmentTier.TRANSCENDENT);
        p.setItemSlot(EquipmentSlot.CHEST,chest);EquipmentAttributeService.sync(p);
        data(p).setAvailable(EssenceTypes.VITALITY,Math.max(1_000_000_000L,investment));float reducedMaximum=p.getMaxHealth();
        var purchase=StatProgressionService.invest(p,EssenceStats.MAX_HEALTH,investment);
        check(purchase.success(),"Fixture real Vitality purchase succeeds: "+purchase);
        check(p.getMaxHealth()>reducedMaximum,"Purchased Vitality applies through actual worn Ascendance equipment: before="+reducedMaximum+", after="+p.getMaxHealth());
        check(ledger.traumaFraction==debt&&!done(p,193),"Actual Vitality purchase cannot fake Damage Ceiling recovery: priorDebt="+debt+", currentDebt="+ledger.traumaFraction+", achieved="+done(p,193));
        p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);EquipmentAttributeService.sync(p);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(p.getAttribute(Attributes.MAX_HEALTH).getBaseValue()+100);
        VitalityDamageService.reconcileTrauma(p);
        check(p.getMaxHealth()>maximum&&ledger.traumaFraction==debt&&!done(p,193),"Growing ordinary maximum beyond old capacity cannot fake recovery while Ceiling debt remains");
        var partial=data(p).achievementProgress().save();reloadRecord(p);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        check(data(p).achievementProgress().save().equals(partial)&&VitalityDamageService.ledger(p).traumaFraction==debt,"Actual player-record reload preserves debt and same-life recovery checkpoint");
        int remaining=VitalityDamageService.ledger(p).traumaQuietTicks;
        for(int i=0;i<remaining;i++){advance(level,1);SkillEffectRuntime.tick(p);}
        check(p.isAlive()&&VitalityDamageService.ledger(p).traumaFraction==0&&done(p,193),"Damage Ceiling's actual quiet-tick recovery reaches zero debt alive and awards");
        revoke(p,193);restore(p);ceilingHit(p);
        remaining=VitalityDamageService.ledger(p).traumaQuietTicks;
        for(int i=1;i<remaining;i++){advance(level,1);SkillEffectRuntime.tick(p);}
        try(var suppression=AttunementGameplay.suppressAdministrativeOutcomes()){advance(level,1);SkillEffectRuntime.tick(p);}
        check(VitalityDamageService.ledger(p).traumaFraction==0&&!done(p,193),"Ineligible natural debt zero disarms an earlier legitimate capacity episode without awarding");
        restore(p);try(var suppression=AttunementGameplay.suppressAdministrativeOutcomes()){ceilingHit(p);}
        remaining=VitalityDamageService.ledger(p).traumaQuietTicks;
        check(VitalityDamageService.ledger(p).traumaFraction>0,"Suppressed fixture native hit still preserves ordinary damage-ceiling gameplay debt");
        for(int i=0;i<remaining;i++){advance(level,1);SkillEffectRuntime.tick(p);}
        check(VitalityDamageService.ledger(p).traumaFraction==0&&!done(p,193),"Eligible zero after a suppressed new debt episode cannot inherit an old armed checkpoint");
        restore(p);ceilingHit(p);remaining=VitalityDamageService.ledger(p).traumaQuietTicks;
        for(int i=0;i<remaining;i++){advance(level,1);SkillEffectRuntime.tick(p);}
        check(done(p,193),"Fresh legitimate native episode can recover after ineligible baseline disarmed the old episode");
        revoke(p,193);restore(p);ceilingHit(p);
        check(VitalityDamageService.ledger(p).traumaFraction>0,"A new real Damage Ceiling episode starts after revoke");
        hurt(p,p.damageSources().fellOutOfWorld(),Float.MAX_VALUE);
        check(!p.isAlive(),"Actual native bypass hit kills the capacity-debt actor; health="+p.getHealth());
        check(VitalityDamageService.ledger(p).traumaFraction==0&&!done(p,193),"Native actual death clears gameplay debt without falsely awarding recovery; debt="+VitalityDamageService.ledger(p).traumaFraction+", achieved="+done(p,193));
        p=respawn(p);enable(p,SkillIds.DAMAGE_CEILING);restore(p);ceilingHit(p);
        remaining=VitalityDamageService.ledger(p).traumaQuietTicks;
        for(int i=0;i<remaining;i++){advance(level,1);SkillEffectRuntime.tick(p);}
        check(done(p,193),"Fresh real debt episode in a new life can recover and re-earn");
        return p;
    }
    private static void ceilingHit(ServerPlayer p){
        double fraction=SkillEffectRuntime.context(p).settings().vitality().damage().damageCeiling().damageTakenFraction();
        float before=p.getHealth();hurt(p,p.damageSources().generic(),(float)(before*.5/fraction));
        check(p.isAlive()&&Math.abs(p.getHealth()-before*.5)<.002,"Native Damage Ceiling health damage retains its configured proportional equation");
    }
    private static void mending(ServerLevel level,ServerPlayer p) throws Exception {
        enable(p,SkillIds.METABOLIC_MENDING);restore(p);p.getInventory().clearContent();position(p,90,-10,90);
        consume(p,Items.BREAD);check(!done(p,209),"Native food with no equipment repair cannot count underground");
        var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.setDamageValue(1000);p.setItemInHand(InteractionHand.MAIN_HAND,tool);
        var noOpFood=new ItemStack(Items.BREAD);var partialBefore=data(p).achievementProgress().save();
        ConsumableRecoveryService.complete(p,noOpFood,()->noOpFood);
        check(partialBefore.equals(data(p).achievementProgress().save()),"No-op native consumption finish cannot fabricate a distinct repaired food receipt");
        position(p,90,0,90);consume(p,Items.APPLE);
        check(tool.getDamageValue()<1000&&!done(p,209),"Actual repair at Y=0 is excluded from below-zero collection");
        position(p,90,-10,90);
        Item[] foods={Items.BREAD,Items.APPLE,Items.COOKED_BEEF,Items.BAKED_POTATO,Items.CARROT};
        for(int i=0;i<foods.length;i++){
            tool=p.getMainHandItem();tool.setDamageValue(1000);int before=tool.getDamageValue();consume(p,foods[i]);
            check(tool.getDamageValue()<before,"Actual native food completion repairs equipment below zero "+foods[i]);
            check(done(p,209)==(i==4),"Only five distinct positive-repair foods reach collection boundary "+i);
            if(i==1){var partial=data(p).achievementProgress().save();reloadRecord(p);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);check(partial.equals(data(p).achievementProgress().save()),"Actual player-record save/load retains distinct repaired food collection");}
            if(i==0){tool.setDamageValue(1000);consume(p,foods[i]);check(!done(p,209),"Repeated same positive-repair food is not a new distinct type");}
        }
    }
    private static void consume(ServerPlayer p,Item item){p.getFoodData().setFoodLevel(5);new ItemStack(item).finishUsingItem(p.level(),p);}
    private static PlayerEssenceData data(ServerPlayer p){return EssenceSavedData.get(p.server).getPlayerData(p.getUUID());}
    @SuppressWarnings("unchecked") private static void reloadRecord(ServerPlayer p)throws Exception {
        var saved=EssenceSavedData.get(p.server);var field=EssenceSavedData.class.getDeclaredField("players");field.setAccessible(true);
        ((Map<UUID,PlayerEssenceData>)field.get(saved)).put(p.getUUID(),PlayerEssenceData.load(data(p).save()));saved.setDirty();
    }
    private static void enable(ServerPlayer p,ResourceLocation... skills){
        var data=data(p);data.setTier(AscendanceTiers.TRANSCENDENT);SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);data.clearAllSkillsForAdmin();
        Set<ResourceLocation> required=new LinkedHashSet<>(Arrays.asList(skills));var queue=new ArrayDeque<ResourceLocation>(required);
        while(!queue.isEmpty()){var definition=SkillRegistry.require(queue.remove());for(var prerequisite:definition.prerequisiteRanks(definition.maximumRank()).keySet())if(required.add(prerequisite))queue.add(prerequisite);}
        var ordered=SkillRegistry.topologicalOrder(required);data.grantAllSkillsForAdmin(ordered);
        for(var definition:ordered){var id=definition.id();if(definition.choiceGroup()!=null)data.setLoadoutSelection(definition.choiceGroup(),id);else if(definition.activationPolicy()==SkillActivationPolicy.TOGGLE)data.setLoadoutSelection(id,id);}
        p.setGameMode(GameType.SURVIVAL);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        for(var id:skills)check(SkillEffectRuntime.context(p).isEffective(id),"Fixture native survival skill effective "+id);
    }
    private static void restore(ServerPlayer p){p.invulnerableTime=0;p.setAbsorptionAmount(0);p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);}
    private static ServerPlayer respawn(ServerPlayer p){
        UUID actor=p.getUUID();
        // Use the real respawn request owner: it both calls PlayerList.respawn and
        // rebinds the packet listener, then restarts the native client-load handshake.
        p.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        var replacement=p.server.getPlayerList().getPlayer(actor);
        nativeReady(replacement);
        check(replacement!=p&&replacement.getUUID().equals(actor)&&replacement.isAlive(),"Actual native PlayerList respawn replaces the body and preserves the saved player identity");
        position(replacement,90,120,90);return replacement;
    }
    private static void position(ServerPlayer p,double x,double y,double z){p.setPos(x,y,z);p.setDeltaMovement(Vec3.ZERO);p.serverLevel().getChunkAt(BlockPos.containing(x,y,z));}
    private static boolean hurt(ServerPlayer p,DamageSource source,float amount){p.invulnerableTime=0;
        //? if >=26.1.2
        /*return p.hurtServer(p.serverLevel(),source,amount);*/
        //? if <26.1.2
        return p.hurt(source,amount);
    }
    private static Mob zombie(ServerLevel level,double x,double y,double z){
        //? if >=26.1.2
        /*return spawn(level,new net.minecraft.world.entity.monster.zombie.Zombie(EntityType.ZOMBIE,level),x,y,z);*/
        //? if <26.1.2
        return spawn(level,new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,level),x,y,z);
    }
    private static <T extends Mob>T spawn(ServerLevel level,T mob,double x,double y,double z){mob.setPos(x,y,z);mob.setNoAi(true);mob.setNoGravity(true);level.getChunkAt(mob.blockPosition());check(level.addFreshEntity(mob),"Native survival fixture source enters world");TARGETS.add(mob);return mob;}
    private static ServerPlayer player(ServerLevel level,String name){
        var profile=new GameProfile(UUID.randomUUID(),name);var client=ClientInformation.createDefault();var p=new ServerPlayer(level.getServer(),level,profile,client);
        var transport=new Connection(PacketFlow.SERVERBOUND);new ServerGamePacketListenerImpl(level.getServer(),transport,p,new CommonListenerCookie(profile,0,client,false));
        new EmbeddedChannel(transport).close().syncUninterruptibly();
        nativeReady(p);return p;
    }
    private static void nativeReady(ServerPlayer p){
        // The fixture has no real joined client; acknowledge native readiness and expire
        // constructor spawn protection explicitly before exercising incoming health commits.
        check(p.connection.player==p,"Native packet listener is bound to the current respawned player body");
        try{
            var pending=ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            if(pending.get(p.connection)!=null){
                var token=ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");token.setAccessible(true);
                p.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket((Integer)token.get(p.connection)));
            }
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Fixture native teleport acknowledgement fields changed",failure);}
        //? if >=26.1.2
        /*p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());*/
        //? if >=26.1.2
        /*check(p.connection.hasClientLoaded()&&!p.isChangingDimension(),"Actual native acknowledgements finish readiness: loaded="+p.connection.hasClientLoaded()+", changingDimension="+p.isChangingDimension());*/
        //? if <26.1.2 {
        try{var protection=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");protection.setAccessible(true);protection.set(p,0);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Fixture native spawn-protection field changed",failure);}
        //? }
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
