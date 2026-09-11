package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
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

    private static final String AVAILABLE_TAG =
            "available";

    private static final String INVESTED_TAG =
            "invested";

    private static final String CRUCIBLE_RESERVOIR_TAG =
            "crucible_reservoir";


    private static final String TIER_TAG =
            "tier";

    private static final String COMPLETED_MILESTONES_TAG =
            "completed_milestones";

    private static final String OWNED_SKILLS_TAG =
            "owned_skills";

    private static final String SKILL_PAID_ESSENCE_TAG =
            "paid_essence";

    private static final String SKILL_PAID_COST_TAG =
            "paid_cost";

    private static final String LOADOUT_SELECTIONS_TAG =
            "loadout_selections";

    private static final String COMPLETED_ATTUNEMENTS_TAG =
            "completed_attunements";

    private static final String NEXUS_REVISION_TAG =
            "nexus_revision";


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

    private final Set<ResourceLocation> completedAttunements =
            new LinkedHashSet<>();


    private ResourceLocation currentTierId =
            AscendanceTiers.DORMANT.id();


    /*
     * Persistent authoritative Nexus-state revision. Every mutation visible
     * to the Nexus increments this value, allowing stale client transactions
     * to be rejected across reconnects and server restarts as well as within a
     * single session.
     */
    private long nexusRevision = 0L;


    public long revision() {
        return nexusRevision;
    }


    public long nexusRevision() {
        return nexusRevision;
    }


    private void bumpRevision() {
        /* Preserve monotonic comparisons used by the legacy allocation path. */
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


    /**
     * Applies a prevalidated allocation transaction in one mutation revision.
     *
     * The caller is responsible for validating tier caps and Essence budgets
     * before invoking this method. Keeping the map replacement here lets a
     * multi-stat Nexus allocation become visible atomically to synchronization
     * and save-data consumers instead of as a sequence of partial investments.
     */
    public boolean applyAllocationTargets(
            Map<StatDefinition, Long> targetInvestments,
            Map<EssenceDefinition, Long> targetAvailable
    ) {
        if (targetInvestments == null || targetAvailable == null) {
            throw new IllegalArgumentException(
                    "Allocation target maps cannot be null"
            );
        }

        boolean changed = false;

        for (Map.Entry<StatDefinition, Long> entry :
                targetInvestments.entrySet()) {
            StatDefinition stat = entry.getKey();
            Long amount = entry.getValue();

            if (stat == null || amount == null || amount < 0L) {
                throw new IllegalArgumentException(
                        "Invalid stat allocation target"
                );
            }

            if (getInvested(stat) != amount) {
                changed = true;
            }
        }

        for (Map.Entry<EssenceDefinition, Long> entry :
                targetAvailable.entrySet()) {
            EssenceDefinition essence = entry.getKey();
            Long amount = entry.getValue();

            if (essence == null || amount == null || amount < 0L) {
                throw new IllegalArgumentException(
                        "Invalid available Essence target"
                );
            }

            if (getAvailable(essence) != amount) {
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        for (Map.Entry<EssenceDefinition, Long> entry :
                targetAvailable.entrySet()) {
            EssenceDefinition essence = entry.getKey();
            long amount = entry.getValue();

            if (amount == 0L) {
                availableEssence.remove(essence.id());
            } else {
                availableEssence.put(essence.id(), amount);
            }
        }

        for (Map.Entry<StatDefinition, Long> entry :
                targetInvestments.entrySet()) {
            StatDefinition stat = entry.getKey();
            long amount = entry.getValue();

            if (amount == 0L) {
                investedEssence.remove(stat.id());
            } else {
                investedEssence.put(stat.id(), amount);
            }
        }

        bumpRevision();
        return true;
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
                        AscendanceTiers.DORMANT
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
        if (removed == 0 && loadoutSelections.isEmpty()) return 0;
        ownedSkills.clear();
        loadoutSelections.clear();
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
     * PERMANENT PLAYER ATTUNEMENTS
     * ============================================================
     */

    public boolean hasAttunement(
            ResourceLocation attunementId
    ) {
        return completedAttunements.contains(
                Objects.requireNonNull(
                        attunementId,
                        "Attunement ID cannot be null"
                )
        );
    }


    public Set<ResourceLocation> getCompletedAttunements() {
        return Collections.unmodifiableSet(
                completedAttunements
        );
    }


    public boolean grantAttunement(
            ResourceLocation attunementId
    ) {
        Objects.requireNonNull(
                attunementId,
                "Attunement ID cannot be null"
        );

        boolean changed =
                completedAttunements.add(
                        attunementId
                );

        if (changed) {
            bumpRevision();
        }

        return changed;
    }


    public boolean revokeAttunement(
            ResourceLocation attunementId
    ) {
        Objects.requireNonNull(
                attunementId,
                "Attunement ID cannot be null"
        );

        boolean changed =
                completedAttunements.remove(
                        attunementId
                );

        if (changed) {
            bumpRevision();
        }

        return changed;
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

        for (Map.Entry<ResourceLocation, SkillPurchase> existing :
                ownedSkills.entrySet()) {
            if (!existing.getValue().equals(
                    normalizedOwnedSkills.get(existing.getKey())
            )) {
                throw new IllegalArgumentException(
                        "A Nexus transaction cannot remove or rewrite permanent skill purchase '"
                                + existing.getKey()
                                + "'"
                );
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

            purchaseTag.putLong(
                    SKILL_PAID_COST_TAG,
                    entry.getValue().paidCost()
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


        /*
         * Permanent Player Attunements
         */
        writeIdSet(
                root,
                COMPLETED_ATTUNEMENTS_TAG,
                completedAttunements
        );


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
                            "Unknown Ascendance tier '{}' in saved player data. Preserving the ID and treating the player as Dormant until the tier becomes available.",
                            tierId
                    );
                }

            } else {

                EssenceAscendance.LOGGER.warn(
                        "Invalid Ascendance tier ID '{}' in saved player data; defaulting to Dormant",
                        savedTier
                );

                data.currentTierId =
                        AscendanceTiers.DORMANT.id();
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
         * Do not consult the current skill registry here. A temporarily
         * missing or retired valid ID must round-trip untouched.
         */

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

                CompoundTag purchaseTag =
                        ownedSkillsTag.getCompound(
                                key
                        );

                if (!purchaseTag.contains(
                        SKILL_PAID_ESSENCE_TAG,
                        Tag.TAG_STRING
                ) || !purchaseTag.contains(
                        SKILL_PAID_COST_TAG,
                        Tag.TAG_LONG
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

                long paidCost =
                        purchaseTag.getLong(
                                SKILL_PAID_COST_TAG
                        );

                if (paidEssenceId == null
                        || paidCost < 0L) {
                    EssenceAscendance.LOGGER.warn(
                            "Ignoring malformed purchase receipt for owned skill '{}' in Essence Ascendance player data",
                            skillId
                    );
                    continue;
                }

                data.ownedSkills.put(
                        skillId,
                        new SkillPurchase(
                                paidEssenceId,
                                paidCost
                        )
                );
            }
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

                data.loadoutSelections.put(
                        selectionId,
                        skillId
                );
            }
        }


        /*
         * ========================================================
         * PERMANENT PLAYER ATTUNEMENTS
         * ========================================================
         */

        readIdSet(
                root,
                COMPLETED_ATTUNEMENTS_TAG,
                data.completedAttunements,
                "Player Attunement"
        );


        return data;
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


    private static void writeIdSet(
            CompoundTag root,
            String tagName,
            Set<ResourceLocation> values
    ) {
        CompoundTag valuesTag =
                new CompoundTag();

        for (ResourceLocation id :
                values) {
            valuesTag.putBoolean(
                    id.toString(),
                    true
            );
        }

        root.put(
                tagName,
                valuesTag
        );
    }


    private static void readIdSet(
            CompoundTag root,
            String tagName,
            Set<ResourceLocation> target,
            String valueKind
    ) {
        if (!root.contains(
                tagName,
                Tag.TAG_COMPOUND
        )) {
            return;
        }

        CompoundTag valuesTag =
                root.getCompound(
                        tagName
                );

        for (String key :
                valuesTag.getAllKeys()) {
            ResourceLocation id =
                    ResourceLocation.tryParse(
                            key
                    );

            if (id == null) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid {} ID '{}' in Essence Ascendance player data",
                        valueKind,
                        key
                );
                continue;
            }

            if (valuesTag.getBoolean(key)) {
                target.add(
                        id
                );
            }
        }
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
