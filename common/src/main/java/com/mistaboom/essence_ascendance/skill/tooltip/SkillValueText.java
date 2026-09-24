package com.mistaboom.essence_ascendance.skill.tooltip;

import com.mistaboom.essence_ascendance.presentation.PresentationMetric;

/** Display precision only; four significant figures preserve small nonzero generated effects. */
public final class SkillValueText {
    private SkillValueText() { }
    public static String number(double value) {
        return PresentationMetric.DisplayConversion.NATIVE.format(value);
    }
}
