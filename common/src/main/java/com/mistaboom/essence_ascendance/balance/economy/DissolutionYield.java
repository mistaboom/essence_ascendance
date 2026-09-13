package com.mistaboom.essence_ascendance.balance.economy;

/** Actual creditable value, expressed in deterministic millionths of Essence. */
public record DissolutionYield(long microUnits) {
    public DissolutionYield {
        if (microUnits < 0) throw new IllegalArgumentException("Negative dissolution yield");
    }
    public double amount() { return FractionalAmountService.amount(microUnits); }
    public static DissolutionYield of(double amount) { return new DissolutionYield(FractionalAmountService.units(amount)); }
}
