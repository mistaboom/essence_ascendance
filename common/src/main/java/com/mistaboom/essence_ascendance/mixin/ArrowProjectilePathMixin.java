package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.projectile.ProjectileRuntime;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only opted-in, skill-managed arrows use the shared sweep. Unmodified bows keep the whole native tick. */
@Mixin(AbstractArrow.class)
public abstract class ArrowProjectilePathMixin extends Projectile implements com.mistaboom.essence_ascendance.projectile.ProjectileStateAccess {
    @Unique private static final EntityDataAccessor<Float> essenceAscendance$flightScale =
            SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.FLOAT);
    @Override public float essenceAscendance$flightScale() { return getEntityData().get(essenceAscendance$flightScale); }
    @Override public void essenceAscendance$flightScale(float scale) { getEntityData().set(essenceAscendance$flightScale, scale); }
    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void essenceAscendance$defineFlightScale(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(essenceAscendance$flightScale, 1.0F);
    }
    @Shadow protected boolean inGround;
    @Shadow private IntOpenHashSet piercingIgnoreEntityIds;
    @Shadow protected abstract float getWaterInertia();
    @Override public boolean essenceAscendance$embedded() { return inGround; }
    @Override public int[] essenceAscendance$nativeHitIds() { return piercingIgnoreEntityIds == null ? new int[0] : piercingIgnoreEntityIds.toIntArray(); }
    protected ArrowProjectilePathMixin(EntityType<? extends Projectile> type, Level level) { super(type, level); }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$flight(CallbackInfo ci) {
        if (level().isClientSide) return;
        var state = ProjectileRuntime.state(this);
        if (state != null && state.managed() && state.ended && !inGround) {
            discard(); ci.cancel(); return;
        }
        if (!ProjectileRuntime.managed(this) || inGround) return;
        super.tick();
        if (isInWaterOrRain() || level().getBlockState(blockPosition()).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) clearFire();
        ProjectileRuntime.move(this);
        if (!isRemoved() && !inGround) {
            var physicsInput = getDeltaMovement();
            double inertia = isInWater() ? getWaterInertia() : 0.99F;
            setDeltaMovement(physicsInput.scale(inertia));
            applyGravity();
            com.mistaboom.essence_ascendance.projectile.ProjectileControlService.afterNativePhysics(this, physicsInput, inertia);
            checkInsideBlocks();
        }
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void essenceAscendance$slowNativePhysics(CallbackInfo ci) {
        if (!ProjectileRuntime.managed(this) && !inGround)
            com.mistaboom.essence_ascendance.projectile.ProjectileControlService.afterNativeArrowFlight(this,
                    isInWater() ? getWaterInertia() : 0.99F);
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$sharedPenetrationBudget(EntityHitResult hit, CallbackInfo ci) {
        var state = ProjectileRuntime.state(this);
        if (!level().isClientSide && state != null && state.managed() && !ProjectileRuntime.nativeImpact(this)) {
            ci.cancel(); return;
        }
        // The shared UUID ledger survives reload. Native's transient numeric-ID set cannot own the extra budget.
        // Keep native PierceLevel intact (including vanilla shield rules) and native killed-target bookkeeping.
        if (ProjectileRuntime.nativeImpact(this) && piercingIgnoreEntityIds != null) piercingIgnoreEntityIds.clear();
    }
    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean essenceAscendance$nativeDamage(Entity victim, DamageSource source, float amount, Operation<Boolean> original) {
        return ProjectileRuntime.damage(victim, source, amount, scaled -> original.call(victim, source, scaled));
    }
    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;discard()V", ordinal = 1))
    private void essenceAscendance$resolveContinuation(AbstractArrow arrow, Operation<Void> original) {
        // Only native's successful, non-piercing removal. Failed impacts and foreign removals are never undone.
        if (!ProjectileRuntime.nativeImpact(arrow)) original.call(arrow);
    }
}
