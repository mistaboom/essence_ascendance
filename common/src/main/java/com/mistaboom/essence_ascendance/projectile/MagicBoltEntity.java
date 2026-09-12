package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Real, gravity-free magic delivery. All gameplay selection/collision lives in ProjectileRuntime. */
public final class MagicBoltEntity extends Projectile implements ItemSupplier {
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
    @Override public ItemStack getItem() { return new ItemStack(Items.AMETHYST_SHARD); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Presentation only. No client targets or damage, and no client entity removal decisions.
            for (int i = 0; i < 3; i++) {
                var point = position().add(getDeltaMovement().scale(i / 3.0));
                level().addParticle(ParticleTypes.END_ROD, point.x, point.y, point.z, 0, 0, 0);
            }
            setPos(position().add(getDeltaMovement()));
        } else if (ProjectileRuntime.state(this) == null || !Double.isFinite(launchDamage) || launchDamage <= 0) discard();
        else ProjectileRuntime.move(this);
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide || !(getOwner() instanceof ServerPlayer owner)) return;
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ProjectileContent.MAGIC_BOLT_DAMAGE), this, owner);
        owner.setLastHurtMob(hit.getEntity());
        ProjectileRuntime.damage(hit.getEntity(), source, (float) launchDamage,
                amount -> hit.getEntity().hurt(source, amount));
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        // No native block callback: bolts cannot activate, destroy or modify terrain.
        setPos(hit.getLocation()); discard();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putDouble("LaunchDamage", launchDamage);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); launchDamage = tag.getDouble("LaunchDamage"); setNoGravity(true);
        if (!Double.isFinite(launchDamage) || launchDamage <= 0) discard();
    }
}
