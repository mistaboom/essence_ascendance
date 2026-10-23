package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.team.MembershipResolution;
import com.mistaboom.essence_ascendance.team.MembershipSnapshot;
import com.mistaboom.essence_ascendance.team.PoolMembershipState;
import com.mistaboom.essence_ascendance.team.PoolCapability;
import com.mistaboom.essence_ascendance.team.MembershipAuthority;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=26.1.2
/*import net.minecraft.world.level.saveddata.SavedDataType;*/

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EssenceSavedData extends SavedData {

    private static final String DATA_NAME =
            "essence_ascendance_players";

    //? if >=26.1.2
    /*private static final SavedDataType<EssenceSavedData> DATA_TYPE = new SavedDataType<>(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, DATA_NAME), EssenceSavedData::new, CompoundTag.CODEC.xmap(tag -> load(tag, null), data -> data.save(new CompoundTag(), null)), DataFixTypes.LEVEL);*/

    private static final String PLAYERS_TAG =
            "players";


    private final Map<UUID, PlayerEssenceData> players =
            new HashMap<>();

    private final ReservoirService reservoirs = new ReservoirService(this);
    public ReservoirService reservoirs() { reservoirs.checkThread(); return reservoirs; }
    PlayerEssenceData existingPlayerData(UUID player) { return players.get(player); }

    private PoolMembershipState memberships = new PoolMembershipState();
    private com.mistaboom.essence_ascendance.team.NativeMembershipService nativeMembership =
            new com.mistaboom.essence_ascendance.team.NativeMembershipService(this);
    private java.util.function.Consumer<UUID> membershipProbe = ignored -> { };
    public com.mistaboom.essence_ascendance.team.NativeMembershipService nativeMembership() {
        reservoirs.checkThread(); return nativeMembership;
    }
    public MembershipAuthority establishedMembershipAuthority() { reservoirs.checkThread(); return memberships.establishedAuthority(); }
    public MembershipAuthority membershipAuthority() {
        reservoirs.checkThread();
        return memberships.establishedAuthority() == null ? MembershipAuthority.NATIVE : memberships.establishedAuthority();
    }
    public void establishMembershipAuthority(MembershipAuthority authority) {
        reservoirs.checkThread(); memberships.establish(authority); setDirty();
    }
    public void activateMembershipAuthority(MembershipAuthority authority,
            com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness ready,
            java.util.Collection<MembershipSnapshot> teams, boolean preview) {
        reservoirs.checkThread();
        if (authority == membershipAuthority()) throw new IllegalArgumentException("Authority is already established");
        PoolMembershipState replacement = memberships.forAuthority(authority);
        reservoirs.activateAuthority(authority, ready, teams, () -> memberships = replacement, preview);
    }
    public void membershipProbe(java.util.function.Consumer<UUID> probe) {
        reservoirs.checkThread(); membershipProbe = java.util.Objects.requireNonNull(probe);
    }

    /** World-wide resolution also works for offline players and every dimension. */
    public MembershipResolution resolvePool(UUID playerId) {
        reservoirs.checkThread();
        membershipProbe.accept(playerId);
        MembershipResolution team = reservoirs.teamResolution(playerId);
        if (team != null) return team;
        boolean existed = memberships.contains(playerId);
        MembershipResolution resolved = memberships.resolveSolo(playerId);
        if (!existed && resolved.snapshot().isPresent()) setDirty();
        return resolved;
    }

    /** Personal identity metadata is independent of current effective membership; never grants spending. */
    public MembershipSnapshot personalPool(UUID player) {
        reservoirs.checkThread();
        MembershipSnapshot personal = memberships.resolveSolo(player).requireReady();
        setDirty(); return personal;
    }
    public UUID personalOwner(com.mistaboom.essence_ascendance.team.PoolId pool) {
        reservoirs.checkThread(); return memberships.personalOwner(pool);
    }
    public MembershipSnapshot resolveMachinePool(com.mistaboom.essence_ascendance.team.PoolId id, UUID personalOwner) {
        reservoirs.checkThread();
        if (id == null || id.authority() != membershipAuthority()) return null;
        if (id.kind() == com.mistaboom.essence_ascendance.team.PoolId.Kind.SOLO) {
            var current = resolvePool(personalOwner).snapshot().orElse(null);
            return current != null && current.pool().equals(id) ? current : null;
        }
        MembershipSnapshot team = reservoirs.settledPool(id);
        if (team == null) return null;
        // One affected-party probe verifies roster/leader; actor probes verify their own current grants.
        resolvePool(team.members().getFirst());
        return com.mistaboom.essence_ascendance.team.MembershipAuthority.ready(reservoirs.readiness()) ? reservoirs.settledPool(id) : null;
    }

    /** Existing accounting is the one-member case; no second spendable treasury. */
    public PlayerEssenceData soloReservoirAccount(UUID playerId) {
        MembershipSnapshot membership = resolvePool(playerId).requireReady();
        if (!membership.isSoloFor(playerId)
                || !membership.allows(playerId, PoolCapability.SPEND))
            throw new IllegalStateException("Personal reservoir access requires an authorized solo pool");
        return getPlayerData(playerId);
    }


    /*
     * ============================================================
     * ACCESS
     * ============================================================
     */

    public static EssenceSavedData get(
            MinecraftServer server
    ) {

        if (!server.isSameThread()) throw new IllegalStateException("EssenceSavedData requires the server thread");
        //? if >=26.1.2
        /*return server.overworld().getDataStorage().computeIfAbsent(DATA_TYPE);*/
        //? if <26.1.2
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(EssenceSavedData::new, EssenceSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }


    public PlayerEssenceData getPlayerData(
            UUID playerId
    ) {

        PlayerEssenceData existing =
                players.get(
                        playerId
                );

        if (existing != null) {
            return existing;
        }


        PlayerEssenceData created =
                new PlayerEssenceData();

        players.put(
                playerId,
                created
        );

        setDirty();

        return created;
    }


    /*
     * ============================================================
     * ESSENCE MUTATION
     * ============================================================
     */

    public long addEssence(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        long updated =
                playerData.addAvailable(
                        essence,
                        amount
                );

        setDirty();

        return updated;
    }


    public void setEssence(
            UUID playerId,
            EssenceDefinition essence,
            long amount
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        playerData.setAvailable(
                essence,
                amount
        );

        setDirty();
    }


    /*
     * ============================================================
     * CRUCIBLE RESERVOIR MUTATION
     * ============================================================
     */

    // Legacy personal entry points remain SOLO-only; current machine work uses pooled transactions.
    public long addCrucibleStored(UUID playerId, EssenceDefinition essence, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Positive credit required");
        soloReservoirAccount(playerId);
        reservoirs.credit(playerId, ReservoirService.wholeUnits(Map.of(essence.id(), amount), false), ReservoirService.NONE);
        return reservoirs.read(playerId).whole(essence.id()).longValueExact();
    }

    public long removeCrucibleStored(UUID playerId, EssenceDefinition essence, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Positive debit required");
        soloReservoirAccount(playerId);
        long removed = reservoirs.read(playerId).whole(essence.id()).min(java.math.BigInteger.valueOf(amount)).longValueExact();
        if (removed > 0) reservoirs.debit(playerId, Map.of(essence.id(), removed), ReservoirService.NONE);
        return removed;
    }

    public boolean removeCrucibleStoredExact(UUID playerId, EssenceDefinition essence, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Positive debit required");
        soloReservoirAccount(playerId);
        return reservoirs.debit(playerId, Map.of(essence.id(), amount), ReservoirService.NONE);
    }

    public long transferCrucibleToAvailable(UUID playerId, EssenceDefinition essence, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Positive transfer required");
        PlayerEssenceData player = soloReservoirAccount(playerId);
        long moved = reservoirs.read(playerId).whole(essence.id()).min(java.math.BigInteger.valueOf(
                Math.min(amount, Long.MAX_VALUE - player.getAvailable(essence)))).longValueExact();
        if (moved > 0) reservoirs.channel(playerId, Map.of(essence.id(), moved));
        return moved;
    }

    /** Explicit administrative aggregate clear, including every selected fractional share. */
    public boolean clearReservoir(UUID member, java.util.Collection<ResourceLocation> types) {
        Map<ResourceLocation, java.math.BigInteger> target = new java.util.LinkedHashMap<>();
        types.forEach(type -> target.put(type, java.math.BigInteger.ZERO));
        return reservoirs.administer(member, target);
    }

    /*
     * ============================================================
     * STAT INVESTMENT
     * ============================================================
     */

    public boolean invest(
            UUID playerId,
            StatDefinition stat,
            long amount
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean success =
                playerData.invest(
                        stat,
                        amount
                );

        if (success) {
            setDirty();
        }

        return success;
    }


    public void setInvested(
            UUID playerId,
            StatDefinition stat,
            long amount
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        playerData.setInvested(
                stat,
                amount
        );

        setDirty();
    }





    /**
     * Applies one prevalidated complete Nexus projection atomically and marks
     * the world data dirty only when the authoritative state changed.
     */
    public boolean applyNexusTransaction(
            UUID playerId,
            Map<ResourceLocation, Long> targetInvestments,
            Map<ResourceLocation, Long> targetAvailable,
            Map<ResourceLocation, SkillPurchase> targetOwnedSkills,
            Map<ResourceLocation, ResourceLocation> targetLoadoutSelections,
            ResourceLocation targetTierId
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.applyNexusTransaction(
                        targetInvestments,
                        targetAvailable,
                        targetOwnedSkills,
                        targetLoadoutSelections,
                        targetTierId
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public boolean recordSkillPurchase(
            UUID playerId,
            ResourceLocation skillId,
            SkillPurchase purchase
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.recordSkillPurchase(
                        skillId,
                        purchase
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }

    public int grantAllSkillsForAdmin(UUID playerId, Iterable<com.mistaboom.essence_ascendance.skill.SkillDefinition> definitions) {
        int granted = getPlayerData(playerId).grantAllSkillsForAdmin(definitions);
        if (granted > 0) setDirty();
        return granted;
    }

    public int clearAllSkillsForAdmin(UUID playerId) {
        int removed = getPlayerData(playerId).clearAllSkillsForAdmin();
        setDirty();
        return removed;
    }


    public boolean setLoadoutSelection(
            UUID playerId,
            ResourceLocation selectionId,
            ResourceLocation skillId
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.setLoadoutSelection(
                        selectionId,
                        skillId
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public boolean clearLoadoutSelection(
            UUID playerId,
            ResourceLocation selectionId
    ) {
        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        boolean changed =
                playerData.clearLoadoutSelection(
                        selectionId
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public void clearAll(
            UUID playerId
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        if (resolvePool(playerId).requireReady().isSoloFor(playerId))
            clearReservoir(playerId, com.mistaboom.essence_ascendance.essence.EssenceTypes.ORDERED.stream().map(EssenceDefinition::id).toList());
        playerData.clearAll();

        setDirty();
    }


    /** Personal resets may clear a SOLO reservoir, but never confiscate a communal share. */
    public void resetProgressionForAdmin(UUID playerId) {
        if (resolvePool(playerId).requireReady().isSoloFor(playerId))
            clearReservoir(playerId, com.mistaboom.essence_ascendance.essence.EssenceTypes.ORDERED.stream().map(EssenceDefinition::id).toList());
        getPlayerData(playerId).resetProgressionForAdmin();
        setDirty();
    }

    /** Persists sub-unit accounting used by server-authoritative menu costs. */
    public boolean setFractionalResourceCostCarry(
            UUID playerId,
            net.minecraft.resources.ResourceLocation channelId,
            double carry
    ) {
        boolean changed = getPlayerData(playerId)
                .setFractionalResourceCostCarry(channelId, carry);

        if (changed) {
            setDirty();
        }
        return changed;
    }


    /*
     * ============================================================
     * ASCENDANCE TIER
     * ============================================================
     */

    public AscendanceTierDefinition getTier(
            UUID playerId
    ) {

        return getPlayerData(
                playerId
        ).getTier();
    }


    public void setTier(
            UUID playerId,
            AscendanceTierDefinition tier
    ) {

        PlayerEssenceData playerData =
                getPlayerData(
                        playerId
                );

        playerData.setTier(
                tier
        );

        setDirty();
    }


    /*
     * ============================================================
     * PERMANENT MILESTONE STATE
     * ============================================================
     */

    public boolean hasCompletedMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {
        return getPlayerData(playerId).hasCompletedMilestone(milestoneId);
    }


    public boolean completeMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {
        PlayerEssenceData playerData = getPlayerData(playerId);
        boolean changed = playerData.completeMilestone(milestoneId);

        if (changed) {
            setDirty();
        }

        return changed;
    }


    public boolean revokeMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {
        PlayerEssenceData playerData = getPlayerData(playerId);
        boolean changed = playerData.revokeMilestone(milestoneId);

        if (changed) {
            setDirty();
        }

        return changed;
    }


    /* Provider compatibility helpers for existing internal milestones. */

    public boolean hasCompletedInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {
        return hasCompletedMilestone(playerId, milestoneId);
    }


    public boolean completeInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {

        return completeMilestone(playerId, milestoneId);
    }


    public boolean revokeInternalMilestone(
            UUID playerId,
            ResourceLocation milestoneId
    ) {

        return revokeMilestone(playerId, milestoneId);
    }


    /*
     * ============================================================
     * CATEGORY ATTUNEMENT
     * ============================================================
     */










    /*
     * ============================================================
     * SAVE / LOAD
     * ============================================================
     */

    //? if <26.1.2
    @Override
    public CompoundTag save(
            CompoundTag root,
            HolderLookup.Provider registries
    ) {

        root.putInt("data_version", 10);
        root.put("native_membership", nativeMembership.save());
        root.put("reservoir_settlement", reservoirs.save());
        root.put("pool_membership", memberships.save());


        CompoundTag playersTag =
                new CompoundTag();


        for (Map.Entry<UUID, PlayerEssenceData> entry :
                players.entrySet()) {

            playersTag.put(
                    entry.getKey().toString(),
                    entry.getValue().save()
            );
        }


        root.put(
                PLAYERS_TAG,
                playersTag
        );


        return root;
    }


    public static EssenceSavedData load(
            CompoundTag root,
            HolderLookup.Provider provider
    ) {

        if (root.getInt("data_version") != 10) {
            throw new IllegalStateException("Essence Ascendance development save schema is incompatible. "
                    + "Recreate the test world; pre-release save migration is unsupported.");
        }

        EssenceSavedData data =
                new EssenceSavedData();

        data.memberships = PoolMembershipState.load(root.getCompound("pool_membership"));
        data.reservoirs.load(root.getCompound("reservoir_settlement"));
        data.nativeMembership = com.mistaboom.essence_ascendance.team.NativeMembershipService.load(data, root.getCompound("native_membership"));


        if (!root.contains(PLAYERS_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND))
            throw new IllegalStateException("Missing saved player records");
        CompoundTag playersTag =
                root.getCompound(
                        PLAYERS_TAG
                );


        for (String key :
                playersTag.getAllKeys()) {
            if (!playersTag.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND))
                throw new IllegalStateException("Malformed player record " + key);

            try {

                UUID playerId =
                        UUID.fromString(
                                key
                        );


                PlayerEssenceData playerData =
                        PlayerEssenceData.load(
                                playersTag.getCompound(
                                        key
                                )
                        );


                data.players.put(
                        playerId,
                        playerData
                );


            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("Invalid saved player record " + key, invalid);
            }
        }




        data.reservoirs.initializeMissingMembers();
        return data;
    }
}
