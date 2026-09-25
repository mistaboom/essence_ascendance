package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.Set;

/** Selected/previously selected primary hosts, never the block registry or placed-block positions. */
public record LatentOreCatalogPayload(Set<ResourceLocation> hosts) implements CustomPacketPayload {
    private static final int MAX_HOSTS = 4096;
    public static final Type<LatentOreCatalogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "latent_ore_hosts"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LatentOreCatalogPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.hosts.size());
                payload.hosts.stream().sorted().forEach(buffer::writeResourceLocation);
            }, buffer -> {
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_HOSTS) throw new IllegalArgumentException("Invalid Latent Ore host catalog size");
                Set<ResourceLocation> hosts = new HashSet<>();
                for (int i = 0; i < size; i++) hosts.add(buffer.readResourceLocation());
                return new LatentOreCatalogPayload(hosts);
            });
    public LatentOreCatalogPayload {
        hosts = Set.copyOf(hosts);
        if (hosts.size() > MAX_HOSTS) throw new IllegalArgumentException("Latent Ore host catalog exceeds transport limit");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
