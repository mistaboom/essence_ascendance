package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Meter;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.MeterKind;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Bounded, skill-agnostic S2C presentation protocol. Adding a card does not change this codec. */
public record SkillEffectHudPayload(SkillEffectHudSnapshot snapshot) implements CustomPacketPayload {
    private static final int MAX_PAYLOAD_BYTES = 900_000;
    public static final Type<SkillEffectHudPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "skill_effect_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillEffectHudPayload> CODEC =
            StreamCodec.of(SkillEffectHudPayload::write, SkillEffectHudPayload::read);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, SkillEffectHudPayload payload) {
        int start = buf.writerIndex();
        SkillEffectHudSnapshot snapshot = payload.snapshot();
        buf.writeLong(snapshot.serverGameTime());
        buf.writeVarInt(snapshot.playerEntityId());
        buf.writeResourceLocation(snapshot.dimension());
        buf.writeDouble(snapshot.primaryMeleeBonusReach());
        buf.writeLong(snapshot.primaryMeleeReachExpiresAt());
        buf.writeVarInt(snapshot.entries().size());
        for (SkillEffectHudEntry entry : snapshot.entries()) {
            buf.writeResourceLocation(entry.id());
            buf.writeResourceLocation(entry.sourceSkill());
            buf.writeBoolean(entry.active());
            buf.writeBoolean(entry.retainAfterActive());
            buf.writeInt(entry.accent());
            writeText(buf, entry.title());
            writeText(buf, entry.badge());
            buf.writeVarInt(entry.lines().size());
            for (Text line : entry.lines()) writeText(buf, line);
            buf.writeEnum(entry.meter().kind());
            writeText(buf, entry.meter().label());
            buf.writeLong(entry.meter().expiresAt());
            buf.writeDouble(entry.meter().fraction());
        }
        if (buf.writerIndex() - start > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("HUD payload exceeds the total byte budget");
        }
    }

    private static SkillEffectHudPayload read(RegistryFriendlyByteBuf buf) {
        if (buf.readableBytes() > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("HUD payload exceeds the total byte budget");
        }
        long now = buf.readLong();
        int entityId = buf.readVarInt();
        ResourceLocation dimension = buf.readResourceLocation();
        double reach = buf.readDouble();
        long reachExpiry = buf.readLong();
        int count = count(buf, SkillEffectHudSnapshot.MAX_ENTRIES);
        List<SkillEffectHudEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            ResourceLocation source = buf.readResourceLocation();
            boolean active = buf.readBoolean();
            boolean retainAfterActive = buf.readBoolean();
            int accent = buf.readInt();
            Text title = readText(buf);
            Text badge = readText(buf);
            int lineCount = count(buf, SkillEffectHudEntry.MAX_LINES);
            List<Text> lines = new ArrayList<>(lineCount);
            for (int line = 0; line < lineCount; line++) lines.add(readText(buf));
            Meter meter = new Meter(buf.readEnum(MeterKind.class), readText(buf), buf.readLong(), buf.readDouble());
            entries.add(new SkillEffectHudEntry(id, source, active, accent, title, badge, lines, meter, retainAfterActive));
        }
        return new SkillEffectHudPayload(new SkillEffectHudSnapshot(now, entityId, dimension, entries, reach, reachExpiry));
    }

    private static void writeText(RegistryFriendlyByteBuf buf, Text text) {
        buf.writeUtf(text.translationKey(), Text.MAX_LENGTH);
        buf.writeUtf(text.literal(), Text.MAX_LENGTH);
        buf.writeVarInt(text.arguments().size());
        for (String argument : text.arguments()) buf.writeUtf(argument, Text.MAX_LENGTH);
    }

    private static Text readText(RegistryFriendlyByteBuf buf) {
        String key = buf.readUtf(Text.MAX_LENGTH);
        String literal = buf.readUtf(Text.MAX_LENGTH);
        int count = count(buf, Text.MAX_ARGUMENTS);
        List<String> arguments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) arguments.add(buf.readUtf(Text.MAX_LENGTH));
        return new Text(key, literal, arguments);
    }

    private static int count(RegistryFriendlyByteBuf buf, int maximum) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("HUD collection size out of bounds");
        return count;
    }
}
