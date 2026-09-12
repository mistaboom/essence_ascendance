package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Future payload composition point. No Explosive/Rooting implementation is registered. */
public final class ProjectileImpactEffects {
    public interface Payload {
        ResourceLocation skillId();
        CompoundTag snapshot(ServerPlayer owner);
        void impact(Impact impact, CompoundTag launchData);
    }
    public record Impact(Projectile projectile, ServerPlayer owner, HitResult hit,
                         LivingEntity victim, double confirmedDamage, boolean continuing) { }
    public record Snapshot(ResourceLocation id, CompoundTag data) {
        public Snapshot { data = data.copy(); }
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putString("Id", id.toString()); tag.put("Data", data.copy()); return tag;
        }
        public static Snapshot load(CompoundTag tag) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Id"));
            return id != null && PAYLOADS.containsKey(id) ? new Snapshot(id, tag.getCompound("Data")) : null;
        }
    }
    private static final Map<ResourceLocation, Payload> PAYLOADS = new LinkedHashMap<>();
    private ProjectileImpactEffects() { }
    public static void register(Payload payload) {
        if (PAYLOADS.putIfAbsent(payload.skillId(), payload) != null) throw new IllegalArgumentException("Duplicate payload");
    }
    public static Snapshot snapshot(ServerPlayer owner, Set<ResourceLocation> effective) {
        var selected = PAYLOADS.values().stream().filter(p -> effective.contains(p.skillId())).toList();
        return selected.size() == 1 ? new Snapshot(selected.getFirst().skillId(), selected.getFirst().snapshot(owner)) : null;
    }
    public static void impact(ProjectileState state, Impact impact) {
        if (state.payload == null) return;
        Payload payload = PAYLOADS.get(state.payload.id());
        if (payload != null) EquipmentDamageService.withSecondarySkillDamage(
                () -> payload.impact(impact, state.payload.data().copy()));
    }
}
