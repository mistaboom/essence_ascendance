package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService;
import com.mistaboom.essence_ascendance.gathering.*;
//? if >=26.1.2
/*import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;*/
//? if <26.1.2
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Extends the native bobber rather than introducing competing fishing/loot or rendering implementations. */
@Mixin(FishingHook.class)
public abstract class AscendanceFishingHookMixin extends Projectile implements RodHookAccess {
    //? if <26.1.2 {
    @Unique private static final EntityDataAccessor<Integer> essenceAscendance$rodHand = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.INT);
    @Unique private static final EntityDataAccessor<Integer> essenceAscendance$attachment = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.INT);
    @Unique private static final EntityDataAccessor<Integer> essenceAscendance$rodTier = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.INT);
    @Unique private static final EntityDataAccessor<net.minecraft.core.BlockPos> essenceAscendance$anchorBlock = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.BLOCK_POS);
    @Unique private static final EntityDataAccessor<org.joml.Vector3f> essenceAscendance$anchorOffset = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.VECTOR3);
    //?}
    @Unique private final RodAttachmentState essenceAscendance$rod = new RodAttachmentState();
    @Shadow private int life;
    @Shadow protected abstract void setHookedEntity(Entity entity);
    protected AscendanceFishingHookMixin(EntityType<? extends Projectile> type, Level level) { super(type, level); }
    //? if <26.1.2 {
    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void essenceAscendance$rodData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(essenceAscendance$rodHand, 0); builder.define(essenceAscendance$attachment, 0); builder.define(essenceAscendance$rodTier, 0);
        builder.define(essenceAscendance$anchorBlock,net.minecraft.core.BlockPos.ZERO); builder.define(essenceAscendance$anchorOffset,new org.joml.Vector3f());
    }
    //?}
    @Override public RodAttachmentState essenceAscendance$rodState() { return essenceAscendance$rod; }
    @Override public int essenceAscendance$rodHand() {
        //? if >=26.1.2
        /*return EntityPresentationBridge2612.get(this).rodHand();*/
        //? if <26.1.2
        return entityData.get(essenceAscendance$rodHand);
    }
    @Override public com.mistaboom.essence_ascendance.equipment.EquipmentTier essenceAscendance$rodTier() {
        return com.mistaboom.essence_ascendance.equipment.EquipmentTier.values()[essenceAscendance$rodTierOrdinal()];
    }
    @Unique private int essenceAscendance$rodTierOrdinal() {
        //? if >=26.1.2
        /*return EntityPresentationBridge2612.get(this).rodTier();*/
        //? if <26.1.2
        return entityData.get(essenceAscendance$rodTier);
    }
    @Override public int essenceAscendance$attachment() {
        //? if >=26.1.2
        /*return EntityPresentationBridge2612.get(this).attachment();*/
        //? if <26.1.2
        return entityData.get(essenceAscendance$attachment);
    }
    @Override public Vec3 essenceAscendance$anchorPosition() {
        //? if >=26.1.2
        /*return EntityPresentationBridge2612.get(this).anchor();*/
        //? if <26.1.2 {
        var block=entityData.get(essenceAscendance$anchorBlock);var offset=entityData.get(essenceAscendance$anchorOffset);
        return Vec3.atLowerCornerOf(block).add(offset.x,offset.y,offset.z);
        //?}
    }
    @Override public void essenceAscendance$initializeRod(InteractionHand hand, ItemStack stack, EquipmentFishingRodService.Context context) {
        essenceAscendance$rod.cast(hand, stack, context);
        //? if >=26.1.2 {
        /*EntityPresentationBridge2612.set(this, EntityPresentationBridge2612.get(this)
                .withRod(hand == InteractionHand.MAIN_HAND ? 1 : 2, context.itemTier().ordinal()));
        *///?} else {
        entityData.set(essenceAscendance$rodHand, hand == InteractionHand.MAIN_HAND ? 1 : 2);
        entityData.set(essenceAscendance$rodTier, context.itemTier().ordinal());
        //?}
        setDeltaMovement(((FishingHook)(Object)this).getPlayerOwner().getLookAngle().scale(context.castSpeed()));
        setNoGravity(false);
    }
    @Override public void essenceAscendance$attachBlock(BlockHitResult hit) {
        essenceAscendance$rod.anchorBlock = hit.getBlockPos().immutable();
        essenceAscendance$rod.anchorGeometry = level().getBlockState(hit.getBlockPos());
        //? if >=26.1.2
        /*essenceAscendance$rod.anchor = hit.getLocation().add(hit.getDirection().getUnitVec3().scale(.025));*/
        //? if <26.1.2
        essenceAscendance$rod.anchor = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.025));
        //? if >=26.1.2 {
        /*EntityPresentationBridge2612.set(this, EntityPresentationBridge2612.get(this)
                .withAttachment(1, essenceAscendance$rod.anchor));
        *///?} else {
        var offset=essenceAscendance$rod.anchor.subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        entityData.set(essenceAscendance$anchorBlock,hit.getBlockPos().immutable());
        entityData.set(essenceAscendance$anchorOffset,new org.joml.Vector3f((float)offset.x,(float)offset.y,(float)offset.z));
        entityData.set(essenceAscendance$attachment, 1);
        //?}
        setPos(essenceAscendance$rod.anchor); setDeltaMovement(Vec3.ZERO);
    }
    @Override public void essenceAscendance$attachEntity(Entity entity) {
        setHookedEntity(entity);
        //? if >=26.1.2 {
        /*EntityPresentationBridge2612.set(this, EntityPresentationBridge2612.get(this)
                .withAttachment(2, entity.getBoundingBox().getCenter()));
        *///?} else {
        entityData.set(essenceAscendance$attachment, 2);
        //?}
        setDeltaMovement(Vec3.ZERO);
        setPos(entity.getBoundingBox().getCenter());
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$rodTick(CallbackInfo ci) {
        FishingHook hook = (FishingHook)(Object)this;
        if (essenceAscendance$rodHand() == 0) return;
        if (!level().isClientSide) {
            if (!(hook.getPlayerOwner() instanceof ServerPlayer player) || !AscendanceFishingService.valid(player, hook)) { discard(); ci.cancel(); return; }
            life = 0; // Valid Ascendance attachments/misses have no inherited grounded lifetime cutoff.
        }
        if (essenceAscendance$attachment() == 0 && !essenceAscendance$rod.waterFishing && hook.getHookedIn() == null) {
            // Sweep water as well as solids: fast casts must not tunnel through a shallow pool.
            // Predict the same landing on the client because native bobbers do not interpolate flight.
            var hit = ProjectileUtil.getHitResultOnMoveVector(this, entity -> canHitEntity(entity));
            if (hit instanceof EntityHitResult entityHit) {
                if (!level().isClientSide && hook.getPlayerOwner() instanceof ServerPlayer player) {
                    if (AscendanceFishingService.movable(player, entityHit.getEntity())) essenceAscendance$attachEntity(entityHit.getEntity());
                    else setDeltaMovement(Vec3.ZERO); // Unsupported collisions remain click-retractable.
                }
            } else if (!essenceAscendance$landInWater(hit) && !level().isClientSide
                    && hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                    && !level().getBlockState(blockHit.getBlockPos()).getCollisionShape(level(), blockHit.getBlockPos()).isEmpty()) {
                essenceAscendance$attachBlock(blockHit);
            }
        }
        if (essenceAscendance$attachment() != 0) {
            super.tick(); AscendanceFishingService.tickAttachment(hook, this); ci.cancel();
        }
    }
    @Unique private boolean essenceAscendance$landInWater(HitResult solidHit) {
        if (level().getFluidState(blockPosition()).is(net.minecraft.tags.FluidTags.WATER)) return true;
        Vec3 movement = getDeltaMovement();
        var waterHit = level().clip(new net.minecraft.world.level.ClipContext(position(), position().add(movement),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, this));
        if (waterHit.getType() != HitResult.Type.BLOCK
                || !level().getFluidState(waterHit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)
                || (solidHit.getType() != HitResult.Type.MISS
                    && position().distanceToSqr(waterHit.getLocation()) >= position().distanceToSqr(solidHit.getLocation()))) return false;
        setPos(waterHit.getLocation().add(movement.normalize().scale(.001)));
        setDeltaMovement(Vec3.ZERO);
        // The native tick below now sees water and enters BOBBING, retaining its countdown/loot logic.
        return true;
    }
    @Inject(method = "checkCollision", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$oneCollisionPass(CallbackInfo ci) { if (essenceAscendance$rodHand() != 0) ci.cancel(); }
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0))
    private boolean essenceAscendance$waterLanding(boolean water) {
        if (water && essenceAscendance$rodHand() != 0) essenceAscendance$rod.waterFishing = true;
        return water;
    }
    @Inject(method = "shouldStopFishing", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$rodOwner(net.minecraft.world.entity.player.Player owner, CallbackInfoReturnable<Boolean> cir) {
        if (essenceAscendance$rodHand() == 0) return;
        boolean invalid = !(owner instanceof ServerPlayer player) || !AscendanceFishingService.valid(player, (FishingHook)(Object)this);
        if (invalid) discard(); cir.setReturnValue(invalid);
    }
    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$retrievalIdentity(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        if (essenceAscendance$rodHand() == 0) return;
        FishingHook hook = (FishingHook)(Object)this;
        if (!(hook.getPlayerOwner() instanceof ServerPlayer player) || !AscendanceFishingService.valid(player, hook)) {
            discard(); cir.setReturnValue(0); return;
        }
        if (rod != essenceAscendance$rod.castStack || !essenceAscendance$rod.armed || essenceAscendance$attachment() != 0)
            cir.setReturnValue(0);
    }
    //? if >=26.1.2 {
    /*
    @Inject(method = "getInterpolation", at = @At("RETURN"))
    private void essenceAscendance$attachedPosition(CallbackInfoReturnable<InterpolationHandler> ci) {
        // Native zero-step interpolation snaps and cancels old interpolation at the packet boundary.
        ci.getReturnValue().setInterpolationLength(essenceAscendance$rodHand() != 0 && essenceAscendance$attachment() != 0
                ? 0 : InterpolationHandler.DEFAULT_INTERPOLATION_STEPS);
    }
    *///?} else {
    @Inject(method="lerpTo",at=@At("HEAD"))
    private void essenceAscendance$attachedPosition(double x,double y,double z,float yaw,float pitch,int steps,CallbackInfo ci) {
        if(essenceAscendance$rodHand()!=0 && essenceAscendance$attachment()!=0)setPos(x,y,z);
    }
    //?}

    @Inject(method="shouldRenderAtSqrDistance",at=@At("HEAD"),cancellable=true)
    private void essenceAscendance$rodRenderRange(double distance,CallbackInfoReturnable<Boolean> cir) {
        if(essenceAscendance$rodHand()==0)return;
        var runtime=com.mistaboom.essence_ascendance.config.EssenceConfigManager.clientRuntime();
        if(runtime==null)runtime=com.mistaboom.essence_ascendance.config.EssenceConfigManager.serverRuntime();
        int tier=essenceAscendance$rodTierOrdinal();
        if(runtime!=null && tier>=0 && tier<com.mistaboom.essence_ascendance.equipment.EquipmentTier.values().length) {
            double range=Math.max(64,runtime.config().fishingRodBalance().tier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.values()[tier]).range()+16);
            cir.setReturnValue(distance<range*range);
        }
    }
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = -0.03D))
    private double essenceAscendance$trajectory(double nativeGravity) {
        if (essenceAscendance$rodHand() == 0) return nativeGravity;
        if (essenceAscendance$rod.castContext != null) return -essenceAscendance$rod.castContext.baseline().gravity();
        var runtime = com.mistaboom.essence_ascendance.config.EssenceConfigManager.clientRuntime();
        int tier = essenceAscendance$rodTierOrdinal();
        return runtime != null && tier >= 0 && tier < com.mistaboom.essence_ascendance.equipment.EquipmentTier.values().length
                ? -runtime.config().fishingRodBalance().tier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.values()[tier]).gravity() : nativeGravity;
    }
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = .92D))
    private double essenceAscendance$castDrag(double nativeDrag) { return essenceAscendance$rodHand() != 0 && !essenceAscendance$rod.waterFishing ? 1 : nativeDrag; }
}
