package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One entity owns one immutable launch selection and one finite continuation budget. */
public final class ProjectileState {
    public final ProjectileSource source;
    public final ProjectilePath path;
    public final UUID owner, ownerLife;
    public final ResourceLocation dimension;
    public final ProjectileBalanceSettings.Profile profile;
    public final long launchedAt;
    public final double maximumSpeed, ricochetRadius, ricochetFalloff, piercingFalloff, piercingShieldMultiplier;
    public final int maximumImpacts;
    public final Set<UUID> visited = new LinkedHashSet<>();
    public double remainingRange, damageMultiplier = 1;
    public int remainingTicks, ricochets, nativePenetrations, skillPenetrations;
    public UUID target;
    public boolean chargeDischarged, ended;
    public ProjectileImpactEffects.Snapshot payload;

    public ProjectileState(ProjectileSource source, ProjectilePath path, UUID owner, UUID ownerLife,
                           ResourceLocation dimension, long launchedAt,
                           ProjectileBalanceSettings settings, int nativePenetrations) {
        this.source = source; this.path = path; this.owner = owner; this.ownerLife = ownerLife; this.dimension = dimension;
        this.launchedAt = launchedAt;
        profile = source == ProjectileSource.ASCENDANCE_MAGIC ? settings.caster() : settings.arrow();
        remainingRange = profile.range(); remainingTicks = profile.lifetimeTicks();
        maximumSpeed = settings.maximumSpeed(); ricochetRadius = settings.ricochetRadius();
        ricochetFalloff = settings.ricochetDamageMultiplier(); piercingFalloff = settings.piercingDamageMultiplier();
        piercingShieldMultiplier = settings.piercingShieldDamageMultiplier();
        this.nativePenetrations = Math.clamp(nativePenetrations, 0, 127);
        maximumImpacts = Math.max(settings.maximumImpacts(), this.nativePenetrations + 1);
        ricochets = path == ProjectilePath.RICOCHET ? settings.ricochets() : 0;
        skillPenetrations = path == ProjectilePath.PIERCING ? settings.penetrations() : 0;
    }

    public boolean managed() { return source == ProjectileSource.ASCENDANCE_MAGIC || path != ProjectilePath.NONE || payload != null; }
    public boolean visit(UUID id) { return visited.size() < maximumImpacts && visited.add(id); }
    public boolean canContinue() { return visited.size() < maximumImpacts && damageMultiplier > 0.000001 && remainingRange > 0.000001; }

    /** Native piercing is spent first at native damage. Only extra skill penetrations incur falloff. */
    public boolean penetrate() {
        if (!canContinue()) return false;
        if (nativePenetrations > 0) { nativePenetrations--; return true; }
        if (skillPenetrations <= 0) return false;
        skillPenetrations--; damageMultiplier *= piercingFalloff;
        return canContinue();
    }

    public boolean ricochet() {
        if (!canContinue() || ricochets <= 0) return false;
        ricochets--; damageMultiplier *= ricochetFalloff;
        return canContinue();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Source", source.name()); tag.putString("Path", path.name());
        tag.putUUID("Owner", owner); tag.putUUID("OwnerLife", ownerLife); tag.putString("Dimension", dimension.toString());
        tag.putLong("LaunchedAt", launchedAt);
        tag.putDouble("Range", profile.range()); tag.putDouble("Speed", profile.speed());
        tag.putInt("Lifetime", profile.lifetimeTicks()); tag.putDouble("AcquireRange", profile.acquisitionRange());
        tag.putDouble("Cone", profile.acquisitionConeDegrees()); tag.putDouble("Turn", profile.turnDegreesPerTick());
        tag.putDouble("MaximumSpeed", maximumSpeed); tag.putDouble("RicochetRadius", ricochetRadius);
        tag.putDouble("RicochetFalloff", ricochetFalloff); tag.putDouble("PiercingFalloff", piercingFalloff);
        tag.putDouble("PiercingShieldMultiplier", piercingShieldMultiplier);
        tag.putInt("MaximumImpacts", maximumImpacts); tag.putDouble("RemainingRange", remainingRange);
        tag.putInt("RemainingTicks", remainingTicks); tag.putDouble("Multiplier", damageMultiplier);
        tag.putInt("Ricochets", ricochets); tag.putInt("NativePenetrations", nativePenetrations);
        tag.putInt("SkillPenetrations", skillPenetrations); tag.putBoolean("ChargeDischarged", chargeDischarged);
        tag.putBoolean("Ended", ended);
        if (target != null) tag.putUUID("Target", target);
        ListTag ids = new ListTag(); visited.forEach(id -> ids.add(StringTag.valueOf(id.toString())));
        tag.put("Visited", ids);
        if (payload != null) tag.put("Payload", payload.save());
        return tag;
    }

    /** Corrupt snapshots fail closed. Loading never consults the player's current loadout. */
    public static ProjectileState load(CompoundTag tag) {
        try {
            var profile = new ProjectileBalanceSettings.Profile(tag.getDouble("Range"), tag.getDouble("Speed"),
                    tag.getInt("Lifetime"), tag.getDouble("AcquireRange"), tag.getDouble("Cone"), tag.getDouble("Turn"));
            var settings = new ProjectileBalanceSettings(profile, profile, tag.getInt("Ricochets"),
                    tag.getDouble("RicochetRadius"), tag.getDouble("RicochetFalloff"),
                    tag.getInt("SkillPenetrations"), tag.getDouble("PiercingFalloff"),
                    tag.getDouble("PiercingShieldMultiplier"),
                    tag.getInt("MaximumImpacts"), tag.getDouble("MaximumSpeed"));
            ProjectileState state = new ProjectileState(ProjectileSource.valueOf(tag.getString("Source")),
                    ProjectilePath.valueOf(tag.getString("Path")), tag.getUUID("Owner"), tag.getUUID("OwnerLife"),
                    ResourceLocation.parse(tag.getString("Dimension")), tag.getLong("LaunchedAt"),
                    settings, tag.getInt("NativePenetrations"));
            state.remainingRange = tag.getDouble("RemainingRange"); state.remainingTicks = tag.getInt("RemainingTicks");
            state.damageMultiplier = tag.getDouble("Multiplier"); state.ended = tag.getBoolean("Ended");
            state.chargeDischarged = tag.getBoolean("ChargeDischarged");
            if (tag.hasUUID("Target")) state.target = tag.getUUID("Target");
            ListTag ids = tag.getList("Visited", Tag.TAG_STRING);
            if (ids.size() > state.maximumImpacts) return null;
            for (int i = 0; i < ids.size(); i++) state.visited.add(UUID.fromString(ids.getString(i)));
            if (tag.contains("Payload")) {
                state.payload = ProjectileImpactEffects.Snapshot.load(tag.getCompound("Payload"));
                if (state.payload == null) return null;
            }
            if (!Double.isFinite(state.remainingRange) || state.remainingRange < 0 || state.remainingRange > profile.range()
                    || !Double.isFinite(state.damageMultiplier) || state.damageMultiplier <= 0 || state.damageMultiplier > 1
                    || state.remainingTicks < 0 || state.remainingTicks > profile.lifetimeTicks()
                    || state.source == ProjectileSource.UNSUPPORTED || state.source == ProjectileSource.SECONDARY) return null;
            return state;
        } catch (IllegalArgumentException exception) { return null; }
    }
}
