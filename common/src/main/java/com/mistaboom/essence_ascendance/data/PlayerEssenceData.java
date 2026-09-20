package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class PlayerEssenceData {
    private com.mistaboom.essence_ascendance.vitality.VitalityDamageLedger vitalityDamage =
            new com.mistaboom.essence_ascendance.vitality.VitalityDamageLedger();
    public com.mistaboom.essence_ascendance.vitality.VitalityDamageLedger vitalityDamage() { return vitalityDamage; }


    // Persistent owner identity invalidates even projectiles stored in unloaded chunks.
    private java.util.UUID projectileLife = java.util.UUID.randomUUID();
    public java.util.UUID projectileLife() { return projectileLife; }
    public void invalidateProjectiles() { projectileLife = java.util.UUID.randomUUID(); }

    private static final String AVAILABLE_TAG =
            "available";

    private static final String INVESTED_TAG =
            "invested";

    private static final String CRUCIBLE_RESERVOIR_TAG =
            "crucible_reservoir";


    private static final String TIER_TAG =
            "tier";

    private static final String DORMANT_GUIDEBOOK_RECEIVED_TAG =
            "dormant_guidebook_received";

    private boolean dormantGuidebookReceived;

    private static final String COMPLETED_MILESTONES_TAG =
            "completed_milestones";

    private static final String OWNED_SKILLS_TAG =
            "skill_ranks";

    private static final String SKILL_PAID_ESSENCE_TAG =
            "paid_essence";

    private static final String SKILL_RANK_COSTS_TAG =
            "rank_paid_costs";

    private static final String LOADOUT_SELECTIONS_TAG =
            "loadout_selections";

private static final String NEXUS_REVISION_TAG =
            "nexus_revision";

    private static final String FRACTIONAL_RESOURCE_COST_CARRY_TAG =
            "fractional_resource_cost_carry";

    private static final String ABILITY_COOLDOWNS_TAG =
            "ability_cooldowns";


    private final Map<ResourceLocation, Long> availableEssence =
            new LinkedHashMap<>();

    private final Map<ResourceLocation, Long> investedEssence =
            new LinkedHashMap<>();

    /*
     * Essence dissolved by owned Crucibles but not yet channeled into the
     * player's spendable AVAILABLE balance. This is intentionally player-
     * owned rather than block-owned so multiple Crucibles share one capped
     * reservoir and breaking/replacing a Crucible cannot lose the stored
     * Essence.
     *
     * Changes here do not bump the normal progression revision because the
     * existing player Essence sync payload does not expose this reservoir.
     * The Crucible screen has its own server-authoritative state payload.
     */
    private final Map<ResourceLocation, Long> crucibleReservoir =
            new LinkedHashMap<>();

    private final Set<ResourceLocation> completedMilestones =
            new LinkedHashSet<>();

    /*
     * Permanent ownership receipts. The category/Essence paid is captured in
     * each receipt so future refunds remain exact even if a definition changes.
     * IDs are intentionally not registry-filtered during load: temporarily
     * unavailable or removed skills must round-trip without reinterpretation.
     */
    private final Map<ResourceLocation, SkillPurchase> ownedSkills =
            new LinkedHashMap<>();

    /*
     * Stable selection-slot/choice-group ID -> selected skill ID. A missing
     * entry means that slot has no selection. Both sides remain raw IDs so
     * data-driven and temporarily unknown definitions survive save/load.
     */
    private final Map<ResourceLocation, ResourceLocation> loadoutSelections =
            new LinkedHashMap<>();

    private com.mistaboom.essence_ascendance.attunement.AttunementLedger attunement =
            new com.mistaboom.essence_ascendance.attunement.AttunementLedger();

    public com.mistaboom.essence_ascendance.attunement.AttunementLedger attunement() { return attunement; }

    /*
     * Sub-unit accounting for percentage reductions applied to integer menu
     * costs. Keys are stable cost-channel IDs rather than menu classes, which
     * lets every resource-cost consumer share the same persistence format.
     * This state is deliberately not part of the Nexus revision: it changes
     * only when gameplay consumes a resource and is never edited in the UI.
     */
    private final Map<ResourceLocation, Double> fractionalResourceCostCarry =
            new LinkedHashMap<>();

    /* Persistent game-time deadlines for reusable ability/choice-group cooldown channels. */
    private final Map<ResourceLocation, Long> abilityCooldowns =
            new LinkedHashMap<>();


    private ResourceLocation currentTierId =
            AscendanceTiers.LATENT.id();


    /*
     * Persistent authoritative Nexus-state revision. Every mutation visible
     * to the Nexus increments this value, allowing stale client transactions
     * to be rejected across reconnects and server restarts as well as within a
     * single session.
     */
    private long nexusRevision = 0L;





    public long nexusRevision() {
        return nexusRevision;
    }


    private void bumpRevision() {
        /* Saturation never permits an old transaction revision to become current again. */
        if (nexusRevision < Long.MAX_VALUE) {
            nexusRevision++;
        }
    }


    /*
     * ============================================================
     * AVAILABLE ESSENCE
     * ============================================================
     */

    public long getAvailable(
            EssenceDefinition essence
    ) {
        return getAvailable(
                essence.id()
        );
    }


    public long getAvailable(
            ResourceLocation essenceId
    ) {
        return availableEssence.getOrDefault(
                essenceId,
                0L
        );
    }


    public long addAvailable(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Essence amount must be greater than zero"
            );
        }

        long current =
                getAvailable(
                        essence
                );

        long updated =
                Math.addExact(
                        current,
                        amount
                );

        availableEssence.put(
                essence.id(),
                updated
        );

        bumpRevision();

        return updated;
    }


    public void setAvailable(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Essence amount cannot be negative"
            );
        }

        long current =
                getAvailable(
                        essence
                );

        if (current == amount) {
            return;
        }

        if (amount == 0) {

            availableEssence.remove(
                    essence.id()
            );

        } else {

            availableEssence.put(
                    essence.id(),
                    amount
            );
        }

        bumpRevision();
    }


    public Map<ResourceLocation, Long> getAllAvailable() {
        return Collections.unmodifiableMap(
                availableEssence
        );
    }


    /*
     * ============================================================
     * CRUCIBLE RESERVOIR
     * ============================================================
     */

    public long getCrucibleStored(
            EssenceDefinition essence
    ) {
        return getCrucibleStored(
                essence.id()
        );
    }


    public long getCrucibleStored(
            ResourceLocation essenceId
    ) {
        return crucibleReservoir.getOrDefault(
                essenceId,
                0L
        );
    }


    public long addCrucibleStored(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount <= 0L) {
            throw new IllegalArgumentException(
                    "Crucible Essence amount must be greater than zero"
            );
        }

        long updated = Math.addExact(
                getCrucibleStored(essence),
                amount
        );

        crucibleReservoir.put(
                essence.id(),
                updated
        );

        return updated;
    }


    public long removeCrucibleStored(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount <= 0L) {
            throw new IllegalArgumentException(
                    "Crucible Essence amount must be greater than zero"
            );
        }

        long current = getCrucibleStored(essence);
        long removed = Math.min(current, amount);
        if (removed <= 0L) {
            return 0L;
        }

        long remaining = current - removed;
        if (remaining == 0L) {
            crucibleReservoir.remove(
                    essence.id()
            );
        } else {
            crucibleReservoir.put(
                    essence.id(),
                    remaining
            );
        }

        return removed;
    }


    public boolean removeCrucibleStoredExact(
            EssenceDefinition essence,
            long amount
    ) {
        if (amount <= 0L) {
            throw new IllegalArgumentException(
                    "Crucible Essence amount must be greater than zero"
            );
        }

        long current = getCrucibleStored(essence);
        if (current < amount) {
            return false;
        }

        long remaining = current - amount;
        if (remaining == 0L) {
            crucibleReservoir.remove(essence.id());
        } else {
            crucibleReservoir.put(essence.id(), remaining);
        }
        return true;
    }


    public long transferCrucibleToAvailable(
            EssenceDefinition essence,
            long requestedAmount
    ) {
        if (requestedAmount <= 0L) {
            throw new IllegalArgumentException(
                    "Transfer amount must be greater than zero"
            );
        }

        long stored = getCrucibleStored(essence);
        if (stored <= 0L) {
            return 0L;
        }

        long available = getAvailable(essence);
        long room = Long.MAX_VALUE - available;
        if (room <= 0L) {
            return 0L;
        }

        long moved = Math.min(
                requestedAmount,
                Math.min(stored, room)
        );
        if (moved <= 0L) {
            return 0L;
        }

        long remainingStored = stored - moved;
        if (remainingStored == 0L) {
            crucibleReservoir.remove(
                    essence.id()
            );
        } else {
            crucibleReservoir.put(
                    essence.id(),
                    remainingStored
            );
        }

        availableEssence.put(
                essence.id(),
                available + moved
        );

        /* AVAILABLE changed, so the normal player Essence payload must sync. */
        bumpRevision();

        return moved;
    }


    public Map<ResourceLocation, Long> getAllCrucibleStored() {
        return Collections.unmodifiableMap(
                crucibleReservoir
        );
    }


    /*
     * ============================================================
     * INVESTED ESSENCE
     * ============================================================
     */

    public long getInvested(
            StatDefinition stat
    ) {
        return getInvested(
                stat.id()
        );
    }


    public long getInvested(
            ResourceLocation statId
    ) {
        return investedEssence.getOrDefault(
                statId,
                0L
        );
    }


    public boolean invest(
            StatDefinition stat,
            long amount
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Investment amount must be greater than zero"
            );
        }

        EssenceDefinition requiredEssence =
                stat.essenceType();

        long available =
                getAvailable(
                        requiredEssence
                );

        if (available < amount) {
            return false;
        }

        long currentInvestment =
                getInvested(
                        stat
                );

        long newInvestment =
                Math.addExact(
                        currentInvestment,
                        amount
                );

        long remaining =
                available - amount;


        if (remaining == 0) {

            availableEssence.remove(
                    requiredEssence.id()
            );

        } else {

            availableEssence.put(
                    requiredEssence.id(),
                    remaining
            );
        }


        investedEssence.put(
                stat.id(),
                newInvestment
        );

        bumpRevision();

        return true;
    }


    public Map<ResourceLocation, Long> getAllInvested() {
        return Collections.unmodifiableMap(
                investedEssence
        );
    }


    public void setInvested(
            StatDefinition stat,
            long amount
    ) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Invested Essence amount cannot be negative"
            );
        }

        long current =
                getInvested(
                        stat
                );

        if (current == amount) {
            return;
        }

        if (amount == 0) {

            investedEssence.remove(
                    stat.id()
            );

        } else {

            investedEssence.put(
                    stat.id(),
                    amount
            );
        }

        bumpRevision();
    }


    public void clearAvailable() {
        if (availableEssence.isEmpty()) {
            return;
        }

        availableEssence.clear();
        bumpRevision();
    }


    public void clearInvested() {
        if (investedEssence.isEmpty()) {
            return;
        }

        investedEssence.clear();
        bumpRevision();
    }


    public void clearAll() {
        /*
         * Intentionally clears Essence/stat progression only.
         *
         * It does not modify Ascendance tier or completed world
         * milestones.
         */
        if (availableEssence.isEmpty()
                && investedEssence.isEmpty()
                && crucibleReservoir.isEmpty()) {
            return;
        }

        availableEssence.clear();
        investedEssence.clear();
        crucibleReservoir.clear();
        bumpRevision();
    }

    /**
     * Start progression from Latent for operator testing, including the
     * onboarding reward. Physical inventory and equipment accounting remain.
     */
    public void resetProgressionForAdmin() {
        availableEssence.clear();
        investedEssence.clear();
        crucibleReservoir.clear();
        ownedSkills.clear();
        loadoutSelections.clear();
        abilityCooldowns.clear();
        completedMilestones.clear();
        attunement = new com.mistaboom.essence_ascendance.attunement.AttunementLedger();
        currentTierId = AscendanceTiers.LATENT.id();
        dormantGuidebookReceived = false;
        bumpRevision();
    }

    /** Invalidate open Nexus drafts after a direct operator edit of earned seals. */
    public void markAttunementChangedForAdmin() {
        bumpRevision();
    }

    public boolean hasReceivedDormantGuidebook() {
        return dormantGuidebookReceived;
    }

    /** Normal tier changes preserve this receipt; a full operator progression reset rearms it. */
    public void markDormantGuidebookReceived() {
        dormantGuidebookReceived = true;
    }


    /*
     * ============================================================
     * ASCENDANCE TIER
     * ============================================================
     */

    public AscendanceTierDefinition getTier() {
        return AscendanceTierRegistry
                .get(
                        currentTierId
                )
                .orElse(
                        AscendanceTiers.LATENT
                );
    }


    public ResourceLocation getTierId() {
        return currentTierId;
    }


    public void setTier(
            AscendanceTierDefinition tier
    ) {
        if (currentTierId.equals(
                tier.id()
        )) {
            return;
        }

        currentTierId =
                tier.id();
        attunement.chapter("");

        bumpRevision();
    }


    /*
     * ============================================================
     * PERMANENT MILESTONE STATE
     *
     * Internal-provider targets and captured configurable milestone IDs share
     * this stable set. Provider evaluation decides which identity is queried.
     * ============================================================
     */

    public boolean hasCompletedMilestone(
            ResourceLocation milestoneId
    ) {
        return completedMilestones.contains(
                Objects.requireNonNull(
                        milestoneId,
                        "Milestone ID cannot be null"
                )
        );
    }


    public boolean completeMilestone(
            ResourceLocation milestoneId
    ) {
        Objects.requireNonNull(
                milestoneId,
                "Milestone ID cannot be null"
        );

        boolean changed =
                completedMilestones.add(
                        milestoneId
                );

        if (changed) {
            bumpRevision();
        }

        return changed;
    }


    public boolean revokeMilestone(
            ResourceLocation milestoneId
    ) {
        Objects.requireNonNull(
                milestoneId,
                "Milestone ID cannot be null"
        );

        boolean changed =
                completedMilestones.remove(
                        milestoneId
                );

        if (changed) {
            bumpRevision();
        }

        return changed;
    }


    public Set<ResourceLocation> getCompletedMilestones() {
        return Collections.unmodifiableSet(
                completedMilestones
        );
    }


    /*
     * ============================================================
     * PERMANENT SKILL OWNERSHIP
     * ============================================================
     */

    public boolean ownsSkill(
            ResourceLocation skillId
    ) {
        return ownedSkills.containsKey(
                Objects.requireNonNull(skillId, "Skill ID cannot be null")
        );
    }


    public int skillRank(ResourceLocation skillId) {
        SkillPurchase purchase = ownedSkills.get(Objects.requireNonNull(skillId));
        return purchase == null ? 0 : purchase.rank();
    }

    public Map<ResourceLocation, Integer> getSkillRanks() {
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        ownedSkills.forEach((id, purchase) -> result.put(id, purchase.rank()));
        return Collections.unmodifiableMap(result);
    }

    public Optional<SkillPurchase> getSkillPurchase(
            ResourceLocation skillId
    ) {
        return Optional.ofNullable(
                ownedSkills.get(
                        Objects.requireNonNull(skillId, "Skill ID cannot be null")
                )
        );
    }


    public Map<ResourceLocation, SkillPurchase> getOwnedSkills() {
        return Collections.unmodifiableMap(
                ownedSkills
        );
    }


    /**
     * Records a first-time permanent purchase without spending Essence.
     * Spending and eligibility validation belong to the server transaction
     * service; this method preserves the original receipt and refuses to
     * overwrite it.
     */
    public boolean recordSkillPurchase(
            ResourceLocation skillId,
            SkillPurchase purchase
    ) {
        Objects.requireNonNull(skillId, "Skill ID cannot be null");
        Objects.requireNonNull(purchase, "Skill purchase cannot be null");

        if (ownedSkills.containsKey(skillId)) {
            return false;
        }

        ownedSkills.put(
                skillId,
                purchase
        );

        bumpRevision();
        return true;
    }

    /** Development-only bulk mutation: grants the current catalog at zero cost in one revision. */
    public int grantAllSkillsForAdmin(Iterable<SkillDefinition> definitions) {
        Objects.requireNonNull(definitions, "Skill definitions cannot be null");
        int granted = 0;
        for (SkillDefinition definition : definitions) {
            Objects.requireNonNull(definition, "Skill definition cannot be null");
            if (!ownedSkills.containsKey(definition.id())) {
                ownedSkills.put(definition.id(), new SkillPurchase(definition.essenceId(), 0L));
                granted++;
            }
        }
        if (granted > 0) bumpRevision();
        return granted;
    }

    /** Development-only reset of both ownership receipts and their selections. */
    public int clearAllSkillsForAdmin() {
        int removed = ownedSkills.size();
        if (removed == 0 && loadoutSelections.isEmpty() && abilityCooldowns.isEmpty()) return 0;
        ownedSkills.clear();
        loadoutSelections.clear();
        abilityCooldowns.clear();
        bumpRevision();
        return removed;
    }


    /*
     * ============================================================
     * FREE LOADOUT / CHOICE SELECTIONS
     * ============================================================
     */

    public Optional<ResourceLocation> getLoadoutSelection(
            ResourceLocation selectionId
    ) {
        return Optional.ofNullable(
                loadoutSelections.get(
                        Objects.requireNonNull(
                                selectionId,
                                "Loadout selection ID cannot be null"
                        )
                )
        );
    }


    public Map<ResourceLocation, ResourceLocation> getLoadoutSelections() {
        return Collections.unmodifiableMap(
                loadoutSelections
        );
    }


    public boolean setLoadoutSelection(
            ResourceLocation selectionId,
            ResourceLocation skillId
    ) {
        Objects.requireNonNull(
                selectionId,
                "Loadout selection ID cannot be null"
        );
        Objects.requireNonNull(
                skillId,
                "Selected skill ID cannot be null"
        );

        ResourceLocation previous =
                loadoutSelections.put(
                        selectionId,
                        skillId
                );

        if (skillId.equals(previous)) {
            return false;
        }

        bumpRevision();
        return true;
    }


    public boolean clearLoadoutSelection(
            ResourceLocation selectionId
    ) {
        Objects.requireNonNull(
                selectionId,
                "Loadout selection ID cannot be null"
        );

        if (loadoutSelections.remove(selectionId) == null) {
            return false;
        }

        bumpRevision();
        return true;
    }


    /*
     * ============================================================
     * CATEGORY ATTUNEMENT
     * ============================================================
     */













    /*
     * ============================================================
     * FRACTIONAL RESOURCE-COST ACCOUNTING
     * ============================================================
     */

    public double getFractionalResourceCostCarry(
            ResourceLocation channelId
    ) {
        return fractionalResourceCostCarry.getOrDefault(
                Objects.requireNonNull(channelId, "Cost channel ID cannot be null"),
                0.0D
        );
    }


    public Map<ResourceLocation, Double> getAllFractionalResourceCostCarry() {
        return Collections.unmodifiableMap(
                fractionalResourceCostCarry
        );
    }


    /**
     * Updates one committed resource-cost carry without changing the Nexus
     * optimistic-concurrency revision.
     */
    public boolean setFractionalResourceCostCarry(
            ResourceLocation channelId,
            double carry
    ) {
        Objects.requireNonNull(channelId, "Cost channel ID cannot be null");
        if (!Double.isFinite(carry) || carry < 0.0D || carry >= 1.0D) {
            throw new IllegalArgumentException(
                    "Fractional resource-cost carry must be finite and in [0, 1)"
            );
        }

        double normalized = carry < 1.0E-9D ? 0.0D : carry;
        double previous = getFractionalResourceCostCarry(channelId);
        if (Double.compare(previous, normalized) == 0) {
            return false;
        }

        if (normalized == 0.0D) {
            fractionalResourceCostCarry.remove(channelId);
        } else {
            fractionalResourceCostCarry.put(channelId, normalized);
        }
        return true;
    }


    /*
     * ============================================================
     * PERSISTENT ABILITY COOLDOWNS
     * ============================================================
     */

    public long getAbilityCooldownUntil(ResourceLocation channelId) {
        return abilityCooldowns.getOrDefault(Objects.requireNonNull(channelId, "Cooldown channel ID cannot be null"), 0L);
    }

    /** Updates gameplay cooldown state without changing the Nexus optimistic-concurrency revision. */
    public boolean setAbilityCooldownUntil(ResourceLocation channelId, long gameTime) {
        Objects.requireNonNull(channelId, "Cooldown channel ID cannot be null");
        if (gameTime < 0) throw new IllegalArgumentException("Ability cooldown game time cannot be negative");
        long previous = getAbilityCooldownUntil(channelId);
        if (previous == gameTime) return false;
        if (gameTime == 0) abilityCooldowns.remove(channelId);
        else abilityCooldowns.put(channelId, gameTime);
        return true;
    }

    public Map<ResourceLocation, Long> getAllAbilityCooldowns() {
        return Collections.unmodifiableMap(abilityCooldowns);
    }

    /*
     * ============================================================
     * ATOMIC NEXUS STATE APPLICATION
     * ============================================================
     */

    /**
     * Replaces one fully validated projected Nexus state in a single revision.
     *
     * <p>All arguments are complete target maps, not deltas. Callers must start
     * from the current getters so raw unknown IDs are retained. Permanent skill
     * receipts may only be added; an existing receipt cannot be removed or
     * rewritten by a normal Nexus transaction.</p>
     */
    public boolean applyNexusTransaction(
            Map<ResourceLocation, Long> targetInvestments,
            Map<ResourceLocation, Long> targetAvailable,
            Map<ResourceLocation, SkillPurchase> targetOwnedSkills,
            Map<ResourceLocation, ResourceLocation> targetLoadoutSelections,
            ResourceLocation targetTierId
    ) {
        Map<ResourceLocation, Long> normalizedInvestments =
                normalizeLongTargets(
                        targetInvestments,
                        "stat investment"
                );

        Map<ResourceLocation, Long> normalizedAvailable =
                normalizeLongTargets(
                        targetAvailable,
                        "available Essence"
                );

        Map<ResourceLocation, SkillPurchase> normalizedOwnedSkills =
                copySkillPurchases(
                        targetOwnedSkills
                );

        Map<ResourceLocation, ResourceLocation> normalizedLoadoutSelections =
                copyLoadoutSelections(
                        targetLoadoutSelections
                );

        ResourceLocation normalizedTierId =
                Objects.requireNonNull(
                        targetTierId,
                        "Target tier ID cannot be null"
                );

        for (var existing : ownedSkills.entrySet()) {
            SkillPurchase previous = existing.getValue();
            SkillPurchase replacement = normalizedOwnedSkills.get(existing.getKey());
            int retainedRank = replacement == null ? 0 : Math.min(previous.rank(), replacement.rank());
            if (replacement != null && (!previous.essenceId().equals(replacement.essenceId())
                    || !previous.paidCosts().subList(0, retainedRank)
                    .equals(replacement.paidCosts().subList(0, retainedRank)))) {
                throw new IllegalArgumentException("A Nexus transaction cannot rewrite paid rank receipts");
            }
            if (replacement == null || replacement.rank() < previous.rank()) {
                var definition = com.mistaboom.essence_ascendance.skill.SkillRegistry.require(existing.getKey());
                if (definition.rankPolicy().refundRule()
                        != com.mistaboom.essence_ascendance.skill.SkillRankPolicy.RefundRule.EXACT_PAID) {
                    throw new IllegalArgumentException("This skill does not permit rank refunds");
                }
            }
        }

        boolean changed =
                !investedEssence.equals(normalizedInvestments)
                        || !availableEssence.equals(normalizedAvailable)
                        || !ownedSkills.equals(normalizedOwnedSkills)
                        || !loadoutSelections.equals(normalizedLoadoutSelections)
                        || !currentTierId.equals(normalizedTierId);

        if (!changed) {
            return false;
        }

        investedEssence.clear();
        investedEssence.putAll(normalizedInvestments);

        availableEssence.clear();
        availableEssence.putAll(normalizedAvailable);

        ownedSkills.clear();
        ownedSkills.putAll(normalizedOwnedSkills);

        loadoutSelections.clear();
        loadoutSelections.putAll(normalizedLoadoutSelections);

        if (!currentTierId.equals(normalizedTierId)) attunement.chapter("");
        currentTierId = normalizedTierId;

        bumpRevision();
        return true;
    }


    /*
     * ============================================================
     * NBT SERIALIZATION
     * ============================================================
     */

    public CompoundTag save() {

        CompoundTag root =
                new CompoundTag();
        root.putUUID("projectile_life", projectileLife);
        root.putBoolean(DORMANT_GUIDEBOOK_RECEIVED_TAG, dormantGuidebookReceived);


        /*
         * Available Essence
         */
        CompoundTag availableTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, Long> entry :
                availableEssence.entrySet()) {

            availableTag.putLong(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(
                AVAILABLE_TAG,
                availableTag
        );


        /*
         * Crucible reservoir Essence
         */
        CompoundTag crucibleReservoirTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, Long> entry :
                crucibleReservoir.entrySet()) {

            crucibleReservoirTag.putLong(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(
                CRUCIBLE_RESERVOIR_TAG,
                crucibleReservoirTag
        );



        /*
         * Invested Essence
         */
        CompoundTag investedTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, Long> entry :
                investedEssence.entrySet()) {

            investedTag.putLong(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(
                INVESTED_TAG,
                investedTag
        );


        /*
         * Ascendance tier
         */
        root.putString(
                TIER_TAG,
                currentTierId.toString()
        );


        /*
         * Permanent milestone completion
         */
        CompoundTag milestoneTag =
                new CompoundTag();

        for (ResourceLocation milestoneId :
                completedMilestones) {

            milestoneTag.putBoolean(
                    milestoneId.toString(),
                    true
            );
        }

        root.put(
                COMPLETED_MILESTONES_TAG,
                milestoneTag
        );


        /*
         * Permanent owned skills and their exact original costs
         */
        CompoundTag ownedSkillsTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, SkillPurchase> entry :
                ownedSkills.entrySet()) {
            CompoundTag purchaseTag =
                    new CompoundTag();

            purchaseTag.putString(
                    SKILL_PAID_ESSENCE_TAG,
                    entry.getValue().essenceId().toString()
            );

            purchaseTag.putLongArray(
                    SKILL_RANK_COSTS_TAG,
                    entry.getValue().paidCosts().stream().mapToLong(Long::longValue).toArray()
            );

            ownedSkillsTag.put(
                    entry.getKey().toString(),
                    purchaseTag
            );
        }

        root.put(
                OWNED_SKILLS_TAG,
                ownedSkillsTag
        );


        /*
         * Free loadout / choice selections
         */
        CompoundTag loadoutTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, ResourceLocation> entry :
                loadoutSelections.entrySet()) {
            loadoutTag.putString(
                    entry.getKey().toString(),
                    entry.getValue().toString()
            );
        }

        root.put(
                LOADOUT_SELECTIONS_TAG,
                loadoutTag
        );


        root.put("category_attunement", attunement.save());
        root.put("vitality_damage", vitalityDamage.save());


        /*
         * Fractional resource-cost accounting
         */
        CompoundTag resourceCostCarryTag =
                new CompoundTag();

        for (Map.Entry<ResourceLocation, Double> entry :
                fractionalResourceCostCarry.entrySet()) {
            resourceCostCarryTag.putDouble(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }

        root.put(
                FRACTIONAL_RESOURCE_COST_CARRY_TAG,
                resourceCostCarryTag
        );

        CompoundTag cooldownTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Long> entry : abilityCooldowns.entrySet()) {
            if (entry.getValue() > 0) cooldownTag.putLong(entry.getKey().toString(), entry.getValue());
        }
        root.put(ABILITY_COOLDOWNS_TAG, cooldownTag);


        /*
         * Persisted optimistic-concurrency token for Nexus transactions
         */
        root.putLong(
                NEXUS_REVISION_TAG,
                nexusRevision
        );


        return root;
    }


    public static PlayerEssenceData load(
            CompoundTag root
    ) {

        PlayerEssenceData data =
                new PlayerEssenceData();
        if (root.hasUUID("projectile_life")) data.projectileLife = root.getUUID("projectile_life");
        data.dormantGuidebookReceived = root.getBoolean(DORMANT_GUIDEBOOK_RECEIVED_TAG);


        /*
         * ========================================================
         * NEXUS REVISION
         * ========================================================
         */

        if (root.contains(
                NEXUS_REVISION_TAG,
                Tag.TAG_LONG
        )) {
            data.nexusRevision =
                    Math.max(
                            0L,
                            root.getLong(NEXUS_REVISION_TAG)
                    );
        }


        /*
         * ========================================================
         * ASCENDANCE TIER
         * ========================================================
         */

        String savedTier =
                root.getString(
                        TIER_TAG
                );

        if (!savedTier.isBlank()) {

            ResourceLocation tierId =
                    ResourceLocation.tryParse(
                            savedTier
                    );

            if (tierId != null) {

                data.currentTierId =
                        tierId;

                if (AscendanceTierRegistry
                        .get(tierId)
                        .isEmpty()) {

                    EssenceAscendance.LOGGER.warn(
                            "Unknown Ascendance tier '{}' in saved player data. Preserving the ID and treating the player as Latent until the tier becomes available.",
                            tierId
                    );
                }

            } else {

                EssenceAscendance.LOGGER.warn(
                        "Invalid Ascendance tier ID '{}' in saved player data; defaulting to Latent",
                        savedTier
                );

                data.currentTierId =
                        AscendanceTiers.LATENT.id();
            }
        }


        /*
         * ========================================================
         * AVAILABLE ESSENCE
         * ========================================================
         */

        readLongMap(
                root.getCompound(
                        AVAILABLE_TAG
                ),
                data.availableEssence,
                id -> EssenceRegistry.get(id).isPresent(),
                "Essence"
        );


        /*
         * ========================================================
         * CRUCIBLE RESERVOIR
         * ========================================================
         *
         * Older saves do not contain this tag, so an absent compound
         * naturally loads as an empty reservoir.
         */

        readLongMap(
                root.getCompound(
                        CRUCIBLE_RESERVOIR_TAG
                ),
                data.crucibleReservoir,
                id -> EssenceRegistry.get(id).isPresent(),
                "Crucible Essence"
        );



        /*
         * ========================================================
         * INVESTED ESSENCE
         * ========================================================
         */

        readLongMap(
                root.getCompound(
                        INVESTED_TAG
                ),
                data.investedEssence,
                id -> EssenceStatRegistry.get(id).isPresent(),
                "stat investment"
        );


        /*
         * ========================================================
         * PERMANENT MILESTONES
         * ========================================================
         *
         * Older saves do not contain this tag, so check for it
         * explicitly.
         */

        if (root.contains(
                COMPLETED_MILESTONES_TAG,
                Tag.TAG_COMPOUND
        )) {

            CompoundTag milestoneTag =
                    root.getCompound(
                            COMPLETED_MILESTONES_TAG
                    );

            for (String key :
                    milestoneTag.getAllKeys()) {

                ResourceLocation milestoneId =
                        ResourceLocation.tryParse(
                                key
                        );

                if (milestoneId == null) {

                    EssenceAscendance.LOGGER.warn(
                            "Ignoring invalid milestone ID '{}' in Essence Ascendance player data",
                            key
                    );

                    continue;
                }

                if (milestoneTag.getBoolean(
                        key
                )) {

                    data.completedMilestones.add(
                            milestoneId
                    );
                }
            }
        }


        /*
         * ========================================================
         * PERMANENT OWNED SKILLS
         * ========================================================
         *
         * Development saves retain only skills that exist in the current
         * catalog. Removed/renamed identities are discarded rather than
         * aliased or migrated into a different skill.
         */

        boolean discardedCatalogSkill = false;

        if (root.contains(
                OWNED_SKILLS_TAG,
                Tag.TAG_COMPOUND
        )) {
            CompoundTag ownedSkillsTag =
                    root.getCompound(
                            OWNED_SKILLS_TAG
                    );

            for (String key :
                    ownedSkillsTag.getAllKeys()) {
                ResourceLocation skillId =
                        ResourceLocation.tryParse(
                                key
                        );

                if (skillId == null
                        || !ownedSkillsTag.contains(key, Tag.TAG_COMPOUND)) {
                    EssenceAscendance.LOGGER.warn(
                            "Ignoring malformed owned skill entry '{}' in Essence Ascendance player data",
                            key
                    );
                    continue;
                }

                if (SkillRegistry.get(skillId).isEmpty()) {
                    discardedCatalogSkill = true;
                    EssenceAscendance.LOGGER.warn(
                            "Discarding saved skill '{}' because it is not present in the current skill catalog",
                            skillId
                    );
                    continue;
                }

                CompoundTag purchaseTag =
                        ownedSkillsTag.getCompound(
                                key
                        );

                if (!purchaseTag.contains(
                        SKILL_PAID_ESSENCE_TAG,
                        Tag.TAG_STRING
                ) || !purchaseTag.contains(
                        SKILL_RANK_COSTS_TAG,
                        Tag.TAG_LONG_ARRAY
                )) {
                    EssenceAscendance.LOGGER.warn(
                            "Ignoring incomplete purchase receipt for owned skill '{}' in Essence Ascendance player data",
                            skillId
                    );
                    continue;
                }

                ResourceLocation paidEssenceId =
                        ResourceLocation.tryParse(
                                purchaseTag.getString(
                                        SKILL_PAID_ESSENCE_TAG
                                )
                        );

                long[] rankCosts = purchaseTag.getLongArray(SKILL_RANK_COSTS_TAG);
                if (paidEssenceId == null || rankCosts.length == 0
                        || rankCosts.length > SkillPurchase.MAX_RANKS) {
                    throw new IllegalArgumentException("Malformed rank receipt for " + skillId);
                }
                data.ownedSkills.put(skillId, new SkillPurchase(paidEssenceId,
                        java.util.Arrays.stream(rankCosts).boxed().toList()));
            }
        }

        /*
         * Only repair prerequisite chains when this load actually discarded a
         * removed/renamed catalog identity. Normal save/load is accounting-
         * lossless: historical rank receipts are not reinterpreted against
         * today's rank ceiling or used as a reason to rewrite ownership.
         */
        if (discardedCatalogSkill) {
            discardLoadedPrerequisiteOrphans(data);
        }


        /*
         * ========================================================
         * LOADOUT / CHOICE SELECTIONS
         * ========================================================
         */

        if (root.contains(
                LOADOUT_SELECTIONS_TAG,
                Tag.TAG_COMPOUND
        )) {
            CompoundTag loadoutTag =
                    root.getCompound(
                            LOADOUT_SELECTIONS_TAG
                    );

            for (String key :
                    loadoutTag.getAllKeys()) {
                ResourceLocation selectionId =
                        ResourceLocation.tryParse(
                                key
                        );

                ResourceLocation skillId =
                        ResourceLocation.tryParse(
                                loadoutTag.getString(
                                        key
                                )
                        );

                if (selectionId == null
                        || skillId == null) {
                    EssenceAscendance.LOGGER.warn(
                            "Ignoring malformed loadout selection '{}' in Essence Ascendance player data",
                            key
                    );
                    continue;
                }

                if (!validLoadedSelection(data, selectionId, skillId)) {
                    EssenceAscendance.LOGGER.warn(
                            "Discarding saved loadout selection '{} -> {}' because it is not valid in the current skill catalog",
                            selectionId,
                            skillId
                    );
                    continue;
                }

                data.loadoutSelections.put(
                        selectionId,
                        skillId
                );
            }
        }


        data.attunement = com.mistaboom.essence_ascendance.attunement.AttunementLedger.load(root.getCompound("category_attunement"));
        data.vitalityDamage = com.mistaboom.essence_ascendance.vitality.VitalityDamageLedger.load(root.getCompound("vitality_damage"));


        /*
         * ========================================================
         * FRACTIONAL RESOURCE-COST ACCOUNTING
         * ========================================================
         */

        if (root.contains(
                FRACTIONAL_RESOURCE_COST_CARRY_TAG,
                Tag.TAG_COMPOUND
        )) {
            CompoundTag carryTag = root.getCompound(
                    FRACTIONAL_RESOURCE_COST_CARRY_TAG
            );

            for (String key : carryTag.getAllKeys()) {
                ResourceLocation channelId = ResourceLocation.tryParse(key);
                double carry = carryTag.getDouble(key);

                if (channelId == null
                        || !Double.isFinite(carry)
                        || carry <= 0.0D
                        || carry >= 1.0D) {
                    EssenceAscendance.LOGGER.warn(
                            "Ignoring invalid fractional resource-cost carry '{}' in Essence Ascendance player data",
                            key
                    );
                    continue;
                }

                data.fractionalResourceCostCarry.put(
                        channelId,
                        carry
                );
            }
        }

        if (root.contains(ABILITY_COOLDOWNS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag cooldownTag = root.getCompound(ABILITY_COOLDOWNS_TAG);
            for (String key : cooldownTag.getAllKeys()) {
                ResourceLocation channelId = ResourceLocation.tryParse(key);
                long readyAt = cooldownTag.getLong(key);
                if (channelId == null || readyAt <= 0) {
                    EssenceAscendance.LOGGER.warn(
                            "Ignoring invalid ability cooldown '{}' in Essence Ascendance player data", key);
                    continue;
                }
                data.abilityCooldowns.put(channelId, readyAt);
            }
        }


        return data;
    }


    private static void discardLoadedPrerequisiteOrphans(
            PlayerEssenceData data
    ) {
        while (true) {
            Map<ResourceLocation, Integer> ownedRanks = data.getSkillRanks();
            Set<ResourceLocation> invalid = new LinkedHashSet<>();

            for (Map.Entry<ResourceLocation, Integer> entry : ownedRanks.entrySet()) {
                SkillDefinition skill = SkillRegistry.get(entry.getKey()).orElse(null);
                if (skill == null) {
                    continue;
                }

                /*
                 * A receipt may legitimately preserve more historical ranks
                 * than the current catalog exposes. Only the prerequisite
                 * gates that exist in today's catalog participate in orphan
                 * cleanup; the receipt itself remains untouched.
                 */
                int catalogRank = Math.min(entry.getValue(), skill.maximumRank());
                for (Map.Entry<ResourceLocation, Integer> prerequisite :
                        skill.prerequisiteRanks(catalogRank).entrySet()) {
                    if (ownedRanks.getOrDefault(prerequisite.getKey(), 0)
                            < prerequisite.getValue()) {
                        invalid.add(entry.getKey());
                        break;
                    }
                }
            }

            if (invalid.isEmpty()) {
                return;
            }

            boolean removedAny = false;
            for (ResourceLocation skillId : invalid) {
                if (data.ownedSkills.remove(skillId) == null) {
                    continue;
                }
                removedAny = true;
                EssenceAscendance.LOGGER.warn(
                        "Discarding saved skill '{}' because a removed/renamed catalog skill left its current prerequisite ownership incomplete",
                        skillId
                );
            }

            if (!removedAny) {
                return;
            }
        }
    }


    private static boolean validLoadedSelection(
            PlayerEssenceData data,
            ResourceLocation selectionId,
            ResourceLocation skillId
    ) {
        if (!data.ownedSkills.containsKey(skillId)) {
            return false;
        }

        var group = SkillRegistry.choiceGroup(selectionId);
        if (group.isPresent()) {
            return group.get().memberIds().contains(skillId);
        }

        SkillDefinition directControl = SkillRegistry.get(selectionId).orElse(null);
        if (directControl == null
                || !selectionId.equals(skillId)) {
            return false;
        }

        return directControl.activationPolicy() == SkillActivationPolicy.TOGGLE
                || directControl.activationPolicy() == SkillActivationPolicy.AUTOMATIC;
    }


    /*
     * ============================================================
     * NBT HELPERS
     * ============================================================
     */

    private static Map<ResourceLocation, Long> normalizeLongTargets(
            Map<ResourceLocation, Long> source,
            String valueKind
    ) {
        Objects.requireNonNull(
                source,
                "Target " + valueKind + " map cannot be null"
        );

        Map<ResourceLocation, Long> copy =
                new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, Long> entry :
                source.entrySet()) {
            ResourceLocation id =
                    Objects.requireNonNull(
                            entry.getKey(),
                            "Target " + valueKind + " ID cannot be null"
                    );

            Long value =
                    Objects.requireNonNull(
                            entry.getValue(),
                            "Target " + valueKind + " value cannot be null"
                    );

            if (value < 0L) {
                throw new IllegalArgumentException(
                        "Target " + valueKind + " cannot be negative"
                );
            }

            if (value > 0L) {
                copy.put(
                        id,
                        value
                );
            }
        }

        return copy;
    }


    private static Map<ResourceLocation, SkillPurchase> copySkillPurchases(
            Map<ResourceLocation, SkillPurchase> source
    ) {
        Objects.requireNonNull(
                source,
                "Target owned-skill map cannot be null"
        );

        Map<ResourceLocation, SkillPurchase> copy =
                new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, SkillPurchase> entry :
                source.entrySet()) {
            copy.put(
                    Objects.requireNonNull(
                            entry.getKey(),
                            "Owned skill ID cannot be null"
                    ),
                    Objects.requireNonNull(
                            entry.getValue(),
                            "Skill purchase receipt cannot be null"
                    )
            );
        }

        return copy;
    }


    private static Map<ResourceLocation, ResourceLocation> copyLoadoutSelections(
            Map<ResourceLocation, ResourceLocation> source
    ) {
        Objects.requireNonNull(
                source,
                "Target loadout selection map cannot be null"
        );

        Map<ResourceLocation, ResourceLocation> copy =
                new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, ResourceLocation> entry :
                source.entrySet()) {
            copy.put(
                    Objects.requireNonNull(
                            entry.getKey(),
                            "Loadout selection ID cannot be null"
                    ),
                    Objects.requireNonNull(
                            entry.getValue(),
                            "Selected skill ID cannot be null"
                    )
            );
        }

        return copy;
    }







    private static void readLongMap(
            CompoundTag tag,
            Map<ResourceLocation, Long> target,
            Predicate<ResourceLocation> supportedId,
            String valueKind
    ) {

        for (String key :
                tag.getAllKeys()) {

            ResourceLocation id =
                    ResourceLocation.tryParse(
                            key
                    );

            if (id == null) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid ResourceLocation '{}' in Essence Ascendance player data",
                        key
                );

                continue;
            }

            if (!supportedId.test(id)) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring unknown/retired {} ID '{}' in Essence Ascendance player data; no conversion or refund was applied",
                        valueKind,
                        id
                );
                continue;
            }

            long value =
                    tag.getLong(
                            key
                    );

            if (value > 0) {

                target.put(
                        id,
                        value
                );
            }
        }
    }
}
