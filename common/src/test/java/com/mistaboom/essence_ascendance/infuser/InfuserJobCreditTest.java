package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Current-job credit survives restart and cannot move to the latest operator or supply. */
public final class InfuserJobCreditTest {
    private static int checks;
    public static void main(String[] args) {
        UUID originalActor = UUID.randomUUID(), supply = UUID.randomUUID(), secondSupply = UUID.randomUUID();
        InfuserJobCredit begin = InfuserJobCredit.begin(originalActor);
        check(begin.source() == null && !begin.observedSupply(), "Start alone does not assert a consumed source");
        InfuserJobCredit paid = begin.suppliedBy(supply);
        check(paid.actor().equals(originalActor) && paid.actionId().equals(begin.actionId()) && paid.source().equals(supply), "Committed supply retains actor and operation identity");
        check(paid.suppliedBy(supply).equals(paid), "Repeated grants from one physical supply remain one operation");
        InfuserJobCredit restored = InfuserJobCredit.load(paid.save());
        check(paid.equals(restored), "Restart restores exact committed operation provenance");
        InfuserJobCredit mixed = restored.suppliedBy(secondSupply);
        check(mixed.mixedSupply() && mixed.source() == null && mixed.actor().equals(originalActor), "A different physical supply invalidates only single-source proof");
        check(mixed.suppliedBy(supply).equals(mixed), "Returning to the first source does not undo mixed-source evidence");
        check(InfuserJobCredit.load(mixed.save()).equals(mixed), "Mixed-source rejection persists across restart");
        InfuserJobCredit cleared = paid.withoutActor();
        check(cleared.actor() == null && cleared.actionId().equals(paid.actionId()) && cleared.source().equals(supply), "Creative or rebinding invalidation cannot fabricate a new action");
        check(InfuserJobCredit.load(new CompoundTag()) == null, "Legacy missing provenance does not invent an actor");
        CompoundTag future = paid.save(); future.putInt("version", 2);
        check(InfuserJobCredit.load(future) == null, "Unknown provenance never becomes eligible operation proof");
        CompoundTag malformed = paid.save(); malformed.putBoolean("observed_supply", false);
        check(InfuserJobCredit.load(malformed) == null, "Malformed source claims reject locally");
        check(paid.suppliedBy(null).mixedSupply(), "Unknown supply fails closed without losing operation actor");
        System.out.println("InfuserJobCreditTest: " + checks + " assertions passed");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
