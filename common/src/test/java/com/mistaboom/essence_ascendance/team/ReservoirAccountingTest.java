package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.balance.economy.FractionalLedgerSavedData;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real player/NBT/ItemStack transaction invariants; no graphical launch or provider integration. */
public final class ReservoirAccountingTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);
    private static final ResourceLocation TYPE = EssenceTypes.OFFENSE.id();
    private static final BigInteger SCALE = ReservoirService.SCALE;
    private static final ProviderReadiness READY = new ProviderReadiness(ProviderReadiness.Status.AVAILABLE, "fixture", "Settled complete roster");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); AscendanceTiers.init();
        normalLifecycle(); resetAndCarryFairness(); widePresentation(); fractionalAndReplay(); crossPoolAndReadiness(); failuresAndLimits(); randomizedConservation();
        System.out.println("ReservoirAccountingTest: " + assertions + " assertions passed");
    }

    private static void normalLifecycle() {
        EssenceSavedData saved = new EssenceSavedData();
        saved.addCrucibleStored(A, EssenceTypes.OFFENSE, 900);
        saved.addCrucibleStored(B, EssenceTypes.OFFENSE, 300);
        saved.setInvested(A, com.mistaboom.essence_ascendance.stat.EssenceStats.MELEE_DAMAGE, 17);
        saved.setInvested(B, com.mistaboom.essence_ascendance.stat.EssenceStats.MELEE_DAMAGE, 29);
        saved.completeMilestone(B, ResourceLocation.parse("test:keep_milestone"));
        saved.setFractionalResourceCostCarry(B, ResourceLocation.parse("test:personal_cost"), .25);
        var personalA = personal(saved, A); var personalB = personal(saved, B);
        var team = team(A, B, B);
        check(saved.reservoirs().reconcile(READY, 1, List.of(team)), "Offline complete roster joins");
        shares(saved, 600, A, B);
        check(personalA.equals(personal(saved, A)) && personalB.equals(personal(saved, B)), "Join preserves all personal data");
        rejects(() -> saved.soloReservoirAccount(A));
        rejects(() -> saved.removeCrucibleStoredExact(A, EssenceTypes.OFFENSE, 1));
        check(saved.reservoirs().channel(A, Map.of(TYPE, 200L)), "Member can channel full communal balance");
        shares(saved, 500, A, B);
        check(saved.getPlayerData(A).getAvailable(TYPE) == 200 && saved.getPlayerData(B).getAvailable(TYPE) == 0,
                "Only recipient gains Available");
        check(personalB.equals(personal(saved, B)), "Other member progression/revision unchanged");
        var left = next(team, B);
        check(saved.reservoirs().reconcile(READY, 2, List.of(left)), "Offline leave settles");
        shares(saved, 500, A, B);
        check(saved.getPlayerData(A).getAvailable(TYPE) == 200, "Departure makes no payout");
        var zeroEntrant = next(left, B, C);
        saved.reservoirs().reconcile(READY, 3, List.of(zeroEntrant));
        shares(saved, 250, B, C);
        check(saved.save(new CompoundTag(), null).getCompound("players").contains(C.toString()), "Missing offline account initialized without opening machine");
        var before = save(saved);
        check(!saved.reservoirs().reconcile(READY, 3, List.of(zeroEntrant)), "Duplicate receipt is inert");
        check(save(saved).equals(before), "Duplicate keeps exact records/revisions/cursors");
        saved.reservoirs().reconcile(READY, 4, List.of());
        shares(saved, 250, B, C);
        var disbanded = save(saved);
        check(!saved.reservoirs().reconcile(READY, 4, List.of()) && save(saved).equals(disbanded), "Disband replay detaches once with no payout");
        check(saved.resolvePool(B).requireReady().isSoloFor(B), "Detached member resolves solo");
    }

    private static void resetAndCarryFairness() {
        EssenceSavedData saved = new EssenceSavedData();
        saved.addCrucibleStored(A, EssenceTypes.OFFENSE, 10);
        saved.resetProgressionForAdmin(A);
        check(saved.getPlayerData(A).reservoirUnits(TYPE).signum() == 0, "Solo operator reset routes through service");
        var team = team(A, B, C);
        saved.reservoirs().reconcile(READY, 1, List.of(team));
        Map<UUID, Integer> winners = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            saved.reservoirs().credit(A, Map.of(TYPE, BigInteger.ONE), ReservoirService.NONE);
            for (UUID member : team.members()) if (saved.getPlayerData(member).reservoirUnits(TYPE).signum() > 0)
                winners.merge(member, 1, Integer::sum);
            saved.clearReservoir(A, List.of(TYPE));
            if (i % 2 == 0) saved = EssenceSavedData.load(save(saved), null);
        }
        check(winners.equals(Map.of(A, 4, B, 4, C, 4)), "Remainder ownership rotates fairly across restarts");
        saved.reservoirs().credit(A, Map.of(TYPE, whole(900)), ReservoirService.NONE);
        var shares = stored(saved, team.members());
        saved.resetProgressionForAdmin(A);
        check(stored(saved, team.members()).equals(shares), "Personal team reset cannot mutate communal funds");
        var state = save(saved);
        state.getCompound("players").remove(C.toString());
        var missing = EssenceSavedData.load(state, null);
        check(missing.isDirty() && save(missing).getCompound("players").contains(C.toString()), "Missing persisted member safely initialized on startup");
        check(missing.getPlayerData(A).getCrucibleStored(TYPE) == 300 && missing.getPlayerData(C).getCrucibleStored(TYPE) == 0,
                "Missing record recovery does not redistribute surviving shares on read");
        var fractional = com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService.accumulate(999999, Long.MAX_VALUE, 999999);
        BigInteger exact = BigInteger.valueOf(999999).multiply(BigInteger.valueOf(Long.MAX_VALUE)).add(BigInteger.valueOf(999999));
        check(BigInteger.valueOf(fractional.wholeAmount()).multiply(SCALE).add(BigInteger.valueOf(fractional.nextCarry())).equals(exact),
                "Shared fractional accumulation uses wide intermediates for representable large counts");
    }

    private static void widePresentation() {
        var category = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        var total = category.multiply(BigInteger.valueOf(6));
        var payload = new com.mistaboom.essence_ascendance.network.EssenceCrucibleStatePayload(
                com.mistaboom.essence_ascendance.network.EssenceCrucibleStatePayload.CURRENT_SCHEMA_VERSION,
                1, 0, "test", "private", true, false, false, 0,
                category, category, category, category, category, category,
                total, category, 20, 16, 20, 0, "smart_round_robin", 0, 4, 16, 1, 1, 63, false, true, true, 19, 0, 0, 0, "STOPPED", true, false, "AVAILABLE");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            com.mistaboom.essence_ascendance.network.EssenceCrucibleStatePayload.CODEC.encode(buffer, payload);
            check(com.mistaboom.essence_ascendance.network.EssenceCrucibleStatePayload.CODEC.decode(buffer).equals(payload),
                    "Six team categories and local capacity beyond long survive actual GUI packet codec without saturation");
        } finally { buffer.release(); }
    }

    private static void fractionalAndReplay() {
        EssenceSavedData saved = new EssenceSavedData();
        var team = team(A, B, C);
        saved.reservoirs().reconcile(READY, 1, List.of(team));
        FractionalLedgerSavedData personalRewards = new FractionalLedgerSavedData();
        personalRewards.commit(A, Map.of("gathering/reward", 123456L));
        var rewardsBefore = personalRewards.save(new CompoundTag(), null);
        for (int i = 1; i <= 7; i++) {
            saved.reservoirs().credit(A, Map.of(TYPE, BigInteger.ONE), ReservoirService.NONE);
            check(saved.reservoirs().read(A).units(TYPE).equals(BigInteger.valueOf(i)), "Tiny deposit conserved " + i);
            equalWithinMicro(saved, team);
        }
        check(personalRewards.save(new CompoundTag(), null).equals(rewardsBefore), "Unrelated reward carry is personal");
        var before = save(saved);
        var cached = saved.reservoirs().read(A);
        for (int i = 0; i < 50; i++) check(saved.reservoirs().read(B) == cached, "Read reuses immutable aggregate view");
        saved.reservoirs().equalize(A);
        check(save(saved).equals(before), "Already equal shares retain remainder owners and revisions");
        EssenceSavedData restart = EssenceSavedData.load(before, null);
        check(!restart.reservoirs().reconcile(READY, 1, List.of(team)), "Startup roster replay is inert");
        check(save(restart).equals(before), "Restart keeps exact shares, cursors and roster identity");
        saved.reservoirs().credit(A, Map.of(TYPE, BigInteger.ONE), ReservoirService.NONE);
        restart.reservoirs().credit(A, Map.of(TYPE, BigInteger.ONE), ReservoirService.NONE);
        check(save(saved).equals(save(restart)), "Next remainder choice survives restart");
        var roles = team.reconcile(team.members(), Map.of(A, EnumSet.allOf(PoolCapability.class))).after();
        var amounts = stored(saved, team.members());
        saved.reservoirs().reconcile(READY, 2, List.of(roles));
        check(stored(saved, team.members()).equals(amounts), "Permission/name-independent metadata does not reshuffle shares");
        rejects(() -> saved.reservoirs().debit(B, Map.of(TYPE, 1L), ReservoirService.NONE));
        saved.clearReservoir(A, List.of(TYPE));
        check(saved.reservoirs().read(A).units(TYPE).signum() == 0, "Explicit admin clear includes fractional shares");
    }

    private static void crossPoolAndReadiness() {
        EssenceSavedData saved = new EssenceSavedData();
        saved.addCrucibleStored(A, EssenceTypes.OFFENSE, 100);
        saved.addCrucibleStored(C, EssenceTypes.OFFENSE, 600);
        var first = team(A, B); var second = team(C, D);
        saved.reservoirs().reconcile(READY, 1, List.of(first, second));
        shares(saved, 50, A, B); shares(saved, 300, C, D);
        var nextFirst = next(first, A, C); var nextSecond = next(second, B, D);
        var before = save(saved);
        var unknown = new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "fixture", "Temporarily unavailable");
        check(!saved.reservoirs().reconcile(unknown, 2, List.of()) && save(saved).equals(before), "Unavailable is not a disband");
        rejects(() -> saved.reservoirs().debit(A, Map.of(TYPE, 1L), ReservoirService.NONE));
        rejects(() -> saved.reservoirs().read(new UUID(0, 999)));
        check(!saved.reservoirs().reconcile(READY, 1, List.of(first, second)), "Confirmed current replay resumes access without redistribution");
        check(saved.reservoirs().read(A).whole(TYPE).equals(BigInteger.valueOf(100)) && save(saved).equals(before),
                "Unavailable/resume preserved receipts, funds and remainder choices");
        rejects(() -> saved.reservoirs().reconcile(READY, 2, List.of(nextFirst, second)));
        check(save(saved).equals(before), "Cross-pool duplicate UUID rejects whole batch");
        saved.reservoirs().reconcile(READY, 2, List.of(nextFirst, nextSecond));
        shares(saved, 175, A, B, C, D);
        check(saved.reservoirs().read(A).units(TYPE).add(saved.reservoirs().read(B).units(TYPE)).equals(whole(700)), "Cross-pool move conserves both original reservoirs");
        var after = save(saved);
        check(!saved.reservoirs().reconcile(READY, 1, List.of(first, second)) && save(saved).equals(after), "Old receipt cannot undo settled state");
        rejects(() -> saved.reservoirs().reconcile(READY, 2, List.of(first, second)));
        check(save(saved).equals(after), "Conflicting duplicate revision is inert");
        // A bulk destination is computed once from original shares, independently of list/event order.
        var replay = EssenceSavedData.load(before, null);
        replay.reservoirs().reconcile(READY, 2, List.of(nextSecond, nextFirst));
        check(stored(saved, List.of(A,B,C,D)).equals(stored(replay, List.of(A,B,C,D))), "Settled batch order cannot change balances");
    }

    private static void failuresAndLimits() throws Exception {
        EssenceSavedData saved = new EssenceSavedData();
        var team = team(A, B);
        saved.reservoirs().reconcile(READY, 1, List.of(team));
        Map<ResourceLocation, Long> six = new LinkedHashMap<>();
        for (var type : EssenceTypes.ORDERED) six.put(type.id(), 10L);
        saved.reservoirs().credit(A, ReservoirService.wholeUnits(six, false), ReservoirService.NONE);
        var before = save(saved);
        Map<ResourceLocation, Long> unfunded = new LinkedHashMap<>(six);
        unfunded.put(EssenceTypes.ORDERED.getLast().id(), 11L);
        int[] calls = {0};
        check(!saved.reservoirs().debit(A, unfunded, new ReservoirService.Mutation() {
            public boolean apply() { calls[0]++; return true; }
            public void rollback() { throw new AssertionError("Unfunded transaction must never start inventory mutation"); }
        }) && calls[0] == 0 && save(saved).equals(before), "Failed six-type cost changes nothing");
        ItemStack[] inventory = {new ItemStack(Items.DIAMOND, 4)};
        ItemStack original = inventory[0].copy();
        int[] progress = {19};
        for (boolean throwFailure : List.of(false, true)) {
            ReservoirService.Mutation mutation = new ReservoirService.Mutation() {
                public boolean apply() {
                    inventory[0].shrink(2); progress[0] = 100;
                    if (throwFailure) throw new IllegalStateException("Injected inventory failure");
                    return false;
                }
                public void rollback() { inventory[0] = original.copy(); progress[0] = 19; }
            };
            if (throwFailure) rejects(() -> saved.reservoirs().debit(A, six, mutation));
            else check(!saved.reservoirs().debit(A, six, mutation), "Participant can reject");
            check(ItemStack.matches(inventory[0], original) && progress[0] == 19 && save(saved).equals(before), "Inventory/job failure restores exact snapshot and all money/revisions");
        }
        var personalA = personal(saved, A); var personalB = personal(saved, B);
        check(saved.reservoirs().debit(A, six, ReservoirService.NONE), "Six-type infusion debit succeeds atomically");
        check(personal(saved,A).equals(personalA) && personal(saved,B).equals(personalB), "Infusion grants no Available and changes no personal progression");
        BigInteger maximum = BigInteger.valueOf(Long.MAX_VALUE).multiply(SCALE).add(SCALE.subtract(BigInteger.ONE));
        saved.reservoirs().administer(A, Map.of(TYPE, maximum.multiply(BigInteger.TWO)));
        check(saved.reservoirs().read(A).whole(TYPE).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0, "Aggregate wider than long is exact");
        var full = save(saved);
        rejects(() -> saved.reservoirs().credit(A, Map.of(TYPE, BigInteger.ONE), new ReservoirService.Mutation() {
            public boolean apply() { throw new AssertionError("Overflow consumed an item"); }
            public void rollback() { throw new AssertionError("Overflow ran rollback"); }
        }));
        check(save(saved).equals(full), "Share overflow preserves exact state");
        saved.setEssence(A, EssenceTypes.OFFENSE, Long.MAX_VALUE);
        var availableFull = save(saved);
        rejects(() -> saved.reservoirs().channel(A, Map.of(TYPE, 1L)));
        check(save(saved).equals(availableFull), "Available overflow rejects channel before debit");
        var reduced = next(team, B);
        saved.reservoirs().reconcile(READY, 2, List.of(reduced));
        check(saved.getPlayerData(A).reservoirUnits(TYPE).equals(maximum) && saved.getPlayerData(B).reservoirUnits(TYPE).equals(maximum), "Near-limit departure detaches rather than redistributing departing share");
        AtomicReference<Throwable> rejected = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { saved.reservoirs().read(B); } catch (Throwable failure) { rejected.set(failure); } });
        worker.start(); worker.join();
        check(rejected.get() instanceof IllegalStateException, "Off-thread read/mutation boundary rejects access");
        var stable = save(saved);
        rejects(() -> saved.reservoirs().debit(B, Map.of(TYPE, 1L), new ReservoirService.Mutation() {
            public boolean apply() { saved.reservoirs().debit(B, Map.of(TYPE, 1L), ReservoirService.NONE); return true; }
            public void rollback() { }
        }));
        check(save(saved).equals(stable), "Reentrant mutation is inert");
        CompoundTag old = stable.copy(); old.putInt("data_version", 7);
        rejects(() -> EssenceSavedData.load(old, null));
        CompoundTag malformedCarry = stable.copy();
        malformedCarry.getCompound("players").getCompound(A.toString()).putString("reservoir_carry", "invalid");
        rejects(() -> EssenceSavedData.load(malformedCarry, null));
        CompoundTag malformedPlayer = stable.copy(); malformedPlayer.getCompound("players").putLong(A.toString(), 1);
        rejects(() -> EssenceSavedData.load(malformedPlayer, null));
        CompoundTag exhausted = stable.copy();
        exhausted.getCompound("players").getCompound(B.toString()).putLong("reservoir_revision", Long.MAX_VALUE);
        var exhaustedSaved = EssenceSavedData.load(exhausted, null);
        var beforeExhausted = save(exhaustedSaved);
        rejects(() -> exhaustedSaved.reservoirs().debit(B, Map.of(TYPE, 1L), ReservoirService.NONE));
        check(save(exhaustedSaved).equals(beforeExhausted), "Revision overflow is checked before any share mutation");
    }

    private static void randomizedConservation() {
        EssenceSavedData saved = new EssenceSavedData();
        var team = team(A, B, C, D);
        saved.reservoirs().reconcile(READY, 1, List.of(team));
        Random random = new Random(721901L);
        Map<ResourceLocation, BigInteger> expected = new HashMap<>();
        for (int i = 0; i < 250; i++) {
            var type = EssenceTypes.ORDERED.get(random.nextInt(6)).id();
            BigInteger units = BigInteger.valueOf(random.nextInt(4_000_001));
            saved.reservoirs().credit(team.members().get(i % 4), Map.of(type, units), ReservoirService.NONE);
            expected.merge(type, units, BigInteger::add);
            if (i % 3 == 0 && expected.get(type).compareTo(SCALE) >= 0) {
                check(saved.reservoirs().debit(A, Map.of(type, 1L), ReservoirService.NONE), "Random funded payment");
                expected.put(type, expected.get(type).subtract(SCALE));
            }
            for (var essence : EssenceTypes.ORDERED) check(saved.reservoirs().read(A).units(essence.id())
                    .equals(expected.getOrDefault(essence.id(), BigInteger.ZERO)), "Random exact conservation");
            equalWithinMicro(saved, team);
            if (i % 31 == 0) saved = EssenceSavedData.load(save(saved), null);
        }
        for (UUID player : team.members()) check(saved.getPlayerData(player).nexusRevision() == 0, "Reservoir traffic never invalidates personal progression");
    }

    private static MembershipSnapshot team(UUID... members) {
        Map<UUID, Set<PoolCapability>> grants = new HashMap<>();
        for (UUID member : members) grants.put(member, EnumSet.allOf(PoolCapability.class));
        return new MembershipSnapshot(PoolId.newNative(PoolId.Kind.TEAM), List.of(members), grants, 1, 1);
    }
    private static MembershipSnapshot next(MembershipSnapshot old, UUID... members) {
        Map<UUID, Set<PoolCapability>> grants = new HashMap<>();
        for (UUID member : members) grants.put(member, EnumSet.allOf(PoolCapability.class));
        return old.reconcile(List.of(members), grants).after();
    }
    private static BigInteger whole(long amount) { return BigInteger.valueOf(amount).multiply(SCALE); }
    private static CompoundTag save(EssenceSavedData saved) { return saved.save(new CompoundTag(), null); }
    private static CompoundTag personal(EssenceSavedData saved, UUID member) {
        var tag = saved.getPlayerData(member).save();
        tag.remove("crucible_reservoir"); tag.remove("reservoir_carry"); tag.remove("reservoir_revision");
        return tag;
    }
    private static Map<UUID, Map<ResourceLocation, BigInteger>> stored(EssenceSavedData saved, List<UUID> members) {
        Map<UUID, Map<ResourceLocation, BigInteger>> records = new HashMap<>();
        for (UUID member : members) {
            Map<ResourceLocation, BigInteger> record = new HashMap<>();
            for (var type : EssenceTypes.ORDERED) record.put(type.id(), saved.getPlayerData(member).reservoirUnits(type.id()));
            records.put(member, record);
        }
        return records;
    }
    private static void shares(EssenceSavedData saved, long expected, UUID... members) {
        for (UUID member : members) check(saved.getPlayerData(member).reservoirUnits(TYPE).equals(whole(expected)), "Expected share " + expected);
    }
    private static void equalWithinMicro(EssenceSavedData saved, MembershipSnapshot pool) {
        for (var type : EssenceTypes.ORDERED) {
            BigInteger min = null, max = BigInteger.ZERO;
            for (UUID member : pool.members()) {
                BigInteger value = saved.getPlayerData(member).reservoirUnits(type.id());
                min = min == null ? value : min.min(value); max = max.max(value);
            }
            check(max.subtract(min).compareTo(BigInteger.ONE) <= 0, "Shares differ by at most one millionth");
        }
    }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
    private static void rejects(Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException | ArithmeticException expected) { return; }
        throw new AssertionError("Invalid transaction accepted");
    }
}
