package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.gameplay.CombatOutcome;
import com.mistaboom.essence_ascendance.gameplay.CombatOutcomes;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.projectile.MagicBoltEntity;
import com.mistaboom.essence_ascendance.projectile.ProjectileRuntime;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.utility.UtilitySanctuaryService;
import com.mistaboom.essence_ascendance.utility.UtilitySenseService;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
//? if >=26.1.2
/*import net.minecraft.world.entity.projectile.arrow.Arrow;*/
//? if <26.1.2
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Real native attack/health/death, action geometry, full skill transitions and advancement serialization.
 * Fixture setup supplies equipment, skills and controlled targets; it does not fabricate combat receipts. */
public final class CombatAchievementNativeFixture {
    private static final List<Integer> IDS = List.of(76,77,85,120,136,138,184,206,207,215);
    private static final List<Mob> TARGETS = new ArrayList<>();
    private static final Set<UUID> CANCELED_HURT = new HashSet<>(), CANCELED_DEATH = new HashSet<>();
    private static UUID captureCauseTarget;
    private static CombatOutcome.Cause capturedNativeCause;
    private static UUID relocateDuringHurt;
    private static double victimYDuringHurt=Double.NaN, actorYDuringHurt=Double.NaN;
    private static int checks;
    private CombatAchievementNativeFixture() { }

    public static void run(ServerLevel level, ServerPlayer player, ServerPlayer other) throws Exception {
        checks = 0;
        EntityEvent.LIVING_HURT.register((victim,source,amount) -> {
            if (victim.getUUID().equals(relocateDuringHurt)) {
                if (Double.isFinite(victimYDuringHurt)) victim.setPos(victim.getX(),victimYDuringHurt,victim.getZ());
                if (Double.isFinite(actorYDuringHurt) && source.getEntity() instanceof ServerPlayer actor)
                    actor.setPos(actor.getX(),actorYDuringHurt,actor.getZ());
            }
            if (victim.getUUID().equals(captureCauseTarget) && source.getEntity() instanceof ServerPlayer actor) {
                var cause=CombatOutcomes.currentCause(actor);
                if (cause!=null) capturedNativeCause=cause;
            }
            return CANCELED_HURT.contains(victim.getUUID()) ? EventResult.interruptFalse() : EventResult.pass();
        });
        EntityEvent.LIVING_DEATH.register((victim,source) -> {
            if (!CANCELED_DEATH.contains(victim.getUUID())) return EventResult.pass();
            victim.setHealth(1); return EventResult.interruptFalse();
        });
        for (int id : IDS) check(advancement(player,id) != null, "Native combat resource exists: " + id);
        player.setGameMode(GameType.SURVIVAL); other.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth()); other.setHealth(other.getMaxHealth());
        player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        for (var slot : List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
            player.setItemSlot(slot,ItemStack.EMPTY);
        other.setPos(90,120,30);
        try {
            glaive(level,player,other);
            burning(level,player,other);
            combustion(level,player,other);
            projectiles(level,player);
            barefoot(level,player,other);
            frostbite(level,player,other);
            desperation(level,player,other);
            sanctuary(level,player,other);
            ledger(level,player,other);
            player.getAdvancements().save();
            for (int id : IDS) {
                check(done(player,id), "Native combat completion before save reload: " + id);
                player.getAdvancements().revoke(advancement(player,id),"achieved");
            }
            player.getAdvancements().reload(level.getServer().getAdvancements());
            for (int id : IDS) check(done(player,id), "Native advancement JSON restores combat completion: " + id);
            System.out.println("CombatAchievementNativeFixture: " + checks + " assertions passed; real action geometry, native damage/death, full Frostbite, Sanctuary, Ledger, save/reload; controlled fixture setup");
        } finally {
            clearTargets();
            WeaponActionRuntime.cancel(player);
            SkillEffectRuntime.reset(player);
            player.setHealth(player.getMaxHealth());
            player.setPos(2,120,2);
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private static void glaive(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p);
        equip(p,true,EquipmentTier.TRANSCENDENT); position(p,64,120,0);
        var canceled=zombie(level,64,120,3,1); CANCELED_HURT.add(canceled.getUUID());
        action(level,p,true);
        check(canceled.getHealth()==1 && !done(p,76),"Loader/native canceled hurt cannot fabricate an accepted kill");
        CANCELED_HURT.clear(); clearTargets();
        var prevented=zombie(level,64,120,3,1); CANCELED_DEATH.add(prevented.getUUID());
        action(level,p,true);
        check(prevented.isAlive() && !done(p,76),"Loader/native canceled death with positive native damage cannot fabricate a confirmed kill");
        CANCELED_DEATH.clear(); clearTargets();
        var protectedTarget = zombie(level,64,120,3,1); protectedTarget.setInvulnerable(true);
        action(level,p,true);
        check(protectedTarget.isAlive() && !done(p,76),"Invulnerable native stab does not create a confirmed kill");
        clearTargets();

        var swappedTarget=zombie(level,64,120,3,1);
        check(WeaponActionRuntime.attempt(p,true),"Real action begins before an item swap");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        tickAction(level,p,10);
        check(swappedTarget.isAlive() && !done(p,76),"Item swap cancels pending action; later held item cannot inherit its kill");
        clearTargets(); equip(p,true,EquipmentTier.TRANSCENDENT);

        p.setGameMode(GameType.CREATIVE);
        var creative=zombie(level,64,120,3,1); action(level,p,true);
        check(!creative.isAlive() && !done(p,76),"Actual creative action damage cannot award a survival stab kill");
        clearTargets(); p.setGameMode(GameType.SURVIVAL);
        var first=zombie(level,64,120,3,1); action(level,p,true);
        check(!first.isAlive() && done(p,76) && !done(other,76),"Real fatal stab awards only its survival actor, including capped progression");
        p.getAdvancements().revoke(advancement(p,76),"achieved");
        SkillEffectRuntime.onLivingDeath(first,p.damageSources().playerAttack(p));
        check(!done(p,76),"Repeated death callback outside the accepted damage frame cannot regrant");
        clearTargets(); first=zombie(level,64,120,3,1); action(level,p,true);
        check(!first.isAlive() && done(p,76),"Fresh legitimate fatal stab can re-earn revoked one-shot completion");
        clearTargets();

        for (int i=0;i<3;i++) {
            var one=zombie(level,61,120,5,1); action(level,p,false);
            check(!one.isAlive() && !done(p,77),"Separate native swipe " + i + " cannot aggregate into one swipe");
            clearTargets();
        }
        var a=zombie(level,61,120,5,1); var b=zombie(level,64,120,6,1); var c=zombie(level,66,120,7,1);
        action(level,p,false);
        check(!a.isAlive() && !b.isAlive() && !c.isAlive() && done(p,77) && !done(other,77),
                "One actual swipe kills three distinct hostile bodies and awards only its actor");
        clearTargets();

        equip(p,true,EquipmentTier.DORMANT);
        var middle=zombie(level,64,120,4.9,1000); action(level,p,true);
        check(middle.getHealth()<1000 && done(p,136),"Current Dormant action blade contact beyond four blocks earns reach without upgrade history");
        clearTargets(); equip(p,true,EquipmentTier.TRANSCENDENT);
        var far=zombie(level,64,120,9.8,1000); action(level,p,true);
        check(far.getHealth()<1000 && done(p,138) && done(p,206),"Accepted actual long-distance blade contact earns >9 and >=8 rules");
        clearTargets();
    }

    private static void burning(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p,SkillIds.KINDLING); equip(p,false,EquipmentTier.TRANSCENDENT);
        var target=zombie(level,64,120,2,100000);
        ignite(p,target);
        target.setHealth(.001F); target.invulnerableTime=0;
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        TwoHandedEquipmentService.refresh(p);
        advance(level,20); SkillEffectRuntime.tick(p);
        check(!target.isAlive(),"Actual Kindling pulse delivers fatal damage after the initiating weapon is swapped out");
        var kills=CombatOutcomes.sequences(p.server).recent(p.getUUID(),level.getGameTime());
        final var burned=target;
        var attributed=kills.stream().filter(k->k.context().victim().equals(burned.getUUID())).findFirst().orElseThrow();
        check(attributed.context().weapon().profileId().equals("essence_ascendance:sword"),
                "Delayed burn retains the action's Ascendance sword snapshot rather than the now-held stick");
        clearTargets();

        equip(p,false,EquipmentTier.TRANSCENDENT);
        target=zombie(level,64,120,2,100000); ignite(p,target);
        target.setHealth(.001F); target.invulnerableTime=0;
        enable(other); equip(other,false,EquipmentTier.TRANSCENDENT);
        position(other,66,120,0);
        var victim=target;
        kill(other,target);
        check(CombatOutcomes.sequences(p.server).recent(other.getUUID(),level.getGameTime()).stream()
                        .anyMatch(k->k.context().victim().equals(victim.getUUID()))
                        && CombatOutcomes.sequences(p.server).recent(p.getUUID(),level.getGameTime()).stream()
                        .noneMatch(k->k.context().victim().equals(victim.getUUID())),
                "Unrelated later native final blow belongs to its actual actor, not the earlier burning source");
        clearTargets();

        enable(p,SkillIds.KINDLING,SkillIds.THREAT_SENSE,SkillIds.HUNTERS_LEDGER);
        equip(p,false,EquipmentTier.TRANSCENDENT); p.setGameMode(GameType.CREATIVE);
        var creativeBurn=zombie(level,64,120,2,100000); ignite(p,creativeBurn);
        creativeBurn.setHealth(.001F); creativeBurn.invulnerableTime=0;
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        TwoHandedEquipmentService.refresh(p); p.setGameMode(GameType.SURVIVAL);
        creativeBurn.setTarget(p);
        advance(level,20); UtilitySenseService.tickThreatSense(SkillEffectRuntime.context(p));
        check(UtilitySenseService.ledgerEligible(p,creativeBurn),
                "Creative-origin burn fixture has a genuinely eligible survival Ledger target before its fatal pulse");
        var beforePulse=EssenceSavedData.get(p.server).getPlayerData(p.getUUID()).achievementProgress().save();
        SkillEffectRuntime.tick(p);
        check(!creativeBurn.isAlive(),"Creative-origin Kindling still applies its normal fatal damage after switching to Survival");
        check(CombatOutcomes.sequences(p.server).recent(p.getUUID(),level.getGameTime()).stream()
                        .noneMatch(k->k.context().victim().equals(creativeBurn.getUUID()))
                        && beforePulse.equals(EssenceSavedData.get(p.server).getPlayerData(p.getUUID()).achievementProgress().save()),
                "Switching to Survival cannot turn a creative-origin burn into a kill fact or Ledger partial");
        clearTargets(); enable(p);
        other.setPos(90,120,30);
    }
    private static void ignite(ServerPlayer p,Mob target) {
        var settings=SkillEffectRuntime.context(p).settings().kindling();
        int hits=(settings.maxHeat()+settings.heatPerHit()-1)/settings.heatPerHit();
        check(hits>0 && hits<=1000,"Live Kindling threshold is bounded for the native fixture");
        for (int i=0;i<hits;i++) { target.invulnerableTime=0; p.attack(target); }
    }

    private static void combustion(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        for (boolean creativeOrigin:List.of(true,false)) {
            if (creativeOrigin) enable(p,SkillIds.KINDLING,SkillIds.COMBUSTION,SkillIds.THREAT_SENSE,SkillIds.HUNTERS_LEDGER);
            else enable(p,SkillIds.KINDLING,SkillIds.COMBUSTION);
            equip(p,false,EquipmentTier.TRANSCENDENT); position(p,64,120,0);
            p.setGameMode(creativeOrigin?GameType.CREATIVE:GameType.SURVIVAL);
            var seed=zombie(level,64,120,2,100000);
            var ignition=igniteWithNativeCause(p,seed);
            check(ignition.eligibleOrigin()!=creativeOrigin && ignition.weapon().profileId().equals("essence_ascendance:sword"),
                    "Actual ignition captures its own immutable mode eligibility and weapon");
            p.setGameMode(GameType.SURVIVAL);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK)); TwoHandedEquipmentService.refresh(p);
            seed.setHealth(.001F); seed.invulnerableTime=0;
            var collateral=zombie(level,65,120,3,.001F);
            if (creativeOrigin) {
                collateral.setTarget(p); advance(level,5);
                UtilitySenseService.tickThreatSense(SkillEffectRuntime.context(p));
                check(UtilitySenseService.ledgerEligible(p,collateral),
                        "Creative ignition's collateral is a genuinely tracked survival Ledger target before the blast");
            }
            var partialBefore=EssenceSavedData.get(p.server).getPlayerData(p.getUUID()).achievementProgress().save();
            enable(other); equip(other,false,EquipmentTier.TRANSCENDENT); position(other,66,120,0);
            other.setSprinting(true); kill(other,seed); other.setSprinting(false);
            check(!collateral.isAlive(),"Other player's native seed final blow triggers actual fatal Combustion collateral damage");
            var ownerKills=CombatOutcomes.sequences(p.server).recent(p.getUUID(),level.getGameTime());
            var finisherKills=CombatOutcomes.sequences(p.server).recent(other.getUUID(),level.getGameTime());
            check(finisherKills.stream().anyMatch(k->k.context().victim().equals(seed.getUUID()))
                            && finisherKills.stream().noneMatch(k->k.context().victim().equals(collateral.getUUID()))
                            && ownerKills.stream().noneMatch(k->k.context().victim().equals(seed.getUUID())),
                    "Seed's actual finisher and Combustion owner retain separate victim credit");
            if (creativeOrigin) {
                check(ownerKills.stream().noneMatch(k->k.context().victim().equals(collateral.getUUID()))
                                && partialBefore.equals(EssenceSavedData.get(p.server).getPlayerData(p.getUUID()).achievementProgress().save()),
                        "Another player's final blow cannot launder creative ignition into survival blast or Ledger credit");
            } else {
                check(ownerKills.stream().anyMatch(k->k.context().victim().equals(collateral.getUUID())
                                && k.context().actionId().equals(ignition.actionId()) && k.context().weapon().equals(ignition.weapon())),
                        "Survival ignition retains its exact captured native action and sword snapshot through another player's trigger");
            }
            clearTargets();
        }

        enable(p,SkillIds.KINDLING,SkillIds.COMBUSTION); equip(p,false,EquipmentTier.TRANSCENDENT);
        var seed=zombie(level,64,120,2,100000); var ignition=igniteWithNativeCause(p,seed);
        seed.setHealth(.001F); seed.invulnerableTime=0;
        var collateral=zombie(level,65,120,3,.001F);
        equip(p,true,EquipmentTier.DORMANT);
        captureCauseTarget=seed.getUUID(); capturedNativeCause=null;
        try { action(level,p,true); } finally { captureCauseTarget=null; }
        var finalBlow=capturedNativeCause;
        check(!seed.isAlive() && !collateral.isAlive() && finalBlow!=null
                        && !finalBlow.actionId().equals(ignition.actionId()) && finalBlow.weapon().glaive()
                        && finalBlow.weapon().mode().equals("STAB"),
                "Owner's new actual glaive final blow creates a distinct causal action after prior sword ignition");
        var kills=CombatOutcomes.sequences(p.server).recent(p.getUUID(),level.getGameTime());
        check(kills.stream().filter(k->k.context().victim().equals(seed.getUUID()) || k.context().victim().equals(collateral.getUUID()))
                        .filter(k->k.context().actionId().equals(finalBlow.actionId()) && k.context().weapon().equals(finalBlow.weapon())).count()==2,
                "Same owner's fresh final-blow action takes precedence for both seed and collateral over earlier ignition");
        clearTargets(); enable(p); other.setPos(90,120,30);
    }
    private static CombatOutcome.Cause igniteWithNativeCause(ServerPlayer p,Mob seed) {
        captureCauseTarget=seed.getUUID(); capturedNativeCause=null;
        try { ignite(p,seed); } finally { captureCauseTarget=null; }
        check(capturedNativeCause!=null,"Fixture observes the exact native ignition damage scope without creating a fact");
        return capturedNativeCause;
    }

    private static void projectiles(ServerLevel level,ServerPlayer p) {
        enable(p); position(p,64,120,0);
        var caster=new ItemStack(AscendanceItems.ASCENDANCE_CASTER.get());
        EquipmentTierData.setTier(caster,EquipmentTier.TRANSCENDENT);
        p.setItemInHand(InteractionHand.MAIN_HAND,caster); TwoHandedEquipmentService.refresh(p);
        var magicTarget=zombie(level,64,120,3,1);
        var cast=EquipmentWeaponService.castMagic(p,InteractionHand.MAIN_HAND,caster);
        check(cast.castPerformed() && level.getEntity(cast.projectileId()) instanceof MagicBoltEntity,
                "Actual accepted Caster use creates its native server projectile");
        var bolt=(MagicBoltEntity)level.getEntity(cast.projectileId());
        projectileKillAfterSwap(level,p,bolt,magicTarget,"essence_ascendance:magic_caster");
        clearTargets();

        var bow=new ItemStack(AscendanceItems.ASCENDANCE_RANGED_WEAPON.get());
        EquipmentTierData.setTier(bow,EquipmentTier.TRANSCENDENT);
        p.setItemInHand(InteractionHand.MAIN_HAND,bow); TwoHandedEquipmentService.refresh(p);
        p.getInventory().add(new ItemStack(Items.ARROW,16));
        var arrowTarget=zombie(level,64,120,3,1);
        int charge=Math.max(20,EquipmentWeaponService.evaluateRanged(p,bow).fullDrawTicks());
        p.startUsingItem(InteractionHand.MAIN_HAND);
        bow.getItem().releaseUsing(bow,level,p,bow.getUseDuration(p)-charge);
        p.stopUsingItem();
        var arrows=level.getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(4),a->a.getOwner()==p);
        check(arrows.size()==1,"Actual native bow release consumes ammunition and creates one server arrow");
        projectileKillAfterSwap(level,p,arrows.getFirst(),arrowTarget,"essence_ascendance:ranged_weapon");
        clearTargets();
    }
    private static void projectileKillAfterSwap(ServerLevel level,ServerPlayer p,Projectile projectile,Mob target,String profile) {
        var state=ProjectileRuntime.state(projectile);
        check(state!=null && state.combatCause!=null && state.combatCause.weapon().profileId().equals(profile),
                "Projectile captures its actual launch weapon before the item swap: " + profile);
        String action=state.combatCause.actionId();
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK)); TwoHandedEquipmentService.refresh(p);
        for (int tick=0;tick<20 && target.isAlive() && !projectile.isRemoved();tick++) {
            advance(level,1); projectile.tick();
        }
        check(!target.isAlive(),"Native projectile flight and collision deliver the fatal hit after swapping away: " + profile);
        check(CombatOutcomes.sequences(p.server).recent(p.getUUID(),level.getGameTime()).stream()
                        .anyMatch(k->k.context().victim().equals(target.getUUID()) && k.context().actionId().equals(action)
                                && k.context().weapon().profileId().equals(profile)),
                "Confirmed projectile kill retains the launch action and weapon, not the held stick: " + profile);
        projectile.discard();
    }

    private static void barefoot(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p); equip(p,false,EquipmentTier.TRANSCENDENT); position(p,64,-10,0);
        var data=EssenceSavedData.get(p.server).getPlayerData(p.getUUID());
        var beforeCommand=data.achievementProgress().save();
        var commanded=zombie(level,64,-10,2,1);
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withEntity(p),
                "damage " + commanded.getUUID() + " 1000 minecraft:player_attack by " + p.getUUID());
        check(!commanded.isAlive() && beforeCommand.equals(data.achievementProgress().save()) && !done(p,120),
                "Actual player-attributed administrative damage kills normally but cannot fabricate survival partial progress");
        clearTargets();
        var killedByCommand=zombie(level,64,-10,2,1);
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withEntity(p), "kill " + killedByCommand.getUUID());
        check(!killedByCommand.isAlive() && beforeCommand.equals(data.achievementProgress().save()),
                "Actual kill command has no successful survival damage receipt or kill counter credit");
        clearTargets();
        p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.LEATHER_BOOTS));
        kill(p,zombie(level,64,-10,2,1));
        check(!done(p,120),"Native underground kill while wearing boots rejects"); clearTargets();
        p.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);
        position(p,64,1,0); kill(p,zombie(level,64,1,2,1));
        check(!done(p,120),"Native barefoot kill above zero rejects"); clearTargets();
        position(p,64,0,0); kill(p,zombie(level,64,-1,2,1));
        check(!done(p,120),"Actor exactly at Y=0 rejects even with an underground victim"); clearTargets();
        position(p,64,-1,0); kill(p,zombie(level,64,0,2,1));
        check(!done(p,120),"Victim exactly at Y=0 rejects even with an underground actor"); clearTargets();

        var beforeElevationCallbacks=data.achievementProgress().save();
        var movedAbove=zombie(level,64,-1,2,1);
        killWithElevationCallback(p,movedAbove,0,Double.NaN);
        check(movedAbove.getY()==0 && beforeElevationCallbacks.equals(data.achievementProgress().save()),
                "Native hurt callback moving the victim above ground rejects the stale underground position"); clearTargets();
        var actorMovedAbove=zombie(level,64,-1,2,1);
        killWithElevationCallback(p,actorMovedAbove,Double.NaN,0);
        check(p.getY()==0 && beforeElevationCallbacks.equals(data.achievementProgress().save()),
                "Native hurt callback moving the actor above ground rejects the stale underground position"); clearTargets();
        position(p,64,-1,0);
        var movedBelow=zombie(level,64,0,2,1);
        killWithElevationCallback(p,movedBelow,-1,Double.NaN);
        var counted=com.mistaboom.essence_ascendance.data.NbtVersionCompat.getCompound(
                com.mistaboom.essence_ascendance.data.NbtVersionCompat.getCompound(data.achievementProgress().save(),"entries"),"a120");
        check(movedBelow.getY()==-1 && com.mistaboom.essence_ascendance.data.NbtVersionCompat.getInt(counted,"count")==1
                        && !done(p,120),
                "Native hurt callback moving the victim underground counts exactly once from its final damage position"); clearTargets();
        position(p,64,-10,0);
        for (int i=2;i<=10;i++) {
            kill(p,zombie(level,64,-10,2,1));
            check(done(p,120)==(i==10),"Ten genuine barefoot Overworld Ascendance weapon kills: " + i);
            clearTargets();
        }
        check(!done(other,120),"Nearby teammate receives no barefoot kill progress");
        p.getAdvancements().revoke(advancement(p,120),"achieved");
        kill(p,zombie(level,64,-10,2,1)); clearTargets();
        check(!done(p,120),"Saturated lifetime counter does not regrant after revoke");
        p.getAdvancements().award(advancement(p,120),"achieved");
        position(p,64,120,0);
    }

    private static void frostbite(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p,SkillIds.FROSTBITE); equip(p,false,EquipmentTier.LATENT);
        var target=zombie(level,64,120,2,100000);
        var settings=SkillEffectRuntime.context(p).settings().frostbite();
        int hits=(settings.maxChill()+settings.chillPerHit()-1)/settings.chillPerHit();
        check(hits>1 && hits<=1000,"Live configured Frostbite threshold is finite and exercises partial buildup");
        for (int i=1;i<=hits;i++) {
            target.invulnerableTime=0; p.attack(target);
            check(done(p,85)==(i==hits),"Only full actual Frostbite transition awards at hit " + i);
        }
        check(target.isAlive() && !done(other,85),"Living fully frozen target credits its source player only");
        p.getAdvancements().revoke(advancement(p,85),"achieved");
        target.invulnerableTime=0; p.attack(target);
        check(!done(p,85),"Refreshing an already frozen target does not replay its transition");
        clearTargets();
        target=zombie(level,64,120,2,100000);
        for (int i=0;i<hits;i++) { target.invulnerableTime=0; p.attack(target); }
        check(done(p,85),"A fresh target's full freeze can re-earn the one-shot rule");
        clearTargets();
    }

    private static void desperation(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p,SkillIds.DESPERATION); equip(p,false,EquipmentTier.TRANSCENDENT);
        p.setHealth(p.getMaxHealth()*.1F);
        var boundary=wither(level,64,120,2); kill(p,boundary);
        check(!done(p,184),"Exactly ten percent health is not strictly below ten percent"); clearTargets();
        enable(p); p.setHealth(p.getMaxHealth()*.05F);
        kill(p,wither(level,64,120,2));
        check(!done(p,184),"Low health without effective Desperation cannot earn Limit Break"); clearTargets();
        enable(p,SkillIds.DESPERATION); p.setHealth(p.getMaxHealth()*.05F);
        kill(p,wither(level,64,120,2));
        check(done(p,184) && !done(other,184),"Credited Wither final blow captures effective Desperation and strictly low health");
        p.setHealth(p.getMaxHealth()); clearTargets();
    }

    private static void sanctuary(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p,SkillIds.WAYLIGHT,SkillIds.SANCTUARY); equip(p,false,EquipmentTier.LATENT);
        var damaged=pillager(level,64,120,2); damaged.setTarget(p);
        UtilitySanctuaryService.tick(SkillEffectRuntime.context(p));
        damaged.invulnerableTime=0; p.attack(damaged);
        check(damaged.isAlive(),"No-damage rejection fixture keeps the native pillager alive");
        advance(level,SkillEffectRuntime.context(p).settings().utility().sanctuary().disengageDelayTicks()+1);
        UtilitySanctuaryService.tick(SkillEffectRuntime.context(p));
        check(damaged.getTarget()==null && !done(p,207),"Sanctuary can pacify a damaged encounter without awarding its no-damage achievement");
        clearTargets();
        var wrong=pillager(level,64,120,2); wrong.setTarget(other);
        UtilitySanctuaryService.tick(SkillEffectRuntime.context(p));
        advance(level,SkillEffectRuntime.context(p).settings().utility().sanctuary().disengageDelayTicks()+1);
        UtilitySanctuaryService.tick(SkillEffectRuntime.context(p));
        check(wrong.getTarget()==other && !done(p,207),"Another player's targeting pillager cannot satisfy this actor's encounter");
        clearTargets();
        var peaceful=pillager(level,64,120,2); peaceful.setTarget(p);
        UtilitySanctuaryService.tick(SkillEffectRuntime.context(p));
        advance(level,SkillEffectRuntime.context(p).settings().utility().sanctuary().disengageDelayTicks()+1);
        UtilitySanctuaryService.tick(SkillEffectRuntime.context(p));
        check(peaceful.getTarget()==null && done(p,207) && !done(other,207),"Specific undamaged targeting pillager successfully pacifies for its actor");
        clearTargets();
    }

    private static void ledger(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p,SkillIds.THREAT_SENSE,SkillIds.HUNTERS_LEDGER); equip(p,false,EquipmentTier.TRANSCENDENT);
        var untracked=zombie(level,64,120,2,1); kill(p,untracked);
        check(!done(p,215),"Owned effective Ledger without a pre-fatal server target snapshot is insufficient"); clearTargets();
        var expired=zombie(level,64,120,2,1); expired.setTarget(p);
        advance(level,5); UtilitySenseService.tickThreatSense(SkillEffectRuntime.context(p));
        advance(level,6); kill(p,expired);
        check(!done(p,215),"Expired pre-fatal Ledger snapshot rejects without inventing a refresh"); clearTargets();
        var outOfRange=zombie(level,64,120,2,1); outOfRange.setTarget(p);
        advance(level,5); UtilitySenseService.tickThreatSense(SkillEffectRuntime.context(p));
        outOfRange.setPos(200,120,0);
        check(!UtilitySenseService.ledgerEligible(p,outOfRange),
                "A stale published member outside live configured Ledger range is rejected by its native sensing owner"); clearTargets();
        for (int i=1;i<=5;i++) {
            var tracked=zombie(level,64,120,2,1); tracked.setTarget(p);
            advance(level,5); UtilitySenseService.tickThreatSense(SkillEffectRuntime.context(p));
            var snapshot=UtilitySenseService.snapshot(p);
            check(snapshot.ledger() && snapshot.threats().stream().anyMatch(t->t.entityId()==tracked.getId()
                    && t.health()>0 && t.maxHealth()>0 && t.armor()>=0),"Actual server Ledger snapshot includes pre-fatal health and armor");
            Object attackIdentity=SkillEffectRuntime.attackRuntimeIdentity(p);
            check(UtilitySenseService.ledgerEligible(p,tracked)
                            && SkillEffectRuntime.attackRuntimeIdentity(p)==attackIdentity,
                    "Read-only Ledger eligibility preserves the existing attack runtime identity");
            kill(p,tracked);
            check(done(p,215)==(i==5),"Five real pre-fatal Ledger-target kills: " + i);
            clearTargets();
        }
        check(!done(other,215),"Target membership and credited kills do not transfer to a nearby player");
    }

    private static ItemStack equip(ServerPlayer p,boolean glaive,EquipmentTier tier) {
        WeaponActionRuntime.cancel(p);
        var stack=new ItemStack(glaive?AscendanceItems.ASCENDANCE_GLAIVE.get():AscendanceItems.ASCENDANCE_SWORD.get());
        EquipmentTierData.setTier(stack,tier);
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        TwoHandedEquipmentService.refresh(p); EquipmentAttributeService.sync(p);
        return stack;
    }
    private static void enable(ServerPlayer p,ResourceLocation... skills) {
        var data=EssenceSavedData.get(p.server).getPlayerData(p.getUUID());
        data.setTier(AscendanceTiers.TRANSCENDENT);
        SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
        data.clearAllSkillsForAdmin();
        data.grantAllSkillsForAdmin(Arrays.stream(skills).map(SkillRegistry::require).toList());
        for (var id:skills) {
            var definition=SkillRegistry.require(id);
            if (definition.choiceGroup()!=null) data.setLoadoutSelection(definition.choiceGroup(),id);
            else if (definition.activationPolicy()==SkillActivationPolicy.TOGGLE) data.setLoadoutSelection(id,id);
        }
        SkillEffectRuntime.reset(p); SkillEffectRuntime.refresh(p);
        for (var id:skills) check(SkillEffectRuntime.context(p).isEffective(id),"Fixture native skill effective: " + id);
    }
    private static void position(ServerPlayer p,double x,double y,double z) {
        p.setPos(x,y,z); p.setYRot(0); p.setXRot(0); p.setDeltaMovement(Vec3.ZERO);
        for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++)
            p.serverLevel().getChunkAt(BlockPos.containing(x+dx*16,y,z+dz*16));
    }
    private static void action(ServerLevel level,ServerPlayer p,boolean stab) {
        advance(level,20);
        check(WeaponActionRuntime.attempt(p,stab),"Native action request is accepted");
        tickAction(level,p,14);
    }
    private static void tickAction(ServerLevel level,ServerPlayer p,int ticks) {
        for (int i=0;i<ticks;i++) { advance(level,1); WeaponActionRuntime.tick(p); }
    }
    private static void advance(ServerLevel level,long ticks) {
        level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+ticks);
    }
    private static void kill(ServerPlayer p,Mob target) {
        target.invulnerableTime=0; p.attack(target);
        check(!target.isAlive(),"Actual native attack delivers a fatal accepted health write");
    }
    private static void killWithElevationCallback(ServerPlayer p,Mob target,double victimY,double actorY) {
        relocateDuringHurt=target.getUUID(); victimYDuringHurt=victimY; actorYDuringHurt=actorY;
        try { kill(p,target); }
        finally { relocateDuringHurt=null; victimYDuringHurt=Double.NaN; actorYDuringHurt=Double.NaN; }
    }
    private static Mob zombie(ServerLevel level,double x,double y,double z,float health) {
        //? if >=26.1.2
        /*var mob=new net.minecraft.world.entity.monster.zombie.Zombie(EntityType.ZOMBIE,level);*/
        //? if <26.1.2
        var mob=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,level);
        return spawn(level,mob,x,y,z,health);
    }
    private static Mob pillager(ServerLevel level,double x,double y,double z) {
        //? if >=26.1.2
        /*var mob=new net.minecraft.world.entity.monster.illager.Pillager(EntityType.PILLAGER,level);*/
        //? if <26.1.2
        var mob=new net.minecraft.world.entity.monster.Pillager(EntityType.PILLAGER,level);
        return spawn(level,mob,x,y,z,1000);
    }
    private static Mob wither(ServerLevel level,double x,double y,double z) {
        return spawn(level,new WitherBoss(EntityType.WITHER,level),x,y,z,1);
    }
    private static Mob spawn(ServerLevel level,Mob mob,double x,double y,double z,float health) {
        level.getChunkAt(BlockPos.containing(x,y,z));
        mob.setPos(x,y,z); mob.setNoAi(true); mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(20,health)); mob.setHealth(health);
        check(level.addFreshEntity(mob),"Fixture native hostile enters the real world entity registry");
        TARGETS.add(mob); return mob;
    }
    private static void clearTargets() { TARGETS.forEach(Mob::discard); TARGETS.clear(); }
    private static AdvancementHolder advancement(ServerPlayer p,int id) {
        return p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("essence_ascendance",String.format("achievements/a%03d",id)));
    }
    private static boolean done(ServerPlayer p,int id) { return p.getAdvancements().getOrStartProgress(advancement(p,id)).isDone(); }
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
}
