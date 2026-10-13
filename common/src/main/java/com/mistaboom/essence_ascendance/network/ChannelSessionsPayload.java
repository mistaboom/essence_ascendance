package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Bounded HUD projection; complete sessions remain on the scheduler and in the paginated interface. */
public record ChannelSessionsPayload(int playerEntityId, ResourceLocation dimension, int total, int selection, List<Link> links) implements CustomPacketPayload {
    public static final int MAX_LINKS = 16;
    public record Link(long source, String reason, long allocated, long actual) {}
    public ChannelSessionsPayload { links = List.copyOf(links); if (links.size() > MAX_LINKS || total < links.size() || !com.mistaboom.essence_ascendance.crucible.CrucibleChannelSelection.valid(selection)) throw new IllegalArgumentException("Invalid HUD bounds"); }
    public static final Type<ChannelSessionsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "channel_sessions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChannelSessionsPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeVarInt(p.playerEntityId); b.writeResourceLocation(p.dimension); b.writeVarInt(p.total); b.writeVarInt(p.selection); b.writeVarInt(p.links.size());
        for (var l : p.links) { b.writeLong(l.source); b.writeUtf(l.reason, 48); b.writeVarLong(l.allocated); b.writeVarLong(l.actual); }
    }, b -> {
        int id = b.readVarInt(); var dimension = b.readResourceLocation(); int total = b.readVarInt(), selection = b.readVarInt(), count = b.readVarInt();
        if (count < 0 || count > MAX_LINKS) throw new IllegalArgumentException("Oversized session HUD");
        List<Link> links = new ArrayList<>(); for (int i = 0; i < count; i++) links.add(new Link(b.readLong(), b.readUtf(48), b.readVarLong(), b.readVarLong()));
        return new ChannelSessionsPayload(id, dimension, total, selection, links);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
