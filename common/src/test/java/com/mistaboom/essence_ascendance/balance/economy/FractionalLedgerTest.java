package com.mistaboom.essence_ascendance.balance.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/** Actual SavedData/NBT account lifecycle checks; does not require an online owner. */
public final class FractionalLedgerTest {
    private static int assertions;
    private static final String OFFENSE = "dissolution/essence_ascendance:offense";
    private static final String DEFENSE = "dissolution/essence_ascendance:defense";

    public static void main(String[] args) throws Exception {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
        FractionalLedgerSavedData ledger = new FractionalLedgerSavedData();
        ledger.commit(owner, Map.of(OFFENSE, 750_000L, DEFENSE, 250_000L, "other/reward", 100_000L));
        ledger.commit(other, Map.of(OFFENSE, 800_000L));

        Map<String, Long> before = ledger.snapshot(owner);
        FractionalAmountService.Resolution preview = FractionalAmountService.accumulate(
                500_000L, 1, before.get(OFFENSE));
        check(preview.wholeAmount() == 1 && preview.nextCarry() == 250_000, "Incorrect preview");
        check(ledger.snapshot(owner).equals(before), "Uncommitted/blocked preview changed saved credit");
        rejects(() -> before.put(OFFENSE, 0L));
        FractionalLedgerSavedData originalLedger = ledger;
        rejects(() -> originalLedger.commit(owner, Map.of(OFFENSE, 1_000_000L)));
        check(ledger.snapshot(owner).equals(before), "Rejected commit discarded existing credit");

        ledger = roundTrip(ledger);
        check(ledger.snapshot(owner).equals(before), "Offline/restart roundtrip lost carry");
        check(ledger.snapshot(other).get(OFFENSE) == 800_000L, "Roundtrip merged owner accounts");
        ledger.setDirty(false);
        check(ledger.clear(owner, OFFENSE), "Explicit essence clear did not remove fractional credit");
        check(ledger.isDirty(), "Cleared credit was not marked for persistence");
        check(ledger.snapshot(owner).equals(Map.of(DEFENSE, 250_000L, "other/reward", 100_000L)),
                "Per-essence clear affected another account");
        check(FractionalAmountService.accumulate(500_000L, 1,
                ledger.snapshot(owner).getOrDefault(OFFENSE, 0L)).wholeAmount() == 0,
                "Cleared credit resurfaced on the next dissolution");
        ledger = roundTrip(ledger);
        check(!ledger.snapshot(owner).containsKey(OFFENSE), "Restart restored cleared credit");
        check(ledger.snapshot(other).get(OFFENSE) == 800_000L, "Clear affected another owner");
        ledger.setDirty(false);
        check(!ledger.clear(owner, OFFENSE) && !ledger.isDirty(), "Empty clear mutated storage");
        check(ledger.clear(owner), "Full reset did not remove remaining account credit");
        check(ledger.snapshot(owner).isEmpty(), "Full reset left account credit");
        CompoundTag saved = ledger.save(new CompoundTag(), null);
        check(!saved.contains(owner.toString()), "Full reset retained an empty saved owner");
        check(roundTrip(ledger).snapshot(other).get(OFFENSE) == 800_000L,
                "Full reset affected another owner's persistence");
        ledger.commit(other, Map.of(OFFENSE, 0L));
        check(ledger.save(new CompoundTag(), null).getAllKeys().isEmpty(),
                "Zero-carry commit retained an empty saved account");
        System.out.println("FractionalLedgerTest: " + assertions + " assertions passed");
    }

    private static FractionalLedgerSavedData roundTrip(FractionalLedgerSavedData ledger) throws Exception {
        Method load = FractionalLedgerSavedData.class.getDeclaredMethod("load", CompoundTag.class,
                HolderLookup.Provider.class);
        load.setAccessible(true);
        return (FractionalLedgerSavedData) load.invoke(null, ledger.save(new CompoundTag(), null), null);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable operation) {
        assertions++;
        try { operation.run(); }
        catch (IllegalArgumentException | UnsupportedOperationException expected) { return; }
        throw new AssertionError("Invalid account mutation was accepted");
    }
}
