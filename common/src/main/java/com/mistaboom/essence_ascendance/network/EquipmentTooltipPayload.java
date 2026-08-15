package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Server -> client snapshot containing already-resolved, display-ready
 * Ascendance equipment information for the local player.
 *
 * Lines are grouped so the client can place real vanilla enchantments between
 * Current Stats and Essence Abilities without mixing the two systems.
 */
public record EquipmentTooltipPayload(
        List<Entry> entries
) implements CustomPacketPayload {

    private static final int MAX_ENTRIES = 32;
    private static final int MAX_LINES_PER_ENTRY = 64;
    private static final int MAX_ITEM_ID_LENGTH = 96;
    private static final int MAX_LINE_LENGTH = 192;

    public static final Type<EquipmentTooltipPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "equipment_tooltips"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentTooltipPayload> CODEC =
            StreamCodec.of(
                    EquipmentTooltipPayload::write,
                    EquipmentTooltipPayload::read
            );

    public EquipmentTooltipPayload {
        Objects.requireNonNull(entries, "Tooltip entries cannot be null");
        entries = List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(
            RegistryFriendlyByteBuf buffer,
            EquipmentTooltipPayload payload
    ) {
        buffer.writeVarInt(payload.entries.size());

        for (Entry entry : payload.entries) {
            buffer.writeUtf(entry.itemId(), MAX_ITEM_ID_LENGTH);
            buffer.writeVarInt(entry.lines().size());

            for (Line line : entry.lines()) {
                buffer.writeByte(line.group().ordinal());
                buffer.writeByte(line.tone().ordinal());
                buffer.writeUtf(line.text(), MAX_LINE_LENGTH);
            }
        }
    }

    private static EquipmentTooltipPayload read(
            RegistryFriendlyByteBuf buffer
    ) {
        int entryCount = buffer.readVarInt();
        if (entryCount < 0 || entryCount > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "Invalid Ascendance tooltip entry count: " + entryCount
            );
        }

        List<Entry> entries = new ArrayList<>(entryCount);

        for (int i = 0; i < entryCount; i++) {
            String itemId = buffer.readUtf(MAX_ITEM_ID_LENGTH);

            int lineCount = buffer.readVarInt();
            if (lineCount < 0 || lineCount > MAX_LINES_PER_ENTRY) {
                throw new IllegalArgumentException(
                        "Invalid Ascendance tooltip line count: " + lineCount
                );
            }

            List<Line> lines = new ArrayList<>(lineCount);

            for (int j = 0; j < lineCount; j++) {
                int groupIndex = buffer.readUnsignedByte();
                int toneIndex = buffer.readUnsignedByte();

                if (groupIndex < 0 || groupIndex >= Group.values().length) {
                    throw new IllegalArgumentException(
                            "Invalid Ascendance tooltip group: " + groupIndex
                    );
                }

                if (toneIndex < 0 || toneIndex >= Tone.values().length) {
                    throw new IllegalArgumentException(
                            "Invalid Ascendance tooltip tone: " + toneIndex
                    );
                }

                lines.add(
                        new Line(
                                Group.values()[groupIndex],
                                Tone.values()[toneIndex],
                                buffer.readUtf(MAX_LINE_LENGTH)
                        )
                );
            }

            entries.add(new Entry(itemId, lines));
        }

        return new EquipmentTooltipPayload(entries);
    }

    public record Entry(
            String itemId,
            List<Line> lines
    ) {
        public Entry {
            Objects.requireNonNull(itemId, "Tooltip item ID cannot be null");
            Objects.requireNonNull(lines, "Tooltip lines cannot be null");
            lines = List.copyOf(lines);
        }
    }

    public record Line(
            Group group,
            Tone tone,
            String text
    ) {
        public Line {
            Objects.requireNonNull(group, "Tooltip group cannot be null");
            Objects.requireNonNull(tone, "Tooltip tone cannot be null");
            Objects.requireNonNull(text, "Tooltip text cannot be null");
        }
    }

    public enum Group {
        IDENTITY,
        STATS,
        ESSENCE
    }

    public enum Tone {
        TIER,
        PRIMARY,
        ABILITY,
        SET_BONUS,
        MUTED
    }
}
