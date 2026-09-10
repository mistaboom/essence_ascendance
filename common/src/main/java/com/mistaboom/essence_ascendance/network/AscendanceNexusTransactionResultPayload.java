package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Explicit acknowledgement for one complete Nexus proposal. */
public record AscendanceNexusTransactionResultPayload(
        int menuId,
        long requestId,
        long nexusRevision,
        Status status,
        boolean ascended
) implements CustomPacketPayload {

    public static final Type<AscendanceNexusTransactionResultPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "ascendance_nexus_transaction_result"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, AscendanceNexusTransactionResultPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.menuId());
                        buffer.writeLong(payload.requestId());
                        buffer.writeLong(payload.nexusRevision());
                        buffer.writeUtf(payload.status().serializedName(), 64);
                        buffer.writeBoolean(payload.ascended());
                    },
                    buffer -> new AscendanceNexusTransactionResultPayload(
                            buffer.readVarInt(),
                            buffer.readLong(),
                            buffer.readLong(),
                            Status.fromSerializedName(buffer.readUtf(64)),
                            buffer.readBoolean()
                    )
            );

    public AscendanceNexusTransactionResultPayload {
        if (requestId < 0L) {
            throw new IllegalArgumentException(
                    "Nexus transaction result request ID cannot be negative"
            );
        }
        if (nexusRevision < 0L) {
            throw new IllegalArgumentException(
                    "Nexus transaction result revision cannot be negative"
            );
        }
        if (status == null) {
            throw new IllegalArgumentException(
                    "Nexus transaction result status cannot be null"
            );
        }
        if (ascended && status != Status.SUCCESS) {
            throw new IllegalArgumentException(
                    "Only a successful Nexus transaction may report Ascension"
            );
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean accepted() {
        return status == Status.SUCCESS;
    }

    /**
     * Stable result identities double as localization suffixes. The server and
     * client should render {@code gui.essence_ascendance.nexus.transaction.*}.
     */
    public enum Status {
        SUCCESS,
        INVALID_MENU,
        RATE_LIMITED,
        STALE,
        INVALID_CONTEXT,
        INVALID_PROPOSAL,
        INCOMPLETE_BONUS_STATE,
        UNKNOWN_STAT,
        CAP_EXCEEDED,
        INSUFFICIENT_ESSENCE,
        UNKNOWN_SKILL,
        SKILL_ALREADY_OWNED,
        SKILL_TIER_REQUIRED,
        SKILL_PREREQUISITE_REQUIRED,
        SKILL_REQUIREMENT_INCOMPLETE,
        INVALID_LOADOUT,
        ASCENSION_NOT_READY,
        MAX_TIER,
        CONFIGURATION_ERROR,
        TRANSACTION_FAILED;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "gui."
                    + EssenceAscendance.MOD_ID
                    + ".nexus.transaction."
                    + serializedName();
        }

        public static Status fromSerializedName(String value) {
            for (Status status : values()) {
                if (status.serializedName().equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException(
                    "Unknown Ascendance Nexus transaction status: " + value
            );
        }
    }
}
