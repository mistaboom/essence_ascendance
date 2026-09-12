package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Projectile.class)
public interface ProjectileNativeAccess {
    @Invoker("canHitEntity") boolean essenceAscendance$canHit(Entity entity);
    @Invoker("hitTargetOrDeflectSelf") ProjectileDeflection essenceAscendance$impact(HitResult hit);
}
