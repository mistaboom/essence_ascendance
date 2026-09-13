package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SpectralArrow;

import java.util.IdentityHashMap;
import java.util.Map;

/** Explicit collision contracts, not a broad tag that accidentally admits utility projectiles. */
public final class ProjectileAdapters {
    private record Contract(ProjectileSource source, Class<? extends AbstractArrow> exactClass) { }
    private static final Map<EntityType<?>, Contract> CONTRACTS = new IdentityHashMap<>();
    static {
        registerArrow(EntityType.ARROW, Arrow.class);
        registerArrow(EntityType.SPECTRAL_ARROW, SpectralArrow.class);
    }
    private ProjectileAdapters() { }

    /** Opt in an exact modded arrow class only after verifying native AbstractArrow collision/impact semantics.
     * Overrides with explosions, duplicate hurt calls, custom movement or return/teleport behavior are unsafe.
     * The common AbstractArrow hook supplies movement, native damage and persistence for this contract. */
    public static void registerArrow(EntityType<?> type, Class<? extends AbstractArrow> exactClass) {
        if (CONTRACTS.putIfAbsent(type, new Contract(ProjectileSource.RANGED_PHYSICAL, exactClass)) != null) {
            throw new IllegalArgumentException("Duplicate projectile adapter");
        }
    }
    public static ProjectileSource source(Projectile projectile) {
        return classify(projectile.getType(), projectile.getClass(), projectile instanceof AbstractArrow arrow && arrow.isNoPhysics(),
                projectile instanceof ProjectileStateAccess access && access.essenceAscendance$secondary());
    }
    /** Explicit type AND exact class, also usable for no-world adapter diagnostics. */
    public static ProjectileSource classify(EntityType<?> type, Class<?> exactClass, boolean noPhysics, boolean secondary) {
        if (secondary) return ProjectileSource.SECONDARY;
        if (exactClass == MagicBoltEntity.class) return ProjectileSource.ASCENDANCE_MAGIC;
        Contract contract = CONTRACTS.get(type);
        return contract != null && contract.exactClass == exactClass && !noPhysics ? contract.source : ProjectileSource.UNSUPPORTED;
    }
    public static boolean damaging(Projectile projectile) {
        var source = source(projectile);
        return source == ProjectileSource.RANGED_PHYSICAL || source == ProjectileSource.ASCENDANCE_MAGIC;
    }
    public static java.util.List<String> diagnostics() {
        var adapters = CONTRACTS.entrySet().stream().map(entry ->
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()) + "="
                        + entry.getValue().exactClass().getSimpleName()).sorted().toList();
        long optional = CONTRACTS.keySet().stream().filter(type -> type != EntityType.ARROW && type != EntityType.SPECTRAL_ARROW).count();
        return java.util.List.of("Adapters: " + adapters + "; Ascendance Bow shares Arrow; real Caster=MagicBoltEntity",
                "Optional projectile adapters registered=" + optional + "; unknown classes and utility projectiles excluded");
    }
    public static boolean inFlight(Projectile projectile) {
        return damaging(projectile) && !projectile.isRemoved() && projectile.isAlive()
                && !((ProjectileStateAccess) projectile).essenceAscendance$embedded()
                && ProjectileControlMath.finite(projectile.getDeltaMovement())
                && projectile.getDeltaMovement().lengthSqr() > 0.000001;
    }
}
