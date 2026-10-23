package com.mistaboom.essence_ascendance.fabric;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;
import com.mistaboom.essence_ascendance.visual.EntityPresentationState2612;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Transient, tracking-visible 26.1.2 presentation state for Fabric entities. */
public final class FabricEntityPresentationSync2612 implements EntityPresentationBridge2612.Backend {
    private static final StreamCodec<RegistryFriendlyByteBuf, EntityPresentationState2612> CODEC =
            StreamCodec.of((buf, value) -> {
                buf.writeVarInt(value.flightFlags());
                buf.writeLong(value.boostTick());
                buf.writeFloat(value.arrowScale());
                buf.writeVarInt(value.rodHand());
                buf.writeVarInt(value.rodTier());
                buf.writeVarInt(value.attachment());
                buf.writeDouble(value.anchor().x);
                buf.writeDouble(value.anchor().y);
                buf.writeDouble(value.anchor().z);
            }, buf -> new EntityPresentationState2612(buf.readVarInt(), buf.readLong(),
                    buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

    private static final AttachmentType<EntityPresentationState2612> STATE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "entity_presentation"),
            builder -> builder.syncWith(CODEC, AttachmentSyncPredicate.all()));

    private FabricEntityPresentationSync2612() { }

    public static void register() {
        EntityPresentationBridge2612.install(new FabricEntityPresentationSync2612());
    }

    @Override public EntityPresentationState2612 get(Entity entity) {
        return ((AttachmentTarget) entity).getAttachedOrElse(STATE, EntityPresentationState2612.DEFAULT);
    }

    @Override public void set(Entity entity, EntityPresentationState2612 state) {
        ((AttachmentTarget) entity).setAttached(STATE, state);
    }
}
