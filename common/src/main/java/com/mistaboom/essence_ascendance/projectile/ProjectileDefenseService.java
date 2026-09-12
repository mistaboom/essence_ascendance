package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Explicit shield-layer contracts. No entity names, blanket damage tags or generic invulnerability bypass. */
public final class ProjectileDefenseService {
    public static final ResourceLocation VANILLA_SHIELD = ResourceLocation.fromNamespaceAndPath("minecraft", "shield");
    private static final Map<ResourceLocation, Predicate<Context>> DETECTORS = new LinkedHashMap<>();
    static {
        // Use native facing, raise delay, shield capability, and existing arrow/tag bypass rules.
        register(VANILLA_SHIELD, context -> context.target().isDamageSourceBlocked(context.source()));
    }
    private ProjectileDefenseService() { }

    public record Context(Projectile projectile, LivingEntity target, DamageSource source) {
        public Context {
            Objects.requireNonNull(projectile); Objects.requireNonNull(target); Objects.requireNonNull(source);
        }
    }

    /** Register on mod initialization, after verifying the defense's actual damage contract.
     * The detector must be side-effect free and report whether this specific hit would meet its shield.
     * A companion hook at that mod's shield operation must consult bypasses() with this SAME id.
     * Merely registering a detector does not alter another mod's damage code. */
    public static void register(ResourceLocation defense, Predicate<Context> activeShield) {
        Objects.requireNonNull(defense); Objects.requireNonNull(activeShield);
        if (DETECTORS.putIfAbsent(defense, activeShield) != null) {
            throw new IllegalArgumentException("Duplicate projectile defense adapter: " + defense);
        }
    }

    static Resolution resolve(Context context, ProjectileState snapshot) {
        if (snapshot.path != ProjectilePath.PIERCING || snapshot.piercingShieldMultiplier <= 0) return Resolution.NONE;
        Set<ResourceLocation> shields = new LinkedHashSet<>();
        DETECTORS.forEach((id, detector) -> { if (detector.test(context)) shields.add(id); });
        return new Resolution(shields, snapshot.piercingShieldMultiplier);
    }

    /** Only true inside the matching authoritative projectile hurt call, for a detected, opted-in layer.
     * Bridges must skip ONLY that shield operation. Keep armor, PvP, cancellation, scripted boss phases,
     * and general invulnerability intact. Never use this as an unconditional "allow damage" flag. */
    public static boolean bypasses(ResourceLocation defense, LivingEntity target, DamageSource source) {
        return ProjectileRuntime.bypassesDefense(defense, target, source);
    }

    /** One shield penalty per impact even if several supported layers overlap. No extra penetration cost. */
    public record Resolution(Set<ResourceLocation> shields, double damageMultiplier) {
        public static final Resolution NONE = new Resolution(Set.of(), 1);
        public Resolution {
            shields = Set.copyOf(shields);
            if (!Double.isFinite(damageMultiplier) || damageMultiplier < 0 || damageMultiplier > 1) {
                throw new IllegalArgumentException("Shield damage multiplier must be between 0 and 1");
            }
            if (shields.isEmpty()) damageMultiplier = 1;
        }
        public boolean bypasses(ResourceLocation defense) { return shields.contains(defense); }
    }
}
