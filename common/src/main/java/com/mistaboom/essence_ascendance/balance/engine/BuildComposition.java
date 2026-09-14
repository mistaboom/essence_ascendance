package com.mistaboom.essence_ascendance.balance.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure numeric composition of resolved equipment, Nexus effects, and a reachable
 * skill selection. Inputs are gameplay units, not semantic allocation weights.
 * The caller owns reachability, tier investment curves, uptime, and equipment
 * selection; this evaluator never consults a global or partly installed profile.
 */
public final class BuildComposition {
    private static final int GUARD_ITERATIONS = 24;

    public enum Participation {
        EQUIPMENT_FOCUSED, BONUS_FOCUSED, SKILL_FOCUSED, MIXED,
        FULLY_COMBINED, CATEGORY_SPECIALIZED, BROAD_GENERALIST
    }
    public enum Metric {
        SUSTAINED_DAMAGE, BURST_DAMAGE, AREA_DAMAGE, EFFECTIVE_HEALTH, SUSTAINED_HEALTH, HEALING_PER_SECOND
    }
    /** A survival limit must not silently reduce weapon or skill damage. */
    public enum Channel {
        OFFENSE, DEFENSE, HEALING;
        public boolean includes(Metric metric) {
            return switch (this) {
                case OFFENSE -> metric == Metric.SUSTAINED_DAMAGE || metric == Metric.BURST_DAMAGE || metric == Metric.AREA_DAMAGE;
                case DEFENSE -> metric == Metric.EFFECTIVE_HEALTH;
                case HEALING -> metric == Metric.SUSTAINED_HEALTH || metric == Metric.HEALING_PER_SECOND;
            };
        }
    }

    public record Equipment(double hitDamage, double attacksPerSecond, double armor, double toughness,
                            double health, double healingPerSecond) {
        public Equipment {
            positive(hitDamage, "equipment hit damage"); positive(attacksPerSecond, "equipment attack rate");
            nonnegative(armor, "armor"); nonnegative(toughness, "toughness");
            positive(health, "health"); nonnegative(healingPerSecond, "equipment healing");
        }
    }

    /**
     * Multipliers use 1 for unchanged. Flat damage and health use hit points.
     * Burst/area fractions are additional primary-hit equivalents, already
     * adjusted by the caller for proc availability and distinct secondary targets.
     * Healing is hit points/second. Probability fields are fractions, not percents.
     */
    public record Modifier(double flatDamage, double damageMultiplier, double attackRateMultiplier,
                           double burstExtraFraction, double areaExtraFraction, double bonusHealth,
                           double armor, double toughness, double damageReduction, double avoidance,
                           double healingPerSecond, double lifeStealFraction) {
        public Modifier {
            nonnegative(flatDamage, "flat damage"); multiplier(damageMultiplier, "damage multiplier");
            multiplier(attackRateMultiplier, "attack rate multiplier");
            nonnegative(burstExtraFraction, "burst fraction"); nonnegative(areaExtraFraction, "area fraction");
            nonnegative(bonusHealth, "bonus health"); nonnegative(armor, "bonus armor"); nonnegative(toughness, "bonus toughness");
            fraction(damageReduction, "damage reduction"); fraction(avoidance, "avoidance");
            nonnegative(healingPerSecond, "healing"); fraction(lifeStealFraction, "life steal");
        }

        public static Modifier none() { return new Modifier(0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0); }

        /** Attenuates only added power; never removes an item's ordinary base damage/rate. */
        public Modifier scaled(double fraction) {
            unit(fraction, "attenuation");
            return new Modifier(flatDamage * fraction, 1 + (damageMultiplier - 1) * fraction,
                    1 + (attackRateMultiplier - 1) * fraction, burstExtraFraction * fraction,
                    areaExtraFraction * fraction, bonusHealth * fraction, armor * fraction, toughness * fraction,
                    damageReduction * fraction, avoidance * fraction, healingPerSecond * fraction,
                    lifeStealFraction * fraction);
        }

        public Modifier scaled(double fraction, Channel channel) {
            unit(fraction, "attenuation");
            double offense = channel == Channel.OFFENSE ? fraction : 1;
            double defense = channel == Channel.DEFENSE ? fraction : 1;
            double recovery = channel == Channel.HEALING ? fraction : 1;
            return new Modifier(flatDamage * offense, 1 + (damageMultiplier - 1) * offense,
                    1 + (attackRateMultiplier - 1) * offense, burstExtraFraction * offense,
                    areaExtraFraction * offense, bonusHealth * defense, armor * defense, toughness * defense,
                    damageReduction * defense, avoidance * defense, healingPerSecond * recovery,
                    lifeStealFraction * recovery);
        }

        public Modifier offenseOnly() {
            return new Modifier(flatDamage, damageMultiplier, attackRateMultiplier, burstExtraFraction,
                    areaExtraFraction, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Moderate effects must come from actual investment points and an actual
     * smaller reachable skill selection. This API deliberately does not invent
     * a fractional rank of a binary ability to describe partial participation.
     */
    public record Inputs(String id, Equipment externalEquipment, Equipment ascendanceEquipment,
                         Modifier nexusFull, Modifier nexusModerate, Modifier skillsFull, Modifier skillsModerate,
                         double incomingHit, double healingWindowSeconds) {
        public Inputs {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Build identity is required");
            Objects.requireNonNull(externalEquipment); Objects.requireNonNull(ascendanceEquipment);
            Objects.requireNonNull(nexusFull); Objects.requireNonNull(nexusModerate);
            Objects.requireNonNull(skillsFull); Objects.requireNonNull(skillsModerate);
            positive(incomingHit, "reference incoming hit"); positive(healingWindowSeconds, "healing window");
        }

        public Inputs attenuated(double factor) {
            return new Inputs(id, externalEquipment, ascendanceEquipment, nexusFull.scaled(factor),
                    nexusModerate.scaled(factor), skillsFull.scaled(factor), skillsModerate.scaled(factor),
                    incomingHit, healingWindowSeconds);
        }
        public Inputs attenuated(double factor, Channel channel) {
            return new Inputs(id, externalEquipment, ascendanceEquipment, nexusFull.scaled(factor, channel),
                    nexusModerate.scaled(factor, channel), skillsFull.scaled(factor, channel), skillsModerate.scaled(factor, channel),
                    incomingHit, healingWindowSeconds);
        }
    }

    public record Metrics(double sustainedDamage, double burstDamage, double areaDamage,
                          double effectiveHealth, double sustainedHealth, double healingPerSecond) {
        public Metrics {
            for (double value : new double[]{sustainedDamage, burstDamage, areaDamage,
                    effectiveHealth, sustainedHealth, healingPerSecond}) nonnegative(value, "composed metric");
        }
        public double value(Metric metric) {
            return switch (metric) {
                case SUSTAINED_DAMAGE -> sustainedDamage;
                case BURST_DAMAGE -> burstDamage;
                case AREA_DAMAGE -> areaDamage;
                case EFFECTIVE_HEALTH -> effectiveHealth;
                case SUSTAINED_HEALTH -> sustainedHealth;
                case HEALING_PER_SECOND -> healingPerSecond;
            };
        }
    }

    /** Explicit independent unit-preserving caps supplied from external evidence and policy. */
    public record Limits(double sustainedDamage, double burstDamage, double areaDamage,
                         double effectiveHealth, double sustainedHealth, double healingPerSecond) {
        public Limits {
            for (double value : new double[]{sustainedDamage, burstDamage, areaDamage,
                    effectiveHealth, sustainedHealth, healingPerSecond}) nonnegative(value, "composition limit");
        }
        public double value(Metric metric) {
            return switch (metric) {
                case SUSTAINED_DAMAGE -> sustainedDamage;
                case BURST_DAMAGE -> burstDamage;
                case AREA_DAMAGE -> areaDamage;
                case EFFECTIVE_HEALTH -> effectiveHealth;
                case SUSTAINED_HEALTH -> sustainedHealth;
                case HEALING_PER_SECOND -> healingPerSecond;
            };
        }
    }

    public record Violation(Participation participation, Metric metric, double actual, double limit) { }
    public record Evaluation(String id, Map<Participation, Metrics> scenarios,
                             List<Violation> violations, List<String> assumptions) {
        public Evaluation {
            Map<Participation, Metrics> ordered = new EnumMap<>(Participation.class);
            ordered.putAll(scenarios); scenarios = Collections.unmodifiableMap(ordered);
            violations = List.copyOf(violations); assumptions = List.copyOf(assumptions);
        }
        public boolean safe() { return violations.isEmpty(); }
        public void requireSafe() {
            if (!safe()) {
                Violation first = violations.getFirst();
                throw new IllegalArgumentException("Unsafe generated build " + id + "/" + first.participation()
                        + ": " + first.metric() + "=" + first.actual() + " exceeds " + first.limit()
                        + "; reduce generated bonuses/skill effects or revise the explicit pack power target");
            }
        }
    }
    public record GuardResult(double attenuation, Evaluation before, Evaluation after) { }

    private BuildComposition() { }

    /** Seven concrete participation states from the supplied resolved values. */
    public static Evaluation evaluate(Inputs inputs, Limits limits) {
        Map<Participation, Metrics> scenarios = new EnumMap<>(Participation.class);
        Modifier none = Modifier.none();
        scenarios.put(Participation.EQUIPMENT_FOCUSED, compose(inputs.ascendanceEquipment(), none, none, inputs.incomingHit(), inputs.healingWindowSeconds()));
        scenarios.put(Participation.BONUS_FOCUSED, compose(inputs.externalEquipment(), inputs.nexusFull(), none, inputs.incomingHit(), inputs.healingWindowSeconds()));
        scenarios.put(Participation.SKILL_FOCUSED, compose(inputs.externalEquipment(), none, inputs.skillsFull(), inputs.incomingHit(), inputs.healingWindowSeconds()));
        scenarios.put(Participation.MIXED, compose(inputs.ascendanceEquipment(), inputs.nexusFull(), inputs.skillsModerate(), inputs.incomingHit(), inputs.healingWindowSeconds()));
        scenarios.put(Participation.FULLY_COMBINED, compose(inputs.ascendanceEquipment(), inputs.nexusFull(), inputs.skillsFull(), inputs.incomingHit(), inputs.healingWindowSeconds()));
        scenarios.put(Participation.CATEGORY_SPECIALIZED, compose(inputs.ascendanceEquipment(), inputs.nexusFull().offenseOnly(),
                inputs.skillsFull().offenseOnly(), inputs.incomingHit(), inputs.healingWindowSeconds()));
        scenarios.put(Participation.BROAD_GENERALIST, compose(inputs.ascendanceEquipment(), inputs.nexusModerate(),
                inputs.skillsFull(), inputs.incomingHit(), inputs.healingWindowSeconds()));
        List<Violation> violations = new ArrayList<>();
        scenarios.forEach((participation, metrics) -> {
            for (Metric metric : Metric.values()) {
                double actual = metrics.value(metric), limit = limits.value(metric);
                if (actual > limit + 1e-9 * Math.max(1, limit)) violations.add(new Violation(participation, metric, actual, limit));
            }
        });
        return new Evaluation(inputs.id(), scenarios, violations, List.of(
                "Equipment and modifiers are resolved gameplay numbers for one reachable skill selection and one equipment family.",
                "Sustained damage is hit damage times attack rate; burst is one attack with burst procs; area is additional secondary-target damage per second.",
                "Critical chance, proc uptime, target count and armor penetration must already be resolved by the caller; this model does not infer them from semantic weights.",
                "Effective health uses ordinary armor/toughness against the declared " + inputs.incomingHit()
                        + "-point incoming hit, multiplicative reduction, and expected avoidance; armor-bypassing damage and shield timing are separate encounter assumptions.",
                "Sustained health adds " + inputs.healingWindowSeconds()
                        + " seconds of fully useful healing, including accepted direct-damage life steal. It is an upper bound, not measured time-to-kill or guaranteed survival.",
                "Moderate Nexus and skill inputs are supplied reachable participation states. Category-specialized is the offense-only slice; other categories retain their separate capability diagnostics."));
    }

    /**
     * Largest safe common attenuation found by a fixed bounded monotone search.
     * Base equipment is preserved. The caller must apply the factor to generated
     * bonuses/skill effects and re-evaluate the final serialized runtime profile.
     */
    public static GuardResult guard(Inputs inputs, Limits limits) {
        Evaluation before = evaluate(inputs, limits);
        if (before.safe()) return new GuardResult(1, before, before);
        Evaluation zero = evaluate(inputs.attenuated(0), limits);
        if (!zero.safe()) {
            throw new IllegalArgumentException("Base equipment or external reference exceeds composition targets for "
                    + inputs.id() + "; bonus/skill attenuation cannot repair this target: " + zero.violations().getFirst());
        }
        double low = 0, high = 1;
        for (int i = 0; i < GUARD_ITERATIONS; i++) {
            double mid = (low + high) / 2;
            if (evaluate(inputs.attenuated(mid), limits).safe()) low = mid; else high = mid;
        }
        Evaluation after = evaluate(inputs.attenuated(low), limits);
        after.requireSafe();
        return new GuardResult(low, before, after);
    }

    /** Bounded search for one power channel, preserving every unrelated upgrade. */
    public static GuardResult guard(Inputs inputs, Limits limits, Channel channel) {
        Evaluation before = evaluate(inputs, limits);
        if (safeFor(before, channel)) return new GuardResult(1, before, before);
        Evaluation zero = evaluate(inputs.attenuated(0, channel), limits);
        if (!safeFor(zero, channel))
            throw new IllegalArgumentException("Base equipment or another resolved channel exceeds " + channel
                    + " targets for " + inputs.id() + ": " + zero.violations());
        double low = 0, high = 1;
        for (int i = 0; i < GUARD_ITERATIONS; i++) {
            double mid = (low + high) / 2;
            if (safeFor(evaluate(inputs.attenuated(mid, channel), limits), channel)) low = mid; else high = mid;
        }
        return new GuardResult(low, before, evaluate(inputs.attenuated(low, channel), limits));
    }

    private static boolean safeFor(Evaluation evaluation, Channel channel) {
        return evaluation.violations().stream().noneMatch(violation -> channel.includes(violation.metric()));
    }

    public static Metrics compose(Equipment equipment, Modifier nexus, Modifier skills,
                                  double incomingHit, double healingWindowSeconds) {
        positive(incomingHit, "incoming hit"); positive(healingWindowSeconds, "healing window");
        double hit = (equipment.hitDamage() + nexus.flatDamage() + skills.flatDamage())
                * nexus.damageMultiplier() * skills.damageMultiplier();
        double rate = equipment.attacksPerSecond() * nexus.attackRateMultiplier() * skills.attackRateMultiplier();
        double sustained = hit * rate;
        double burst = hit * (1 + nexus.burstExtraFraction() + skills.burstExtraFraction());
        double area = sustained * (nexus.areaExtraFraction() + skills.areaExtraFraction());
        double health = equipment.health() + nexus.bonusHealth() + skills.bonusHealth();
        double armor = equipment.armor() + nexus.armor() + skills.armor();
        double toughness = equipment.toughness() + nexus.toughness() + skills.toughness();
        // EquipmentDamageService applies equipment and posture resistance before
        // Minecraft's nonlinear armor/toughness calculation, in that same hit.
        double resistanceTaken = (1 - nexus.damageReduction()) * (1 - skills.damageReduction());
        double taken = armorDamageFraction(incomingHit * resistanceTaken, armor, toughness)
                * resistanceTaken
                * (1 - nexus.avoidance()) * (1 - skills.avoidance());
        double healing = equipment.healingPerSecond() + nexus.healingPerSecond() + skills.healingPerSecond()
                + sustained * (nexus.lifeStealFraction() + skills.lifeStealFraction());
        return new Metrics(sustained, burst, area, health / taken,
                (health + healing * healingWindowSeconds) / taken, healing);
    }

    /** Vanilla armor/toughness mitigation, with the usual 80% armor-only cap. */
    public static double armorDamageFraction(double incomingHit, double armor, double toughness) {
        positive(incomingHit, "incoming hit"); nonnegative(armor, "armor"); nonnegative(toughness, "toughness");
        double effectiveArmor = Math.min(20, Math.max(armor / 5, armor - incomingHit / (2 + toughness / 4)));
        return 1 - effectiveArmor / 25;
    }

    private static void nonnegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(label + " must be finite and nonnegative");
    }
    private static void positive(double value, String label) {
        nonnegative(value, label);
        if (value == 0) throw new IllegalArgumentException(label + " must be positive");
    }
    private static void multiplier(double value, String label) {
        nonnegative(value, label);
        if (value < 1) throw new IllegalArgumentException(label + " must be at least one");
    }
    private static void fraction(double value, String label) {
        nonnegative(value, label);
        if (value >= 1) throw new IllegalArgumentException(label + " must be below one; immunity needs a separate capability policy");
    }
    private static void unit(double value, String label) {
        nonnegative(value, label);
        if (value > 1) throw new IllegalArgumentException(label + " must not exceed one");
    }
}
