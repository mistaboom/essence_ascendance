package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.PreciseMobMotionPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Receives authoritative motion, with dimension and UUID checks to reject stale reused entity IDs. */
public final class PreciseMobMotionClient {
    private PreciseMobMotionClient() { }
    public static void apply(Entity entity, PreciseMobMotionPayload payload) {
        if (entity instanceof Mob && !entity.isRemoved() && entity.getId() == payload.entityId()
                && entity.getUUID().equals(payload.entityUuid())
                && entity.level().dimension().location().equals(payload.dimension()))
            entity.lerpMotion(payload.velocity().x, payload.velocity().y, payload.velocity().z);
    }
    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PreciseMobMotionPayload.TYPE, PreciseMobMotionPayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> {
                    var level = Minecraft.getInstance().level;
                    if (level != null && level.dimension().location().equals(payload.dimension())) apply(level.getEntity(payload.entityId()), payload);
                }));
    }
}
