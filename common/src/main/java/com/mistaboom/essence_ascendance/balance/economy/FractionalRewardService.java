package com.mistaboom.essence_ascendance.balance.economy;

import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Commits an exact positive fractional reward to a persistent owner/account carry ledger. */
public final class FractionalRewardService {
    private FractionalRewardService() { }

    public static Credit credit(MinecraftServer server, UUID owner, String account, double exactAmount) {
        if (server == null || owner == null || account == null || account.isBlank())
            throw new IllegalArgumentException("Fractional reward identity cannot be empty");
        if (!Double.isFinite(exactAmount) || exactAmount < 0)
            throw new IllegalArgumentException("Invalid fractional reward amount");
        if (exactAmount == 0) return new Credit(0, 0);

        FractionalLedgerSavedData ledger = FractionalLedgerSavedData.get(server);
        Map<String, Long> carry = new TreeMap<>(ledger.snapshot(owner));
        long previous = carry.getOrDefault(account, 0L);
        FractionalAmountService.Resolution result = FractionalAmountService.accumulate(
                FractionalAmountService.units(exactAmount), 1L, previous);
        if (result.nextCarry() == 0) carry.remove(account);
        else carry.put(account, result.nextCarry());
        ledger.commit(owner, carry);
        return new Credit(result.wholeAmount(), result.nextCarry());
    }

    public record Credit(long wholeAmount, long carryUnits) { }
}
