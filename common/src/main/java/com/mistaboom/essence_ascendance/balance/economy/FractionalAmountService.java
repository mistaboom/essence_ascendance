package com.mistaboom.essence_ascendance.balance.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared exact six-decimal accounting for rewards, costs, and processing. */
public final class FractionalAmountService {
    public static final long SCALE = 1_000_000L;
    private FractionalAmountService() { }

    public static long units(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid fractional amount");
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(SCALE))
                .setScale(0, RoundingMode.DOWN).longValueExact();
    }

    public static double amount(long units) {
        if (units < 0) throw new IllegalArgumentException("Negative fractional amount");
        return (double) units / SCALE;
    }

    /** Shared boundary for percentage menu costs and server-random harvest bonuses. */
    public static Split split(double exactAmount) {
        if (!Double.isFinite(exactAmount) || exactAmount < 0)
            throw new IllegalArgumentException("Invalid exact amount");
        double whole = Math.floor(exactAmount);
        return new Split(whole, exactAmount - whole);
    }

    /** Ceil cost prevents splitting a conversion into profitable rounded fragments. */
    public static long requiredForEfficiency(long target, int efficiencyBasisPoints) {
        if (target <= 0 || efficiencyBasisPoints <= 0 || efficiencyBasisPoints > 10_000) return 0;
        try {
            java.math.BigInteger numerator = java.math.BigInteger.valueOf(target).multiply(java.math.BigInteger.valueOf(10_000));
            java.math.BigInteger divisor = java.math.BigInteger.valueOf(efficiencyBasisPoints);
            return numerator.add(divisor).subtract(java.math.BigInteger.ONE).divide(divisor).longValueExact();
        } catch (ArithmeticException overflow) { return 0; }
    }

    public static long yieldForEfficiencyUnits(long storedAmount, int efficiencyBasisPoints) {
        if (storedAmount < 0 || efficiencyBasisPoints < 0 || efficiencyBasisPoints > 10_000)
            throw new IllegalArgumentException("Invalid extraction transaction");
        // SCALE is divisible by 10000, so every stored integer is represented
        // exactly even when extracted as nine nuggets instead of one ingot.
        return Math.multiplyExact(storedAmount, Math.multiplyExact(SCALE / 10_000, efficiencyBasisPoints));
    }

    /** No mutation: callers persist nextCarry only when the transaction commits. */
    public static Resolution accumulate(long perActionUnits, long count, long previousCarry) {
        if (perActionUnits < 0 || count < 0 || previousCarry < 0 || previousCarry >= SCALE)
            throw new IllegalArgumentException("Invalid fractional transaction");
        // Split first so a representable whole result need not fit in micro-units.
        long whole = Math.multiplyExact(perActionUnits / SCALE, count);
        long remainder = Math.addExact(Math.multiplyExact(perActionUnits % SCALE, count), previousCarry);
        return new Resolution(Math.addExact(whole, remainder / SCALE), remainder % SCALE);
    }

    public record Resolution(long wholeAmount, long nextCarry) { }
    public record Split(double whole, double remainder) { }
}
