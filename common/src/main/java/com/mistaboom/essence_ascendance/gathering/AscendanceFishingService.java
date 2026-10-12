package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.movement.*;
import com.mistaboom.essence_ascendance.skill.effect.CrowdControlEligibility;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Authoritative persistent attachments on the native bobber. Fishing retrieval never enters this path. */
public final class AscendanceFishingService {
    private AscendanceFishingService() { }
    public static boolean valid(ServerPlayer player, FishingHook hook) {
        if (!(hook instanceof RodHookAccess access) || access.essenceAscendance$rodHand() == 0) return false;
        var state = access.essenceAscendance$rodState();
        if (!EssenceConfigManager.authoritativeReady() || state.hand == null || state.castStack == null
                || !player.isAlive() || player.isRemoved() || player.isSpectator() || hook.isRemoved()
                || player.level() != hook.level() || player.fishing != hook
                || state.castContext == null || EquipmentTierData.tier(state.castStack) != state.castContext.itemTier()
                || player.getItemInHand(state.hand) != state.castStack || !EquipmentFishingRodService.functional(state.castStack)) return false;
        // Re-evaluate the same stack, never the newly selected rod or its stale casting tier.
        var context = EquipmentFishingRodService.context(player, state.castStack);
        return hook.distanceToSqr(player) <= context.baseline().range() * context.baseline().range();
    }
    public static boolean movable(ServerPlayer owner, Entity target) {
        if (target == null || target == owner || !target.isAlive() || target.isRemoved() || target.isSpectator()
                || target.level() != owner.level() || target.isPassenger() || target.isVehicle()
                || target.getType().is(CrowdControlEligibility.IMMUNE)) return false;
        if (target instanceof LivingEntity living)
            return !(living instanceof ArmorStand) && CrowdControlEligibility.canControl(owner, living);
        // Explicit native adapters; unknown entities require a future movement contract.
        return target instanceof Boat || target instanceof AbstractMinecart || target instanceof ItemEntity;
    }
    public static void tickAttachment(FishingHook hook, RodHookAccess access) {
        if (hook.level().isClientSide) {
            hook.setDeltaMovement(Vec3.ZERO);
            if(access.essenceAscendance$attachment()==1)hook.setPos(access.essenceAscendance$anchorPosition());
            if (access.essenceAscendance$attachment() == 2 && hook.getHookedIn() != null)
                hook.setPos(hook.getHookedIn().getBoundingBox().getCenter());
            return;
        }
        if (!(hook.getPlayerOwner() instanceof ServerPlayer player) || !valid(player, hook)) { hook.discard(); return; }
        var state = access.essenceAscendance$rodState();
        boolean block = access.essenceAscendance$attachment() == 1;
        Entity target = block ? null : hook.getHookedIn();
        if (block) {
            if (state.anchorBlock == null || !hook.level().hasChunkAt(state.anchorBlock)
                    || !hook.level().getBlockState(state.anchorBlock).equals(state.anchorGeometry)
                    || state.anchorGeometry.getCollisionShape(hook.level(), state.anchorBlock).toAabbs().stream()
                        .noneMatch(box -> box.move(state.anchorBlock).inflate(.05).contains(state.anchor))) { hook.discard(); return; }
            hook.setPos(state.anchor);
        } else {
            if (!movable(player, target)) { hook.discard(); return; }
            hook.setPos(target.getBoundingBox().getCenter());
        }
        hook.setDeltaMovement(Vec3.ZERO);
        if (!valid(player, hook) || obstructed(player, hook, state, block)) { hook.discard(); return; }
        if (!state.pulling) return; // The first release preserves an attachment indefinitely.
        long now = player.level().getGameTime();
        var policy = EssenceConfigManager.get().fishingRodBalance();
        int timeout = EssenceConfigManager.skillEffects().posture().movement().intentTimeoutTicks();
        if (!player.isUsingItem() || player.getUseItem() != state.castStack || player.getUsedItemHand() != state.hand
                || now < state.lastHeldInputTick || now - state.lastHeldInputTick > timeout
                || !TraversalService.nativeMovement(player)
                || com.mistaboom.essence_ascendance.skill.effect.ImmobilizationController.active(player)) { hook.discard(); return; }
        if (state.lastForceTick == now) return;
        state.lastForceTick = now;
        var context = EquipmentFishingRodService.context(player, state.castStack);
        Entity moved = block ? player : target;
        Vec3 before = moved instanceof ServerPlayer other ? PlayerMotionTracker.velocity(other) : moved.getDeltaMovement();
        Vec3 toward = block ? state.anchor.subtract(player.getEyePosition())
                : player.getBoundingBox().getCenter().subtract(target.getBoundingBox().getCenter());
        double resistance = target instanceof LivingEntity living ? living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) : 0;
        if (!block && state.openingFrame == null) state.openingFrame = new RodPullMath.OpeningFrame(toward, before);
        Vec3 force = block ? RodPullMath.anchorForce(toward, before, player.getLookAngle(), context.baseline(), state.poweredTicks, policy,
                player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY))
                : PersonalizedRodLaunch.force(player, state.castStack, target, toward, before, context, state.poweredTicks,
                    policy, resistance, state.entityViewScale, state.openingFrame);
        // Counter native gravity only while held beneath an anchor. Near-anchor radial force eases;
        // ordinary directional input, collisions and native drag continue to control the player.
        if (Double.isFinite(force.lengthSqr()) && force.lengthSqr() > 0) {
            moved.setDeltaMovement(before.add(force)); moved.hasImpulse = true;
            if (moved instanceof ServerPlayer other) {
                PlayerMotionTracker.forced(other);
                com.mistaboom.essence_ascendance.network.RodForcePayload.send(other, hook.getId(), player.getId(), force);
            } else {
                moved.hurtMarked = true;
                if (moved instanceof Mob mob && PersonalizedRodLaunch.multiplier(player, state.castStack, mob) > 1
                        && state.poweredTicks < policy.forceRampTicks()) {
                    // A genuine hook-set is recent activity, like ordinary accepted knockback.
                    // Prevent a pre-existing idle countdown from discarding a released launch.
                    mob.setNoActionTime(0);
                    PreciseMobMotion.launched(mob);
                }
            }
        }
        trackSkiing(player, state, block && force.horizontalDistanceSqr() > 1e-10, policy.waterSkiingDistance());
        // Continuous wear uses the native ItemStack path exactly once; Unbreaking, Efficiency,
        // maintenance and artifact fracture remain owned by their established hooks.
        state.poweredTicks = state.poweredTicks >= Integer.MAX_VALUE - 1 ? policy.forceRampTicks() : state.poweredTicks + 1;
        if (state.poweredTicks % policy.wearIntervalTicks() == 0) {
            state.castStack.hurtAndBreak(policy.wearPerInterval(), player, LivingEntity.getSlotForHand(state.hand));
            if (!EquipmentFishingRodService.functional(state.castStack)) hook.discard();
        }
    }
    private static boolean obstructed(ServerPlayer player, FishingHook hook, RodAttachmentState state, boolean block) {
        var obstruction = hook.level().clip(new ClipContext(player.getEyePosition(), hook.position(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return obstruction.getType() != HitResult.Type.MISS
                && !(block && obstruction.getBlockPos().equals(state.anchorBlock)
                    && obstruction.getLocation().distanceTo(state.anchor) < .15);
    }
    public static boolean onWaterSurface(ServerPlayer player) {
        if (player.isEyeInFluid(FluidTags.WATER) || player.isInLava()) return false;
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos pos = player.blockPosition().offset(0, dy, 0);
            var fluid = player.level().getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && player.level().getFluidState(pos.above()).isEmpty()
                    && Math.abs(player.getY() - pos.getY() - fluid.getHeight(player.level(), pos)) <= .2) return true;
        }
        return false;
    }
    private static void trackSkiing(ServerPlayer player, RodAttachmentState state, boolean horizontalPull, double threshold) {
        var motion = EssenceConfigManager.skillEffects().posture().movement();
        double step = RodPullMath.skiingStep(state.lastTravelPosition, player.position(), state.previousWaterPull,
                onWaterSurface(player), player.isPassenger(), false, Math.min(4, motion.maximumDisplacement()));
        state.lastTravelPosition = player.position();
        state.previousWaterPull = horizontalPull && onWaterSurface(player);
        if (!onWaterSurface(player)) { state.waterTravel = 0; return; }
        state.waterTravel += step;
        if (state.waterTravel >= threshold) {
            var advancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "water_skiing"));
            if (advancement != null) player.getAdvancements().award(advancement, "travel");
        }
    }
    public static void heldInput(ServerPlayer player, int hookId, boolean held) {
        heldInput(player, hookId, held, Double.NaN);
    }
    public static void heldInput(ServerPlayer player, int hookId, boolean held, double entityViewScale) {
        if (player.fishing == null || player.fishing.getId() != hookId || !valid(player, player.fishing)) return;
        var access = (RodHookAccess) player.fishing; var state = access.essenceAscendance$rodState();
        if (Double.isFinite(entityViewScale)) state.entityViewScale = Math.clamp(entityViewScale, .25, 12.5);
        if (held && player.isUsingItem() && player.getUseItem() == state.castStack && player.getUsedItemHand() == state.hand)
            state.lastHeldInputTick = player.level().getGameTime();
        else if (!held && state.release()) player.fishing.discard();
    }
    public static void forget(ServerPlayer player) {
        if (player.fishing instanceof RodHookAccess access && access.essenceAscendance$rodHand() != 0) player.fishing.discard();
    }
    public static void tickOwner(ServerPlayer player) {
        if(player.fishing instanceof RodHookAccess access && access.essenceAscendance$rodHand()!=0
                && (!valid(player,player.fishing) || !player.level().hasChunkAt(player.fishing.blockPosition())))player.fishing.discard();
    }
}
