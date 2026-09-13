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
 * Server -> client chunk of the final resolved item -> Essence table.
 *
 * The server resolves config overrides, tag mappings, priorities, removals and
 * one-for-one replacements. The client receives only display-ready item IDs and
 * final Essence amounts; it never reads the server config directory.
 *
 * Chunking avoids turning a large modpack's mapping table into one oversized
 * custom payload.
 */
public record ItemEssenceTooltipPayload(
        long mappingGeneration,
        int chunkIndex,
        int chunkCount,
        List<Entry> entries
) implements CustomPacketPayload {

    private static final int MAX_CHUNKS = 4096;
    private static final int MAX_ENTRIES_PER_CHUNK = 256;
    private static final int MAX_OUTPUTS_PER_ENTRY = 6;
    private static final int MAX_ID_LENGTH = 128;

    public static final Type<ItemEssenceTooltipPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "item_essence_tooltips_v2"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemEssenceTooltipPayload> CODEC =
            StreamCodec.of(
                    ItemEssenceTooltipPayload::write,
                    ItemEssenceTooltipPayload::read
            );

    public ItemEssenceTooltipPayload {
        Objects.requireNonNull(
                entries,
                "Item Essence tooltip entries cannot be null"
        );

        if (mappingGeneration < 0L) {
            throw new IllegalArgumentException(
                    "Mapping generation cannot be negative"
            );
        }

        if (chunkCount < 1
                || chunkCount > MAX_CHUNKS) {
            throw new IllegalArgumentException(
                    "Invalid Item Essence tooltip chunk count: "
                            + chunkCount
            );
        }

        if (chunkIndex < 0
                || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid Item Essence tooltip chunk index: "
                            + chunkIndex
            );
        }

        if (entries.size()
                > MAX_ENTRIES_PER_CHUNK) {
            throw new IllegalArgumentException(
                    "Too many Item Essence tooltip entries in one chunk: "
                            + entries.size()
            );
        }

        entries =
                List.copyOf(
                        entries
                );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(
            RegistryFriendlyByteBuf buffer,
            ItemEssenceTooltipPayload payload
    ) {
        buffer.writeLong(
                payload.mappingGeneration()
        );

        buffer.writeVarInt(
                payload.chunkIndex()
        );

        buffer.writeVarInt(
                payload.chunkCount()
        );

        buffer.writeVarInt(
                payload.entries()
                        .size()
        );

        for (Entry entry :
                payload.entries()) {

            buffer.writeUtf(
                    entry.itemId(),
                    MAX_ID_LENGTH
            );

            buffer.writeVarInt(
                    entry.outputs()
                            .size()
            );

            for (Output output :
                    entry.outputs()) {

                buffer.writeUtf(
                        output.essenceId(),
                        MAX_ID_LENGTH
                );

                buffer.writeLong(
                        output.microUnits()
                );
            }
        }
    }

    private static ItemEssenceTooltipPayload read(
            RegistryFriendlyByteBuf buffer
    ) {
        long generation =
                buffer.readLong();

        int chunkIndex =
                buffer.readVarInt();

        int chunkCount =
                readBoundedCount(
                        buffer,
                        MAX_CHUNKS,
                        "chunk"
                );

        if (chunkCount < 1
                || chunkIndex < 0
                || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid Item Essence tooltip chunk "
                            + chunkIndex
                            + " / "
                            + chunkCount
            );
        }

        int entryCount =
                readBoundedCount(
                        buffer,
                        MAX_ENTRIES_PER_CHUNK,
                        "entry"
                );

        List<Entry> entries =
                new ArrayList<>(
                        entryCount
                );

        for (int i = 0;
             i < entryCount;
             i++) {

            String itemId =
                    buffer.readUtf(
                            MAX_ID_LENGTH
                    );

            int outputCount =
                    readBoundedCount(
                            buffer,
                            MAX_OUTPUTS_PER_ENTRY,
                            "output"
                    );

            List<Output> outputs =
                    new ArrayList<>(
                            outputCount
                    );

            for (int j = 0;
                 j < outputCount;
                 j++) {

                outputs.add(
                        new Output(
                                buffer.readUtf(
                                        MAX_ID_LENGTH
                                ),
                                buffer.readLong()
                        )
                );
            }

            entries.add(
                    new Entry(
                            itemId,
                            outputs
                    )
            );
        }

        return new ItemEssenceTooltipPayload(
                generation,
                chunkIndex,
                chunkCount,
                entries
        );
    }

    private static int readBoundedCount(
            RegistryFriendlyByteBuf buffer,
            int maximum,
            String label
    ) {
        int count =
                buffer.readVarInt();

        if (count < 0
                || count > maximum) {
            throw new IllegalArgumentException(
                    "Invalid Item Essence tooltip "
                            + label
                            + " count: "
                            + count
            );
        }

        return count;
    }

    public record Entry(
            String itemId,
            List<Output> outputs
    ) {
        public Entry {
            Objects.requireNonNull(
                    itemId,
                    "Tooltip item ID cannot be null"
            );

            Objects.requireNonNull(
                    outputs,
                    "Tooltip outputs cannot be null"
            );

            if (outputs.size()
                    > MAX_OUTPUTS_PER_ENTRY) {
                throw new IllegalArgumentException(
                        "An item cannot contain more than "
                                + MAX_OUTPUTS_PER_ENTRY
                                + " core Essence outputs"
                );
            }

            outputs =
                    List.copyOf(
                            outputs
                    );
        }
    }

    public record Output(
            String essenceId,
            long microUnits
    ) {
        public Output {
            Objects.requireNonNull(
                    essenceId,
                    "Tooltip Essence ID cannot be null"
            );

            if (microUnits <= 0) {
                throw new IllegalArgumentException(
                        "Tooltip Essence amount must be positive"
                );
            }
        }
    }
}
