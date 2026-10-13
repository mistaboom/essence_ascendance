package com.mistaboom.essence_ascendance.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;

/** Shared exact and compact display formats for authoritative essence yields. */
public final class EssenceYieldFormat {
    private EssenceYieldFormat() { }

    public static String format(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Essence yield must be finite and nonnegative");
        }
        return formatMicros(FractionalAmountService.units(amount));
    }

    /** Long micro-units preserve every digit, including above double's exact-integer range. */
    public static String formatMicros(long microUnits) {
        if (microUnits < 0) throw new IllegalArgumentException("Essence yield cannot be negative");
        DecimalFormat formatter = new DecimalFormat("#,##0.######",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        formatter.setRoundingMode(RoundingMode.UNNECESSARY);
        // New generated values are whole; existing cached profiles stay honest until rebuilt.
        return formatter.format(BigDecimal.valueOf(microUnits, 6));
    }

    /** Compact item tooltips hide fractions without changing stored or extracted essence. */
    public static String formatTooltipMicros(long microUnits) {
        if (microUnits < 0) throw new IllegalArgumentException("Essence yield cannot be negative");
        if (microUnits > 0 && microUnits < FractionalAmountService.SCALE) return "<1";
        DecimalFormat formatter = new DecimalFormat("#,##0",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return formatter.format(BigDecimal.valueOf(microUnits, 6));
    }
}
