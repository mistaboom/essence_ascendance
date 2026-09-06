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
 * Server -> client semantic Ascendance equipment tooltip snapshot.
 *
 * The server owns the resolved values, but it does NOT pre-render English.
 * Each line carries a translation key plus compact semantic arguments so the
 * receiving client can render it in its own language. Arguments beginning with
 * '@' are semantic references interpreted by EquipmentTooltipClientState
 * (for example @stat:... and @equipment_tier:...).
 */
public record EquipmentTooltipPayload(
        List<Entry> entries
) implements CustomPacketPayload {

    private static final int MAX_ENTRIES = 96;
    private static final int MAX_LINES_PER_ENTRY = 64;
    private static final int MAX_ITEM_ID_LENGTH = 96;
    private static final int MAX_TRANSLATION_KEY_LENGTH = 160;
    private static final int MAX_ARGUMENTS_PER_LINE = 8;
    private static final int MAX_ARGUMENT_LENGTH = 192;

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
            buffer.writeDouble(entry.guardedMovementPercent());
            buffer.writeVarInt(entry.lines().size());

            for (Line line : entry.lines()) {
                buffer.writeByte(line.group().ordinal());
                buffer.writeByte(line.tone().ordinal());
                buffer.writeUtf(line.translationKey(), MAX_TRANSLATION_KEY_LENGTH);
                buffer.writeVarInt(line.arguments().size());
                for (String argument : line.arguments()) {
                    buffer.writeUtf(argument, MAX_ARGUMENT_LENGTH);
                }
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
            double guardedMovementPercent = buffer.readDouble();

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

                String translationKey = buffer.readUtf(MAX_TRANSLATION_KEY_LENGTH);
                int argumentCount = buffer.readVarInt();
                if (argumentCount < 0 || argumentCount > MAX_ARGUMENTS_PER_LINE) {
                    throw new IllegalArgumentException(
                            "Invalid Ascendance tooltip argument count: " + argumentCount
                    );
                }
                List<String> arguments = new ArrayList<>(argumentCount);
                for (int argument = 0; argument < argumentCount; argument++) {
                    arguments.add(buffer.readUtf(MAX_ARGUMENT_LENGTH));
                }

                lines.add(
                        new Line(
                                Group.values()[groupIndex],
                                Tone.values()[toneIndex],
                                translationKey,
                                arguments
                        )
                );
            }

            entries.add(new Entry(itemId, lines, guardedMovementPercent));
        }

        return new EquipmentTooltipPayload(entries);
    }

    public record Entry(
            String itemId,
            List<Line> lines,
            double guardedMovementPercent
    ) {
        public Entry(String itemId, List<Line> lines) {
            this(itemId, lines, 0.0D);
        }

        public Entry {
            if (!Double.isFinite(guardedMovementPercent) || guardedMovementPercent < 0.0D
                    || guardedMovementPercent > 100.0D) {
                throw new IllegalArgumentException("Invalid synchronized shield slowdown reduction");
            }
            Objects.requireNonNull(itemId, "Tooltip item ID cannot be null");
            Objects.requireNonNull(lines, "Tooltip lines cannot be null");
            lines = List.copyOf(lines);
        }
    }

    public record Line(
            Group group,
            Tone tone,
            String translationKey,
            List<String> arguments
    ) {
        public Line {
            Objects.requireNonNull(group, "Tooltip group cannot be null");
            Objects.requireNonNull(tone, "Tooltip tone cannot be null");
            Objects.requireNonNull(translationKey, "Tooltip translation key cannot be null");
            Objects.requireNonNull(arguments, "Tooltip arguments cannot be null");
            arguments = List.copyOf(arguments);
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
