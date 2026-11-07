package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.movement.MovementAbilityRules;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.MobilityFlightEffects;
import com.mistaboom.essence_ascendance.skill.effect.MobilityMomentumEffects;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BiConsumer;

/** Post-commit native observers. This owner never modifies movement, forces, damage or skill state. */
public final class MovementOutcomes {
    public static final int MAX_ACTORS=256;
    private static final Map<MinecraftServer,Map<UUID,State>> SERVERS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Boolean> CAPACITY_OVERFLOW=new WeakHashMap<>();
    private static BiConsumer<MinecraftServer,MovementOutcome> listener;
    private static BiConsumer<MinecraftServer,UUID> resetListener;
    private MovementOutcomes() { }
    private static final class State {
        final MovementSessions sessions;
        String dimension;
        Vec3 before,end;
        boolean observed,invalid,surface=true;
        boolean maximumBaselineRequired;
        double horizontal,spatial;
        final List<VaultMove> vaults=new ArrayList<>();
        State(ServerPlayer player) {
            maximumBaselineRequired=CAPACITY_OVERFLOW.containsKey(player.server);
            com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.track(player,
                    com.mistaboom.essence_ascendance.config.EssenceConfigManager.skillEffects().posture().movement());
            dimension=dimension(player);before=end=player.position();
            MinecraftServer server=player.server;
            sessions=new MovementSessions(player.getUUID(),()->AttunementGameplay.action("movement"),fact->publish(server,fact));
        }
        void consumed(ServerPlayer player){before=end=player.position();observed=invalid=false;surface=true;horizontal=spatial=0;vaults.clear();}
    }
    /** Captured native geometry only, committed after handleMovePlayer returns at the same endpoint. */
    public record VaultMove(Vec3 before,Vec3 end,long obstacle) { }
    public record MoveStart(Vec3 before,boolean grounded,double velocityY,double ordinaryStep,double skillStep,AABB box) { }
    public static void register(BiConsumer<MinecraftServer,MovementOutcome> consumer) {
        if(listener!=null)throw new IllegalStateException("Movement consumer already registered");listener=Objects.requireNonNull(consumer);
    }
    private static void publish(MinecraftServer server,MovementOutcome fact) {
        if(listener!=null)try { listener.accept(server,fact); }
        catch(RuntimeException failure) { com.mistaboom.essence_ascendance.EssenceAscendance.LOGGER.error("Movement outcome delivery failed",failure); }
    }
    public static void registerReset(BiConsumer<MinecraftServer,UUID> consumer) {
        if(resetListener!=null)throw new IllegalStateException("Movement reset consumer already registered");resetListener=Objects.requireNonNull(consumer);
    }
    private static State existing(ServerPlayer p){var server=SERVERS.get(p.server);return server==null?null:server.get(p.getUUID());}
    private static State ensure(ServerPlayer p){
        var actors=SERVERS.computeIfAbsent(p.server,ignored->new HashMap<>());
        if(!actors.containsKey(p.getUUID())&&actors.size()>=MAX_ACTORS){CAPACITY_OVERFLOW.put(p.server,true);return null;}
        return actors.computeIfAbsent(p.getUUID(),ignored->new State(p));
    }
    public static void beginGlide(ServerPlayer p) {
        if(!AttunementGameplay.eligible(p))return;
        var s=ensure(p);if(s!=null)s.sessions.beginGlide(dimension(p),p.level().getGameTime(),position(p.position()));
    }
    public static void boost(ServerPlayer p) {
        var s=existing(p);if(s!=null&&AttunementGameplay.eligible(p))s.sessions.boost(dimension(p),p.level().getGameTime());
    }
    public static void rush(SkillEffectRuntime.Context context,long expires,boolean filledFromBelow) {
        var p=context.player();
        if(!AttunementGameplay.eligible(p)){
            var s=ensure(p);if(s!=null)baseline(p,s,MobilityMomentumEffects.observation(context),context.now());
            return;
        }
        var s=ensure(p);
        if(s!=null){
            var momentum=MobilityMomentumEffects.observation(context);
            if(filledFromBelow)s.sessions.maximumWasBelow();else s.sessions.baselineMaximum(momentum.amount(),momentum.maximum());
            s.maximumBaselineRequired=false;s.sessions.rush(expires);
        }
    }
    /** Called only after the native movement handler, never from input-intent transport. */
    public static void moved(ServerPlayer p,Vec3 before,String dimension) {
        var s=existing(p);if(s==null)return;
        var context=SkillEffectRuntime.observationContext(p);
        if(context==null){reset(p);return;}
        if(!AttunementGameplay.eligible(p)){baseline(p,s,MobilityMomentumEffects.observation(context),context.now());return;}
        Vec3 after=p.position();var delta=after.subtract(before);
        double maximum=context.settings().posture().movement().maximumDisplacement();
        if(!dimension.equals(dimension(p))||!s.dimension.equals(dimension)||!Double.isFinite(delta.lengthSqr())
                ||delta.length()>maximum||s.spatial+delta.length()>maximum
                ||s.end.distanceTo(before)>1e-7){s.invalid=true;return;}
        if(!s.observed)s.before=before;
        s.observed=true;s.end=after;s.horizontal+=delta.horizontalDistance();s.spatial+=delta.length();
        s.surface &= context.isEffective(SkillIds.SURFACE_TENSION)&&p.isSprinting()&&!p.isShiftKeyDown()
                && TraversalService.nativeMovement(p)&&p.onGround()&&TraversalService.exposedWaterRoute(p,before,after);
        for(var vault:s.vaults)if(vault.end().distanceTo(after)<=1e-6&&vault.before().distanceTo(before)<=1e-6)
            s.sessions.vault(vault.obstacle());
        s.vaults.clear();
    }
    /** Called once after existing effect handlers, and only ticks scalar sessions that are active. */
    public static void tick(SkillEffectRuntime.Context context) {
        var p=context.player();var snapshot=MobilityMomentumEffects.observation(context);
        boolean wings=MobilityFlightEffects.ownedWings(context);
        boolean surface=context.isEffective(SkillIds.SURFACE_TENSION)&&p.isSprinting();
        var s=existing(p);
        if(s==null&&(wings||snapshot.amount()>0||surface))s=ensure(p);
        if(s==null)return;
        if(!AttunementGameplay.eligible(p)){baseline(p,s,snapshot,context.now());return;}
        if(s.invalid||!s.dimension.equals(dimension(p))){baseline(p,s,snapshot,context.now());return;}
        if(s.maximumBaselineRequired){s.sessions.baselineMaximum(snapshot.amount(),snapshot.maximum());s.maximumBaselineRequired=false;}
        boolean nativeMode=!p.isPassenger()&&!p.getAbilities().flying&&!p.isSleeping()&&!p.onClimbable()&&!p.isInLava()
                &&!p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION);
        boolean airborne=nativeMode&&!p.isInWater();
        boolean supportedWater=surface&&TraversalService.exposedWaterRoute(p,p.position(),p.position());
        boolean sprint=p.isSprinting()&&!p.isShiftKeyDown()&&nativeMode&&!p.isFallFlying()&&(!p.isInWater()||supportedWater)
                && AttunementGameplay.movementIntent(p,context.settings().posture().movement().intentTimeoutTicks());
        var motion=com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.sample(p);
        boolean momentumMovement=!motion.discontinuity()&&!com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.nonJumpForced(p)&&motion.intentional();
        s.sessions.sample(new MovementSessions.Frame(dimension(p),context.now(),position(s.before),position(p.position()),s.observed,
                s.horizontal,s.spatial,context.settings().posture().movement().maximumDisplacement(),true,
                airborne,MovementAbilityRules.supported(p),wings,sprint,snapshot.amount(),snapshot.maximum(),s.surface&&surface
                &&supportedWater,momentumMovement,snapshot.rushProtected()));
        s.consumed(p);
        if(!s.sessions.active()&&snapshot.amount()<=0&&!surface)reset(p);
    }
    public static void combat(ServerPlayer p,CombatOutcome fact) {
        if(!(fact instanceof CombatOutcome.Damage damage)||!damage.context().weapon().glaive()
                ||!Double.isFinite(damage.contactDistance())||damage.contactDistance()<0||damage.loss()<=0)return;
        var s=existing(p);
        if(s==null||!AttunementGameplay.eligible(p)||p.isPassenger()||p.getAbilities().flying||p.isInWater()
                ||p.onClimbable()||p.isSleeping()||p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                ||p.isInLava()||MovementAbilityRules.supported(p)||!s.dimension.equals(dimension(p)))return;
        s.sessions.hit(dimension(p),p.level().getGameTime(),fact.context().actionId());
    }
    public static MoveStart beginMove(Entity entity,MoverType type) {
        if(!(entity instanceof ServerPlayer p)||type!=MoverType.PLAYER||!AttunementGameplay.eligible(p))return null;
        var context=SkillEffectRuntime.observationContext(p);
        if(context==null||!MobilityMomentumEffects.vaultAvailable(context))return null;
        var attribute=p.getAttribute(Attributes.STEP_HEIGHT);
        if(attribute==null||attribute.getModifier(SkillIds.MOMENTUM_VAULT)==null)return null;
        double subtotal=attribute.getBaseValue(),base=1,total=1;
        for(var modifier:attribute.getModifiers())if(!modifier.id().equals(SkillIds.MOMENTUM_VAULT))switch(modifier.operation()) {
            case ADD_VALUE -> subtotal+=modifier.amount();
            case ADD_MULTIPLIED_BASE -> base+=modifier.amount();
            case ADD_MULTIPLIED_TOTAL -> total*=1+modifier.amount();
        }
        double ordinary=com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController.stepHeight(p,(float)(subtotal*base*total));
        return new MoveStart(p.position(),p.onGround(),p.getDeltaMovement().y,ordinary,p.maxUpStep(),p.getBoundingBox());
    }
    public static void completedMove(Entity entity,MoveStart start) {
        if(start==null||!(entity instanceof ServerPlayer p))return;
        Vec3 after=p.position();double rise=after.y-start.before().y;
        if(!start.grounded()||start.velocityY()>0||rise<=start.ordinaryStep()+1e-6||rise>start.skillStep()+1e-6
                ||after.subtract(start.before()).horizontalDistance()<=1e-6||!TraversalService.nativeMovement(p))return;
        // A counterfactual ordinary-height box must collide with an actual solid obstacle; support/headroom
        // must exist at the accepted upper endpoint. Fluid surfaces and ordinary stairs cannot substitute.
        AABB atFoot=start.box().move(after.x-start.before().x,0,after.z-start.before().z);
        if(p.level().noCollision(p,atFoot)||p.level().noCollision(p,p.getBoundingBox().move(0,-0.05,0)))return;
        BlockPos obstacle=BlockPos.containing(after.x,start.before().y+Math.min(rise-1e-5,start.ordinaryStep()+1e-4),after.z);
        var block=p.level().getBlockState(obstacle);
        if(block.getCollisionShape(p.level(),obstacle).isEmpty()||!block.getFluidState().isEmpty())return;
        var s=existing(p);if(s!=null&&s.vaults.size()<64)s.vaults.add(new VaultMove(start.before(),after,obstacle.asLong()));
    }
    public static void reset(ServerPlayer p) {
        var actors=SERVERS.get(p.server);if(actors!=null){actors.remove(p.getUUID());if(actors.isEmpty())SERVERS.remove(p.server);}
        resetCheckpoints(p);
    }
    private static void resetCheckpoints(ServerPlayer p) {
        if(resetListener!=null)try { resetListener.accept(p.server,p.getUUID()); }
        catch(RuntimeException failure) { com.mistaboom.essence_ascendance.EssenceAscendance.LOGGER.error("Movement reset observation failed",failure); }
    }
    private static void baseline(ServerPlayer p,State state,MobilityMomentumEffects.Observation momentum,long tick) {
        state.sessions.baselineMomentum(dimension(p),tick,momentum.amount(),momentum.maximum());
        state.maximumBaselineRequired=false;
        state.dimension=dimension(p);state.consumed(p);
        if(momentum.amount()<=0)reset(p);else resetCheckpoints(p);
    }
    public static void clear(MinecraftServer server){SERVERS.remove(server);CAPACITY_OVERFLOW.remove(server);}
    public static void clearAll(){SERVERS.clear();CAPACITY_OVERFLOW.clear();}
    private static MovementSessions.Position position(Vec3 p){return new MovementSessions.Position(p.x,p.y,p.z);}
    private static String dimension(ServerPlayer p){return p.level().dimension().location().toString();}
}
