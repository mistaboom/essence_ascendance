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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only opted-in, skill-managed arrows use the shared sweep. Unmodified bows keep the whole native tick. */
@Mixin(AbstractArrow.class)
public abstract class ArrowProjectilePathMixin extends Projectile {
    @Shadow protected boolean inGround;
    @Shadow private IntOpenHashSet piercingIgnoreEntityIds;
    @Shadow protected abstract float getWaterInertia();
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
            setDeltaMovement(getDeltaMovement().scale(isInWater() ? getWaterInertia() : 0.99));
            applyGravity();
            checkInsideBlocks();
        }
        ci.cancel();
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
