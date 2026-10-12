package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.network.PreciseMobMotionPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;

/** Transient server tracking only. Never marks a mob persistent or changes ordinary despawn rules. */
public final class PreciseMobMotion {
    private static final Map<Mob, Boolean> LAUNCHED = new WeakHashMap<>();
    private PreciseMobMotion() { }
    public static boolean exceedsNativePacket(Vec3 velocity) {
        return Math.abs(velocity.x) > 3.9 || Math.abs(velocity.y) > 3.9 || Math.abs(velocity.z) > 3.9;
    }
    public static void launched(Mob mob) {
        if (exceedsNativePacket(mob.getDeltaMovement())) LAUNCHED.put(mob, true);
    }
    public static PreciseMobMotionPayload update(Entity entity) {
        if (!(entity instanceof Mob mob) || !LAUNCHED.containsKey(mob)) return null;
        if (mob.isRemoved()) { LAUNCHED.remove(mob); return null; }
        var velocity = mob.getDeltaMovement();
        if (!Double.isFinite(velocity.lengthSqr()) || velocity.length() > 1_000_000) {
            LAUNCHED.remove(mob); return null;
        }
        if (!exceedsNativePacket(velocity)) LAUNCHED.remove(mob);
        return new PreciseMobMotionPayload(mob.level().dimension().location(), mob.getId(), mob.getUUID(), velocity);
    }
    /** Sent after native tracker updates, including its clamped hurtMarked packet. */
    public static void synchronize(Entity entity, Consumer<Packet<?>> broadcast) {
        var payload = update(entity);
        if (payload == null) return;
        NetworkManager.toPackets(NetworkManager.Side.S2C, payload, entity.registryAccess()).forEach(broadcast);
    }
}
