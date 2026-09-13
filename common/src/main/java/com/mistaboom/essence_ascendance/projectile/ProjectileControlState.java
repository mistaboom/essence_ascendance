package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Independent of offensive launch state: native mob/dispenser arrows need the same persisted theft ledger. */
public final class ProjectileControlState {
    public static final int MAX_REDIRECTS = 1;
    public final Set<UUID> owners = new LinkedHashSet<>();
    public int remainingRedirects = -1;
    public double dragFactor = 1;
    public long lastFlightTick = Long.MIN_VALUE;
    /** Transient velocity after the pre-movement field response; never persisted across a reload without leases. */
    public Vec3 physicsInput;

    public boolean redirect(UUID from, UUID defender, int configuredBudget) {
        if (from != null && from.equals(defender) || owners.contains(defender)) return false;
        if (remainingRedirects < 0) remainingRedirects = Math.clamp(configuredBudget, 0, MAX_REDIRECTS);
        if (remainingRedirects <= 0 || owners.size() >= MAX_REDIRECTS + 1) return false;
        if (from != null) owners.add(from);
        owners.add(defender); remainingRedirects--;
        return true;
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Redirects", remainingRedirects); tag.putDouble("DragFactor", dragFactor);
        var ids = new ListTag(); owners.forEach(id -> ids.add(StringTag.valueOf(id.toString()))); tag.put("Owners", ids);
        return tag;
    }
    public static ProjectileControlState load(CompoundTag tag) {
        try {
            var state = new ProjectileControlState();
            state.remainingRedirects = tag.getInt("Redirects"); state.dragFactor = tag.getDouble("DragFactor");
            var ids = tag.getList("Owners", Tag.TAG_STRING);
            if (state.remainingRedirects < -1 || state.remainingRedirects > 0
                    || !Double.isFinite(state.dragFactor) || state.dragFactor < 0.01 || state.dragFactor > 1
                    || ids.size() > MAX_REDIRECTS + 1) return null;
            for (int i = 0; i < ids.size(); i++) if (!state.owners.add(UUID.fromString(ids.getString(i)))) return null;
            if (state.remainingRedirects == -1 && !state.owners.isEmpty()) return null;
            return state;
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
