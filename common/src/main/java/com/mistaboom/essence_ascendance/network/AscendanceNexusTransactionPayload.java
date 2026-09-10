package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One complete, client-staged Nexus proposal.
 *
 * <p>The client supplies final Bonus-investment targets and identities for
 * requested purchases/loadout selections. It never supplies authoritative
 * costs, balances, eligibility, effective states, or Ascension results. The
 * server derives and validates all of those values before one atomic commit of
 * persistent state.</p>
 */
public record AscendanceNexusTransactionPayload(
        int menuId,
        long requestId,
        long baseNexusRevision,
        String baseTierId,
        String baseBalanceProfileId,
        List<BonusTarget> bonusTargets,
        List<String> skillPurchases,
        List<LoadoutSelection> loadoutSelections,
        boolean ascend
) implements CustomPacketPayload {

    public static final int MAX_BONUS_TARGETS = 256;
    public static final int MAX_SKILL_PURCHASES = 256;
    public static final int MAX_LOADOUT_SELECTIONS = 256;
    public static final int MAX_ID_LENGTH = 128;

    public static final Type<AscendanceNexusTransactionPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "ascendance_nexus_transaction"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, AscendanceNexusTransactionPayload> CODEC =
            StreamCodec.of(
                    AscendanceNexusTransactionPayload::write,
                    AscendanceNexusTransactionPayload::read
            );

    public AscendanceNexusTransactionPayload {
        if (requestId < 0L) {
            throw new IllegalArgumentException(
                    "Nexus request ID cannot be negative"
            );
        }
        if (baseNexusRevision < 0L) {
            throw new IllegalArgumentException(
                    "Base Nexus revision cannot be negative"
            );
        }

        requireId(baseTierId, "Base tier ID", false);
        requireId(baseBalanceProfileId, "Base balance-profile ID", false);
        Objects.requireNonNull(bonusTargets, "Bonus targets cannot be null");
        Objects.requireNonNull(skillPurchases, "Skill purchases cannot be null");
        Objects.requireNonNull(loadoutSelections, "Loadout selections cannot be null");

        if (bonusTargets.size() > MAX_BONUS_TARGETS
                || skillPurchases.size() > MAX_SKILL_PURCHASES
                || loadoutSelections.size() > MAX_LOADOUT_SELECTIONS) {
            throw new IllegalArgumentException(
                    "Ascendance Nexus proposal exceeds its bounded entry count"
            );
        }

        bonusTargets = List.copyOf(bonusTargets);
        skillPurchases = List.copyOf(skillPurchases);
        loadoutSelections = List.copyOf(loadoutSelections);

        for (String skillId : skillPurchases) {
            requireId(skillId, "Skill ID", false);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(
            RegistryFriendlyByteBuf buffer,
            AscendanceNexusTransactionPayload payload
    ) {
        buffer.writeVarInt(payload.menuId());
        buffer.writeLong(payload.requestId());
        buffer.writeLong(payload.baseNexusRevision());
        buffer.writeUtf(payload.baseTierId(), MAX_ID_LENGTH);
        buffer.writeUtf(payload.baseBalanceProfileId(), MAX_ID_LENGTH);

        buffer.writeVarInt(payload.bonusTargets().size());
        for (BonusTarget target : payload.bonusTargets()) {
            buffer.writeUtf(target.statId(), MAX_ID_LENGTH);
            buffer.writeLong(target.targetInvestment());
        }

        buffer.writeVarInt(payload.skillPurchases().size());
        for (String skillId : payload.skillPurchases()) {
            buffer.writeUtf(skillId, MAX_ID_LENGTH);
        }

        buffer.writeVarInt(payload.loadoutSelections().size());
        for (LoadoutSelection selection : payload.loadoutSelections()) {
            buffer.writeUtf(selection.groupId(), MAX_ID_LENGTH);
            buffer.writeUtf(selection.selectedSkillId(), MAX_ID_LENGTH);
        }

        buffer.writeBoolean(payload.ascend());
    }

    private static AscendanceNexusTransactionPayload read(
            RegistryFriendlyByteBuf buffer
    ) {
        int menuId = buffer.readVarInt();
        long requestId = buffer.readLong();
        long baseRevision = buffer.readLong();
        String baseTierId = buffer.readUtf(MAX_ID_LENGTH);
        String baseProfileId = buffer.readUtf(MAX_ID_LENGTH);

        int bonusCount = readBoundedCount(
                buffer,
                MAX_BONUS_TARGETS,
                "Bonus target"
        );
        List<BonusTarget> bonusTargets = new ArrayList<>(bonusCount);
        for (int i = 0; i < bonusCount; i++) {
            bonusTargets.add(
                    new BonusTarget(
                            buffer.readUtf(MAX_ID_LENGTH),
                            buffer.readLong()
                    )
            );
        }

        int purchaseCount = readBoundedCount(
                buffer,
                MAX_SKILL_PURCHASES,
                "skill purchase"
        );
        List<String> purchases = new ArrayList<>(purchaseCount);
        for (int i = 0; i < purchaseCount; i++) {
            purchases.add(buffer.readUtf(MAX_ID_LENGTH));
        }

        int selectionCount = readBoundedCount(
                buffer,
                MAX_LOADOUT_SELECTIONS,
                "loadout selection"
        );
        List<LoadoutSelection> selections = new ArrayList<>(selectionCount);
        for (int i = 0; i < selectionCount; i++) {
            selections.add(
                    new LoadoutSelection(
                            buffer.readUtf(MAX_ID_LENGTH),
                            buffer.readUtf(MAX_ID_LENGTH)
                    )
            );
        }

        return new AscendanceNexusTransactionPayload(
                menuId,
                requestId,
                baseRevision,
                baseTierId,
                baseProfileId,
                bonusTargets,
                purchases,
                selections,
                buffer.readBoolean()
        );
    }

    private static int readBoundedCount(
            RegistryFriendlyByteBuf buffer,
            int maximum,
            String label
    ) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Invalid " + label + " count in Ascendance Nexus proposal: " + count
            );
        }
        return count;
    }

    private static String requireId(
            String value,
            String label,
            boolean allowBlank
    ) {
        Objects.requireNonNull(value, label + " cannot be null");
        if ((!allowBlank && value.isBlank()) || value.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    label + " is blank or exceeds " + MAX_ID_LENGTH + " characters"
            );
        }
        return value;
    }

    public record BonusTarget(
            String statId,
            long targetInvestment
    ) {
        public BonusTarget {
            requireId(statId, "Stat ID", false);
            if (targetInvestment < 0L) {
                throw new IllegalArgumentException(
                        "Bonus investment target cannot be negative"
                );
            }
        }
    }

    /** A blank selected-skill ID explicitly clears this group's selection. */
    public record LoadoutSelection(
            String groupId,
            String selectedSkillId
    ) {
        public LoadoutSelection {
            requireId(groupId, "Loadout group ID", false);
            requireId(selectedSkillId, "Selected skill ID", true);
        }
    }
}
