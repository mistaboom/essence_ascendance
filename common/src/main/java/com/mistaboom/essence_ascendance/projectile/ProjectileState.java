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
    public boolean chargeDischarged, ended, redirected;
    public final Set<UUID> payloadVictims = new LinkedHashSet<>();
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

    public boolean managed() { return source == ProjectileSource.ASCENDANCE_MAGIC || path != ProjectilePath.NONE || payload != null || redirected; }
    public int remainingPayloadTriggers() { return payload == null ? 0 : Math.max(0, payload.triggerBudget() - payloadVictims.size()); }
    public boolean claimPayloadImpact(UUID victim, double confirmedDamage) {
        return victim != null && visited.contains(victim) && Double.isFinite(confirmedDamage) && confirmedDamage > 0
                && remainingPayloadTriggers() > 0 && payloadVictims.add(victim);
    }

    /** Transfer responsibility without granting either player's selected offensive skills or resetting native budgets. */
    public ProjectileState transferTo(UUID defender, UUID defenderLife, double theftTurnDegreesPerTick) {
        CompoundTag tag = save();
        tag.putUUID("Owner", defender); tag.putUUID("OwnerLife", defenderLife);
        tag.putString("Path", ProjectilePath.NONE.name()); tag.remove("Payload"); tag.remove("PayloadVictims");
        tag.remove("Target"); tag.putDouble("Turn", Math.max(profile.turnDegreesPerTick(), theftTurnDegreesPerTick));
        tag.putInt("Ricochets", 0); tag.putInt("SkillPenetrations", 0);
        tag.putBoolean("ChargeDischarged", true); tag.putBoolean("Redirected", true);
        return load(tag);
    }
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
        tag.putBoolean("Ended", ended); tag.putBoolean("Redirected", redirected);
        if (target != null) tag.putUUID("Target", target);
        ListTag ids = new ListTag(); visited.forEach(id -> ids.add(StringTag.valueOf(id.toString())));
        tag.put("Visited", ids);
        if (payload != null) tag.put("Payload", payload.save());
        ListTag payloadIds = new ListTag(); payloadVictims.forEach(id -> payloadIds.add(StringTag.valueOf(id.toString())));
        tag.put("PayloadVictims", payloadIds);
        return tag;
    }

    /** Corrupt snapshots fail closed. Loading never consults the player's current loadout. */
    public static ProjectileState load(CompoundTag tag) {
        try {
            ProjectileState state = new ProjectileState(tag);
            state.remainingRange = tag.getDouble("RemainingRange"); state.remainingTicks = tag.getInt("RemainingTicks");
            state.damageMultiplier = tag.getDouble("Multiplier"); state.ended = tag.getBoolean("Ended");
            state.chargeDischarged = tag.getBoolean("ChargeDischarged");
            state.redirected = tag.getBoolean("Redirected");
            if (tag.hasUUID("Target")) state.target = tag.getUUID("Target");
            ListTag ids = tag.getList("Visited", Tag.TAG_STRING);
            if (ids.size() > state.maximumImpacts) return null;
            for (int i = 0; i < ids.size(); i++) if (!state.visited.add(UUID.fromString(ids.getString(i)))) return null;
            if (tag.contains("Payload")) {
                state.payload = ProjectileImpactEffects.Snapshot.load(tag.getCompound("Payload"));
                if (state.payload == null) return null;
            }
            ListTag payloadIds = tag.getList("PayloadVictims", Tag.TAG_STRING);
            if (payloadIds.size() > (state.payload == null ? 0 : state.payload.triggerBudget())) return null;
            for (int i = 0; i < payloadIds.size(); i++)
                if (!state.payloadVictims.add(UUID.fromString(payloadIds.getString(i)))) return null;
            if (!state.visited.containsAll(state.payloadVictims) || state.redirected && (state.path != ProjectilePath.NONE
                    || state.payload != null || state.ricochets != 0 || state.skillPenetrations != 0 || !state.chargeDischarged)
                    || state.target != null && state.path != ProjectilePath.HOMING && !state.redirected) return null;
            if (!Double.isFinite(state.remainingRange) || state.remainingRange < 0 || state.remainingRange > state.profile.range()
                    || !Double.isFinite(state.damageMultiplier) || state.damageMultiplier <= 0 || state.damageMultiplier > 1
                    || state.remainingTicks < 0 || state.remainingTicks > state.profile.lifetimeTicks()
                    || state.source == ProjectileSource.UNSUPPORTED || state.source == ProjectileSource.SECONDARY) return null;
            return state;
        } catch (IllegalArgumentException exception) { return null; }
    }

    /** Reads only immutable persisted launch values, never live configuration or a current loadout. */
    private ProjectileState(CompoundTag tag) {
        source = ProjectileSource.valueOf(tag.getString("Source")); path = ProjectilePath.valueOf(tag.getString("Path"));
        owner = tag.getUUID("Owner"); ownerLife = tag.getUUID("OwnerLife");
        dimension = ResourceLocation.parse(tag.getString("Dimension")); launchedAt = tag.getLong("LaunchedAt");
        profile = new ProjectileBalanceSettings.Profile(valid(tag.getDouble("Range"), 1, 512), valid(tag.getDouble("Speed"), 0.1, 16),
                (int) valid(tag.getInt("Lifetime"), 1, 2400), valid(tag.getDouble("AcquireRange"), 0, tag.getDouble("Range")),
                valid(tag.getDouble("Cone"), 0, 30), valid(tag.getDouble("Turn"), 0, 45));
        maximumSpeed = valid(tag.getDouble("MaximumSpeed"), 0.1, 16);
        ricochetRadius = valid(tag.getDouble("RicochetRadius"), 0, 32);
        ricochetFalloff = valid(tag.getDouble("RicochetFalloff"), 0, 1);
        piercingFalloff = valid(tag.getDouble("PiercingFalloff"), 0, 1);
        piercingShieldMultiplier = valid(tag.getDouble("PiercingShieldMultiplier"), 0, 1);
        maximumImpacts = (int) valid(tag.getInt("MaximumImpacts"), 1, 256);
        ricochets = (int) valid(tag.getInt("Ricochets"), 0, 16);
        nativePenetrations = (int) valid(tag.getInt("NativePenetrations"), 0, 127);
        skillPenetrations = (int) valid(tag.getInt("SkillPenetrations"), 0, 32);
    }
    private static double valid(double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) throw new IllegalArgumentException("Invalid projectile snapshot");
        return value;
    }
}
