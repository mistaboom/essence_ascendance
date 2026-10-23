package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;
import com.mistaboom.essence_ascendance.visual.EntityPresentationState2612;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

/** NeoForge 26 tracking sync replaces foreign SynchedEntityData IDs. */
public final class NeoEntityPresentationSync2612 implements EntityPresentationBridge2612.Backend {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, EssenceAscendance.MOD_ID);
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
    private static final Supplier<AttachmentType<EntityPresentationState2612>> STATE = TYPES.register(
            "entity_presentation", () -> AttachmentType.builder(() -> EntityPresentationState2612.DEFAULT)
                    .sync(CODEC).build());

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        EntityPresentationBridge2612.install(new NeoEntityPresentationSync2612());
    }

    @Override public EntityPresentationState2612 get(Entity entity) { return entity.getData(STATE); }
    @Override public void set(Entity entity, EntityPresentationState2612 state) { entity.setData(STATE, state); }
}
