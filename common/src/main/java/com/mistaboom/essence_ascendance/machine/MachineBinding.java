package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.team.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;

/** Persistent infrastructure identity, independent of provenance, operator and recipient. No roster copy. */
public final class MachineBinding {
    private UUID placer;
    private UUID personalOwner;
    private UUID pendingPlacementOwner;
    private PoolId pool;
    private UUID operator;
    private UUID recipient;
    private UUID automationRecipient;
    private boolean paused;
    private long revision;
    private long ownershipRosterRevision = -1;
    private boolean pickupClaimed;
    boolean claimPickup() { if (pickupClaimed) return false; pickupClaimed = true; return true; }
    public long revision() { return revision; }
    public UUID placer() { return placer; }
    public UUID personalOwner() { return personalOwner; }
    public PoolId pool() { return pool; }
    public UUID operator() { return operator; }
    public UUID recipient() { return recipient; }
    public UUID automationRecipient() { return automationRecipient; }
    /** Current server-held workpiece policy; never an authenticated automation actor. */
    public void workpieceRecipient(UUID target) { recipient = target; }
    public boolean configureAutomationRecipient(EssenceSavedData saved, UUID actor, UUID target) {
        var current = resolve(saved);
        if (!allows(saved, actor, MachineAction.CONFIGURE) || current == null
                || target != null && !current.members().contains(target)) return false;
        long next = Math.incrementExact(revision);
        automationRecipient = target; revision = next; return true;
    }
    public boolean automationRecipientValid(EssenceSavedData saved) {
        var current = resolve(saved);
        return automationRecipient != null && current != null && current.members().contains(automationRecipient);
    }
    public boolean paused() { return paused; }
    public boolean initialize(EssenceSavedData saved, UUID actor) {
        return pool == null && place(saved, actor);
    }
    /** Called only by physical placement, including a previously owned machine item. */
    public boolean place(EssenceSavedData saved, UUID actor) {
        MembershipSnapshot current = saved.resolvePool(actor).snapshot().orElse(null);
        revision = Math.incrementExact(revision);
        placer = actor; personalOwner = current != null && current.isSoloFor(actor) ? actor : null;
        pendingPlacementOwner = current == null ? actor : null;
        pool = current == null ? null : current.pool(); ownershipRosterRevision = saved.reservoirs().rosterRevision();
        paused = false; operator = null; recipient = null; automationRecipient = null; pickupClaimed = false;
        return true;
    }
    public MembershipSnapshot resolve(EssenceSavedData saved) {
        return saved.resolveMachinePool(pool, personalOwner);
    }
    public boolean allows(EssenceSavedData saved, UUID actor, MachineAction action) {
        if (pool == null) return false; // Opening/using a machine never claims it.
        var actorMembership = saved.resolvePool(actor).snapshot().orElse(null);
        MembershipSnapshot current = resolve(saved);
        return actorMembership != null && pool.equals(actorMembership.pool()) && current != null && current.allows(actor, action.capability)
                && (action != MachineAction.SPEND && action != MachineAction.CONTRIBUTE || !paused);
    }
    public boolean operational(EssenceSavedData saved) { return !paused && resolve(saved) != null; }
    public boolean canAct(EssenceSavedData saved, MachineAction action) {
        var current = resolve(saved);
        return !paused && current != null && current.members().stream().anyMatch(m -> current.allows(m, action.capability));
    }
    public UUID fundingActor(EssenceSavedData saved) {
        MembershipSnapshot current = resolve(saved);
        if (paused || current == null) return null;
        return current.members().stream().filter(m -> current.allows(m, PoolCapability.SPEND)).findFirst().orElse(null);
    }
    public boolean samePool(EssenceSavedData saved, MachineBinding other) {
        return pool != null && pool.equals(other.pool) && canAct(saved, MachineAction.CONTRIBUTE) && other.canAct(saved, MachineAction.SPEND);
    }
    /** Replay persisted membership events so unloaded infrastructure follows the same ownership rules. */
    public boolean refresh(EssenceSavedData saved) {
        boolean changed = false;
        if (pendingPlacementOwner != null) {
            var placement = saved.resolvePool(pendingPlacementOwner).snapshot().orElse(null);
            if (placement == null) return false;
            pool = placement.pool(); personalOwner = placement.isSoloFor(pendingPlacementOwner) ? pendingPlacementOwner : null;
            pendingPlacementOwner = null; ownershipRosterRevision = saved.reservoirs().rosterRevision(); changed = true;
        }
        if (pool == null || pool.authority() != saved.membershipAuthority()) return false;
        while (true) {
            if (personalOwner != null) {
                // Resolving the owner also probes optional providers before looking at committed receipts.
                MembershipSnapshot current = saved.resolvePool(personalOwner).snapshot().orElse(null);
                if (current == null) break;
                var join = saved.reservoirs().machineJoin(personalOwner, ownershipRosterRevision);
                if (join != null) {
                    pool = join.pool(); personalOwner = null; ownershipRosterRevision = join.revision(); changed = true;
                    continue;
                }
            } else if (saved.reservoirs().isDissolved(pool)) {
                UUID successor = saved.reservoirs().dissolutionRecipient(pool);
                if (successor == null) break;
                long dissolvedAt = saved.reservoirs().dissolutionRevision(pool);
                personalOwner = successor; pool = saved.personalPool(successor).pool();
                // Provider publications may disband the old team and join the successor's next team together.
                // Never replay receipts preceding the already consumed ownership event.
                ownershipRosterRevision = Math.max(ownershipRosterRevision, dissolvedAt - 1);
                changed = true;
                continue;
            }
            break;
        }
        if (paused && resolve(saved) != null) { paused = false; changed = true; }
        if (changed) {
            revision = Math.incrementExact(revision);
            paused = false; operator = null; recipient = null; automationRecipient = null;
        }
        return changed;
    }
    /** Trusted administrator recovery; the authenticated world entry point owns the permission check. */
    void recover(EssenceSavedData saved, UUID owner) {
        var current = saved.resolvePool(owner).requireReady();
        revision = Math.incrementExact(revision);
        pool = current.pool(); personalOwner = current.isSoloFor(owner) ? owner : null; pendingPlacementOwner = null;
        ownershipRosterRevision = saved.reservoirs().rosterRevision();
        paused = false; operator = null; recipient = null; automationRecipient = null;
        if (placer == null) placer = owner;
    }
    public boolean start(EssenceSavedData saved, UUID actor, UUID equipmentRecipient) {
        return start(saved, actor, equipmentRecipient, null);
    }
    /** The concrete equipment policy supplies an existing soulbound identity from the server-held workpiece. */
    public boolean start(EssenceSavedData saved, UUID actor, UUID equipmentRecipient, UUID boundRecipient) {
        var current = resolve(saved);
        if (current == null || !current.allows(actor, PoolCapability.CONFIGURE) || !current.allows(actor, PoolCapability.SPEND)) return false;
        if (boundRecipient == null && equipmentRecipient != null && !current.members().contains(equipmentRecipient)) return false;
        paused = false; operator = actor; recipient = boundRecipient == null ? equipmentRecipient : boundRecipient;
        return true;
    }
    public boolean operatorValid(EssenceSavedData saved) {
        return operator != null && allows(saved, operator, MachineAction.SPEND);
    }
    public boolean recipientValid(EssenceSavedData saved) {
        var current = resolve(saved);
        return recipient != null && current != null && current.members().contains(recipient);
    }
    public void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        if (pool != null) tag.put("pool", pool.save());
        if (placer != null) tag.putUUID("placer", placer);
        if (personalOwner != null) tag.putUUID("personal_owner", personalOwner);
        if (pendingPlacementOwner != null) tag.putUUID("pending_placement_owner", pendingPlacementOwner);
        if (operator != null) tag.putUUID("operator", operator);
        if (recipient != null) tag.putUUID("recipient", recipient);
        if (automationRecipient != null) tag.putUUID("automation_recipient", automationRecipient);
        tag.putLong("ownership_roster_revision", ownershipRosterRevision);
        tag.putLong("revision", revision);
        tag.putBoolean("paused", paused); root.put("machine_binding", tag);
    }
    public void load(CompoundTag root) {
        CompoundTag tag = root.getCompound("machine_binding");
        pool = tag.contains("pool", Tag.TAG_COMPOUND) ? PoolId.load(tag.getCompound("pool")) : null;
        placer = tag.hasUUID("placer") ? tag.getUUID("placer") : null;
        personalOwner = tag.hasUUID("personal_owner") ? tag.getUUID("personal_owner") : null;
        pendingPlacementOwner = tag.hasUUID("pending_placement_owner") ? tag.getUUID("pending_placement_owner") : null;
        operator = tag.hasUUID("operator") ? tag.getUUID("operator") : null;
        recipient = tag.hasUUID("recipient") ? tag.getUUID("recipient") : null;
        automationRecipient = tag.hasUUID("automation_recipient") ? tag.getUUID("automation_recipient") : null;
        if (root.contains("machine_binding") && !tag.contains("ownership_roster_revision", Tag.TAG_LONG))
            throw new IllegalStateException("Missing machine ownership revision");
        ownershipRosterRevision = root.contains("machine_binding") ? tag.getLong("ownership_roster_revision") : -1;
        paused = tag.getBoolean("paused");
        revision = tag.getLong("revision");
        if (ownershipRosterRevision < -1 || revision < 0 || pool != null && (revision < 1 || ownershipRosterRevision < 0)) throw new IllegalStateException("Invalid machine binding revision");
        if (pendingPlacementOwner != null && (pool != null || personalOwner != null || !pendingPlacementOwner.equals(placer)))
            throw new IllegalStateException("Invalid pending machine placement");
        if (pool != null && (placer == null || (pool.kind() == PoolId.Kind.SOLO) != (personalOwner != null)))
            throw new IllegalStateException("Invalid machine binding");
    }
}
