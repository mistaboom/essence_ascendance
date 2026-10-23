package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Projectile.class)
public interface ProjectileNativeAccess {
    //? if >=26.1.2
    /*@Accessor("owner") net.minecraft.world.entity.EntityReference<Entity> essenceAscendance$ownerReference();*/
    //? if <26.1.2
    @Accessor("ownerUUID") java.util.UUID essenceAscendance$ownerUUID();
    @Accessor("leftOwner") void essenceAscendance$leftOwner(boolean left);
    @Invoker("canHitEntity") boolean essenceAscendance$canHit(Entity entity);
    @Invoker("hitTargetOrDeflectSelf") ProjectileDeflection essenceAscendance$impact(HitResult hit);
}
