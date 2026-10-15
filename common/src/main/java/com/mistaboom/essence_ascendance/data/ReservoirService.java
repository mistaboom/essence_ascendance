package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.team.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.*;

/** Server-thread transaction boundary. Only player records hold money; views are disposable.
 * Roster receipts, remainder cursors, whole amounts and carry share EssenceSavedData's save.
 * Machine inventory lives in chunk saves: this is runtime atomicity, NOT cross-file crash atomicity.
 */
public final class ReservoirService {
    public static final BigInteger SCALE = BigInteger.valueOf(FractionalAmountService.SCALE);
    private static final BigInteger MAX_SHARE = BigInteger.valueOf(Long.MAX_VALUE).multiply(SCALE)
            .add(SCALE.subtract(BigInteger.ONE));
    private final EssenceSavedData saved;
    private final Thread thread = Thread.currentThread();
    private Map<PoolId, MembershipSnapshot> teams = new HashMap<>();
    private Map<UUID, MembershipSnapshot> memberIndex = new HashMap<>();
    private final Map<PoolId, Map<ResourceLocation, Integer>> cursors = new HashMap<>();
    private final Map<PoolId, View> views = new HashMap<>();
    private final Map<PoolId, Optional<UUID>> dissolved = new HashMap<>();
    /** Durable ownership transitions for machines whose chunks are unloaded during membership changes. */
    public record MachineJoin(long revision, PoolId pool) { }
    private final Map<UUID, List<MachineJoin>> machineJoins = new HashMap<>();
    private final Map<PoolId, Long> dissolutionRevisions = new HashMap<>();
    public MachineJoin machineJoin(UUID owner, long afterRevision) {
        checkThread();
        return machineJoins.getOrDefault(owner, List.of()).stream().filter(j -> j.revision() > afterRevision && j.pool().authority() == saved.membershipAuthority()).findFirst().orElse(null);
    }
    public long dissolutionRevision(PoolId pool) { checkThread(); return dissolutionRevisions.getOrDefault(pool, 0L); }
    private long rosterRevision;
    private long revision;
    private boolean inTransaction;
    private ProviderReadiness readiness = new ProviderReadiness(ProviderReadiness.Status.AVAILABLE,
            "stage-2", "Saved native membership ready");

    ReservoirService(EssenceSavedData saved) { this.saved = saved; }
    void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("Reservoir requires the server thread");
        if (inTransaction) throw new IllegalStateException("Reentrant reservoir transaction");
    }
    MembershipResolution teamResolution(UUID member) {
        checkThread();
        // Unknown membership can also hide a join by a previously solo player. Never fall back.
        if (!MembershipAuthority.ready(readiness)) return new MembershipResolution(readiness, Optional.empty());
        MembershipSnapshot team = memberIndex.get(member);
        return team == null ? null : new MembershipResolution(readiness, Optional.of(team));
    }
    public long revision() { checkThread(); return revision; }
    public long rosterRevision() { checkThread(); return rosterRevision; }
    public Collection<MembershipSnapshot> settledTeams() { checkThread(); return List.copyOf(teams.values()); }
    public MembershipSnapshot settledTeam(UUID member) { checkThread(); return memberIndex.get(member); }
    public MembershipSnapshot settledPool(PoolId id) { checkThread(); return teams.get(id); }
    public boolean isDissolved(PoolId id) { checkThread(); return dissolved.containsKey(id); }
    public UUID dissolutionRecipient(PoolId id) { checkThread(); return dissolved.getOrDefault(id, Optional.empty()).orElse(null); }
    public ProviderReadiness readiness() { checkThread(); return readiness; }
    public void pause(ProviderReadiness reason) {
        checkThread();
        if (MembershipAuthority.ready(reason)) throw new IllegalArgumentException("Pause needs unresolved evidence");
        readiness = Objects.requireNonNull(reason);
    }

    public record View(PoolId pool, long membershipRevision, long reservoirRevision,
                       Map<ResourceLocation, BigInteger> units) {
        public View { units = Map.copyOf(units); }
        public BigInteger units(ResourceLocation type) { return units.getOrDefault(type, BigInteger.ZERO); }
        public BigInteger whole(ResourceLocation type) { return units(type).divide(SCALE); }
    }

    public View read(UUID member) {
        checkThread();
        MembershipSnapshot pool = saved.resolvePool(member).requireReady();
        return views.computeIfAbsent(pool.pool(), ignored -> view(pool));
    }
    private View view(MembershipSnapshot pool) {
        return new View(pool.pool(), pool.membershipRevision(), revision, aggregate(pool.members()));
    }
    private Map<ResourceLocation, BigInteger> aggregate(List<UUID> members) {
        Map<ResourceLocation, BigInteger> total = new LinkedHashMap<>();
        for (var type : EssenceTypes.ORDERED) {
            BigInteger value = BigInteger.ZERO;
            for (UUID member : members) {
                PlayerEssenceData record = saved.existingPlayerData(member);
                if (record != null) value = value.add(record.reservoirUnits(type.id()));
            }
            total.put(type.id(), value);
        }
        return total;
    }

    /** A participant must restore its EXACT inventory/job snapshot even after partial apply failure.
     * Callbacks may only mutate their local machine; they cannot call this service or save world data.
     */
    public interface Mutation {
        boolean apply();
        void rollback();
    }
    public static final Mutation NONE = new Mutation() {
        public boolean apply() { return true; }
        public void rollback() { }
    };

    public boolean credit(UUID actor, Map<ResourceLocation, BigInteger> units, Mutation mutation) {
        validateAmounts(units);
        return transact(actor, units, null, Map.of(), mutation, false);
    }
    public boolean debit(UUID actor, Map<ResourceLocation, Long> costs, Mutation mutation) {
        return transact(actor, wholeUnits(costs, true), null, Map.of(), mutation, false);
    }
    public boolean channel(UUID recipient, Map<ResourceLocation, Long> amounts) {
        return channel(recipient, amounts, NONE);
    }
    public boolean channel(UUID recipient, Map<ResourceLocation, Long> amounts, Mutation validation) {
        wholeUnits(amounts, false); // validate all types before touching personal currency
        return transact(recipient, wholeUnits(amounts, true), recipient, amounts, validation, false);
    }
    public boolean equalize(UUID actor) {
        return transact(actor, Map.of(), null, Map.of(), NONE, false);
    }
    /** Explicit operator operation: values replace aggregate amounts, including fractional carry. */
    public boolean administer(UUID member, Map<ResourceLocation, BigInteger> replacement) {
        validateAmounts(replacement);
        return transact(member, replacement, null, Map.of(), NONE, true);
    }
    public static Map<ResourceLocation, BigInteger> wholeUnits(Map<ResourceLocation, Long> values, boolean negative) {
        Map<ResourceLocation, BigInteger> result = new LinkedHashMap<>();
        values.forEach((type, amount) -> {
            if (amount == null || amount < 0) throw new IllegalArgumentException("Negative reservoir amount");
            result.put(type, BigInteger.valueOf(amount).multiply(SCALE).multiply(BigInteger.valueOf(negative ? -1 : 1)));
        });
        validateTypes(result.keySet());
        return result;
    }
    private static void validateTypes(Set<ResourceLocation> types) {
        for (ResourceLocation type : types)
            if (EssenceTypes.ORDERED.stream().noneMatch(e -> e.id().equals(type)))
                throw new IllegalArgumentException("Unsupported reservoir type " + type);
    }
    private static void validateAmounts(Map<ResourceLocation, BigInteger> values) {
        validateTypes(values.keySet());
        for (BigInteger amount : values.values())
            if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Negative reservoir amount");
    }

    private boolean transact(UUID actor, Map<ResourceLocation, BigInteger> change, UUID recipient,
                             Map<ResourceLocation, Long> channel, Mutation mutation, boolean admin) {
        checkThread();
        Objects.requireNonNull(mutation);
        MembershipSnapshot pool = saved.resolvePool(actor).requireReady();
        if (!admin && !pool.allows(actor, PoolCapability.SPEND)) throw new IllegalStateException("Reservoir spend denied");
        Map<ResourceLocation, BigInteger> total = new LinkedHashMap<>(read(actor).units());
        for (var entry : change.entrySet()) {
            BigInteger value = admin ? entry.getValue() : total.get(entry.getKey()).add(entry.getValue());
            if (value.signum() < 0) return false;
            total.put(entry.getKey(), value);
        }
        Map<ResourceLocation, Long> available = new LinkedHashMap<>();
        if (recipient != null) {
            PlayerEssenceData record = saved.existingPlayerData(recipient);
            for (var entry : channel.entrySet()) available.put(entry.getKey(), Math.addExact(
                    record == null ? 0 : record.getAvailable(entry.getKey()), entry.getValue()));
            if (record != null && !channel.isEmpty()) Math.addExact(record.nexusRevision(), channel.values().stream().filter(v -> v > 0).count());
        }
        Plan plan = new Plan();
        plan.equalize(pool, total, false);
        boolean changed = !plan.shares.isEmpty() || available.entrySet().stream().anyMatch(e ->
                saved.existingPlayerData(recipient) == null || saved.existingPlayerData(recipient).getAvailable(e.getKey()) != e.getValue());
        long nextRevision = changed ? Math.incrementExact(revision) : revision;
        inTransaction = true;
        try {
            boolean applied;
            try {
                applied = mutation.apply();
            } catch (RuntimeException failure) {
                try { mutation.rollback(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
            if (!applied) { mutation.rollback(); return false; }
            // Everything that can reject (including revisions and representability) precedes apply.
            plan.commit();
            if (recipient != null) available.forEach((type, value) -> saved.getPlayerData(recipient)
                    .setAvailable(EssenceRegistry.get(type).orElseThrow(), value));
            revision = nextRevision;
            if (changed) { views.put(pool.pool(), view(pool)); saved.setDirty(); }
            return true;
        } finally { inTransaction = false; }
    }

    private final class Plan {
        private final Map<UUID, Map<ResourceLocation, BigInteger>> shares = new LinkedHashMap<>();
        private final Map<PoolId, Map<ResourceLocation, Integer>> nextCursors = new HashMap<>();
        void equalize(MembershipSnapshot pool, Map<ResourceLocation, BigInteger> total, boolean rosterChanged) {
            int count = pool.members().size();
            Map<ResourceLocation, BigInteger> originalTotal = aggregate(pool.members());
            Map<ResourceLocation, Integer> next = new HashMap<>(cursors.getOrDefault(pool.pool(), Map.of()));
            for (var entry : total.entrySet()) {
                ResourceLocation type = entry.getKey();
                BigInteger[] split = entry.getValue().divideAndRemainder(BigInteger.valueOf(count));
                int extra = split[1].intValueExact();
                if (split[0].add(extra > 0 ? BigInteger.ONE : BigInteger.ZERO).compareTo(MAX_SHARE) > 0)
                    throw new ArithmeticException("Reservoir share overflow");
                // No-op equalization/read/replay preserves existing remainder owners and cursor.
                boolean alreadyEqual = !rosterChanged;
                for (UUID member : pool.members()) {
                    PlayerEssenceData record = saved.existingPlayerData(member);
                    BigInteger current = record == null ? BigInteger.ZERO : record.reservoirUnits(type);
                    if (!current.equals(split[0]) && !(extra > 0 && current.equals(split[0].add(BigInteger.ONE)))) alreadyEqual = false;
                }
                if (alreadyEqual && originalTotal.get(type).equals(entry.getValue())) continue;
                int start = Math.floorMod(next.getOrDefault(type, 0), count);
                for (int i = 0; i < count; i++) {
                    UUID member = pool.members().get(i);
                    BigInteger amount = split[0].add(Math.floorMod(i - start, count) < extra ? BigInteger.ONE : BigInteger.ZERO);
                    PlayerEssenceData record = saved.existingPlayerData(member);
                    BigInteger current = record == null ? BigInteger.ZERO : record.reservoirUnits(type);
                    if (!current.equals(amount)) shares.computeIfAbsent(member, ignored -> new LinkedHashMap<>()).put(type, amount);
                }
                next.put(type, (int) (((long) start + extra) % count));
            }
            for (UUID member : pool.members()) {
                PlayerEssenceData record = saved.existingPlayerData(member);
                if (record == null) shares.computeIfAbsent(member, ignored -> new LinkedHashMap<>());
                else if (shares.containsKey(member)) Math.incrementExact(record.reservoirRevision());
            }
            if (!next.equals(cursors.getOrDefault(pool.pool(), Map.of()))) nextCursors.put(pool.pool(), next);
        }
        void commit() {
            shares.forEach((member, amounts) -> saved.getPlayerData(member).applyReservoir(amounts));
            cursors.putAll(nextCursors);
        }
    }

    /** ONE settled, complete authoritative team-roster batch, never an individual provider event.
     * Omissions are confirmed disbands; unready evidence is inert. The source revision must be
     * durable and monotonic. Authority switches require a separate explicit boundary.
     */
    public boolean reconcile(ProviderReadiness readiness, long sourceRevision, Collection<MembershipSnapshot> settledTeams) {
        return reconcile(readiness, sourceRevision, settledTeams, saved.membershipAuthority(), () -> { }, false);
    }

    /** Explicit administrator boundary uses the same preflight, equalization and commit as normal joins. */
    public void activateAuthority(MembershipAuthority authority, ProviderReadiness ready,
                                  Collection<MembershipSnapshot> teams, Runnable publishAuthority, boolean preview) {
        if (!MembershipAuthority.ready(ready)) throw new IllegalStateException("Target authority is unavailable");
        reconcile(ready, Math.incrementExact(rosterRevision), teams, authority, publishAuthority, preview);
    }

    private boolean reconcile(ProviderReadiness readiness, long sourceRevision, Collection<MembershipSnapshot> settledTeams,
                              MembershipAuthority authority, Runnable publishAuthority, boolean preview) {
        checkThread();
        if (!MembershipAuthority.ready(readiness)) {
            this.readiness = Objects.requireNonNull(readiness);
            return false;
        }
        if (sourceRevision < 1) throw new IllegalArgumentException("Invalid settled roster revision");
        Map<PoolId, MembershipSnapshot> next = new HashMap<>();
        Map<UUID, MembershipSnapshot> index = new HashMap<>();
        for (MembershipSnapshot pool : settledTeams) {
            if (pool.pool().authority() != authority || pool.pool().kind() != PoolId.Kind.TEAM)
                throw new IllegalArgumentException("Roster does not belong to the established authority");
            if (next.put(pool.pool(), pool) != null) throw new IllegalArgumentException("Duplicate pool");
            for (UUID member : pool.members()) if (index.put(member, pool) != null)
                throw new IllegalArgumentException("UUID in multiple pools");
        }
        if (sourceRevision < rosterRevision) return false;
        if (sourceRevision == rosterRevision) {
            if (!next.equals(teams)) throw new IllegalStateException("Conflicting settled revision");
            this.readiness = readiness;
            return false;
        }
        Plan plan = new Plan();
        for (MembershipSnapshot pool : next.values()) {
            MembershipSnapshot old = teams.get(pool.pool());
            if (old != null) {
                MembershipSnapshot expected = old.reconcile(pool.members(), pool.permissions()).after();
                if (!expected.members().equals(pool.members()) || !expected.permissions().equals(pool.permissions())
                        || expected.membershipRevision() != pool.membershipRevision() || expected.permissionRevision() != pool.permissionRevision()) throw new IllegalStateException("Invalid pool revision");
            }
            // All destination totals use original player records: departure detaches its recorded share.
            // No redistribution through transient event states, even for cross-pool moves.
            if (old == null || !old.members().equals(pool.members())) plan.equalize(pool, aggregate(pool.members()), true);
        }
        long nextRevision = Math.incrementExact(revision);
        if (preview) return true;
        boolean sameAuthority = authority == saved.membershipAuthority();
        publishAuthority.run();
        plan.commit();
        // Authority activation is not evidence of team dissolution under the old authority.
        if (sameAuthority) {
            for (var old : teams.values()) if (!next.containsKey(old.pool())) {
                dissolved.put(old.pool(), Optional.ofNullable(old.recoveryRecipient()));
                dissolutionRevisions.put(old.pool(), sourceRevision);
            }
            for (var entry : index.entrySet()) if (!memberIndex.containsKey(entry.getKey()) || !memberIndex.get(entry.getKey()).pool().equals(entry.getValue().pool()))
                machineJoins.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(new MachineJoin(sourceRevision, entry.getValue().pool()));
        }
        next.keySet().forEach(dissolved::remove);
        next.keySet().forEach(dissolutionRevisions::remove);
        teams = next; memberIndex = index; rosterRevision = sourceRevision; revision = nextRevision;
        this.readiness = readiness;
        views.clear();
        next.values().forEach(pool -> views.put(pool.pool(), view(pool)));
        saved.setDirty();
        return true;
    }

    void initializeMissingMembers() {
        // An absent account is zero, never a reason to omit an offline member or rebalance on load.
        for (UUID member : memberIndex.keySet()) saved.getPlayerData(member);
    }

    CompoundTag save() {
        checkThread();
        CompoundTag tag = new CompoundTag();
        tag.putLong("revision", revision); tag.putLong("roster_revision", rosterRevision);
        ListTag pools = new ListTag();
        teams.values().stream().sorted(Comparator.comparing(s -> s.pool().id())).forEach(pool -> pools.add(pool.save()));
        tag.put("teams", pools);
        ListTag remainder = new ListTag();
        cursors.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).forEach(record -> {
            PoolId pool = record.getKey();
            Map<ResourceLocation, Integer> positions = record.getValue();
            CompoundTag entry = new CompoundTag(); entry.put("pool", pool.save());
            CompoundTag values = new CompoundTag(); positions.forEach((type, cursor) -> values.putInt(type.toString(), cursor));
            entry.put("positions", values); remainder.add(entry);
        });
        tag.put("cursors", remainder);
        ListTag resolutions = new ListTag();
        dissolved.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).forEach(e -> {
            CompoundTag receipt = new CompoundTag(); receipt.put("pool", e.getKey().save());
            e.getValue().ifPresent(recipient -> receipt.putUUID("recipient", recipient));
            receipt.putLong("roster_revision", dissolutionRevisions.getOrDefault(e.getKey(), 0L)); resolutions.add(receipt);
        });
        tag.put("dissolved", resolutions);
        ListTag joins = new ListTag();
        machineJoins.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            for (var join : entry.getValue()) {
                CompoundTag record = new CompoundTag(); record.putUUID("owner", entry.getKey());
                record.putLong("revision", join.revision()); record.put("pool", join.pool().save()); joins.add(record);
            }
        });
        tag.put("machine_joins", joins);
        return tag;
    }
    void load(CompoundTag tag) {
        if (!tag.contains("revision", Tag.TAG_LONG) || !tag.contains("roster_revision", Tag.TAG_LONG)
                || !tag.contains("teams", Tag.TAG_LIST) || !tag.contains("cursors", Tag.TAG_LIST))
            throw new IllegalStateException("Missing reservoir settlement metadata");
        revision = tag.getLong("revision"); rosterRevision = tag.getLong("roster_revision");
        if (revision < 0 || rosterRevision < 0) throw new IllegalStateException("Negative reservoir revision");
        for (String key : List.of("teams", "cursors")) {
            ListTag list = (ListTag) tag.get(key);
            if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                throw new IllegalStateException("Malformed reservoir settlement list");
        }
        if (!tag.contains("dissolved", Tag.TAG_LIST)) throw new IllegalStateException("Missing binding resolution metadata");
        ListTag resolutions = tag.getList("dissolved", Tag.TAG_COMPOUND);
        if (!((ListTag) tag.get("dissolved")).isEmpty() && ((ListTag) tag.get("dissolved")).getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalStateException("Invalid binding resolution list");
        for (Tag raw : resolutions) {
            CompoundTag receipt = (CompoundTag) raw;
            PoolId id = PoolId.load(receipt.getCompound("pool"));
            if (id.kind() != PoolId.Kind.TEAM || dissolved.put(id, receipt.hasUUID("recipient") ? Optional.of(receipt.getUUID("recipient")) : Optional.empty()) != null)
                throw new IllegalStateException("Invalid binding resolution receipt");
            if (!receipt.contains("roster_revision", Tag.TAG_LONG)) throw new IllegalStateException("Missing dissolution revision");
            long deletionRevision = receipt.getLong("roster_revision");
            if (deletionRevision < 1 || deletionRevision > rosterRevision) throw new IllegalStateException("Invalid dissolution revision");
            dissolutionRevisions.put(id, deletionRevision);
        }
        if (!tag.contains("machine_joins", Tag.TAG_LIST)) throw new IllegalStateException("Missing or invalid machine joins");
        {
            ListTag joins = (ListTag) tag.get("machine_joins");
            if (!joins.isEmpty() && joins.getElementType() != Tag.TAG_COMPOUND) throw new IllegalStateException("Invalid machine join records");
            for (Tag raw : joins) {
                CompoundTag record = (CompoundTag) raw;
                if (!record.hasUUID("owner") || !record.contains("revision", Tag.TAG_LONG)) throw new IllegalStateException("Incomplete machine join");
                UUID owner = record.getUUID("owner"); long joinedAt = record.getLong("revision");
                PoolId pool = PoolId.load(record.getCompound("pool"));
                List<MachineJoin> history = machineJoins.computeIfAbsent(owner, ignored -> new ArrayList<>());
                if (pool.kind() != PoolId.Kind.TEAM || joinedAt < 1 || joinedAt > rosterRevision
                        || !history.isEmpty() && history.getLast().revision() >= joinedAt) throw new IllegalStateException("Invalid machine join receipt");
                history.add(new MachineJoin(joinedAt, pool));
            }
        }
        for (Tag raw : tag.getList("teams", Tag.TAG_COMPOUND)) {
            MembershipSnapshot pool = MembershipSnapshot.load((CompoundTag) raw);
            if (pool.pool().authority() != saved.membershipAuthority() || pool.pool().kind() != PoolId.Kind.TEAM
                    || rosterRevision == 0 || teams.put(pool.pool(), pool) != null)
                throw new IllegalStateException("Invalid reconciled pool");
            for (UUID member : pool.members()) if (memberIndex.put(member, pool) != null)
                throw new IllegalStateException("Duplicate reconciled member");
        }
        for (Tag raw : tag.getList("cursors", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.contains("positions", Tag.TAG_COMPOUND)) throw new IllegalStateException("Missing remainder positions");
            Map<ResourceLocation, Integer> positions = new HashMap<>();
            CompoundTag values = entry.getCompound("positions");
            for (String key : values.getAllKeys()) {
                ResourceLocation type = ResourceLocation.parse(key);
                if (!values.contains(key, Tag.TAG_INT) || values.getInt(key) < 0) throw new IllegalStateException("Invalid remainder cursor");
                positions.put(type, values.getInt(key));
            }
            validateTypes(positions.keySet());
            if (cursors.put(PoolId.load(entry.getCompound("pool")), positions) != null)
                throw new IllegalStateException("Duplicate remainder cursor");
        }
        if (saved.membershipAuthority() != MembershipAuthority.NATIVE)
            pause(new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "stage-3", "External roster must be confirmed after restart"));
    }
}
