package com.mistaboom.essence_ascendance.balance.economy;

/** Opportunity cost is evidence, never an amount credited to a reservoir. */
public record EconomicValue(double amount) {
    public EconomicValue {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid economic value");
    }
}
