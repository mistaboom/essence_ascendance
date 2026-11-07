package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.movement.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Disposable-world native skill activations and actual accepted movement-handler/weapon input.
 * The fixture controls geometry, movement packets, time, skill ownership and target placement. */
public final class MovementAchievementNativeFixture {
    private static final List<Integer> IDS=List.of(95,99,100,101,102,145,201,216);
    private static int checks;
    private MovementAchievementNativeFixture() { }
    public static void run(ServerLevel level,ServerPlayer player,ServerPlayer other) {
        checks=0;player.setGameMode(GameType.SURVIVAL);other.setGameMode(GameType.SURVIVAL);
        for(int id:IDS){check(advancement(player,id)!=null,"Native movement resource exists "+id);revoke(player,id);revoke(other,id);}
        try {
            runningAndVault(level,player,other);surface(level,player,other);rush(level,player,other);glide(level,player,other);
            player.getAdvancements().save();
            for(int id:IDS){check(done(player,id),"Native movement completion before save "+id);revoke(player,id);}
            player.getAdvancements().reload(level.getServer().getAdvancements());
            for(int id:IDS)check(done(player,id),"Native movement advancement JSON restores "+id);
            System.out.println("MovementAchievementNativeFixture: "+checks+" assertions passed; real Wings/Boost/Rush/native step and surface geometry, accepted handler packets, glaive action while gliding, advancement save/reload; controlled fixture trajectories");
        } finally {SkillEffectRuntime.reset(player);SkillEffectRuntime.reset(other);WeaponActionRuntime.cancel(player);player.stopFallFlying();player.setSprinting(false);}
    }
    private static void runningAndVault(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p,SkillIds.RUNNING_MOMENTUM,SkillIds.MOMENTUM_VAULT);
        var context=SkillEffectRuntime.context(p);
        check(context.settings().mobility().runningMomentum().maximumSpeedBonus()>0,"Configured effective Running Momentum has a real buff");
        int steps=context.settings().mobility().runningMomentum().buildTicks()+1010;
        int width=24;
        for(int x=-3;x<=width+3;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
        position(p,0.5,120,0.5);p.setSprinting(true);tick(level,p);
        // Back-and-forth is intentionally a path-distance test; turn yaw remains fixed and native input is fresh.
        double x=.5;boolean forward=true;
        for(int i=0;i<steps;i++){
            if(x>=20.5)forward=false;if(x<=.5)forward=true;
            int middle=steps/2;
            if(i==middle){
                p.jumpFromGround();p.setOnGround(false);
                check(p.getDeltaMovement().y>0&&!PlayerMotionTracker.nonJumpForced(p),"Actual ordinary native sprint jump carries its own impulse without external-force attribution");
            }
            boolean airborne=i==middle||i==middle+1;
            x+=forward?.5:-.5;move(level,p,x,airborne?120.35:120,.5,!airborne);
            if(i==middle)check(PlayerMotionTracker.sample(p).forced(),"Observer cause metadata preserves the existing gameplay forced quiet window for sprint jumps");
        }
        check(done(p,100)&&!done(other,100),"Accepted running reaches live effective cap for actor only");
        check(done(p,216)&&!done(other,216),"500 accepted horizontal path blocks preserve one momentum sprint across a real ordinary jump");
        check(!done(p,101),"Flat native sprint cannot fabricate obstacle vaults");
        check(MobilityMomentumEffects.observation(SkillEffectRuntime.context(p)).amount()>0,"Momentum remains active before native obstacles");
        double step=SkillEffectRuntime.context(p).settings().mobility().momentumVault().stepHeight();
        check(step>=1,"Default configured Momentum Vault supports one-block native obstacles");
        position(p,.5,120,5.5);p.setSprinting(true);
        for(int xx=-1;xx<=18;xx++)for(int z=4;z<=7;z++){
            int height=120+Math.max(0,Math.min(5,(xx-1)/3));
            for(int y=119;y<height;y++)level.setBlockAndUpdate(new BlockPos(xx,y,z),Blocks.STONE.defaultBlockState());
        }
        // Position setup is a discontinuity, so build a fresh legitimate sprint before first ledge.
        SkillEffectRuntime.movementDiscontinuity(p);SkillEffectRuntime.refresh(p);p.setSprinting(true);
        int build=SkillEffectRuntime.context(p).settings().mobility().runningMomentum().buildTicks();
        for(int i=0;i<build+3;i++)move(level,p,(i%2==0)?.6:.5,120,5.5,true);
        move(level,p,1.5,120,5.5,true);
        for(int obstacle=1;obstacle<=5;obstacle++){
            double targetX=1.5+3*obstacle;
            move(level,p,targetX-1,119+obstacle,5.5,true);
            move(level,p,targetX,120+obstacle,5.5,true);
            if(obstacle<5)check(!done(p,101),"Fewer than five distinct native obstacle clearances remain incomplete");
        }
        check(done(p,101)&&!done(other,101),"Five distinct actual native obstacle steps in one sprint earn Parkour");
    }
    private static void surface(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p,SkillIds.SURFACE_TENSION);
        for(int x=0;x<=36;x++)for(int z=15;z<=17;z++)level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.WATER.defaultBlockState());
        double y=119+level.getFluidState(new BlockPos(0,119,16)).getHeight(level,new BlockPos(0,119,16));
        position(p,.5,y,16.5);p.setSprinting(true);tick(level,p);
        check(MovementAbilityRules.supported(p),"Actual Surface Tension collision supports feet on exposed water");
        for(int i=1;i<=63;i++)move(level,p,.5+i*.5,y,16.5,true);
        check(!done(p,102),"31.5 accepted supported water blocks below threshold");
        move(level,p,32.5,y,16.5,true);
        check(done(p,102)&&!done(other,102),"32 actual accepted Surface Tension water blocks award actor only");
        revoke(p,102);p.setSprinting(false);move(level,p,33,y,16.5,false);
        check(!done(p,102),"Walking or rod surface support cannot replay sprint achievement");
        // Re-earn from a fresh full route, preserving the save/reload checks at the end.
        enable(p,SkillIds.SURFACE_TENSION,SkillIds.RUNNING_MOMENTUM);
        position(p,.5,y,16.5);SkillEffectRuntime.movementDiscontinuity(p);p.setSprinting(true);tick(level,p);
        for(int i=1;i<=64;i++)move(level,p,.5+i*.5,y,16.5,true);
        check(done(p,102),"Surface Tension plus effective Running Momentum still earns from fresh supported water sprint");
    }
    private static void rush(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        creativeMomentum(level,p);
        overflowBaseline(level,p,other);
        enable(p,SkillIds.RUNNING_MOMENTUM,SkillIds.RUSH);equip(p,false);
        position(p,3.5,120,0.5);p.setSprinting(false);revoke(p,100);revoke(p,201);
        var administrative=zombie(level,3.5,120,2,1);
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                "damage "+administrative.getUUID()+" 1000 minecraft:generic by "+p.getUUID());
        check(!administrative.isAlive(),"Actual administrative player-attributed damage retains normal fatal gameplay");
        var unchanged=MobilityMomentumEffects.observation(SkillEffectRuntime.context(p));
        check(unchanged.amount()==0&&!unchanged.rushProtected(),"Generic administrative damage remains outside existing first-party Rush kill attribution");
        check(!done(p,100)&&!done(p,201),"Actual administrative command cannot fabricate maximum or Rush activation");
        administrative.discard();
        var scoped=zombie(level,3.5,120,2,1);
        // Controlled scope probe for the actual first-party native attack path: this retains Rush
        // gameplay while exercising the same outcome-suppression boundary used by native commands.
        try(var suppression=AttunementGameplay.suppressAdministrativeOutcomes()){p.attack(scoped);}
        check(!scoped.isAlive(),"Actual first-party attack succeeds inside the controlled administrative outcome scope");
        var supplied=MobilityMomentumEffects.observation(SkillEffectRuntime.context(p));
        check(supplied.maximum()>0&&supplied.amount()==supplied.maximum()&&supplied.rushProtected(),
                "Suppressed first-party kill still fills and protects unchanged real Rush gameplay momentum");
        tick(level,p);p.setSprinting(true);move(level,p,4,120,.5,true);
        check(!done(p,100)&&!done(p,201),"Later eligible tick and sprint cannot turn administrative Rush into a survival cap or activation");
        scoped.discard();
        enable(p,SkillIds.RUNNING_MOMENTUM,SkillIds.RUSH);equip(p,false);
        position(p,3.5,120,0.5);p.setSprinting(false);
        var victim=zombie(level,3.5,120,2,1);
        advance(level,20);p.attack(victim);
        check(!victim.isAlive(),"Native accepted kill activates Rush through existing death handlers");
        check(MobilityMomentumEffects.observation(SkillEffectRuntime.context(p)).rushProtected(),"Real Rush protection is live after kill");
        check(!done(p,201),"Kill activation alone is not qualifying sprint");
        p.setSprinting(true);move(level,p,4,120,.5,true);
        check(done(p,100)&&done(p,201)&&!done(other,201),"Fresh legitimate kill and accepted protected sprint can earn maximum and Rush after rejected administrative baseline");
        victim.discard();
        revoke(p,100);
        var alreadyFull=zombie(level,p.getX(),120,2,1);
        // Observe every intervening tick. Jumping the fixture clock by 20 makes both the real
        // motion tracker and reservoir correctly reset for missing history before this probe.
        for(int i=0;i<20;i++)tick(level,p);
        var beforeRefresh=MobilityMomentumEffects.observation(SkillEffectRuntime.context(p));
        check(beforeRefresh.maximum()>0&&beforeRefresh.amount()==beforeRefresh.maximum()&&beforeRefresh.rushProtected(),
                "Consecutive real effect ticks retain the actual full Rush reservoir before refresh");
        p.attack(alreadyFull);
        check(!alreadyFull.isAlive(),"Fresh native Rush kill occurs while the actual momentum reservoir is already full");
        move(level,p,p.getX()+.1,120,.5,true);
        check(!done(p,100),"Refreshing Rush at an unchanged full reservoir cannot replay revoked maximum");
        alreadyFull.discard();
        // Emulate a small external position write, then let the real native movement-handler hook
        // detect its gap. It invalidates route evidence while Rush legitimately retains its reservoir.
        p.setPos(p.getX()+.125,p.getY(),p.getZ());
        move(level,p,p.getX()+.1,120,.5,true);
        var retained=MobilityMomentumEffects.observation(SkillEffectRuntime.context(p));
        check(retained.amount()==retained.maximum()&&retained.maximum()>0,"A route gap does not change the existing Rush-protected gameplay reservoir");
        move(level,p,p.getX()+.1,120,.5,true);
        check(!done(p,100),"A native accepted packet after a route gap cannot replay unchanged full momentum as a new cap transition");
        p.setSprinting(false);advance(level,SkillEffectRuntime.context(p).settings().mobility().rush().durationTicks()+1);tick(level,p);
        var below=MobilityMomentumEffects.observation(SkillEffectRuntime.context(p));
        check(below.amount()<below.maximum(),"Expired protection permits a real below-cap transition");
        var fresh=zombie(level,p.getX(),120,2,1);p.attack(fresh);p.setSprinting(true);move(level,p,p.getX()+.1,120,.5,true);
        check(!fresh.isAlive()&&done(p,100),"A new legitimate Rush fill from below cap restores a revoked maximum");fresh.discard();
    }
    @SuppressWarnings("unchecked")
    private static void overflowBaseline(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(other,SkillIds.RUNNING_MOMENTUM,SkillIds.RUSH);equip(other,false);other.setHealth(other.getMaxHealth());
        position(other,8.5,120,.5);other.setSprinting(false);revoke(other,100);revoke(other,201);
        Map<UUID,Object> actors=null;Map<UUID,Object> original=null;
        Mob victim=null;
        try {
            // Populate only bounded scalar tracker slots, not hundreds of fake ServerPlayers.
            // This is explicit capacity setup; the kill/fill and later movement still use real native paths.
            var field=com.mistaboom.essence_ascendance.gameplay.MovementOutcomes.class.getDeclaredField("SERVERS");field.setAccessible(true);
            var servers=(Map<net.minecraft.server.MinecraftServer,Map<UUID,Object>>)field.get(null);
            actors=servers.get(level.getServer());check(actors!=null&&actors.containsKey(p.getUUID()),"Native capacity fixture has one real retained momentum actor");
            original=new HashMap<>(actors);var borrowed=actors.get(p.getUUID());var synthetic=new ArrayList<UUID>();
            for(int i=0;actors.size()<com.mistaboom.essence_ascendance.gameplay.MovementOutcomes.MAX_ACTORS;i++){
                var id=new UUID(0x504835434150L,i);if(!actors.containsKey(id)){actors.put(id,borrowed);synthetic.add(id);}
            }
            victim=zombie(level,8.5,120,2,1);
            other.setGameMode(GameType.CREATIVE);other.attack(victim);
            check(!victim.isAlive()&&!actors.containsKey(other.getUUID()),"Full native tracker capacity rejects Creative native Rush fill observation without allocating overflow actor history");
            var current=MobilityMomentumEffects.observation(SkillEffectRuntime.context(other));
            check(current.maximum()>0&&current.amount()==current.maximum(),"Unobserved overflow actor still receives unchanged actual Rush gameplay fill");
            actors.remove(synthetic.get(0));other.setGameMode(GameType.SURVIVAL);other.setSprinting(true);move(level,other,9,120,.5,true);
            check(actors.containsKey(other.getUUID())&&!done(other,100)&&!done(other,201),"A later free tracker slot baselines unknown current maximum and cannot manufacture survival history after capacity loss");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native capacity fixture reflection changed",failure);}
        finally{
            if(victim!=null)victim.discard();
            if(actors!=null&&original!=null){actors.clear();actors.putAll(original);}
            SkillEffectRuntime.reset(other);other.setGameMode(GameType.SURVIVAL);other.setSprinting(false);
        }
    }
    private static void creativeMomentum(ServerLevel level,ServerPlayer p) {
        enable(p,SkillIds.RUNNING_MOMENTUM);position(p,3.5,120,.5);revoke(p,100);revoke(p,201);
        p.setGameMode(GameType.CREATIVE);p.setSprinting(true);tick(level,p);
        int build=SkillEffectRuntime.context(p).settings().mobility().runningMomentum().buildTicks();
        for(int i=0;i<build+3;i++)move(level,p,(i%2==0)?3.6:3.5,120,.5,true);
        var cap=MobilityMomentumEffects.observation(SkillEffectRuntime.context(p));
        check(cap.maximum()>0&&cap.amount()==cap.maximum(),"Creative running still reaches the unchanged real gameplay reservoir maximum");
        check(!done(p,100),"Creative running cannot award survival maximum");
        p.setGameMode(GameType.SURVIVAL);p.setSprinting(true);move(level,p,4,120,.5,true);
        check(MobilityMomentumEffects.observation(SkillEffectRuntime.context(p)).amount()==cap.maximum(),"Game-mode transition retains the actual unchanged full reservoir under test");
        check(!done(p,100)&&!done(p,201),"Switching to Survival at unchanged Creative maximum cannot manufacture a cap or Rush activation");
        p.setSprinting(false);tick(level,p);
        check(MobilityMomentumEffects.observation(SkillEffectRuntime.context(p)).amount()<cap.maximum(),"A real unprotected stop drains momentum below cap");
        p.setSprinting(true);
        for(int i=0;i<build+3;i++)move(level,p,(i%2==0)?4.1:4,120,.5,true);
        check(done(p,100),"Fresh legitimate rebuilding after a real below-cap transition earns maximum");
    }
    private static void glide(ServerLevel level,ServerPlayer p,ServerPlayer other) {
        enable(p,SkillIds.ESSENCE_WINGS,SkillIds.VECTOR_BOOST);equip(p,true);
        position(p,2.5,210,30.5);p.setOnGround(false);p.setSprinting(false);p.setDeltaMovement(new Vec3(0,-.1,.2));
        FlightAbilityService.input(p,new MovementAbilityInput(MovementAbilityInput.ENABLED|MovementAbilityInput.JUMP));
        advance(level,MovementAbilityInput.HEARTBEAT_TICKS);
        FlightAbilityService.input(p,new MovementAbilityInput(MovementAbilityInput.ENABLED|MovementAbilityInput.JUMP));SkillEffectRuntime.tick(p);
        check(MobilityFlightEffects.ownedWings(SkillEffectRuntime.context(p))&&p.isFallFlying(),"Real held-Jump/apex transition starts owned Wings glide");
        for(int boost=0;boost<3;boost++){
            if(boost>0)advance(level,SkillEffectRuntime.context(p).settings().mobility().vectorBoost().rechargeTicks());
            FlightAbilityService.input(p,new MovementAbilityInput(MovementAbilityInput.ENABLED));advance(level,1);
            FlightAbilityService.input(p,new MovementAbilityInput(MovementAbilityInput.ENABLED|MovementAbilityInput.JUMP));
            // This headless fixture does not run native client Elytra drag between activations.
            // Supply a controlled ordinary glide velocity so each boost can actually change motion.
            p.setDeltaMovement(new Vec3(0,-.1,.2));
            Vec3 before=p.getDeltaMovement();var tuning=SkillEffectRuntime.context(p).settings().mobility();
            var glideVelocity=MovementImpulseMath.essenceWingsGlide(new MovementImpulseMath.Velocity(before.x,before.y,before.z),tuning.essenceWings().horizontalDragCompensation());
            Vec3 look=p.getLookAngle();var expected=MovementImpulseMath.vectorBoost(glideVelocity,new MovementImpulseMath.Velocity(look.x,look.y,look.z),tuning.vectorBoost().rocketSpeedBonus());
            SkillEffectRuntime.tick(p);
            check(p.getDeltaMovement().distanceTo(new Vec3(expected.x(),expected.y(),expected.z()))<1e-9,"Movement observer preserves exact existing glide/boost velocity");
            FlightAbilityState boostState=SkillEffectRuntime.context(p).existingState(SkillIds.VECTOR_BOOST);
            check(boostState.boostCharge()<1,"Successful native boost spends charge rather than no-op at the velocity cap");
            if(boost<2)check(!done(p,95),"Fewer than three actual successful boosts remain incomplete");
        }
        check(done(p,95)&&!done(other,95),"Three real successful Boost activations in one owned glide award actor only");
        // A fixed square stays in a few disposable chunks and proves accumulated 3D path, not endpoint displacement.
        double x=2.5,z=30.5;
        for(int i=0;i<1000;i++){
            int edge=(i/10)%4;switch(edge){case 0->x+=1;case 1->z+=1;case 2->x-=1;default->z-=1;}
            move(level,p,x,210,z,false);
        }
        check(done(p,99)&&!done(other,99),"1000 accepted boosted-glide path blocks earn distance without teleport credit");
        p.setYRot(0);p.setXRot(0);var target=zombie(level,p.getX(),p.getY(),p.getZ()+3,1000);
        advance(level,20);
        check(WeaponActionRuntime.input(p,true,Long.MAX_VALUE-5,level.getGameTime(),TwoHandedEquipmentService.decision(p).revision()),"Actual server glaive input is accepted during native Essence Wings glide");
        for(int i=0;i<14;i++){advance(level,1);WeaponActionRuntime.tick(p);}
        check(target.getHealth()<1000&&p.isFallFlying(),"Real glaive geometry delivers accepted health loss while gliding");
        check(done(p,145)&&!done(other,145),"Begin owned glide then actual glaive hit before grounding earns actor only");
        target.discard();
        revoke(p,95);SkillEffectRuntime.tick(p);
        check(!done(p,95),"Unchanged same glide cannot replay revoked boost threshold");
        // Restore intentionally through a fresh native three-boost session, not a synthetic grant.
        SkillEffectRuntime.movementDiscontinuity(p);p.stopFallFlying();p.setOnGround(false);p.setDeltaMovement(new Vec3(0,-.1,.2));
        FlightAbilityService.input(p,new MovementAbilityInput(MovementAbilityInput.ENABLED|MovementAbilityInput.JUMP));advance(level,MovementAbilityInput.HEARTBEAT_TICKS);
        FlightAbilityService.input(p,new MovementAbilityInput(MovementAbilityInput.ENABLED|MovementAbilityInput.JUMP));SkillEffectRuntime.tick(p);
        for(int b=0;b<3;b++){
            advance(level,SkillEffectRuntime.context(p).settings().mobility().vectorBoost().rechargeTicks());
            FlightAbilityService.input(p,new MovementAbilityInput(MovementAbilityInput.ENABLED));advance(level,1);
            FlightAbilityService.input(p,new MovementAbilityInput(MovementAbilityInput.ENABLED|MovementAbilityInput.JUMP));p.setDeltaMovement(new Vec3(0,-.1,.2));SkillEffectRuntime.tick(p);
        }
        check(done(p,95),"Fresh native glide re-earns revoked boost achievement");
    }
    private static void move(ServerLevel level,ServerPlayer p,double x,double y,double z,boolean ground) {
        advance(level,1);
        // Supply the connection's per-tick good-position baseline without doTick()'s unrelated player
        // physics pass. The real handler still validates collision, rejection and endpoint acceptance.
        p.connection.resetPosition();AttunementGameplay.setMovementIntent(p,true);
        //? if >=26.1.2
        /*p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(x,y,z,p.getYRot(),p.getXRot(),ground,false));*/
        //? if <26.1.2
        p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(x,y,z,p.getYRot(),p.getXRot(),ground));
        check(p.position().distanceTo(new Vec3(x,y,z))<1e-6,"Real server movement handler accepts fixture trajectory endpoint; expected="+new Vec3(x,y,z)+" actual="+p.position());
        SkillEffectRuntime.tick(p);
    }
    private static void tick(ServerLevel level,ServerPlayer p){advance(level,1);AttunementGameplay.setMovementIntent(p,true);SkillEffectRuntime.tick(p);}
    private static void position(ServerPlayer p,double x,double y,double z) {
        // Prior native teleport tests use a fake connection. Complete its actual acknowledgement
        // protocol before sending normal movement, rather than clearing the native acceptance guard.
        try {
            var pending=p.connection.getClass().getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            if(pending.get(p.connection)!=null){
                var token=p.connection.getClass().getDeclaredField("awaitingTeleport");token.setAccessible(true);
                p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket((Integer)token.get(p.connection)));
            }
        } catch(ReflectiveOperationException failure){throw new IllegalStateException("Native fixture teleport acknowledgement fields changed",failure);}
        // 26's normal client-load acknowledgement is a separate native acceptance prerequisite.
        //? if >=26.1.2 {
        /*p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        check(p.connection.hasClientLoaded(),"Native client-load acknowledgement enables fixture movement");
        *///? }
        SkillEffectRuntime.movementDiscontinuity(p);
        p.setPos(x,y,z);p.connection.resetPosition();p.setYRot(0);p.setXRot(0);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)p.serverLevel().getChunkAt(BlockPos.containing(x+dx*16,y,z+dz*16));
    }
    private static void enable(ServerPlayer p,ResourceLocation... skills){
        var data=EssenceSavedData.get(p.server).getPlayerData(p.getUUID());data.setTier(AscendanceTiers.TRANSCENDENT);
        SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);data.clearAllSkillsForAdmin();
        Set<ResourceLocation> required=new LinkedHashSet<>(Arrays.asList(skills));
        var queue=new ArrayDeque<ResourceLocation>(required);
        while(!queue.isEmpty()){
            var definition=SkillRegistry.require(queue.remove());
            for(var prerequisite:definition.prerequisiteRanks(definition.maximumRank()).keySet())if(required.add(prerequisite))queue.add(prerequisite);
        }
        var ordered=SkillRegistry.topologicalOrder(required);data.grantAllSkillsForAdmin(ordered);
        for(var definition:ordered){var id=definition.id();if(definition.choiceGroup()!=null)data.setLoadoutSelection(definition.choiceGroup(),id);else if(definition.activationPolicy()==SkillActivationPolicy.TOGGLE)data.setLoadoutSelection(id,id);}
        SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);for(var id:skills)check(SkillEffectRuntime.context(p).isEffective(id),"Fixture skill is natively effective "+id);
    }
    private static void equip(ServerPlayer p,boolean glaive){
        WeaponActionRuntime.cancel(p);var item=new ItemStack(glaive?AscendanceItems.ASCENDANCE_GLAIVE.get():AscendanceItems.ASCENDANCE_SWORD.get());EquipmentTierData.setTier(item,EquipmentTier.TRANSCENDENT);
        p.setItemInHand(InteractionHand.MAIN_HAND,item);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);TwoHandedEquipmentService.refresh(p);EquipmentAttributeService.sync(p);
    }
    private static Mob zombie(ServerLevel level,double x,double y,double z,float health){
        //? if >=26.1.2
        /*var mob=new net.minecraft.world.entity.monster.zombie.Zombie(EntityType.ZOMBIE,level);*/
        //? if <26.1.2
        var mob=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,level);
        mob.setPos(x,y,z);mob.setNoAi(true);mob.setNoGravity(true);mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(20,health));mob.setHealth(health);level.getChunkAt(mob.blockPosition());check(level.addFreshEntity(mob),"Fixture hostile enters actual native entity registry");return mob;
    }
    private static void advance(ServerLevel level,long ticks){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+ticks);}
    private static net.minecraft.advancements.AdvancementHolder advancement(ServerPlayer p,int id){return p.server.getAdvancements().get(AchievementService.resource(id));}
    private static boolean done(ServerPlayer p,int id){return p.getAdvancements().getOrStartProgress(advancement(p,id)).isDone();}
    private static void revoke(ServerPlayer p,int id){p.getAdvancements().revoke(advancement(p,id),"achieved");}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
