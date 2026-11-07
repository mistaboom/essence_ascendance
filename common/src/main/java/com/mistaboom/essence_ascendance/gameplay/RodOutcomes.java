package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.gathering.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Server-only observation of successful rod force, accepted route and actual native fatal falls. */
public final class RodOutcomes {
    private static final Map<MinecraftServer,RodTrajectoryTracker> TRACKERS = new WeakHashMap<>();
    private static BiConsumer<MinecraftServer,RodOutcome> listener;
    private RodOutcomes() { }
    public static void register(BiConsumer<MinecraftServer,RodOutcome> consumer) {
        if (listener!=null) throw new IllegalStateException("Rod consumer already registered");
        listener=Objects.requireNonNull(consumer);
    }
    private static RodTrajectoryTracker tracker(MinecraftServer server) { return TRACKERS.computeIfAbsent(server,s->new RodTrajectoryTracker()); }
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static String dimension(Entity entity) { return entity.level().dimension().location().toString(); }
    private static RodTrajectoryTracker.Point point(Vec3 vec) { return new RodTrajectoryTracker.Point(vec.x,vec.y,vec.z); }
    private static Vec3 vec(RodTrajectoryTracker.Point point) { return new Vec3(point.x(),point.y(),point.z()); }
    private static void publish(MinecraftServer server,RodOutcome fact) {
        if (listener!=null) try { listener.accept(server,fact); }
        catch (RuntimeException failure) { com.mistaboom.essence_ascendance.EssenceAscendance.LOGGER.error("Rod outcome delivery failed",failure); }
    }

    /** Called strictly after the unchanged gameplay force was successfully added. */
    public static void forced(ServerPlayer player,FishingHook hook,RodAttachmentState state,Entity moved,boolean anchor,Vec3 force) {
        boolean eligible=state.survivalCast && state.survivalAttachment
                && (anchor || Objects.equals(state.attachedTarget,moved.getUUID())) && AttunementGameplay.eligible(player);
        var existing=TRACKERS.get(player.server);
        if (!eligible) {
            state.observedPull=true; // This pull cannot re-enter eligibility after a transient mode switch.
            if (existing!=null) existing.eligibleForce(player.getUUID(),anchor ? null : moved.getUUID(),false);
            return;
        }
        if (state.observedPull) return;
        state.observedPull=true;
        state.pullAction=AttunementGameplay.action("rod_pull");
        boolean hostile=moved instanceof Enemy && !player.isAlliedTo(moved);
        double initialRange=anchor ? 0 : Math.sqrt(moved.getBoundingBox().distanceToSqr(player.getEyePosition()));
        if (moved instanceof ItemEntity && Objects.equals(state.attachedTarget,moved.getUUID())) initialRange=state.attachedRange;
        var origin=new RodTrajectoryTracker.Origin(state.pullAction,player.getUUID(),hook.getUUID(),anchor ? null : moved.getUUID(),
                dimension(player),now(player.server),point(moved.position()),point(player.getEyePosition()),anchor,hostile,moved instanceof ItemEntity,
                initialRange,
                anchor && exposedWater(player,player.position()),force.y>0,moved.tickCount);
        tracker(player.server).start(origin,true);
    }
    /** Successful input release is distinct from obstruction, broken rod, timeout and lost attachment. */
    public static void released(ServerPlayer player,FishingHook hook) {
        RodTrajectoryTracker tracker=TRACKERS.get(player.server); if (tracker==null) return;
        tick(player,true);
        Entity target=hook.getHookedIn();
        tracker.release(player.getUUID(),hook.getUUID(),now(player.server),target==null ? null : point(target.position()));
    }
    public static void forget(ServerPlayer player) { var tracker=TRACKERS.get(player.server); if (tracker!=null) tracker.forget(player.getUUID()); }
    public static void clear(MinecraftServer server) { TRACKERS.remove(server); }
    public static void merged(ItemEntity first,ItemEntity second,int firstBefore,int secondBefore) {
        if (first.level().isClientSide || first.level()!=second.level()) return;
        var tracker=TRACKERS.get(first.level().getServer());
        if (tracker!=null) tracker.merged(first.getUUID(),second.getUUID(),firstBefore,secondBefore,
                first.getItem().getCount(),second.getItem().getCount());
    }
    /** Accepted native knockback is a new external cause; canceled/no-op impulses do not call this. */
    public static void competingImpulse(Entity entity) {
        if (entity.level().isClientSide) return;
        var tracker=TRACKERS.get(entity.level().getServer());
        if (tracker!=null) tracker.removed(entity.getUUID());
    }
    public static void removed(Entity entity) {
        if (entity.level().isClientSide) return;
        MinecraftServer server=entity.level().getServer();
        var tracker=TRACKERS.get(server); if (tracker==null) return;
        if (entity instanceof FishingHook) tracker.hookRemoved(entity.getUUID());
        // A death's setRemoved happens inside native hurt. Its accepted-damage commit owns final attribution.
        if (!(entity instanceof LivingEntity living) || living.isAlive()) tracker.removed(entity.getUUID());
    }
    /** Includes same-dimension and short teleports, which cannot be identified by a distance cap alone. */
    public static void teleported(Entity entity) {
        if (entity.level().isClientSide) return;
        var tracker=TRACKERS.get(entity.level().getServer());
        if (tracker!=null) { tracker.removed(entity.getUUID()); if (entity instanceof ServerPlayer p) tracker.forget(p.getUUID()); }
    }
    public static void committedDamage(LivingEntity target,DamageSource source,double loss,boolean death) {
        if (!(loss>0) || target.level().isClientSide) return;
        var server=target.level().getServer(); var tracker=TRACKERS.get(server); if (tracker==null) return;
        var origin=tracker.target(target.getUUID());
        if (origin==null) return;
        var originActor=server.getPlayerList().getPlayer(origin.actor());
        if (!AttunementGameplay.eligible(originActor) || originActor.level()!=target.level()) {
            tracker.removed(target.getUUID()); return;
        }
        boolean fall=source.is(DamageTypes.FALL);
        if (!fall) tracker.damaged(target.getUUID(),false);
        if (fall && !death) tracker.landed(target.getUUID());
        if (death) {
            tracker.died(target.getUUID(),dimension(target),now(server),fall,point(target.position()),EssenceConfigManager.skillEffects().posture().movement().maximumDisplacement(),fact->{
                var actor=server.getPlayerList().getPlayer(fact.actor());
                if (AttunementGameplay.eligible(actor) && actor.level()==target.level()) publish(server,fact);
            });
        }
    }
    public static void itemTouch(ItemEntity item,net.minecraft.world.entity.player.Player touching) {
        if (!(touching instanceof ServerPlayer player) || !AttunementGameplay.eligible(player) || item.isRemoved() || !item.isAlive()) return;
        var tracker=TRACKERS.get(player.server); if (tracker==null) return;
        var origin=tracker.target(item.getUUID());
        if (origin==null || !origin.actor().equals(player.getUUID()) || item.level()!=player.level()) return;
        if (!tracker.released(item.getUUID(),false) && !activePull(player,origin,false)) return;
        AABB pickup=player.getBoundingBox().inflate(1,0,1);
        if (item.getBoundingBox().intersects(pickup))
            tracker.arrived(item.getUUID(),player.getUUID(),dimension(item),now(player.server),point(item.position()),
                    EssenceConfigManager.skillEffects().posture().movement().maximumDisplacement(),fact->publish(player.server,fact));
    }
    /** A different actor's partial pickup may leave the same item UUID alive. Observe actual consumed count. */
    public static void itemPickup(ItemEntity item,net.minecraft.world.entity.player.Player picker,int before) {
        if (item.level().isClientSide) return;
        var tracker=TRACKERS.get(item.level().getServer());
        if (tracker!=null) tracker.pickedUp(item.getUUID(),picker.getUUID(),before,item.getItem().getCount());
    }
    /** Only actors with active sessions perform target lookups or route sampling. */
    public static void tick(ServerPlayer player) {
        tick(player,false);
    }
    private static void tick(ServerPlayer player,boolean checkpoint) {
        var tracker=TRACKERS.get(player.server); if (tracker==null) return;
        long now=now(player.server); tracker.prune(now);
        if (!AttunementGameplay.eligible(player)) { tracker.forget(player.getUUID()); return; }
        var policy=EssenceConfigManager.skillEffects().posture().movement();
        var anchor=tracker.anchor(player.getUUID());
        if (anchor!=null) {
            var previous=tracker.last(player.getUUID(),true);
            boolean forbidden=player.isPassenger() || player.getAbilities().flying || player.isFallFlying() || player.isSleeping()
                    || !tracker.released(player.getUUID(),true) && !activePull(player,anchor,checkpoint);
            boolean water=previous!=null && exposedWaterRoute(player,vec(previous),player.position());
            boolean supported=supportedDry(player);
            tracker.sampleAnchor(player.getUUID(),dimension(player),now,point(player.position()),policy.maximumDisplacement(),forbidden,water,supported,checkpoint,fact->publish(player.server,fact));
        }
        for (var origin:tracker.targets(player.getUUID())) {
            // UUID resolves the same physical native entity; despawn/replacement/merge never substitutes a new one.
            Entity target=player.serverLevel().getEntity(origin.target());
            if (target==null || !target.isAlive() || target.isRemoved() || target.level()!=player.level()) { tracker.removed(origin.target()); continue; }
            boolean forbidden=target.isPassenger() || target.isVehicle() || target instanceof LivingEntity living && living.isFallFlying()
                    || !tracker.released(origin.target(),false) && !activePull(player,origin,checkpoint);
            boolean arrival;
            if (origin.item()) {
                AABB pickup=player.getBoundingBox().inflate(1,0,1);
                arrival=target.getBoundingBox().intersects(pickup);
            } else {
                double range=player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
                arrival=target.getBoundingBox().distanceToSqr(player.getEyePosition())<=range*range;
            }
            // The same live movement-validation maximum is used for native players and launched entities.
            tracker.sampleTarget(origin.target(),dimension(target),now,point(target.position()),policy.maximumDisplacement(),forbidden,
                    target.onGround() && target.tickCount>origin.entityTick(),target.isInWater() || target.isInLava(),arrival,checkpoint,fact->publish(player.server,fact));
        }
    }
    private static boolean activePull(ServerPlayer player,RodTrajectoryTracker.Origin origin,boolean releasing) {
        if (!(player.fishing instanceof RodHookAccess access) || !player.fishing.getUUID().equals(origin.hook())
                || !AscendanceFishingService.valid(player,player.fishing)) return false;
        var state=access.essenceAscendance$rodState();
        if (origin.anchor() ? access.essenceAscendance$attachment()!=1
                : player.fishing.getHookedIn()==null || !player.fishing.getHookedIn().getUUID().equals(origin.target())) return false;
        long tick=player.level().getGameTime();
        return Objects.equals(state.pullAction,origin.action()) && (state.pulling || releasing)
                && state.lastForceTick>=tick-1 && state.lastForceTick<=tick
                && tick>=state.lastHeldInputTick && tick-state.lastHeldInputTick<=EssenceConfigManager.skillEffects().posture().movement().intentTimeoutTicks();
    }
    public static boolean supportedDry(ServerPlayer player) {
        if (!player.onGround() || player.isInWater() || player.isInLava()) return false;
        AABB feet=player.getBoundingBox().move(0,-.05,0);
        AABB slice=new AABB(feet.minX,feet.minY,feet.minZ,feet.maxX,feet.minY+.051,feet.maxZ);
        for (BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(slice.minX,slice.minY,slice.minZ),
                BlockPos.containing(slice.maxX-1e-7,slice.maxY,slice.maxZ-1e-7))) {
            var block=player.level().getBlockState(pos);
            if (block.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
            if (block.getCollisionShape(player.level(),pos,net.minecraft.world.phys.shapes.CollisionContext.empty()).toAabbs().stream()
                    .anyMatch(box->box.move(pos).intersects(slice))) return true;
        }
        return false;
    }
    /** Every half-block projects onto an exposed water column below the accepted route. */
    public static boolean exposedWaterRoute(ServerPlayer player,Vec3 from,Vec3 to) {
        double length=to.subtract(from).length();
        if (!Double.isFinite(length) || length>EssenceConfigManager.skillEffects().posture().movement().maximumDisplacement()) return false;
        int steps=Math.max(1,(int)Math.ceil(length/.5));
        if (steps>1024) return false;
        for (int i=0;i<=steps;i++) if (!exposedWater(player,from.lerp(to,(double)i/steps))) return false;
        return !player.isEyeInFluid(FluidTags.WATER);
    }
    private static boolean exposedWater(ServerPlayer player,Vec3 at) {
        BlockPos column=BlockPos.containing(at);
        if (!player.level().hasChunkAt(column)) return false;
        int top=player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,column.getX(),column.getZ())-1;
        BlockPos pos=new BlockPos(column.getX(),top,column.getZ());
        var block=player.level().getBlockState(pos); var fluid=block.getFluidState();
        return block.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock && fluid.is(FluidTags.WATER)
                && player.level().getFluidState(pos.above()).isEmpty()
                && player.level().getBlockState(pos.above()).getCollisionShape(player.level(),pos.above()).isEmpty()
                && at.y>=pos.getY()+fluid.getHeight(player.level(),pos)-.05;
    }
}
