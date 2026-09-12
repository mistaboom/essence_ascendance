package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SpectralArrow;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Explicit collision contracts, not a broad tag that accidentally admits utility projectiles. */
public final class ProjectileAdapters {
    private record Contract(ProjectileSource source, Predicate<Projectile> accepts) { }
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
        if (CONTRACTS.putIfAbsent(type, new Contract(ProjectileSource.RANGED_PHYSICAL,
                p -> p.getClass() == exactClass && !((AbstractArrow) p).isNoPhysics())) != null) {
            throw new IllegalArgumentException("Duplicate projectile adapter");
        }
    }
    public static ProjectileSource source(Projectile projectile) {
        if (projectile instanceof ProjectileStateAccess access && access.essenceAscendance$secondary()) return ProjectileSource.SECONDARY;
        if (projectile instanceof MagicBoltEntity) return ProjectileSource.ASCENDANCE_MAGIC;
        Contract contract = CONTRACTS.get(projectile.getType());
        return contract != null && contract.accepts.test(projectile) ? contract.source : ProjectileSource.UNSUPPORTED;
    }
}
