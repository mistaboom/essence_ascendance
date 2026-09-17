package com.mistaboom.essence_ascendance.skill.tooltip;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Display precision only; four significant figures preserve small nonzero generated effects. */
public final class SkillValueText {
    private SkillValueText() { }
    public static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite generated tooltip value");
        return BigDecimal.valueOf(value).round(new MathContext(4, RoundingMode.HALF_UP))
                .stripTrailingZeros().toPlainString();
    }
}
