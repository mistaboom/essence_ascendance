package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;

/** Pure, conservation-based resource and trauma arithmetic shared by gameplay and balance projections. */
public final class DamageRoutingMath {
    private DamageRoutingMath() { }
    public record FoodDebit(double remainingDamage, int food, double saturation, double prepaidFood) { }
    public static FoodDebit debitFood(double damage, double healthPerPoint, int food, double saturation, double prepaid) {
        require(damage); require(healthPerPoint); require(saturation); require(prepaid);
        if (food < 0 || prepaid >= 1) throw new IllegalArgumentException("Invalid food reservoir");
        if (healthPerPoint == 0) return new FoodDebit(damage, food, saturation, prepaid);
        double needed = damage / healthPerPoint;
        double saturationUsed = Math.min(saturation, needed);
        needed -= saturationUsed;
        double prepaidUsed = Math.min(prepaid, needed);
        needed -= prepaidUsed;
        prepaid -= prepaidUsed;
        // This is upfront payment with prepaid change, not a deferred efficiency discount.
        // Share the exact integer boundary, but do not use the discount service's opposite carry convention.
        var split = FractionalAmountService.split(needed);
        int wholeUsed = (int)Math.min(food, split.whole() + (split.remainder() > 0 ? 1 : 0));
        double foodUsed = Math.min(needed, wholeUsed);
        needed -= foodUsed;
        prepaid += wholeUsed - foodUsed;
        // Fractional credit is food already paid, not newly generated nutrition.
        return new FoodDebit(Math.max(0, needed * healthPerPoint), food - wholeUsed,
                Math.max(0, saturation - saturationUsed), Math.clamp(prepaid, 0, Math.nextDown(1.0)));
    }
    /** Only the generated fraction is offered to food; unpayable redirected damage returns to health. */
    public static FoodDebit ward(double damage, double share, double healthPerPoint,
                                 int food, double saturation, double prepaid) {
        require(damage);
        if (!Double.isFinite(share) || share < 0 || share >= 1)
            throw new IllegalArgumentException("Ward share must be in [0, 1)");
        double redirected = damage * share;
        FoodDebit paid = debitFood(redirected, healthPerPoint, food, saturation, prepaid);
        return new FoodDebit(Math.clamp(damage - redirected + paid.remainingDamage(), 0, damage),
                paid.food(), paid.saturation(), paid.prepaidFood());
    }

    /** Finite food reserve AND the damage share constrain the pre-refill survival budget. */
    public static double wardProtection(double health, double reserve, double share) {
        require(health); require(reserve);
        if (!Double.isFinite(share) || share < 0 || share >= 1)
            throw new IllegalArgumentException("Ward share must be in [0, 1)");
        return Math.min(reserve, health * share / (1 - share));
    }

    /** Scale added survivability without making food prohibitively expensive when calibration lowers power. */
    public static double scaleShare(double share, double factor) {
        require(factor);
        if (!Double.isFinite(share) || share < 0 || share >= 1)
            throw new IllegalArgumentException("Ward share must be in [0, 1)");
        double pressure = share / (1 - share) * factor;
        return Double.isInfinite(pressure) ? Math.nextDown(1.0)
                : Math.min(Math.nextDown(1.0), pressure / (1 + pressure));
    }

    public record FoodRestore(int food, double saturation, double carry) { }
    public static FoodRestore restoreFood(double points, int food, double saturation, double carry, int maximumFood) {
        require(points); require(saturation); require(carry);
        if (food < 0 || food > maximumFood || maximumFood <= 0 || carry >= 1) throw new IllegalArgumentException("Invalid food state");
        double remaining = points + carry;
        int hunger = (int)Math.min(maximumFood - food, FractionalAmountService.split(remaining).whole());
        food += hunger; remaining -= hunger;
        if (food < maximumFood) return new FoodRestore(food, saturation, Math.clamp(remaining, 0, Math.nextDown(1.0)));
        double restored = Math.min(Math.max(0, food - saturation), remaining);
        return new FoodRestore(food, saturation + restored, 0); // no bank above a full reservoir
    }
    /** Percentage prevention and its max-health cost. Neither the available health capacity nor
     * a hit-size threshold changes the fraction of damage taken. Values are native HP, not hearts. */
    public record Ceiling(double healthDamage, double traumaAdded, double convertedDamage) { }
    public static Ceiling ceiling(double damage, double maximumHealth, double health, double traumaFraction,
                                  double takenFraction, double minimumMaximumHealth) {
        require(damage); require(maximumHealth); require(health); require(traumaFraction); require(minimumMaximumHealth);
        if (!(maximumHealth > 0) || !(takenFraction > 0 && takenFraction <= 1) || traumaFraction >= 1)
            throw new IllegalArgumentException("Invalid proportional damage state");
        double ordinaryMaximum = maximumHealth / (1 - traumaFraction);
        double applied = nativeDamage(damage * takenFraction);
        double prevented = Math.max(0, damage - applied);
        double healthAfter = Math.max(0, (float)health - (float)applied);
        // Keep the real attribute's minimum and surviving HP. These are safety boundaries, not
        // a generated Trauma bank; reaching them never sends prevented damage back to health.
        double safeFloor = Math.min(maximumHealth, Math.max(minimumMaximumHealth,
                healthAfter + Math.ulp((float)maximumHealth)));
        double safeRoom = Math.max(0, maximumHealth - safeFloor) / ordinaryMaximum;
        // The SAME generated fraction pays both costs; there is no independent Trauma ratio.
        double added = Math.min(safeRoom, prevented * takenFraction / ordinaryMaximum);
        double total = traumaFraction + added;
        if (added > 0 && (float)(ordinaryMaximum * (1 - total)) < safeFloor) {
            total = Math.nextDown(total);
            added = Math.max(0, total - traumaFraction);
        }
        return new Ceiling(applied, added, prevented);
    }

    /** Rounding never makes the native damage exceed the requested fraction. */
    public static float nativeDamage(double amount) {
        require(amount);
        float result = (float)Math.min(Float.MAX_VALUE, amount);
        return result > amount ? Math.nextDown(result) : result;
    }

    /** Scale the BONUS effective health, not the unmodified health pool. One factor is shared by
     * rank projection and defensive calibration; zero power means ordinary incoming damage. */
    public static double scaleTakenFraction(double fraction, double factor) {
        require(factor);
        if (!Double.isFinite(fraction) || fraction <= 0 || fraction > 1)
            throw new IllegalArgumentException("Damage taken fraction must be in (0, 1]");
        return Math.clamp(1 / (1 + (1 / fraction - 1) * factor), .01, 1);
    }

    /** Completed native HP loss, excluding absorption, prevention, healing and max-HP restoration. */
    public static double healthLost(double before, double after) {
        if (!Double.isFinite(before) || !Double.isFinite(after) || before <= 0) return 0;
        return Math.clamp(before - Math.max(0, after), 0, before);
    }
    private static void require(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Expected finite nonnegative value");
    }
}
