package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ProjectileContent {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.ENTITY_TYPE);
    public static final RegistrySupplier<EntityType<MagicBoltEntity>> MAGIC_BOLT = TYPES.register("magic_bolt",
            () -> EntityType.Builder.<MagicBoltEntity>of(MagicBoltEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F).clientTrackingRange(8).updateInterval(1)
                    .build(EssenceAscendance.MOD_ID + ":magic_bolt"));
    public static final ResourceKey<DamageType> MAGIC_BOLT_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "magic_bolt"));
    private ProjectileContent() { }
    public static void init() { TYPES.register(); }
}
