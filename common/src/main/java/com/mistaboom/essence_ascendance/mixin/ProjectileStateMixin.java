package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.projectile.ProjectileRuntime;
import com.mistaboom.essence_ascendance.projectile.ProjectileState;
import com.mistaboom.essence_ascendance.projectile.ProjectileStateAccess;
import com.mistaboom.essence_ascendance.projectile.ProjectileControlState;
import com.mistaboom.essence_ascendance.projectile.ProjectileControlService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileStateMixin implements ProjectileStateAccess {
    @Unique private ProjectileState essenceAscendance$projectileState;
    @Unique private boolean essenceAscendance$checked;
    @Unique private boolean essenceAscendance$secondary;
    @Unique private ProjectileControlState essenceAscendance$controlState;
    @Override public ProjectileControlState essenceAscendance$control() { return essenceAscendance$controlState; }
    @Override public void essenceAscendance$control(ProjectileControlState state) { essenceAscendance$controlState = state; }
    @Override public boolean essenceAscendance$secondary() { return essenceAscendance$secondary; }
    @Override public void essenceAscendance$secondary(boolean secondary) { essenceAscendance$secondary = secondary; }
    @Override public ProjectileState essenceAscendance$state() { return essenceAscendance$projectileState; }
    @Override public void essenceAscendance$state(ProjectileState state) { essenceAscendance$projectileState = state; }
    @Override public boolean essenceAscendance$launchChecked() { return essenceAscendance$checked; }
    @Override public void essenceAscendance$launchChecked(boolean checked) { essenceAscendance$checked = checked; }

    @Inject(method = "shoot", at = @At("RETURN"))
    private void essenceAscendance$launch(double x, double y, double z, float speed, float inaccuracy, CallbackInfo ci) {
        ProjectileRuntime.launch((Projectile) (Object) this);
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void essenceAscendance$sealUnknownLaunch(CallbackInfo ci) {
        // Unrecognized custom spawning cannot acquire a skill later by ticking.
        essenceAscendance$checked = true;
        ProjectileControlService.beforeFlight((Projectile) (Object) this);
        ProjectileRuntime.observeNativeFlight((Projectile) (Object) this);
    }
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void essenceAscendance$save(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("EssenceProjectileSecondary", essenceAscendance$secondary);
        if (essenceAscendance$projectileState != null) tag.put("EssenceProjectile", essenceAscendance$projectileState.save());
        if (essenceAscendance$controlState != null) tag.put("EssenceProjectileControl", essenceAscendance$controlState.save());
    }
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void essenceAscendance$load(CompoundTag tag, CallbackInfo ci) {
        essenceAscendance$checked = true; // Never acquire a new loadout from a saved arrow.
        essenceAscendance$secondary = tag.getBoolean("EssenceProjectileSecondary");
        if (tag.contains("EssenceProjectileControl")) {
            essenceAscendance$controlState = ProjectileControlState.load(tag.getCompound("EssenceProjectileControl"));
            if (essenceAscendance$controlState == null) ((Projectile) (Object) this).discard();
        }
        if (tag.contains("EssenceProjectile")) {
            essenceAscendance$projectileState = ProjectileState.load(tag.getCompound("EssenceProjectile"));
            if (essenceAscendance$projectileState == null) ((Projectile) (Object) this).discard();
        }
    }
}
