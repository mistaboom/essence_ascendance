package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.projectile.ProjectileControlService;
import com.mistaboom.essence_ascendance.projectile.ProjectileControlState;
import com.mistaboom.essence_ascendance.projectile.ProjectileFlightTraceAccess;
import com.mistaboom.essence_ascendance.projectile.ProjectileRuntime;
import com.mistaboom.essence_ascendance.projectile.ProjectileState;
import com.mistaboom.essence_ascendance.projectile.ProjectileStateAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.List;

@Mixin(Projectile.class)
public abstract class ProjectileStateMixin implements ProjectileStateAccess, ProjectileFlightTraceAccess {
    /** Short presentation history only; never persisted and never affects projectile gameplay. */
    @Unique private static final int ESSENCE_ASCENDANCE$TRACE_POINTS = 32;
    @Unique private final ArrayDeque<Vec3> essenceAscendance$flightTrace = new ArrayDeque<>();
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
    @Override public List<Vec3> essenceAscendance$flightTrace() { return List.copyOf(essenceAscendance$flightTrace); }

    @Inject(method = "shoot", at = @At("RETURN"))
    private void essenceAscendance$launch(double x, double y, double z, float speed, float inaccuracy, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        essenceAscendance$flightTrace.clear();
        essenceAscendance$rememberFlightPoint(projectile.position());
        ProjectileRuntime.launch(projectile);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void essenceAscendance$sealUnknownLaunch(CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        essenceAscendance$rememberFlightPoint(projectile.position());
        // Unrecognized custom spawning cannot acquire a skill later by ticking.
        essenceAscendance$checked = true;
        ProjectileControlService.beforeFlight(projectile);
        ProjectileRuntime.observeNativeFlight(projectile);
    }

    @Unique
    private void essenceAscendance$rememberFlightPoint(Vec3 point) {
        Vec3 previous = essenceAscendance$flightTrace.peekLast();
        if (previous != null && previous.distanceToSqr(point) <= 1.0E-8) return;
        essenceAscendance$flightTrace.addLast(point);
        while (essenceAscendance$flightTrace.size() > ESSENCE_ASCENDANCE$TRACE_POINTS) {
            essenceAscendance$flightTrace.removeFirst();
        }
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
