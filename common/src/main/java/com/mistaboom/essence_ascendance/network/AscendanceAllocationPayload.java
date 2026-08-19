package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One explicit client request containing the Nexus's complete staged changes.
 *
 * The server never trusts the proposed targets. The packet carries enough
 * staging context to distinguish harmless AVAILABLE-Essence sync changes from
 * conflicting progression changes before any mutation occurs.
 */
public record AscendanceAllocationPayload(
        int menuId,
        long basePlayerRevision,
        String baseTierId,
        String baseBalanceProfileId,
        List<Target> targets
) implements CustomPacketPayload {

    public static final int MAX_TARGETS = 256;
    private static final int MAX_STAT_ID_LENGTH = 128;

    public static final Type<AscendanceAllocationPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "ascendance_allocate"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, AscendanceAllocationPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.menuId());
                        buffer.writeLong(payload.basePlayerRevision());
                        buffer.writeUtf(
                                payload.baseTierId(),
                                MAX_STAT_ID_LENGTH
                        );
                        buffer.writeUtf(
                                payload.baseBalanceProfileId(),
                                MAX_STAT_ID_LENGTH
                        );
                        buffer.writeVarInt(payload.targets().size());

                        for (Target target : payload.targets()) {
                            buffer.writeUtf(
                                    target.statId(),
                                    MAX_STAT_ID_LENGTH
                            );
                            buffer.writeLong(
                                    target.baseInvestment()
                            );
                            buffer.writeLong(
                                    target.targetInvestment()
                            );
                        }
                    },
                    buffer -> {
                        int menuId = buffer.readVarInt();
                        long baseRevision = buffer.readLong();
                        String baseTierId =
                                buffer.readUtf(MAX_STAT_ID_LENGTH);
                        String baseBalanceProfileId =
                                buffer.readUtf(MAX_STAT_ID_LENGTH);
                        int targetCount = buffer.readVarInt();

                        if (targetCount < 0 || targetCount > MAX_TARGETS) {
                            throw new IllegalArgumentException(
                                    "Invalid Ascendance allocation target count: "
                                            + targetCount
                            );
                        }

                        java.util.ArrayList<Target> targets =
                                new java.util.ArrayList<>(targetCount);

                        for (int i = 0; i < targetCount; i++) {
                            targets.add(
                                    new Target(
                                            buffer.readUtf(MAX_STAT_ID_LENGTH),
                                            buffer.readLong(),
                                            buffer.readLong()
                                    )
                            );
                        }

                        return new AscendanceAllocationPayload(
                                menuId,
                                baseRevision,
                                baseTierId,
                                baseBalanceProfileId,
                                targets
                        );
                    }
            );

    public AscendanceAllocationPayload {
        if (baseTierId == null || baseTierId.isBlank()) {
            throw new IllegalArgumentException("Base tier ID cannot be blank");
        }
        if (baseBalanceProfileId == null || baseBalanceProfileId.isBlank()) {
            throw new IllegalArgumentException("Base balance profile ID cannot be blank");
        }
        targets = List.copyOf(targets);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Target(
            String statId,
            long baseInvestment,
            long targetInvestment
    ) {
    }
}
