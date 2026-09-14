package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.BuildComposition.Participation;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;

/** Final-output ceilings, relative to the matching external equipment frontier.
 * These are not independent multipliers to apply to each system in gameplay. */
public final class BuildPowerTargets {
    private BuildPowerTargets() { }

    public static double multiplier(BalanceSettings settings, ProgressionBand band, Participation participation) {
        if (participation == Participation.EQUIPMENT_FOCUSED) return 1;
        double combined = switch (band) {
            case ENTRY -> 1.5;
            case EARLY -> 1.7;
            case MID -> 2;
            case LATE -> 2.5;
            case APEX -> 3;
        };
        // A single system can use the early headroom, but never the extra
        // endgame headroom reserved for participating in both systems.
        double target = participation == Participation.BONUS_FOCUSED || participation == Participation.SKILL_FOCUSED
                ? Math.min(2, combined) : combined;
        return 1 + (target - 1) * intent(settings, band);
    }

    public static double burstMultiplier(BalanceSettings settings, ProgressionBand band,
                                         Participation participation, boolean lowHealth) {
        double ordinary = multiplier(settings, band, participation);
        // Only a modeled Desperation build at <=20% current health qualifies.
        // The offense-only report row by itself does not grant this allowance.
        boolean combined = participation != Participation.EQUIPMENT_FOCUSED
                && participation != Participation.BONUS_FOCUSED && participation != Participation.SKILL_FOCUSED
                && participation != Participation.CATEGORY_SPECIALIZED;
        return lowHealth && combined && band == ProgressionBand.APEX
                ? 1 + 3 * intent(settings, band) : ordinary;
    }

    /** Explicit first-purchase budget, independent of future rank counts/curves.
     * Do not fill a later-tier ceiling with rank one and then sell zero-value ranks. */
    public static double rankOneMultiplier(BalanceSettings settings, ProgressionBand band, Participation participation) {
        double full = multiplier(settings, band, participation);
        if (participation == Participation.EQUIPMENT_FOCUSED || participation == Participation.BONUS_FOCUSED
                || band == ProgressionBand.ENTRY) return full;
        if (participation == Participation.SKILL_FOCUSED) return Math.min(full, 1 + .5 * intent(settings, band));
        return 1 + .8 * (full - 1);
    }

    private static double intent(BalanceSettings s, ProgressionBand band) {
        // Preserve friendly power controls without making the default apex=1.1
        // silently turn the approved 3x target into 3.2x. Equipment is unchanged.
        return s.overallPower() * switch (band) {
            case ENTRY, EARLY -> s.earlyPower() / .8;
            case MID -> s.midPower() / .9;
            case LATE -> s.latePower();
            case APEX -> s.apexPower() / 1.1;
        };
    }
}
