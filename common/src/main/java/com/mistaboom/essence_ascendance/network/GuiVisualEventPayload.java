package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.visual.transientfx.GuiVisualEvent;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A server-confirmed GUI recipe invocation; anchors resolve against the current screen. */
public record GuiVisualEventPayload(GuiVisualEvent event, int containerId) implements CustomPacketPayload {
    public GuiVisualEventPayload(GuiVisualEvent event) { this(event, -1); }
    public static final Type<GuiVisualEventPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "gui_visual_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuiVisualEventPayload> CODEC =
            StreamCodec.of(GuiVisualEventPayload::write, GuiVisualEventPayload::read);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buffer, GuiVisualEventPayload payload) {
        GuiVisualEvent event = payload.event();
        buffer.writeResourceLocation(event.recipeId());
        buffer.writeEnum(event.anchor());
        buffer.writeVarInt(event.slotIndex() + 1);
        buffer.writeInt(event.offsetX());
        buffer.writeInt(event.offsetY());
        buffer.writeFloat(event.scale());
        buffer.writeFloat(event.intensity());
        buffer.writeEnum(event.presentation());
        buffer.writeEnum(event.color());
        buffer.writeVarInt(event.lifetimeTicks());
        buffer.writeLong(event.seed());
        buffer.writeFloat(event.parameterA());
        buffer.writeFloat(event.parameterB());
        buffer.writeVarInt(payload.containerId());
    }

    private static GuiVisualEventPayload read(RegistryFriendlyByteBuf buffer) {
        return new GuiVisualEventPayload(new GuiVisualEvent(
                buffer.readResourceLocation(), buffer.readEnum(GuiVisualEvent.Anchor.class),
                buffer.readVarInt() - 1, buffer.readInt(), buffer.readInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readEnum(VisualIntensity.class),
                buffer.readEnum(SemanticVisualColor.class), buffer.readVarInt(), buffer.readLong(),
                buffer.readFloat(), buffer.readFloat()), buffer.readVarInt());
    }
}
