package com.mistaboom.essence_ascendance.neoforge;

import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Executes the production commit journal; game-backed slot tests require the full loader fixture. */
public final class MachineTransferJournal2612Test {
    private static int checks;
    public static void main(String[] args) {
        int[] result = {0, 0};
        var journal = new MachineNeoForgeResourceHandler.Effects(flags -> { result[0]++; result[1] = flags; });
        try (var root = Transaction.openRoot()) {
            journal.record(root, 3);
        }
        check(result[0] == 0, "Abort publishes nothing");
        try (var root = Transaction.openRoot()) {
            journal.record(root, 0);
            try (var nested = Transaction.open(root)) { journal.record(nested, 3); }
            root.commit();
        }
        check(result[0] == 1 && result[1] == 0, "Aborted child flags do not leak into extraction commit");
        try (var root = Transaction.openRoot()) {
            journal.record(root, 1);
            try (var nested = Transaction.open(root)) { journal.record(nested, 2); nested.commit(); }
            check(result[0] == 1, "Child commit remains provisional");
            root.commit();
        }
        check(result[0] == 2 && result[1] == 3, "Root publishes combined flags once");
        try (var root = Transaction.openRoot()) {
            try (var nested = Transaction.open(root)) { journal.record(nested, 3); nested.commit(); }
        }
        check(result[0] == 2, "Outer abort suppresses committed child effects");
        try (var root = Transaction.openRoot()) { journal.record(root, 0); root.commit(); }
        check(result[0] == 3 && result[1] == 0, "Reused journal resets flags between transactions");
        try (var root = Transaction.openRoot()) { root.commit(); }
        check(result[0] == 3, "Empty transaction has no effects");
        System.out.println("NeoForge 26.1.2 production commit-journal invariants passed: " + checks);
    }
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
}
