package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Complete server-authoritative progression snapshot for the local player.
 *
 * This is intentionally independent of any screen implementation. Tooltips,
 * the future Ascendance UI, JEI integration, and other client presentation can
 * all consume the same client-side cache without reading server-only SavedData.
 */
public record PlayerEssenceSyncPayload(
        int schemaVersion,
        long playerRevision,
        String tierId,
        String balanceProfileId,
        List<EssenceBalance> essenceBalances,
        List<StatState> stats,
        List<String> completedMilestones,
        List<MilestoneState> skillMilestones,
        List<OwnedSkillState> ownedSkills,
        List<LoadoutSelection> loadoutSelections,
        AttunementSnapshot attunement,
        ProgressState progress
) implements CustomPacketPayload {

    public static final int CURRENT_SCHEMA_VERSION = 10;

    static final int MAX_ID_LENGTH = 128;
    private static final int MAX_ESSENCES = 128;
    private static final int MAX_STATS = 256;
    static final int MAX_MILESTONES = 2048;
    static final int MAX_OWNED_SKILLS = 4096;
    static final int MAX_LOADOUT_SELECTIONS = 2048;
    public static final int MAX_WORLD_REQUIREMENT_LINES = 256;
    static final int MAX_REQUIREMENT_LABEL_LENGTH = 192;

    public static final Type<PlayerEssenceSyncPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "player_essence_sync"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerEssenceSyncPayload> CODEC =
            StreamCodec.of(
                    PlayerEssenceSyncPayload::write,
                    PlayerEssenceSyncPayload::read
            );

    public PlayerEssenceSyncPayload {
        Objects.requireNonNull(tierId, "Tier ID cannot be null");
        Objects.requireNonNull(balanceProfileId, "Balance profile ID cannot be null");
        Objects.requireNonNull(essenceBalances, "Essence balances cannot be null");
        Objects.requireNonNull(stats, "Stat states cannot be null");
        Objects.requireNonNull(completedMilestones, "Completed milestones cannot be null");
        Objects.requireNonNull(skillMilestones, "Skill milestones cannot be null");
        Objects.requireNonNull(ownedSkills, "Owned skills cannot be null");
        Objects.requireNonNull(loadoutSelections, "Loadout selections cannot be null");
        Objects.requireNonNull(attunement, "Attunement cannot be null");
        Objects.requireNonNull(progress, "Progress state cannot be null");

        essenceBalances = List.copyOf(essenceBalances);
        stats = List.copyOf(stats);
        completedMilestones = List.copyOf(completedMilestones);
        skillMilestones = List.copyOf(skillMilestones);
        ownedSkills = List.copyOf(ownedSkills);
        loadoutSelections = List.copyOf(loadoutSelections);
    }

    /** The synchronized player revision is the persisted Nexus revision. */
    public long nexusRevision() {
        return playerRevision;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(
            RegistryFriendlyByteBuf buffer,
            PlayerEssenceSyncPayload payload
    ) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeLong(payload.playerRevision);
        buffer.writeUtf(payload.tierId, MAX_ID_LENGTH);
        buffer.writeUtf(payload.balanceProfileId, MAX_ID_LENGTH);

        buffer.writeVarInt(payload.essenceBalances.size());
        for (EssenceBalance balance : payload.essenceBalances) {
            buffer.writeUtf(balance.essenceId(), MAX_ID_LENGTH);
            buffer.writeLong(balance.available());
        }

        buffer.writeVarInt(payload.stats.size());
        for (StatState stat : payload.stats) {
            buffer.writeUtf(stat.statId(), MAX_ID_LENGTH);
            buffer.writeLong(stat.storedInvestment());
            buffer.writeLong(stat.effectiveInvestment());
            buffer.writeLong(stat.currentInvestmentCap());
            buffer.writeDouble(stat.progression());
            buffer.writeDouble(stat.currentTierMaximumBonus());
            buffer.writeDouble(stat.transcendentMaximumBonus());
            buffer.writeDouble(stat.scaledBonus());
            BonusTrackSnapshot.write(buffer, stat.track());
        }

        buffer.writeVarInt(payload.completedMilestones.size());
        for (String milestoneId : payload.completedMilestones) {
            buffer.writeUtf(milestoneId, MAX_ID_LENGTH);
        }

        buffer.writeVarInt(payload.skillMilestones.size());
        for (MilestoneState milestone : payload.skillMilestones) {
            buffer.writeUtf(milestone.milestoneId(), MAX_ID_LENGTH);
            buffer.writeUtf(milestone.displayName(), MAX_REQUIREMENT_LABEL_LENGTH);
            buffer.writeBoolean(milestone.resolvable());
            buffer.writeBoolean(milestone.complete());
        }

        buffer.writeVarInt(payload.ownedSkills.size());
        for (OwnedSkillState skill : payload.ownedSkills) {
            buffer.writeUtf(skill.skillId(), MAX_ID_LENGTH);
            buffer.writeUtf(skill.paidEssenceId(), MAX_ID_LENGTH);
            buffer.writeVarInt(skill.paidCosts().size());
            for (long cost : skill.paidCosts()) buffer.writeLong(cost);
            buffer.writeBoolean(skill.hudEnabled());
        }

        buffer.writeVarInt(payload.loadoutSelections.size());
        for (LoadoutSelection selection : payload.loadoutSelections) {
            buffer.writeUtf(selection.selectionId(), MAX_ID_LENGTH);
            buffer.writeUtf(selection.skillId(), MAX_ID_LENGTH);
        }

        AttunementSnapshotCodec.write(buffer, payload.attunement);

        buffer.writeByte(payload.progress.status().ordinal());
        buffer.writeUtf(payload.progress.nextTierId(), MAX_ID_LENGTH);
        buffer.writeLong(payload.progress.effectiveInvestment());
        buffer.writeLong(payload.progress.requiredInvestment());
        buffer.writeVarInt(payload.progress.developedStats());
        buffer.writeVarInt(payload.progress.requiredDevelopedStats());
        buffer.writeVarInt(payload.progress.representedCategories());
        buffer.writeVarInt(payload.progress.requiredRepresentedCategories());
        buffer.writeDouble(payload.progress.developedStatThreshold());
        buffer.writeVarInt(payload.progress.worldRequirements().size());
        for (WorldRequirementState requirement : payload.progress.worldRequirements()) {
            buffer.writeVarInt(requirement.depth());
            buffer.writeByte(requirement.kind().ordinal());
            buffer.writeUtf(requirement.label(), MAX_REQUIREMENT_LABEL_LENGTH);
            buffer.writeBoolean(requirement.resolvable());
            buffer.writeBoolean(requirement.complete());
        }
        buffer.writeBoolean(payload.progress.worldProgressComplete());
        buffer.writeBoolean(payload.progress.readyToAscend());
    }

    private static PlayerEssenceSyncPayload read(
            RegistryFriendlyByteBuf buffer
    ) {
        int schemaVersion =
                buffer.readVarInt();

        long playerRevision =
                buffer.readLong();

        String tierId =
                buffer.readUtf(MAX_ID_LENGTH);

        String balanceProfileId =
                buffer.readUtf(MAX_ID_LENGTH);

        int essenceCount =
                readBoundedCount(
                        buffer,
                        MAX_ESSENCES,
                        "Essence"
                );

        List<EssenceBalance> essenceBalances =
                new ArrayList<>(essenceCount);

        for (int i = 0; i < essenceCount; i++) {
            essenceBalances.add(
                    new EssenceBalance(
                            buffer.readUtf(MAX_ID_LENGTH),
                            buffer.readLong()
                    )
            );
        }

        int statCount =
                readBoundedCount(
                        buffer,
                        MAX_STATS,
                        "stat"
                );

        List<StatState> stats =
                new ArrayList<>(statCount);

        for (int i = 0; i < statCount; i++) {
            stats.add(
                    new StatState(
                            buffer.readUtf(MAX_ID_LENGTH),
                            buffer.readLong(),
                            buffer.readLong(),
                            buffer.readLong(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            BonusTrackSnapshot.read(buffer)
                    )
            );
        }

        int completedMilestoneCount =
                readBoundedCount(
                        buffer,
                        MAX_MILESTONES,
                        "completed milestone"
                );

        List<String> completedMilestones =
                new ArrayList<>(completedMilestoneCount);

        for (int i = 0; i < completedMilestoneCount; i++) {
            completedMilestones.add(buffer.readUtf(MAX_ID_LENGTH));
        }

        int skillMilestoneCount =
                readBoundedCount(
                        buffer,
                        MAX_MILESTONES,
                        "skill milestone"
                );

        List<MilestoneState> skillMilestones =
                new ArrayList<>(skillMilestoneCount);

        for (int i = 0; i < skillMilestoneCount; i++) {
            skillMilestones.add(
                    new MilestoneState(
                            buffer.readUtf(MAX_ID_LENGTH),
                            buffer.readUtf(MAX_REQUIREMENT_LABEL_LENGTH),
                            buffer.readBoolean(),
                            buffer.readBoolean()
                    )
            );
        }

        int ownedSkillCount =
                readBoundedCount(
                        buffer,
                        MAX_OWNED_SKILLS,
                        "owned skill"
                );

        List<OwnedSkillState> ownedSkills =
                new ArrayList<>(ownedSkillCount);

        for (int i = 0; i < ownedSkillCount; i++) {
            String skillId = buffer.readUtf(MAX_ID_LENGTH);
            String essenceId = buffer.readUtf(MAX_ID_LENGTH);
            int ranks = readBoundedCount(buffer, 64, "skill rank");
            List<Long> receipts = new ArrayList<>(ranks);
            for (int rank = 0; rank < ranks; rank++) receipts.add(buffer.readLong());
            ownedSkills.add(new OwnedSkillState(skillId, essenceId, receipts, buffer.readBoolean()));
        }

        int loadoutSelectionCount =
                readBoundedCount(
                        buffer,
                        MAX_LOADOUT_SELECTIONS,
                        "loadout selection"
                );

        List<LoadoutSelection> loadoutSelections =
                new ArrayList<>(loadoutSelectionCount);

        for (int i = 0; i < loadoutSelectionCount; i++) {
            loadoutSelections.add(
                    new LoadoutSelection(
                            buffer.readUtf(MAX_ID_LENGTH),
                            buffer.readUtf(MAX_ID_LENGTH)
                    )
            );
        }

        AttunementSnapshot attunement = AttunementSnapshotCodec.read(buffer);

        int statusOrdinal =
                buffer.readUnsignedByte();

        if (statusOrdinal < 0
                || statusOrdinal >= ProgressStatus.values().length) {
            throw new IllegalArgumentException(
                    "Invalid Ascendance progress status: "
                            + statusOrdinal
            );
        }

        String nextTierId =
                buffer.readUtf(MAX_ID_LENGTH);
        long effectiveInvestment = buffer.readLong();
        long requiredInvestment = buffer.readLong();
        int developedStats = buffer.readVarInt();
        int requiredDevelopedStats = buffer.readVarInt();
        int representedCategories = buffer.readVarInt();
        int requiredRepresentedCategories = buffer.readVarInt();
        double developedStatThreshold = buffer.readDouble();

        int worldRequirementCount =
                readBoundedCount(
                        buffer,
                        MAX_WORLD_REQUIREMENT_LINES,
                        "world requirement"
                );

        List<WorldRequirementState> worldRequirements =
                new ArrayList<>(worldRequirementCount);

        for (int i = 0; i < worldRequirementCount; i++) {
            int depth = buffer.readVarInt();
            int kindOrdinal = buffer.readUnsignedByte();

            if (kindOrdinal < 0
                    || kindOrdinal >= WorldRequirementKind.values().length) {
                throw new IllegalArgumentException(
                        "Invalid Ascendance world requirement kind: "
                                + kindOrdinal
                );
            }

            worldRequirements.add(
                    new WorldRequirementState(
                            depth,
                            WorldRequirementKind.values()[kindOrdinal],
                            buffer.readUtf(MAX_REQUIREMENT_LABEL_LENGTH),
                            buffer.readBoolean(),
                            buffer.readBoolean()
                    )
            );
        }

        ProgressState progress =
                new ProgressState(
                        ProgressStatus.values()[statusOrdinal],
                        nextTierId,
                        effectiveInvestment,
                        requiredInvestment,
                        developedStats,
                        requiredDevelopedStats,
                        representedCategories,
                        requiredRepresentedCategories,
                        developedStatThreshold,
                        worldRequirements,
                        buffer.readBoolean(),
                        buffer.readBoolean()
                );

        return new PlayerEssenceSyncPayload(
                schemaVersion,
                playerRevision,
                tierId,
                balanceProfileId,
                essenceBalances,
                stats,
                completedMilestones,
                skillMilestones,
                ownedSkills,
                loadoutSelections,
                attunement,
                progress
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
                    "Invalid "
                            + label
                            + " count in Essence sync payload: "
                            + count
            );
        }

        return count;
    }

    public record EssenceBalance(
            String essenceId,
            long available
    ) {
        public EssenceBalance {
            Objects.requireNonNull(
                    essenceId,
                    "Essence ID cannot be null"
            );

            if (available < 0L) {
                throw new IllegalArgumentException(
                        "Available Essence cannot be negative"
                );
            }
        }
    }

    public record StatState(
            String statId,
            long storedInvestment,
            long effectiveInvestment,
            long currentInvestmentCap,
            double progression,
            double currentTierMaximumBonus,
            double transcendentMaximumBonus,
            double scaledBonus,
            BonusTrackSnapshot track
    ) {
        public StatState {
            Objects.requireNonNull(track, "Resolved Bonus track cannot be null");
            Objects.requireNonNull(
                    statId,
                    "Stat ID cannot be null"
            );

            if (storedInvestment < 0L
                    || effectiveInvestment < 0L
                    || currentInvestmentCap < 0L) {
                throw new IllegalArgumentException(
                        "Synchronized stat investment values cannot be negative"
                );
            }

            if (!Double.isFinite(progression)
                    || progression < 0.0
                    || progression > 1.0) {
                throw new IllegalArgumentException(
                        "Synchronized stat progression must be between 0 and 1"
                );
            }

            requireNonNegativeFinite(
                    currentTierMaximumBonus,
                    "current tier maximum bonus"
            );

            requireNonNegativeFinite(
                    transcendentMaximumBonus,
                    "Transcendent maximum bonus"
            );

            requireNonNegativeFinite(
                    scaledBonus,
                    "scaled bonus"
            );
        }

        private static void requireNonNegativeFinite(
                double value,
                String label
        ) {
            if (!Double.isFinite(value)
                    || value < 0.0) {
                throw new IllegalArgumentException(
                        "Synchronized "
                                + label
                                + " must be finite and non-negative"
                );
            }
        }
    }

    /** Server-resolved state for one configurable milestone referenced by skills. */
    public record MilestoneState(
            String milestoneId,
            String displayName,
            boolean resolvable,
            boolean complete
    ) {
        public MilestoneState {
            Objects.requireNonNull(milestoneId, "Milestone ID cannot be null");
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("Milestone display name cannot be blank");
            }
            if (complete && !resolvable) {
                throw new IllegalArgumentException(
                        "A completed milestone must also be resolvable"
                );
            }
        }
    }

    public record OwnedSkillState(String skillId, String paidEssenceId, List<Long> paidCosts, boolean hudEnabled) {
        public OwnedSkillState(String skillId, String paidEssenceId, List<Long> paidCosts) {
            this(skillId, paidEssenceId, paidCosts, true);
        }
        public OwnedSkillState {
            Objects.requireNonNull(skillId, "Owned skill ID cannot be null");
            Objects.requireNonNull(paidEssenceId, "Paid Essence ID cannot be null");
            paidCosts = List.copyOf(paidCosts);
            if (paidCosts.isEmpty() || paidCosts.size() > 64) throw new IllegalArgumentException("Invalid skill rank");
            long total = 0L;
            for (long cost : paidCosts) {
                if (cost < 0L) throw new IllegalArgumentException("Invalid skill receipt");
                total = Math.addExact(total, cost);
            }
        }
        public int rank() { return paidCosts.size(); }
        public long paidCost() {
            long total = 0L;
            for (long cost : paidCosts) total = Math.addExact(total, cost);
            return total;
        }
    }

    public record LoadoutSelection(
            String selectionId,
            String skillId
    ) {
        public LoadoutSelection {
            Objects.requireNonNull(
                    selectionId,
                    "Loadout selection ID cannot be null"
            );
            Objects.requireNonNull(
                    skillId,
                    "Selected skill ID cannot be null"
            );
        }
    }

    public record ProgressState(
            ProgressStatus status,
            String nextTierId,
            long effectiveInvestment,
            long requiredInvestment,
            int developedStats,
            int requiredDevelopedStats,
            int representedCategories,
            int requiredRepresentedCategories,
            double developedStatThreshold,
            List<WorldRequirementState> worldRequirements,
            boolean worldProgressComplete,
            boolean readyToAscend
    ) {
        public ProgressState {
            Objects.requireNonNull(
                    status,
                    "Progress status cannot be null"
            );

            Objects.requireNonNull(
                    nextTierId,
                    "Next tier ID cannot be null"
            );

            Objects.requireNonNull(
                    worldRequirements,
                    "World requirements cannot be null"
            );

            worldRequirements = List.copyOf(worldRequirements);

            if (effectiveInvestment < 0L
                    || requiredInvestment < 0L
                    || developedStats < 0
                    || requiredDevelopedStats < 0
                    || representedCategories < 0
                    || requiredRepresentedCategories < 0) {
                throw new IllegalArgumentException(
                        "Synchronized Ascendance progress cannot be negative"
                );
            }

            if (!Double.isFinite(developedStatThreshold)
                    || developedStatThreshold < 0.0D
                    || developedStatThreshold > 1.0D) {
                throw new IllegalArgumentException(
                        "Synchronized developed-stat threshold must be between 0 and 1"
                );
            }
        }

        public boolean hasNextTier() {
            return !nextTierId.isBlank();
        }
    }

    public record WorldRequirementState(
            int depth,
            WorldRequirementKind kind,
            String label,
            boolean resolvable,
            boolean complete
    ) {
        public WorldRequirementState {
            if (depth < 0 || depth > 32) {
                throw new IllegalArgumentException(
                        "World requirement depth must be between 0 and 32"
                );
            }

            Objects.requireNonNull(kind, "World requirement kind cannot be null");
            Objects.requireNonNull(label, "World requirement label cannot be null");
            if (label.length() > MAX_REQUIREMENT_LABEL_LENGTH) {
                label = label.substring(0, MAX_REQUIREMENT_LABEL_LENGTH);
            }
        }
    }

    public enum WorldRequirementKind {
        MILESTONE,
        ALL_OF,
        ANY_OF,
        ALWAYS
    }

    public enum ProgressStatus {
        AVAILABLE,
        MAX_TIER,
        CONFIGURATION_ERROR
    }
}
