package com.mistaboom.essence_ascendance.damage;

/** Client-local aggregation between native health updates. Any ordinary impact wins over quiet debt.
 * This stores presentation intent only, never an authoritative or shadow health value. */
public final class DamageFeedbackState {
    private boolean quiet, impact;
    public void observe(boolean quietDamage) {
        if (quietDamage) quiet = true;
        else impact = true;
    }
    public boolean consumeQuietUpdate() {
        boolean result = quiet && !impact;
        quiet = false;
        impact = false;
        return result;
    }
}
