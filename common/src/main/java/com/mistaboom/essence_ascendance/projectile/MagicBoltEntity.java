package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueInput;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueOutput;*/
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Real, gravity-free magic delivery. All gameplay selection/collision lives in ProjectileRuntime. */
public final class MagicBoltEntity extends Projectile {
    private double launchDamage;
    public MagicBoltEntity(EntityType<? extends MagicBoltEntity> type, Level level) {
        super(type, level); setNoGravity(true);
    }
    public MagicBoltEntity(ServerPlayer owner, double damage, double speed) {
        this(ProjectileContent.MAGIC_BOLT.get(), owner.level());
        setOwner(owner); setPos(owner.getEyePosition()); launchDamage = damage;
        shoot(owner.getLookAngle().x, owner.getLookAngle().y, owner.getLookAngle().z, (float) speed, 0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Presentation only. The entity renderer owns the procedural body and trail.
            setPos(position().add(getDeltaMovement()));
        } else if (ProjectileRuntime.state(this) == null || !Double.isFinite(launchDamage) || launchDamage <= 0) discard();
        else ProjectileRuntime.move(this);
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide || !(getOwner() instanceof ServerPlayer owner)) return;
        //? if >=26.1.2
        /*DamageSource source = new DamageSource(level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)*/
        //? if <26.1.2
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                //? if >=26.1.2
                /*.getOrThrow(ProjectileContent.MAGIC_BOLT_DAMAGE), this, owner);*/
                //? if <26.1.2
                .getHolderOrThrow(ProjectileContent.MAGIC_BOLT_DAMAGE), this, owner);
        owner.setLastHurtMob(hit.getEntity());
        ProjectileRuntime.damage(hit.getEntity(), source, (float) launchDamage,
                //? if >=26.1.2
                /*amount -> hit.getEntity().hurtServer(owner.serverLevel(), source, amount));*/
                //? if <26.1.2
                amount -> hit.getEntity().hurt(source, amount));
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        // No native block callback: bolts cannot activate, destroy or modify terrain.
        setPos(hit.getLocation()); discard();
    }
    //? if >=26.1.2
    /*@Override protected void addAdditionalSaveData(ValueOutput tag) {*/
    //? if <26.1.2
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putDouble("LaunchDamage", launchDamage);
    }
    //? if >=26.1.2
    /*@Override protected void readAdditionalSaveData(ValueInput tag) {*/
    //? if <26.1.2
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        //? if >=26.1.2
        /*super.readAdditionalSaveData(tag); launchDamage = tag.getDoubleOr("LaunchDamage", 0.0); setNoGravity(true);*/
        //? if <26.1.2
        super.readAdditionalSaveData(tag); launchDamage = tag.getDouble("LaunchDamage"); setNoGravity(true);
        if (!Double.isFinite(launchDamage) || launchDamage <= 0) discard();
    }
}
