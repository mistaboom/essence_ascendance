package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.gathering.GatheringSurveyService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Bounded S2C presentation snapshot for Ore Sight and Treasure Sense. */
public record GatheringSurveyPayload(GatheringSurveyService.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<GatheringSurveyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "gathering_survey"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GatheringSurveyPayload> CODEC =
            StreamCodec.of(GatheringSurveyPayload::write, GatheringSurveyPayload::read);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, GatheringSurveyPayload payload) {
        var snapshot = payload.snapshot();
        buf.writeResourceLocation(snapshot.dimension());
        buf.writeEnum(snapshot.mode());
        if (snapshot.targets().size() > GatheringSurveyService.MAX_TARGETS) throw new IllegalArgumentException("Gathering survey target count exceeds protocol bound");
        buf.writeVarInt(snapshot.targets().size());
        for (var target : snapshot.targets()) {
            buf.writeLong(target.pos().asLong());
            buf.writeBoolean(target.emphasized());
        }
    }

    private static GatheringSurveyPayload read(RegistryFriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        GatheringSurveyService.Mode mode = buf.readEnum(GatheringSurveyService.Mode.class);
        int count = buf.readVarInt();
        if (count < 0 || count > GatheringSurveyService.MAX_TARGETS) throw new IllegalArgumentException("Gathering survey target count out of bounds");
        List<GatheringSurveyService.Target> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(new GatheringSurveyService.Target(BlockPos.of(buf.readLong()), buf.readBoolean()));
        }
        return new GatheringSurveyPayload(new GatheringSurveyService.Snapshot(dimension, mode, targets));
    }
}
