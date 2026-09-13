package com.mistaboom.essence_ascendance.client;

import java.util.Locale;

/** The displayed value must equal the authoritative value, including old cached fractions. */
public final class EssenceYieldFormatTest {
    public static void main(String[] args) {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            expect(0, "0");
            expect(0.000001, "0.000001");
            expect(0.009999, "0.009999");
            expect(0.01, "0.01");
            expect(0.019999, "0.019999");
            expect(0.1, "0.1");
            expect(0.782391, "0.782391");
            expect(0.999999, "0.999999");
            expect(1, "1");
            expect(1.999999, "1.999999");
            expect(5.844611, "5.844611");
            expect(999.999999, "999.999999");
            expect(1_000, "1,000");
            expect(123_456.987654, "123,456.987654");
            expectMicros(1L, "0.000001");
            expectMicros(5_000_000L, "5");
            expectMicros(9_007_199_254_740_993L, "9,007,199,254.740993");
            expectMicros(Long.MAX_VALUE, "9,223,372,036,854.775807");
            try {
                EssenceYieldFormat.formatMicros(-1);
                throw new AssertionError("Negative exact yield accepted");
            } catch (IllegalArgumentException expected) { }
            for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
                try {
                    EssenceYieldFormat.format(invalid);
                    throw new AssertionError("Invalid yield accepted: " + invalid);
                } catch (IllegalArgumentException expected) { }
            }
            System.out.println("EssenceYieldFormatTest: exact whole/cached fractional values, long precision and locale boundaries passed");
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static void expect(double amount, String expected) {
        String actual = EssenceYieldFormat.format(amount);
        if (!expected.equals(actual)) throw new AssertionError(amount + ": expected " + expected + ", got " + actual);
    }

    private static void expectMicros(long amount, String expected) {
        String actual = EssenceYieldFormat.formatMicros(amount);
        if (!expected.equals(actual)) throw new AssertionError(amount + ": expected " + expected + ", got " + actual);
    }
}
